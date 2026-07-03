package org.example.app.infrastructure

import org.example.app.domain.AudioRecorder
import java.io.File
import javax.sound.sampled.*
import kotlin.concurrent.thread

class JvmAudioRecorder : AudioRecorder {
    private var line: TargetDataLine? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    override fun start(outputFile: File) {
        if (isRecording) return

        val format = AudioFormat(48000f, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)

        if (!AudioSystem.isLineSupported(info)) {
            throw RuntimeException("Line not supported")
        }

        line = AudioSystem.getLine(info) as TargetDataLine
        line?.open(format)
        line?.start()

        isRecording = true

        recordingThread = thread(start = true) {
            try {
                AudioSystem.write(AudioInputStream(line), AudioFileFormat.Type.WAVE, outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRecording = false
            }
        }
    }

    override fun stop() {
        line?.stop()
        line?.close()
        isRecording = false
        recordingThread?.interrupt()
    }

    override fun isRecording(): Boolean = isRecording
}
