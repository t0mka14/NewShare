package org.example.app.ui.scenario

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.AppContainer
import org.example.app.fakes.ConfigFixtures
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeIdGenerator
import org.example.app.fakes.TestAppDirectories
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultRootComponent
import org.example.app.ui.RootContent
import org.example.app.ui.TestTags
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * §10.3 workflow scenarios 3 (validation) and 5 (offline config). Unlike the scenarios in
 * [ScenarioHarness], neither of these ever navigates into `SessionComponent` — scenario 3 is
 * blocked at `PatientInfo`, scenario 5 never leaves the main menu / blocking screen — so both are
 * safe to drive through the *real* `DefaultRootComponent`/`RootContent`/`AppContainer` (only
 * `directories`/`clock`/`idGenerator`/`dispatchers` swapped for fakes/temp-dir, exactly as
 * `RootComponentTest` already does at the component level; this suite adds the Compose-rendered,
 * testTag-driven layer on top).
 */
class ValidationAndOfflineConfigScenarioTest {

    private fun buildContainer(tempDir: Path): AppContainer = AppContainer(
        directories = TestAppDirectories(tempDir),
        clock = FakeClock(),
        idGenerator = FakeIdGenerator(),
        dispatchers = TestCoroutineDispatchers(),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 3 - an invalid patient field blocks continuation and shows the error`(@TempDir tempDir: Path) =
        runComposeUiTest {
            val container = buildContainer(tempDir)
            container.rawConfigCache.write(ConfigFixtures.fullProtocol)
            container.configurationRepository.loadCached()
            val root = DefaultRootComponent(DefaultComponentContext(LifecycleRegistry()), container)

            setContent { RootContent(root) }

            onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).performClick()
            onNodeWithTag(TestTags.PatientInfo.field("code")).performTextInput("HC005")
            // visitNumber's configured regex is `V\d+` (§ fixture) — "not-a-visit-number" fails it.
            onNodeWithTag(TestTags.PatientInfo.field("visitNumber")).performTextInput("not-a-visit-number")
            onNodeWithTag(TestTags.PatientInfo.CONTINUE_BUTTON).performClick()

            onNodeWithTag(TestTags.PatientInfo.fieldError("visitNumber")).assertIsDisplayed()
            onNodeWithTag(TestTags.PatientInfo.ERROR_TEXT).assertIsDisplayed()
            // Still on PatientInfo — never reached Session/Calibration.
            onNodeWithTag(TestTags.PatientInfo.CONTINUE_BUTTON).assertIsDisplayed()
            onNodeWithTag(TestTags.Calibration.CONFIRM_BUTTON).assertDoesNotExist()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 5a - no cache renders the blocking configuration-required screen from fallback strings`(
        @TempDir tempDir: Path,
    ) = runComposeUiTest {
        val container = buildContainer(tempDir)
        // No rawConfigCache.write(...): first run offline, no cache (§6.1 pt 4) — the same end
        // state a fetch attempt against a NetworkUnavailable `ConfigApi` with no cache produces;
        // `RefreshConfigurationUseCase`'s handling of that fetch result is already covered by
        // `RefreshConfigurationUseCaseTest`/`SettingsComponentTest` at the unit/component level.
        container.configurationRepository.loadCached()
        val root = DefaultRootComponent(DefaultComponentContext(LifecycleRegistry()), container)

        setContent { RootContent(root) }

        onNodeWithTag(TestTags.Blocking.CONFIGURATION_REQUIRED_MESSAGE).assertIsDisplayed()
        onNodeWithTag(TestTags.Blocking.OPEN_SETTINGS_BUTTON).assertIsDisplayed()
        onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 5b - a cached config makes the main menu usable offline`(@TempDir tempDir: Path) =
        runComposeUiTest {
            val container = buildContainer(tempDir)
            container.rawConfigCache.write(ConfigFixtures.fullProtocol)
            container.configurationRepository.loadCached()
            val root = DefaultRootComponent(DefaultComponentContext(LifecycleRegistry()), container)

            setContent { RootContent(root) }

            onNodeWithTag(TestTags.Blocking.CONFIGURATION_REQUIRED_MESSAGE).assertDoesNotExist()
            onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).assertIsDisplayed().assertIsEnabled()
        }
}
