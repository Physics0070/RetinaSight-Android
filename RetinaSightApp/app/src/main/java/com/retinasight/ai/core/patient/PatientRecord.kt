package com.retinasight.ai.core.patient

import androidx.annotation.StringRes
import com.retinasight.ai.R

/**
 * Which eye a screening belongs to.
 *
 * Diabetic retinopathy is frequently asymmetric - one eye can be grade 0 while
 * the other needs urgent referral - so a screening that does not record the eye
 * is clinically incomplete. The dashboard's worker portal captures each eye as
 * its own step for the same reason.
 */
enum class Eye(@StringRes val labelRes: Int) {
    RIGHT(R.string.eye_right),
    LEFT(R.string.eye_left)
}

/** Known-diabetic status. "Unknown" is a real field answer, not a missing value. */
enum class DiabetesStatus(@StringRes val labelRes: Int) {
    UNKNOWN(R.string.diabetes_unknown),
    YES(R.string.diabetes_yes),
    NO(R.string.diabetes_no)
}

enum class Sex(@StringRes val labelRes: Int) {
    UNSPECIFIED(R.string.sex_unspecified),
    FEMALE(R.string.sex_female),
    MALE(R.string.sex_male),
    OTHER(R.string.sex_other)
}

/**
 * One patient, held on this device.
 *
 * The record contains a name, a phone number and health status. It is stored
 * in app-private storage and only the fields below are ever uploaded - never
 * the fundus photograph.
 *
 * [consentGivenAtMillis] is non-null only once screening consent was explicitly
 * recorded. The capture flow will not start without it, mirroring the
 * dashboard's rule that consent precedes any image capture.
 */
data class PatientRecord(
    val id: String,
    val fullName: String,
    val ageYears: Int?,
    val sex: Sex,
    val phone: String?,
    val diabetes: DiabetesStatus,
    val yearsSinceDiagnosis: Int?,
    val consentGivenAtMillis: Long?,
    val createdAtMillis: Long
) {
    val hasConsent: Boolean get() = consentGivenAtMillis != null

    /**
     * Duration of diabetes is the strongest predictor of developing
     * retinopathy, so it is surfaced rather than buried in a form.
     */
    val isElevatedRisk: Boolean
        get() = diabetes == DiabetesStatus.YES && (yearsSinceDiagnosis ?: 0) >= 10
}
