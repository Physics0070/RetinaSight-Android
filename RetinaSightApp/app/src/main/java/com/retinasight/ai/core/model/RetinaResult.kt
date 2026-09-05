package com.retinasight.ai.core.model

import android.graphics.Bitmap

/**
 * The outcome of one screening run.
 *
 * Every field here is DERIVED FROM THE MODEL AND THE ACTUAL IMAGE.
 * Nothing in this class may ever be populated with a canned or demo value in a
 * production build - that is the core authenticity rule of this project.
 */
data class RetinaResult(
    /** Severity grade produced by the classifier. */
    val grade: DrGrade,

    /** Model confidence in [0f, 1f]. */
    val confidence: Float,

    /** Class activation overlay showing where the model looked. Null if unavailable. */
    val heatmap: Bitmap?,

    /**
     * The cropped, resized image the model actually saw.
     *
     * The heat map is aligned to THIS, not to the original photo, so the result
     * screen draws the overlay on top of it. Null when unavailable.
     */
    val processedImage: Bitmap? = null,

    /**
     * Plain-language explanation in the user's selected language, generated from
     * the structured outputs above. Never invents clinical facts.
     */
    val explanation: String,

    /**
     * The softmax-weighted mean grade, before rounding.
     *
     * The referral decision reads this rather than the rounded grade, so the
     * screening threshold can sit below the grader's balanced point without
     * changing the grade that is displayed. See [ReferralPolicy].
     */
    val expectedGrade: Float,

    /** When the scan ran. */
    val timestampMillis: Long = System.currentTimeMillis()
) {
    /** Whether this scan should go to an ophthalmologist. */
    val referable: Boolean get() = ReferralPolicy.isReferable(expectedGrade)

    /**
     * True when the referral comes from the screening threshold, not the grade.
     * The result screen explains the disagreement instead of hiding it.
     */
    val borderlineReferral: Boolean get() = ReferralPolicy.isBorderline(expectedGrade, grade)

    /**
     * Referral urgency - kept in one place, not duplicated per screen.
     *
     * Normally the grade's own urgency. A scan the screening threshold refers
     * but the grade does not is raised to SOON: under-referring is the error
     * this app is built to avoid.
     */
    val urgency: Urgency
        get() = if (borderlineReferral) Urgency.SOON else grade.urgency

    /**
     * Coarse confidence band, for users who cannot read a percentage.
     *
     * The cut points are measured, not guessed. Binning the held-out APTOS
     * validation split (n=546) by the confidence the app displays, and
     * counting how often the displayed grade was actually correct:
     *
     *   confidence      n     exact grade correct
     *   below 0.55     42            50.0%
     *   0.55 - 0.90   157             ~70%
     *   0.90 and up   357            93.3%
     *
     * 0.90 is where "High" starts telling the truth. The previous cut of 0.80
     * pulled in the 0.80-0.90 band, which is only 71% correct - calling that
     * "High" to a technician overstates what the model knows.
     *
     * Worth knowing: the model is overconfident at the very top. The 0.90-0.95
     * bin is 97.6% correct while 0.95-1.00 falls to 83.9%, so confidence is not
     * monotonic with accuracy. That is precisely why this is a three-way band
     * and not a percentage on screen.
     *
     * These describe the *grade* only. Even below 0.55 the referral decision
     * still agreed with ground truth 97.6% of the time: low confidence means
     * "unsure which grade", not "unsure whether to refer".
     */
    val confidenceBand: ConfidenceBand get() = ConfidenceBand.forConfidence(confidence)

    init {
        require(confidence in 0f..1f) { "Confidence out of range: $confidence" }
        require(expectedGrade in 0f..4f) { "Expected grade out of range: $expectedGrade" }
    }

    companion object {
        /** At or above this the displayed grade was right 93.3% of the time. */
        const val HIGH_CONFIDENCE = 0.90f

        /**
         * Below this the grade is close to a coin flip (50.0% correct), which
         * is why `OnDeviceInferenceEngine.LOW_CONFIDENCE` advises a retake at
         * the same value. Keep the two equal, or the app shows "Low" without
         * suggesting the retake that would fix it.
         */
        const val MEDIUM_CONFIDENCE = 0.55f
    }
}

enum class ConfidenceBand {
    LOW, MEDIUM, HIGH;

    companion object {
        /**
         * The one place a confidence becomes a band.
         *
         * History replays saved records and needs the same answer the result
         * screen gave on the day, so the thresholds cannot live in two places.
         */
        fun forConfidence(confidence: Float): ConfidenceBand = when {
            confidence >= RetinaResult.HIGH_CONFIDENCE -> HIGH
            confidence >= RetinaResult.MEDIUM_CONFIDENCE -> MEDIUM
            else -> LOW
        }
    }
}
