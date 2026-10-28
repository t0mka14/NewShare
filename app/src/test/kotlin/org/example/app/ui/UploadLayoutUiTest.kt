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
import org.example.app.domain.upload.EligibleUploadsQuery
import org.example.app.domain.upload.UploadAttempt
import org.example.app.domain.upload.UploadResult
import org.example.app.domain.upload.UploadSessionUseCase
import org.example.app.domain.upload.UploadStatus
import org.example.app.domain.upload.UploadStatusValue
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeFileHashService
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeUploadApi
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultUploadComponent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** F-126: the list, a failed row's message and the progress bar must not push Upload / Back out of the window at the minimum size. */
@OptIn(ExperimentalTestApi::class)
class UploadLayoutUiTest {

    private fun check(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val dispatchers = TestCoroutineDispatchers()
        val sessionRepository = FakeSessionRepository()
        val uploadStatusRepository = FakeUploadStatusRepository()
        repeat(6) { i ->
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
            sessionRepository.seedArchiveExists(folder)
            if (i == 0) {
                uploadStatusRepository.write(
                    folder,
                    UploadStatus(
                        status = UploadStatusValue.Failed,
                        attempts = listOf(UploadAttempt(at = "2026-07-01T10:00:00Z", outcome = "NetworkFailure")),
                        attemptCount = 1,
                    ),
                )
            }
        }
        val component = DefaultUploadComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            eligibleUploadsQuery = EligibleUploadsQuery(sessionRepository, uploadStatusRepository),
            uploadSessionUseCase = UploadSessionUseCase(
                sessionRepository = sessionRepository,
                uploadStatusRepository = uploadStatusRepository,
                uploadApi = FakeUploadApi().apply { nextResult = UploadResult.Success(null) },
                settingsRepository = FakeAppSettingsRepository(),
                appVersion = "1.2.3",
                fileHashService = FakeFileHashService(),
                clock = FakeClock(),
                dispatchers = dispatchers,
            ),
            dispatchers = dispatchers,
            onBackClicked = {},
        )
        setContent { UploadContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) }
        waitForIdle()

        // The failed row sits first in the list (same order as listSessions), so its message is on screen.
        onNodeWithTag(TestTags.Upload.sessionErrorText("session-0")).assertExists()
        listOf(TestTags.Upload.UPLOAD_BUTTON, TestTags.Upload.BACK_BUTTON).forEach { tag ->
            onNodeWithTag(tag).assertIsDisplayed()
            val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(bounds.height > 10.dp, "$tag has height ${bounds.height} at ${width}x$height")
        }
    }

    @Test
    fun `upload and back are fully visible with a full list and a failed row at 960x720`() = check(960, 720)

    @Test
    fun `upload and back are fully visible with a full list and a failed row in the 944x681 content of a 960x720 window`() = check(944, 681)

    @Test
    fun `upload and back are fully visible with a full list and a failed row at the old 800x600 default`() = check(800, 600)

    @Test
    fun `upload and back are fully visible with a full list and a failed row at 1000x820`() = check(1000, 820)
}
