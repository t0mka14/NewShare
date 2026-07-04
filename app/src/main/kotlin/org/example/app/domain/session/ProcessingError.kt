package org.example.app.domain.session

/** Why [ProcessSessionUseCase] failed a session (§8.8). Every branch also marks
 * `examination.json.processing.status = Failed` before returning, when `examination.json`
 * itself could be loaded (§8.10). */
sealed interface ProcessingError {
    data class MissingExamination(val folderName: String) : ProcessingError
    object MissingTimeline : ProcessingError
    data class MissingConfigSnapshot(val detail: String) : ProcessingError
    data class ProtocolNotFound(val protocolName: String) : ProcessingError
    data class ClipPlanning(val errors: List<ClipPlanningError>) : ProcessingError
    data class IoFailure(val detail: String) : ProcessingError
}
