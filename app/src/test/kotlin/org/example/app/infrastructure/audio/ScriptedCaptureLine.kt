package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.TargetDataLine

/**
 * A controllable capture line for interruption tests. Reads deliver non-zero audio paced at ~10x real time
 * until [stalled]; a closed line releases a blocked read with [partialBytesOnClose] bytes once (what a real
 * driver may do) and then reports end-of-stream. [ignoreClose] models a driver whose read never wakes.
 */
internal class ScriptedCaptureLine {
    private val closed = AtomicBoolean(false)
    private val partialSent = AtomicBoolean(false)
    private val deliveredBytes = AtomicLong(0)

    @Volatile var stalled = false
    @Volatile var ignoreClose = false
    @Volatile var partialBytesOnClose = 0

    /** Makes the one read currently blocked (or the next) return this many bytes of audio, closed or not. */
    @Volatile var releaseWithBytes = 0

    /** Bytes handed out by full-chunk reads (excludes the partial chunk released by close). */
    val fullChunkBytes: Long get() = deliveredBytes.get()
    val isClosed: Boolean get() = closed.get()

    val line: TargetDataLine = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(TargetDataLine::class.java),
    ) { _, method, args ->
        when (method.name) {
            "read" -> read(args[0] as ByteArray, args[1] as Int, args[2] as Int)
            "close" -> { close(); null }
            "isOpen", "isActive", "isRunning" -> !closed.get()
            "available", "getFramePosition", "getBufferSize" -> 0
            "getLongFramePosition", "getMicrosecondPosition" -> 0L
            "getLevel" -> 0f
            "hashCode" -> System.identityHashCode(this)
            "equals" -> args[0] === this
            "toString" -> "ScriptedCaptureLine"
            else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null
        }
    } as TargetDataLine

    /**
     * Like a driver whose blocked read wakes inside close(): the closing thread is held until the reader
     * has taken the partial chunk (plus a moment to act on it), so the "reader wins the race" ordering
     * behind F-129 is deterministic instead of a timing accident.
     */
    private fun close() {
        closed.set(true)
        if (partialBytesOnClose > 0 && !ignoreClose) {
            val deadline = System.currentTimeMillis() + 500
            while (!partialTaken.get() && System.currentTimeMillis() < deadline) Thread.sleep(1)
            Thread.sleep(30)
        }
    }

    private val partialTaken = AtomicBoolean(false)

    private fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            val released = minOf(releaseWithBytes, length)
            if (released > 0) {
                releaseWithBytes = 0
                fill(buffer, offset, released)
                return released
            }
            if (closed.get() && !ignoreClose) {
                val partial = minOf(partialBytesOnClose, length)
                if (partial > 0 && partialSent.compareAndSet(false, true)) {
                    fill(buffer, offset, partial)
                    partialTaken.set(true)
                    return partial
                }
                return -1
            }
            if (stalled || (closed.get() && ignoreClose)) {
                Thread.sleep(2)
                continue
            }
            Thread.sleep(5)
            fill(buffer, offset, length)
            deliveredBytes.addAndGet(length.toLong())
            return length
        }
    }

    private fun fill(buffer: ByteArray, offset: Int, length: Int) {
        for (i in 0 until length) buffer[offset + i] = if (i % 2 == 0) 0x10 else 0x01 // non-zero PCM16 samples
    }
}

/** Opener handing out [ScriptedCaptureLine]s; [deviceGone] flips what `deviceExists` reports. */
internal class ScriptedCaptureOpener : CaptureLineOpener {
    val lines = java.util.concurrent.CopyOnWriteArrayList<ScriptedCaptureLine>()

    @Volatile var deviceGone = false
    @Volatile var configure: (ScriptedCaptureLine) -> Unit = {}

    override fun deviceExists(deviceId: String) = !deviceGone
    override fun isLineSupported(deviceId: String, format: CaptureFormat) = true
    override fun open(deviceId: String, format: CaptureFormat, bufferMs: Int): TargetDataLine {
        val scripted = ScriptedCaptureLine().also(configure)
        lines += scripted
        return scripted.line
    }
}
