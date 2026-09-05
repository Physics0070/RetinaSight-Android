package com.retinasight.ai.core.sync

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.retinasight.ai.core.history.ScanHistoryStore
import com.retinasight.ai.core.history.ScanRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

private val Context.syncDataStore by preferencesDataStore(name = "retinasight_sync")

/**
 * Uploads completed screenings to a clinic, when there is one and there is a
 * connection.
 *
 * Design rules this class exists to enforce:
 *
 *  1. Screening NEVER waits for this. Records are queued after the fact; the
 *     diagnostic path has no dependency on the network in either direction.
 *  2. Nothing is ever deleted after upload. A health worker returning to a
 *     village needs the local history whether or not they have signal.
 *  3. Failures stay visible. A failed record is marked FAILED and counted in
 *     the UI rather than silently retried into oblivion.
 */
class SyncManager(
    private val context: Context,
    private val history: ScanHistoryStore,
    private val connectivity: ConnectivityObserver,
    private val transport: SyncTransport,
    private val scope: CoroutineScope
) {

    private val baseUrlKey = stringPreferencesKey("clinic_base_url")
    private val tokenKey = stringPreferencesKey("clinic_token")
    private val deviceIdKey = stringPreferencesKey("device_id")

    private val _status = MutableStateFlow(
        SyncStatus(
            isOnline = false,
            clinic = null,
            pendingCount = 0,
            failedCount = 0,
            isSyncing = false,
            lastSyncedAtMillis = null
        )
    )
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val syncLock = Mutex()

    /**
     * Declared ABOVE init on purpose.
     *
     * Kotlin runs property initialisers and init blocks in declaration order,
     * so a flow declared below init is still null when an init coroutine
     * touches it - which crashed the app at launch with a NullPointerException
     * inside Flow.collect. Order is load-bearing here, not style.
     */
    val clinicFlow = context.syncDataStore.data.map { prefs ->
        prefs[baseUrlKey]?.let { url ->
            ClinicConnection(
                baseUrl = url,
                deviceId = prefs[deviceIdKey] ?: "",
                token = prefs[tokenKey]
            )
        }
    }

    init {
        scope.launch {
            // Resolve the clinic BEFORE touching _status. Written as
            // `_status.value.copy(clinic = loadClinic())` the receiver is read
            // first and the DataStore read then suspends, so this coroutine
            // would write back a status captured before the connectivity
            // collector below had run - silently reverting isOnline to false
            // and leaving the app claiming to be offline on a live network.
            val clinic = loadClinic()
            _status.update { it.copy(clinic = clinic) }
            refreshCounts()
        }
        scope.launch {
            connectivity.isOnline.collect { online ->
                _status.update { it.copy(isOnline = online) }
                // Coming back online is the natural moment to drain the queue -
                // that is the whole point of an offline-first field tool.
                if (online) syncNow()
            }
        }
    }

    // ------------------------------------------------------------ clinic

    private suspend fun loadClinic(): ClinicConnection? = clinicFlow.first()

    /**
     * Connects a clinic.
     *
     * The device id is generated once and kept, because the backend uses it to
     * attribute records to a device across sessions.
     */
    suspend fun connectClinic(baseUrl: String, token: String?) {
        context.syncDataStore.edit { prefs ->
            prefs[baseUrlKey] = baseUrl.trim()
            if (token.isNullOrBlank()) prefs.remove(tokenKey) else prefs[tokenKey] = token.trim()
            if (prefs[deviceIdKey].isNullOrBlank()) {
                prefs[deviceIdKey] = UUID.randomUUID().toString()
            }
        }
        val connected = loadClinic()
        _status.update { it.copy(clinic = connected, lastError = null) }
        syncNow()
    }

    suspend fun disconnectClinic() {
        context.syncDataStore.edit { prefs ->
            prefs.remove(baseUrlKey)
            prefs.remove(tokenKey)
        }
        _status.update { it.copy(clinic = null) }
    }

    // -------------------------------------------------------------- sync

    suspend fun refreshCounts() {
        val counts = history.countByState()
        _status.update {
            it.copy(
                pendingCount = counts[SyncState.PENDING] ?: 0,
                failedCount = counts[SyncState.FAILED] ?: 0
            )
        }
    }

    /** Drains the queue. Safe to call at any time; does nothing when it cannot run. */
    fun syncNow() {
        scope.launch { runSync() }
    }

    private suspend fun runSync() = syncLock.withLock {
        refreshCounts()

        val clinic = _status.value.clinic
        if (clinic == null || !clinic.isAuthenticated) return@withLock
        if (!connectivity.hasValidatedInternet()) return@withLock

        val queued = history.pending()
        if (queued.isEmpty()) return@withLock

        _status.update { it.copy(isSyncing = true, lastError = null) }

        val result = transport.push(clinic, queued.map(::toSyncItem))

        if (result.syncedLocalIds.isNotEmpty()) {
            history.markState(result.syncedLocalIds, SyncState.SYNCED)
        }
        // Anything the server did not acknowledge stays visible as FAILED
        // rather than quietly disappearing from the worker's count.
        val unacknowledged = queued.map { it.id } - result.syncedLocalIds.toSet()
        if (result.error != null && unacknowledged.isNotEmpty()) {
            history.markState(unacknowledged, SyncState.FAILED)
        }

        refreshCounts()
        _status.update {
            it.copy(
                isSyncing = false,
                lastError = result.error,
                lastSyncedAtMillis = if (result.error == null) {
                    System.currentTimeMillis()
                } else {
                    it.lastSyncedAtMillis
                }
            )
        }

        Log.i(
            TAG,
            "sync: accepted=${result.accepted} dup=${result.duplicates} failed=${result.failed}"
        )
    }

    /**
     * The record as the backend expects it.
     *
     * The fundus image is deliberately NOT included. It is the largest and most
     * identifying artefact the app holds, and a screening record is clinically
     * useful without it; sending images should be an explicit, separate
     * decision rather than a side effect of turning on sync.
     */
    private fun toSyncItem(record: ScanRecord) = SyncItem(
        localId = record.id,
        entityType = "screening",
        payloadJson = JSONObject().apply {
            put("local_id", record.id)
            put("captured_at", record.timestampMillis)
            put("grade", record.grade.grade)
            put("confidence", record.confidence.toDouble())
            put("explanation", record.explanation)
            put("eye", record.eye?.name?.lowercase() ?: "")
            put("patient_local_id", record.patientId ?: "")
            put("patient_name", record.patientName ?: "")
            put("model", "dr-v2")
            put("source", "retinasight-android")
        }.toString()
    )

    private companion object {
        const val TAG = "SyncManager"
    }
}
