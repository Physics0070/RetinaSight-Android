package com.retinasight.ai.core.sync

import androidx.annotation.StringRes
import com.retinasight.ai.R

/**
 * Where a saved screening stands with respect to the clinic.
 *
 * A record is PENDING the moment it is saved, whether or not a clinic is
 * connected. That is deliberate: the health worker's job is finished when the
 * screening is done, and nothing about uploading should feel like their
 * responsibility.
 */
enum class SyncState(@StringRes val labelRes: Int) {
    /** Saved on this phone, not yet sent anywhere. */
    PENDING(R.string.sync_state_pending),

    /** Accepted by the clinic. */
    SYNCED(R.string.sync_state_synced),

    /** Tried and rejected. Stays on the phone; never silently dropped. */
    FAILED(R.string.sync_state_failed)
}

/**
 * The clinic this device uploads to.
 *
 * Absent by default. The app is completely usable having never connected one -
 * connecting only adds the ability to share records, it never unlocks
 * screening.
 */
data class ClinicConnection(
    val baseUrl: String,
    val deviceId: String,
    val token: String?
) {
    val isAuthenticated: Boolean get() = !token.isNullOrBlank()
}

/** What the sync UI shows. */
data class SyncStatus(
    val isOnline: Boolean,
    val clinic: ClinicConnection?,
    val pendingCount: Int,
    val failedCount: Int,
    val isSyncing: Boolean,
    val lastSyncedAtMillis: Long?,
    val lastError: String? = null
) {
    val isConnected: Boolean get() = clinic != null

    /**
     * Uploading requires all three. Checked as one property so no screen can
     * accidentally try to sync while offline or unconnected.
     */
    val canSync: Boolean
        get() = isOnline && clinic?.isAuthenticated == true && pendingCount > 0
}

/**
 * One queued change, matching the backend's `SyncItemInput`.
 *
 * `localId` is what makes the push idempotent: the server acknowledges a
 * repeat as a duplicate rather than creating a second clinical record, so a
 * retry after a dropped connection is safe.
 */
data class SyncItem(
    val localId: String,
    val entityType: String,
    val operation: String = "create",
    val payloadJson: String
)

/** Outcome of one push batch. */
data class SyncResult(
    val accepted: Int,
    val duplicates: Int,
    val failed: Int,
    val syncedLocalIds: List<String>,
    val error: String? = null
) {
    companion object {
        fun failure(message: String) = SyncResult(0, 0, 0, emptyList(), message)
    }
}
