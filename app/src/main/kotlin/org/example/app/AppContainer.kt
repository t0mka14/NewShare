package org.example.app

import org.example.app.domain.AppDirectories
import org.example.app.domain.AudioRecorder
import org.example.app.domain.Clock
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.DefaultCoroutineDispatchers
import org.example.app.domain.IdGenerator
import org.example.app.domain.RealClock
import org.example.app.domain.audio.AudioInputDeviceProvider
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.domain.config.ConfigApi
import org.example.app.infrastructure.DefaultAppDirectories
import org.example.app.infrastructure.JvmAudioRecorder
import org.example.app.infrastructure.UuidIdGenerator
import org.example.app.infrastructure.audio.JvmAudioInputDeviceProvider
import org.example.app.infrastructure.audio.JvmContinuousSessionRecorder
import org.example.app.infrastructure.config.RawConfigCache
import org.example.app.infrastructure.lock.SingleInstanceLock
import org.example.app.infrastructure.network.KtorConfigApi
import java.io.File

/**
 * Manual DI root (§5.2). Every port gets a production binding here; tests build
 * their own container from fakes. No DI framework, no singletons.
 */
class AppContainer(
    val directories: AppDirectories = DefaultAppDirectories(),
    val clock: Clock = RealClock(),
    val idGenerator: IdGenerator = UuidIdGenerator(),
    val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers(),
) {
    // Demo-era recorder; replaced by ContinuousSessionRecorder when the session
    // components land (§12 "Current state").
    val audioRecorder: AudioRecorder = JvmAudioRecorder()

    /** Acquired at the top of Main before any other startup work (§5.2). */
    val singleInstanceLock: SingleInstanceLock = SingleInstanceLock(directories)

    val audioInputDeviceProvider: AudioInputDeviceProvider = JvmAudioInputDeviceProvider()

    /**
     * The recorder is session-scoped (§5.2: SessionComponent owns it) — a factory,
     * not a shared instance, so no recording state outlives a session.
     */
    val sessionRecorderFactory: () -> ContinuousSessionRecorder =
        { JvmContinuousSessionRecorder(dispatchers) }

    val configApi: ConfigApi = KtorConfigApi()

    val rawConfigCache: RawConfigCache = RawConfigCache(directories)

    val dataDir: File get() = directories.dataRoot.toFile()
    val sessionsDir: File get() = directories.sessionsDir.toFile()
}
