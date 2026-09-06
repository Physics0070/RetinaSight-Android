package com.retinasight.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.retinasight.ai.core.lang.AppLanguage
import com.retinasight.ai.core.lang.LanguageState
import com.retinasight.ai.core.lang.LocalizedContent
import com.retinasight.ai.ui.nav.RetinaNavHost
import com.retinasight.ai.ui.screens.LanguagePickerSheetContent
import com.retinasight.ai.ui.screens.LanguageScreen
import com.retinasight.ai.ui.theme.RetinaSightTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RetinaSightApplication).container

        setContent {
            val scope = rememberCoroutineScope()
            val languageState by container.languagePreferences.languageState
                .collectAsState(initial = LanguageState.Loading)
            var showLanguagePicker by remember { mutableStateOf(false) }
            val languageSheetState = rememberModalBottomSheetState()

            RetinaSightTheme {
                // Android 15+ forces edge-to-edge when targetSdk >= 35, so
                // content would otherwise draw underneath the status bar and
                // the gesture nav bar. Padding once here covers every screen.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                when (val state = languageState) {

                    // Still reading the stored choice. Showing anything else here
                    // would flash the picker at users who already chose.
                    LanguageState.Loading -> SplashScreen()

                    // First run: pre-highlight the phone's own language, but make
                    // the user confirm - we never assume on their behalf.
                    LanguageState.NotChosen -> LocalizedContent(AppLanguage.fromSystem()) {
                        LanguageScreen(
                            selected = null,
                            onSelect = { language ->
                                container.speechManager.speak(language.endonym, language)
                                scope.launch {
                                    container.languagePreferences.setLanguage(language)
                                }
                            }
                        )
                    }

                    is LanguageState.Chosen -> LocalizedContent(state.language) {
                        // The picker is drawn OVER the nav host, not instead of
                        // it. Swapping them out destroyed the back stack, so
                        // dismissing the picker rebuilt the graph at Home and
                        // the user lost the screen they opened it from.
                        Box(Modifier.fillMaxSize()) {
                            RetinaNavHost(
                                container = container,
                                language = state.language,
                                onChangeLanguage = { showLanguagePicker = true }
                            )

                            if (showLanguagePicker) {
                                // A sheet rather than a full screen: choosing a
                                // language is a detour, and the screen behind
                                // stays visible so it reads as one.
                                //
                                // It still renders OVER the nav host, so the
                                // back stack is intact. ModalBottomSheet also
                                // handles system back itself - dismissing the
                                // sheet rather than finishing the activity,
                                // which is what the old BackHandler was for.
                                ModalBottomSheet(
                                    onDismissRequest = { showLanguagePicker = false },
                                    sheetState = languageSheetState,
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    LanguagePickerSheetContent(
                                        selected = state.language,
                                        onSelect = { language ->
                                            container.speechManager.speak(
                                                language.endonym,
                                                language
                                            )
                                            scope.launch {
                                                container.languagePreferences
                                                    .setLanguage(language)
                                                showLanguagePicker = false
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only tear down speech when the app is really going away, not on a
        // rotation or other configuration change.
        if (isFinishing) {
            (application as RetinaSightApplication).container.speechManager.shutdown()
        }
    }
}

@Composable
private fun SplashScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
