package org.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.domain.participant.FieldValidationError
import org.example.app.domain.participant.PatientFieldCatalogue
import org.example.app.domain.participant.PatientFieldCatalogue.FieldKind
import org.example.app.navigation.PatientInfoComponent

/**
 * §8.10 participant-info screen — the chosen protocol's `patientFields` (config alignment row 6).
 * Catalogue fields render per [PatientFieldCatalogue]: `current_date` as read-only text, `sex`
 * and `education` as a dropdown of fixed options, everything else as a text field.
 */
@Composable
fun PatientInfoContent(component: PatientInfoComponent, localization: UiLocalization, onBack: () -> Unit) {
    val state by component.state.subscribeAsState()

    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.contentWidth(1200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localization.resolve("patientInfo.title"), style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(32.dp))

            state.fields.forEach { field ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    val value = state.values[field.name].orEmpty()
                    when (val kind = PatientFieldCatalogue.kindOf(field.name)) {
                        FieldKind.AutoDate -> {
                            Text(localization.resolve(field.labelKey), style = MaterialTheme.typography.titleMedium)
                            Text(value, modifier = Modifier.testTag(TestTags.PatientInfo.field(field.name)))
                        }
                        is FieldKind.Choice -> {
                            Text(localization.resolve(field.labelKey), style = MaterialTheme.typography.titleMedium)
                            DropdownSelector(
                                triggerTag = TestTags.PatientInfo.field(field.name),
                                selectedLabel = if (value.isEmpty()) "" else
                                    localization.resolvePlain(PatientFieldCatalogue.optionLabelKey(field.name, value)),
                                items = kind.options,
                                itemLabel = { localization.resolvePlain(PatientFieldCatalogue.optionLabelKey(field.name, it)) },
                                itemTag = { TestTags.PatientInfo.fieldOption(field.name, it) },
                                onSelected = { component.onFieldChanged(field.name, it) },
                            )
                        }
                        FieldKind.Text -> OutlinedTextField(
                            value = value,
                            onValueChange = { component.onFieldChanged(field.name, it) },
                            label = { Text(localization.resolve(field.labelKey)) },
                            placeholder = field.placeholder.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                            isError = state.errors[field.name].orEmpty().isNotEmpty(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag(TestTags.PatientInfo.field(field.name)),
                        )
                    }
                    if (field.helpKey.isNotEmpty() && localization.resolvePlain(field.helpKey).isNotBlank()) {
                        Text(localization.resolve(field.helpKey), style = MaterialTheme.typography.bodySmall)
                    }
                    state.errors[field.name].orEmpty().forEach { error ->
                        Text(
                            localization.resolve(error.messageKey()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag(TestTags.PatientInfo.fieldError(field.name)),
                        )
                    }
                }
            }

            if (state.errors.isNotEmpty()) {
                Text(
                    localization.resolve("patientInfo.error.summary"),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(TestTags.PatientInfo.ERROR_TEXT),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onBack, modifier = Modifier.testTag(TestTags.PatientInfo.BACK_BUTTON)) {
                    Text(localization.resolve("action.back"))
                }
                Button(onClick = component::onContinue, modifier = Modifier.testTag(TestTags.PatientInfo.CONTINUE_BUTTON)) {
                    Text(localization.resolve("patientInfo.continueButton"))
                }
            }
        }
    }
}

private fun FieldValidationError.messageKey(): String = when (this) {
    is FieldValidationError.Required -> "patientInfo.error.required"
    is FieldValidationError.PatternMismatch -> "patientInfo.error.pattern"
    is FieldValidationError.NotAnOption -> "patientInfo.error.option"
}
