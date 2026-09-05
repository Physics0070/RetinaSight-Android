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

    /** Coarse confidence band, for users who cannot read a percentage. */
    val confidenceBand: ConfidenceBand
        get() = when {
            confidence >= 0.80f -> ConfidenceBand.HIGH
            confidence >= 0.55f -> ConfidenceBand.MEDIUM
            else -> ConfidenceBand.LOW
        }

    init {
        require(confidence in 0f..1f) { "Confidence out of range: $confidence" }
        require(expectedGrade in 0f..4f) { "Expected grade out of range: $expectedGrade" }
    }
}

enum class ConfidenceBand { LOW, MEDIUM, HIGH }
