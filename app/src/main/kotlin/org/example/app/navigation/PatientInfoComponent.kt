package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import org.example.app.domain.config.PatientField
import org.example.app.domain.participant.FieldValidationError
import org.example.app.domain.participant.ValidateParticipantInfoUseCase

interface PatientInfoComponent {
    val state: Value<State>

    fun onFieldChanged(fieldName: String, value: String)
    fun onContinue()

    data class State(
        val fields: List<PatientField>,
        val values: Map<String, String> = emptyMap(),
        val errors: Map<String, List<FieldValidationError>> = emptyMap(),
    ) {
        val isValid: Boolean get() = errors.isEmpty()
    }
}

/**
 * §8.10 participant-info screen: fields are entirely config-driven ([PatientField], §6.2).
 * Continue is gated on [ValidateParticipantInfoUseCase]; on success this hands the raw field
 * values to [onValidated] rather than calling `StartSessionUseCase` itself — session creation
 * (incl. the recorder format-negotiation preflight) is owned by [SessionComponent], per that
 * use case's own doc comment ("the caller ... is the Phase 2 UI's SessionComponent").
 */
class DefaultPatientInfoComponent(
    componentContext: ComponentContext,
    fields: List<PatientField>,
    private val validateParticipantInfoUseCase: ValidateParticipantInfoUseCase,
    private val onValidated: (Map<String, String>) -> Unit,
) : PatientInfoComponent, ComponentContext by componentContext {

    private val _state = MutableValue(PatientInfoComponent.State(fields = fields))
    override val state: Value<PatientInfoComponent.State> = _state

    override fun onFieldChanged(fieldName: String, value: String) {
        val updatedValues = _state.value.values + (fieldName to value)
        _state.value = _state.value.copy(
            values = updatedValues,
            errors = _state.value.errors - fieldName,
        )
    }

    override fun onContinue() {
        val current = _state.value
        val errors = validateParticipantInfoUseCase.execute(current.fields, current.values)
        _state.value = current.copy(errors = errors)
        if (errors.isEmpty()) {
            onValidated(current.values)
        }
    }
}
