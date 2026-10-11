package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.example.app.domain.config.IndicatorType
import org.junit.jupiter.api.Test

/**
 * The two live indicators (§6.2) differ in what they do outside an open take: CIRCLE stays on
 * screen at rest radius (legacy parity, §13 decision 36) while WAVEFORM disappears entirely —
 * a trace scrolling in Idle/Stopped shows room noise and reads as "recording" when nothing is.
 */
class TaskIndicatorsTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `waveform indicator is absent unless a take is being captured`() = runComposeUiTest {
        setContent {
            TaskLevelIndicator(
                indicatorType = IndicatorType.WAVEFORM,
                level = 0.4f,
                capturing = false,
            )
        }

        onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `waveform indicator renders while capturing`() = runComposeUiTest {
        setContent {
            TaskLevelIndicator(
                indicatorType = IndicatorType.WAVEFORM,
                level = 0.4f,
                capturing = true,
            )
        }

        onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `circle indicator stays on screen when idle`() = runComposeUiTest {
        setContent {
            TaskLevelIndicator(
                indicatorType = IndicatorType.CIRCLE,
                level = 0f,
                capturing = false,
            )
        }

        onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the preview glow renders its content while capturing a loud level`() = runComposeUiTest {
        setContent {
            LevelGlow(level = 0.9f, capturing = true, modifier = androidx.compose.ui.Modifier.size(400.dp, 300.dp)) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.testTag("content"))
            }
        }
        mainClock.advanceTimeBy(2_000) // let the spring reach the level so the blurred halo is drawn
        waitForIdle()

        onNodeWithTag(TestTags.Task.LEVEL_GLOW).assertExists()
        onNodeWithTag("content").assertExists()
    }
}
