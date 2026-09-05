package com.retinasight.ai

import android.content.Context
import com.retinasight.ai.core.benchmark.BenchmarkExporter
import com.retinasight.ai.core.benchmark.DeviceTelemetry
import com.retinasight.ai.core.history.ScanHistoryStore
import com.retinasight.ai.core.inference.InferenceEngine
import com.retinasight.ai.core.inference.OnDeviceInferenceEngine
import com.retinasight.ai.core.patient.PatientStore
import com.retinasight.ai.core.lang.LanguagePreferences
import com.retinasight.ai.core.llm.LlmNarrator
import com.retinasight.ai.core.speech.SpeechManager
import com.retinasight.ai.core.sync.ConnectivityObserver
import com.retinasight.ai.core.sync.HttpSyncTransport
import com.retinasight.ai.core.sync.SyncManager
import kotlinx.coroutines.CoroutineScope

/**
 * Manual dependency container.
 *
 * A DI framework would add build complexity for no benefit at this size, so the
 * app wires itself here in one readable place.
 */
class AppContainer(context: Context, appScope: CoroutineScope) {

    private val appContext = context.applicationContext

    val languagePreferences = LanguagePreferences(appContext)
    val speechManager = SpeechManager(appContext)
    val historyStore = ScanHistoryStore(appContext)
    val patientStore = PatientStore(appContext)

    /**
     * Optional on-device narrator. Absent unless a model has been pushed to
     * the device; the app is fully functional without it.
     */
    val llmNarrator = LlmNarrator(appContext)

    /**
     * Clinic upload. Entirely separate from screening: the diagnostic path has
     * no reference to any of this, so it cannot come to depend on the network.
     */
    val connectivity = ConnectivityObserver(appContext)
    val syncManager = SyncManager(
        context = appContext,
        history = historyStore,
        connectivity = connectivity,
        transport = HttpSyncTransport(),
        scope = appScope
    )

    /** Power and thermal instrumentation behind the published field metrics. */
    val deviceTelemetry = DeviceTelemetry(appContext)
    val benchmarkExporter = BenchmarkExporter(appContext)

    /**
     * The real trained model: EfficientNet-B0 (456px, ordinal objective),
     * exported to ONNX as assets/dr-v2.onnx and run with ONNX Runtime.
     *
     * Swap back to MockInferenceEngine(appContext) only for UI work without
     * the model present.
     */
    val inferenceEngine: InferenceEngine = OnDeviceInferenceEngine(appContext)
}
