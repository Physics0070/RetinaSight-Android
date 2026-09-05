package com.retinasight.ai.core.history

import com.retinasight.ai.core.model.ConfidenceBand
import com.retinasight.ai.core.model.DrGrade
import com.retinasight.ai.core.model.ReferralPolicy
import com.retinasight.ai.core.model.Urgency
import com.retinasight.ai.core.patient.Eye
import com.retinasight.ai.core.sync.SyncState

/**
 * One saved screening, stored on the device only.
 *
 * [imageFileName] points at a JPEG in the app's private storage and is NEVER
 * uploaded. History exists so a health worker can show progression over months
 * in a village with no connectivity; when a clinic is connected only the
 * structured result is shared, never the image.
 */
data class ScanRecord(
    val id: String,
    val timestampMillis: Long,
    val grade: DrGrade,
    val confidence: Float,
    val explanation: String,
    val imageFileName: String?,

    /** Who this screening belongs to. Null when screened without details. */
    val patientId: String? = null,
    val patientName: String? = null,

    /**
     * Which eye. Null only for records made before eye capture existed -
     * retinopathy is often asymmetric, so a result without an eye is
     * clinically incomplete.
     */
    val eye: Eye? = null,

    /**
     * The softmax-weighted mean grade at the time of the scan.
     *
     * Kept so history can show the urgency the patient was actually given.
     * A scan between the referral threshold and the rounding point is shown
     * as "see a doctor soon" even though its grade reads mild; recomputing
     * urgency from the grade alone would quietly contradict the record.
     *
     * Null for records saved before this was stored.
     */
    val expectedGrade: Float? = null,

    /**
     * Whether this record has reached the clinic.
     *
     * Every record starts PENDING regardless of connectivity - the health
     * worker's job ends when the screening does, and uploading is never their
     * problem to remember.
     */
    val syncState: SyncState = SyncState.PENDING
) {
    /** The band shown on the day, from the thresholds the result screen uses. */
    val confidenceBand: ConfidenceBand get() = ConfidenceBand.forConfidence(confidence)

    /**
     * The urgency this patient was actually told.
     *
     * Where [expectedGrade] was recorded the screening threshold applies just
     * as it did on the day. Older records have only the grade to go on and fall
     * back to it - which is why [explanation], stored verbatim, remains the
     * authoritative account of what was said.
     */
    val urgency: Urgency
        get() = if (expectedGrade != null && ReferralPolicy.isBorderline(expectedGrade, grade)) {
            Urgency.SOON
        } else {
            grade.urgency
        }
}
