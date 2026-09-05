package com.retinasight.ai.ui.benchmark

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.core.benchmark.BenchmarkProgress
import com.retinasight.ai.core.benchmark.BenchmarkReport
import com.retinasight.ai.core.benchmark.ThermalPoint
import java.util.Locale

/**
 * The field benchmark.
 *
 * This is the screen that produces the numbers a district health officer needs
 * and that competing offline products do not publish: milliseconds per image,
 * milliwatts, screenings per charge, and the thermal curve over a full camp.
 */
@Composable
fun BenchmarkScreen(
    viewModel: BenchmarkViewModel,
    lastCapturedImage: Bitmap?,
    languageTag: String,
    modifier: Modifier = Modifier
) {
    val progress by viewModel.progress.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val exportedPath by viewModel.exportedPath.collectAsState()

    var screenings by remember { mutableIntStateOf(100) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.benchmark_title),
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.benchmark_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(20.dp))

            // ---- Camp size ----
            Text(
                text = stringResource(R.string.benchmark_count),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(50, 100, 200).forEach { option ->
                    val selected = screenings == option
                    if (selected) {
                        Button(onClick = { screenings = option }) { Text(option.toString()) }
                    } else {
                        OutlinedButton(
                            onClick = { screenings = option },
                            enabled = !isRunning
                        ) { Text(option.toString()) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (isRunning) {
                        viewModel.cancel()
                    } else {
                        viewModel.start(lastCapturedImage, languageTag, screenings)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    text = stringResource(
                        if (isRunning) R.string.benchmark_cancel else R.string.benchmark_run
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            when (val state = progress) {
                is BenchmarkProgress.MeasuringBaseline -> StatusCard(
                    stringResource(R.string.benchmark_baseline, state.secondsRemaining)
                )

                is BenchmarkProgress.Screening -> {
                    StatusCard(
                        stringResource(R.string.benchmark_progress, state.done, state.total)
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.done.toFloat() / state.total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${state.lastLatencyMs.f1()} ms  ·  ${state.batteryTemperatureC.f1()} °C",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                is BenchmarkProgress.Aborted -> StatusCard(
                    text = state.reason,
                    container = MaterialTheme.colorScheme.errorContainer
                )

                is BenchmarkProgress.Complete -> ReportView(
                    report = state.report,
                    exportedPath = exportedPath,
                    onExport = viewModel::export
                )

                null -> Unit
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusCard(
    text: String,
    container: Color = MaterialTheme.colorScheme.primaryContainer
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ReportView(
    report: BenchmarkReport,
    exportedPath: String?,
    onExport: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {

        // ---- Headline metric ----
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (report.patientsPerChargeRealistic > 0) {
                        report.patientsPerChargeRealistic.toString()
                    } else {
                        "—"
                    },
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.benchmark_patients_per_charge),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = stringResource(
                        R.string.benchmark_duty_cycle,
                        report.secondsPerPatientAssumed
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Section(stringResource(R.string.benchmark_section_latency)) {
            MetricRow(stringResource(R.string.benchmark_mean), "${report.latencyMeanMs.f1()} ms")
            MetricRow(stringResource(R.string.benchmark_p50), "${report.latencyP50Ms.f1()} ms")
            MetricRow(stringResource(R.string.benchmark_p90), "${report.latencyP90Ms.f1()} ms")
            MetricRow(stringResource(R.string.benchmark_p99), "${report.latencyP99Ms.f1()} ms")
        }

        Section(stringResource(R.string.benchmark_section_power)) {
            MetricRow(stringResource(R.string.benchmark_idle), "${report.idlePowerMilliwatts.f1()} mW")
            MetricRow(stringResource(R.string.benchmark_busy), "${report.busyPowerMilliwatts.f1()} mW")
            MetricRow(
                stringResource(R.string.benchmark_net),
                "${report.netInferencePowerMilliwatts.f1()} mW"
            )
        }

        Section(stringResource(R.string.benchmark_section_energy)) {
            MetricRow(
                stringResource(R.string.benchmark_per_screening),
                "${report.microAmpHoursPerScreening.f2()} µAh  ·  ${report.milliwattHoursPerScreening.f2()} mWh"
            )
            MetricRow(
                stringResource(R.string.benchmark_continuous_hours),
                "${report.continuousScreeningHours.f1()} h"
            )
            MetricRow(
                stringResource(R.string.benchmark_measured_capacity),
                "${report.measuredCapacityMah} mAh"
            )
            MetricRow(
                stringResource(R.string.benchmark_back_to_back),
                if (report.backToBackScreeningsPerCharge > 0) {
                    report.backToBackScreeningsPerCharge.toString()
                } else {
                    "—"
                }
            )
        }

        Section(stringResource(R.string.benchmark_section_thermal)) {
            MetricRow(
                stringResource(R.string.benchmark_temp_rise),
                "${report.batteryTempStartC.f1()} → ${report.batteryTempEndC.f1()} °C"
            )
            Spacer(Modifier.height(12.dp))
            ThermalCurveChart(report.thermalCurve)
            Spacer(Modifier.height(12.dp))
            Text(
                text = report.throttleOnsetScreening?.let {
                    stringResource(R.string.benchmark_throttle_at, it)
                } ?: stringResource(R.string.benchmark_throttle_none),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (report.throttled) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }

        if (report.warnings.isNotEmpty()) {
            Section(stringResource(R.string.benchmark_warnings)) {
                report.warnings.forEach { warning ->
                    Text(
                        text = "• $warning",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onExport,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            androidx.compose.material3.Icon(Icons.Filled.SaveAlt, contentDescription = null)
            Text(
                text = stringResource(R.string.benchmark_export),
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        exportedPath?.let { path ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.benchmark_exported, path),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Battery temperature across the camp, with the throttle onset marked.
 *
 * Drawn directly rather than pulled from a charting library: one line and one
 * marker does not justify a dependency.
 */
@Composable
private fun ThermalCurveChart(
    curve: List<ThermalPoint>,
    modifier: Modifier = Modifier
) {
    if (curve.size < 2) return

    val temps = curve.map { it.batteryTemperatureC }.filter { !it.isNaN() }
    if (temps.size < 2) return

    val minTemp = temps.min()
    val maxTemp = temps.max()
    val span = (maxTemp - minTemp).takeIf { it > 0.5f } ?: 1f

    val lineColor = MaterialTheme.colorScheme.primary
    val throttleColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(8.dp)
        ) {
            val w = size.width
            val h = size.height

            // Baseline grid
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = h * fraction
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            val path = Path()
            curve.forEachIndexed { index, point ->
                val temp = if (point.batteryTemperatureC.isNaN()) minTemp else point.batteryTemperatureC
                val x = w * index / (curve.size - 1).toFloat()
                val y = h - ((temp - minTemp) / span) * h
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))

            // Mark where throttling begins
            curve.indexOfFirst { it.thermalStatus >= 2 }.takeIf { it >= 0 }?.let { idx ->
                val x = w * idx / (curve.size - 1).toFloat()
                drawLine(throttleColor, Offset(x, 0f), Offset(x, h), strokeWidth = 3f)
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${minTemp.f1()} °C",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "1 → ${curve.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "${maxTemp.f1()} °C",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun Double.f1(): String =
    if (isNaN()) "—" else String.format(Locale.US, "%.1f", this)

private fun Double.f2(): String =
    if (isNaN()) "—" else String.format(Locale.US, "%.2f", this)

private fun Float.f1(): String =
    if (isNaN()) "—" else String.format(Locale.US, "%.1f", this)
