package com.retinasight.ai.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.core.quality.ImageQualityGate
import com.retinasight.ai.ui.components.BigActionButton
import com.retinasight.ai.ui.components.SecondaryActionButton

/**
 * Shown when the capture-quality gate rejects a photo.
 *
 * The point of this screen is that it is ACTIONABLE. It does not say "bad
 * image"; it says which of sharpness, lighting, framing or visibility failed
 * and what to physically do about it. A health worker can fix the photo in the
 * ten seconds they still have the patient in front of them.
 *
 * "Check it anyway" is deliberately offered but visually secondary. Refusing
 * outright would strand someone whose only camera produces marginal images;
 * the honest compromise is to warn clearly and let them decide.
 */
@Composable
fun QualityScreen(
    quality: ImageQualityGate.Result,
    onRetake: () -> Unit,
    onProceedAnyway: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.quality_title),
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.quality_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // ---- What to actually do about it ----
            quality.issues.forEach { issue ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        text = stringResource(issue.messageRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            ScoreBar(stringResource(R.string.quality_score_overall), quality.overall, 0.55f)
            ScoreBar(stringResource(R.string.quality_score_blur), quality.blur, 0.45f)
            ScoreBar(stringResource(R.string.quality_score_lighting), quality.lighting, 0.40f)
            ScoreBar(stringResource(R.string.quality_score_framing), quality.framing, 0.40f)
            ScoreBar(stringResource(R.string.quality_score_visibility), quality.visibility, 0.50f)

            Spacer(Modifier.height(24.dp))

            BigActionButton(
                text = stringResource(R.string.quality_retake),
                icon = Icons.Filled.PhotoCamera,
                onClick = onRetake
            )

            Spacer(Modifier.height(12.dp))

            SecondaryActionButton(
                text = stringResource(R.string.quality_use_anyway),
                icon = Icons.Filled.Visibility,
                onClick = onProceedAnyway
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * One quality dimension.
 *
 * The pass mark is drawn as a tick on the bar, so a failing score is visibly
 * short of a line rather than just "some amount of red".
 */
@Composable
private fun ScoreBar(
    label: String,
    score: Float,
    threshold: Float,
    modifier: Modifier = Modifier
) {
    val passed = score >= threshold
    val barColor = if (passed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(score * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = barColor
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score.coerceIn(0f, 1f))
                    .height(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
            // Pass mark
            Box(
                modifier = Modifier
                    .fillMaxWidth(threshold.coerceIn(0f, 1f))
                    .height(14.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(14.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }
        }
    }
}
