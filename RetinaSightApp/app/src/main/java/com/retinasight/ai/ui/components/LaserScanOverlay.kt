package com.retinasight.ai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.retinasight.ai.ui.theme.CalmingTeal
import com.retinasight.ai.ui.theme.LaserCyan

/**
 * The beam that sweeps the image while the model runs.
 *
 * It replaces a spinner during inference. A spinner says "wait"; this says "the
 * retina is being read", which is what is actually happening - and on this phone
 * it is over in about 100 ms, so the sweep is usually seen once rather than
 * looped. It is decoration over the captured image and never alters it.
 */
@Composable
fun LaserScanOverlay(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isScanning) return

    val transition = rememberInfiniteTransition(label = "LaserScan")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Sweep"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val currentY = size.height * progress

        // Volumetric aura around the beam
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    CalmingTeal.copy(alpha = 0.13f),
                    LaserCyan.copy(alpha = 0.33f),
                    CalmingTeal.copy(alpha = 0.13f),
                    Color.Transparent
                ),
                startY = currentY - 70f,
                endY = currentY + 70f
            ),
            topLeft = Offset(0f, currentY - 70f),
            size = Size(w, 140f)
        )

        drawLine(
            color = LaserCyan,
            start = Offset(0f, currentY),
            end = Offset(w, currentY),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )

        drawCircle(Color.White, radius = 4f, center = Offset(w / 2f, currentY))
    }
}
