package com.retinasight.ai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.ui.theme.LaserCyan

/**
 * Two eyelids that close over the retinal photograph.
 *
 * This is the privacy control, not an ornament. A vision centre is a crowded
 * room and the phone is often handed across a table, so hiding the image needs
 * to be one tap and needs to be unmistakable when it is on - a blank rectangle
 * would read as "the app broke".
 *
 * [isOpen] true leaves the image visible; false closes the lids over it.
 */
@Composable
fun EyelidShutter(
    isOpen: Boolean,
    modifier: Modifier = Modifier
) {
    val fraction by animateFloatAsState(
        targetValue = if (isOpen) 0f else 1f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "EyelidShutter"
    )

    if (fraction <= 0.01f) return

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cover = (h / 2f) * fraction

            val upper = Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w, cover)
                quadraticTo(w / 2f, cover + (15f * fraction), 0f, cover)
                close()
            }
            drawPath(
                path = upper,
                brush = Brush.verticalGradient(
                    colors = listOf(LidDeep, LidMid, LidEdge),
                    startY = 0f,
                    endY = cover + 15f
                )
            )
            drawPath(
                path = Path().apply {
                    moveTo(0f, cover)
                    quadraticTo(w / 2f, cover + (15f * fraction), w, cover)
                },
                color = LashMargin,
                style = Stroke(width = 2.5f)
            )

            val lowerStart = h - cover
            val lower = Path().apply {
                moveTo(0f, h)
                lineTo(w, h)
                lineTo(w, lowerStart)
                quadraticTo(w / 2f, lowerStart - (15f * fraction), 0f, lowerStart)
                close()
            }
            drawPath(
                path = lower,
                brush = Brush.verticalGradient(
                    colors = listOf(LidEdge, LidMid, LidDeep),
                    startY = lowerStart - 15f,
                    endY = h
                )
            )
            drawPath(
                path = Path().apply {
                    moveTo(0f, lowerStart)
                    quadraticTo(w / 2f, lowerStart - (15f * fraction), w, lowerStart)
                },
                color = LashMargin,
                style = Stroke(width = 2.5f)
            )
        }

        // Fades in only once the lids have essentially met, so the label does not
        // flicker during the close.
        if (fraction > 0.85f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.privacy_shutter_active),
                    color = LaserCyan.copy(alpha = ((fraction - 0.85f) * 6.6f).coerceIn(0f, 1f)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Anatomy of a drawn eyelid, not app state.
private val LidDeep = Color(0xFF0F172A)
private val LidMid = Color(0xFF1E293B)
private val LidEdge = Color(0xFF334155)
private val LashMargin = Color(0xFF475569)
