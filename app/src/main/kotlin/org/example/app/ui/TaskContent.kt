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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.delay
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

    // F-127: the content is capped at ~1100 dp (plus the 20 dp padding) and centred, like Settings
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier.fillMaxHeight().contentWidth(1100.dp + 40.dp).testTag(TestTags.Task.CONTENT).padding(all = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top line - title and info
        TaskTitle(state, localization)

        if (vocal != null) {
            // VOCAL: instructions first at their natural height, the indicator / preview in what is left (see VocalBody)
            VocalBody(component, state, vocal, capturing, localization, Modifier.weight(1f).fillMaxWidth())
        } else {
            // Instructions field, with the example-audio utility row between the two cards
            InstructionsField(component, state, localization, Modifier.contentWidth(1500.dp), scrollable = false)

            // Middle area: the camera preview (VIDEO) or the questionnaire. Weighted instead of the
            // legacy fillMaxSize(0.5f) so the layout scales (§13/36).
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (val content = state.content) {
                    is TaskComponent.Content.Vocal -> Unit // handled by VocalBody above

                    is TaskComponent.Content.Questionnaire -> {
                        // Scrolls when the questions outgrow the middle area; the scrollbar shows that
                        // more follows (F-091: the last option sat unseen under the hard edge)
                        val scrollState = rememberScrollState()
                        Box(
                            Modifier
                                .contentWidth(1500.dp)
                                .fillMaxHeight()
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .testTag(TestTags.Questionnaire.FRAME),
                        ) {
                            QuestionnaireBody(
                                questions = content.questions,
                                content = content,
                                localization = localization,
                                onOpenAnswerChanged = component::onOpenAnswerChanged,
                                onOptionToggled = component::onOptionToggled,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag(TestTags.Questionnaire.CONTAINER)
                                    .verticalScroll(scrollState),
                            )
                            if (scrollState.maxValue > 0) {
                                VerticalScrollbar(
                                    adapter = rememberScrollbarAdapter(scrollState),
                                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().testTag(TestTags.Questionnaire.SCROLLBAR),
                                )
                            }
                        }
                    }

                    is TaskComponent.Content.Video -> VideoTaskBody(
                        content = content,
                        localization = localization,
                        onPtz = component::onPtz,
                        modifier = Modifier.contentWidth(1500.dp).fillMaxHeight(),
                    )

                    TaskComponent.Content.Info -> Unit
                }
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

/** Narrowest the leftover may get before the level indicator is left out altogether. */
internal val MIN_INDICATOR_HEIGHT = 80.dp

/**
 * Clear space between the instructions and the indicator / preview below them. It is spare height only: it
 * exists only while something is shown, comes out of the leftover (never out of the instructions) and
 * collapses toward 0 as the leftover approaches what the shown middle area needs
 * ([PREVIEW_BLOCK_HEIGHT] with the preview, [MIN_INDICATOR_HEIGHT] without), so it can never cost
 * the preview or the indicator their place.
 */
internal val INSTRUCTIONS_MIDDLE_GAP = 8.dp

private val INSTRUCTIONS_MAX_WIDTH = 1500.dp
private val INDICATOR_MAX_WIDTH = 500.dp
private val PREVIEW_WIDTH = 200.dp
private val PREVIEW_PICTURE_HEIGHT = 150.dp
private val PREVIEW_INDICATOR_GAP = 200.dp

/** The preview's picture plus its reserved message line: the leftover height it needs to be shown at all. */
private val PREVIEW_BLOCK_HEIGHT = PREVIEW_PICTURE_HEIGHT + CAMERA_MESSAGE_SLOT_NARROW

private enum class VocalSlot { Instructions, Message, Middle }

/**
 * The body of a VOCAL task: the instructions at their natural height (they scroll only when they
 * exceed the space left after the title, timer and buttons), then, below an [INSTRUCTIONS_MIDDLE_GAP]
 * that is part of the leftover, the indicator and the camera preview in whatever height is left, centred there.
 *
 * A [SubcomposeLayout], because what is composed depends on measured heights: the preview and the
 * indicator are left out (not just clipped) when the leftover is too small, and the camera message
 * becomes a line of its own only while the preview is hidden. The instructions' natural height is
 * read through their intrinsic height, so every decision is made before anything is measured and
 * does not feed back into itself: the preview's fit is judged against the instructions at full
 * size, and the message line, which only exists while the preview is hidden, can therefore never
 * bring the preview back.
 */
@Composable
private fun VocalBody(
    component: TaskComponent,
    state: TaskComponent.State,
    vocal: TaskComponent.Content.Vocal,
    capturing: Boolean,
    localization: UiLocalization,
    modifier: Modifier,
) {
    val feed = vocal.video
    val messageShowing = feed?.showsCameraMessage() == true
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val instructionsWidth = minOf(width, INSTRUCTIONS_MAX_WIDTH.roundToPx())

        val instructionsItem = subcompose(VocalSlot.Instructions) {
            InstructionsField(component, state, localization, Modifier, scrollable = true)
        }.first()
        val natural = minOf(instructionsItem.maxIntrinsicHeight(instructionsWidth), height)
        val previewFits = feed != null && height - natural >= PREVIEW_BLOCK_HEIGHT.roundToPx()

        // Preview hidden and a camera message to show: it takes a line of its own, the instructions give way
        val messageLine = if (feed != null && messageShowing && !previewFits) {
            subcompose(VocalSlot.Message) { CameraMessage(feed, localization, Modifier.fillMaxWidth()) }
                .first().measure(Constraints(maxWidth = width))
        } else {
            null
        }
        val messageHeight = messageLine?.height ?: 0
        val instructions = instructionsItem.measure(
            Constraints(instructionsWidth, instructionsWidth, 0, (height - messageHeight).coerceAtLeast(0)),
        )

        val room = (height - instructions.height - messageHeight).coerceAtLeast(0)
        val indicatorShown = vocal.showIndicator && (previewFits || room >= MIN_INDICATOR_HEIGHT.roundToPx())
        // spare height only: what is left above the shown middle area's required minimum, at most the full gap
        val required = if (previewFits) PREVIEW_BLOCK_HEIGHT.roundToPx() else MIN_INDICATOR_HEIGHT.roundToPx()
        val gap = minOf(INSTRUCTIONS_MIDDLE_GAP.roundToPx(), room - required).coerceAtLeast(0)
        val middle = if (previewFits || indicatorShown) {
            subcompose(VocalSlot.Middle) {
                VocalMiddleArea(vocal, capturing, localization, showPreview = previewFits, showIndicator = indicatorShown)
            }.first().measure(Constraints(maxWidth = width, maxHeight = room - gap))
        } else {
            null
        }

        layout(width, height) {
            instructions.placeRelative((width - instructions.width) / 2, 0)
            messageLine?.placeRelative((width - messageLine.width) / 2, height - messageHeight)
            middle?.placeRelative((width - middle.width) / 2, instructions.height + gap + (room - gap - middle.height) / 2)
        }
    }
}

/** The indicator and/or the 200x150 preview (legacy: a small preview beside the recording circle), each at the height left to it. */
@Composable
private fun VocalMiddleArea(
    vocal: TaskComponent.Content.Vocal,
    capturing: Boolean,
    localization: UiLocalization,
    showPreview: Boolean,
    showIndicator: Boolean,
) {
    val feed = vocal.video
    val indicator: @Composable () -> Unit = {
        TaskLevelIndicator(
            indicatorType = vocal.indicatorType,
            level = vocal.level,
            capturing = capturing,
            history = vocal.levelHistory,
        )
    }
    Box(contentAlignment = Alignment.Center) {
        if (showPreview && feed != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(PREVIEW_INDICATOR_GAP), verticalAlignment = Alignment.CenterVertically) {
                if (showIndicator) {
                    Box(Modifier.weight(1f, fill = false).widthIn(max = INDICATOR_MAX_WIDTH), contentAlignment = Alignment.Center) {
                        indicator()
                    }
                }
                VideoPreview(feed, localization, Modifier.width(PREVIEW_WIDTH), pictureHeight = PREVIEW_PICTURE_HEIGHT)
            }
        } else if (showIndicator) {
            indicator()
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

    // The scrollbar shows only when the cards overflow the height they were given
    val scrollState = rememberScrollState()
    Box(modifier = modifier) {
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
            // An overlay of the field's size that stays out of intrinsic queries: VocalBody asks the
            // field for its intrinsic height, and a scrollbar cannot measure with unbounded height
            Layout(
                content = {
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.fillMaxHeight().testTag(TestTags.Task.INSTRUCTIONS_SCROLLBAR),
                    )
                },
                modifier = Modifier.matchParentSize(),
                measurePolicy = EndOverlayPolicy,
            )
        }
    }
}

/** Places its content at the end edge, as tall as itself; reports no intrinsic size so it never takes part in one. */
private object EndOverlayPolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        return layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { it.placeRelative(constraints.maxWidth - it.width, 0) }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = 0
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = 0
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = 0
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = 0
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

private val QUESTIONNAIRE_PADDING = 16.dp
private val QUESTION_DIVIDER_PADDING = 12.dp

/** Start padding added per rule depth of a conditional question, and the depth after which it stops growing. */
private val QUESTION_INDENT_STEP = 24.dp
private const val QUESTION_MAX_INDENT_LEVELS = 3

@Composable
private fun QuestionnaireBody(
    questions: List<Question>,
    content: TaskComponent.Content.Questionnaire,
    localization: UiLocalization,
    onOpenAnswerChanged: (String, String) -> Unit,
    onOptionToggled: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(QUESTIONNAIRE_PADDING),
    ) {
        questions.forEachIndexed { index, question ->
            key(question.questionKey) {
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier
                            .padding(vertical = QUESTION_DIVIDER_PADDING)
                            .testTag(TestTags.Questionnaire.DIVIDER),
                    )
                }
                val answer = content.answers[question.questionKey] ?: AnswerState()
                val showError = answer.showsError(content.submitAttempted)
                val indent = QUESTION_INDENT_STEP * (content.indentLevels[question.questionKey] ?: 0).coerceIn(0, QUESTION_MAX_INDENT_LEVELS)
                Column(Modifier.padding(start = indent)) {
                    Text(localization.resolve(question.questionTextKey), style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(4.dp))

                    when (question.questionType) {
                        QuestionType.OPEN -> OutlinedTextField(
                            value = answer.selected.firstOrNull().orEmpty(),
                            onValueChange = { onOpenAnswerChanged(question.questionKey, it) },
                            isError = showError,
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

                    if (showError) {
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
}
