package org.example.app.ui.scenario

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.example.app.fakes.ConfigFixtures
import org.example.app.ui.TestTags
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * §10.3 workflow scenarios 3 (validation) and 5 (offline config). Neither of these ever
 * navigates into `SessionComponent` — scenario 3 is blocked at `PatientInfo`, scenario 5 never
 * leaves the main menu / blocking screen — but they use [ScenarioHarness]/[ScenarioApp] like
 * every other scenario for consistency (§10.3: one harness, one pattern).
 */
class ValidationAndOfflineConfigScenarioTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 3 - an invalid patient field blocks continuation and shows the error`(@TempDir tempDir: Path) =
        runComposeUiTest {
            val harness = ScenarioHarness(tempDir)
            harness.loadConfig(ConfigFixtures.fullProtocol)

            setContent { ScenarioApp(harness) }

            onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).performClick()
            // §3 follow-up: `fullProtocol` defines two protocols ("Share"/"QuestionnaireOnly"),
            // so Start now opens the protocol picker first (skipped entirely for single-protocol
            // configs) before patient info.
            onNodeWithTag(TestTags.ProtocolPicker.protocolButton("Share")).performClick()
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
        val harness = ScenarioHarness(tempDir)
        // No loadConfig(...) call: first run offline, no cache (§6.1 pt 4) — the same end
        // state a fetch attempt against a NetworkUnavailable `ConfigApi` with no cache produces;
        // `RefreshConfigurationUseCase`'s handling of that fetch result is already covered by
        // `RefreshConfigurationUseCaseTest`/`SettingsComponentTest` at the unit/component level.
        harness.container.configurationRepository.loadCached()

        setContent { ScenarioApp(harness) }

        onNodeWithTag(TestTags.Blocking.CONFIGURATION_REQUIRED_MESSAGE).assertIsDisplayed()
        onNodeWithTag(TestTags.Blocking.OPEN_SETTINGS_BUTTON).assertIsDisplayed()
        onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 5b - a cached config makes the main menu usable offline`(@TempDir tempDir: Path) =
        runComposeUiTest {
            val harness = ScenarioHarness(tempDir)
            harness.loadConfig(ConfigFixtures.fullProtocol)

            setContent { ScenarioApp(harness) }

            onNodeWithTag(TestTags.Blocking.CONFIGURATION_REQUIRED_MESSAGE).assertDoesNotExist()
            onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).assertIsDisplayed().assertIsEnabled()
        }
}
