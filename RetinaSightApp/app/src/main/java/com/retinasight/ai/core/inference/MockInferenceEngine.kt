package com.retinasight.ai.core.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.retinasight.ai.BuildConfig
import com.retinasight.ai.R
import com.retinasight.ai.core.model.DrGrade
import com.retinasight.ai.core.model.RetinaResult
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/**
 * DEVELOPMENT ONLY. Lets the UI be built and reviewed before the real model is
 * exported. It does NOT perform any medical analysis.
 *
 * Two deliberate safeguards keep this from ever reaching a user:
 *  1. It refuses to run in a release build (see [guardAgainstReleaseBuild]).
 *  2. Its output varies with the image's own pixels, so a fixed "demo result"
 *     can never be mistaken for a real one during development.
 */
class MockInferenceEngine(private val context: Context) : InferenceEngine {

    override var isReady: Boolean = false
        private set

    override suspend fun warmUp() {
        guardAgainstReleaseBuild()
        delay(150) // stands in for loading the real model
        isReady = true
    }

    override suspend fun analyze(image: Bitmap, languageTag: String): RetinaResult {
        guardAgainstReleaseBuild()
        if (!isReady) warmUp()

        // Stands in for on-device inference time so the UI's progress state is
        // exercised realistically during development.
        delay(700)

        // Derive a stable pseudo-grade from the image itself. This is NOT a
        // prediction - it only guarantees different images give different
        // screens, so nobody mistakes a constant for a working model.
        val seed = imageFingerprint(image)
        val grade = DrGrade.fromInt(abs(seed) % 5)
        val confidence = 0.55f + Random(seed).nextFloat() * 0.44f

        return RetinaResult(
            grade = grade,
            confidence = confidence.coerceIn(0f, 1f),
            heatmap = null, // the real engine supplies a Grad-CAM overlay
            // No distribution to average over here, so the grade stands in for
            // the expected grade. Dev-only path; never a referral decision.
            expectedGrade = grade.grade.toFloat(),
            explanation = context.getString(R.string.mock_explanation_notice)
        )
    }

    override fun close() {
        isReady = false
    }

    /** Cheap, deterministic fingerprint of the image content. */
    private fun imageFingerprint(image: Bitmap): Int {
        val samples = 16
        var hash = 7
        for (i in 0 until samples) {
            val x = (image.width - 1) * i / samples
            val y = (image.height - 1) * i / samples
            val px = image.getPixel(x, y)
            hash = hash * 31 + (Color.red(px) + Color.green(px) + Color.blue(px))
        }
        return hash
    }

    private fun guardAgainstReleaseBuild() {
        check(BuildConfig.DEBUG) {
            "MockInferenceEngine must never run in a release build. " +
                "Wire OnDeviceInferenceEngine before shipping."
        }
    }
}
