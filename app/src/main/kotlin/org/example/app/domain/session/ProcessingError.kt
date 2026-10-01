package org.example.app.domain.session

/** Why [ProcessSessionUseCase] failed a session (§8.8). Every branch also marks
 * `examination.json.processing.status = Failed` before returning, when `examination.json`
 * itself could be loaded (§8.10). */
sealed interface ProcessingError {
    data class MissingExamination(val folderName: String) : ProcessingError
    object MissingTimeline : ProcessingError
    data class MissingConfigSnapshot(val detail: String) : ProcessingError
    data class ProtocolNotFound(val protocolName: String) : ProcessingError
    /** `participant.json` is missing or unreadable — its values feed `${field.<name>}`. */
    data class MissingParticipant(val folderName: String) : ProcessingError
    data class ClipPlanning(val errors: List<ClipPlanningError>) : ProcessingError
    /** A kept take's video could not be turned into an MP4 holding all of its frames. */
    data class VideoRemux(val taskIndex: Int, val repetition: Int, val detail: String) : ProcessingError
    data class IoFailure(val detail: String) : ProcessingError
}
