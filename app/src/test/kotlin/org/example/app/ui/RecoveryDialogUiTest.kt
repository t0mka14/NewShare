package org.example.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.session.RecoverSessionsUseCase
import org.example.app.domain.session.RecoveryOutcome
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.domain.session.StartupRecovery
import org.example.app.navigation.DefaultMainMenuComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/** F-055: the crash-recovery notice is modal; only its buttons close it. */
@OptIn(ExperimentalTestApi::class)
class RecoveryDialogUiTest {

    private fun component(): DefaultMainMenuComponent {
        val recovery = StartupRecovery(
            RecoverSessionsUseCase(FakeSessionRepository(), FakeTimelineRepository(), FakeClock()),
            FakeSessionRepository(),
        ).also { it.publish(listOf(RecoveryOutcome.Recovered("2026-10-23_P01_s1", "s1", 100L))) }
        return DefaultMainMenuComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            configurationRepository = FakeConfigurationRepository(
                RemoteConfig(schemaVersion = 1, configVersion = "v1", defaultLanguage = "en"),
            ),
            dispatchers = TestCoroutineDispatchers(),
            onStartProtocolClicked = {},
            onUploadClicked = {},
            onSettingsClicked = {},
            onSessionBrowserClicked = {},
            onLanguageSelectedClicked = {},
            startupRecovery = recovery,
        )
    }

    @Test
    fun `F-055 Esc and an outside click leave the recovery dialog open`() = runDesktopComposeUiTest(1000, 820) {
        val component = component()
        setContent { MainMenuContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) }
        waitForIdle()
        onNodeWithTag(TestTags.MainMenu.RECOVERY_DIALOG).assertIsDisplayed()

        // The dialog lives in its own root of the scene: root 1 is the dialog, root 0 the menu behind it.
        onAllNodes(isRoot())[1].performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag(TestTags.MainMenu.RECOVERY_DIALOG).assertIsDisplayed()
        assertNotNull(component.state.value.recovery)

        onAllNodes(isRoot())[0].performMouseInput { click(Offset(3f, 3f)) }
        waitForIdle()
        onNodeWithTag(TestTags.MainMenu.RECOVERY_DIALOG).assertIsDisplayed()
        assertNotNull(component.state.value.recovery)
    }

    @Test
    fun `F-055 OK closes the recovery dialog`() = runDesktopComposeUiTest(1000, 820) {
        val component = component()
        setContent { MainMenuContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) }
        waitForIdle()
        onNodeWithTag(TestTags.MainMenu.RECOVERY_OK_BUTTON).performClick()
        waitForIdle()
        assertEquals(null, component.state.value.recovery)
        onNodeWithTag(TestTags.MainMenu.RECOVERY_DIALOG).assertDoesNotExist()
    }
}
