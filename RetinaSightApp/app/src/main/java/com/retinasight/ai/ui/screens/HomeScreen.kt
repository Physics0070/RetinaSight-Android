package com.retinasight.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.core.sync.SyncStatus
import com.retinasight.ai.ui.components.BigActionButton
import com.retinasight.ai.ui.components.OfflineBadge
import com.retinasight.ai.ui.components.RetinaScannerLogo
import com.retinasight.ai.ui.components.SecondaryActionButton

/**
 * One screen, one obvious thing to do.
 *
 * Everything secondary is smaller and lower. A first-time user should not have
 * to decide anything except "check my eye".
 */
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(Modifier.fillMaxSize()) {

            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    modifier = Modifier.size(30.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                OfflineBadge(isOnline = syncStatus.isOnline)

                Spacer(Modifier.height(22.dp))

                // The scanning mark. It is an illustration and carries no result,
                // but it is the first thing anyone sees and it states the whole
                // proposition before a word of copy is read.
                RetinaScannerLogo(size = 148.dp)

                Spacer(Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(48.dp))

                BigActionButton(
                    text = stringResource(R.string.home_scan_button),
                    icon = Icons.Filled.RemoveRedEye,
                    onClick = onScan
                )

                Spacer(Modifier.height(16.dp))

                SecondaryActionButton(
                    text = stringResource(R.string.home_history_button),
                    icon = Icons.Filled.History,
                    onClick = onHistory
                )

            }
        }
    }
}
