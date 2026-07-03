package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.fakes.FakeAudioRecorder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class RecorderComponentTest {

    @Test
    fun `when record is clicked then recorder starts`() {
        val fakeRecorder = FakeAudioRecorder()
        val tempDir = Files.createTempDirectory("recorder_test").toFile()
        val context = DefaultComponentContext(LifecycleRegistry())
        
        val component = DefaultRecorderComponent(
            context = context,
            audioRecorder = fakeRecorder,
            sessionsDir = tempDir,
            onFinished = {}
        )

        component.onRecord()

        assertTrue(fakeRecorder.isRecordingCalled)
        assertTrue(component.state.value.isRecording)
    }

    @Test
    fun `when stop is clicked then recorder stops`() {
        val fakeRecorder = FakeAudioRecorder()
        val tempDir = Files.createTempDirectory("recorder_test").toFile()
        val context = DefaultComponentContext(LifecycleRegistry())
        
        val component = DefaultRecorderComponent(
            context = context,
            audioRecorder = fakeRecorder,
            sessionsDir = tempDir,
            onFinished = {}
        )

        component.onRecord()
        component.onStop()

        assertTrue(fakeRecorder.stopCalled)
        assertFalse(component.state.value.isRecording)
        assertTrue(component.state.value.hasRecording)
    }

    @Test
    fun `when start again is clicked then state is reset`() {
        val fakeRecorder = FakeAudioRecorder()
        val tempDir = Files.createTempDirectory("recorder_test").toFile()
        val context = DefaultComponentContext(LifecycleRegistry())
        
        val component = DefaultRecorderComponent(
            context = context,
            audioRecorder = fakeRecorder,
            sessionsDir = tempDir,
            onFinished = {}
        )

        component.onRecord()
        component.onStop()
        component.onStartAgain()

        assertFalse(component.state.value.isRecording)
        assertFalse(component.state.value.hasRecording)
        assertEquals("Ready to record again.", component.state.value.statusMessage)
    }
}
