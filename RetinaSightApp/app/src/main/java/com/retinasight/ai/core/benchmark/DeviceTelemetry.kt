package com.retinasight.ai.core.benchmark

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import kotlin.math.abs

/**
 * Reads the power and thermal signals Android actually exposes to an app.
 *
 * This is the measurement layer behind the field metrics we publish:
 * milliseconds per image, milliwatts, screenings per charge, and the thermal
 * throttling curve over a simulated camp.
 *
 * Three honest limitations are baked into the design rather than hidden:
 *
 *  1. Android reports WHOLE-DEVICE battery draw. There is no per-app or
 *     per-chip power rail. We therefore measure an idle baseline and report
 *     net power (busy - idle) so the number is attributable to inference.
 *
 *  2. BATTERY_PROPERTY_CURRENT_NOW is documented in microamperes, but some
 *     OEMs report milliamperes and some invert the sign. [readCurrentMicroAmps]
 *     normalises both, and [unitsLookSuspicious] flags when a one-time manual
 *     check on the real device is warranted.
 *
 *  3. getThermalHeadroom returns NaN if the device does not support it OR if
 *     it is polled faster than roughly once per second. We cache accordingly.
 */
class DeviceTelemetry(private val context: Context) {

    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var lastHeadroomValue: Float = Float.NaN
    private var lastHeadroomAtMs: Long = 0L

    fun sample(): TelemetrySample {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val voltageMv = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            ?.takeIf { it > 0 } ?: -1

        // EXTRA_TEMPERATURE is in tenths of a degree Celsius.
        val temperatureC = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
            ?.div(10f) ?: Float.NaN

        val isCharging = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            ?.let {
                it == BatteryManager.BATTERY_STATUS_CHARGING ||
                    it == BatteryManager.BATTERY_STATUS_FULL
            } ?: false

        return TelemetrySample(
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            currentMicroAmps = readCurrentMicroAmps(),
            voltageMillivolts = voltageMv,
            chargeCounterMicroAmpHours = readLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            batteryPercent = readLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toInt(),
            batteryTemperatureC = temperatureC,
            thermalStatus = readThermalStatus(),
            thermalHeadroom = readThermalHeadroom(),
            isCharging = isCharging
        )
    }

    /**
     * Discharge current as a POSITIVE microamp value.
     *
     * Android documents positive as "current into the battery" (charging), so a
     * discharging phone normally reports negative. Some OEMs invert this, and
     * some report milliamps. We take the magnitude and rescale if the value is
     * implausibly small for microamps.
     */
    private fun readCurrentMicroAmps(): Long {
        val raw = readLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == UNAVAILABLE) return UNAVAILABLE

        val magnitude = abs(raw)
        // A phone under load draws roughly 200-3000 mA = 200_000-3_000_000 uA.
        // A value in the hundreds or low thousands is therefore milliamps.
        return if (magnitude in 1..MILLIAMP_THRESHOLD) magnitude * 1000 else magnitude
    }

    /** True when the reported current looks like it needs a manual sanity check. */
    fun unitsLookSuspicious(): Boolean {
        val raw = readLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return raw == UNAVAILABLE || abs(raw) in 1..MILLIAMP_THRESHOLD
    }

    private fun readThermalStatus(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus
        } else {
            THERMAL_STATUS_UNSUPPORTED
        }

    /**
     * Predicted headroom before SEVERE throttling: 0.0 = cool, 1.0 = throttling.
     *
     * Polling faster than ~1 Hz makes this return NaN, so the value is cached
     * and refreshed at most once per second.
     */
    private fun readThermalHeadroom(): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Float.NaN

        val now = SystemClock.elapsedRealtime()
        if (now - lastHeadroomAtMs < HEADROOM_MIN_INTERVAL_MS && !lastHeadroomValue.isNaN()) {
            return lastHeadroomValue
        }

        val value = runCatching { powerManager.getThermalHeadroom(HEADROOM_FORECAST_SECONDS) }
            .getOrDefault(Float.NaN)

        lastHeadroomAtMs = now
        if (!value.isNaN()) lastHeadroomValue = value
        return value
    }

    private fun readLongProperty(property: Int): Long =
        runCatching { batteryManager.getLongProperty(property) }
            .getOrDefault(UNAVAILABLE)
            .let { if (it == Long.MIN_VALUE) UNAVAILABLE else it }

    companion object {
        const val UNAVAILABLE = Long.MIN_VALUE + 1
        const val THERMAL_STATUS_UNSUPPORTED = -1

        private const val MILLIAMP_THRESHOLD = 10_000L
        private const val HEADROOM_MIN_INTERVAL_MS = 1_000L
        private const val HEADROOM_FORECAST_SECONDS = 0
    }
}
