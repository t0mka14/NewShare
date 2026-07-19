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
import org.example.app.domain.audio.AudioError
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.AudioPlaybackService
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.domain.audio.RecorderState
import org.example.app.domain.config.InfoTask
import org.example.app.domain.config.Question
import org.example.app.domain.config.QuestionType
import org.example.app.domain.config.QuestionnaireTask
import org.example.app.domain.config.Task
import org.example.app.domain.config.VocalTask
import org.example.app.domain.session.TaskRecord
import org.example.app.domain.timeline.TaskInstance
import org.example.app.domain.timeline.TimelineEventType
import java.nio.file.Path

/**
 * §8.6 task screen state machine for a VOCAL task instance. Exhaustive over
 * Idle/Capturing/Stopped/Failed — no `TODO()` branches.
 */
sealed interface TaskScreenState {
    data object Idle : TaskScreenState
    data object Capturing : TaskScreenState
    data object Stopped : TaskScreenState
    data class Failed(val error: AudioError) : TaskScreenState
}

/** Button enablement derived from [TaskScreenState] + task config (§8.6 table). */
data class TaskButtonState(
    val startEnabled: Boolean,
    val stopEnabled: Boolean,
    val repeatEnabled: Boolean,
    val nextEnabled: Boolean,
    val skipEnabled: Boolean,
) {
    companion object {
        fun of(state: TaskScreenState, canRepeat: Boolean, canSkip: Boolean): TaskButtonState =
            when (state) {
                is TaskScreenState.Idle -> TaskButtonState(
                    startEnabled = true,
                    stopEnabled = false,
                    repeatEnabled = false,
                    nextEnabled = false,
                    skipEnabled = canSkip,
                )

                is TaskScreenState.Capturing -> TaskButtonState(
                    startEnabled = false,
                    stopEnabled = true,
                    repeatEnabled = false,
                    nextEnabled = false,
                    skipEnabled = false,
                )

                is TaskScreenState.Stopped -> TaskButtonState(
                    startEnabled = false,
                    stopEnabled = false,
                    repeatEnabled = canRepeat,
                    nextEnabled = true,
                    skipEnabled = false,
                )

                is TaskScreenState.Failed -> TaskButtonState(
                    startEnabled = true,
                    stopEnabled = false,
                    repeatEnabled = false,
                    nextEnabled = false,
                    skipEnabled = false,
                )
            }
    }
}

/** One question's answer-in-progress (§8.6 QUESTIONNAIRE rendering). */
data class AnswerState(
    val selected: List<String> = emptyList(),
    val valid: Boolean = true,
)

interface TaskComponent {
    val state: Value<State>

    // VOCAL
    fun onStart()
    fun onStop()
    fun onRepeat()

    // Common
    fun onNext()
    fun onSkip()

    // QUESTIONNAIRE
    fun onOpenAnswerChanged(questionKey: String, value: String)
    fun onOptionToggled(questionKey: String, option: String, selected: Boolean)

    // Device-loss recovery (§8.5)
    fun onDeviceReselected(device: AudioInputDevice)

    // Example audio (§8.6 follow-up) — disabled while `Capturing`.
    fun onPlayExampleAudio()
    fun onStopExampleAudio()

    sealed interface Content {
        data class Vocal(
            val screenState: TaskScreenState,
            val takeNumber: Int,
            val level: Float,
            val deviceLost: Boolean,
            /** `VocalTask.showIndicator` (§6.2) — carried directly so the UI never needs the
             * raw task definition (see the removed `RootComponent` re-expansion workaround). */
            val showIndicator: Boolean,
            val exampleAudioAvailable: Boolean,
            val exampleAudioPlaying: Boolean,
        ) : Content

        data class Questionnaire(
            /** The task's own question definitions (§8.6), carried directly for the same reason. */
            val questions: List<Question>,
            val answers: Map<String, AnswerState>,
            val allValid: Boolean,
        ) : Content

        data object Info : Content
    }

    data class State(
        val taskIndex: Int,
        val repetition: Int,
        /** 1-based position among *navigable* task instances (calibration excluded, §8.6) and
         * the total count — e.g. "Task 3 of 10". Computed by `SessionComponent`, which already
         * holds the full navigable list, instead of being re-derived by the UI layer. */
        val positionInProtocol: Int,
        val totalInstanceCount: Int,
        val titleKey: String,
        val instructionKeys: List<String>,
        /** `VocalTask.length` (target seconds, 0 = none) — drives the legacy timer's green
         * "task can be finished" highlight (§13 decision 36); carried directly like the other
         * task-definition fields. */
        val taskLengthSeconds: Int,
        /** Title key of the *next* navigable task instance, `null` on the last one — the
         * legacy next-task button shows the upcoming task's name. Computed by
         * `SessionComponent` like [positionInProtocol]. */
        val nextTaskTitleKey: String?,
        val canSkip: Boolean,
        val content: Content,
        val buttons: TaskButtonState,
        /** Device list + the device in use when this task screen was created, for the
         * device-lost dialog (§8.5) — carried directly for the same reason as [Content]'s
         * task-definition fields. */
        val availableDevices: List<AudioInputDevice>,
        val currentDevice: AudioInputDevice?,
    )
}

/**
 * One task-screen instance (§8.6, §8.3). Owns its own take counter and, for VOCAL tasks,
 * observes the (session-owned) [recorder] for device loss to auto-reject an open take.
 * Never touches the mic itself for Start/Stop — only [SessionComponent] drives the recorder;
 * this component only emits timeline events through [eventLogger] (single-writer principle,
 * §5.2).
 *
 * [recorder] is `null` for no-master (questionnaire/info-only) protocols (§6.2) — device-loss
 * handling and the live level indicator are skipped entirely in that case.
 */
class DefaultTaskComponent(
    componentContext: ComponentContext,
    private val taskInstance: TaskInstance,
    private val recorder: ContinuousSessionRecorder?,
    private val dispatchers: CoroutineDispatchers,
    /** 1-based position / total among navigable task instances (§8.6), computed once by
     * [SessionComponent] from the same expanded list it already holds internally. */
    private val positionInProtocol: Int,
    private val totalInstanceCount: Int,
    /** Title key of the next navigable task instance (`null` on the last), for the legacy
     * next-task button (§13 decision 36); computed by [SessionComponent] like [positionInProtocol]. */
    private val nextTaskTitleKey: String? = null,
    /** Device list + the device in use when this task screen was created (§8.5), so the
     * device-lost dialog needs nothing from the UI layer beyond this component's own state. */
    private val availableDevices: List<AudioInputDevice>,
    private val currentDevice: AudioInputDevice?,
    /** `null` when the task has no `audioExamplePath`, or example playback isn't wired for a
     * no-master session; resolved to an absolute path by [SessionComponent] (§8.6 follow-up). */
    private val resolvedAudioExamplePath: Path?,
    private val audioPlaybackService: AudioPlaybackService?,
    /** Emits one timeline event for this task instance; taskIndex/repetition are bound by the caller. */
    private val eventLogger: (type: TimelineEventType, take: Int?, reason: String?) -> Unit,
    /** Computes the next master part file path for a device-loss resume (§8.5), owned by [SessionComponent]. */
    private val nextPartFile: () -> Path,
    /** Notifies [SessionComponent] a resume succeeded, so it can log RECORDING_RESUMED + update examination.interruptions. */
    private val onResumeRecorded: (device: AudioInputDevice, partFile: Path) -> Unit,
    /** Notifies the owner (SessionComponent) this task instance is done; carries the record for examination.json. */
    private val onTaskFinished: (TaskRecord) -> Unit,
) : TaskComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val task: Task = taskInstance.task

    private var screenState: TaskScreenState = TaskScreenState.Idle
    private var currentTake = 0
    private var level = 0f
    private var deviceLost = false
    private var exampleAudioPlaying = false
    private var answers: Map<String, AnswerState> = initialAnswers()

    private val _state = MutableValue(buildState())
    override val state: Value<TaskComponent.State> = _state

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        eventLogger(TimelineEventType.TASK_SCREEN_ENTERED, null, null)

        if (task is VocalTask && recorder != null) {
            scope.launch(dispatchers.main) {
                recorder.levels.collect { l ->
                    level = l
                    publish()
                }
            }
            scope.launch(dispatchers.main) {
                recorder.state.collect { rs ->
                    when (rs) {
                        is RecorderState.Interrupted -> onInterrupted()
                        is RecorderState.Failed -> onRecorderFailed(rs.error)
                        else -> Unit
                    }
                }
            }
        }

        if (task is VocalTask && audioPlaybackService != null) {
            scope.launch(dispatchers.main) {
                audioPlaybackService.isPlaying.collect { playing ->
                    exampleAudioPlaying = playing
                    publish()
                }
            }
        }
    }

    override fun onStart() {
        if (task !is VocalTask) return
        if (screenState !is TaskScreenState.Idle && screenState !is TaskScreenState.Failed) return
        currentTake += 1
        eventLogger(TimelineEventType.START_BUTTON_PRESSED, currentTake, null)
        screenState = TaskScreenState.Capturing
        publish()
    }

    override fun onStop() {
        if (task !is VocalTask) return
        if (screenState !is TaskScreenState.Capturing) return
        eventLogger(TimelineEventType.STOP_BUTTON_PRESSED, currentTake, null)
        screenState = TaskScreenState.Stopped
        publish()
    }

    override fun onRepeat() {
        if (task !is VocalTask || !task.canRepeat) return
        if (screenState !is TaskScreenState.Stopped) return
        eventLogger(TimelineEventType.TAKE_REJECTED, currentTake, REASON_EXAMINER_REPEAT)
        currentTake += 1
        eventLogger(TimelineEventType.START_BUTTON_PRESSED, currentTake, null)
        screenState = TaskScreenState.Capturing
        publish()
    }

    override fun onNext() {
        when (task) {
            is VocalTask -> {
                if (screenState !is TaskScreenState.Stopped) return
                eventLogger(TimelineEventType.TASK_COMPLETED, currentTake, null)
                onTaskFinished(
                    TaskRecord(
                        taskIndex = taskInstance.taskIndex,
                        type = "VOCAL",
                        subtype = task.subtype.name,
                        repetition = taskInstance.repetition,
                        takes = currentTake,
                        skipped = false,
                    ),
                )
            }

            is QuestionnaireTask -> {
                if (!allAnswersValid()) return
                eventLogger(TimelineEventType.TASK_COMPLETED, null, null)
                onTaskFinished(
                    TaskRecord(
                        taskIndex = taskInstance.taskIndex,
                        type = "QUESTIONNAIRE",
                        subtype = null,
                        repetition = taskInstance.repetition,
                        takes = 0,
                        skipped = false,
                        questionnaireAnswers = answers.mapValues { it.value.selected },
                    ),
                )
            }

            is InfoTask -> {
                eventLogger(TimelineEventType.TASK_COMPLETED, null, null)
                onTaskFinished(
                    TaskRecord(
                        taskIndex = taskInstance.taskIndex,
                        type = "INFO",
                        subtype = null,
                        repetition = taskInstance.repetition,
                        takes = 0,
                        skipped = false,
                    ),
                )
            }

            else -> Unit
        }
    }

    override fun onSkip() {
        if (!task.canSkip) return
        if (task is VocalTask && screenState !is TaskScreenState.Idle) return
        eventLogger(TimelineEventType.TASK_SKIPPED, null, null)
        onTaskFinished(
            TaskRecord(
                taskIndex = taskInstance.taskIndex,
                type = taskTypeName(),
                subtype = (task as? VocalTask)?.subtype?.name,
                repetition = taskInstance.repetition,
                takes = 0,
                skipped = true,
            ),
        )
    }

    override fun onOpenAnswerChanged(questionKey: String, value: String) {
        val questionnaire = task as? QuestionnaireTask ?: return
        val question = questionnaire.questions.firstOrNull { it.questionKey == questionKey } ?: return
        val regex = question.questionRegex
        val valid = regex.isNullOrEmpty() || Regex(regex).matches(value)
        answers = answers + (questionKey to AnswerState(selected = listOf(value), valid = valid))
        publish()
    }

    override fun onOptionToggled(questionKey: String, option: String, selected: Boolean) {
        val questionnaire = task as? QuestionnaireTask ?: return
        val question = questionnaire.questions.firstOrNull { it.questionKey == questionKey } ?: return
        val current = answers[questionKey]?.selected.orEmpty()
        val updated = when (question.questionType) {
            QuestionType.SINGLE_CHOICE -> if (selected) listOf(option) else emptyList()
            QuestionType.MULTIPLE_CHOICE -> if (selected) (current + option).distinct() else current - option
            QuestionType.OPEN -> current
        }
        val valid = when (question.questionType) {
            QuestionType.SINGLE_CHOICE -> updated.size == 1
            QuestionType.MULTIPLE_CHOICE -> true
            QuestionType.OPEN -> true
        }
        answers = answers + (questionKey to AnswerState(selected = updated, valid = valid))
        publish()
    }

    override fun onPlayExampleAudio() {
        val path = resolvedAudioExamplePath ?: return
        val playback = audioPlaybackService ?: return
        if (screenState is TaskScreenState.Capturing) return // §8.6: disabled while a take is open
        playback.play(path)
    }

    override fun onStopExampleAudio() {
        audioPlaybackService?.stop()
    }

    override fun onDeviceReselected(device: AudioInputDevice) {
        if (recorder == null) return
        scope.launch(dispatchers.main) {
            val partFile = nextPartFile()
            recorder.resume(device, partFile)
            deviceLost = false
            onResumeRecorded(device, partFile)
            publish()
        }
    }

    private fun onInterrupted() {
        if (screenState is TaskScreenState.Capturing) {
            eventLogger(TimelineEventType.TAKE_REJECTED, currentTake, REASON_DEVICE_LOST)
            screenState = TaskScreenState.Idle
        }
        deviceLost = true
        publish()
    }

    /** A hard recorder failure (§11 AudioError), distinct from a recoverable [RecorderState.Interrupted]
     * (§8.5) — no reconnect flow exists for this, but the state machine still surfaces it per the
     * §8.6 table (Start ✓ so the examiner can attempt to recover by pressing Start again once the
     * underlying issue — e.g. disk space — is resolved out of band). */
    private fun onRecorderFailed(error: AudioError) {
        screenState = TaskScreenState.Failed(error)
        publish()
    }

    private fun allAnswersValid(): Boolean {
        val questionnaire = task as? QuestionnaireTask ?: return true
        return questionnaire.questions.all { q -> answers[q.questionKey]?.valid ?: (q.questionRegex.isNullOrEmpty()) }
    }

    private fun initialAnswers(): Map<String, AnswerState> {
        val questionnaire = task as? QuestionnaireTask ?: return emptyMap()
        return questionnaire.questions.associate { q ->
            val valid = q.questionType == QuestionType.MULTIPLE_CHOICE ||
                q.questionType == QuestionType.OPEN && q.questionRegex.isNullOrEmpty()
            q.questionKey to AnswerState(selected = emptyList(), valid = valid)
        }
    }

    private fun taskTypeName(): String = when (task) {
        is VocalTask -> "VOCAL"
        is QuestionnaireTask -> "QUESTIONNAIRE"
        is InfoTask -> "INFO"
        else -> task::class.simpleName.orEmpty()
    }

    private fun publish() {
        _state.value = buildState()
    }

    private fun buildState(): TaskComponent.State {
        val content: TaskComponent.Content = when (task) {
            is VocalTask -> TaskComponent.Content.Vocal(
                screenState = screenState,
                takeNumber = currentTake,
                level = level,
                deviceLost = deviceLost,
                showIndicator = task.showIndicator,
                exampleAudioAvailable = resolvedAudioExamplePath != null,
                exampleAudioPlaying = exampleAudioPlaying,
            )

            is QuestionnaireTask -> TaskComponent.Content.Questionnaire(
                questions = task.questions,
                answers = answers,
                allValid = allAnswersValid(),
            )

            is InfoTask -> TaskComponent.Content.Info

            else -> TaskComponent.Content.Info
        }

        val buttons = if (task is VocalTask) {
            TaskButtonState.of(screenState, task.canRepeat, task.canSkip)
        } else {
            TaskButtonState(
                startEnabled = false,
                stopEnabled = false,
                repeatEnabled = false,
                nextEnabled = task !is QuestionnaireTask || allAnswersValid(),
                skipEnabled = task.canSkip,
            )
        }

        return TaskComponent.State(
            taskIndex = taskInstance.taskIndex,
            repetition = taskInstance.repetition,
            positionInProtocol = positionInProtocol,
            totalInstanceCount = totalInstanceCount,
            titleKey = task.titleKey,
            instructionKeys = (task as? VocalTask)?.instructionKeys
                ?: (task as? InfoTask)?.instructionKeys
                ?: emptyList(),
            taskLengthSeconds = (task as? VocalTask)?.length ?: 0,
            nextTaskTitleKey = nextTaskTitleKey,
            canSkip = task.canSkip,
            content = content,
            buttons = buttons,
            availableDevices = availableDevices,
            currentDevice = currentDevice,
        )
    }

    private companion object {
        const val REASON_EXAMINER_REPEAT = "EXAMINER_REPEAT"
        const val REASON_DEVICE_LOST = "DEVICE_LOST"
    }
}
