package com.retinasight.ai.core.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.retinasight.ai.R
import com.retinasight.ai.core.model.DrGrade
import com.retinasight.ai.core.model.ReferralPolicy
import com.retinasight.ai.core.model.RetinaResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.util.Locale
import kotlin.math.exp

/**
 * Runs the trained dr-v2 grader entirely on the phone.
 *
 * The model is the EfficientNet-B0 (456px, ordinal objective) exported to ONNX,
 * shipped in assets/dr-v2.onnx. It is used AS EXPORTED - no conversion, no
 * re-quantisation - so the numbers on device are the numbers that were
 * validated on the desktop.
 *
 * The graph emits three outputs from one forward pass:
 *
 *   logits (1,5)        raw scores
 *   cam    (1,5,15,15)  one class activation map per grade, gradient-free
 *   grade  (1,)         the ordinal decision, ALREADY rounded from the
 *                       expected grade inside the graph
 *
 * That last output matters: this model was trained with an ordinal objective
 * and decides by rounding the softmax-weighted mean grade, not by argmax.
 * Taking argmax here would silently disagree with the reported QWK, so the
 * graph's own `grade` output is authoritative and is what we use.
 *
 * Execution providers are tried in order and the first that initialises wins.
 * The provider changes only SPEED - never the output shape or the decision -
 * so a phone without NNAPI still produces identical results, just slower.
 */
class OnDeviceInferenceEngine(private val context: Context) : InferenceEngine {

    override var isReady: Boolean = false
        private set

    /** Which execution provider actually initialised. Shown in Settings. */
    @Volatile
    var activeProvider: String = "not initialised"
        private set

    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null

    // ORT sessions are not safe for concurrent run() calls from coroutines.
    private val lock = Mutex()

    override suspend fun warmUp() = withContext(Dispatchers.Default) {
        lock.withLock {
            if (isReady) return@withLock

            val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val env = OrtEnvironment.getEnvironment()

            // Try the accelerated provider first, fall back without failing.
            val created = tryCreateSession(env, modelBytes, useNnapi = true)
                ?: tryCreateSession(env, modelBytes, useNnapi = false)
                ?: error("Could not create an ONNX Runtime session for $MODEL_ASSET")

            environment = env
            session = created.first
            activeProvider = created.second
            isReady = true

            Log.i(TAG, "model loaded (${modelBytes.size / 1024} KB), provider=$activeProvider")
        }
    }

    private fun tryCreateSession(
        env: OrtEnvironment,
        modelBytes: ByteArray,
        useNnapi: Boolean
    ): Pair<OrtSession, String>? = runCatching {
        val options = OrtSession.SessionOptions()
        val label: String
        if (useNnapi) {
            // NNAPI routes to the Hexagon NPU / GPU where the vendor driver
            // supports the ops, and silently falls back per-op where it does not.
            options.addNnapi()
            label = "NNAPI (NPU/GPU)"
        } else {
            options.setIntraOpNumThreads(4)
            label = "CPU"
        }
        env.createSession(modelBytes, options) to label
    }.onFailure {
        Log.w(TAG, "provider ${if (useNnapi) "NNAPI" else "CPU"} unavailable: ${it.message}")
    }.getOrNull()

    override suspend fun analyze(image: Bitmap, languageTag: String): RetinaResult =
        withContext(Dispatchers.Default) {
            if (!isReady) warmUp()
            val active = session ?: error("Session not initialised")
            val env = environment ?: error("Environment not initialised")

            // One preprocessing pass feeds both the model and the display,
            // which guarantees the overlay is aligned to what the model saw.
            val processedRgb = RetinaPreprocessor.processToRgb(image)
            val input = RetinaPreprocessor.normalize(processedRgb)
            val processedBitmap = RetinaPreprocessor.toBitmap(processedRgb)

            lock.withLock {
                OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(input),
                    longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())
                ).use { tensor ->
                    active.run(mapOf(INPUT_NAME to tensor)).use { results ->

                        @Suppress("UNCHECKED_CAST")
                        val logits = (results.get(0).value as Array<FloatArray>)[0]

                        @Suppress("UNCHECKED_CAST")
                        val cams = (results.get(1).value as Array<Array<Array<FloatArray>>>)[0]

                        val gradeIndex = (results.get(2).value as LongArray)[0].toInt()

                        val probabilities = softmax(logits)
                        val grade = DrGrade.fromInt(gradeIndex.coerceIn(0, 4))

                        // The same quantity the graph rounds to reach `grade`.
                        // Recomputed here because the referral threshold sits
                        // below the rounding point and needs the raw value.
                        val expectedGrade = expectedGrade(probabilities)

                        RetinaResult(
                            grade = grade,
                            confidence = probabilities[gradeIndex].coerceIn(0f, 1f),
                            heatmap = camToBitmap(cams[gradeIndex]),
                            processedImage = processedBitmap,
                            expectedGrade = expectedGrade,
                            explanation = buildExplanation(
                                grade, probabilities[gradeIndex], expectedGrade, languageTag
                            )
                        )
                    }
                }
            }
        }

    override fun close() {
        runCatching { session?.close() }
        session = null
        environment = null
        isReady = false
        activeProvider = "closed"
    }

    /**
     * Composes the advice line from values the model actually produced.
     *
     * This is deliberately NOT generated text. A small language model asked to
     * explain a medical result will eventually invent a symptom or a treatment,
     * and there is no way to catch that on a phone in a village. Every sentence
     * here is a fixed, translated string selected by a real number, so the app
     * can be wrong about the grade but can never fabricate clinical advice.
     */
    private fun buildExplanation(
        grade: DrGrade,
        confidence: Float,
        expectedGrade: Float,
        languageTag: String
    ): String {
        // The engine holds the Application context, whose locale follows the
        // SYSTEM language - not the one the user picked in the app. Resolving
        // against a locale-specific context is what keeps the spoken advice in
        // the same language as the rest of the screen.
        val localized = localizedContext(languageTag)
        val parts = mutableListOf<String>()

        if (confidence < LOW_CONFIDENCE) {
            parts += localized.getString(R.string.explain_low_confidence)
        }
        when {
            // A scan the screening threshold refers but the grade does not.
            // Say why, rather than letting the grade and the advice disagree
            // silently on screen.
            ReferralPolicy.isBorderline(expectedGrade, grade) ->
                parts += localized.getString(R.string.explain_borderline_referral)

            grade == DrGrade.NO_DR -> parts += localized.getString(R.string.explain_no_dr)

            grade.grade >= DrGrade.MODERATE.grade ->
                parts += localized.getString(R.string.explain_refer_soon)
        }
        parts += localized.getString(R.string.explain_heatmap_hint)

        return parts.joinToString(" ")
    }

    private fun localizedContext(languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config)
    }

    // ------------------------------------------------------------- helpers

    /**
     * The softmax-weighted mean grade - the quantity the graph rounds to reach
     * its `grade` output. Identical arithmetic, kept unrounded.
     */
    private fun expectedGrade(probabilities: FloatArray): Float {
        var sum = 0f
        for (i in probabilities.indices) sum += probabilities[i] * i
        return sum.coerceIn(0f, 4f)
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exps = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return FloatArray(exps.size) { exps[it] / sum }
    }

    /**
     * A 15x15 class activation map -> a translucent heat overlay.
     *
     * Min-max normalised so the strongest evidence in THIS image is the
     * brightest point; the map is returned small and the UI scales it, which
     * keeps the bilinear smoothing on the GPU where it is free.
     */
    private fun camToBitmap(cam: Array<FloatArray>): Bitmap? {
        val h = cam.size
        if (h == 0) return null
        val w = cam[0].size
        if (w == 0) return null

        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (row in cam) for (v in row) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val span = (max - min).takeIf { it > 1e-6f } ?: return null

        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val t = (cam[y][x] - min) / span
                pixels[y * w + x] = heatColor(t)
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Transparent -> amber -> red.
     *
     * Low activation stays fully transparent so the retina underneath is still
     * readable; a clinician has to be able to see the lesion, not just the blob.
     */
    private fun heatColor(t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val alpha = (clamped * 190f).toInt().coerceIn(0, 190)
        val red = 255
        val green = ((1f - clamped) * 190f).toInt().coerceIn(0, 255)
        return Color.argb(alpha, red, green, 0)
    }

    private companion object {
        const val TAG = "OnDeviceInference"
        const val MODEL_ASSET = "dr-v2.onnx"
        const val INPUT_NAME = "input"
        const val SIZE = RetinaPreprocessor.INPUT_SIZE

        /**
         * Below this, advise retaking the photo rather than trusting the grade.
         *
         * Deliberately the same value that makes the band LOW, so a result the
         * app calls "Low" always carries the retake advice with it.
         */
        const val LOW_CONFIDENCE = RetinaResult.MEDIUM_CONFIDENCE
    }
}
