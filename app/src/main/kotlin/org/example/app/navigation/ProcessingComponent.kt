package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.session.ProcessSessionUseCase

/**
 * §8.8 processing progress screen: blocks navigation while [ProcessSessionUseCase] runs,
 * showing its step + fractional progress. On failure the examiner can retry (re-runs the whole
 * use case — it is idempotent per session) or go back (session stays `NotProcessed`/`Failed`,
 * reprocessable later from the session browser, §8.11).
 */
interface ProcessingComponent {
    val state: Value<State>

    fun onRetry()
    fun onBack()

    data class State(
        val step: ProcessSessionUseCase.Step? = null,
        val fraction: Float = 0f,
        val running: Boolean = true,
        val failed: Boolean = false,
    )
}

class DefaultProcessingComponent(
    componentContext: ComponentContext,
    private val folderName: String,
    private val processSessionUseCase: ProcessSessionUseCase,
    private val dispatchers: CoroutineDispatchers,
    private val onDone: (folderName: String) -> Unit,
    private val onBackClicked: () -> Unit,
) : ProcessingComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private var job: Job? = null

    private val _state = MutableValue(ProcessingComponent.State())
    override val state: Value<ProcessingComponent.State> = _state

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        start()
    }

    private fun start() {
        _state.value = ProcessingComponent.State(running = true, failed = false)
        job = scope.launch(dispatchers.main) {
            processSessionUseCase.process(folderName).collect { progress ->
                when (progress) {
                    is ProcessSessionUseCase.Progress.InProgress -> {
                        _state.value = _state.value.copy(step = progress.step, fraction = progress.fraction)
                    }

                    is ProcessSessionUseCase.Progress.Completed -> {
                        when (progress.outcome) {
                            is ProcessSessionUseCase.Outcome.Success -> {
                                _state.value = _state.value.copy(running = false, failed = false, fraction = 1f)
                                onDone(folderName)
                            }

                            is ProcessSessionUseCase.Outcome.Failed -> {
                                _state.value = _state.value.copy(running = false, failed = true)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onRetry() {
        if (_state.value.running) return
        start()
    }

    override fun onBack() = onBackClicked()
}
