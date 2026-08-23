package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.RemoteConfig
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainMenuComponentTest {

    private fun sampleConfig() = RemoteConfig(
        schemaVersion = 1,
        configVersion = "v1",
        defaultLanguage = "en",
    )

    private class Harness(configurationRepository: FakeConfigurationRepository) {
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
        )
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
}
