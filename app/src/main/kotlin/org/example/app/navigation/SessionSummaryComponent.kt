package org.example.app.navigation

import org.example.app.domain.session.ProcessingStatus
import org.example.app.domain.session.SessionRepository

/**
 * Post-protocol summary (§8.6/§8.8). F-026: processing auto-advances here, so the summary states
 * the processing result read from the session's `examination.json`.
 */
interface SessionSummaryComponent {
    val result: Result

    sealed interface Result {
        /** Not `Done` (or unreadable): the summary makes no processing claim. */
        data object NotProcessed : Result

        /** Done, but nothing was cut (no-master questionnaire/info-only protocols). */
        data object ProcessedNoClips : Result

        data class ProcessedClips(val count: Int) : Result
    }
}

class DefaultSessionSummaryComponent(
    folderName: String,
    sessionRepository: SessionRepository,
) : SessionSummaryComponent {
    override val result: SessionSummaryComponent.Result = run {
        val examination = runCatching { sessionRepository.readExamination(folderName) }.getOrNull()
        if (examination?.processing?.status != ProcessingStatus.Done) {
            SessionSummaryComponent.Result.NotProcessed
        } else {
            val clips = examination.tasks.count { it.clipFile != null || it.videoFile != null }
            if (clips == 0) SessionSummaryComponent.Result.ProcessedNoClips else SessionSummaryComponent.Result.ProcessedClips(clips)
        }
    }
}
