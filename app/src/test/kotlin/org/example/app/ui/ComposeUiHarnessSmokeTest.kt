package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultMainMenuComponent
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Proves the Compose Desktop UI-test harness (§10.3) works headless, under Skia
 * software rendering. The `-Dskiko.renderApi=SOFTWARE` / `-Djava.awt.headless=true`
 * JVM args are set on the `test` task in `app/build.gradle.kts`.
 *
 * This now drives a real screen (`MainMenuContent`/`DefaultMainMenuComponent`) found purely by
 * `TestTags` (§10.3) instead of the retired demo `HomeContent` (§12 "current state" — the demo
 * skeleton is gone as of this chunk). The full workflow-scenario suite (§10.3 items 1-8) is
 * qa-engineer's next task.
 *
 * CI note: verified locally on Linux with `DISPLAY` unset entirely (no X server running at all,
 * not even Xvfb) — `-Dskiko.renderApi=SOFTWARE` plus `-Djava.awt.headless=true` was sufficient;
 * no virtual framebuffer was required. If a future CI image's JDK/AWT combination behaves
 * differently, `xvfb-run ./gradlew :app:test` is the fallback, but it should not be needed by
 * default.
 */
class ComposeUiHarnessSmokeTest {

    private fun sampleConfig() = RemoteConfig(
        schemaVersion = 1,
        configVersion = "v1",
        defaultLanguage = "en",
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `runComposeUiTest renders and clicks the real MainMenuContent headlessly`() = runComposeUiTest {
        var startClicked = false
        val configurationRepository = FakeConfigurationRepository(sampleConfig())
        val dispatchers = TestCoroutineDispatchers()
        val component = DefaultMainMenuComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            configurationRepository = configurationRepository,
            dispatchers = dispatchers,
            onStartProtocolClicked = { startClicked = true },
            onUploadClicked = {},
            onSettingsClicked = {},
            onSessionBrowserClicked = {},
        )
        val localization = UiLocalization(LocalizedStringProvider(), "en", sampleConfig())

        setContent { MainMenuContent(component, localization) }

        onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).assertIsDisplayed().performClick()

        assertTrue(startClicked, "clicking Start should invoke the component's navigation callback")
    }
}
