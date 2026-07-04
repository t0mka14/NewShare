package org.example.app.fakes

import org.example.app.domain.upload.UploadApi
import org.example.app.domain.upload.UploadResult
import java.nio.file.Path

/**
 * Scripted [UploadApi] for `UploadSessionUseCase` state-machine tests (§10.1, §8.9): returns
 * [nextResult] (default [UploadResult.Success] with a `null` server response) for every call
 * and records every call's arguments for assertions.
 *
 * [respondTo] scripts a per-`sessionId` outcome — needed for batch-upload scenarios (§10.3
 * workflow 8, §13 decisions 34/35) where one shared [nextResult] can't express "the first
 * session in the batch fails, the second succeeds"; a scripted sessionId takes priority over
 * [nextResult] for that call only.
 */
class FakeUploadApi : UploadApi {
    var nextResult: UploadResult = UploadResult.Success(serverResponse = null)
    val calls = mutableListOf<Call>()
    private val scriptedBySessionId = mutableMapOf<String, UploadResult>()

    /** Test setup: the call for [sessionId] returns [result] instead of [nextResult]. */
    fun respondTo(sessionId: String, result: UploadResult) {
        scriptedBySessionId[sessionId] = result
    }

    override suspend fun uploadSession(
        zip: Path,
        installationId: String,
        sessionId: String,
        zipSha256: String,
        onProgress: (Float) -> Unit,
    ): UploadResult {
        calls += Call(zip, installationId, sessionId, zipSha256)
        onProgress(1.0f)
        return scriptedBySessionId[sessionId] ?: nextResult
    }

    data class Call(val zip: Path, val installationId: String, val sessionId: String, val zipSha256: String)
}
