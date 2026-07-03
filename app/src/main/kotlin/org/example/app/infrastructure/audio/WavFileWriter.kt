package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Writes PCM data to a `*.partial.wav` staging file with a real, periodically
 * patched WAV header, then atomically renames to the final path on
 * [finalizeAndRename] (§8.4). Data writes and header patches go through this
 * single [RandomAccessFile]/[FileChannel] handle, as required for Windows
 * file-sharing safety.
 *
 * Not thread-safe by design: callers (the recorder's dedicated capture thread)
 * must serialize [write] and [patchHeader] calls themselves — this keeps the
 * "one handle, no cross-thread races" property trivially true rather than
 * papering over it with locks.
 */
class WavFileWriter private constructor(
    private val channel: FileChannel,
    val partialPath: Path,
    val finalPath: Path,
    val format: CaptureFormat,
) {
    /** PCM bytes written so far in *this* part (local to the current file, not
     * the recorder's global cross-part counter — see `GlobalSampleCounter`). */
    var bytesWritten: Long = 0
        private set

    private var closed = false

    /** Appends [length] bytes from [buffer] starting at [offset] as PCM data. */
    fun write(buffer: ByteArray, offset: Int, length: Int) {
        check(!closed) { "writer already closed" }
        channel.position(WavHeader.HEADER_SIZE.toLong() + bytesWritten)
        channel.write(ByteBuffer.wrap(buffer, offset, length))
        bytesWritten += length
    }

    /** Rewrites the header in place to reflect [bytesWritten] so far (§8.4: every ~5 s). */
    fun patchHeader() {
        check(!closed) { "writer already closed" }
        val header = WavHeader.build(format, bytesWritten)
        channel.position(0)
        channel.write(ByteBuffer.wrap(header))
        channel.force(true)
    }

    /** Final header patch + atomic rename `*.partial.wav` -> the target path (clean stop, §8.4). */
    fun finalizeAndRename(): Path {
        check(!closed) { "writer already closed" }
        patchHeader()
        channel.close()
        closed = true
        Files.move(
            partialPath, finalPath,
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
        )
        return finalPath
    }

    /** Closes the handle without renaming, leaving the last-patched partial file
     * on disk as-is (used when a lower layer already decided not to finalize). */
    fun closeWithoutRename() {
        if (closed) return
        try {
            channel.force(true)
        } finally {
            channel.close()
            closed = true
        }
    }

    companion object {
        /** Derives the `*.partial.wav` staging path for a given final target path. */
        fun partialPathFor(finalPath: Path): Path {
            val name = finalPath.fileName.toString()
            require(name.endsWith(".wav")) { "expected a .wav target path, got $name" }
            val partialName = name.removeSuffix(".wav") + ".partial.wav"
            return finalPath.resolveSibling(partialName)
        }

        /** Creates a new partial file at [finalPath]'s staging path, truncating any
         * pre-existing partial file, and writes a placeholder (zero-length) header. */
        fun create(finalPath: Path, format: CaptureFormat): WavFileWriter {
            val partialPath = partialPathFor(finalPath)
            finalPath.parent?.let { Files.createDirectories(it) }
            val raf = RandomAccessFile(partialPath.toFile(), "rw")
            raf.setLength(0)
            val channel = raf.channel
            channel.write(ByteBuffer.wrap(WavHeader.build(format, 0L)))
            channel.force(true)
            return WavFileWriter(channel, partialPath, finalPath, format)
        }
    }
}
