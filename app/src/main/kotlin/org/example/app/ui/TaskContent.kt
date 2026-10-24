package org.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.delay
import org.example.app.domain.config.IndicatorType
import org.example.app.domain.config.Question
import org.example.app.domain.config.QuestionType
import org.example.app.navigation.AnswerState
import org.example.app.navigation.TaskButtonState
import org.example.app.navigation.TaskComponent
import org.example.app.navigation.TaskScreenState

/**
 * §8.6 task screen — a 1:1 copy of the legacy `StandardProtocolScreen` (§13 decision 36,
 * `shareapp/src/main/kotlin/screens/StandardProtocolScreen.kt`), rendered from
 * [TaskComponent.state]. Layout, spacing, type styles and the three-button bottom row
 * (hidden prev slot / Start-Stop-Repeat state button / next-task button) are the legacy ones;
 * only its scaling bugs are fixed (fixed 350dp button widths → weighted slots,
 * `fillMaxSize(0.5f)` canvas → a weighted middle area). Deliberate mappings onto this app's
 * state machine: the legacy Start→Stop→Again cycling button carries the
 * `START/STOP/REPEAT_BUTTON` testTag of its current role, and a skippable task gets
 * a separate outlined Skip button (tag `SKIP_BUTTON`, with a confirmation) in the otherwise
 * empty prev slot — the legacy app had no skip.
 * QUESTIONNAIRE content has no legacy counterpart and renders in the same visual language.
 */
@Composable
fun TaskContent(component: TaskComponent, localization: UiLocalization) {
    val state by component.state.subscribeAsState()
    val vocal = state.content as? TaskComponent.Content.Vocal
    val video = state.content as? TaskComponent.Content.Video
    val capturing = (vocal?.screenState ?: video?.screenState) is TaskScreenState.Capturing
    val elapsedSeconds = rememberElapsedSeconds(
        capturing = capturing,
        resetKey = vocal?.takeNumber ?: video?.takeNumber,
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(all = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top line - title and info
        TaskTitle(state, localization)

        // Instructions field, with the example-audio utility row between the two cards
        // The cards take only the height their text needs and scroll when the window is too short;
        // a VOCAL task's middle area keeps its full size (the other task types fill the rest).
        val fixedMiddle = vocal != null
        InstructionsField(
            component, state, localization,
            if (fixedMiddle) Modifier.weight(1f, fill = false).contentWidth(1500.dp) else Modifier.contentWidth(1500.dp),
            scrollable = fixedMiddle,
        )

        // Middle area: recording circle / live waveform (VOCAL), the camera preview (VIDEO, or
        // VOCAL with `recordVideo` — shown even without `showIndicator`, since the examiner
        // frames the shot by it) or the questionnaire.
        // Weighted instead of the legacy fillMaxSize(0.5f) so the layout scales (§13/36).
        val indicatorHeight = if (vocal?.indicatorType == IndicatorType.WAVEFORM) 250.dp else 160.dp
        val middleModifier = if (vocal != null) {
            // Never squeezed, but no taller than needed: the indicator (the waveform is 250 tall) or the 150 preview
            val minHeight = maxOf(if (vocal.showIndicator) indicatorHeight else 0.dp, if (vocal.video != null) 150.dp else 0.dp)
            Modifier.fillMaxWidth().heightIn(min = minHeight)
        } else {
            Modifier.weight(1f).fillMaxWidth()
        }
        Box(modifier = middleModifier, contentAlignment = Alignment.Center) {
            when (val content = state.content) {
                is TaskComponent.Content.Vocal -> if (content.video != null) {
                    // Legacy: a small ~200x150 preview beside the recording circle
                    Row(horizontalArrangement = Arrangement.spacedBy(200.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (content.showIndicator) {
                            // Reserve the waveform's footprint from the start (it replaces the circle
                            // after Stop), which would otherwise shift the preview.
                            Box(Modifier.weight(1f, fill = false).widthIn(max = 500.dp).height(indicatorHeight), contentAlignment = Alignment.Center) {
                                TaskLevelIndicator(
                                    indicatorType = content.indicatorType,
                                    level = content.level,
                                    capturing = capturing,
                                    takeKey = content.takeNumber,
                                )
                            }
                        }
                        VideoPreview(content.video, Modifier.size(200.dp, 150.dp))
                    }
                } else if (content.showIndicator) {
                    TaskLevelIndicator(
                        indicatorType = content.indicatorType,
                        level = content.level,
                        capturing = capturing,
                        takeKey = content.takeNumber,
                    )
                }

                is TaskComponent.Content.Questionnaire -> QuestionnaireBody(
                    questions = content.questions,
                    content = content,
                    localization = localization,
                    onOpenAnswerChanged = component::onOpenAnswerChanged,
                    onOptionToggled = component::onOptionToggled,
                    modifier = Modifier
                        .contentWidth(1500.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                )

                is TaskComponent.Content.Video -> VideoTaskBody(
                    content = content,
                    onPtz = component::onPtz,
                    modifier = Modifier.contentWidth(1500.dp).fillMaxHeight(),
                )

                TaskComponent.Content.Info -> Unit
            }
        }

        // The timer (legacy `timerBox`): invisible until a take is running, green once
        // the configured task length is reached
        TimerBox(
            elapsedSeconds = if (capturing) elapsedSeconds else 0,
            taskLengthSeconds = state.taskLengthSeconds,
        )

        // Bottom line: hidden prev slot / state button / next-task button
        BottomButtonRow(component, state, localization)
    }

    if (vocal != null && vocal.deviceLost) {
        DeviceLostDialog(
            localization = localization,
            availableDevices = state.availableDevices,
            defaultDevice = state.currentDevice,
            requiresExplicitResume = true,
            messageTag = TestTags.Task.DEVICE_LOST_ERROR,
            onDeviceAction = component::onDeviceReselected,
        )
    }

    if (state.skipConfirmationOpen) SkipConfirmDialog(component, localization)

    val screenState = vocal?.screenState
    if (screenState is TaskScreenState.Failed) {
        var dismissed by remember(screenState) { mutableStateOf(false) }
        if (!dismissed) {
            // Legacy `ErrorDialog` (shape = medium, single Close action)
            AlertDialog(
                onDismissRequest = { dismissed = true },
                modifier = Modifier.testTag(TestTags.Task.ERROR_DIALOG),
                shape = MaterialTheme.shapes.medium,
                title = { Text(localization.resolve("error.dialog.title")) },
                text = { Text(localization.resolve(screenState.error.messageKey())) },
                confirmButton = {
                    Button(
                        onClick = { dismissed = true },
                        modifier = Modifier.testTag(TestTags.Task.ERROR_DIALOG_DISMISS_BUTTON),
                    ) {
                        Text(localization.resolve("error.dialog.dismiss"))
                    }
                },
            )
        }
    }
}

@Composable
private fun TaskTitle(state: TaskComponent.State, localization: UiLocalization) {
    val numberLabel = if (state.totalInstanceCount > 0) {
        localization.resolve(
            "task.numberOfTotalLabel",
            mapOf("n" to state.positionInProtocol.toString(), "total" to state.totalInstanceCount.toString()),
        )
    } else {
        localization.resolve("task.numberLabel", mapOf("n" to (state.taskIndex + 1).toString()))
    }
    // Legacy `currentTaskTitle`: the repetition is appended to the title line itself
    val title = buildAnnotatedString {
        append(localization.resolve(state.titleKey))
        if (state.repetition > 1) {
            append(", ")
            append(localization.resolve("task.repetitionLabel", mapOf("n" to state.repetition.toString())))
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        if (state.previousTaskSkipped) {
            Text(
                localization.resolve("task.previousSkipped"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag(TestTags.Task.PREVIOUS_SKIPPED_NOTICE),
            )
        }
        Text(
            numberLabel,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal))
    }
}

@Composable
private fun InstructionsField(
    component: TaskComponent,
    state: TaskComponent.State,
    localization: UiLocalization,
    modifier: Modifier,
    scrollable: Boolean,
) {
    val firstKey = state.instructionKeys.firstOrNull()
    val remainingKeys = state.instructionKeys.drop(1)
    // Legacy `showExampleButtonAndSecondInstructions`: consecutive repetitions of the same
    // task hide the utility row and the second instructions card
    val showSecond = state.repetition <= 1

    // At least ~110dp of the cards stay readable; the scrollbar shows only when they overflow
    val scrollState = rememberScrollState()
    Box(modifier = if (scrollable && firstKey != null) modifier.heightIn(min = 110.dp) else modifier) {
        Column(
            modifier = if (scrollable) {
                Modifier.fillMaxWidth().testTag(TestTags.Task.INSTRUCTIONS).verticalScroll(scrollState)
            } else {
                Modifier.fillMaxWidth()
            },
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The legacy replaced its `xx` marker with the task length; this config's instruction
            // strings use the named `{length}` placeholder (§7)
            val placeholders = mapOf("length" to state.taskLengthSeconds.toString())
            if (firstKey != null) InstructionsCard(listOf(firstKey), placeholders, localization)
            if (showSecond) {
                UtilitiesRowBelowInstructions(component, state, localization)
                if (remainingKeys.isNotEmpty()) InstructionsCard(remainingKeys, placeholders, localization)
            }
            // READING's passage is what the participant reads aloud, so unlike the second card it
            // stays on every repetition; upright and larger than the italic instructions.
            state.readingPassageKey?.let { key ->
                Card(modifier = Modifier.fillMaxWidth().testTag(TestTags.Task.READING_PASSAGE)) {
                    Text(
                        localization.resolve(key, placeholders),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
            }
        }
        if (scrollable && scrollState.maxValue > 0) {
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().testTag(TestTags.Task.INSTRUCTIONS_SCROLLBAR),
            )
        }
    }
}

@Composable
private fun InstructionsCard(keys: List<String>, placeholders: Map<String, String>, localization: UiLocalization) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            // Legacy `processStringTags` rendered instructions italic by default; markup
            // (<bold>/<italic>) comes through the resolved AnnotatedString (§7, decision 26)
            keys.forEach { key ->
                Text(
                    localization.resolve(key, placeholders),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}

@Composable
private fun UtilitiesRowBelowInstructions(
    component: TaskComponent,
    state: TaskComponent.State,
    localization: UiLocalization,
) {
    val vocal = state.content as? TaskComponent.Content.Vocal ?: return
    if (!vocal.exampleAudioAvailable) return
    val capturing = vocal.screenState is TaskScreenState.Capturing

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Button(
            onClick = {
                if (vocal.exampleAudioPlaying) component.onStopExampleAudio() else component.onPlayExampleAudio()
            },
            enabled = !capturing,
            modifier = Modifier.testTag(TestTags.Task.EXAMPLE_AUDIO_BUTTON),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    localization.resolve(if (vocal.exampleAudioPlaying) "task.stopExample" else "task.playExample"),
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(
                    if (vocal.exampleAudioPlaying) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = "Example playback",
                    modifier = Modifier.size(35.dp).padding(start = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun TimerBox(elapsedSeconds: Int, taskLengthSeconds: Int) {
    Card(
        colors = if (elapsedSeconds == 0) {
            CardDefaults.cardColors( // Timer is hidden
                containerColor = Color.Transparent,
                contentColor = Color.Transparent,
            )
        } else if (elapsedSeconds < taskLengthSeconds || taskLengthSeconds == 0) {
            CardDefaults.cardColors( // Timer is running, task ongoing
                containerColor = Color.Transparent,
                contentColor = Color.Black,
            )
        } else {
            CardDefaults.cardColors( // Timer is running, task can be finished
                containerColor = Color(0xFF006E2A).copy(alpha = 0.4f),
                contentColor = Color.Black,
            )
        },
    ) {
        Text(
            elapsedSeconds.toString(),
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BottomButtonRow(component: TaskComponent, state: TaskComponent.State, localization: UiLocalization) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Legacy prev-task slot: the legacy button was gated behind a `showBackButton`
        // preference defaulting to false, so the 1:1 default look is an empty slot that
        // keeps the state button centered. There is no back navigation in this app; the slot
        // holds the secondary Skip button of a skippable task instead.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (state.canSkip) SkipTaskButton(component, state, localization)
        }

        when (val content = state.content) {
            is TaskComponent.Content.Vocal ->
                StartStateButton(component, content.screenState, state.buttons, localization)
            is TaskComponent.Content.Video ->
                StartStateButton(component, content.screenState, state.buttons, localization)
            else -> Unit
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            NextTaskButton(component, state, localization)
        }
    }
}

/** The legacy Start→Stop→Again cycling button (`startTaskButton` + `StartButtonState`), with
 * the testTag of whichever role it currently plays so the §10.3 scenarios drive it unchanged. */
@Composable
private fun StartStateButton(
    component: TaskComponent,
    screenState: TaskScreenState,
    buttons: TaskButtonState,
    localization: UiLocalization,
) {
    data class ButtonVisual(
        val color: Color,
        val icon: ImageVector,
        val contentColor: Color,
        val text: AnnotatedString,
        val tag: String,
        val enabled: Boolean,
        val onClick: () -> Unit,
    )

    // Colors are the legacy `StartButtonState` hardcoded values (light tertiary green /
    // error red / outline gray)
    val visual = when (screenState) {
        is TaskScreenState.Idle, is TaskScreenState.Failed -> ButtonVisual(
            color = Color(0xFF006E2A),
            icon = Icons.Filled.PlayArrow,
            contentColor = Color(0xFFFFFFFF),
            text = localization.resolve("action.start"),
            tag = TestTags.Task.START_BUTTON,
            enabled = buttons.startEnabled,
            onClick = component::onStart,
        )

        is TaskScreenState.Capturing -> ButtonVisual(
            color = Color(0xFFBA1A1A),
            icon = Icons.Filled.Close,
            contentColor = Color(0xFFFFFFFF),
            text = localization.resolve("action.stop"),
            tag = TestTags.Task.STOP_BUTTON,
            enabled = buttons.stopEnabled,
            onClick = component::onStop,
        )

        is TaskScreenState.Stopped -> ButtonVisual(
            color = Color(0xFF71787D),
            icon = Icons.Filled.Refresh,
            contentColor = Color(0xFF000000),
            text = localization.resolve("action.repeat"),
            tag = TestTags.Task.REPEAT_BUTTON,
            enabled = buttons.repeatEnabled,
            onClick = component::onRepeat,
        )
    }

    Button(
        onClick = visual.onClick,
        enabled = visual.enabled,
        modifier = Modifier.padding(top = 16.dp).testTag(visual.tag),
        colors = ButtonDefaults.buttonColors(
            containerColor = visual.color,
            contentColor = visual.contentColor,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                visual.icon,
                contentDescription = "Start button",
                modifier = Modifier.size(75.dp),
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(visual.text, style = actionButtonTextStyle())
            }
        }
    }
}

/** A skippable task's own secondary button: an outlined "Skip task", asking before it skips. */
@Composable
private fun SkipTaskButton(component: TaskComponent, state: TaskComponent.State, localization: UiLocalization) {
    OutlinedButton(
        onClick = component::onSkipRequested,
        enabled = state.buttons.skipEnabled,
        modifier = Modifier.padding(top = 16.dp).testTag(TestTags.Task.SKIP_BUTTON),
    ) {
        Text(localization.resolve("task.skipLabel"), style = MaterialTheme.typography.titleMedium)
    }
}

/** The "Skip this task?" confirmation, inside the main window like every dialog. */
@Composable
private fun SkipConfirmDialog(component: TaskComponent, localization: UiLocalization) {
    AlertDialog(
        onDismissRequest = component::onSkipCancelled,
        modifier = Modifier.testTag(TestTags.Task.SKIP_DIALOG),
        title = { Text(localization.resolve("task.skipConfirm.title")) },
        text = { Text(localization.resolve("task.skipConfirm.message")) },
        confirmButton = {
            Button(onClick = component::onSkip, modifier = Modifier.testTag(TestTags.Task.SKIP_CONFIRM_BUTTON)) {
                Text(localization.resolve("task.skipConfirm.confirm"))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = component::onSkipCancelled, modifier = Modifier.testTag(TestTags.Task.SKIP_CANCEL_BUTTON)) {
                Text(localization.resolve("action.cancel"))
            }
        },
    )
}

/** The legacy `nextTaskButton` (small "Next task" line over the upcoming task's name). */
@Composable
private fun NextTaskButton(component: TaskComponent, state: TaskComponent.State, localization: UiLocalization) {
    val buttons = state.buttons
    val nextTitle = state.nextTaskTitleKey?.let { localization.resolve(it) }
        ?: localization.resolve("task.endOfProtocol")

    Button(
        onClick = component::onNext,
        enabled = buttons.nextEnabled,
        modifier = Modifier
            .widthIn(max = 350.dp)
            .padding(top = 16.dp)
            .testTag(TestTags.Task.NEXT_BUTTON),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f, fill = false).padding(horizontal = 16.dp)) {
                Text(
                    localization.resolve("task.nextLabel"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    nextTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = "Forward button",
                modifier = Modifier.size(75.dp),
            )
        }
    }
}

@Composable
private fun rememberElapsedSeconds(capturing: Boolean, resetKey: Any?): Int {
    var seconds by remember(resetKey) { mutableStateOf(0) }
    LaunchedEffect(capturing, resetKey) {
        if (capturing) {
            seconds = 0
            while (true) {
                delay(1000)
                seconds++
            }
        }
    }
    return seconds
}

@Composable
private fun QuestionnaireBody(
    questions: List<Question>,
    content: TaskComponent.Content.Questionnaire,
    localization: UiLocalization,
    onOpenAnswerChanged: (String, String) -> Unit,
    onOptionToggled: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        questions.forEach { question ->
            val answer = content.answers[question.questionKey] ?: AnswerState()
            Column {
                Text(localization.resolve(question.questionTextKey), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(4.dp))

                when (question.questionType) {
                    QuestionType.OPEN -> OutlinedTextField(
                        value = answer.selected.firstOrNull().orEmpty(),
                        onValueChange = { onOpenAnswerChanged(question.questionKey, it) },
                        isError = !answer.valid,
                        modifier = Modifier.fillMaxWidth().testTag(TestTags.Questionnaire.answerField(question.questionKey)),
                    )

                    QuestionType.SINGLE_CHOICE -> Column {
                        question.questionOptions.orEmpty().forEach { optionKey ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = answer.selected.contains(optionKey),
                                    onClick = { onOptionToggled(question.questionKey, optionKey, true) },
                                    modifier = Modifier.testTag(TestTags.Questionnaire.answerOption(question.questionKey, optionKey)),
                                )
                                Text(localization.resolve(optionKey), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    QuestionType.MULTIPLE_CHOICE -> Column {
                        question.questionOptions.orEmpty().forEach { optionKey ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val checked = answer.selected.contains(optionKey)
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onOptionToggled(question.questionKey, optionKey, it) },
                                    modifier = Modifier.testTag(TestTags.Questionnaire.answerOption(question.questionKey, optionKey)),
                                )
                                Text(localization.resolve(optionKey), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                if (!answer.valid) {
                    Text(
                        localization.resolve("questionnaire.error.invalid"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(TestTags.Questionnaire.validationError(question.questionKey)),
                    )
                }
            }
        }
    }
}
