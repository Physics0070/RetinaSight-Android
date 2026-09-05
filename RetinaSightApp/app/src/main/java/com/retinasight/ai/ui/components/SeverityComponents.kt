package com.retinasight.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.core.model.ConfidenceBand
import com.retinasight.ai.core.model.DrGrade
import com.retinasight.ai.ui.theme.SeverityPalette

/**
 * The headline of the result screen.
 *
 * Severity is communicated three ways at once - colour, icon and words - so it
 * survives colour blindness, bright sunlight, and not being able to read.
 */
@Composable
fun SeverityBanner(
    grade: DrGrade,
    modifier: Modifier = Modifier
) {
    val color = SeverityPalette.colorFor(grade)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = iconFor(grade),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(grade.labelRes),
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        GradeScale(grade)
    }
}

/** Five dots showing where this result sits on the scale. */
@Composable
private fun GradeScale(grade: DrGrade, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrGrade.entries.forEach { step ->
            val isCurrent = step == grade
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 18.dp else 12.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f)
                    )
            )
        }
    }
}

private fun iconFor(grade: DrGrade): ImageVector = when (grade) {
    DrGrade.NO_DR -> Icons.Filled.CheckCircle
    DrGrade.MILD -> Icons.Filled.Info
    DrGrade.MODERATE -> Icons.Filled.Warning
    DrGrade.SEVERE -> Icons.Filled.Warning
    DrGrade.PROLIFERATIVE -> Icons.Filled.Error
}

/**
 * Confidence as a bar plus a word.
 *
 * A percentage alone is meaningless to many users, so the band (High / Medium /
 * Low) is the primary signal and is what gets spoken aloud.
 */
@Composable
fun ConfidenceIndicator(
    confidence: Float,
    band: ConfidenceBand,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.result_confidence_label),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(bandLabel(band)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(confidence.coerceIn(0f, 1f))
                    .height(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

private fun bandLabel(band: ConfidenceBand): Int = when (band) {
    ConfidenceBand.HIGH -> R.string.confidence_high
    ConfidenceBand.MEDIUM -> R.string.confidence_medium
    ConfidenceBand.LOW -> R.string.confidence_low
}
