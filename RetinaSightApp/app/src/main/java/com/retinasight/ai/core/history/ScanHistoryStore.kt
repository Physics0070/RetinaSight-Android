package com.retinasight.ai.core.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.retinasight.ai.core.model.DrGrade
import com.retinasight.ai.core.model.RetinaResult
import com.retinasight.ai.core.patient.Eye
import com.retinasight.ai.core.sync.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * On-device scan history.
 *
 * Implemented as a JSON file plus JPEGs in the app's private directory rather
 * than a database. That is a deliberate trade: it removes an annotation
 * processor from the build (one less thing to break under deadline) and the
 * data volume here - a few hundred rows at most - does not need SQL.
 */
class ScanHistoryStore(private val context: Context) {

    private val indexFile: File get() = File(context.filesDir, "scan_history.json")
    private val imageDir: File get() = File(context.filesDir, "scans").apply { mkdirs() }

    suspend fun add(
        result: RetinaResult,
        image: Bitmap,
        patientId: String? = null,
        patientName: String? = null,
        eye: Eye? = null
    ): ScanRecord = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val imageName = "$id.jpg"

        File(imageDir, imageName).outputStream().use { out ->
            image.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        val record = ScanRecord(
            id = id,
            timestampMillis = result.timestampMillis,
            grade = result.grade,
            confidence = result.confidence,
            explanation = result.explanation,
            imageFileName = imageName,
            expectedGrade = result.expectedGrade,
            patientId = patientId,
            patientName = patientName,
            eye = eye
        )

        val existing = readAll().toMutableList()
        existing.add(0, record) // newest first
        writeAll(existing)
        record
    }

    /** Newest first. */
    suspend fun all(): List<ScanRecord> = withContext(Dispatchers.IO) { readAll() }

    /** Records still waiting to reach the clinic, oldest first so the queue drains in order. */
    suspend fun pending(): List<ScanRecord> = withContext(Dispatchers.IO) {
        readAll().filter { it.syncState != SyncState.SYNCED }.reversed()
    }

    suspend fun countByState(): Map<SyncState, Int> = withContext(Dispatchers.IO) {
        readAll().groupingBy { it.syncState }.eachCount()
    }

    /**
     * Marks records as synced.
     *
     * Records are never deleted after upload - a health worker revisiting a
     * village needs the progression history on the device itself, with or
     * without a connection.
     */
    suspend fun markState(ids: Collection<String>, state: SyncState) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val idSet = ids.toSet()
        val updated = readAll().map { record ->
            if (record.id in idSet) record.copy(syncState = state) else record
        }
        writeAll(updated)
    }

    /**
     * Removes one screening, and the photograph with it.
     *
     * The image file goes too, deliberately. A record the worker has deleted
     * must not leave a fundus photograph of that patient sitting in storage -
     * deleting the index entry alone would keep the picture and lose the only
     * reference to it.
     *
     * Sync state is not consulted. A record already sent to the clinic still
     * disappears from this phone; the clinic's copy is the clinic's to manage.
     */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val remaining = readAll().filterNot { record ->
            if (record.id == id) {
                record.imageFileName?.let { name ->
                    runCatching { File(imageDir, name).delete() }
                }
                true
            } else {
                false
            }
        }
        writeAll(remaining)
    }

    suspend fun loadImage(record: ScanRecord): Bitmap? = withContext(Dispatchers.IO) {
        val name = record.imageFileName ?: return@withContext null
        val file = File(imageDir, name)
        if (!file.exists()) return@withContext null
        BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun readAll(): List<ScanRecord> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        ScanRecord(
                            id = o.getString("id"),
                            timestampMillis = o.getLong("timestamp"),
                            grade = DrGrade.fromInt(o.getInt("grade")),
                            confidence = o.getDouble("confidence").toFloat(),
                            explanation = o.optString("explanation", ""),
                            imageFileName = o.optString("image").takeIf { it.isNotEmpty() },
                            // Absent on records written before this was kept.
                            expectedGrade = if (o.has("expected_grade")) {
                                o.getDouble("expected_grade").toFloat()
                            } else {
                                null
                            },
                            patientId = o.optString("patient_id").takeIf { it.isNotEmpty() },
                            patientName = o.optString("patient_name").takeIf { it.isNotEmpty() },
                            eye = o.optString("eye").takeIf { it.isNotEmpty() }
                                ?.let { e -> runCatching { Eye.valueOf(e) }.getOrNull() },
                            syncState = runCatching {
                                SyncState.valueOf(o.optString("sync", SyncState.PENDING.name))
                            }.getOrDefault(SyncState.PENDING)
                        )
                    )
                }
            }
        }.getOrElse {
            // A corrupt history file must never crash the app or block a scan.
            emptyList()
        }
    }

    private fun writeAll(records: List<ScanRecord>) {
        val array = JSONArray()
        records.forEach { r ->
            array.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("timestamp", r.timestampMillis)
                    put("grade", r.grade.grade)
                    put("confidence", r.confidence.toDouble())
                    put("explanation", r.explanation)
                    put("image", r.imageFileName ?: "")
                    r.expectedGrade?.let { put("expected_grade", it.toDouble()) }
                    put("sync", r.syncState.name)
                    put("patient_id", r.patientId ?: "")
                    put("patient_name", r.patientName ?: "")
                    put("eye", r.eye?.name ?: "")
                }
            )
        }
        indexFile.writeText(array.toString())
    }
}
