package org.example.app.domain.session

import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineRepository

private val logger = KotlinLogging.logger {}

/**
 * F-047: removes a session folder that never recorded anything. Entering calibration creates the
 * folder, so leaving before `SESSION_RECORDING_STARTED` (Back, or a microphone that never opened)
 * would otherwise leave an empty "Not processed" session in the browser for good.
 *
 * This is the only code that deletes session data, so it deletes only when every check proves the
 * session is empty: [active] is not it, `examination.json` is readable and untouched (not
 * recovered, no task records, not ended, not processed, no archive), `master/` has no audio frame,
 * and the event log and timeline hold nothing but task-screen entries. Anything else, including an
 * unreadable file, keeps the folder. The log line carries the sessionId only, never the folder name.
 */
class EmptySessionCleanup(
    private val sessionRepository: SessionRepository,
    private val timelineRepository: TimelineRepository,
) {
    /** Deletes [folderName] if it is provably empty and not the [active] session; returns whether it did. Never throws. */
    fun deleteIfEmpty(folderName: String, active: String? = null): Boolean = try {
        if (folderName == active) {
            false
        } else {
            val examination = sessionRepository.readExamination(folderName)
            if (examination == null || !isEmpty(folderName, examination)) {
                false
            } else {
                sessionRepository.deleteSessionDirectory(folderName)
                logger.info { "removed empty session: sessionId=${examination.sessionId}" }
                true
            }
        }
    } catch (e: Exception) {
        logger.warn { "could not check or remove an empty session: ${e::class.simpleName}" }
        false
    }

    /** Applies [deleteIfEmpty] to each of [folderNames] (the sessions recovery left untouched). */
    fun sweep(folderNames: List<String>, active: String? = null): Int =
        folderNames.count { deleteIfEmpty(it, active) }

    private fun isEmpty(folderName: String, examination: Examination): Boolean {
        if (examination.recovered || examination.tasks.isNotEmpty() || examination.endedAt != null ||
            examination.processing != null || sessionRepository.archiveExists(folderName)
        ) return false
        if (sessionRepository.hasMasterAudio(folderName)) return false
        if (timelineRepository.originalExists(folderName) || timelineRepository.editedExists(folderName)) return false
        val log = timelineRepository.readEventLog(folderName)
        return !log.tornLastLine && log.events.all { it.type == TimelineEventType.TASK_SCREEN_ENTERED }
    }
}
