package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import org.example.app.domain.AudioRecorder
import java.io.File
import java.time.format.DateTimeFormatter

interface RecorderComponent {
    val state: Value<State>
    
    fun onRecord()
    fun onStop()
    fun onStartAgain()
    fun onSave()
    fun onBack()

    data class State(
        val isRecording: Boolean = false,
        val hasRecording: Boolean = false,
        val lastRecordingPath: String? = null,
        val statusMessage: String = "Ready"
    )
}

class DefaultRecorderComponent(
    context: ComponentContext,
    private val audioRecorder: AudioRecorder,
    private val sessionsDir: File,
    private val onFinished: () -> Unit
) : RecorderComponent, ComponentContext by context {

    private val _state = MutableValue(RecorderComponent.State())
    override val state: Value<RecorderComponent.State> = _state

    private var currentFile: File? = null

    override fun onRecord() {
        val timestamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(sessionsDir, "recording_$timestamp.wav")
        currentFile = file
        try {
            audioRecorder.start(file)
            _state.value = _state.value.copy(
                isRecording = true,
                statusMessage = "Recording..."
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                statusMessage = "Error: ${e.message}"
            )
        }
    }

    override fun onStop() {
        audioRecorder.stop()
        _state.value = _state.value.copy(
            isRecording = false,
            hasRecording = true,
            lastRecordingPath = currentFile?.absolutePath,
            statusMessage = "Stopped. Recording saved to temporary file."
        )
    }

    override fun onStartAgain() {
        if (_state.value.isRecording) {
            audioRecorder.stop()
        }
        currentFile?.delete()
        currentFile = null
        _state.value = RecorderComponent.State(statusMessage = "Ready to record again.")
    }

    override fun onSave() {
        // In this simple implementation, 'stop' already saved it to a file.
        // We could implement a move from temp to final here if needed.
        _state.value = _state.value.copy(
            statusMessage = "Recording finalized at ${_state.value.lastRecordingPath}"
        )
    }

    override fun onBack() {
        if (_state.value.isRecording) {
            audioRecorder.stop()
        }
        onFinished()
    }
}
