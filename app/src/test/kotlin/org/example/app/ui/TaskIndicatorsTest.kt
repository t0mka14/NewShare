package org.example.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.example.app.domain.config.IndicatorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
                history = emptyList(),
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
                history = listOf(0.4f),
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
                history = emptyList(),
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
            TaskLevelIndicator(indicatorType = IndicatorType.WAVEFORM, level = 0.4f, history = listOf(0.4f), capturing = capturing)
        }
        waitForIdle()
        capturing = false
        waitForIdle()

        onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a new take starts with no trace`() = runComposeUiTest {
        var history by mutableStateOf(listOf(0.4f))
        setContent {
            TaskLevelIndicator(indicatorType = IndicatorType.WAVEFORM, level = 0.4f, history = history, capturing = history.isNotEmpty())
        }
        waitForIdle()
        history = emptyList() // a new, not yet captured take
        waitForIdle()

        onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun `F-144 three points span the full width, centred in thirds`() {
        val bars = waveformBars(listOf(0.1f, 0.5f, 0.9f), widthPx = 300f, maxStrokePx = 6f)
        assertEquals(listOf(50f, 150f, 250f), bars.map { it.x })
        assertTrue(bars.all { it.strokeWidth <= 6f && it.strokeWidth >= 1f })
        assertEquals(listOf(0.1f, 0.5f, 0.9f), bars.map { it.level })
    }

    @Test
    fun `F-144 a single point is centred, not left-aligned`() {
        val bars = waveformBars(listOf(0.5f), widthPx = 400f, maxStrokePx = 6f)
        assertEquals(200f, bars.single().x)
    }

    @Test
    fun `F-144 a long take is one bar per pixel column holding the bucket maximum, still spanning the width`() {
        val history = List(2000) { if (it == 1234) 1f else 0.1f }
        val bars = waveformBars(history, widthPx = 480f, maxStrokePx = 6f)
        assertEquals(480, bars.size)
        assertEquals(0.5f, bars.first().x)
        assertEquals(479.5f, bars.last().x)
        assertEquals(1f, bars.maxOf { it.level })
        assertEquals(1, bars.count { it.level == 1f })
        assertTrue(bars.all { it.strokeWidth >= 1f })
    }
}
