package com.retinasight.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.core.sync.SyncStatus
import com.retinasight.ai.ui.components.BigActionButton
import com.retinasight.ai.ui.components.SecondaryActionButton

/**
 * Connect a clinic so completed screenings can be uploaded.
 *
 * The framing matters: this screen never gates anything. A health worker who
 * never opens it can screen patients all day. Connecting a clinic only adds
 * the ability to share results, which is why the subtitle says so explicitly
 * rather than making the user infer it.
 */
@Composable
fun ClinicScreen(
    status: SyncStatus,
    onConnect: (url: String, token: String) -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember(status.clinic) { mutableStateOf(status.clinic?.baseUrl.orEmpty()) }
    var token by remember(status.clinic) { mutableStateOf("") }

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
                text = stringResource(
                    if (status.isConnected) R.string.clinic_connected_title
                    else R.string.clinic_connect_title
                ),
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.clinic_subtitle_disconnected),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(20.dp))

            SyncStatusCard(status)

            Spacer(Modifier.height(20.dp))

            if (!status.isConnected) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.clinic_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.clinic_token)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                BigActionButton(
                    text = stringResource(R.string.clinic_connect),
                    icon = Icons.Filled.CloudUpload,
                    enabled = url.isNotBlank(),
                    onClick = { onConnect(url, token) }
                )
            } else {
                BigActionButton(
                    text = stringResource(
                        if (status.isSyncing) R.string.sync_sending else R.string.sync_now
                    ),
                    icon = Icons.Filled.CloudUpload,
                    enabled = !status.isSyncing && status.isOnline,
                    onClick = onSyncNow
                )
                Spacer(Modifier.height(12.dp))
                SecondaryActionButton(
                    text = stringResource(R.string.clinic_disconnect),
                    icon = Icons.Filled.LinkOff,
                    onClick = onDisconnect
                )
            }

            Spacer(Modifier.height(24.dp))

            // Stated plainly, because "we upload your screenings" and "we upload
            // photographs of your eye" are very different promises to a patient.
            Text(
                text = stringResource(R.string.clinic_note_image),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            status.lastError?.let { error ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Connection and queue state, readable at a glance. */
@Composable
fun SyncStatusCard(status: SyncStatus, modifier: Modifier = Modifier) {
    val container = when {
        !status.isOnline -> MaterialTheme.colorScheme.surfaceVariant
        status.failedCount > 0 -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = when {
                    !status.isOnline -> Icons.Filled.CloudOff
                    status.pendingCount == 0 -> Icons.Filled.CloudDone
                    else -> Icons.Filled.CloudUpload
                },
                contentDescription = null,
                modifier = Modifier.size(34.dp)
            )
            Column {
                Text(
                    text = when {
                        !status.isOnline -> stringResource(R.string.sync_offline)
                        status.pendingCount > 0 ->
                            stringResource(R.string.sync_pending_count, status.pendingCount)
                        else -> stringResource(R.string.sync_all_sent)
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                if (status.failedCount > 0) {
                    Text(
                        text = stringResource(R.string.sync_failed_count, status.failedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
