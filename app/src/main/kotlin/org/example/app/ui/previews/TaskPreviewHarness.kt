package org.example.app.ui.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.config.IndicatorType
import org.example.app.domain.localization.BuiltinStrings
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.video.PtzAction
import org.example.app.domain.video.VideoError
import org.example.app.navigation.TaskButtonState
import org.example.app.navigation.TaskComponent
import org.example.app.navigation.TaskScreenState
import org.example.app.navigation.VideoFeed
import org.example.app.ui.TaskContent
import org.example.app.ui.UiLocalization
import org.example.app.ui.theme.ShareTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Runs the real [TaskContent] for a VOCAL task in a real window, with an inert fake
 * [TaskComponent] (no AppContainer, recorder, camera, network, or `app/data`):
 * `./gradlew :app:previewTask`, or under Compose Hot Reload
 * `./gradlew :app:hotRun --mainClass=org.example.app.ui.previews.TaskPreviewHarnessKt --auto`.
 *
 * Four independent chip groups sit above the screen: indicator, state, instructions, filming.
 * The chip row is 40 dp high and sits outside [TaskContent], so the screen gets the window height
 * minus 40 dp. Start/Stop/Repeat/Next/Skip work on the fake but a chip click replaces the fake.
 */
fun main() = singleWindowApplication(
    title = "Task — preview harness",
    state = WindowState(size = DpSize(1000.dp, 860.dp)),
) {
    TaskPreview()
}

private enum class IndicatorChoice(val label: String, val type: IndicatorType) {
    CIRCLE("Circle", IndicatorType.CIRCLE),
    WAVEFORM("Waveform", IndicatorType.WAVEFORM),
}

private enum class StateChoice(val label: String) { IDLE("Idle"), CAPTURING("Capturing"), STOPPED("Stopped") }

private enum class InstructionsChoice(val label: String) { SHORT("Short text"), LONG("Long text") }

private enum class FilmingChoice(val label: String) { OFF("Film off"), ON("Film on"), FAILED("Camera failed") }

/** Height of the chip row: 32 dp chips (touch-target padding removed) plus 4 dp above and below. */
private val ChipRowHeight = 40.dp

@Composable
private fun TaskPreview() {
    var indicator by remember { mutableStateOf(IndicatorChoice.CIRCLE) }
    var screen by remember { mutableStateOf(StateChoice.IDLE) }
    var instructions by remember { mutableStateOf(InstructionsChoice.LONG) }
    var filming by remember { mutableStateOf(FilmingChoice.OFF) }

    val component = remember(indicator, screen, instructions, filming) {
        PreviewTaskComponent(previewState(indicator.type, screen, instructions, filming))
    }
    val localization = remember { UiLocalization(LocalizedStringProvider(builtins = BuiltinStrings.en + previewStrings), "en", null) }

    ShareTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(ChipRowHeight).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        IndicatorChoice.entries.forEach { PreviewChip(it.label, indicator == it) { indicator = it } }
                        Spacer(Modifier.width(10.dp))
                        StateChoice.entries.forEach { PreviewChip(it.label, screen == it) { screen = it } }
                        Spacer(Modifier.width(10.dp))
                        InstructionsChoice.entries.forEach { PreviewChip(it.label, instructions == it) { instructions = it } }
                        Spacer(Modifier.width(10.dp))
                        FilmingChoice.entries.forEach { PreviewChip(it.label, filming == it) { filming = it } }
                    }
                }
                Box(Modifier.fillMaxSize()) { TaskContent(component, localization) }
            }
        }
    }
}

@Composable
private fun PreviewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
    )
}

private val previewStrings = mapOf(
    "preview.task.title" to "Sustained phonation",
    "preview.task.next" to "Reading passage",
    "preview.instr.short" to "Say \"aaah\" as steadily as you can for {length} seconds.",
    "preview.instr.long.1" to "In this task you are asked to produce a sustained vowel. Sit comfortably, relax your " +
        "shoulders and take a deep breath. When you hear the signal, say \"aaah\" at a comfortable pitch and " +
        "loudness, and hold it for as long as you can on a single breath, aiming for at least {length} seconds. " +
        "Do not whisper, and try not to let the pitch or the loudness drift up or down while you hold the sound.",
    "preview.instr.long.2" to "<bold>Before you begin</bold>, listen to the example recording so you know what a steady " +
        "tone sounds like. Keep the microphone about a hand-width from your mouth and do not move your head " +
        "while the circle is pulsating.",
    "preview.instr.long.3" to "If you run out of breath before the end, stop, rest for a moment, and press Repeat to " +
        "try again. There is no penalty for repeating the task; the clearest and longest attempt is the one " +
        "that is used. Take a short break between attempts if you feel light-headed or if your throat feels dry.",
    "preview.instr.long.4" to "When you are satisfied with your recording, press the button at the bottom right to " +
        "continue with the next task. If anything is unclear, ask the examiner before you start.",
)

private val previewDevice = AudioInputDevice(id = "usb", name = "Microphone (Sennheiser USB headset)", eligible = true)

/** Rolling-envelope shaped history: a rise, a steady wobble and a release, `size` points in 0..1. */
private fun envelope(size: Int): List<Float> = List(size) { i ->
    val t = i / (size - 1f)
    val body = (minOf(t / 0.08f, 1f) * minOf((1f - t) / 0.1f, 1f)).coerceIn(0f, 1f)
    (body * (0.55f + 0.25f * abs(sin(t * 9f * PI.toFloat())))).coerceIn(0f, 1f)
}

/** A flat grey JPEG standing in for a camera frame; the harness has no camera. */
private fun fakeFrame(): ByteArray {
    val bitmap = org.jetbrains.skia.Bitmap().apply {
        allocPixels(org.jetbrains.skia.ImageInfo.makeN32(200, 150, org.jetbrains.skia.ColorAlphaType.OPAQUE))
        erase(0xFF607080.toInt())
    }
    return org.jetbrains.skia.Image.makeFromBitmap(bitmap)
        .encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG)!!.bytes
}

private fun previewState(
    indicator: IndicatorType,
    screen: StateChoice,
    instructions: InstructionsChoice,
    filming: FilmingChoice,
): TaskComponent.State {
    val screenState = when (screen) {
        StateChoice.IDLE -> TaskScreenState.Idle
        StateChoice.CAPTURING -> TaskScreenState.Capturing
        StateChoice.STOPPED -> TaskScreenState.Stopped
    }
    val video = when (filming) {
        FilmingChoice.OFF -> null
        FilmingChoice.ON -> VideoFeed(
            frames = MutableStateFlow(fakeFrame()),
            decodeDispatcher = Dispatchers.Default,
            ready = true,
            error = null,
        )
        FilmingChoice.FAILED -> VideoFeed(
            frames = MutableStateFlow(null),
            decodeDispatcher = Dispatchers.Default,
            ready = false,
            error = VideoError.DeviceUnavailable("none"),
            audioOnly = true,
        )
    }
    val long = instructions == InstructionsChoice.LONG
    return TaskComponent.State(
        taskIndex = 2,
        repetition = 1,
        positionInProtocol = 3,
        totalInstanceCount = 10,
        titleKey = "preview.task.title",
        instructionKeys = if (long) {
            listOf("preview.instr.long.1", "preview.instr.long.2", "preview.instr.long.3", "preview.instr.long.4")
        } else {
            listOf("preview.instr.short")
        },
        taskLengthSeconds = 15,
        nextTaskTitleKey = "preview.task.next",
        canSkip = true,
        content = TaskComponent.Content.Vocal(
            screenState = screenState,
            takeNumber = if (screen == StateChoice.IDLE) 0 else 1,
            level = if (screen == StateChoice.CAPTURING) 0.6f else 0f,
            levelHistory = when (screen) {
                StateChoice.IDLE -> emptyList()
                StateChoice.CAPTURING -> envelope(120).take(90)
                StateChoice.STOPPED -> envelope(240)
            },
            deviceLost = false,
            showIndicator = true,
            exampleAudioAvailable = long,
            exampleAudioPlaying = false,
            video = video,
            indicatorType = indicator,
        ),
        buttons = TaskButtonState.of(screenState, canRepeat = true, canSkip = true),
        availableDevices = listOf(previewDevice),
        currentDevice = previewDevice,
    )
}

/** Inert [TaskComponent]: Start/Stop/Repeat move the screen state; nothing is recorded or logged. */
private class PreviewTaskComponent(initial: TaskComponent.State) : TaskComponent {
    private val _state = MutableValue(initial)
    override val state: Value<TaskComponent.State> = _state

    private fun move(next: TaskScreenState) {
        val s = _state.value
        val vocal = s.content as? TaskComponent.Content.Vocal ?: return
        _state.value = s.copy(
            content = vocal.copy(screenState = next, takeNumber = maxOf(vocal.takeNumber, 1)),
            buttons = TaskButtonState.of(next, canRepeat = true, canSkip = true),
        )
    }

    override fun onStart() = move(TaskScreenState.Capturing)
    override fun onStop() = move(TaskScreenState.Stopped)
    override fun onRepeat() = move(TaskScreenState.Capturing)
    override fun onNext() = Unit
    override fun onSkipRequested() = Unit
    override fun onSkipCancelled() = Unit
    override fun onSkip() = Unit
    override fun onOpenAnswerChanged(questionKey: String, value: String) = Unit
    override fun onOptionToggled(questionKey: String, option: String, selected: Boolean) = Unit
    override fun onDeviceReselected(device: AudioInputDevice) = Unit
    override fun onPtz(action: PtzAction) = Unit
    override fun onPlayExampleAudio() = Unit
    override fun onStopExampleAudio() = Unit
}
