package com.retinasight.ai.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.retinasight.ai.AppContainer
import com.retinasight.ai.R
import com.retinasight.ai.core.history.ScanRecord
import com.retinasight.ai.core.lang.AppLanguage
import com.retinasight.ai.ui.benchmark.BenchmarkScreen
import com.retinasight.ai.ui.benchmark.BenchmarkViewModel
import com.retinasight.ai.ui.scan.ScanUiState
import com.retinasight.ai.ui.scan.ScanViewModel
import com.retinasight.ai.ui.screens.AnalyzingScreen
import com.retinasight.ai.ui.screens.CaptureScreen
import com.retinasight.ai.ui.screens.ClinicScreen
import com.retinasight.ai.ui.screens.HistoryScreen
import com.retinasight.ai.ui.screens.HomeScreen
import com.retinasight.ai.ui.screens.PatientScreen
import com.retinasight.ai.ui.screens.QualityScreen
import com.retinasight.ai.ui.screens.ResultScreen
import com.retinasight.ai.ui.screens.SettingsScreen

/**
 * The whole navigation graph.
 *
 * The ScanViewModel is created here, above the graph, so capture, analysing and
 * result all read the same run without serialising a Bitmap into a nav argument.
 */
@Composable
fun RetinaNavHost(
    container: AppContainer,
    language: AppLanguage,
    onChangeLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val scanViewModel: ScanViewModel = viewModel(factory = ScanViewModel.factory(container))
    val benchmarkViewModel: BenchmarkViewModel =
        viewModel(factory = BenchmarkViewModel.factory(container))

    val scanState by scanViewModel.state.collectAsState()
    val isSaved by scanViewModel.isSaved.collectAsState()
    val narration by scanViewModel.narration.collectAsState()
    val isNarrating by scanViewModel.isNarrating.collectAsState()
    val isSpeaking by container.speechManager.isSpeaking.collectAsState()
    val isTtsReady by container.speechManager.isReady.collectAsState()
    val syncStatus by container.syncManager.status.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // A single home affordance for every destination, rather than a bespoke
    // bar on each screen. Hidden on Home itself, and while a screening is
    // actually running so nobody abandons a scan by reflex.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showHome = currentRoute != null &&
        currentRoute != Routes.HOME &&
        currentRoute != Routes.ANALYZING

    Box(modifier.fillMaxSize()) {

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {

        composable(Routes.HOME) {
            HomeScreen(
                onScan = {
                    scanViewModel.reset()
                    // Consent comes before any capture, so the flow starts here.
                    navController.navigate(Routes.PATIENT)
                },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onClinic = { navController.navigate(Routes.CLINIC) },
                syncStatus = syncStatus
            )
        }

        composable(Routes.PATIENT) {
            PatientScreen(
                onContinue = { entry ->
                    scanViewModel.startRun(entry)
                    navController.navigate(Routes.CAPTURE) {
                        popUpTo(Routes.PATIENT) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CAPTURE) {
            CaptureScreen(
                onImageReady = { bitmap ->
                    scanViewModel.analyze(bitmap, language)
                    navController.navigate(Routes.ANALYZING) {
                        popUpTo(Routes.CAPTURE) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ANALYZING) {
            AnalyzingScreen()

            // Move on as soon as inference finishes - the user never has to tap.
            LaunchedEffect(scanState) {
                when (scanState) {
                    is ScanUiState.Success -> navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.ANALYZING) { inclusive = true }
                    }
                    is ScanUiState.QualityRejected -> navController.navigate(Routes.QUALITY) {
                        popUpTo(Routes.ANALYZING) { inclusive = true }
                    }
                    is ScanUiState.Failure -> navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                    else -> Unit
                }
            }
        }

        composable(Routes.RESULT) {
            when (val state = scanState) {
                is ScanUiState.Success -> ResultScreen(
                    result = state.result,
                    capturedImage = scanViewModel.capturedImage,
                    isSaved = isSaved,
                    isSpeaking = isSpeaking,
                    onSpeak = { text -> container.speechManager.speak(text, language) },
                    onStopSpeaking = { container.speechManager.stop() },
                    onSave = { scanViewModel.save() },
                    eye = scanViewModel.patientEntry?.eye,
                    narratorAvailable = scanViewModel.isNarratorAvailable,
                    narration = narration,
                    isNarrating = isNarrating,
                    onNarrate = { gradeLabel, confidencePercent, urgencyLabel ->
                        scanViewModel.narrate(
                            grade = state.result.grade,
                            gradeLabel = gradeLabel,
                            confidencePercent = confidencePercent,
                            urgencyLabel = urgencyLabel,
                            language = language
                        )
                    },
                    onNewScan = {
                        container.speechManager.stop()
                        scanViewModel.reset()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )

                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.common_error),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        composable(Routes.HISTORY) {
            // Loaded fresh each time the screen opens; the list is small.
            val records by produceState(initialValue = emptyList<ScanRecord>()) {
                value = container.historyStore.all()
            }
            HistoryScreen(records = records)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                currentLanguage = language,
                isModelReady = container.inferenceEngine.isReady,
                isVoiceAvailable = isTtsReady &&
                    container.speechManager.isLanguageSupported(language),
                onChangeLanguage = onChangeLanguage,
                onOpenBenchmark = { navController.navigate(Routes.BENCHMARK) },
                onTestVoice = {
                    container.speechManager.speak(language.endonym, language)
                },
                onInstallVoice = {
                    runCatching {
                        context.startActivity(container.speechManager.installVoiceDataIntent())
                    }
                }
            )
        }

        composable(Routes.QUALITY) {
            when (val state = scanState) {
                is ScanUiState.QualityRejected -> QualityScreen(
                    quality = state.quality,
                    onRetake = {
                        // Keep the consent and patient details; only the photo
                        // was the problem.
                        navController.navigate(Routes.CAPTURE) {
                            popUpTo(Routes.QUALITY) { inclusive = true }
                        }
                    },
                    onProceedAnyway = {
                        scanViewModel.proceedDespiteQuality(language)
                        navController.navigate(Routes.ANALYZING) {
                            popUpTo(Routes.QUALITY) { inclusive = true }
                        }
                    }
                )
                else -> Unit
            }
        }

        composable(Routes.CLINIC) {
            ClinicScreen(
                status = syncStatus,
                onConnect = { url, token ->
                    scope.launch { container.syncManager.connectClinic(url, token) }
                },
                onDisconnect = {
                    scope.launch { container.syncManager.disconnectClinic() }
                },
                onSyncNow = { container.syncManager.syncNow() }
            )
        }

        composable(Routes.BENCHMARK) {
            BenchmarkScreen(
                viewModel = benchmarkViewModel,
                lastCapturedImage = scanViewModel.capturedImage,
                languageTag = language.tag
            )
        }
    }

    if (showHome) {
        Surface(
            onClick = {
                container.speechManager.stop()
                scanViewModel.reset()
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.HOME) { inclusive = true }
                }
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = stringResource(R.string.action_home),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
    }
}
