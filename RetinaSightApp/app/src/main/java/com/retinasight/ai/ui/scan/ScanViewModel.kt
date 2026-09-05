package com.retinasight.ai.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retinasight.ai.AppContainer
import com.retinasight.ai.core.lang.AppLanguage
import com.retinasight.ai.core.model.RetinaResult
import com.retinasight.ai.core.model.DrGrade
import com.retinasight.ai.core.patient.PatientRecord
import com.retinasight.ai.core.quality.ImageQualityGate
import com.retinasight.ai.ui.screens.PatientEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanUiState {
    data object Idle : ScanUiState

    /**
     * The capture-quality gate rejected the photo before it reached the model.
     *
     * This state exists because the model will ALWAYS return a grade - it has
     * no way to say "this is not a usable fundus image". Stopping here is what
     * turns a confident wrong answer into an actionable "take it again".
     */
    data class QualityRejected(val quality: ImageQualityGate.Result) : ScanUiState

    data object Analyzing : ScanUiState
    data class Success(val result: RetinaResult) : ScanUiState
    data class Failure(val cause: Throwable) : ScanUiState
}

/**
 * Owns one screening run, from captured image through to saved result.
 *
 * Scoped to the Activity so the capture, analysing and result screens all read
 * the same run without passing a Bitmap through navigation arguments.
 */
class ScanViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val _narration = MutableStateFlow<String?>(null)
    val narration: StateFlow<String?> = _narration.asStateFlow()

    private val _isNarrating = MutableStateFlow(false)
    val isNarrating: StateFlow<Boolean> = _isNarrating.asStateFlow()

    /** True only when a narrator model is actually installed on this device. */
    val isNarratorAvailable: Boolean get() = container.llmNarrator.isModelInstalled()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    /** The image this run is about. Shown behind the Grad-CAM overlay. */
    var capturedImage: Bitmap? = null
        private set

    /** Consent + details + eye for the run in progress. */
    var patientEntry: PatientEntry? = null
        private set

    private var savedPatient: PatientRecord? = null

    fun startRun(entry: PatientEntry) {
        patientEntry = entry
        savedPatient = null
    }

    /**
     * Screens an image, running the capture-quality gate first.
     *
     * @param skipQualityGate set when the user has seen the warning and chosen
     *        to continue anyway. The result is still real - the gate only
     *        advises - but they have been told the photo is marginal.
     */
    fun analyze(image: Bitmap, language: AppLanguage, skipQualityGate: Boolean = false) {
        capturedImage = image
        _isSaved.value = false

        if (!skipQualityGate) {
            val quality = ImageQualityGate.assess(image)
            if (!quality.isAcceptable) {
                _state.value = ScanUiState.QualityRejected(quality)
                return
            }
        }

        _state.value = ScanUiState.Analyzing

        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.Default) {
                    container.inferenceEngine.analyze(image, language.tag)
                }
            }
            _state.value = outcome.fold(
                onSuccess = { ScanUiState.Success(it) },
                onFailure = { ScanUiState.Failure(it) }
            )
        }
    }

    fun save() {
        val result = (_state.value as? ScanUiState.Success)?.result ?: return
        val image = capturedImage ?: return
        if (_isSaved.value) return

        viewModelScope.launch {
            runCatching {
                val entry = patientEntry
                // Persist the patient once per run, so both eyes of the same
                // visit attach to one person rather than two.
                val patient = savedPatient ?: entry
                    ?.takeIf { it.fullName.isNotBlank() }
                    ?.let {
                        container.patientStore.save(
                            fullName = it.fullName,
                            ageYears = it.ageYears,
                            sex = it.sex,
                            phone = it.phone,
                            diabetes = it.diabetes,
                            yearsSinceDiagnosis = it.yearsSinceDiagnosis,
                            consented = true
                        )
                    }
                savedPatient = patient

                container.historyStore.add(
                    result = result,
                    image = image,
                    patientId = patient?.id,
                    patientName = patient?.fullName,
                    eye = entry?.eye
                )
            }.onSuccess {
                _isSaved.value = true
                // Queue it for the clinic. Never blocks, never required.
                container.syncManager.syncNow()
            }
        }
    }

    /** Continue past a quality warning with the image already captured. */
    fun proceedDespiteQuality(language: AppLanguage) {
        val image = capturedImage ?: return
        analyze(image, language, skipQualityGate = true)
    }

    /**
     * Asks the on-device LLM to restate the result.
     *
     * Labels are passed in already resolved and localised: the model is given
     * finished facts to rephrase, never asked to interpret raw values.
     */
    fun narrate(
        grade: DrGrade,
        gradeLabel: String,
        confidencePercent: Int,
        urgencyLabel: String,
        language: AppLanguage
    ) {
        if (_isNarrating.value) return
        viewModelScope.launch {
            _isNarrating.value = true
            _narration.value = runCatching {
                container.llmNarrator.narrate(
                    grade, gradeLabel, confidencePercent, urgencyLabel, language
                )
            }.getOrNull()
            _isNarrating.value = false
        }
    }

    fun reset() {
        capturedImage = null
        _narration.value = null
        patientEntry = null
        savedPatient = null
        _isSaved.value = false
        _state.value = ScanUiState.Idle
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ScanViewModel(container) }
        }
    }
}
