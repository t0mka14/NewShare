package org.example.app.fakes

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.example.app.domain.CoroutineDispatchers

/**
 * Deterministic [CoroutineDispatchers] for tests (§5.2, §10.3) — no real threads,
 * no real delays. All three dispatchers share one [TestCoroutineScheduler] so a
 * single `scheduler.advanceUntilIdle()` (or `runTest`'s own scheduler when
 * [scheduler] is left as the default and the test uses `TestScope`) drives
 * everything consistently.
 *
 * [main] is unconfined (mirrors `Dispatchers.Main` under `Dispatchers.setMain` in
 * real usage: UI-bound work that should run "immediately" from the test's point of
 * view); [default] and [io] are `StandardTestDispatcher`s so background work is
 * only executed when the test explicitly advances the scheduler — this is what
 * catches "forgot to await a coroutine" bugs instead of hiding them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestCoroutineDispatchers(
    val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
) : CoroutineDispatchers {
    override val main = UnconfinedTestDispatcher(scheduler)
    override val default = StandardTestDispatcher(scheduler)
    override val io = StandardTestDispatcher(scheduler)
}
