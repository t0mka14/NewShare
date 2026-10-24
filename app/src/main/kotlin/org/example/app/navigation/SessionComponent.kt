package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.example.app.domain.Clock
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.AudioPlaybackService
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.domain.audio.RecorderState
import org.example.app.domain.config.ExampleAudioCache
import org.example.app.domain.config.IndicatorType
import org.example.app.domain.config.PatientField
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.VocalTask
import org.example.app.domain.config.capturesVideo
import org.example.app.domain.audio.DeviceOpenReportingRecorder
import org.example.app.domain.session.CameraUse
import org.example.app.domain.session.EmptySessionCleanup
import org.example.app.domain.session.Examination
import org.example.app.domain.session.HardwareEvent
import org.example.app.domain.session.MicUse
import org.example.app.domain.session.SessionHardware
import org.example.app.domain.session.Interruption
import org.example.app.domain.session.SessionRepository
import org.example.app.domain.session.StartSessionUseCase
import org.example.app.domain.session.StorageError
import org.example.app.domain.session.TaskRecord
import org.example.app.domain.session.VideoTakeRecord
import org.example.app.domain.timeline.TaskInstance
import org.example.app.domain.timeline.TimelineCompactor
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.config.VideoTask
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.video.NoOpPtzController
import org.example.app.domain.video.PtzController
import org.example.app.domain.video.SessionVideoRecorder
import org.example.app.domain.video.VideoError
import org.example.app.domain.video.redactPaths
import org.example.app.domain.video.VideoRecorderState
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.domain.video.VideoInputDevice
import org.example.app.domain.video.VideoInputDeviceProvider
import org.example.app.domain.timeline.TimelineRepository
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

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
 * Navigation is by index into the expanded task-instance list (§8.6) — never object identity.
 * Calibration is not a task (config alignment row 5): with [useCalibration] a VOCAL protocol
 * opens on the calibration screen, whose confirm starts the master recording; without it the
 * recording starts right after bootstrap.
 */
class DefaultSessionComponent(
    componentContext: ComponentContext,
    private val installationId: String,
    private val protocol: Protocol,
    private val configVersion: String,
    private val rawConfigJson: String,
    private val participantFieldValues: Map<String, String>,
    private val initialDevice: AudioInputDevice,
    private val availableDevices: List<AudioInputDevice>,
    private val recorderFactory: () -> ContinuousSessionRecorder,
    /**
     * Where the camera comes from, rather than a camera already chosen.
     *
     * Listing cameras spawns ffmpeg and blocks until it exits, so it cannot happen in
     * `RootComponent.buildSessionChild` — that is a Decompose `childFactory` and runs on the Swing
     * EDT. It happens in [bootstrap] instead, on the IO dispatcher, which is also where the
     * microphone is set up and which already completes before the first task screen exists.
     */
    private val videoInputDeviceProvider: VideoInputDeviceProvider,
    /** `AppSettings.cameraDeviceId`; the first eligible camera is used when it no longer matches. */
    private val savedCameraDeviceId: String?,
    private val videoRecorderFactory: () -> SessionVideoRecorder,
    /** Bound to a real controller only on a host with a PTZ backend; see `AppContainer`. */
    private val ptzControllerFactory: (VideoInputDevice) -> PtzController,
    private val startSessionUseCase: StartSessionUseCase,
    private val sessionRepository: SessionRepository,
    private val timelineRepository: TimelineRepository,
    private val clock: Clock,
    private val dispatchers: CoroutineDispatchers,
    /** Maps a VOCAL task's `audioExamplePath` URL to its downloaded file for
     * `AudioPlaybackService` (row 13); not cached means no example button. */
    private val exampleAudioCache: ExampleAudioCache,
    private val audioPlaybackService: AudioPlaybackService,
    /** `RemoteConfig.useCalibration`: show calibration before the first task of a VOCAL protocol. */
    private val useCalibration: Boolean,
    /** Calibration target band, `AppSettings.loudnessRange()`. */
    private val calibrationLoudness: ClosedFloatingPointRange<Double>,
    /** Live task-screen indicator, `AppSettings.indicatorType(config)` (§13 decision 53). */
    private val indicatorType: IndicatorType,
    /** Carries the finished session's folder name, so the caller (RootComponent) can route
     * into the editor (if `enableEditor`) or straight to processing (§8.8) without
     * re-deriving it. */
    private val onSessionEnded: (folderName: String) -> Unit,
    /** `AppSettings.micDeviceId`; only logged, to show whether the device used is the configured one. */
    private val savedMicDeviceId: String? = null,
    /** The OS input level of a device right now (`MicGainApplier.read`); null when unreadable. */
    private val readMicGain: suspend (AudioInputDevice) -> Int? = { null },
) : SessionComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val navigation = StackNavigation<Config>()

    private val _startError = MutableValue(SessionComponent.StartError())
    override val startError: Value<SessionComponent.StartError> = _startError

    private var recorder: ContinuousSessionRecorder? = null
    private var videoRecorder: SessionVideoRecorder? = null

    /**
     * Stands in for the recorder's own state when the protocol wants video but no camera was
     * found, so the task screen reports it and refuses Start instead of appearing to record.
     */
    private val noCameraState = MutableStateFlow<VideoRecorderState>(
        VideoRecorderState.Failed(VideoError.DeviceUnavailable("none")),
    )
    /**
     * How many filming task screens (VIDEO, or VOCAL with `recordVideo`) are currently alive. A counter rather than a boolean because
     * Decompose may create the incoming child before destroying the outgoing one: on a
     * VIDEO -> VIDEO transition (a task with `nrepetition > 1`) a boolean would either try to
     * open a camera the previous screen still holds, or let the outgoing screen's close kill a
     * camera the incoming one just opened. With a count, either ordering is correct — and
     * create-before-destroy keeps the camera open across consecutive takes for free.
     */
    private var videoScreensOpen = 0

    /** Resolved once by [bootstrap], before any task screen exists; null when no camera was found. */
    private var initialVideoDevice: VideoInputDevice? = null
    private var folderName: String? = null
    private var examination: Examination? = null
    private var navigableInstances: List<TaskInstance> = emptyList()
    private var currentDeviceId: String? = initialDevice.id
    private var pendingInterruption: PendingInterruption? = null
    private var interruptionPartCounter = 1

    /**
     * Devices actually used, written into `examination.json.hardware`. Kept here as well as in
     * [examination] because the first microphone open happens during [bootstrap], before the
     * examination exists; [updateHardware] flushes it once it does.
     */
    private var hardware = SessionHardware(
        configuredMicDeviceId = savedMicDeviceId,
        configuredCameraDeviceId = savedCameraDeviceId,
    )

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
            scope.cancel()
            // Not [scope]: a job launched into it here would be cancelled before the main
            // dispatcher got to run it, leaving the line open so the next session cannot open
            // the device (F-051). Stopped devices are released even though the component is gone.
            val releaseScope = CoroutineScope(dispatchers.main + NonCancellable)
            recorder?.let { r -> releaseScope.launch { r.stop() } }
            // Idempotent safety net: the screen that opened the camera normally closes it.
            videoRecorder?.let { v -> releaseScope.launch { v.stop() } }
            // F-047: a session left before anything was recorded is not kept. The cleanup checks
            // the log and the master itself, so a session that recorded (or ended) is never removed.
            folderName?.let { EmptySessionCleanup(sessionRepository, timelineRepository).deleteIfEmpty(it) }
        }
        scope.launch(dispatchers.main) { bootstrap() }
    }

    private suspend fun bootstrap() {
        val hasVocal = protocol.tasks.any { it is VocalTask }
        var negotiatedFormat: CaptureFormat? = null

        if (hasVocal) {
            val r = DeviceOpenReportingRecorder(recorderFactory(), ::onMicOpened)
            recorder = r
            r.startMonitoring(initialDevice)
            negotiatedFormat = r.captureFormat.value
            observeInterruptions(r)
        }

        // Cameras are listed only for a protocol that films something (a VIDEO task, or a VOCAL
        // one with `recordVideo`) — it spawns a process, and an audio-only protocol should not pay
        // for it. On the IO dispatcher, because that
        // process is waited on, and this is the last point before a task screen can exist, so
        // everything downstream (including the per-screen PTZ controller) sees a resolved device.
        if (protocol.tasks.any { it.capturesVideo }) {
            val cameras = withContext(dispatchers.io) { videoInputDeviceProvider.availableDevices() }
            initialVideoDevice = cameras.firstOrNull { it.id == savedCameraDeviceId }
                ?: cameras.firstOrNull { it.eligible }
                ?: cameras.firstOrNull()
            if (initialVideoDevice == null) logger.warn { "protocol wants video but no camera was found" }
        }

        // The recorder *object* is created here so the flows handed to task components are
        // stable for the whole session, but the camera itself is not opened until a filming task
        // screen is entered (see [buildTaskComponent]). Constructing it starts no process.
        if (initialVideoDevice != null) {
            videoRecorder = videoRecorderFactory().also(::observeCamera)
        }

        val outcome = startSessionUseCase.start(
            StartSessionUseCase.Params(
                installationId = installationId,
                protocol = protocol,
                configVersion = configVersion,
                rawConfigJson = rawConfigJson,
                participantFieldValues = participantFieldValues,
                negotiatedFormat = negotiatedFormat,
            ),
        )

        when (outcome) {
            is StartSessionUseCase.Outcome.Started -> {
                val result = outcome.result
                folderName = result.folderName
                examination = result.examination
                navigableInstances = result.expansion.instances
                updateHardware { it } // flush what was opened before the examination existed

                when {
                    hasVocal && useCalibration -> navigation.replaceAll(Config.Calibration)
                    // A mic that failed to open still gets the calibration screen: it carries the error and Retry.
                    hasVocal -> if (!startRecording()) navigation.replaceAll(Config.Calibration)
                    else -> navigation.replaceAll(Config.TaskScreen(0))
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
                    loudness = calibrationLoudness,
                    initialDevice = initialDevice,
                    availableDevices = availableDevices,
                    onConfirmed = ::onCalibrationConfirmed,
                ),
            )

            is Config.TaskScreen -> SessionComponent.Child.TaskScreen(buildTaskComponent(childContext, config.index))
        }

    private fun buildTaskComponent(childContext: ComponentContext, listIndex: Int): TaskComponent {
        val instance = navigableInstances[listIndex]
        val task = instance.task
        val resolvedAudioExamplePath = (task as? VocalTask)?.audioExamplePath
            ?.let(exampleAudioCache::localFileFor)
        val currentDevice = availableDevices.firstOrNull { it.id == currentDeviceId }

        // The camera is opened here rather than from a lifecycle callback for two reasons:
        // `childFactory` runs exactly once per screen entry (every transition is `replaceAll`,
        // so each screen is a fresh child), and `doOnCreate` would not fire for a child that is
        // already created by the time this returns — essenty's lifecycle does not replay past
        // events. The PTZ controller has to exist before the component is constructed, because
        // `Content.Video.ptzAvailable` reads it. A VOCAL task that films has no PTZ.
        val camera = initialVideoDevice
        val ptz = if (task is VideoTask && camera != null) {
            ptzControllerFactory(camera)
        } else {
            NoOpPtzController
        }
        if (task.capturesVideo) {
            onVideoScreenEntered()
            childContext.lifecycle.doOnDestroy {
                onVideoScreenExited()
                ptz.close()
            }
        }

        return DefaultTaskComponent(
            componentContext = childContext,
            taskInstance = instance,
            recorder = recorder,
            dispatchers = dispatchers,
            positionInProtocol = listIndex + 1,
            totalInstanceCount = navigableInstances.size,
            nextTaskTitleKey = navigableInstances.getOrNull(listIndex + 1)?.task?.titleKey,
            previousTaskSkipped = listIndex > 0 && examination?.tasks?.lastOrNull()?.skipped == true,
            availableDevices = availableDevices,
            currentDevice = currentDevice,
            resolvedAudioExamplePath = resolvedAudioExamplePath,
            audioPlaybackService = audioPlaybackService,
            eventLogger = { type, take, reason ->
                logEvent(type, instance.taskIndex, instance.repetition, take, reason)
            },
            videoFrames = videoRecorder?.previewFrames,
            videoState = videoRecorder?.state ?: noCameraState.takeIf { task.capturesVideo },
            ptzController = ptz,
            indicatorType = indicatorType,
            onVideoTakeStarted = { take -> startVideoTake(instance, take) },
            onVideoTakeStopped = { stopVideoTake() },
            nextPartFile = { computeNextPartFile() },
            onResumeRecorded = { device, partFile -> onResumeRecorded(device, partFile) },
            onTaskFinished = { record -> onTaskInstanceFinished(listIndex, record) },
        )
    }

    /**
     * Opens the camera for the first filming screen. Previously this happened once at session
     * bootstrap, which held the device — and its indicator light — for the whole examination
     * and put a continuous UVC isochronous load on the bus shared with the USB microphone.
     *
     * The re-check inside the coroutine discards a decision a fast transition has already
     * invalidated.
     */
    private fun onVideoScreenEntered() {
        val v = videoRecorder ?: return
        val device = initialVideoDevice ?: return
        videoScreensOpen++
        if (videoScreensOpen != 1) return
        scope.launch(dispatchers.main) {
            if (videoScreensOpen > 0) {
                v.startPreview(device)
                if (v.state.value == VideoRecorderState.Previewing) {
                    updateHardware {
                        it.copy(
                            cameraUses = it.cameraUses + CameraUse(
                                openedAt = clock.now().toString(),
                                deviceId = device.id,
                                deviceName = device.name,
                                nameOrdinal = device.nameOrdinal,
                                platformIndex = device.platformIndex,
                                captureFormat = v.captureFormat.value,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** A camera that dies mid-session leaves no other trace than a Failed state; log it. */
    private fun observeCamera(v: SessionVideoRecorder) {
        scope.launch(dispatchers.main) {
            v.state.collect { vs ->
                if (vs is VideoRecorderState.Failed) {
                    val reason = vs.error.readableReason()
                    logger.warn { "camera failed: $reason" }
                    addHardwareEvent("camera", "capture_failed", reason)
                }
            }
        }
    }

    /** A short sentence for the log and `hardware.events`, never the error object's `toString`. */
    private fun VideoError.readableReason(): String = when (this) {
        is VideoError.DeviceUnavailable -> "camera unavailable"
        VideoError.CaptureBackendUnavailable -> "capture backend unavailable"
        is VideoError.CaptureStartFailed -> "capture did not start: ${redactPaths(detail)}"
        is VideoError.CaptureInterrupted -> "capture interrupted: ${redactPaths(detail)}"
        // Not the detail: an I/O message names the file, and that path holds the patient code.
        is VideoError.DiskWriteFailed -> "video write failed"
    }.let { text -> folderName?.let { text.replace(it, "<session>") } ?: text }.take(MAX_HARDWARE_DETAIL_CHARS)

    /** Every successful microphone open (see [DeviceOpenReportingRecorder]); also the change log. */
    private suspend fun onMicOpened(device: AudioInputDevice, format: CaptureFormat?) {
        val gain = readMicGain(device)
        currentDeviceId = device.id
        logger.info { "microphone opened: '${device.name}' (${device.id}) gain=$gain format=$format" }
        updateHardware {
            it.copy(
                micUses = it.micUses + MicUse(
                    openedAt = clock.now().toString(),
                    deviceId = device.id,
                    deviceName = device.name,
                    description = device.description,
                    inputGainPercent = gain,
                    captureFormat = format,
                ),
            )
        }
    }

    private fun addHardwareEvent(device: String, event: String, detail: String? = null) =
        updateHardware { it.copy(events = it.events + HardwareEvent(clock.now().toString(), device, event, detail)) }

    private fun updateHardware(change: (SessionHardware) -> SessionHardware) {
        hardware = change(hardware)
        val folder = folderName ?: return
        val exam = examination ?: return
        val updated = exam.copy(hardware = hardware)
        examination = updated
        sessionRepository.writeExamination(folder, updated)
    }

    /** Releases the camera once the last filming screen is gone. */
    private fun onVideoScreenExited() {
        val v = videoRecorder ?: return
        videoScreensOpen--
        if (videoScreensOpen != 0) return
        scope.launch(dispatchers.main) {
            if (videoScreensOpen == 0) v.stop()
        }
    }

    /**
     * Begins a VIDEO take. The camera is session-owned, so [TaskComponent] asks for this
     * rather than driving the recorder itself (single-writer principle, §5.2) — the same
     * reason it never starts or stops the microphone.
     *
     * The file is a bare MJPEG elementary stream; `ProcessSessionUseCase` remuxes it into a
     * playable container during processing, where the frame rate is known.
     */
    private fun startVideoTake(instance: TaskInstance, take: Int) {
        val v = videoRecorder ?: return
        val folder = folderName ?: return
        scope.launch(dispatchers.main) {
            // The screen gates Start on readiness, but the camera could have failed in between;
            // `startRecording` requires Previewing and would throw inside this coroutine. This is
            // also the audio-only fallback of a VOCAL task with `recordVideo`: its take goes on
            // without video and gets no `videoTakes[]` entry, which is how a reader tells.
            if (v.state.value != VideoRecorderState.Previewing) {
                logger.warn { "ignoring video take $take: capture is ${v.state.value}" }
                return@launch
            }
            val name = videoFileName(instance, take)
            v.startRecording(sessionRepository.videoDir(folder).resolve(name))
            // Only once the file is genuinely open, so a take that failed on disk is not advertised
            // as a file that exists. Recorded now rather than at stop: the format is already known,
            // and a crash mid-take then still leaves the frame rate on record — the one thing a
            // timestamp-less elementary stream cannot supply for itself.
            if (v.state.value == VideoRecorderState.Recording) {
                recordVideoTake(folder, instance, take, name, v.captureFormat.value)
            }
        }
    }

    private fun recordVideoTake(
        folder: String,
        instance: TaskInstance,
        take: Int,
        fileName: String,
        format: VideoCaptureFormat?,
    ) {
        val exam = examination ?: return
        if (format == null) {
            // `startPreview` sets the format before it reports Previewing, and `startRecording`
            // refuses anything else, so this is unreachable — but a take is worth more than its
            // metadata, so it proceeds unrecorded rather than failing.
            logger.warn { "video take $take has no capture format; not recording its metadata" }
            return
        }
        val updated = exam.copy(
            videoTakes = exam.videoTakes + VideoTakeRecord(
                // Same shape as `TaskRecord.clipFile`: session-relative with a literal '/'.
                file = "video/$fileName",
                taskIndex = instance.taskIndex,
                repetition = instance.repetition,
                take = take,
                captureFormat = format,
            ),
        )
        examination = updated
        sessionRepository.writeExamination(folder, updated)
    }

    private fun stopVideoTake() {
        val v = videoRecorder ?: return
        scope.launch(dispatchers.main) { v.stopRecording() }
    }

    /** Windows-safe by construction: only the sanitised task index, repetition and take. */
    private fun videoFileName(instance: TaskInstance, take: Int): String =
        "task%02d_rep%02d_take%02d.mjpeg".format(instance.taskIndex, instance.repetition, take)

    private fun onCalibrationConfirmed() {
        scope.launch(dispatchers.main) { startRecording() }
    }

    /**
     * The master recording starts here: on calibration confirm, or straight after bootstrap without it.
     * Returns false (nothing started, no navigation) when the recorder is not [RecorderState.Monitoring].
     */
    private suspend fun startRecording(): Boolean {
        val r = recorder ?: return false
        if (r.state.value != RecorderState.Monitoring) {
            // The mic never opened (or was lost); startWriting would throw. Calibration shows the
            // error with Retry, so the caller sends the examiner there instead.
            logger.warn { "not starting the master recording: recorder is ${r.state.value}" }
            return false
        }
        val master = sessionRepository.defaultMasterFile(requireNotNull(folderName))
        r.startWriting(master)
        logEvent(TimelineEventType.SESSION_RECORDING_STARTED, null, null, null)
        navigation.replaceAll(Config.TaskScreen(0))
        return true
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
            // Somebody turning the OS mic level mid-session is invisible otherwise.
            val device = availableDevices.firstOrNull { it.id == currentDeviceId } ?: initialDevice
            val last = hardware.micUses.lastOrNull()
            val gainNow = readMicGain(device)
            if (last != null && gainNow != last.inputGainPercent) {
                addHardwareEvent("microphone", "gain_changed", "${last.inputGainPercent} -> $gainNow")
            }
            logEvent(TimelineEventType.SESSION_RECORDING_STOPPED, null, null, null)
            r.stop()
        }

        exam = (examination ?: exam).copy(endedAt = clock.now().toString()) // re-read: hardware events may have landed
        examination = exam
        sessionRepository.writeExamination(folder, exam)

        val parseResult = timelineRepository.readEventLog(folder)
        val sampleRate = exam.captureFormat?.sampleRate ?: 0
        val compacted = TimelineCompactor.compact(exam.sessionId, sampleRate, parseResult.events)
        timelineRepository.writeOriginal(folder, compacted)

        onSessionEnded(folder)
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
                    addHardwareEvent("microphone", "interrupted", rs.reason.toString())
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

    private companion object {
        const val MAX_HARDWARE_DETAIL_CHARS = 300
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
