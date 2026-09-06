package com.retinasight.ai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.ui.theme.CalmingTeal
import com.retinasight.ai.ui.theme.DeepNavy
import com.retinasight.ai.ui.theme.LaserCyan
import kotlin.math.cos
import kotlin.math.sin

/**
 * The scanner mark on the home screen.
 *
 * A real fundus photograph inside a glass housing, with an instrument ring,
 * corner brackets, a centre reticle and a beam sweeping down it. The retina is
 * a genuine photograph rather than a drawing, because the app's whole claim is
 * that it reads real tissue and a stylised diagram undersells that.
 *
 * Two deliberate choices about which photograph:
 *
 *  - It is a **healthy** retina (grade 0 from the public APTOS set). Branding
 *    should not depict disease, and a diseased logo invites the reading that
 *    the app is showing somebody's finding.
 *  - **Nothing is marked on it.** The earlier version drew pulsing lesion rings;
 *    over a real photograph those would be a fabricated finding on real tissue.
 *    The reticle and ring say "this is being examined", which is true, and stop
 *    there.
 *
 * It is chrome. It never reads a patient image and never reflects a result -
 * it draws identically on every launch.
 */
@Composable
fun RetinaScannerLogo(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp
) {
    val transition = rememberInfiniteTransition(label = "RetinaScan")

    val sweepProgress by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserSweep"
    )

    // The instrument ring turns slowly and continuously, which is what makes the
    // mark read as an active instrument rather than a static icon.
    val ringAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingRotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(34.dp),
                    spotColor = CalmingTeal.copy(alpha = 0.45f)
                )
                .clip(RoundedCornerShape(34.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(HousingTop, DeepNavy, HousingBottom)
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            LaserCyan.copy(alpha = 0.75f),
                            CalmingTeal.copy(alpha = 0.30f),
                            Color.Transparent,
                            LaserCyan.copy(alpha = 0.45f)
                        )
                    ),
                    shape = RoundedCornerShape(34.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // The photograph itself, circular, inset so the housing frames it.
            Image(
                painter = painterResource(R.drawable.fundus_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.14f)
                    .clip(CircleShape)
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val centre = Offset(w / 2f, h / 2f)
                val discRadius = (w / 2f) - (w * 0.14f)

                // Vignette, so the photograph sits into the housing rather than
                // looking pasted on top of it.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, DeepNavy.copy(alpha = 0.55f)),
                        center = centre,
                        radius = discRadius
                    ),
                    radius = discRadius,
                    center = centre
                )

                drawInstrumentRing(centre, discRadius * 1.06f, ringAngle)
                drawReticle(centre, w)

                // The beam, clipped to the disc so it reads as light crossing
                // tissue rather than a line drawn over a picture.
                val beamY = centre.y - discRadius + (discRadius * 2f * sweepProgress)
                clipPath(circlePath(centre, discRadius)) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                LaserCyan.copy(alpha = 0.20f),
                                LaserCyan.copy(alpha = 0.42f),
                                LaserCyan.copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            startY = beamY - (h * 0.11f),
                            endY = beamY + (h * 0.11f)
                        ),
                        topLeft = Offset(0f, beamY - (h * 0.11f)),
                        size = Size(w, h * 0.22f)
                    )
                    drawLine(
                        color = LaserCyan,
                        start = Offset(0f, beamY),
                        end = Offset(w, beamY),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

    }
}

// Housing glass. Illustration only, not app state.
private val HousingTop = Color(0xFF102A5C)
private val HousingBottom = Color(0xFF04102E)

private fun circlePath(centre: Offset, radius: Float) =
    androidx.compose.ui.graphics.Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                left = centre.x - radius,
                top = centre.y - radius,
                right = centre.x + radius,
                bottom = centre.y + radius
            )
        )
    }

private inline fun DrawScope.clipPath(
    path: androidx.compose.ui.graphics.Path,
    block: DrawScope.() -> Unit
) {
    drawContext.canvas.save()
    drawContext.canvas.clipPath(path)
    block()
    drawContext.canvas.restore()
}

/** Broken arcs on a slowly turning ring - the instrument bezel. */
private fun DrawScope.drawInstrumentRing(centre: Offset, radius: Float, angle: Float) {
    val arcs = listOf(0f to 54f, 78f to 26f, 132f to 62f, 214f to 34f, 268f to 48f, 330f to 20f)
    for ((start, sweep) in arcs) {
        drawArc(
            color = LaserCyan.copy(alpha = 0.85f),
            startAngle = start + angle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
    drawCircle(
        color = CalmingTeal.copy(alpha = 0.35f),
        radius = radius,
        center = centre,
        style = Stroke(width = 1f)
    )
}

/** Corner brackets and a centre cross - the focus reticle. */
private fun DrawScope.drawReticle(centre: Offset, w: Float) {
    val half = w * 0.11f
    val arm = w * 0.035f
    val colour = Color.White.copy(alpha = 0.9f)
    val stroke = 2f

    val corners = listOf(
        Offset(centre.x - half, centre.y - half) to Pair(1f, 1f),
        Offset(centre.x + half, centre.y - half) to Pair(-1f, 1f),
        Offset(centre.x - half, centre.y + half) to Pair(1f, -1f),
        Offset(centre.x + half, centre.y + half) to Pair(-1f, -1f)
    )
    for ((corner, dir) in corners) {
        drawLine(
            colour,
            corner,
            Offset(corner.x + arm * dir.first, corner.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            colour,
            corner,
            Offset(corner.x, corner.y + arm * dir.second),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }

    // Fine crosshair through the centre, and a bead where they meet.
    val tick = w * 0.022f
    drawLine(
        LaserCyan.copy(alpha = 0.8f),
        Offset(centre.x - tick, centre.y),
        Offset(centre.x + tick, centre.y),
        strokeWidth = 1.5f
    )
    drawLine(
        LaserCyan.copy(alpha = 0.8f),
        Offset(centre.x, centre.y - tick),
        Offset(centre.x, centre.y + tick),
        strokeWidth = 1.5f
    )
    drawCircle(Color.White, radius = w * 0.008f, center = centre)

    // Faint graticule marks around the disc edge.
    val gradRadius = w * 0.40f
    for (i in 0 until 24) {
        val a = (i * 15f) * (Math.PI / 180f).toFloat()
        val inner = gradRadius * 0.95f
        drawLine(
            color = LaserCyan.copy(alpha = 0.16f),
            start = Offset(centre.x + cos(a) * inner, centre.y + sin(a) * inner),
            end = Offset(centre.x + cos(a) * gradRadius, centre.y + sin(a) * gradRadius),
            strokeWidth = 1f
        )
    }
}
