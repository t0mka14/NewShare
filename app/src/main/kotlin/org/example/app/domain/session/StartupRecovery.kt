package org.example.app.domain.session

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val logger = KotlinLogging.logger {}

/**
 * §8.4: runs [RecoverSessionsUseCase] once at startup, logs every outcome and keeps what the
 * examiner must be told until the main menu's one-time dialog dismisses it. Because [pending]
 * lives here (in the container) and not in the menu component, returning to the menu or
 * recreating it never brings the dialog back: [dismiss] clears it for the rest of the run.
 */
class StartupRecovery(
    private val recoverSessionsUseCase: RecoverSessionsUseCase,
    private val sessionRepository: SessionRepository,
) {
    /** One interrupted session, in the short human form the dialog lists. */
    data class Entry(
        val folderName: String,
        /** Participant identifier from the folder name; null when the session has none or can't be read. */
        val patientCode: String?,
        /** ISO instant from `examination.json`; null when unreadable (the dialog falls back to the folder name). */
        val startedAt: String?,
    )

    data class Summary(val recovered: List<Entry>, val failed: List<Entry>)

    private val _pending = MutableStateFlow<Summary?>(null)

    /** The summary still to show; null when there was nothing to report or it was dismissed. */
    val pending: StateFlow<Summary?> = _pending

    /** Never throws: a failing scan must not keep the app from starting. */
    fun run() {
        val outcomes = runCatching { recoverSessionsUseCase.recoverAll() }
            .onFailure { logger.warn(it) { "crash recovery scan failed" } }
            .getOrNull() ?: return
        publish(outcomes)
    }

    /** Logs [outcomes] and, if any session was recovered or failed, makes it [pending]. */
    fun publish(outcomes: List<RecoveryOutcome>) {
        _pending.value = report(outcomes, ::describe)
    }

    fun dismiss() {
        _pending.value = null
    }

    private fun describe(folderName: String): Entry {
        val examination = runCatching { sessionRepository.readExamination(folderName) }.getOrNull()
        val code = examination?.let { SessionFolderNaming.extractPatientCode(folderName, it.sessionId) }
        return Entry(folderName, code?.ifBlank { null }, examination?.startedAt)
    }

    companion object {
        /** Logs each outcome (nothing for [RecoveryOutcome.NotRecovered]) and maps them to a [Summary], or null if none needs reporting. */
        internal fun report(outcomes: List<RecoveryOutcome>, describe: (String) -> Entry): Summary? {
            val recovered = mutableListOf<Entry>()
            val failed = mutableListOf<Entry>()
            for (outcome in outcomes) {
                when (outcome) {
                    is RecoveryOutcome.NotRecovered -> Unit
                    // Never the folder name: it embeds the patient code (see StorageError).
                    is RecoveryOutcome.Recovered -> {
                        logger.info { "recovered interrupted session: sessionId=${outcome.sessionId}" }
                        recovered += describe(outcome.folderName)
                    }
                    is RecoveryOutcome.Failed -> {
                        logger.warn { "could not recover an interrupted session: error=${outcome.error}" }
                        failed += describe(outcome.folderName)
                    }
                }
            }
            return if (recovered.isEmpty() && failed.isEmpty()) null else Summary(recovered, failed)
        }
    }
}
