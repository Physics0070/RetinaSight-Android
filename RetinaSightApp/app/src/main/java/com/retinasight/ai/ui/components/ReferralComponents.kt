package com.retinasight.ai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.core.model.ConfidenceBand
import com.retinasight.ai.core.model.DrGrade
import com.retinasight.ai.core.model.RetinaResult
import com.retinasight.ai.core.model.Urgency
import com.retinasight.ai.ui.theme.SeverityPalette

/**
 * The action, given the largest and loudest treatment on the screen.
 *
 * The grade is what the model says; THIS is what the patient has to do about
 * it, and the two are not the same thing. The app refers at an expected grade
 * of 1.15, below the rounding point of 1.5, so a scan can read "Mild" and still
 * need an eye doctor. When that happens the reader is told *why* rather than
 * being left to reconcile a mild grade with an urgent instruction on their own.
 *
 * Nothing here is computed. [result] already carries the urgency and the
 * borderline flag, decided once in core - this only renders them.
 */
@Composable
fun ReferralBanner(
    result: RetinaResult,
    urgencyLabel: String,
    modifier: Modifier = Modifier
) {
    val colour = SeverityPalette.colorFor(result.grade)

    // Grade 4 breathes. The most urgent state is the one most likely to be read
    // at a glance across a table, and a static block is easy to skim past. It is
    // a slow opacity cycle, not a flash - nothing on a medical screen should
    // blink.
    val pulse = if (result.grade == DrGrade.PROLIFERATIVE) {
        val transition = rememberInfiniteTransition(label = "UrgentPulse")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.72f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulseAlpha"
        )
        alpha
    } else {
        1f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(pulse)
            .clip(RoundedCornerShape(20.dp))
            .background(colour)
            .padding(20.dp)
    ) {
        Text(
            text = stringResourceSafe(R.string.referral_action_label),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.85f)
        )

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (result.urgency == Urgency.URGENT || result.urgency == Urgency.IMMEDIATE) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = urgencyLabel,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        // Only when the referral is owed to the screening threshold rather than
        // to the grade itself. Without this line the screen contradicts itself.
        if (result.borderlineReferral) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResourceSafe(R.string.referral_borderline_note),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Confidence as three discrete segments rather than a continuous bar.
 *
 * A continuous bar invites reading the fill as a score, and this model's
 * confidence is *not* monotonic with accuracy - 0.90-0.95 is 97.6% correct
 * while 0.95-1.00 falls to 83.9%. Three lit segments say which band the scan
 * is in and refuse to imply more precision than that.
 *
 * The low band is amber and carries the retake advice, because at that level
 * the displayed grade was right only half the time.
 */
@Composable
fun ConfidenceSegments(
    band: ConfidenceBand,
    bandLabel: String,
    confidenceLabel: String,
    retakeAdvice: String?,
    modifier: Modifier = Modifier
) {
    val lit = when (band) {
        ConfidenceBand.LOW -> 1
        ConfidenceBand.MEDIUM -> 2
        ConfidenceBand.HIGH -> 3
    }
    val activeColour = when (band) {
        ConfidenceBand.LOW -> SeverityPalette.grade2      // amber, matched to caution
        ConfidenceBand.MEDIUM -> MaterialTheme.colorScheme.secondary
        ConfidenceBand.HIGH -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = confidenceLabel, style = MaterialTheme.typography.titleLarge)
            Text(
                text = bandLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = activeColour
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index < lit) activeColour
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        if (retakeAdvice != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SeverityPalette.containerFor(DrGrade.MODERATE))
                    .padding(12.dp)
            ) {
                Text(
                    text = retakeAdvice,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Local alias so this file reads the same as the rest of the UI layer. */
@Composable
private fun stringResourceSafe(id: Int): String =
    androidx.compose.ui.res.stringResource(id)
