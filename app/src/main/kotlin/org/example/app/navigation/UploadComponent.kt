package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.upload.EligibleUpload
import org.example.app.domain.upload.EligibleUploadsQuery
import org.example.app.domain.upload.UploadSessionUseCase
import org.example.app.domain.upload.UploadStatusValue

/**
 * §8.9/§13 decision 34 upload screen: manual-only, sequential batch upload of every eligible
 * session ([EligibleUploadsQuery]). Progress aggregates completed-sessions fraction plus the
 * current session's own [UploadSessionUseCase] progress callback; a failed session shows its
 * localized reason and does **not** stop the batch (remaining sessions still upload). The list
 * is refreshed from the query after every session so uploaded sessions disappear immediately
 * (terminal, §8.9) and failed ones stay listed with an updated reason for manual retry.
 */
interface UploadComponent {
    val state: Value<State>

    fun onUploadClicked()
    fun onBack()

    data class Row(
        val folderName: String,
        val sessionId: String,
        val patientCode: String,
        val status: UploadStatusValue,
        /** Localized-string key for the last attempt's failure reason (§8.9), `null` if the
         * session has never been attempted or [status] isn't `Failed`. */
        val failureReasonKey: String?,
    )

    data class State(
        val rows: List<Row> = emptyList(),
        val uploading: Boolean = false,
        val overallProgress: Float = 0f,
        /** `"upload.successMessage"` once a batch finished with every session uploaded;
         * `null` before the first batch or once the examiner presses Upload again. */
        val batchResultKey: String? = null,
    )
}

class DefaultUploadComponent(
    componentContext: ComponentContext,
    private val eligibleUploadsQuery: EligibleUploadsQuery,
    private val uploadSessionUseCase: UploadSessionUseCase,
    private val dispatchers: CoroutineDispatchers,
    private val onBackClicked: () -> Unit,
) : UploadComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val _state = MutableValue(UploadComponent.State(rows = loadRows()))
    override val state: Value<UploadComponent.State> = _state

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

    private fun loadRows(): List<UploadComponent.Row> = eligibleUploadsQuery.eligibleSessions().map(::toRow)

    private fun toRow(upload: EligibleUpload): UploadComponent.Row = UploadComponent.Row(
        folderName = upload.folderName,
        sessionId = upload.sessionId,
        patientCode = upload.patientCode,
        status = upload.status,
        failureReasonKey = if (upload.status == UploadStatusValue.Failed) failureReasonKey(upload.failureReason) else null,
    )

    private fun failureReasonKey(reason: String?): String = when {
        reason == null -> "upload.error.generic"
        reason == EligibleUploadsQuery.INTERRUPTED_REASON -> "upload.error.interrupted"
        reason == "NetworkFailure" -> "upload.error.network"
        reason.startsWith("ServerError") -> "upload.error.server"
        reason.startsWith("Rejected") -> "upload.error.rejected"
        else -> "upload.error.generic"
    }

    override fun onUploadClicked() {
        if (_state.value.uploading) return
        val batch = _state.value.rows
        if (batch.isEmpty()) return

        _state.value = _state.value.copy(uploading = true, batchResultKey = null)
        scope.launch(dispatchers.main) {
            val total = batch.size
            batch.forEachIndexed { index, row ->
                uploadSessionUseCase.upload(row.folderName) { fraction ->
                    val overall = (index + fraction) / total
                    _state.value = _state.value.copy(overallProgress = overall)
                }
                val refreshed = loadRows()
                _state.value = _state.value.copy(rows = refreshed, overallProgress = (index + 1f) / total)
            }
            val allUploaded = _state.value.rows.isEmpty()
            _state.value = _state.value.copy(
                uploading = false,
                batchResultKey = if (allUploaded) "upload.successMessage" else null,
            )
        }
    }

    override fun onBack() = onBackClicked()
}
