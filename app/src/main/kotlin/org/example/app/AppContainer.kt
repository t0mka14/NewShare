package org.example.app

import org.example.app.domain.AppDirectories
import org.example.app.domain.Clock
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.DefaultCoroutineDispatchers
import org.example.app.domain.IdGenerator
import org.example.app.domain.RealClock
import org.example.app.domain.audio.AudioClipService
import org.example.app.domain.audio.AudioInputDeviceProvider
import org.example.app.domain.audio.AudioPlaybackService
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.domain.audio.WaveformService
import org.example.app.domain.config.ConfigApi
import org.example.app.infrastructure.DefaultAppDirectories
import org.example.app.infrastructure.UuidIdGenerator
import org.example.app.infrastructure.audio.JvmAudioClipService
import org.example.app.infrastructure.audio.JvmAudioInputDeviceProvider
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
    val audioClipService: AudioClipService = JvmAudioClipService(),
    val waveformService: WaveformService = JvmWaveformService(),
    val audioPlaybackService: AudioPlaybackService = JvmAudioPlaybackService(),
    /**
     * The recorder is session-scoped (§5.2: SessionComponent owns it) — a factory,
     * not a shared instance, so no recording state outlives a session.
     */
    val sessionRecorderFactory: () -> ContinuousSessionRecorder =
        { JvmContinuousSessionRecorder(dispatchers) },
) {
    /** Acquired at the top of Main before any other startup work (§5.2). */
    val singleInstanceLock: SingleInstanceLock = SingleInstanceLock(directories)

    val rawConfigCache: RawConfigCache = RawConfigCache(directories)

    val configurationRepository: ConfigurationRepository = JsonConfigurationRepository(rawConfigCache)

    val appSettingsRepository: AppSettingsRepository = JsonAppSettingsRepository(directories)

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
