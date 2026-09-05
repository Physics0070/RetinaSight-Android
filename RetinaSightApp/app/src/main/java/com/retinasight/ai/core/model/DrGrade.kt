package com.retinasight.ai.core.model

import androidx.annotation.StringRes
import com.retinasight.ai.R

/**
 * The five internationally used diabetic-retinopathy severity grades.
 *
 * The ordinal IS the clinical grade (0..4) produced by the classifier, so the
 * model's raw output maps here directly and nothing needs re-encoding.
 *
 * Labels are string resources, never literals, so every grade is shown in the
 * user's selected language.
 */
enum class DrGrade(
    val grade: Int,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val urgency: Urgency
) {
    NO_DR(0, R.string.grade_0_label, R.string.grade_0_desc, Urgency.ROUTINE),
    MILD(1, R.string.grade_1_label, R.string.grade_1_desc, Urgency.MONITOR),
    MODERATE(2, R.string.grade_2_label, R.string.grade_2_desc, Urgency.SOON),
    SEVERE(3, R.string.grade_3_label, R.string.grade_3_desc, Urgency.URGENT),
    PROLIFERATIVE(4, R.string.grade_4_label, R.string.grade_4_desc, Urgency.IMMEDIATE);

    companion object {
        /**
         * Maps a raw classifier output to a grade.
         * Throws on an out-of-range value rather than silently clamping: a bad
         * grade is a bug in the model wiring and must not be shown to a patient.
         */
        fun fromInt(value: Int): DrGrade =
            entries.firstOrNull { it.grade == value }
                ?: throw IllegalArgumentException("Invalid DR grade from model: $value (expected 0..4)")
    }
}
