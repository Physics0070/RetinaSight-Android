package com.retinasight.ai.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.core.history.ScanRecord
import com.retinasight.ai.ui.theme.SeverityPalette
import java.text.DateFormat
import java.util.Date

/**
 * Past checks, newest first, plus a compact progression strip.
 *
 * The progression strip is the point of this screen: a health worker revisiting
 * a village needs to see at a glance whether an eye is getting worse, without
 * reading any numbers.
 */
@Composable
fun HistoryScreen(
    records: List<ScanRecord>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(Modifier.height(16.dp))

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
                return@Column
            }

            ProgressionStrip(records)

            Spacer(Modifier.height(20.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(records) { record -> HistoryRow(record) }
            }
        }
    }
}

/** Oldest to newest, left to right, so the trend reads naturally. */
@Composable
private fun ProgressionStrip(records: List<ScanRecord>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.history_progression),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            records.reversed().takeLast(12).forEach { record ->
                // Bar height encodes severity: taller means worse.
                val heightFraction = (record.grade.grade + 1) / 5f
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height((16 + 56 * heightFraction).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SeverityPalette.colorFor(record.grade))
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(record: ScanRecord, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(SeverityPalette.colorFor(record.grade))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(record.grade.labelRes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = DateFormat
                        .getDateInstance(DateFormat.MEDIUM)
                        .format(Date(record.timestampMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
