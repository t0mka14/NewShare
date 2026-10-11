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

    /** A text field lost focus: check it and show its format errors (§13 decision 56). */
    fun onFieldFocusLost(fieldName: String)

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
 *
 * `current_date` fields (the catalogue's auto-filled date) start as [examinationDate] in ISO form
 * and ignore edits.
 *
 * Errors show at two points (§13 decision 56): when a field loses focus (its format errors;
 * "required" only once Continue has been tried) and on Continue (every field). Editing a field
 * clears its errors until the next check, so nothing flickers while typing.
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

    private val _state = MutableValue(PatientInfoComponent.State(fields = fields, values = autoFilled))
    override val state: Value<PatientInfoComponent.State> = _state

    /** Set by the first Continue: from then on a focus-loss check also reports "required". */
    private var continueAttempted = false

    override fun onFieldChanged(fieldName: String, value: String) {
        if (fieldName in autoFilled) return
        val updatedValues = _state.value.values + (fieldName to value)
        _state.value = _state.value.copy(
            values = updatedValues,
            errors = _state.value.errors - fieldName,
        )
    }

    override fun onFieldFocusLost(fieldName: String) {
        if (fieldName in autoFilled) return
        val current = _state.value
        val fieldErrors = validateParticipantInfoUseCase.execute(current.fields, current.values)[fieldName].orEmpty()
            .filter { continueAttempted || it !is FieldValidationError.Required }
        _state.value = current.copy(
            errors = if (fieldErrors.isEmpty()) current.errors - fieldName else current.errors + (fieldName to fieldErrors),
        )
    }

    override fun onContinue() {
        continueAttempted = true
        val current = _state.value
        val errors = validateParticipantInfoUseCase.execute(current.fields, current.values)
        _state.value = current.copy(errors = errors)
        if (errors.isEmpty()) {
            onValidated(current.values)
        }
    }
}
