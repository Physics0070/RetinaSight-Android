package com.retinasight.ai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retinasight.ai.core.patient.Eye
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * An anatomical eye, drawn on a Canvas and turned toward the eye being examined.
 *
 * It exists to remove a specific field error: a technician photographing the
 * wrong eye. Reading the words "right" and "left" on a form is easy to get
 * backwards in a hurry; a model that physically turns is not. Selecting an eye
 * rotates it, so the choice is visible without being read.
 *
 * Everything here is drawn with trigonometry on a Compose Canvas. There is no
 * 3D engine, no model file and no new dependency - which is what makes it
 * viable on the low-end phones this app targets.
 *
 * [eye] null means neutral gaze - nothing chosen yet.
 */
@Composable
fun InteractiveEye3D(
    eye: Eye?,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    enableInteractiveDrag: Boolean = true,
    enableSaccades: Boolean = true,
    showVessels: Boolean = true,
    onBlink: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    // The eye turns toward the side being examined. Signs are chosen so the
    // model looks the same way the patient's eye does from the operator's side.
    val targetRotation = when (eye) {
        Eye.LEFT -> 25f
        Eye.RIGHT -> -25f
        null -> 0f
    }

    val baseRotationY by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 120f),
        label = "BaseRotationY"
    )

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var saccadeX by remember { mutableFloatStateOf(0f) }
    var saccadeY by remember { mutableFloatStateOf(0f) }
    val blinkProgress = remember { Animatable(0f) }

    // Micro-saccades: the small involuntary movements a real eye makes. Without
    // them the drawing reads as a sticker rather than a living thing.
    LaunchedEffect(enableSaccades) {
        if (!enableSaccades) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2200, 4800))
            saccadeX = (Random.nextFloat() - 0.5f) * 8f
            saccadeY = (Random.nextFloat() - 0.5f) * 6f
            if (Random.nextFloat() < 0.35f) {
                blinkProgress.animateTo(1f, tween(110, easing = LinearEasing))
                blinkProgress.animateTo(0f, tween(160, easing = LinearEasing))
            }
            delay(Random.nextLong(300, 700))
            saccadeX = 0f
            saccadeY = 0f
        }
    }

    val totalRotationX = (dragOffsetY * 0.15f + saccadeY).coerceIn(-28f, 28f)
    val totalRotationY = (baseRotationY + dragOffsetX * 0.15f + saccadeX).coerceIn(-40f, 40f)

    val transition = rememberInfiniteTransition(label = "PupilBreath")
    val pupilDilation by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PupilDilation"
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 8.dp, shape = CircleShape, spotColor = ShadowNavy)
            .clip(CircleShape)
            .background(OrbitBackdrop)
            .clickable {
                scope.launch {
                    blinkProgress.animateTo(1f, tween(90))
                    blinkProgress.animateTo(0f, tween(140))
                    onBlink?.invoke()
                }
            }
            .then(
                if (enableInteractiveDrag) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { dragOffsetX = 0f; dragOffsetY = 0f },
                            onDragCancel = { dragOffsetX = 0f; dragOffsetY = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffsetX = (dragOffsetX + amount.x).coerceIn(-120f, 120f)
                                dragOffsetY = (dragOffsetY + amount.y).coerceIn(-80f, 80f)
                            }
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val radius = w / 2f
            val centre = Offset(w / 2f, h / 2f)

            // Sclera, lit from the upper left so the sphere reads as round
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, ScleraBody, ScleraMargin, ScleraShadow),
                    center = centre + Offset(-radius * 0.15f, -radius * 0.15f),
                    radius = radius
                ),
                radius = radius,
                center = centre
            )

            if (showVessels) drawScleraVessels(centre, radius)

            // The iris is displaced by the rotation, which is what sells depth
            val radX = totalRotationX * (PI / 180f).toFloat()
            val radY = totalRotationY * (PI / 180f).toFloat()
            val irisCentre = centre + Offset(
                x = sin(radY) * (radius * 0.52f),
                y = -sin(radX) * (radius * 0.52f)
            )
            val irisRadius = radius * 0.54f

            drawCircle(LimbalRing.copy(alpha = 0.9f), irisRadius + 2f, irisCentre)
            drawDeepIris(irisCentre, irisRadius)
            drawCircle(PupilBlack, irisRadius * 0.38f * pupilDilation, irisCentre)

            // Two corneal highlights: a broad wet gleam and a small hard catch
            val gleam = irisCentre + Offset(-irisRadius * 0.35f, -irisRadius * 0.35f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = gleam,
                    radius = irisRadius * 0.28f
                ),
                radius = irisRadius * 0.28f,
                center = gleam
            )
            val catch = irisCentre + Offset(irisRadius * 0.32f, irisRadius * 0.22f)
            drawCircle(Color.White.copy(alpha = 0.55f), irisRadius * 0.09f, catch)

            if (blinkProgress.value > 0f) {
                val lid = h * 0.5f * blinkProgress.value
                drawRect(
                    brush = Brush.verticalGradient(listOf(LidUpper, OrbitBackdrop)),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, lid)
                )
                drawLine(LashLine, Offset(0f, lid), Offset(w, lid), strokeWidth = 3f)
                drawRect(
                    brush = Brush.verticalGradient(listOf(OrbitBackdrop, LidUpper)),
                    topLeft = Offset(0f, h - lid),
                    size = Size(w, lid)
                )
                drawLine(LashLine, Offset(0f, h - lid), Offset(w, h - lid), strokeWidth = 2.5f)
            }
        }
    }
}

// Drawn anatomy. These describe a picture of an eye, not any app state, so they
// stay local rather than entering the theme.
private val ShadowNavy = Color(0x330A2463)
private val OrbitBackdrop = Color(0xFF0F172A)
private val ScleraBody = Color(0xFFF1F5F9)
private val ScleraMargin = Color(0xFFE2E8F0)
private val ScleraShadow = Color(0xFF94A3B8)
private val LimbalRing = Color(0xFF0F172A)
private val PupilBlack = Color(0xFF05070B)
private val LidUpper = Color(0xFF0A192F)
private val LashLine = Color(0xFF1E293B)
private val ConjunctivalVessel = Color(0xFFDC2626)
private val IrisPupilMargin = Color(0xFF247BA0)
private val IrisStriae = Color(0xFF0284C7)
private val IrisCiliary = Color(0xFF0A2463)
private val IrisLimbus = Color(0xFF031438)
private val FibreLight = Color(0xFF5EEAD4)
private val FibreDark = Color(0xFF00A896)

private fun DrawScope.drawScleraVessels(centre: Offset, radius: Float) {
    val vessel = ConjunctivalVessel.copy(alpha = 0.28f)

    drawPath(
        Path().apply {
            moveTo(centre.x - radius * 0.92f, centre.y - radius * 0.1f)
            quadraticTo(
                centre.x - radius * 0.65f, centre.y - radius * 0.25f,
                centre.x - radius * 0.45f, centre.y - radius * 0.18f
            )
            lineTo(centre.x - radius * 0.35f, centre.y - radius * 0.26f)
        },
        vessel,
        style = Stroke(width = 1.6f, cap = StrokeCap.Round)
    )
    drawPath(
        Path().apply {
            moveTo(centre.x - radius * 0.65f, centre.y - radius * 0.25f)
            quadraticTo(
                centre.x - radius * 0.55f, centre.y - radius * 0.4f,
                centre.x - radius * 0.42f, centre.y - radius * 0.45f
            )
        },
        vessel.copy(alpha = 0.20f),
        style = Stroke(width = 1.1f)
    )
    drawPath(
        Path().apply {
            moveTo(centre.x + radius * 0.90f, centre.y + radius * 0.15f)
            quadraticTo(
                centre.x + radius * 0.62f, centre.y + radius * 0.30f,
                centre.x + radius * 0.44f, centre.y + radius * 0.22f
            )
            lineTo(centre.x + radius * 0.35f, centre.y + radius * 0.32f)
        },
        vessel,
        style = Stroke(width = 1.5f, cap = StrokeCap.Round)
    )
    drawPath(
        Path().apply {
            moveTo(centre.x - radius * 0.2f, centre.y - radius * 0.88f)
            quadraticTo(
                centre.x - radius * 0.1f, centre.y - radius * 0.60f,
                centre.x - radius * 0.25f, centre.y - radius * 0.45f
            )
        },
        vessel.copy(alpha = 0.22f),
        style = Stroke(width = 1.2f)
    )
}

private fun DrawScope.drawDeepIris(irisCentre: Offset, irisRadius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(IrisPupilMargin, IrisStriae, IrisCiliary, IrisLimbus),
            center = irisCentre,
            radius = irisRadius
        ),
        radius = irisRadius,
        center = irisCentre
    )

    // Radial striae. 42 fibres is enough to read as texture without the draw
    // cost showing up on a cheap GPU.
    val fibreCount = 42
    for (i in 0 until fibreCount) {
        val angle = (i * (360f / fibreCount)) * (PI / 180f).toFloat()
        val inner = irisRadius * 0.38f
        val outer = irisRadius * (0.88f + (i % 3) * 0.04f)
        drawLine(
            color = if (i % 2 == 0) FibreLight.copy(alpha = 0.42f)
            else FibreDark.copy(alpha = 0.28f),
            start = Offset(
                irisCentre.x + cos(angle) * inner,
                irisCentre.y + sin(angle) * inner
            ),
            end = Offset(
                irisCentre.x + cos(angle) * outer,
                irisCentre.y + sin(angle) * outer
            ),
            strokeWidth = 1.2f,
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        color = FibreLight.copy(alpha = 0.22f),
        radius = irisRadius * 0.58f,
        center = irisCentre,
        style = Stroke(width = 2f)
    )
}
