package com.retinasight.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.retinasight.ai.R
import com.retinasight.ai.core.patient.DiabetesStatus
import com.retinasight.ai.core.patient.Eye
import com.retinasight.ai.core.patient.Sex
import com.retinasight.ai.ui.components.BigActionButton
import com.retinasight.ai.ui.components.SecondaryActionButton

/** What the screen hands back when the worker continues. */
data class PatientEntry(
    val fullName: String,
    val ageYears: Int?,
    val sex: Sex,
    val phone: String?,
    val diabetes: DiabetesStatus,
    val yearsSinceDiagnosis: Int?,
    val eye: Eye
)

/**
 * Consent, patient details, and which eye - the worker portal's pre-capture
 * steps, on one scrollable screen.
 *
 * Two rules are enforced here rather than left to discipline:
 *
 *  1. CONSENT IS MANDATORY. Continue stays disabled until it is given, and
 *     "skip" skips the details, never the consent. The dashboard states the
 *     same rule: no image may be captured without it.
 *  2. Details are optional. A queue of forty people at a camp is a real
 *     constraint, and a worker who cannot skip typing will simply stop
 *     recording anything.
 */
@Composable
fun PatientScreen(
    onContinue: (PatientEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var consented by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(Sex.UNSPECIFIED) }
    var phone by remember { mutableStateOf("") }
    var diabetes by remember { mutableStateOf(DiabetesStatus.UNKNOWN) }
    var years by remember { mutableStateOf("") }
    var eye by remember { mutableStateOf(Eye.RIGHT) }

    fun entry(withDetails: Boolean) = PatientEntry(
        fullName = if (withDetails) name.trim() else "",
        ageYears = if (withDetails) age.toIntOrNull() else null,
        sex = if (withDetails) sex else Sex.UNSPECIFIED,
        phone = if (withDetails) phone.trim().takeIf { it.isNotEmpty() } else null,
        diabetes = if (withDetails) diabetes else DiabetesStatus.UNKNOWN,
        yearsSinceDiagnosis = if (withDetails) years.toIntOrNull() else null,
        eye = eye
    )

    val elevatedRisk = diabetes == DiabetesStatus.YES && (years.toIntOrNull() ?: 0) >= 10

    /**
     * "Continue to photo" promises a record with the patient in it, so it only
     * unlocks once there is one. Name and age are the two fields with no
     * sensible default; sex, diabetes and phone all have valid "not known" or
     * empty answers and are not held against the worker.
     *
     * Anyone who does not want to type takes the second button instead, which
     * is always available. This is a fork, not a gate - what it prevents is the
     * silent middle case, a record labelled as having details that has none.
     */
    val enteredAge = age.toIntOrNull()
    val detailsComplete = name.isNotBlank() && enteredAge != null && enteredAge in 1..120

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
            // ---------------- Step 1: consent ----------------
            StepLabel(stringResource(R.string.consent_step))
            Text(
                text = stringResource(R.string.consent_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.consent_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (consented) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The whole row toggles: a 48dp checkbox alone is a
                        // small target for someone holding a phone and a lens.
                        .toggleable(value = consented, onValueChange = { consented = it })
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = consented, onCheckedChange = null)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = stringResource(R.string.consent_checkbox),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ---------------- Step 2: details ----------------
            StepLabel(stringResource(R.string.patient_step))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.patient_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.patient_age)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(15) },
                    label = { Text(stringResource(R.string.patient_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1.4f)
                )
            }

            Spacer(Modifier.height(16.dp))
            ChoiceRow(
                label = stringResource(R.string.patient_sex),
                options = Sex.entries,
                selected = sex,
                labelOf = { stringResource(it.labelRes) },
                onSelect = { sex = it }
            )

            Spacer(Modifier.height(16.dp))
            ChoiceRow(
                label = stringResource(R.string.patient_diabetes),
                options = DiabetesStatus.entries,
                selected = diabetes,
                labelOf = { stringResource(it.labelRes) },
                onSelect = { diabetes = it }
            )

            if (diabetes == DiabetesStatus.YES) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = years,
                    onValueChange = { years = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.patient_years)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (elevatedRisk) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.patient_elevated_risk),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ---------------- Step 3: which eye ----------------
            StepLabel(stringResource(R.string.eye_step))
            Text(
                text = stringResource(R.string.eye_prompt),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(10.dp))
            ChoiceRow(
                label = "",
                options = Eye.entries,
                selected = eye,
                labelOf = { stringResource(it.labelRes) },
                onSelect = { eye = it }
            )

            Spacer(Modifier.height(28.dp))

            BigActionButton(
                text = stringResource(R.string.patient_continue),
                icon = Icons.Filled.ArrowForward,
                enabled = consented && detailsComplete,
                onClick = { onContinue(entry(withDetails = true)) }
            )

            // A disabled button with no reason given is the usual way this
            // screen gets abandoned. Say what is missing, but only once the
            // worker has consented and is actually looking at it.
            if (consented && !detailsComplete) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.patient_details_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(12.dp))

            // Skips the DETAILS, never the consent.
            SecondaryActionButton(
                text = stringResource(R.string.patient_skip),
                icon = Icons.Filled.RemoveRedEye,
                enabled = consented,
                onClick = { onContinue(entry(withDetails = false)) }
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun StepLabel(text: String) {
    if (text.isBlank()) return
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

/** A labelled row of mutually exclusive choices, sized for gloved thumbs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(6.dp))
        }
        // Wraps instead of overflowing: four options ("Prefer not to say",
        // Female, Male, Other) do not fit one portrait row, and the last one
        // was being clipped off-screen where nobody could tap it.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { option ->
                val text = labelOf(option)
                if (option == selected) {
                    Button(onClick = { onSelect(option) }) { Text(text) }
                } else {
                    OutlinedButton(onClick = { onSelect(option) }) { Text(text) }
                }
            }
        }
    }
}
