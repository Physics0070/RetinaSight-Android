package com.retinasight.ai.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.ui.components.LaserScanOverlay
import com.retinasight.ai.ui.components.OfflineBadge
import com.retinasight.ai.ui.components.RetinaScannerLogo

/**
 * Shown while inference runs.
 *
 * The screen never goes blank and always says the work is happening on the
 * phone - that reassurance is the point, not decoration.
 */
@Composable
fun AnalyzingScreen(isOnline: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "analyzing")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // The scanner mark with a beam running over it, rather than a
            // spinner. Inference is ~100 ms on this phone, so this is usually
            // seen for a moment - long enough to say what is happening, short
            // enough that it never becomes a wait.
            Box(contentAlignment = Alignment.Center) {
                RetinaScannerLogo(size = 168.dp)
                LaserScanOverlay(
                    isScanning = true,
                    modifier = Modifier
                        .size(168.dp)
                        .clip(RoundedCornerShape(32.dp))
                )
            }

            Spacer(Modifier.height(20.dp))

            Icon(
                imageVector = Icons.Filled.RemoveRedEye,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(40.dp)
                    .alpha(pulse)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.analyzing_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.analyzing_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            OfflineBadge(isOnline = isOnline)
        }
    }
}
