package org.example.app.infrastructure.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.audio.AudioInputDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JvmContinuousSessionRecorderFastLevelsTest {

    private val dispatchers = object : CoroutineDispatchers {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
    }

    /**
     * The scripted line delivers identical audio, so every chunk measures the same level. A conflated
     * StateFlow would deliver that value once and never again; a collector slower than the chunk rate
     * would additionally skip values. One value per chunk means a slow collector still sees many.
     */
    @Test
    fun `F-144 fastLevels delivers one value per chunk, not conflated, to a slow collector`() = runBlocking {
        val opener = ScriptedCaptureOpener()
        val recorder = JvmContinuousSessionRecorder(dispatchers, opener)
        try {
            recorder.startMonitoring(AudioInputDevice(id = "dev", name = "Fake mic", eligible = true))
            val values = withTimeout(5_000) {
                val out = mutableListOf<Float>()
                recorder.fastLevels.take(15).collect { out += it; delay(20) } // far slower than the ~5 ms chunk pace
                out
            }
            assertEquals(15, values.size)
            // After the replayed initial 0f, the real (equal, non-zero) chunk levels follow one by one.
            assertEquals(true, values.drop(2).all { it > 0f })
        } finally {
            recorder.stop()
        }
    }
}
