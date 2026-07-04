package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
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

    /** Called by `RootComponent` when navigation returns here from the editor/processing
     * screens, so the list reflects whatever just changed. */
    fun refresh()

    data class Row(
        val folderName: String,
        val sessionId: String,
        val patientCode: String,
        val startedAt: String,
        val recovered: Boolean,
        val processingStatus: ProcessingStatus?,
        val uploadStatus: UploadStatusValue?,
    )

    data class State(val rows: List<Row> = emptyList())
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
        _state.value = SessionBrowserComponent.State(rows = loadRows())
    }

    override fun onOpenEditor(folderName: String) = onOpenEditorClicked(folderName)

    override fun onReprocess(folderName: String) = onReprocessClicked(folderName)

    override fun onGoToUpload() = onGoToUploadClicked()

    override fun onBack() = onBackClicked()
}
