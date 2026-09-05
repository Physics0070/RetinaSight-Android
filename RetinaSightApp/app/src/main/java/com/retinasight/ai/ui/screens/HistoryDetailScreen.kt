package com.retinasight.ai.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.core.history.ScanRecord
import com.retinasight.ai.core.patient.Eye
import com.retinasight.ai.ui.components.ConfidenceIndicator
import com.retinasight.ai.ui.components.SecondaryActionButton
import com.retinasight.ai.ui.components.SeverityBanner
import com.retinasight.ai.ui.theme.SeverityPalette
import java.text.DateFormat
import java.util.Date

/**
 * One past screening, in full.
 *
 * The list can only ever show a grade and a date. Everything the app actually
 * recorded - which eye, how sure it was, what it told the patient to do, and
 * the explanation that was read out at the time - lives here, so a technician
 * revisiting a village can answer "what did we tell this person, and when?"
 * without guessing from a coloured dot.
 *
 * Read-only by design. A past result is a record of what was said on the day;
 * editing it after the fact would make the history untrustworthy.
 */
@Composable
fun HistoryDetailScreen(
    record: ScanRecord,
    loadImage: suspend (ScanRecord) -> android.graphics.Bitmap?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Deleting a clinical record is not an undo-able tap, so it asks first.
    var confirmingDelete by remember { mutableStateOf(false) }

    // The photograph never leaves the phone, so it is read straight off local
    // storage here rather than being carried through navigation.
    val image by produceState<android.graphics.Bitmap?>(initialValue = null, record.id) {
        value = loadImage(record)
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
            SeverityBanner(grade = record.grade)

            Spacer(Modifier.height(18.dp))

            DetailCard {
                // Date, patient, eye and sync state each read as a complete
                // phrase on their own ("Right eye", "Waiting to send"), so they
                // are listed rather than labelled. The label/value row this
                // replaced gave a long label all the width and wrapped the
                // value one character per line.
                Text(
                    text = DateFormat
                        .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(record.timestampMillis)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                record.patientName?.takeIf { it.isNotBlank() }?.let { name ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = name, style = MaterialTheme.typography.bodyLarge)
                }
                record.eye?.let { eye ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            if (eye == Eye.RIGHT) R.string.eye_right else R.string.eye_left
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(record.syncState.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(16.dp))

            DetailCard {
                ConfidenceIndicator(
                    confidence = record.confidence,
                    band = record.confidenceBand
                )
            }

            Spacer(Modifier.height(16.dp))

            DetailCard {
                Text(
                    text = stringResource(R.string.result_urgency_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(SeverityPalette.colorFor(record.grade))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(record.urgency.labelRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // The description the patient was actually given, kept verbatim.
            if (record.explanation.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                DetailCard {
                    Text(
                        text = stringResource(R.string.result_explanation_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = record.explanation,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            image?.let { bitmap ->
                Spacer(Modifier.height(16.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                )
            }

            Spacer(Modifier.height(24.dp))

            if (confirmingDelete) {
                Text(
                    text = stringResource(R.string.history_delete_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                SecondaryActionButton(
                    text = stringResource(R.string.history_delete),
                    icon = Icons.Filled.DeleteForever,
                    onClick = onDelete
                )
                Spacer(Modifier.height(10.dp))
                // The safe choice is the one left selected by default.
                SecondaryActionButton(
                    text = stringResource(R.string.history_delete_cancel),
                    icon = Icons.Filled.Close,
                    onClick = { confirmingDelete = false }
                )
            } else {
                SecondaryActionButton(
                    text = stringResource(R.string.history_delete),
                    icon = Icons.Filled.DeleteOutline,
                    onClick = { confirmingDelete = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.result_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

