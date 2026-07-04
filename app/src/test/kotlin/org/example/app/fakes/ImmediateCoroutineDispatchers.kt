package org.example.app.fakes

import kotlinx.coroutines.Dispatchers
import org.example.app.domain.CoroutineDispatchers

/**
 * [CoroutineDispatchers] for plain suspend-function/`Flow` use-case tests (§10.1) that just want
 * `runBlocking`/`.toList()` to run everything eagerly, with no manual scheduler advancement —
 * unlike [TestCoroutineDispatchers], which is for Compose/component tests that need to control
 * *when* background work runs. `ProcessSessionUseCase`/`UploadSessionUseCase` do no real
 * asynchronous I/O (everything is a blocking call wrapped in `withContext`/`flowOn`), so
 * `Dispatchers.Unconfined` everywhere is sufficient and keeps these tests simple.
 */
class ImmediateCoroutineDispatchers : CoroutineDispatchers {
    override val main = Dispatchers.Unconfined
    override val default = Dispatchers.Unconfined
    override val io = Dispatchers.Unconfined
}
