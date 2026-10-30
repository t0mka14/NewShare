package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.AudioError
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.InterruptionReason
import org.example.app.domain.config.Question
import org.example.app.domain.config.QuestionType
import org.example.app.domain.config.QuestionnaireTask
import org.example.app.domain.config.InfoTask
import org.example.app.domain.config.VocalSubtype
import org.example.app.domain.config.VocalTask
import org.example.app.domain.session.TaskRecord
import org.example.app.domain.timeline.TaskInstance
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class TaskComponentTest {

    private data class LoggedEvent(val type: TimelineEventType, val take: Int?, val reason: String?)

    private class Harness(
        task: org.example.app.domain.config.Task,
        taskIndex: Int = 3,
        repetition: Int = 1,
        withRecorder: Boolean = true,
        audioExamplePath: Path? = null,
        previousTaskSkipped: Boolean = false,
    ) {
        val clock = FakeClock()
        val dispatchers = TestCoroutineDispatchers()
        val recorder = if (withRecorder) FakeContinuousSessionRecorder(clock) else null
        val events = mutableListOf<LoggedEvent>()
        val finished = mutableListOf<TaskRecord>()
        val resumeCalls = mutableListOf<Pair<AudioInputDevice, Path>>()
        val instance = TaskInstance(taskIndex = taskIndex, repetition = repetition, task = task)
        val devices = listOf(AudioInputDevice(id = "default", name = "Default Mic", eligible = true))
        val audioPlaybackService = FakeAudioPlaybackService()
        val resolvedAudioExamplePath = audioExamplePath

        init {
            recorder?.let {
                kotlinx.coroutines.runBlocking { it.startWriting(Path.of("master.wav")) }
            }
        }

        val component: TaskComponent = DefaultTaskComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            taskInstance = instance,
            recorder = recorder,
            dispatchers = dispatchers,
            positionInProtocol = taskIndex + 1,
            totalInstanceCount = taskIndex + 1,
            previousTaskSkipped = previousTaskSkipped,
            availableDevices = devices,
            currentDevice = devices.first(),
            resolvedAudioExamplePath = resolvedAudioExamplePath,
            audioPlaybackService = audioPlaybackService,
            eventLogger = { type, take, reason -> events += LoggedEvent(type, take, reason) },
            nextPartFile = { Path.of("session_master.part2.wav") },
            onResumeRecorded = { device, partFile -> resumeCalls += device to partFile },
            onTaskFinished = { record -> finished += record },
        )
    }

    private val vocalTask = VocalTask(
        titleKey = "vocal.title",
        subtype = VocalSubtype.PHONATION,
        canRepeat = true,
        canSkip = true,
    )

    @Test
    fun `idle state allows start and skip only`() {
        val h = Harness(vocalTask)
        val content = h.component.state.value.content as TaskComponent.Content.Vocal
        assertTrue(content.screenState is TaskScreenState.Idle)
        val buttons = h.component.state.value.buttons
        assertTrue(buttons.startEnabled)
        assertFalse(buttons.stopEnabled)
        assertFalse(buttons.repeatEnabled)
        assertFalse(buttons.nextEnabled)
        assertTrue(buttons.skipEnabled)
    }

    @Test
    fun `start then stop moves Idle to Capturing to Stopped and logs events with take 1`() {
        val h = Harness(vocalTask)

        h.component.onStart()
        var buttons = h.component.state.value.buttons
        assertTrue((h.component.state.value.content as TaskComponent.Content.Vocal).screenState is TaskScreenState.Capturing)
        assertFalse(buttons.startEnabled)
        assertTrue(buttons.stopEnabled)

        h.component.onStop()
        buttons = h.component.state.value.buttons
        assertTrue((h.component.state.value.content as TaskComponent.Content.Vocal).screenState is TaskScreenState.Stopped)
        assertFalse(buttons.stopEnabled)
        assertTrue(buttons.repeatEnabled) // canRepeat = true
        assertTrue(buttons.nextEnabled)

        assertEquals(
            listOf(
                LoggedEvent(TimelineEventType.TASK_SCREEN_ENTERED, null, null),
                LoggedEvent(TimelineEventType.START_BUTTON_PRESSED, 1, null),
                LoggedEvent(TimelineEventType.STOP_BUTTON_PRESSED, 1, null),
            ),
            h.events,
        )
    }

    @Test
    fun `worked example — 2 repetitions each tried 3 times produces 6 takes total, last accepted`() {
        // §8.3: a task with 2 repetitions, each tried 3 times: 2 Repeat presses per instance,
        // 3rd take accepted via Next. Verified here for one instance (TaskComponent is
        // per-instance; SessionComponent multiplies this across repetitions).
        val h = Harness(vocalTask)

        h.component.onStart() // take 1
        h.component.onStop()
        h.component.onRepeat() // rejects take 1, starts take 2
        h.component.onStop()
        h.component.onRepeat() // rejects take 2, starts take 3
        h.component.onStop()
        h.component.onNext() // accepts take 3

        val takeEvents = h.events.filter { it.type == TimelineEventType.TAKE_REJECTED }
        assertEquals(listOf(1, 2), takeEvents.map { it.take })
        assertTrue(takeEvents.all { it.reason == "EXAMINER_REPEAT" })

        val completed = h.events.single { it.type == TimelineEventType.TASK_COMPLETED }
        assertEquals(3, completed.take)

        assertEquals(1, h.finished.size)
        assertEquals(3, h.finished.single().takes)
        assertFalse(h.finished.single().skipped)
    }

    @Test
    fun `repeat is a no-op when canRepeat is false`() {
        val nonRepeatable = vocalTask.copy(canRepeat = false)
        val h = Harness(nonRepeatable)

        h.component.onStart()
        h.component.onStop()
        assertFalse(h.component.state.value.buttons.repeatEnabled)

        h.component.onRepeat()
        // Still Stopped, no additional events logged.
        assertTrue((h.component.state.value.content as TaskComponent.Content.Vocal).screenState is TaskScreenState.Stopped)
        assertEquals(0, h.events.count { it.type == TimelineEventType.TAKE_REJECTED })
    }

    @Test
    fun `skip is only available from Idle and only if canSkip`() {
        val h = Harness(vocalTask)

        h.component.onStart()
        h.component.onSkip() // Capturing -> ignored
        assertEquals(0, h.finished.size)

        h.component.onStop()
        h.component.onSkip() // Stopped -> ignored (table only grants Skip in Idle)
        assertEquals(0, h.finished.size)
    }

    @Test
    fun `skip from idle logs TASK_SKIPPED and finishes with skipped=true`() {
        val h = Harness(vocalTask)
        h.component.onSkip()

        assertEquals(1, h.finished.size)
        assertTrue(h.finished.single().skipped)
        assertEquals(0, h.finished.single().takes)
        assertTrue(h.events.any { it.type == TimelineEventType.TASK_SKIPPED })
    }

    @Test
    fun `the Skip button asks first and nothing is skipped until it is confirmed`() {
        val h = Harness(vocalTask)

        h.component.onSkipRequested()
        assertTrue(h.component.state.value.skipConfirmationOpen)
        assertEquals(0, h.finished.size)
        assertTrue(h.events.none { it.type == TimelineEventType.TASK_SKIPPED })

        h.component.onSkipCancelled()
        assertFalse(h.component.state.value.skipConfirmationOpen)
        assertEquals(0, h.finished.size)

        h.component.onSkipRequested()
        h.component.onSkip() // "Skip" in the dialog
        assertFalse(h.component.state.value.skipConfirmationOpen)
        assertTrue(h.finished.single().skipped)
    }

    @Test
    fun `the Skip button does nothing while a take is running or kept, or when the task is not skippable`() {
        val h = Harness(vocalTask)
        h.component.onStart()
        h.component.onSkipRequested()
        assertFalse(h.component.state.value.skipConfirmationOpen)
        h.component.onStop()
        h.component.onSkipRequested()
        assertFalse(h.component.state.value.skipConfirmationOpen)

        val notSkippable = Harness(vocalTask.copy(canSkip = false))
        notSkippable.component.onSkipRequested()
        assertFalse(notSkippable.component.state.value.skipConfirmationOpen)
    }

    @Test
    fun `Next stays disabled on an unrecorded skippable task, so Skip cannot be mistaken for it`() {
        val h = Harness(vocalTask)

        assertTrue(h.component.state.value.canSkip)
        assertFalse(h.component.state.value.buttons.nextEnabled)
        assertTrue(h.component.state.value.buttons.skipEnabled)
    }

    @Test
    fun `a task after a skipped one carries the skipped notice`() {
        assertFalse(Harness(vocalTask).component.state.value.previousTaskSkipped)
        assertTrue(Harness(vocalTask, previousTaskSkipped = true).component.state.value.previousTaskSkipped)
    }

    @Test
    fun `non-skippable task has skip disabled even in Idle`() {
        val h = Harness(vocalTask.copy(canSkip = false))
        assertFalse(h.component.state.value.buttons.skipEnabled)
        h.component.onSkip()
        assertEquals(0, h.finished.size)
    }

    @Test
    fun `device lost while capturing auto-rejects the open take and returns to Idle`() {
        val h = Harness(vocalTask)
        h.component.onStart() // take 1, Capturing

        h.recorder!!.simulateInterruption(InterruptionReason.DEVICE_LOST)
        h.dispatchers.scheduler.advanceUntilIdle()

        val content = h.component.state.value.content as TaskComponent.Content.Vocal
        assertTrue(content.screenState is TaskScreenState.Idle)
        assertTrue(content.deviceLost)

        val rejected = h.events.single { it.type == TimelineEventType.TAKE_REJECTED }
        assertEquals(1, rejected.take)
        assertEquals("DEVICE_LOST", rejected.reason)
    }

    @Test
    fun `device lost while idle does not fabricate a TAKE_REJECTED but still flags deviceLost`() {
        val h = Harness(vocalTask)

        h.recorder!!.simulateInterruption(InterruptionReason.DEVICE_LOST)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertTrue((h.component.state.value.content as TaskComponent.Content.Vocal).deviceLost)
        assertEquals(0, h.events.count { it.type == TimelineEventType.TAKE_REJECTED })
    }

    @Test
    fun `device reselection resumes the recorder and notifies the owner`() {
        val h = Harness(vocalTask)
        h.component.onStart()
        h.recorder!!.simulateInterruption(InterruptionReason.DEVICE_LOST)
        h.dispatchers.scheduler.advanceUntilIdle()

        val newDevice = AudioInputDevice(id = "secondary", name = "USB Mic", eligible = true)
        h.component.onDeviceReselected(newDevice)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(1, h.recorder.resumeCalls.size)
        assertEquals(newDevice, h.recorder.resumeCalls.single().device)
        assertEquals(1, h.resumeCalls.size)
        assertFalse((h.component.state.value.content as TaskComponent.Content.Vocal).deviceLost)
    }

    @Test
    fun `a hard recorder failure surfaces as Failed with Start re-enabled and other buttons disabled`() {
        val h = Harness(vocalTask)
        h.component.onStart()

        h.recorder!!.simulateFailure(AudioError.DiskWriteFailed("disk full"))
        h.dispatchers.scheduler.advanceUntilIdle()

        val content = h.component.state.value.content as TaskComponent.Content.Vocal
        val failedState = content.screenState as TaskScreenState.Failed
        assertEquals(AudioError.DiskWriteFailed("disk full"), failedState.error)

        val buttons = h.component.state.value.buttons
        assertTrue(buttons.startEnabled)
        assertFalse(buttons.stopEnabled)
        assertFalse(buttons.repeatEnabled)
        assertFalse(buttons.nextEnabled)
    }

    // ---- QUESTIONNAIRE ----

    private val questionnaireTask = QuestionnaireTask(
        titleKey = "q.title",
        questions = listOf(
            Question(
                questionType = QuestionType.OPEN,
                questionKey = "age",
                questionTextKey = "q.age",
                questionRegex = "\\d+",
            ),
            Question(
                questionType = QuestionType.SINGLE_CHOICE,
                questionKey = "handedness",
                questionTextKey = "q.handedness",
                questionOptions = listOf("left", "right"),
            ),
            Question(
                questionType = QuestionType.MULTIPLE_CHOICE,
                questionKey = "symptoms",
                questionTextKey = "q.symptoms",
                questionOptions = listOf("cough", "fever"),
            ),
        ),
        canSkip = true,
    )

    @Test
    fun `questionnaire next stays pressable but only finishes once required answers are valid`() {
        val h = Harness(questionnaireTask, withRecorder = false)

        assertTrue(h.component.state.value.buttons.nextEnabled)
        assertFalse((h.component.state.value.content as TaskComponent.Content.Questionnaire).allValid)

        h.component.onNext() // invalid: reveals errors, does not finish
        assertEquals(0, h.finished.size)
        assertTrue((h.component.state.value.content as TaskComponent.Content.Questionnaire).submitAttempted)

        h.component.onOpenAnswerChanged("age", "not a number")
        h.component.onNext()
        assertEquals(0, h.finished.size)

        h.component.onOpenAnswerChanged("age", "42")
        h.component.onNext()
        assertEquals(0, h.finished.size) // handedness still unselected

        h.component.onOptionToggled("handedness", "right", true)
        // symptoms is MULTIPLE_CHOICE with no minimum — should already be valid with zero selections.
        assertTrue((h.component.state.value.content as TaskComponent.Content.Questionnaire).allValid)

        h.component.onNext()
        assertEquals(1, h.finished.size)
        val record = h.finished.single()
        assertEquals(mapOf("age" to listOf("42"), "handedness" to listOf("right"), "symptoms" to emptyList()), record.questionnaireAnswers)
    }

    @Test
    fun `an untouched optional open question does not block next, a required one does`() {
        fun openTask(regex: String) = QuestionnaireTask(
            titleKey = "q",
            questions = listOf(Question(QuestionType.OPEN, "note", "note_text", questionRegex = regex)),
        )

        // The web sends `.*` for an optional open question and `.+` for a required one.
        val optional = Harness(openTask(".*"), withRecorder = false)
        optional.component.onNext()
        assertEquals(1, optional.finished.size)

        val required = Harness(openTask(".+"), withRecorder = false)
        required.component.onNext()
        assertEquals(0, required.finished.size)
        required.component.onOpenAnswerChanged("note", "x")
        required.component.onNext()
        assertEquals(1, required.finished.size)
    }

    @Test
    fun `a READING task's passage is kept out of the cards and, with the panel hidden, shown nowhere`() {
        val reading = VocalTask(titleKey = "r", subtype = VocalSubtype.READING, instructionKeys = listOf("a", "b", "passage"))

        listOf(1, 2).forEach { repetition ->
            val state = Harness(reading, repetition = repetition).component.state.value
            assertEquals(null, state.readingPassageKey) // decision 59: panel hidden
            assertFalse("passage" in state.instructionKeys)
        }
        assertEquals(listOf("a", "b"), Harness(reading, repetition = 1).component.state.value.instructionKeys)

        val single = Harness(reading.copy(instructionKeys = listOf("passage"))).component.state.value
        assertEquals(null, single.readingPassageKey)
        assertEquals(emptyList<String>(), single.instructionKeys)
    }

    @Test
    fun `from the second repetition the first instruction becomes the repeat prompt`() {
        val task = vocalTask.copy(instructionKeys = listOf("a", "b"))
        assertEquals(listOf("a", "b"), Harness(task, repetition = 1).component.state.value.instructionKeys)
        assertEquals(listOf("task.repeatInstruction", "b"), Harness(task, repetition = 2).component.state.value.instructionKeys)
        assertEquals(listOf("task.repeatInstruction", "b"), Harness(task, repetition = 3).component.state.value.instructionKeys)
        // A task without instructions still says it is a repetition.
        assertEquals(listOf("task.repeatInstruction"), Harness(vocalTask.copy(instructionKeys = emptyList()), repetition = 2).component.state.value.instructionKeys)
    }

    @Test
    fun `other tasks have no reading passage`() {
        val state = Harness(vocalTask.copy(instructionKeys = listOf("a", "b"))).component.state.value
        assertEquals(null, state.readingPassageKey)
        assertEquals(listOf("a", "b"), state.instructionKeys)
    }

    @Test
    fun `multiple choice toggles accumulate and can be deselected`() {
        val h = Harness(questionnaireTask, withRecorder = false)

        h.component.onOptionToggled("symptoms", "cough", true)
        h.component.onOptionToggled("symptoms", "fever", true)
        val questionnaireContent = h.component.state.value.content as TaskComponent.Content.Questionnaire
        assertEquals(listOf("cough", "fever"), questionnaireContent.answers["symptoms"]?.selected)

        h.component.onOptionToggled("symptoms", "cough", false)
        val updated = h.component.state.value.content as TaskComponent.Content.Questionnaire
        assertEquals(listOf("fever"), updated.answers["symptoms"]?.selected)
    }

    // ---- INFO ----

    @Test
    fun `info task is display-only and Next completes it immediately`() {
        val h = Harness(InfoTask(titleKey = "info.title"), withRecorder = false)

        assertTrue(h.component.state.value.buttons.nextEnabled)
        h.component.onNext()

        assertEquals(1, h.finished.size)
        assertTrue(h.events.any { it.type == TimelineEventType.TASK_COMPLETED })
    }

    // ---- State enrichment (§8.6 follow-up: no more RootComponent re-expansion workaround) ----

    @Test
    fun `state carries position, total, device list, and showIndicator directly`() {
        val h = Harness(vocalTask.copy(showIndicator = false), taskIndex = 2)
        val state = h.component.state.value

        assertEquals(3, state.positionInProtocol) // taskIndex + 1 from the harness
        assertEquals(3, state.totalInstanceCount)
        assertEquals(h.devices, state.availableDevices)
        assertEquals(h.devices.first(), state.currentDevice)
        assertFalse((state.content as TaskComponent.Content.Vocal).showIndicator)
    }

    @Test
    fun `questionnaire content carries the question definitions directly`() {
        val h = Harness(questionnaireTask, withRecorder = false)
        val content = h.component.state.value.content as TaskComponent.Content.Questionnaire
        assertEquals(questionnaireTask.questions, content.questions)
    }

    @Test
    fun `example audio is unavailable without a configured path and available with one`() {
        val withoutExample = Harness(vocalTask)
        assertFalse((withoutExample.component.state.value.content as TaskComponent.Content.Vocal).exampleAudioAvailable)

        val withExample = Harness(vocalTask, audioExamplePath = Path.of("example.wav"))
        assertTrue((withExample.component.state.value.content as TaskComponent.Content.Vocal).exampleAudioAvailable)
    }

    @Test
    fun `play example audio forwards to the playback service unless a take is open`() {
        val h = Harness(vocalTask, audioExamplePath = Path.of("example.wav"))

        h.component.onPlayExampleAudio()
        assertEquals(listOf(Path.of("example.wav")), h.audioPlaybackService.playCalls)
        h.dispatchers.scheduler.advanceUntilIdle()
        assertTrue((h.component.state.value.content as TaskComponent.Content.Vocal).exampleAudioPlaying)

        h.component.onStopExampleAudio()
        assertEquals(1, h.audioPlaybackService.stopCallCount)

        h.component.onStart() // Capturing — example playback disabled (§8.6)
        h.component.onPlayExampleAudio()
        assertEquals(1, h.audioPlaybackService.playCalls.size) // no second call
    }

    private fun vocal(h: Harness) = h.component.state.value.content as TaskComponent.Content.Vocal

    @Test
    fun `F-144 a burst of fast levels while capturing gives exactly one history point each, equal values included`() {
        val h = Harness(vocalTask)
        h.component.onStart()
        val burst = listOf(0.3f, 0.3f, 0.3f, 0.5f, 0.5f, 0.1f, 0.1f, 0.1f)
        burst.forEach { h.recorder!!.emitLevel(it) }

        assertEquals(burst, vocal(h).levelHistory)
    }

    @Test
    fun `F-144 levels emitted while not capturing are not added to the history`() {
        val h = Harness(vocalTask)
        h.recorder!!.emitLevel(0.9f) // Idle
        assertEquals(emptyList<Float>(), vocal(h).levelHistory)

        h.component.onStart()
        h.recorder.emitLevel(0.4f)
        h.component.onStop()
        h.recorder.emitLevel(0.8f) // Stopped
        assertEquals(listOf(0.4f), vocal(h).levelHistory)
    }

    @Test
    fun `F-144 the history survives Stop and a new take clears it`() {
        val h = Harness(vocalTask)
        h.component.onStart()
        h.recorder!!.emitLevel(0.2f)
        h.recorder.emitLevel(0.6f)
        h.component.onStop()
        assertEquals(listOf(0.2f, 0.6f), vocal(h).levelHistory)

        h.component.onRepeat()
        assertEquals(emptyList<Float>(), vocal(h).levelHistory)
        h.recorder.emitLevel(0.7f)
        assertEquals(listOf(0.7f), vocal(h).levelHistory)
    }
}
