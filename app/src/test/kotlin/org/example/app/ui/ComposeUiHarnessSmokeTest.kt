package org.example.app.ui

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.navigation.DefaultHomeComponent
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Proves the Compose Desktop UI-test harness (§10.3) works headless, under Skia
 * software rendering. The `-Dskiko.renderApi=SOFTWARE` / `-Djava.awt.headless=true`
 * JVM args are set on the `test` task in `app/build.gradle.kts` — this is
 * groundwork only; the real workflow scenarios (§10.3, items 1-8) land once the
 * session/task screens exist. See the class doc on [TestTags] for the tagging
 * convention these tests will drive against.
 *
 * CI note: verified locally on Linux with `DISPLAY` unset entirely (no X server
 * running at all, not even Xvfb) — `-Dskiko.renderApi=SOFTWARE` plus
 * `-Djava.awt.headless=true` (both set as `test` task JVM args in
 * `app/build.gradle.kts`) was sufficient; no virtual framebuffer was required.
 * If a future CI image's JDK/AWT combination behaves differently, `xvfb-run
 * ./gradlew :app:test` is the fallback, but it should not be needed by default.
 */
class ComposeUiHarnessSmokeTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `runComposeUiTest renders and clicks the real HomeContent headlessly`() = runComposeUiTest {
        var navigated = false
        val component = DefaultHomeComponent(
            context = DefaultComponentContext(LifecycleRegistry()),
            onNavigateToRecorder = { navigated = true },
        )

        setContent { HomeContent(component) }

        // HomeContent has no testTag yet (see TestTags doc) so this drives it by
        // text, still proving the full render -> find -> click -> component-callback
        // round trip works headlessly.
        onNodeWithText("Go to Recorder").assertIsDisplayed().performClick()

        assertTrue(navigated, "clicking the Home button should invoke the component's navigation callback")
    }

    /**
     * `HomeContent`/`RecorderContent` currently have no `Modifier.testTag(...)`
     * (§10.3 requires one on every interactive composable) — this is a gap for
     * the ui-engineer to close, flagged to the lead in the phase-1 QA report.
     * Until real screens carry tags, this test exercises the TestTags-driven
     * find-by-tag + click pattern the rest of the suite will use, against a
     * minimal local composable (main-source components are not modified here).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `runComposeUiTest drives a tagged button via TestTags`() = runComposeUiTest {
        var clicked = false

        setContent {
            Button(
                onClick = { clicked = true },
                modifier = Modifier.testTag(TestTags.Home.RECORDER_BUTTON),
            ) {
                Text("Tagged action")
            }
        }

        onNodeWithTag(TestTags.Home.RECORDER_BUTTON).assertIsDisplayed().performClick()

        assertTrue(clicked)
    }
}
