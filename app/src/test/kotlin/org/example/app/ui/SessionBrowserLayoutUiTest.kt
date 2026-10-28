package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.session.Examination
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.navigation.DefaultSessionBrowserComponent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** F-124: a full session list must not push Go to upload / Back out of the window at the minimum size. */
@OptIn(ExperimentalTestApi::class)
class SessionBrowserLayoutUiTest {

    private fun check(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val sessionRepository = FakeSessionRepository()
        repeat(10) { i ->
            val folder = "2026-07-%02d_P$i".format(i + 1)
            sessionRepository.createSessionDirectory(folder)
            sessionRepository.writeExamination(
                folder,
                Examination(
                    sessionId = "session-$i",
                    installationId = "install-1",
                    protocolName = "Share",
                    configVersion = "v1",
                    startedAt = "2026-07-%02dT09:00:00Z".format(i + 1),
                ),
            )
        }
        val component = DefaultSessionBrowserComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            sessionRepository = sessionRepository,
            uploadStatusRepository = FakeUploadStatusRepository(),
            onOpenEditorClicked = {},
            onReprocessClicked = {},
            onGoToUploadClicked = {},
            onBackClicked = {},
        )
        setContent { SessionBrowserContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) }
        waitForIdle()

        // Newest first, so session-9 is the first row.
        listOf(
            TestTags.SessionBrowser.openEditorButton("session-9"),
            TestTags.SessionBrowser.reprocessButton("session-9"),
            TestTags.SessionBrowser.GO_TO_UPLOAD_BUTTON,
            TestTags.SessionBrowser.BACK_BUTTON,
        ).forEach { tag ->
            onNodeWithTag(tag).assertIsDisplayed()
            val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(bounds.height > 10.dp, "$tag has height ${bounds.height} at ${width}x$height")
        }
    }

    @Test
    fun `every session browser button is fully visible with a full list at 960x720`() = check(960, 720)

    @Test
    fun `every session browser button is fully visible with a full list in the 944x681 content of a 960x720 window`() = check(944, 681)

    @Test
    fun `every session browser button is fully visible with a full list at the old 800x600 default`() = check(800, 600)

    @Test
    fun `every session browser button is fully visible with a full list at 1000x820`() = check(1000, 820)
}
