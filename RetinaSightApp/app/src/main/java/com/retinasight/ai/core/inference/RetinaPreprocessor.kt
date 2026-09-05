package com.retinasight.ai.core.inference

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Kotlin port of the preprocessing the dr-v2 model was TRAINED with.
 *
 * Source of truth: Omnikon `backend/app/ml/preprocessing.py`, which the
 * training code imports directly (`ml/datasets/retinal_dataset.py`), so
 * training and serving already shared one pipeline. This file makes the phone
 * the third consumer of that same contract.
 *
 * The pipeline is deliberately simple - and notably does NOT include Ben Graham
 * local-colour normalisation or a circular mask. Adding either would change the
 * input distribution the model was fitted on and silently degrade accuracy, so
 * do not "improve" this file.
 *
 *   1. crop to the retinal disc
 *        luminance = 0.299R + 0.587G + 0.114B  (ITU-R BT.601)
 *        keep pixels with luminance > 18, take the bounding box,
 *        then pad by 2% of the box on each axis
 *   2. resize to 456x456 with Pillow's antialiased BILINEAR
 *        (OpenCV is absent from the training environment, so the Pillow branch
 *        is the one that produced the cached training images)
 *   3. scale to [0,1], ImageNet mean/std, NCHW float32
 */
object RetinaPreprocessor {

    /** dr-v2 is a 456px model. See assets/dr-v2.json. */
    const val INPUT_SIZE = 456

    private const val LUMINANCE_THRESHOLD = 18.0f
    private const val CROP_PADDING = 0.02f

    private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** Raw bitmap -> NCHW float32 tensor of length 3*456*456. */
    fun process(source: Bitmap): FloatArray = normalize(processToRgb(source))

    /**
     * Everything except normalisation: crop + resize.
     *
     * Returns interleaved RGB bytes (456*456*3). This is the array the Python
     * pipeline produces at the same point, so it is what a parity check
     * compares - and it is also what the Grad-CAM overlay is drawn on top of.
     */
    fun processToRgb(source: Bitmap): IntArray {
        val width = source.width
        val height = source.height
        val argb = IntArray(width * height)
        source.getPixels(argb, 0, width, 0, 0, width, height)

        val box = findCropBox(argb, width, height)
        val cropped = crop(argb, width, box)
        return resizeBilinear(cropped, box.width, box.height, INPUT_SIZE)
    }

    // ------------------------------------------------------------------ crop

    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /**
     * Bounding box of the illuminated retinal disc, padded by 2%.
     *
     * Mirrors find_crop_box(): right/bottom are EXCLUSIVE, and the padding is
     * computed with integer truncation, exactly as Python's int() does.
     */
    private fun findCropBox(argb: IntArray, width: Int, height: Int): Box {
        var top = -1
        var bottom = -1
        var left = width
        var right = -1

        for (y in 0 until height) {
            var rowHit = false
            val rowBase = y * width
            for (x in 0 until width) {
                val p = argb[rowBase + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val luminance = 0.299f * r + 0.587f * g + 0.114f * b
                if (luminance > LUMINANCE_THRESHOLD) {
                    rowHit = true
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
            if (rowHit) {
                if (top < 0) top = y
                bottom = y
            }
        }

        // Nothing above threshold: use the whole frame, as Python does.
        if (top < 0 || right < 0) return Box(0, 0, width, height)

        val exclusiveRight = right + 1
        val exclusiveBottom = bottom + 1
        val padY = ((exclusiveBottom - top) * CROP_PADDING).toInt()
        val padX = ((exclusiveRight - left) * CROP_PADDING).toInt()

        val box = Box(
            left = maxOf(0, left - padX),
            top = maxOf(0, top - padY),
            right = minOf(width, exclusiveRight + padX),
            bottom = minOf(height, exclusiveBottom + padY)
        )

        // crop_to_retina() falls back to the full frame for a degenerate box.
        return if (box.width < 2 || box.height < 2) Box(0, 0, width, height) else box
    }

    private fun crop(argb: IntArray, srcWidth: Int, box: Box): FloatArray {
        val out = FloatArray(box.width * box.height * 3)
        var d = 0
        for (y in box.top until box.bottom) {
            val rowBase = y * srcWidth
            for (x in box.left until box.right) {
                val p = argb[rowBase + x]
                out[d++] = ((p shr 16) and 0xFF).toFloat()
                out[d++] = ((p shr 8) and 0xFF).toFloat()
                out[d++] = (p and 0xFF).toFloat()
            }
        }
        return out
    }

    // ---------------------------------------------------------------- resize

    /**
     * Pillow's BILINEAR resample.
     *
     * This is NOT naive bilinear sampling and NOT Bitmap.createScaledBitmap.
     * On downscale Pillow widens the triangle filter by the reduction factor,
     * which antialiases; a naive sampler aliases instead and shifts the pixel
     * statistics the model was trained on.
     *
     * Two separable passes, with the intermediate rounded back to 8-bit exactly
     * as Pillow's 8-bit path does.
     */
    private fun resizeBilinear(src: FloatArray, srcW: Int, srcH: Int, outSize: Int): IntArray {
        val horizontal = resamplePass(src, srcW, srcH, outSize, horizontal = true)
        val vertical = resamplePass(horizontal, outSize, srcH, outSize, horizontal = false)
        return IntArray(vertical.size) { vertical[it].roundToInt().coerceIn(0, 255) }
    }

    /** One separable pass of the triangle filter. */
    private fun resamplePass(
        src: FloatArray,
        srcW: Int,
        srcH: Int,
        outSize: Int,
        horizontal: Boolean
    ): FloatArray {
        val inSize = if (horizontal) srcW else srcH
        val otherSize = if (horizontal) srcH else outSize

        val scale = inSize.toFloat() / outSize
        val filterScale = if (scale < 1.0f) 1.0f else scale
        val support = 1.0f * filterScale          // bilinear support = 1.0
        val invFilterScale = 1.0f / filterScale

        val outW = if (horizontal) outSize else srcW
        val out = FloatArray(outW * otherSize * 3)

        val weights = FloatArray((support * 2 + 2).toInt() + 2)

        for (i in 0 until outSize) {
            val center = (i + 0.5f) * scale
            var lo = (center - support + 0.5f).toInt()
            if (lo < 0) lo = 0
            var hi = (center + support + 0.5f).toInt()
            if (hi > inSize) hi = inSize
            val count = hi - lo

            var total = 0.0f
            for (k in 0 until count) {
                val x = (k + lo - center + 0.5f) * invFilterScale
                val w = if (abs(x) < 1.0f) 1.0f - abs(x) else 0.0f
                weights[k] = w
                total += w
            }
            if (total != 0.0f) {
                for (k in 0 until count) weights[k] /= total
            }

            for (j in 0 until otherSize) {
                var sr = 0.0f
                var sg = 0.0f
                var sb = 0.0f
                for (k in 0 until count) {
                    val si = if (horizontal) {
                        (j * srcW + (lo + k)) * 3
                    } else {
                        ((lo + k) * outW + j) * 3
                    }
                    val w = weights[k]
                    sr += w * src[si]
                    sg += w * src[si + 1]
                    sb += w * src[si + 2]
                }
                val di = if (horizontal) (j * outW + i) * 3 else (i * outW + j) * 3
                // Pillow's 8-bit path clips and rounds between passes.
                out[di] = sr.coerceIn(0f, 255f).roundToInt().toFloat()
                out[di + 1] = sg.coerceIn(0f, 255f).roundToInt().toFloat()
                out[di + 2] = sb.coerceIn(0f, 255f).roundToInt().toFloat()
            }
        }
        return out
    }

    // ------------------------------------------------------------- normalise

    /**
     * The exact pixels the model saw, as a Bitmap for display.
     *
     * The Grad-CAM/CAM grid is computed over THIS image - cropped to the disc
     * and resized to 456 - not over the original photo. Overlaying the heat map
     * on the original would point at the wrong part of the eye, so the result
     * screen must show this image underneath it.
     */
    fun toBitmap(rgb: IntArray, size: Int = INPUT_SIZE): Bitmap {
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            val r = rgb[i * 3]
            val g = rgb[i * 3 + 1]
            val b = rgb[i * 3 + 2]
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Interleaved RGB uint8 -> NCHW float32, ImageNet-normalised. */
    fun normalize(rgb: IntArray): FloatArray {
        val plane = INPUT_SIZE * INPUT_SIZE
        val out = FloatArray(3 * plane)
        for (p in 0 until plane) {
            for (c in 0 until 3) {
                val v = rgb[p * 3 + c] / 255.0f
                out[c * plane + p] = (v - IMAGENET_MEAN[c]) / IMAGENET_STD[c]
            }
        }
        return out
    }
}
