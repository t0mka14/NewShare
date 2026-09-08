package org.example.app

import org.example.app.domain.AppDirectories
import org.example.app.domain.ErrorReporter
import org.example.app.domain.Clock
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.DefaultCoroutineDispatchers
import org.example.app.domain.IdGenerator
import org.example.app.domain.RealClock
import org.example.app.domain.audio.AudioClipService
import org.example.app.domain.audio.AudioInputDeviceProvider
import org.example.app.domain.audio.AudioInputGainControl
import org.example.app.domain.audio.MicGainApplier
import org.example.app.domain.audio.AudioPlaybackService
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.domain.audio.WaveformService
import org.example.app.domain.config.ConfigApi
import org.example.app.domain.video.NoOpPtzController
import org.example.app.domain.video.PtzController
import org.example.app.domain.video.SessionVideoRecorder
import org.example.app.domain.video.VideoInputDevice
import org.example.app.domain.video.VideoInputDeviceProvider
import org.example.app.infrastructure.video.FfmpegDeviceEnumerator
import org.example.app.infrastructure.video.FfmpegSessionVideoRecorder
import org.example.app.infrastructure.DefaultAppDirectories
import org.example.app.infrastructure.UuidIdGenerator
import org.example.app.infrastructure.audio.JvmAudioClipService
import org.example.app.infrastructure.audio.JvmAudioInputDeviceProvider
import org.example.app.infrastructure.audio.JvmAudioInputGainControl
import org.example.app.infrastructure.audio.JvmAudioPlaybackService
import org.example.app.infrastructure.audio.JvmContinuousSessionRecorder
import org.example.app.infrastructure.audio.JvmWaveformService
import org.example.app.domain.config.ConfigurationRepository
import org.example.app.domain.config.RefreshConfigurationUseCase
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.participant.ValidateParticipantInfoUseCase
import org.example.app.domain.session.ProcessSessionUseCase
import org.example.app.domain.session.RecoverSessionsUseCase
import org.example.app.domain.session.SessionArchiveService
import org.example.app.domain.session.StartSessionUseCase
import org.example.app.domain.upload.EligibleUploadsQuery
import org.example.app.domain.upload.FileHashService
import org.example.app.domain.upload.UploadApi
import org.example.app.domain.upload.UploadSessionUseCase
import org.example.app.domain.settings.AppSettingsRepository
import org.example.app.domain.timeline.TimelineRepository
import org.example.app.infrastructure.config.JsonConfigurationRepository
import org.example.app.infrastructure.config.RawConfigCache
import org.example.app.infrastructure.lock.SingleInstanceLock
import org.example.app.infrastructure.network.KtorConfigApi
import org.example.app.infrastructure.network.KtorUploadApi
import org.example.app.infrastructure.persistence.JsonAppSettingsRepository
import org.example.app.infrastructure.persistence.JsonSessionRepository
import org.example.app.infrastructure.persistence.JsonTimelineRepository
import org.example.app.infrastructure.persistence.JsonUploadStatusRepository
import org.example.app.infrastructure.persistence.JvmDiskSpaceProvider
import org.example.app.infrastructure.persistence.Sha256FileHashService
import org.example.app.infrastructure.persistence.ZipSessionArchiveService

/**
 * Manual DI root (§5.2). Every port gets a production binding here; tests build
 * their own container from fakes. No DI framework, no singletons.
 */
class AppContainer(
    val directories: AppDirectories = DefaultAppDirectories(),
    val clock: Clock = RealClock(),
    val idGenerator: IdGenerator = UuidIdGenerator(),
    val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers(),
    // §10.3: every hardware/network port is injectable so tests can build a
    // full-fake container; production defaults below.
    val configApi: ConfigApi = KtorConfigApi(),
    val uploadApi: UploadApi = KtorUploadApi(),
    val audioInputDeviceProvider: AudioInputDeviceProvider = JvmAudioInputDeviceProvider(),
    /** OS-level microphone gain, addressed by device name (§13 decision 44). */
    val audioInputGainControl: AudioInputGainControl = JvmAudioInputGainControl(),
    val audioClipService: AudioClipService = JvmAudioClipService(),
    val waveformService: WaveformService = JvmWaveformService(),
    val audioPlaybackService: AudioPlaybackService = JvmAudioPlaybackService(),
    /**
     * The recorder is session-scoped (§5.2: SessionComponent owns it) — a factory,
     * not a shared instance, so no recording state outlives a session.
     */
    val sessionRecorderFactory: () -> ContinuousSessionRecorder =
        { JvmContinuousSessionRecorder(dispatchers) },
    val videoInputDeviceProvider: VideoInputDeviceProvider = FfmpegDeviceEnumerator(),
    /** Session-scoped for the same reason as [sessionRecorderFactory]: no camera state
     * outlives a session. */
    val sessionVideoRecorderFactory: () -> SessionVideoRecorder =
        { FfmpegSessionVideoRecorder(dispatchers) },
    /**
     * PTZ is DirectShow/COM and exists on Windows only. This factory is the single place that
     * may name a platform implementation: everywhere else the app sees [PtzController], so the
     * Windows classes are never even *loaded* off Windows — JVM class loading is lazy and the
     * type appears in no other signature. A `havePTZ: true` task on macOS therefore produces
     * no controls rather than dead ones.
     *
     * Stage 2 adds the DirectShow implementation, guarded exactly here:
     *
     *     if (HostOs.isWindows) DirectShowPtzController(device, dispatchers) else NoOpPtzController
     *
     * Until then no platform has a PTZ backend and [PtzController.isAvailable] is false
     * everywhere, which is the correct answer on every non-Windows host in any case.
     */
    val ptzControllerFactory: (VideoInputDevice) -> PtzController = { _ -> NoOpPtzController },
) {
    /** Acquired at the top of Main before any other startup work (§5.2). */
    val singleInstanceLock: SingleInstanceLock = SingleInstanceLock(directories)

    /** Last-resort sink for uncaught exceptions; wired to the global handlers in Main. */
    val errorReporter = ErrorReporter()

    val rawConfigCache: RawConfigCache = RawConfigCache(directories)

    val configurationRepository: ConfigurationRepository = JsonConfigurationRepository(rawConfigCache)

    val appSettingsRepository: AppSettingsRepository = JsonAppSettingsRepository(directories)

    /**
     * Decides and applies the microphone level (§13 decision 44): the Settings slider wins over
     * the config's `defaultMicGain`. `RootComponent` wraps each session recorder in a
     * `MicGainReapplyingRecorder` around this, so the level is set every time a device is opened.
     */
    val micGainApplier = MicGainApplier(audioInputGainControl, appSettingsRepository, dispatchers)

    val sessionRepository = JsonSessionRepository(directories)
    val timelineRepository: TimelineRepository = JsonTimelineRepository(directories)
    val uploadStatusRepository = JsonUploadStatusRepository(directories)

    val localizedStringProvider = LocalizedStringProvider()

    val refreshConfigurationUseCase =
        RefreshConfigurationUseCase(appSettingsRepository, configApi, configurationRepository)

    val validateParticipantInfoUseCase = ValidateParticipantInfoUseCase()

    val startSessionUseCase = StartSessionUseCase(
        directories = directories,
        sessionRepository = sessionRepository,
        diskSpaceProvider = JvmDiskSpaceProvider(),
        idGenerator = idGenerator,
        clock = clock,
    )

    /** Run once at startup, before any session UI (§8.4). */
    val recoverSessionsUseCase = RecoverSessionsUseCase(sessionRepository, timelineRepository, clock)

    val sessionArchiveService: SessionArchiveService = ZipSessionArchiveService()
    val fileHashService: FileHashService = Sha256FileHashService()

    val processSessionUseCase = ProcessSessionUseCase(
        directories = directories,
        sessionRepository = sessionRepository,
        timelineRepository = timelineRepository,
        uploadStatusRepository = uploadStatusRepository,
        audioClipService = audioClipService,
        archiveService = sessionArchiveService,
        clock = clock,
        dispatchers = dispatchers,
    )

    /** Manual-only upload (§13 decision 34): invoked once per session when the examiner
     * presses Upload on the upload screen — no background worker, no automatic retry. */
    val uploadSessionUseCase = UploadSessionUseCase(
        sessionRepository = sessionRepository,
        uploadStatusRepository = uploadStatusRepository,
        uploadApi = uploadApi,
        fileHashService = fileHashService,
        clock = clock,
        dispatchers = dispatchers,
    )

    /** Computes the upload screen's session list on demand (§5.4, §13 decision 34) — no
     * persisted queue. */
    val eligibleUploadsQuery = EligibleUploadsQuery(sessionRepository, uploadStatusRepository)
}
