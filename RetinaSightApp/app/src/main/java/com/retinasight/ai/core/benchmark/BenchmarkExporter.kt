package com.retinasight.ai.core.benchmark

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the benchmark out as CSV so the numbers can go straight into a slide
 * or a report without being retyped from a phone screen.
 *
 * Files land in the app's external files directory, which needs no permission
 * and is reachable over USB / MTP from a laptop.
 */
class BenchmarkExporter(private val context: Context) {

    suspend fun export(report: BenchmarkReport): File = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "retinasight_benchmark_$stamp.csv")

        file.writeText(buildCsv(report))
        file
    }

    fun buildCsv(report: BenchmarkReport): String = buildString {
        appendLine("# RetinaSight AI - on-device screening benchmark")
        appendLine("# device,${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("# android_sdk,${Build.VERSION.SDK_INT}")
        appendLine("# generated,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine()

        appendLine("metric,value,unit")
        appendLine("screenings,${report.screeningCount},count")
            // Reading the CSV without this cannot tell a paced camp
            // from a burst, and the energy rows mean different things.
            appendLine("seconds_per_patient,${report.secondsPerPatient},s")
        appendLine("battery_capacity_spec,${report.batteryDesignCapacityMah},mAh")
        appendLine("battery_capacity_measured,${report.measuredCapacityMah},mAh")
        appendLine("latency_mean,${report.latencyMeanMs.fmt()},ms")
        appendLine("latency_p50,${report.latencyP50Ms.fmt()},ms")
        appendLine("latency_p90,${report.latencyP90Ms.fmt()},ms")
        appendLine("latency_p99,${report.latencyP99Ms.fmt()},ms")
        appendLine("latency_min,${report.latencyMinMs.fmt()},ms")
        appendLine("latency_max,${report.latencyMaxMs.fmt()},ms")
        appendLine("power_idle_baseline,${report.idlePowerMilliwatts.fmt()},mW")
        appendLine("power_busy,${report.busyPowerMilliwatts.fmt()},mW")
        appendLine("power_net_inference,${report.netInferencePowerMilliwatts.fmt()},mW")
        appendLine("energy_per_screening,${report.microAmpHoursPerScreening.fmt()},uAh")
        appendLine("energy_per_screening,${report.milliwattHoursPerScreening.fmt()},mWh")
        appendLine("continuous_screening,${report.continuousScreeningHours.fmt()},hours")
        appendLine("patients_per_charge,${report.patientsPerChargeRealistic},count")
        appendLine("patients_per_charge_assumption,${report.secondsPerPatientAssumed},s_per_patient")
        appendLine("back_to_back_inference_limit,${report.backToBackScreeningsPerCharge},count")
        appendLine("battery_temp_start,${report.batteryTempStartC.fmt()},C")
        appendLine("battery_temp_end,${report.batteryTempEndC.fmt()},C")
        appendLine("throttle_onset_screening,${report.throttleOnsetScreening ?: "none"},index")
        appendLine()

        appendLine("# thermal throttling curve")
        appendLine("screening_index,elapsed_s,thermal_status,thermal_headroom,battery_temp_c")
        report.thermalCurve.forEach { p ->
            appendLine(
                listOf(
                    p.screeningIndex,
                    p.elapsedSeconds.fmt(),
                    p.statusLabel,
                    p.thermalHeadroom.fmt(),
                    p.batteryTemperatureC.fmt()
                ).joinToString(",")
            )
        }

        if (report.warnings.isNotEmpty()) {
            appendLine()
            appendLine("# warnings")
            report.warnings.forEach { appendLine("# $it") }
        }
    }
}

private fun Double.fmt(): String =
    if (isNaN()) "NA" else String.format(Locale.US, "%.2f", this)

private fun Float.fmt(): String =
    if (isNaN()) "NA" else String.format(Locale.US, "%.2f", this)
