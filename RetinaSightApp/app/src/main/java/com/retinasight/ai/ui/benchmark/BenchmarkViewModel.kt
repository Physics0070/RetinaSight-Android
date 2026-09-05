package com.retinasight.ai.ui.benchmark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retinasight.ai.AppContainer
import com.retinasight.ai.core.benchmark.BenchmarkProgress
import com.retinasight.ai.core.benchmark.BenchmarkRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class BenchmarkViewModel(private val container: AppContainer) : ViewModel() {

    private val _progress = MutableStateFlow<BenchmarkProgress?>(null)
    val progress: StateFlow<BenchmarkProgress?> = _progress.asStateFlow()

    private val _exportedPath = MutableStateFlow<String?>(null)
    val exportedPath: StateFlow<String?> = _exportedPath.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var runJob: Job? = null

    /**
     * @param image the fundus photo to run repeatedly. A synthetic stand-in is
     *        used when the user has not captured one yet, so the benchmark is
     *        always runnable during a demo.
     */
    fun start(image: Bitmap?, languageTag: String, screenings: Int) {
        if (_isRunning.value) return

        val subject = image ?: syntheticFundus()
        _exportedPath.value = null
        _isRunning.value = true

        runJob = viewModelScope.launch {
            BenchmarkRunner(
                engine = container.inferenceEngine,
                telemetry = container.deviceTelemetry
            )
                .run(subject, languageTag, screenings)
                .flowOn(Dispatchers.Default)
                .collect { update ->
                    _progress.value = update
                    if (update is BenchmarkProgress.Complete ||
                        update is BenchmarkProgress.Aborted
                    ) {
                        _isRunning.value = false
                    }
                }
        }
    }

    fun cancel() {
        runJob?.cancel()
        runJob = null
        _isRunning.value = false
        _progress.value = null
    }

    fun export() {
        val report = (_progress.value as? BenchmarkProgress.Complete)?.report ?: return
        viewModelScope.launch {
            runCatching { container.benchmarkExporter.export(report) }
                .onSuccess { _exportedPath.value = it.absolutePath }
        }
    }

    /**
     * A stand-in fundus image: a dark circular field on black with a brighter
     * disc, so it exercises the same resize and preprocessing path as a real
     * photo rather than a flat colour that could be optimised away.
     */
    private fun syntheticFundus(size: Int = 1024): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val radius = size / 2f
        val retina = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                radius, radius, radius,
                intArrayOf(Color.rgb(190, 90, 40), Color.rgb(120, 40, 20), Color.BLACK),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(radius, radius, radius, retina)

        val opticDisc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(235, 200, 140)
        }
        canvas.drawCircle(radius * 1.45f, radius, radius * 0.13f, opticDisc)

        return bitmap
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { BenchmarkViewModel(container) }
        }
    }
}
