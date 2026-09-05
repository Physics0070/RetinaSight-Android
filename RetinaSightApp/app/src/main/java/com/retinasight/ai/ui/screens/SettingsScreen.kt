package com.retinasight.ai.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.core.lang.AppLanguage
import com.retinasight.ai.ui.components.SecondaryActionButton

/**
 * Settings.
 *
 * Deliberately short. The only setting most users will ever touch is language,
 * so it sits at the top; everything else is status, not configuration.
 */
@Composable
fun SettingsScreen(
    currentLanguage: AppLanguage,
    isModelReady: Boolean,
    isVoiceAvailable: Boolean,
    onChangeLanguage: () -> Unit,
    onTestVoice: () -> Unit,
    onInstallVoice: () -> Unit,
    onOpenBenchmark: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(Modifier.height(24.dp))

            // ---- Language ----
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            SecondaryActionButton(
                text = currentLanguage.endonym,
                icon = Icons.Filled.Language,
                onClick = onChangeLanguage
            )

            Spacer(Modifier.height(24.dp))

            // ---- Voice ----
            SecondaryActionButton(
                text = stringResource(R.string.settings_voice_test),
                icon = Icons.Filled.VolumeUp,
                onClick = onTestVoice
            )
            if (!isVoiceAvailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_voice_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.voice_install_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(10.dp))
                SecondaryActionButton(
                    text = stringResource(R.string.voice_install),
                    icon = Icons.Filled.Download,
                    onClick = onInstallVoice
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---- Field benchmark ----
            SecondaryActionButton(
                text = stringResource(R.string.benchmark_title),
                icon = Icons.Filled.Speed,
                onClick = onOpenBenchmark
            )

            Spacer(Modifier.height(24.dp))

            // ---- Model status ----
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_model_status),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            if (isModelReady) R.string.settings_model_ready
                            else R.string.settings_model_loading
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- About / disclaimer ----
            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.result_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
