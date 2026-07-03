package org.example.app

import org.example.app.domain.AppDirectories
import org.example.app.domain.Clock
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.DefaultCoroutineDispatchers
import org.example.app.domain.IdGenerator
import org.example.app.domain.RealClock
import org.example.app.domain.audio.AudioInputDeviceProvider
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.domain.config.ConfigApi
import org.example.app.infrastructure.DefaultAppDirectories
import org.example.app.infrastructure.UuidIdGenerator
import org.example.app.infrastructure.audio.JvmAudioInputDeviceProvider
import org.example.app.infrastructure.audio.JvmContinuousSessionRecorder
import org.example.app.domain.config.ConfigurationRepository
import org.example.app.domain.config.RefreshConfigurationUseCase
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.participant.ValidateParticipantInfoUseCase
import org.example.app.domain.session.RecoverSessionsUseCase
import org.example.app.domain.session.StartSessionUseCase
import org.example.app.domain.settings.AppSettingsRepository
import org.example.app.domain.timeline.TimelineRepository
import org.example.app.infrastructure.config.JsonConfigurationRepository
import org.example.app.infrastructure.config.RawConfigCache
import org.example.app.infrastructure.lock.SingleInstanceLock
import org.example.app.infrastructure.network.KtorConfigApi
import org.example.app.infrastructure.persistence.JsonAppSettingsRepository
import org.example.app.infrastructure.persistence.JsonSessionRepository
import org.example.app.infrastructure.persistence.JsonTimelineRepository
import org.example.app.infrastructure.persistence.JsonUploadStatusRepository
import org.example.app.infrastructure.persistence.JvmDiskSpaceProvider

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
    val audioInputDeviceProvider: AudioInputDeviceProvider = JvmAudioInputDeviceProvider(),
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
}
