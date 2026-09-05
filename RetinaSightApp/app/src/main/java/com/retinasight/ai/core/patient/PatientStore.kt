package com.retinasight.ai.core.patient

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Patients held on this device.
 *
 * Same JSON-file approach as the scan history and for the same reason: a few
 * hundred rows do not need SQL, and avoiding an annotation processor keeps the
 * build one moving part simpler.
 */
class PatientStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, "patients.json")

    suspend fun save(
        fullName: String,
        ageYears: Int?,
        sex: Sex,
        phone: String?,
        diabetes: DiabetesStatus,
        yearsSinceDiagnosis: Int?,
        consented: Boolean
    ): PatientRecord = withContext(Dispatchers.IO) {
        val record = PatientRecord(
            id = UUID.randomUUID().toString(),
            fullName = fullName.trim(),
            ageYears = ageYears,
            sex = sex,
            phone = phone?.trim()?.takeIf { it.isNotEmpty() },
            diabetes = diabetes,
            yearsSinceDiagnosis = yearsSinceDiagnosis,
            // Consent is stamped with a time, not a boolean: "when did this
            // person agree" is the question an audit actually asks.
            consentGivenAtMillis = if (consented) System.currentTimeMillis() else null,
            createdAtMillis = System.currentTimeMillis()
        )
        writeAll(readAll() + record)
        record
    }

    suspend fun all(): List<PatientRecord> = withContext(Dispatchers.IO) {
        readAll().sortedByDescending { it.createdAtMillis }
    }

    suspend fun byId(id: String): PatientRecord? = withContext(Dispatchers.IO) {
        readAll().firstOrNull { it.id == id }
    }

    private fun readAll(): List<PatientRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        PatientRecord(
                            id = o.getString("id"),
                            fullName = o.optString("name"),
                            ageYears = o.optInt("age", -1).takeIf { it >= 0 },
                            sex = enumOr(o.optString("sex"), Sex.UNSPECIFIED),
                            phone = o.optString("phone").takeIf { it.isNotEmpty() },
                            diabetes = enumOr(o.optString("diabetes"), DiabetesStatus.UNKNOWN),
                            yearsSinceDiagnosis = o.optInt("years", -1).takeIf { it >= 0 },
                            consentGivenAtMillis = o.optLong("consent_at", 0L).takeIf { it > 0L },
                            createdAtMillis = o.optLong("created_at", 0L)
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }  // a corrupt file must never block a screening
    }

    private inline fun <reified T : Enum<T>> enumOr(raw: String, fallback: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)

    private fun writeAll(records: List<PatientRecord>) {
        val array = JSONArray()
        records.forEach { r ->
            array.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("name", r.fullName)
                    put("age", r.ageYears ?: -1)
                    put("sex", r.sex.name)
                    put("phone", r.phone ?: "")
                    put("diabetes", r.diabetes.name)
                    put("years", r.yearsSinceDiagnosis ?: -1)
                    put("consent_at", r.consentGivenAtMillis ?: 0L)
                    put("created_at", r.createdAtMillis)
                }
            )
        }
        file.writeText(array.toString())
    }
}
