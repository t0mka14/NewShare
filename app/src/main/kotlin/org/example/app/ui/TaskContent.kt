package org.example.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.delay
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.config.IndicatorType
import org.example.app.domain.config.QuestionType
import org.example.app.domain.config.QuestionnaireTask
import org.example.app.domain.config.Task
import org.example.app.domain.config.VocalTask
import org.example.app.navigation.AnswerState
import org.example.app.navigation.TaskComponent
import org.example.app.navigation.TaskScreenState

/**
 * §8.6 task screen. [taskDefinition]/[positionInProtocol]/[totalNavigableTasks]/[availableDevices]
 * come from the caller ([SessionContent]) rather than [TaskComponent]'s own state — see the
 * chunk-2 gap notes on `TaskComponent.Content` (no question/task metadata, no instance count, no
 * device list) forwarded to the tech lead; [SessionContent] recomputes the same expansion
 * `SessionComponent` uses internally ([org.example.app.domain.timeline.TaskInstanceExpander])
 * purely to look these up for rendering, never to drive navigation itself.
 */
@Composable
fun TaskContent(
    component: TaskComponent,
    localization: UiLocalization,
    taskDefinition: Task,
    positionInProtocol: Int,
    totalNavigableTasks: Int,
    availableDevices: List<AudioInputDevice>,
    initialDevice: AudioInputDevice?,
) {
    val state by component.state.subscribeAsState()

    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TaskHeader(state, localization, positionInProtocol, totalNavigableTasks)
            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(0.8f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    state.instructionKeys.forEach { key -> Text(localization.resolve(key)) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            when (val content = state.content) {
                is TaskComponent.Content.Vocal -> VocalTaskBody(
                    content = content,
                    localization = localization,
                    indicatorType = localization.config?.indicatorType ?: IndicatorType.CIRCLE,
                    showIndicator = (taskDefinition as? VocalTask)?.showIndicator ?: true,
                )

                is TaskComponent.Content.Questionnaire -> QuestionnaireBody(
                    task = taskDefinition as QuestionnaireTask,
                    content = content,
                    localization = localization,
                    onOpenAnswerChanged = component::onOpenAnswerChanged,
                    onOptionToggled = component::onOptionToggled,
                )

                TaskComponent.Content.Info -> Unit
            }

            Spacer(modifier = Modifier.height(32.dp))
            TaskButtonRow(component, state, localization)
            Spacer(modifier = Modifier.height(24.dp))
        }

        val content = state.content
        if (content is TaskComponent.Content.Vocal && content.deviceLost) {
            DeviceLostDialog(
                localization = localization,
                availableDevices = availableDevices,
                defaultDevice = initialDevice,
                requiresExplicitResume = true,
                messageTag = TestTags.Task.DEVICE_LOST_ERROR,
                onDeviceAction = component::onDeviceReselected,
            )
        }

        val screenState = (content as? TaskComponent.Content.Vocal)?.screenState
        if (screenState is TaskScreenState.Failed) {
            var dismissed by remember(screenState) { mutableStateOf(false) }
            if (!dismissed) {
                AlertDialog(
                    onDismissRequest = { dismissed = true },
                    modifier = Modifier.testTag(TestTags.Task.ERROR_DIALOG),
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
}

@Composable
private fun TaskHeader(
    state: TaskComponent.State,
    localization: UiLocalization,
    positionInProtocol: Int,
    totalNavigableTasks: Int,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (totalNavigableTasks > 0) {
                localization.resolve(
                    "task.numberOfTotalLabel",
                    mapOf("n" to positionInProtocol.toString(), "total" to totalNavigableTasks.toString()),
                )
            } else {
                localization.resolve("task.numberLabel", mapOf("n" to (state.taskIndex + 1).toString()))
            },
            style = MaterialTheme.typography.subtitle1,
        )
        if (state.repetition > 1) {
            Text(
                localization.resolve("task.repetitionLabel", mapOf("n" to state.repetition.toString())),
                style = MaterialTheme.typography.caption,
            )
        }
        Text(localization.resolve(state.titleKey), style = MaterialTheme.typography.h4)
    }
}

@Composable
private fun VocalTaskBody(
    content: TaskComponent.Content.Vocal,
    localization: UiLocalization,
    indicatorType: IndicatorType,
    showIndicator: Boolean,
) {
    val capturing = content.screenState is TaskScreenState.Capturing
    val elapsedSeconds = rememberElapsedSeconds(capturing = capturing, resetKey = content.takeNumber)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (content.takeNumber > 0) {
            Text(localization.resolve("task.takeLabel", mapOf("n" to content.takeNumber.toString())))
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (showIndicator) {
            TaskLevelIndicator(indicatorType = indicatorType, level = content.level)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (content.takeNumber > 0) {
            Text("${elapsedSeconds}s", style = MaterialTheme.typography.h6)
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
    task: QuestionnaireTask,
    content: TaskComponent.Content.Questionnaire,
    localization: UiLocalization,
    onOpenAnswerChanged: (String, String) -> Unit,
    onOptionToggled: (String, String, Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(0.8f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        task.questions.forEach { question ->
            val answer = content.answers[question.questionKey] ?: AnswerState()
            Column {
                Text(localization.resolve(question.questionTextKey), style = MaterialTheme.typography.subtitle1)
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
                                Text(localization.resolve(optionKey))
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
                                Text(localization.resolve(optionKey))
                            }
                        }
                    }
                }

                if (!answer.valid) {
                    Text(
                        localization.resolve("questionnaire.error.invalid"),
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.testTag(TestTags.Questionnaire.validationError(question.questionKey)),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskButtonRow(component: TaskComponent, state: TaskComponent.State, localization: UiLocalization) {
    val buttons = state.buttons
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = component::onStart, enabled = buttons.startEnabled, modifier = Modifier.testTag(TestTags.Task.START_BUTTON)) {
            Text(localization.resolve("action.start"))
        }
        Button(onClick = component::onStop, enabled = buttons.stopEnabled, modifier = Modifier.testTag(TestTags.Task.STOP_BUTTON)) {
            Text(localization.resolve("action.stop"))
        }
        Button(onClick = component::onRepeat, enabled = buttons.repeatEnabled, modifier = Modifier.testTag(TestTags.Task.REPEAT_BUTTON)) {
            Text(localization.resolve("action.repeat"))
        }
        Button(onClick = component::onSkip, enabled = buttons.skipEnabled, modifier = Modifier.testTag(TestTags.Task.SKIP_BUTTON)) {
            Text(localization.resolve("action.skip"))
        }
        Button(onClick = component::onNext, enabled = buttons.nextEnabled, modifier = Modifier.testTag(TestTags.Task.NEXT_BUTTON)) {
            Text(localization.resolve("action.next"))
        }
    }
}
