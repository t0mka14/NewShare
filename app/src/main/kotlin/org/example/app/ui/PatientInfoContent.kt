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
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.domain.participant.FieldValidationError
import org.example.app.navigation.PatientInfoComponent

/** §8.10 participant-info screen — fields are entirely config-driven (§6.2 `patientFields`). */
@Composable
fun PatientInfoContent(component: PatientInfoComponent, localization: UiLocalization, onBack: () -> Unit) {
    val state by component.state.subscribeAsState()

    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxWidth(0.65f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localization.resolve("patientInfo.title"), style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(32.dp))

            state.fields.forEach { field ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedTextField(
                        value = state.values[field.name].orEmpty(),
                        onValueChange = { component.onFieldChanged(field.name, it) },
                        label = { Text(localization.resolve(field.labelKey)) },
                        placeholder = field.placeholder?.let { { Text(it) } },
                        isError = state.errors[field.name].orEmpty().isNotEmpty(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(TestTags.PatientInfo.field(field.name)),
                    )
                    field.helpKey?.let { helpKey ->
                        Text(localization.resolve(helpKey), style = MaterialTheme.typography.caption)
                    }
                    state.errors[field.name].orEmpty().forEach { error ->
                        Text(
                            localization.resolve(error.messageKey()),
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.testTag(TestTags.PatientInfo.fieldError(field.name)),
                        )
                    }
                }
            }

            if (state.errors.isNotEmpty()) {
                Text(
                    localization.resolve("patientInfo.error.summary"),
                    color = MaterialTheme.colors.error,
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
}
