package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultMainMenuComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The main menu explains a disabled New examination button when the config has no protocol. */
@OptIn(ExperimentalTestApi::class)
class MainMenuNoProtocolUiTest {

    private val localization = UiLocalization(LocalizedStringProvider(), "en", null)

    private fun config(protocols: List<Protocol>) =
        RemoteConfig(schemaVersion = 1, configVersion = "v1", defaultLanguage = "en", protocols = protocols)

    private fun component(config: RemoteConfig?) = DefaultMainMenuComponent(
        componentContext = DefaultComponentContext(LifecycleRegistry()),
        configurationRepository = FakeConfigurationRepository(initialConfig = config),
        dispatchers = TestCoroutineDispatchers(),
        onStartProtocolClicked = {},
        onUploadClicked = {},
        onSettingsClicked = {},
        onSessionBrowserClicked = {},
        onLanguageSelectedClicked = {},
    )

    @Test
    fun `zero protocols shows the line and disables New examination at several sizes`() {
        listOf(944 to 681, 1000 to 820).forEach { (w, h) ->
            runDesktopComposeUiTest(w, h) {
                val component = component(config(emptyList()))
                setContent { MainMenuContent(component, localization) }
                waitForIdle()
                assertEquals("No protocol is currently configured for this site.", localization.resolvePlain("mainMenu.noProtocolConfigured"))
                onNodeWithTag(TestTags.MainMenu.NO_PROTOCOL_MESSAGE).assertIsDisplayed()
                    .assertTextEquals(localization.resolvePlain("mainMenu.noProtocolConfigured"))
                onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `one protocol hides the line and enables New examination`() = runDesktopComposeUiTest(944, 681) {
        val component = component(config(listOf(Protocol(name = "P", recordingsFileName = "\${taskIndex}"))))
        setContent { MainMenuContent(component, localization) }
        waitForIdle()
        onNodeWithTag(TestTags.MainMenu.NO_PROTOCOL_MESSAGE).assertDoesNotExist()
        onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).assertIsEnabled()
    }

    @Test
    fun `no config hides the line`() = runDesktopComposeUiTest(944, 681) {
        val component = component(null)
        setContent { MainMenuContent(component, localization) }
        waitForIdle()
        onNodeWithTag(TestTags.MainMenu.NO_PROTOCOL_MESSAGE).assertDoesNotExist()
        onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).assertIsNotEnabled()
    }
}
