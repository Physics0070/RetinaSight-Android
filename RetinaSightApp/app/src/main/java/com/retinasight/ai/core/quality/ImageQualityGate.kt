package com.retinasight.ai.core.quality

import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.retinasight.ai.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Capture-quality gate, ported from Omnikon `backend/app/ml/quality.py`.
 *
 * Why this exists: the worst failure mode for a screening app is confidently
 * grading a photo that is blurred, dark, or not even a retina. The model will
 * always return a grade - it has no way to say "this is not a fundus image".
 * This gate is what lets the app say "take it again" instead.
 *
 * Four measurements are taken from the pixels:
 *   blur       variance of the Laplacian (focus)
 *   lighting   mean retinal luminance vs target, plus clipped-white fraction
 *   framing    how centred the retinal disc is and how much frame it fills
 *   visibility how much of the frame is retina, and whether it is red-dominant
 *
 * The reference values are NOT invented here - they are the ones calibrated
 * against 250 real APTOS photographs in the Python implementation, so a phone
 * and the server agree on what "too blurry" means.
 *
 * Nothing here is a clinical assessment. It only decides whether an image is
 * worth sending to the model at all.
 */
object ImageQualityGate {

    // ---- thresholds (quality.thresholds) ----
    private const val OVERALL_MIN = 0.55f
    private const val BLUR_MIN = 0.45f
    private const val LIGHTING_MIN = 0.40f
    private const val FRAMING_MIN = 0.40f
    private const val VISIBILITY_MIN = 0.50f
    private const val MIN_WIDTH = 224
    private const val MIN_HEIGHT = 224

    // ---- normalisation (quality.normalisation) ----
    private const val ANALYSIS_LONG_EDGE = 512
    private const val SHARPNESS_REFERENCE = 30.0f
    private const val TARGET_LUMINANCE = 90.5f
    private const val LUMINANCE_TOLERANCE = 65.0f
    private const val MAX_CLIPPED_FRACTION = 0.02f
    private const val TARGET_COVERAGE = 0.789f
    private const val COVERAGE_TOLERANCE = 0.692f
    private const val MAX_CENTRE_OFFSET = 0.136f
    private const val RED_RATIO_FLOOR = 0.36f
    private const val RED_RATIO_SPAN = 0.14f

    /** Luminance above which a pixel counts as part of the illuminated disc. */
    private const val RETINA_THRESHOLD = 18.0f

    enum class Issue(@StringRes val messageRes: Int) {
        BLUR(R.string.quality_issue_blur),
        LOW_LIGHT(R.string.quality_issue_low_light),
        OVEREXPOSED(R.string.quality_issue_overexposed),
        POOR_FRAMING(R.string.quality_issue_framing),
        RETINA_NOT_VISIBLE(R.string.quality_issue_not_retina),
        LOW_RESOLUTION(R.string.quality_issue_resolution)
    }

    data class Result(
        val isAcceptable: Boolean,
        val overall: Float,
        val blur: Float,
        val lighting: Float,
        val framing: Float,
        val visibility: Float,
        val issues: List<Issue>
    )

    fun assess(bitmap: Bitmap): Result {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val issues = mutableListOf<Issue>()

        // Resolution is judged on the ORIGINAL; everything else on a fixed
        // analysis scale, because Laplacian variance is scale-dependent and
        // would otherwise track the phone's sensor rather than image quality.
        if (originalWidth < MIN_WIDTH || originalHeight < MIN_HEIGHT) {
            issues += Issue.LOW_RESOLUTION
        }

        val analysis = toAnalysisScale(bitmap)
        val w = analysis.width
        val h = analysis.height
        val pixels = IntArray(w * h)
        analysis.getPixels(pixels, 0, w, 0, 0, w, h)
        if (analysis !== bitmap) analysis.recycle()

        val gray = FloatArray(w * h)
        val mask = BooleanArray(w * h)
        var maskCount = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            gray[i] = lum
            if (lum > RETINA_THRESHOLD) {
                mask[i] = true
                maskCount++
            }
        }

        // ---- blur ----
        val blurScore = clamp(laplacianVariance(gray, w, h) / SHARPNESS_REFERENCE)

        // ---- lighting ----
        var retinaSum = 0.0
        var clippedBright = 0
        for (i in gray.indices) {
            if (mask[i]) retinaSum += gray[i]
            if (gray[i] >= 253f) clippedBright++
        }
        val meanLuminance = when {
            maskCount > 0 -> (retinaSum / maskCount).toFloat()
            gray.isNotEmpty() -> gray.average().toFloat()
            else -> 0f
        }
        val luminanceScore = clamp(1f - abs(meanLuminance - TARGET_LUMINANCE) / LUMINANCE_TOLERANCE)
        val clippedFraction = clippedBright.toFloat() / max(1, gray.size)
        val clippingScore = clamp(1f - clippedFraction / MAX_CLIPPED_FRACTION)
        val lightingScore = clamp(min(luminanceScore, clippingScore))

        // ---- framing ----
        val coverage = maskCount.toFloat() / max(1, gray.size)
        val coverageScore = clamp(1f - abs(coverage - TARGET_COVERAGE) / COVERAGE_TOLERANCE)

        val offset = if (maskCount > 0) {
            var sumX = 0.0
            var sumY = 0.0
            for (i in mask.indices) {
                if (mask[i]) {
                    sumX += (i % w)
                    sumY += (i / w)
                }
            }
            val cx = (sumX / maskCount).toFloat()
            val cy = (sumY / maskCount).toFloat()
            hypot(cx - w / 2f, cy - h / 2f) / max(1f, hypot(w.toFloat(), h.toFloat()) / 2f)
        } else {
            1f
        }
        val centringScore = clamp(1f - offset / MAX_CENTRE_OFFSET)
        val framingScore = clamp(min(coverageScore, centringScore))

        // ---- retinal visibility ----
        // Fundus imagery is red-dominant; a frame with no red-dominant disc is
        // very unlikely to be a retina at all.
        val redRatio = if (maskCount > 0) {
            var sr = 0.0
            var sg = 0.0
            var sb = 0.0
            for (i in mask.indices) {
                if (mask[i]) {
                    val p = pixels[i]
                    sr += (p shr 16) and 0xFF
                    sg += (p shr 8) and 0xFF
                    sb += p and 0xFF
                }
            }
            val total = (sr + sg + sb).takeIf { it > 0.0 } ?: 1.0
            (sr / total).toFloat()
        } else {
            0f
        }
        val rednessScore = clamp((redRatio - RED_RATIO_FLOOR) / RED_RATIO_SPAN)
        val visibilityScore = clamp(min(coverageScore, rednessScore))

        val overall = (blurScore + lightingScore + framingScore + visibilityScore) / 4f

        // ---- policy ----
        if (blurScore < BLUR_MIN) issues += Issue.BLUR
        if (lightingScore < LIGHTING_MIN) {
            // Distinguish too-dark from blown-out so the advice is actionable.
            issues += if (clippingScore < luminanceScore) Issue.OVEREXPOSED else Issue.LOW_LIGHT
        }
        if (framingScore < FRAMING_MIN) issues += Issue.POOR_FRAMING
        if (visibilityScore < VISIBILITY_MIN) issues += Issue.RETINA_NOT_VISIBLE

        return Result(
            isAcceptable = issues.isEmpty() && overall >= OVERALL_MIN,
            overall = overall,
            blur = blurScore,
            lighting = lightingScore,
            framing = framingScore,
            visibility = visibilityScore,
            issues = issues.distinct()
        )
    }

    /** 3x3 discrete Laplacian - a standard focus operator. */
    private fun laplacianVariance(gray: FloatArray, w: Int, h: Int): Float {
        if (w < 3 || h < 3) return 0f
        val n = (w - 2) * (h - 2)
        var sum = 0.0
        var sumSq = 0.0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val r = gray[i - w] + gray[i + w] + gray[i - 1] + gray[i + 1] - 4f * gray[i]
                sum += r
                sumSq += r.toDouble() * r
            }
        }
        val mean = sum / n
        return ((sumSq / n) - mean * mean).toFloat()
    }

    /** Downscale so measurements do not depend on the camera's resolution. */
    private fun toAnalysisScale(bitmap: Bitmap): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= ANALYSIS_LONG_EDGE) return bitmap
        val scale = ANALYSIS_LONG_EDGE.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * scale).toInt()),
            max(1, (bitmap.height * scale).toInt()),
            true
        )
    }

    private fun clamp(v: Float) = v.coerceIn(0f, 1f)
}
