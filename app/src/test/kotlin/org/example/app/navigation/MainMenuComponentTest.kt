package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.session.RecoverSessionsUseCase
import org.example.app.domain.session.RecoveryOutcome
import org.example.app.domain.session.StartupRecovery
import org.example.app.domain.session.StorageError
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MainMenuComponentTest {

    private fun sampleConfig() = RemoteConfig(
        schemaVersion = 1,
        configVersion = "v1",
        defaultLanguage = "en",
    )

    private fun startupRecovery(vararg outcomes: RecoveryOutcome): StartupRecovery =
        StartupRecovery(
            RecoverSessionsUseCase(FakeSessionRepository(), FakeTimelineRepository(), FakeClock()),
            FakeSessionRepository(),
        ).also { it.publish(outcomes.toList()) }

    private class Harness(
        configurationRepository: FakeConfigurationRepository,
        appVersion: String? = null,
        startupRecovery: StartupRecovery? = null,
        usingCachedConfig: StateFlow<Boolean> = MutableStateFlow(false),
    ) {
        val dispatchers = TestCoroutineDispatchers()
        var startClicks = 0
        var uploadClicks = 0
        var settingsClicks = 0
        var browserClicks = 0
        val selectedLanguages = mutableListOf<String>()

        val component: MainMenuComponent = DefaultMainMenuComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            configurationRepository = configurationRepository,
            dispatchers = dispatchers,
            onStartProtocolClicked = { startClicks++ },
            onUploadClicked = { uploadClicks++ },
            onSettingsClicked = { settingsClicks++ },
            onSessionBrowserClicked = { browserClicks++ },
            onLanguageSelectedClicked = { selectedLanguages += it },
            appVersion = appVersion,
            startupRecovery = startupRecovery,
            usingCachedConfig = usingCachedConfig,
        )
    }

    @Test
    fun `site name follows the active config, blank shows none`() {
        val repo = FakeConfigurationRepository(initialConfig = sampleConfig().copy(siteName = "Paris"))
        val h = Harness(repo)
        assertEquals("Paris", h.component.state.value.siteName)

        repo.setActiveConfig(sampleConfig().copy(siteName = "  "))
        h.dispatchers.scheduler.advanceUntilIdle()
        assertEquals(null, h.component.state.value.siteName)
    }

    @Test
    fun `the offline notice carries the cache date while a refresh fell back to the cache and clears after a success`() {
        val savedAt = Instant.parse("2026-10-20T08:00:00Z")
        val repo = FakeConfigurationRepository(sampleConfig()).apply { cachedAtInstant = savedAt }
        val offline = MutableStateFlow(false)
        val h = Harness(repo, usingCachedConfig = offline)
        assertEquals(null, h.component.state.value.offlineConfigSavedAt)

        offline.value = true
        h.dispatchers.scheduler.advanceUntilIdle()
        assertEquals(savedAt, h.component.state.value.offlineConfigSavedAt)

        // a config change while offline keeps the notice
        repo.setActiveConfig(sampleConfig().copy(siteName = "Paris"))
        h.dispatchers.scheduler.advanceUntilIdle()
        assertEquals(savedAt, h.component.state.value.offlineConfigSavedAt)

        offline.value = false
        h.dispatchers.scheduler.advanceUntilIdle()
        assertEquals(null, h.component.state.value.offlineConfigSavedAt)
    }

    @Test
    fun `app version is shown as given and absent when unknown`() {
        assertEquals("1.2.3", Harness(FakeConfigurationRepository(sampleConfig()), appVersion = "1.2.3").component.state.value.appVersion)
        assertEquals(null, Harness(FakeConfigurationRepository(sampleConfig())).component.state.value.appVersion)
    }

    @Test
    fun `start is disabled with no active config and clicking it does nothing`() {
        val h = Harness(FakeConfigurationRepository(initialConfig = null))
        assertFalse(h.component.state.value.startEnabled)

        h.component.onStartProtocol()
        assertEquals(0, h.startClicks)
    }

    @Test
    fun `start becomes enabled once a config is activated and reacts to it turning back to null`() {
        val repo = FakeConfigurationRepository(initialConfig = null)
        val h = Harness(repo)
        assertFalse(h.component.state.value.startEnabled)

        repo.setActiveConfig(sampleConfig())
        h.dispatchers.scheduler.advanceUntilIdle()
        assertTrue(h.component.state.value.startEnabled)

        h.component.onStartProtocol()
        assertEquals(1, h.startClicks)
    }

    @Test
    fun `upload, settings, and session browser always forward regardless of config state`() {
        val h = Harness(FakeConfigurationRepository(initialConfig = null))
        h.component.onUpload()
        h.component.onSettings()
        h.component.onSessionBrowser()

        assertEquals(1, h.uploadClicks)
        assertEquals(1, h.settingsClicks)
        assertEquals(1, h.browserClicks)
    }

    @Test
    fun `only protocols declaring an instructions URL reach the PDF button`() {
        val repo = FakeConfigurationRepository(initialConfig = null)
        val h = Harness(repo)
        assertTrue(h.component.state.value.protocolPdfs.isEmpty())

        repo.setActiveConfig(
            sampleConfig().copy(
                protocols = listOf(
                    protocol("With manual", "https://example.org/manual.pdf"),
                    protocol("Blank manual", "  "),
                    protocol("No manual", null),
                ),
            ),
        )
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(MainMenuComponent.ProtocolPdf("With manual", "https://example.org/manual.pdf")),
            h.component.state.value.protocolPdfs,
        )
    }

    @Test
    fun `a protocol listed under several projects gives one PDF entry, a single-project one keeps its project`() {
        val repo = FakeConfigurationRepository(initialConfig = null)
        val h = Harness(repo)

        repo.setActiveConfig(
            sampleConfig().copy(
                protocols = listOf(
                    protocol("Shared", "https://example.org/shared.pdf").copy(project = "A"),
                    protocol("Shared", "https://example.org/shared.pdf").copy(project = "B"),
                    protocol("Own", "https://example.org/own.pdf").copy(project = "A"),
                ),
            ),
        )
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(
                MainMenuComponent.ProtocolPdf("Shared", "https://example.org/shared.pdf", project = null),
                MainMenuComponent.ProtocolPdf("Own", "https://example.org/own.pdf", project = "A"),
            ),
            h.component.state.value.protocolPdfs,
        )
    }

    @Test
    fun `selecting a language forwards it to the root`() {
        val h = Harness(FakeConfigurationRepository(sampleConfig()))
        h.component.onLanguageSelected("cs")

        assertEquals(listOf("cs"), h.selectedLanguages)
    }

    private fun protocol(name: String, pdfUrl: String?) = Protocol(
        name = name,
        protocolInstructionsPdfUrl = pdfUrl,
        recordingsFileName = "\${taskIndex}",
    )

    @Test
    fun `recovery dialog state is present when a session was recovered or failed`() {
        val recovered = RecoveryOutcome.Recovered("2026-10-23_P01_s1", "s1", 100L)
        val failed = RecoveryOutcome.Failed("2026-10-23_P02_s2", StorageError.CorruptSessionMetadata("x"))

        val onlyRecovered = Harness(FakeConfigurationRepository(sampleConfig()), startupRecovery = startupRecovery(recovered))
        assertEquals(listOf("2026-10-23_P01_s1"), onlyRecovered.component.state.value.recovery?.recovered?.map { it.folderName })

        val onlyFailed = Harness(FakeConfigurationRepository(sampleConfig()), startupRecovery = startupRecovery(failed))
        val summary = onlyFailed.component.state.value.recovery
        assertTrue(summary != null && summary.recovered.isEmpty())
        assertEquals(listOf("2026-10-23_P02_s2"), summary?.failed?.map { it.folderName })
    }

    @Test
    fun `recovery dialog state is absent for no outcomes or only NotRecovered`() {
        val none = Harness(FakeConfigurationRepository(sampleConfig()), startupRecovery = startupRecovery())
        assertEquals(null, none.component.state.value.recovery)

        val clean = Harness(
            FakeConfigurationRepository(sampleConfig()),
            startupRecovery = startupRecovery(RecoveryOutcome.NotRecovered("a"), RecoveryOutcome.NotRecovered("b")),
        )
        assertEquals(null, clean.component.state.value.recovery)
    }

    @Test
    fun `OK dismisses the recovery dialog and it does not return when the menu is recreated`() {
        val recovery = startupRecovery(RecoveryOutcome.Recovered("2026-10-23_P01_s1", "s1", null))
        val first = Harness(FakeConfigurationRepository(sampleConfig()), startupRecovery = recovery)
        assertTrue(first.component.state.value.recovery != null)

        first.component.onRecoveryDismissed()
        assertEquals(null, first.component.state.value.recovery)
        assertEquals(0, first.browserClicks)

        val second = Harness(FakeConfigurationRepository(sampleConfig()), startupRecovery = recovery)
        assertEquals(null, second.component.state.value.recovery)
    }

    @Test
    fun `Open Sessions dismisses the recovery dialog and opens the session browser`() {
        val recovery = startupRecovery(RecoveryOutcome.Recovered("2026-10-23_P01_s1", "s1", null))
        val h = Harness(FakeConfigurationRepository(sampleConfig()), startupRecovery = recovery)

        h.component.onRecoveryOpenSessions()

        assertEquals(1, h.browserClicks)
        assertEquals(null, h.component.state.value.recovery)
        assertEquals(null, recovery.pending.value)
    }
}
