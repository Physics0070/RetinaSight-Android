package com.retinasight.ai.core.benchmark

import android.os.PowerManager

/** One instantaneous read of the device's power and thermal state. */
data class TelemetrySample(
    val elapsedRealtimeMs: Long,
    val currentMicroAmps: Long,
    val voltageMillivolts: Int,
    val chargeCounterMicroAmpHours: Long,
    val batteryPercent: Int,
    val batteryTemperatureC: Float,
    val thermalStatus: Int,
    val thermalHeadroom: Float,
    val isCharging: Boolean
) {
    /** Whole-device power draw in milliwatts, or NaN when unavailable. */
    val powerMilliwatts: Float
        get() = if (currentMicroAmps == DeviceTelemetry.UNAVAILABLE || voltageMillivolts <= 0) {
            Float.NaN
        } else {
            // uA * mV / 1e6 = mW
            currentMicroAmps.toFloat() * voltageMillivolts / 1_000_000f
        }
}

/** One screening in the simulated camp. */
data class ScreeningSample(
    val index: Int,
    val latencyMs: Double,
    val telemetry: TelemetrySample
)

/**
 * The published result.
 *
 * Energy is computed two independent ways so they can be sanity-checked
 * against each other:
 *   - the battery fuel gauge's own charge counter (primary, most direct)
 *   - integrating current x voltage over the run (secondary, cross-check)
 * If the two disagree badly, the device's current reporting is untrustworthy
 * and [warnings] says so instead of publishing a confident wrong number.
 */
data class BenchmarkReport(
    val screeningCount: Int,

    /**
     * Seconds allotted per patient during the run. 0 means back to back.
     *
     * Recorded because it decides which figures in this report mean anything:
     * latency is valid either way, energy and thermal only when the run
     * occupied realistic time.
     */
    val secondsPerPatient: Int,
    val batteryDesignCapacityMah: Int,

    // Latency
    val latencyMeanMs: Double,
    val latencyP50Ms: Double,
    val latencyP90Ms: Double,
    val latencyP99Ms: Double,
    val latencyMinMs: Double,
    val latencyMaxMs: Double,

    // Power
    val idlePowerMilliwatts: Float,
    val busyPowerMilliwatts: Float,
    val netInferencePowerMilliwatts: Float,

    // Energy and field metric
    val microAmpHoursPerScreening: Double,
    val milliwattHoursPerScreening: Double,

    /**
     * Capacity derived from the fuel gauge (charge counter / state of charge)
     * rather than the marketing spec. A cell that ships as "7000 mAh" reports
     * less than that in practice, and quoting the spec number inflates every
     * downstream figure.
     */
    val measuredCapacityMah: Int,

    /** Hours the phone can screen continuously on a full charge. */
    val continuousScreeningHours: Double,

    /**
     * Patients per charge at a realistic field duty cycle.
     *
     * THIS is the number a district health officer needs. The back-to-back
     * figure below is arithmetically true but describes a phone doing nothing
     * but inference forever, which is not what a camp looks like: most of each
     * patient's time is spent positioning the camera and reading the result.
     */
    val patientsPerChargeRealistic: Int,
    val secondsPerPatientAssumed: Int,

    /** Inference-bound upper bound. Reported for completeness, not as a claim. */
    val backToBackScreeningsPerCharge: Int,

    // Thermal
    val thermalCurve: List<ThermalPoint>,
    val throttleOnsetScreening: Int?,
    val batteryTempStartC: Float,
    val batteryTempEndC: Float,

    val warnings: List<String>
) {
    val throttled: Boolean get() = throttleOnsetScreening != null
}

/** One point on the thermal throttling curve. */
data class ThermalPoint(
    val screeningIndex: Int,
    val elapsedSeconds: Double,
    val thermalStatus: Int,
    val thermalHeadroom: Float,
    val batteryTemperatureC: Float
) {
    val statusLabel: String get() = thermalStatusLabel(thermalStatus)
}

fun thermalStatusLabel(status: Int): String = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> "NONE"
    PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
    PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
    PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
    PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
    PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
    PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
    else -> "UNSUPPORTED"
}
