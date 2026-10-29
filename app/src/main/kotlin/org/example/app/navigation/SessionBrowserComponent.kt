package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnPause
import com.arkivanov.essenty.lifecycle.doOnResume
import org.example.app.domain.session.ProcessingStatus
import org.example.app.domain.session.SessionRepository
import org.example.app.domain.upload.UploadStatusRepository
import org.example.app.domain.upload.UploadStatusValue

/**
 * §8.11 session browser: lists every recorded session (id, patient code, date, processing/
 * upload status, `recovered` flag) with actions to open the editor, reprocess, or go to the
 * upload screen. Metadata is re-read on demand ([refresh]) rather than cached — sessions are
 * infrequent and this keeps the list always consistent with `SessionRepository`/
 * `UploadStatusRepository`, the two authorities (§5.4), after a reprocess/edit round-trip.
 */
interface SessionBrowserComponent {
    val state: Value<State>

    fun onOpenEditor(folderName: String)
    fun onReprocess(folderName: String)
    fun onGoToUpload()
    fun onBack()

    /** Re-reads the rows. Runs by itself whenever navigation returns here from the editor,
     * processing or upload screens, so the list reflects whatever just changed. */
    fun refresh()

    /** Shows [message] as the one result line; set by the root when processing started from here
     * finishes, and cleared when the examiner leaves this screen (or takes the next action). */
    fun showResult(message: Message)

    /** What happened to the session the examiner just came back from (F-104). */
    sealed interface Message {
        /** Processing finished; [clips] = tasks with a clip or video (0 for questionnaire-only protocols). */
        data class Processed(val patientCode: String, val clips: Int) : Message

        /** Editor accepted with nothing changed, so nothing was reprocessed. */
        data object NoChanges : Message
    }

    data class Row(
        val folderName: String,
        val sessionId: String,
        val patientCode: String,
        val startedAt: String,
        val recovered: Boolean,
        val processingStatus: ProcessingStatus?,
        val uploadStatus: UploadStatusValue?,
    ) {
        /** Only a session that still needs processing offers it: failed, never done, or stuck
         * `Processing` (app closed mid-run). A processed or uploaded one has nothing to redo; edits
         * go through the editor, which reprocesses by itself. */
        val canReprocess: Boolean
            get() = uploadStatus != UploadStatusValue.Uploaded && processingStatus != ProcessingStatus.Done
    }

    data class State(val rows: List<Row> = emptyList(), val message: Message? = null)
}

class DefaultSessionBrowserComponent(
    componentContext: ComponentContext,
    private val sessionRepository: SessionRepository,
    private val uploadStatusRepository: UploadStatusRepository,
    private val onOpenEditorClicked: (folderName: String) -> Unit,
    private val onReprocessClicked: (folderName: String) -> Unit,
    private val onGoToUploadClicked: () -> Unit,
    private val onBackClicked: () -> Unit,
) : SessionBrowserComponent, ComponentContext by componentContext {

    private val _state = MutableValue(SessionBrowserComponent.State(rows = loadRows()))
    override val state: Value<SessionBrowserComponent.State> = _state

    init {
        // The browser stays on the stack under Editor/Processing/Upload; coming back to it resumes
        // it, which is when processing or upload status may have changed.
        lifecycle.doOnResume { refresh() }
        // The result line belongs to the visit that produced it: leaving Sessions (editor, upload,
        // processing, back) clears it. The root pops first and then shows the result on the resumed browser.
        lifecycle.doOnPause { _state.value = _state.value.copy(message = null) }
    }

    private fun loadRows(): List<SessionBrowserComponent.Row> =
        sessionRepository.listSessions()
            .sortedByDescending { it.startedAt }
            .map { summary ->
                SessionBrowserComponent.Row(
                    folderName = summary.folderName,
                    sessionId = summary.sessionId,
                    patientCode = summary.patientCode,
                    startedAt = summary.startedAt,
                    recovered = summary.recovered,
                    processingStatus = summary.processingStatus,
                    uploadStatus = uploadStatusRepository.read(summary.folderName)?.status,
                )
            }

    override fun refresh() {
        _state.value = _state.value.copy(rows = loadRows())
    }

    override fun showResult(message: SessionBrowserComponent.Message) {
        _state.value = _state.value.copy(message = message)
    }

    override fun onOpenEditor(folderName: String) {
        // Uploaded is terminal (§8.9): an edit could never reach the server.
        if (_state.value.rows.firstOrNull { it.folderName == folderName }?.uploadStatus == UploadStatusValue.Uploaded) return
        onOpenEditorClicked(folderName)
    }

    override fun onReprocess(folderName: String) = onReprocessClicked(folderName)

    override fun onGoToUpload() = onGoToUploadClicked()

    override fun onBack() = onBackClicked()
}
