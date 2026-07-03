package org.example.app.fakes

import org.example.app.domain.AudioRecorder
import java.io.File

class FakeAudioRecorder : AudioRecorder {
    var isRecordingCalled = false
    var stopCalled = false
    var lastOutputFile: File? = null

    override fun start(outputFile: File) {
        isRecordingCalled = true
        lastOutputFile = outputFile
    }

    override fun stop() {
        stopCalled = true
        isRecordingCalled = false
    }

    override fun isRecording(): Boolean = isRecordingCalled
}
