package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.MicGainApplier
import org.example.app.domain.config.PatientField
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.Question
import org.example.app.domain.config.QuestionType
import org.example.app.domain.config.QuestionnaireTask
import org.example.app.domain.config.Task
import org.example.app.domain.config.VideoTask
import org.example.app.domain.config.VocalSubtype
import org.example.app.domain.config.VocalTask
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.participant.ValidateParticipantInfoUseCase
import org.example.app.domain.settings.InstallationIdProvider
import org.example.app.domain.timeline.TaskInstance
import org.example.app.domain.video.VideoRecorderState
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeAudioInputGainControl
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.FakeVideoInputDeviceProvider
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultCalibrationComponent
import org.example.app.navigation.DefaultMainMenuComponent
import org.example.app.navigation.DefaultPatientInfoComponent
import org.example.app.navigation.DefaultProtocolPickerComponent
import org.example.app.navigation.DefaultSettingsComponent
import org.example.app.navigation.DefaultTaskComponent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.LocalDate

/**
 * F-124/F-126: the app's minimum window is 960x720 outer, i.e. about 944x681 of content (the
 * 16 px / 39 px frame of an 800x600 window gave 784x561 in `TaskLayoutUiTest`). Each screen's main
 * controls must stay reachable (directly or by scrolling) with a usable height at that size.
 * The waveform editor is covered separately.
 */
@OptIn(ExperimentalTestApi::class)
class MinimumWindowSizeUiTest {

    private val width = 944
    private val height = 681
    private val localization = UiLocalization(LocalizedStringProvider(), "en", null)

    private fun androidx.compose.ui.test.ComposeUiTest.assertReachable(vararg tags: String) {
        tags.forEach { tag ->
            // Directly visible, or else inside a scroll container that can bring it into view.
            runCatching { onNodeWithTag(tag).assertIsDisplayed() }
                .onFailure { onNodeWithTag(tag).performScrollTo().assertIsDisplayed() }
            val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(bounds.height > 10.dp, "$tag has height ${bounds.height} at ${width}x$height")
        }
    }

    private fun taskComponent(task: Task, videoState: VideoRecorderState = VideoRecorderState.Previewing) =
        DefaultTaskComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            taskInstance = TaskInstance(taskIndex = 0, repetition = 1, task = task),
            recorder = FakeContinuousSessionRecorder(FakeClock()).also {
                kotlinx.coroutines.runBlocking { it.startWriting(Path.of("master.wav")) }
            },
            dispatchers = TestCoroutineDispatchers(),
            positionInProtocol = 1,
            totalInstanceCount = 1,
            availableDevices = listOf(AudioInputDevice(id = "default", name = "Default Mic", eligible = true)),
            currentDevice = AudioInputDevice(id = "default", name = "Default Mic", eligible = true),
            resolvedAudioExamplePath = Path.of("example.wav"),
            audioPlaybackService = FakeAudioPlaybackService(),
            eventLogger = { _, _, _ -> },
            videoFrames = MutableStateFlow(null),
            videoState = MutableStateFlow(videoState),
            nextPartFile = { Path.of("part2.wav") },
            onResumeRecorded = { _, _ -> },
            onTaskFinished = {},
        )

    @Test
    fun `main menu buttons are reachable`() = runDesktopComposeUiTest(width, height) {
        val component = DefaultMainMenuComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            configurationRepository = FakeConfigurationRepository(
                org.example.app.domain.config.RemoteConfig(schemaVersion = 1, configVersion = "v1", defaultLanguage = "en"),
            ),
            dispatchers = TestCoroutineDispatchers(),
            onStartProtocolClicked = {},
            onUploadClicked = {},
            onSettingsClicked = {},
            onSessionBrowserClicked = {},
            onLanguageSelectedClicked = {},
        )
        setContent { MainMenuContent(component, localization) }
        waitForIdle()
        assertReachable(
            TestTags.MainMenu.START_PROTOCOL_BUTTON,
            TestTags.MainMenu.UPLOAD_BUTTON,
            TestTags.MainMenu.SETTINGS_BUTTON,
            TestTags.MainMenu.SESSION_BROWSER_BUTTON,
        )
    }

    @Test
    fun `settings controls are reachable`() = runDesktopComposeUiTest(width, height) {
        val settingsRepository = FakeAppSettingsRepository()
        val configurationRepository = FakeConfigurationRepository()
        val dispatchers = TestCoroutineDispatchers()
        val component = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            videoDeviceProvider = FakeVideoInputDeviceProvider(),
            deviceProvider = FakeAudioInputDeviceProvider(
                listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE, FakeAudioInputDeviceProvider.SECONDARY_DEVICE),
            ),
            settingsRepository = settingsRepository,
            configurationRepository = configurationRepository,
            refreshConfigurationUseCase = org.example.app.domain.config.RefreshConfigurationUseCase(
                settingsRepository, FakeConfigApi(), configurationRepository,
            ),
            micGainApplier = MicGainApplier(FakeAudioInputGainControl(), settingsRepository, dispatchers),
            installationIdProvider = InstallationIdProvider(settingsRepository) { "generated-pc-id" },
            dispatchers = dispatchers,
        )
        setContent { SettingsContent(component, localization) {} }
        waitForIdle()
        assertReachable(
            TestTags.Settings.REFRESH_CONFIG_BUTTON,
            TestTags.Settings.INSTALLATION_ID_FIELD,
            TestTags.Settings.BACK_BUTTON,
        )
    }

    @Test
    fun `protocol picker buttons are reachable`() = runDesktopComposeUiTest(width, height) {
        val protocols = (1..6).map { Protocol(name = "Protocol $it", recordingsFileName = "\${taskIndex}.wav") }
        val component = DefaultProtocolPickerComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            protocols = protocols,
            onProtocolSelectedClicked = {},
            onBackClicked = {},
        )
        setContent { ProtocolPickerContent(component, localization) }
        waitForIdle()
        assertReachable(
            TestTags.ProtocolPicker.protocolButton("Protocol 1"),
            TestTags.ProtocolPicker.protocolButton("Protocol 6"),
            TestTags.ProtocolPicker.BACK_BUTTON,
        )
    }

    @Test
    fun `patient form buttons are reachable`() = runDesktopComposeUiTest(width, height) {
        val fields = listOf("current_date", "sex", "education", "patient_code") + (1..6).map { "custom_$it" }
        val component = DefaultPatientInfoComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            fields = fields.map { PatientField(name = it, labelKey = it) },
            examinationDate = LocalDate.of(2026, 9, 29),
            validateParticipantInfoUseCase = ValidateParticipantInfoUseCase(),
            onValidated = {},
        )
        setContent { PatientInfoContent(component, localization, onBack = {}) }
        waitForIdle()
        assertReachable(TestTags.PatientInfo.CONTINUE_BUTTON, TestTags.PatientInfo.BACK_BUTTON)
    }

    @Test
    fun `calibration confirm button is reachable`() = runDesktopComposeUiTest(width, height) {
        val recorder = FakeContinuousSessionRecorder(FakeClock())
        val device = FakeAudioInputDeviceProvider.DEFAULT_DEVICE
        kotlinx.coroutines.runBlocking { recorder.startMonitoring(device) }
        val component = DefaultCalibrationComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            recorder = recorder,
            dispatchers = TestCoroutineDispatchers(),
            loudness = 0.2..0.6,
            initialDevice = device,
            availableDevices = listOf(device, FakeAudioInputDeviceProvider.SECONDARY_DEVICE),
            onConfirmed = {},
            onDevicePicked = {},
        )
        setContent { CalibrationContent(component, localization, onBack = {}) }
        waitForIdle()
        assertReachable(TestTags.Calibration.LEVEL_INDICATOR, TestTags.Calibration.CONFIRM_BUTTON, TestTags.Calibration.DEVICE_SELECT)
    }

    @Test
    fun `speech task controls are reachable`() = runDesktopComposeUiTest(width, height) {
        val task = VocalTask(titleKey = "t", subtype = VocalSubtype.PHONATION, instructionKeys = listOf("a", "b"))
        setContent { TaskContent(taskComponent(task), localization) }
        waitForIdle()
        assertReachable(TestTags.Task.LEVEL_INDICATOR, TestTags.Task.START_BUTTON, TestTags.Task.NEXT_BUTTON)
    }

    @Test
    fun `filming speech task controls are reachable`() = runDesktopComposeUiTest(width, height) {
        val task = VocalTask(titleKey = "t", subtype = VocalSubtype.PHONATION, instructionKeys = listOf("a", "b"), recordVideo = true)
        setContent { TaskContent(taskComponent(task), localization) }
        waitForIdle()
        assertReachable(TestTags.Task.VIDEO_PREVIEW, TestTags.Task.START_BUTTON, TestTags.Task.NEXT_BUTTON)
    }

    @Test
    fun `video task controls are reachable`() = runDesktopComposeUiTest(width, height) {
        val task = VideoTask(titleKey = "t", instructionKeys = listOf("a", "b"))
        setContent { TaskContent(taskComponent(task), localization) }
        waitForIdle()
        assertReachable(TestTags.Task.VIDEO_PREVIEW, TestTags.Task.START_BUTTON, TestTags.Task.NEXT_BUTTON)
    }

    @Test
    fun `questionnaire answers and next button are reachable`() = runDesktopComposeUiTest(width, height) {
        val task = QuestionnaireTask(
            titleKey = "q",
            questions = listOf(
                Question(QuestionType.OPEN, "age", "q.age", questionRegex = ".*"),
                Question(QuestionType.SINGLE_CHOICE, "hand", "q.hand", questionOptions = listOf("left", "right", "both")),
                Question(QuestionType.MULTIPLE_CHOICE, "sym", "q.sym", questionOptions = listOf("cough", "cold", "fever")),
                Question(QuestionType.OPEN, "note", "q.note", questionRegex = ".*"),
            ),
        )
        setContent { TaskContent(taskComponent(task), localization) }
        waitForIdle()
        assertReachable(
            TestTags.Questionnaire.answerField("age"),
            TestTags.Questionnaire.answerOption("hand", "right"),
            TestTags.Questionnaire.answerOption("sym", "fever"),
            TestTags.Questionnaire.answerField("note"),
            TestTags.Task.NEXT_BUTTON,
        )
    }
}
