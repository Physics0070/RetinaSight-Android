package com.retinasight.ai.core.history

import com.retinasight.ai.core.model.DrGrade
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
     * Whether this record has reached the clinic.
     *
     * Every record starts PENDING regardless of connectivity - the health
     * worker's job ends when the screening does, and uploading is never their
     * problem to remember.
     */
    val syncState: SyncState = SyncState.PENDING
)
