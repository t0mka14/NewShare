package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.example.app.domain.Clock
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.domain.audio.RecorderState
import org.example.app.domain.config.CalibrationTask
import org.example.app.domain.config.PatientField
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.VocalTask
import org.example.app.domain.session.Examination
import org.example.app.domain.session.Interruption
import org.example.app.domain.session.SessionRepository
import org.example.app.domain.session.StartSessionUseCase
import org.example.app.domain.session.StorageError
import org.example.app.domain.session.TaskRecord
import org.example.app.domain.timeline.TaskInstance
import org.example.app.domain.timeline.TimelineCompactor
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineRepository
import java.nio.file.Path

interface SessionComponent {
    val stack: Value<ChildStack<*, Child>>

    /** [StartError.error] is non-null once [StartSessionUseCase.Outcome.Rejected] is reached
     * (§8.1 preflight). Wrapped because Decompose's [Value] requires a non-null type param. */
    val startError: Value<StartError>

    data class StartError(val error: StorageError? = null)

    sealed class Child {
        /** Session creation / recorder format negotiation in progress (async, §5.3.1). */
        data object Bootstrapping : Child()
        class Calibration(val component: CalibrationComponent) : Child()
        class TaskScreen(val component: TaskComponent) : Child()

        /** Session could not be started; see [SessionComponent.startError] for the reason. */
        data object Failed : Child()
    }
}

/**
 * §5.2 session-scoped component: owns the [ContinuousSessionRecorder] instance, the timeline
 * writer, and a coroutine scope bound to this component's Decompose lifecycle. Created with a
 * config snapshot + participant data + protocol (the expanded task-instance list is derived
 * internally via [StartSessionUseCase], which this component calls itself — see that use
 * case's own doc comment: "the caller ... already knows the negotiated CaptureFormat ... by
 * the time it calls start").
 *
 * Bootstrap sequence (§8.1, §6.2): if the protocol has at least one VOCAL task, a recorder is
 * created and `startMonitoring` is called on [initialDevice] to negotiate the capture format
 * *before* [StartSessionUseCase.start] is invoked (session/examination creation needs the
 * format up front); the same recorder instance is then handed to [CalibrationComponent], and
 * to every child [TaskComponent] once writing has started. Questionnaire/info-only protocols
 * never create a recorder and skip straight to the first task instance (§6.2).
 *
 * Navigation is by index into the expanded, calibration-filtered task-instance list (§8.6) —
 * never object identity. `taskIndex`/`repetition` used in logged events come from
 * [TaskInstance], which still reflects calibration's own slot in the original expansion
 * (decision 22) even though calibration is not part of the navigable task list here.
 */
class DefaultSessionComponent(
    componentContext: ComponentContext,
    private val installationId: String,
    private val protocol: Protocol,
    private val configVersion: String,
    private val rawConfigJson: String,
    private val patientFields: List<PatientField>,
    private val participantFieldValues: Map<String, String>,
    private val initialDevice: AudioInputDevice,
    private val availableDevices: List<AudioInputDevice>,
    private val recorderFactory: () -> ContinuousSessionRecorder,
    private val startSessionUseCase: StartSessionUseCase,
    private val sessionRepository: SessionRepository,
    private val timelineRepository: TimelineRepository,
    private val clock: Clock,
    private val dispatchers: CoroutineDispatchers,
    private val onSessionEnded: () -> Unit,
) : SessionComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val navigation = StackNavigation<Config>()

    private val _startError = MutableValue(SessionComponent.StartError())
    override val startError: Value<SessionComponent.StartError> = _startError

    private var recorder: ContinuousSessionRecorder? = null
    private var folderName: String? = null
    private var examination: Examination? = null
    private var navigableInstances: List<TaskInstance> = emptyList()
    private var calibrationInstance: TaskInstance? = null
    private var currentDeviceId: String? = initialDevice.id
    private var pendingInterruption: PendingInterruption? = null
    private var interruptionPartCounter = 1

    override val stack: Value<ChildStack<*, SessionComponent.Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.Bootstrapping,
            handleBackButton = false,
            childFactory = ::createChild,
        )

    init {
        lifecycle.doOnDestroy {
            recorder?.let { r -> scope.launch(dispatchers.main) { r.stop() } }
            scope.cancel()
        }
        scope.launch(dispatchers.main) { bootstrap() }
    }

    private suspend fun bootstrap() {
        val hasVocal = protocol.tasks.any { it is VocalTask }
        var negotiatedFormat: CaptureFormat? = null

        if (hasVocal) {
            val r = recorderFactory()
            recorder = r
            r.startMonitoring(initialDevice)
            negotiatedFormat = r.captureFormat.value
            observeInterruptions(r)
        }

        val outcome = startSessionUseCase.start(
            StartSessionUseCase.Params(
                installationId = installationId,
                protocol = protocol,
                configVersion = configVersion,
                rawConfigJson = rawConfigJson,
                patientFields = patientFields,
                participantFieldValues = participantFieldValues,
                negotiatedFormat = negotiatedFormat,
            ),
        )

        when (outcome) {
            is StartSessionUseCase.Outcome.Started -> {
                val result = outcome.result
                folderName = result.folderName
                examination = result.examination
                calibrationInstance = result.expansion.instances.firstOrNull { it.task is CalibrationTask }
                navigableInstances = result.expansion.instances.filterNot { it.task is CalibrationTask }

                if (hasVocal && calibrationInstance != null) {
                    navigation.replaceAll(Config.Calibration)
                } else {
                    navigation.replaceAll(Config.TaskScreen(0))
                }
            }

            is StartSessionUseCase.Outcome.Rejected -> {
                _startError.value = SessionComponent.StartError(outcome.error)
                navigation.replaceAll(Config.Failed)
            }
        }
    }

    private fun createChild(config: Config, childContext: ComponentContext): SessionComponent.Child =
        when (config) {
            Config.Bootstrapping -> SessionComponent.Child.Bootstrapping
            Config.Failed -> SessionComponent.Child.Failed

            Config.Calibration -> SessionComponent.Child.Calibration(
                DefaultCalibrationComponent(
                    componentContext = childContext,
                    recorder = requireNotNull(recorder),
                    dispatchers = dispatchers,
                    calibrationTask = calibrationInstance!!.task as CalibrationTask,
                    initialDevice = initialDevice,
                    availableDevices = availableDevices,
                    onConfirmed = ::onCalibrationConfirmed,
                ),
            )

            is Config.TaskScreen -> SessionComponent.Child.TaskScreen(buildTaskComponent(childContext, config.index))
        }

    private fun buildTaskComponent(childContext: ComponentContext, listIndex: Int): TaskComponent {
        val instance = navigableInstances[listIndex]
        return DefaultTaskComponent(
            componentContext = childContext,
            taskInstance = instance,
            recorder = recorder,
            dispatchers = dispatchers,
            eventLogger = { type, take, reason ->
                logEvent(type, instance.taskIndex, instance.repetition, take, reason)
            },
            nextPartFile = { computeNextPartFile() },
            onResumeRecorded = { device, partFile -> onResumeRecorded(device, partFile) },
            onTaskFinished = { record -> onTaskInstanceFinished(listIndex, record) },
        )
    }

    private fun onCalibrationConfirmed() {
        scope.launch(dispatchers.main) {
            val r = recorder ?: return@launch
            val master = sessionRepository.defaultMasterFile(requireNotNull(folderName))
            r.startWriting(master)
            logEvent(TimelineEventType.SESSION_RECORDING_STARTED, null, null, null)
            navigation.replaceAll(Config.TaskScreen(0))
        }
    }

    private fun onTaskInstanceFinished(listIndex: Int, record: TaskRecord) {
        val folder = folderName ?: return
        val exam = examination ?: return
        val updated = exam.copy(tasks = exam.tasks + record)
        examination = updated
        sessionRepository.writeExamination(folder, updated)

        if (listIndex + 1 < navigableInstances.size) {
            navigation.replaceAll(Config.TaskScreen(listIndex + 1))
        } else {
            scope.launch(dispatchers.main) { endSession() }
        }
    }

    private suspend fun endSession() {
        val folder = folderName ?: return
        var exam = examination ?: return

        recorder?.let { r ->
            logEvent(TimelineEventType.SESSION_RECORDING_STOPPED, null, null, null)
            r.stop()
        }

        exam = exam.copy(endedAt = clock.now().toString())
        examination = exam
        sessionRepository.writeExamination(folder, exam)

        val parseResult = timelineRepository.readEventLog(folder)
        val sampleRate = exam.captureFormat?.sampleRate ?: 0
        val compacted = TimelineCompactor.compact(exam.sessionId, sampleRate, parseResult.events)
        timelineRepository.writeOriginal(folder, compacted)

        onSessionEnded()
    }

    private fun observeInterruptions(r: ContinuousSessionRecorder) {
        scope.launch(dispatchers.main) {
            r.state.collect { rs ->
                if (rs is RecorderState.Interrupted && pendingInterruption == null) {
                    pendingInterruption = PendingInterruption(
                        oldDeviceId = currentDeviceId,
                        sampleOffset = rs.atSample,
                        wallClockStart = clock.now().toString(),
                    )
                    logEvent(TimelineEventType.RECORDING_INTERRUPTED, null, null, null)
                }
            }
        }
    }

    private fun onResumeRecorded(device: AudioInputDevice, partFile: Path) {
        val folder = folderName ?: return
        val r = recorder ?: return
        val exam = examination ?: return
        val pending = pendingInterruption ?: return
        pendingInterruption = null
        currentDeviceId = device.id

        logEvent(TimelineEventType.RECORDING_RESUMED, null, null, null)

        val format = r.captureFormat.value ?: return
        val interruption = Interruption(
            sampleOffset = pending.sampleOffset,
            start = pending.wallClockStart,
            end = clock.now().toString(),
            oldDevice = pending.oldDeviceId,
            newDevice = device.id,
            partFile = partFile.fileName.toString(),
            captureFormat = format,
        )
        val updated = exam.copy(interruptions = exam.interruptions + interruption)
        examination = updated
        sessionRepository.writeExamination(folder, updated)
    }

    private fun computeNextPartFile(): Path {
        val folder = requireNotNull(folderName)
        interruptionPartCounter += 1
        return sessionRepository.masterDir(folder).resolve("session_master.part$interruptionPartCounter.wav")
    }

    private fun logEvent(type: TimelineEventType, taskIndex: Int?, repetition: Int?, take: Int?, reason: String? = null) {
        val folder = folderName ?: return
        val offset = recorder?.writtenSamples?.value
        timelineRepository.appendEvent(
            folder,
            TimelineEvent(
                type = type,
                sampleOffset = offset,
                wallClock = clock.now().toString(),
                taskIndex = taskIndex,
                repetition = repetition,
                take = take,
                reason = reason,
            ),
        )
    }

    private data class PendingInterruption(val oldDeviceId: String?, val sampleOffset: Long, val wallClockStart: String)

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Bootstrapping : Config

        @Serializable
        data object Calibration : Config

        @Serializable
        data class TaskScreen(val index: Int) : Config

        @Serializable
        data object Failed : Config
    }
}
