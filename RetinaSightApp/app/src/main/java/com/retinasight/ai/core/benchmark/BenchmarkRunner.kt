package com.retinasight.ai.core.benchmark

import android.graphics.Bitmap
import android.os.PowerManager
import android.os.SystemClock
import com.retinasight.ai.core.inference.InferenceEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

sealed interface BenchmarkProgress {
    data class MeasuringBaseline(val secondsRemaining: Int) : BenchmarkProgress
    data class Screening(
        val done: Int,
        val total: Int,
        val lastLatencyMs: Double,
        val thermalStatus: Int,
        val batteryTemperatureC: Float
    ) : BenchmarkProgress
    data class Complete(val report: BenchmarkReport) : BenchmarkProgress
    data class Aborted(val reason: String) : BenchmarkProgress
}

/**
 * Runs a simulated screening camp on the device and produces the field numbers
 * a district health officer actually needs: time per screening, power draw,
 * how many patients one charge covers, and when the phone starts throttling.
 *
 * Method:
 *  1. Measure an idle baseline so power can be attributed to inference rather
 *     than to the whole phone being switched on.
 *  2. Run N screenings back to back through the real InferenceEngine.
 *  3. Sample the fuel gauge and thermal APIs throughout.
 *
 * The run refuses to start while charging, because the battery charge counter
 * moves the wrong way and every energy number would be meaningless.
 */
class BenchmarkRunner(
    private val engine: InferenceEngine,
    private val telemetry: DeviceTelemetry,
    private val batteryDesignCapacityMah: Int = DEFAULT_BATTERY_MAH
) {

    fun run(
        image: Bitmap,
        languageTag: String,
        screenings: Int = DEFAULT_SCREENINGS,
        secondsPerPatient: Int = DEFAULT_SECONDS_PER_PATIENT
    ): Flow<BenchmarkProgress> = flow {

        val warnings = mutableListOf<String>()

        val startSample = telemetry.sample()
        if (startSample.isCharging) {
            emit(
                BenchmarkProgress.Aborted(
                    "Unplug the charger first - energy numbers are invalid while charging."
                )
            )
            return@flow
        }
        if (telemetry.unitsLookSuspicious()) {
            warnings += "Battery current reporting on this device needs a one-time manual check " +
                "(some OEMs report mA where the API documents uA)."
        }

        // ---- 1. Idle baseline ----
        val idleReadings = mutableListOf<Float>()
        repeat(BASELINE_SECONDS) { second ->
            emit(BenchmarkProgress.MeasuringBaseline(BASELINE_SECONDS - second))
            delay(1000)
            telemetry.sample().powerMilliwatts.takeIf { !it.isNaN() }?.let(idleReadings::add)
        }
        val idlePower = idleReadings.averageOrNaN()

        // ---- 2. The camp ----
        engine.warmUp()

        val samples = mutableListOf<ScreeningSample>()
        val runStartMs = SystemClock.elapsedRealtime()
        val chargeAtStart = telemetry.sample().chargeCounterMicroAmpHours

        repeat(screenings) { i ->
            val t0 = SystemClock.elapsedRealtimeNanos()
            engine.analyze(image, languageTag)
            val latencyMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0

            val sample = telemetry.sample()
            samples += ScreeningSample(index = i + 1, latencyMs = latencyMs, telemetry = sample)

            emit(
                BenchmarkProgress.Screening(
                    done = i + 1,
                    total = screenings,
                    lastLatencyMs = latencyMs,
                    thermalStatus = sample.thermalStatus,
                    batteryTemperatureC = sample.batteryTemperatureC
                )
            )

            // Wait out the rest of this patient's slot.
            //
            // Without this the run is 100 inferences in eleven seconds, and
            // the battery, temperature and thermal-headroom readings describe
            // an eleven-second burst rather than a camp. Nothing gets warm in
            // eleven seconds, and the charge counter barely moves, so
            // patients-per-charge came out of an extrapolation rather than a
            // measurement - two runs of the same test disagreed by 40%.
            //
            // A real camp is roughly one patient every 30 seconds, most of it
            // spent talking to the patient rather than inferring. Idling
            // through that gap is what makes the energy delta real.
            if (secondsPerPatient > 0 && i < screenings - 1) {
                val remainingMs = secondsPerPatient * 1000L - latencyMs.toLong()
                if (remainingMs > 0) delay(remainingMs)
            }
        }

        val runEndMs = SystemClock.elapsedRealtime()
        val endSample = telemetry.sample()
        val chargeAtEnd = endSample.chargeCounterMicroAmpHours

        emit(
            BenchmarkProgress.Complete(
                buildReport(
                    samples = samples,
                    idlePowerMw = idlePower,
                    chargeAtStart = chargeAtStart,
                    chargeAtEnd = chargeAtEnd,
                    runWallMs = (runEndMs - runStartMs).toDouble(),
                    secondsPerPatient = secondsPerPatient,
                    startSample = startSample,
                    endSample = endSample,
                    warnings = warnings
                )
            )
        )
    }

    private fun buildReport(
        samples: List<ScreeningSample>,
        idlePowerMw: Float,
        chargeAtStart: Long,
        chargeAtEnd: Long,
        runWallMs: Double,
        secondsPerPatient: Int,
        startSample: TelemetrySample,
        endSample: TelemetrySample,
        warnings: MutableList<String>
    ): BenchmarkReport {

        val latencies = samples.map { it.latencyMs }.sorted()
        val count = samples.size

        val busyPower = samples
            .mapNotNull { sample -> sample.telemetry.powerMilliwatts.takeIf { !it.isNaN() } }
            .averageOrNaN()

        val netPower = if (busyPower.isNaN() || idlePowerMw.isNaN()) {
            Float.NaN
        } else {
            (busyPower - idlePowerMw).coerceAtLeast(0f)
        }

        // ---- Energy, method A: the fuel gauge's own charge counter ----
        val chargeUsable = chargeAtStart != DeviceTelemetry.UNAVAILABLE &&
            chargeAtEnd != DeviceTelemetry.UNAVAILABLE
        val consumedUah = if (chargeUsable) (chargeAtStart - chargeAtEnd).toDouble() else 0.0

        if (!chargeUsable) {
            warnings += "This device does not expose BATTERY_PROPERTY_CHARGE_COUNTER."
        } else if (consumedUah <= 0.0) {
            warnings += "Fuel gauge did not register a measurable drop over " + count +
                " screenings. Run more screenings for a trustworthy energy figure."
        }

        val uahPerScreening = if (consumedUah > 0) consumedUah / count else Double.NaN

        val meanVoltageV = samples
            .map { it.telemetry.voltageMillivolts }
            .filter { it > 0 }
            .takeIf { it.isNotEmpty() }
            ?.average()?.div(1000.0) ?: Double.NaN

        // uAh * V / 1000 = mWh
        val mwhPerScreening = if (uahPerScreening.isNaN() || meanVoltageV.isNaN()) {
            Double.NaN
        } else {
            uahPerScreening * meanVoltageV / 1000.0
        }

        // ---- Capacity: measured, not the marketing spec ----
        // charge_counter is uAh remaining at the current state of charge, so
        // dividing by that fraction recovers what the cell actually holds.
        val measuredCapacityUah = if (
            startSample.chargeCounterMicroAmpHours != DeviceTelemetry.UNAVAILABLE &&
            startSample.batteryPercent in 1..100
        ) {
            startSample.chargeCounterMicroAmpHours.toDouble() / (startSample.batteryPercent / 100.0)
        } else {
            batteryDesignCapacityMah * 1000.0
        }
        val measuredCapacityMah = (measuredCapacityUah / 1000.0).roundToInt()

        // ---- Runtime on one charge ----
        // Uses TOTAL device draw while screening, not net inference power: the
        // screen, camera and SoC are all on during a camp and all drain the
        // same battery. Subtracting idle here would overstate the range.
        val batteryEnergyMwh = if (meanVoltageV.isNaN()) {
            Double.NaN
        } else {
            measuredCapacityUah / 1000.0 * meanVoltageV
        }

        val continuousHours = if (
            batteryEnergyMwh.isNaN() || busyPower.isNaN() || busyPower <= 0f
        ) {
            Double.NaN
        } else {
            batteryEnergyMwh / busyPower
        }

        val patientsRealistic = if (continuousHours.isNaN()) {
            0
        } else {
            (continuousHours * 3600.0 / SECONDS_PER_PATIENT).roundToInt()
        }

        val backToBack = if (continuousHours.isNaN() || latencies.isEmpty()) {
            0
        } else {
            (continuousHours * 3_600_000.0 / latencies.average()).roundToInt()
        }

        if (consumedUah <= 0.0 && chargeUsable) {
            warnings += "Energy per screening is below the fuel gauge's resolution over " +
                count + " screenings. Latency and power are still valid; run 200 " +
                "screenings for a trustworthy per-screening energy figure."
        }

        // ---- Thermal ----
        val firstAtMs = samples.first().telemetry.elapsedRealtimeMs
        val curve = samples.map { s ->
            ThermalPoint(
                screeningIndex = s.index,
                elapsedSeconds = (s.telemetry.elapsedRealtimeMs - firstAtMs) / 1000.0,
                thermalStatus = s.telemetry.thermalStatus,
                thermalHeadroom = s.telemetry.thermalHeadroom,
                batteryTemperatureC = s.telemetry.batteryTemperatureC
            )
        }

        if (curve.all { it.thermalHeadroom.isNaN() }) {
            warnings += "getThermalHeadroom is unsupported on this device; the curve uses " +
                "thermal status and battery temperature only."
        }

        val throttleOnset = samples.firstOrNull {
            it.telemetry.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        }?.index

        return BenchmarkReport(
            screeningCount = count,
            secondsPerPatient = secondsPerPatient,
            batteryDesignCapacityMah = batteryDesignCapacityMah,
            latencyMeanMs = latencies.average(),
            latencyP50Ms = latencies.percentile(0.50),
            latencyP90Ms = latencies.percentile(0.90),
            latencyP99Ms = latencies.percentile(0.99),
            latencyMinMs = latencies.first(),
            latencyMaxMs = latencies.last(),
            idlePowerMilliwatts = idlePowerMw,
            busyPowerMilliwatts = busyPower,
            netInferencePowerMilliwatts = netPower,
            microAmpHoursPerScreening = uahPerScreening,
            milliwattHoursPerScreening = mwhPerScreening,
            measuredCapacityMah = measuredCapacityMah,
            continuousScreeningHours = continuousHours,
            patientsPerChargeRealistic = patientsRealistic,
            secondsPerPatientAssumed = SECONDS_PER_PATIENT,
            backToBackScreeningsPerCharge = backToBack,
            thermalCurve = curve,
            throttleOnsetScreening = throttleOnset,
            batteryTempStartC = startSample.batteryTemperatureC,
            batteryTempEndC = endSample.batteryTemperatureC,
            warnings = warnings
        )
    }

    companion object {
        /**
         * Seconds per patient. 30 matches a screening camp; 0 runs flat out,
         * which measures latency correctly and energy not at all.
         */
        const val DEFAULT_SECONDS_PER_PATIENT = 30

        const val DEFAULT_SCREENINGS = 100
        const val DEFAULT_BATTERY_MAH = 7000

        /**
         * Phone-on time per patient in a real camp: position the camera, take
         * the photo, read and speak the result. Inference is ~0.1 s of it.
         */
        const val SECONDS_PER_PATIENT = 30
        private const val BASELINE_SECONDS = 5
    }
}

private fun List<Float>.averageOrNaN(): Float =
    if (isEmpty()) Float.NaN else (sum() / size)

/** Nearest-rank percentile on an already-sorted list. */
private fun List<Double>.percentile(p: Double): Double {
    if (isEmpty()) return Double.NaN
    val rank = ceil(p * size).toInt().coerceIn(1, size)
    return this[rank - 1]
}
