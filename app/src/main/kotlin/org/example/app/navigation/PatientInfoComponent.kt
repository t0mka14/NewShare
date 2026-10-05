package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import org.example.app.domain.config.PatientField
import org.example.app.domain.participant.FieldValidationError
import org.example.app.domain.participant.PatientFieldCatalogue
import org.example.app.domain.participant.ValidateParticipantInfoUseCase
import java.time.LocalDate

interface PatientInfoComponent {
    val state: Value<State>

    fun onFieldChanged(fieldName: String, value: String)
    fun onContinue()

    data class State(
        val fields: List<PatientField>,
        val values: Map<String, String> = emptyMap(),
        /** Errors currently shown. Pattern errors are live; `Required` only appears after Continue. */
        val errors: Map<String, List<FieldValidationError>> = emptyMap(),
        /** Whether every field currently validates — drives the Continue button's look, not its clickability. */
        val canContinue: Boolean = false,
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
 *
 * `current_date` fields (the catalogue's auto-filled date) start as [examinationDate] in ISO form
 * and ignore edits.
 */
class DefaultPatientInfoComponent(
    componentContext: ComponentContext,
    fields: List<PatientField>,
    /** Clinic-local date of the examination, for `current_date` fields. */
    examinationDate: LocalDate,
    private val validateParticipantInfoUseCase: ValidateParticipantInfoUseCase,
    private val onValidated: (Map<String, String>) -> Unit,
) : PatientInfoComponent, ComponentContext by componentContext {

    private val autoFilled: Map<String, String> = fields
        .filter { PatientFieldCatalogue.kindOf(it.name) == PatientFieldCatalogue.FieldKind.AutoDate }
        .associate { it.name to examinationDate.toString() }

    private val _state = MutableValue(
        PatientInfoComponent.State(
            fields = fields,
            values = autoFilled,
            canContinue = validateParticipantInfoUseCase.execute(fields, autoFilled).isEmpty(),
        )
    )
    override val state: Value<PatientInfoComponent.State> = _state

    /** Editing a field re-checks it live, except "not filled", which stays hidden until Continue. */
    override fun onFieldChanged(fieldName: String, value: String) {
        if (fieldName in autoFilled) return
        val current = _state.value
        val updatedValues = current.values + (fieldName to value)
        val allErrors = validateParticipantInfoUseCase.execute(current.fields, updatedValues)
        val liveErrors = allErrors[fieldName].orEmpty().filterNot { it is FieldValidationError.Required }
        _state.value = current.copy(
            values = updatedValues,
            errors = if (liveErrors.isEmpty()) current.errors - fieldName else current.errors + (fieldName to liveErrors),
            canContinue = allErrors.isEmpty(),
        )
    }

    override fun onContinue() {
        val current = _state.value
        val errors = validateParticipantInfoUseCase.execute(current.fields, current.values)
        _state.value = current.copy(errors = errors, canContinue = errors.isEmpty())
        if (errors.isEmpty()) {
            onValidated(current.values)
        }
    }
}
