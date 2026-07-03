package org.example.app.domain

import java.io.File

interface AudioRecorder {
    fun start(outputFile: File)
    fun stop()
    fun isRecording(): Boolean
}
