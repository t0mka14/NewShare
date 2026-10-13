package org.example.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.example.app.domain.config.IndicatorType
import org.junit.jupiter.api.Test

/**
 * The two live indicators (§6.2) before and after a take: before the first sample CIRCLE rests
 * at minimum radius (legacy parity, §13 decision 36) while WAVEFORM draws nothing — no room
 * noise in Idle. After Stop both show the take's frozen trace (decision 61).
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
    fun `waveform trace stays on screen after Stop`() = runComposeUiTest {
        var capturing by mutableStateOf(true)
        setContent {
            TaskLevelIndicator(indicatorType = IndicatorType.WAVEFORM, level = 0.4f, capturing = capturing, takeKey = 1)
        }
        waitForIdle()
        capturing = false
        waitForIdle()

        onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a new take starts with no trace`() = runComposeUiTest {
        var takeKey by mutableStateOf(1)
        setContent {
            TaskLevelIndicator(indicatorType = IndicatorType.WAVEFORM, level = 0.4f, capturing = takeKey == 1, takeKey = takeKey)
        }
        waitForIdle()
        takeKey = 2 // a new, not yet captured take
        waitForIdle()

        onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertDoesNotExist()
    }
}
