package com.retinasight.ai.core.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * How queued records reach the clinic.
 *
 * Behind an interface for the same reason inference is: the app can be built,
 * demonstrated and reasoned about without a reachable server, and swapping the
 * transport changes no screen.
 */
interface SyncTransport {
    suspend fun push(clinic: ClinicConnection, items: List<SyncItem>): SyncResult
}

/**
 * Talks to the FastAPI backend's `POST /sync/push`.
 *
 * Uses HttpURLConnection rather than adding an HTTP client dependency - one
 * JSON POST does not justify pulling OkHttp and its transitive tree into an
 * APK that already carries a 15 MB model.
 *
 * The endpoint is idempotent on `local_id`, so a retry after a dropped
 * connection is safe and re-sent items come back as duplicates rather than
 * creating a second clinical record.
 */
class HttpSyncTransport : SyncTransport {

    override suspend fun push(
        clinic: ClinicConnection,
        items: List<SyncItem>
    ): SyncResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext SyncResult(0, 0, 0, emptyList())

        val body = JSONObject().apply {
            put("device_id", clinic.deviceId)
            put("items", JSONArray().apply {
                items.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("local_id", item.localId)
                            put("entity_type", item.entityType)
                            put("operation", item.operation)
                            put("payload", JSONObject(item.payloadJson))
                        }
                    )
                }
            })
        }.toString()

        val endpoint = clinic.baseUrl.trimEnd('/') + PUSH_PATH
        var connection: HttpURLConnection? = null

        return@withContext runCatching {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                clinic.token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            connection!!.outputStream.use { it.write(body.toByteArray()) }

            val code = connection!!.responseCode
            if (code !in 200..299) {
                val detail = connection!!.errorStream
                    ?.bufferedReader()?.use(BufferedReader::readText)
                    .orEmpty()
                    .take(200)
                return@runCatching SyncResult.failure("HTTP $code ${detail.ifBlank { "" }}".trim())
            }

            val json = JSONObject(
                connection!!.inputStream.bufferedReader().use(BufferedReader::readText)
            )
            val results = json.optJSONArray("items") ?: JSONArray()

            // Duplicates count as synced: the server already has the record, so
            // leaving it queued would retry it forever.
            val done = buildList {
                for (i in 0 until results.length()) {
                    val entry = results.getJSONObject(i)
                    val status = entry.optString("status")
                    if (status == "accepted" || status == "duplicate") {
                        add(entry.optString("local_id"))
                    }
                }
            }

            SyncResult(
                accepted = json.optInt("accepted"),
                duplicates = json.optInt("duplicates"),
                failed = json.optInt("failed"),
                syncedLocalIds = done
            )
        }.getOrElse { error ->
            Log.w(TAG, "push failed", error)
            SyncResult.failure(error.message ?: "network error")
        }.also {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TAG = "HttpSyncTransport"
        const val PUSH_PATH = "/api/v1/sync/push"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
