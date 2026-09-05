package com.retinasight.ai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.core.model.ConfidenceBand
import com.retinasight.ai.core.model.RetinaResult
import com.retinasight.ai.core.patient.Eye
import com.retinasight.ai.ui.components.ConfidenceIndicator
import com.retinasight.ai.ui.components.SecondaryActionButton
import com.retinasight.ai.ui.components.SeverityBanner
import com.retinasight.ai.ui.theme.SeverityPalette

/**
 * The result screen.
 *
 * It speaks automatically the first time it appears, because the user this app
 * is built for may not be able to read any of it.
 *
 * Every value shown here comes from [result], which comes from the model. There
 * is no path in this screen that can display a fixed or canned outcome.
 */
@Composable
fun ResultScreen(
    result: RetinaResult,
    capturedImage: Bitmap?,
    isSaved: Boolean,
    isSpeaking: Boolean,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onSave: () -> Unit,
    onNewScan: () -> Unit,
    eye: Eye?,
    narratorAvailable: Boolean,
    narration: String?,
    isNarrating: Boolean,
    onNarrate: (gradeLabel: String, confidencePercent: Int, urgencyLabel: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Default ON: the overlay is the app's answer to "why should I believe this",
    // and a toggle most users never find is the same as not having it.
    var showHeatmap by remember { mutableStateOf(true) }

    // The heat map is computed over the cropped, resized image the model saw,
    // so that is what must sit underneath it. Falling back to the original
    // photo would misplace the overlay.
    val baseImage = result.processedImage ?: capturedImage

    val gradeLabelText = stringResource(result.grade.labelRes)
    val urgencyLabelText = stringResource(result.urgency.labelRes)

    // A summary written to be HEARD, not the screen read out loud.
    //
    // Deliberately three facts and nothing else: what was found, how sure the
    // check is, and what to do. Headings, button labels and the disclaimer are
    // screen furniture - narrating them wastes the listener's attention on the
    // one channel a person who cannot read actually has.
    val sureText = stringResource(
        when (result.confidenceBand) {
            ConfidenceBand.HIGH -> R.string.speak_sure_high
            ConfidenceBand.MEDIUM -> R.string.speak_sure_medium
            ConfidenceBand.LOW -> R.string.speak_sure_low
        }
    )
    val spokenText = if (eye != null) {
        stringResource(
            R.string.speak_summary_eye,
            stringResource(eye.labelRes),
            gradeLabelText,
            sureText,
            urgencyLabelText
        )
    } else {
        stringResource(R.string.speak_summary, gradeLabelText, sureText, urgencyLabelText)
    }

    // Speak once when the result first appears.
    LaunchedEffect(result) {
        onSpeak(spokenText)
    }

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
            SeverityBanner(grade = result.grade)

            Spacer(Modifier.height(20.dp))

            ConfidenceIndicator(
                confidence = result.confidence,
                band = result.confidenceBand
            )

            Spacer(Modifier.height(24.dp))

            // ---- The eye image, with the Grad-CAM overlay toggle ----
            if (baseImage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    Image(
                        bitmap = baseImage.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (showHeatmap && result.heatmap != null) {
                        // The activation grid is only 15x15; High filtering
                        // interpolates it into a smooth field instead of
                        // showing the raw blocks.
                        Image(
                            bitmap = result.heatmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (result.heatmap != null) {
                    SecondaryActionButton(
                        text = stringResource(
                            if (showHeatmap) R.string.result_heatmap_hide
                            else R.string.result_heatmap_show
                        ),
                        icon = Icons.Filled.Visibility,
                        onClick = { showHeatmap = !showHeatmap }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.result_heatmap_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(Modifier.height(24.dp))
            }

            // ---- What to do ----
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SeverityPalette.containerFor(result.grade)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.result_urgency_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(result.urgency.labelRes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- What this means ----
            Text(
                text = stringResource(R.string.result_explanation_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(result.grade.descriptionRes),
                style = MaterialTheme.typography.bodyLarge
            )

            if (result.explanation.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = result.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // ---- Optional on-device restatement ----
            // Shown only when a narrator model is installed, and always BELOW
            // the advice above, which stays the authoritative text.
            if (narratorAvailable) {
                Spacer(Modifier.height(20.dp))

                if (narration == null) {
                    SecondaryActionButton(
                        text = stringResource(
                            if (isNarrating) R.string.narrate_working
                            else R.string.narrate_button
                        ),
                        icon = Icons.Filled.AutoAwesome,
                        enabled = !isNarrating,
                        onClick = {
                            onNarrate(
                                gradeLabelText,
                                (result.confidence * 100).toInt(),
                                urgencyLabelText
                            )
                        }
                    )
                } else {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                text = stringResource(R.string.narrate_heading),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = narration,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.narrate_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            SecondaryActionButton(
                text = stringResource(
                    if (isSpeaking) R.string.result_stop else R.string.result_listen
                ),
                icon = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                onClick = { if (isSpeaking) onStopSpeaking() else onSpeak(spokenText) }
            )

            Spacer(Modifier.height(12.dp))

            SecondaryActionButton(
                text = stringResource(
                    if (isSaved) R.string.result_saved else R.string.result_save
                ),
                icon = Icons.Filled.Save,
                onClick = onSave
            )

            Spacer(Modifier.height(12.dp))

            SecondaryActionButton(
                text = stringResource(R.string.result_new_scan),
                icon = Icons.Filled.Visibility,
                onClick = onNewScan
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.result_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
