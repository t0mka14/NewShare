package org.example.app.infrastructure.lock

import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.app.domain.AppDirectories
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val logger = KotlinLogging.logger {}

/**
 * Exclusive single-instance lock (§5.2), backed by `FileChannel.tryLock` on a lock file
 * under [AppDirectories.dataRoot]. Prevents two app instances from sharing the microphone,
 * the upload queue, and session directories.
 *
 * Usage: call [acquire] once at startup; if it returns `false`, another instance already
 * holds the lock (show the localized message and exit — that's UI's responsibility, not
 * this class's). Call [release] on shutdown; it is safe to call multiple times and safe to
 * call even if [acquire] was never called or returned `false`.
 *
 * Not reentrant across separate [SingleInstanceLock] instances/`FileChannel`s by design: a
 * second instance pointed at the same lock file — whether in this JVM or another process —
 * must fail to acquire.
 */
class SingleInstanceLock(directories: AppDirectories) {

    private val lockFile: Path = directories.dataRoot.resolve("app.lock")

    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /**
     * Attempts to acquire the exclusive lock. Returns `true` if this call (or a prior call
     * on this same instance) holds it, `false` if another holder has it or the lock file
     * cannot be opened.
     */
    @Synchronized
    fun acquire(): Boolean {
        if (lock != null) return true
        return try {
            Files.createDirectories(lockFile.parent)
            val ch = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val fl = try {
                ch.tryLock()
            } catch (e: OverlappingFileLockException) {
                null
            }
            if (fl == null) {
                ch.close()
                logger.info { "single-instance lock unavailable, another instance is running" }
                false
            } else {
                channel = ch
                lock = fl
                true
            }
        } catch (e: IOException) {
            logger.warn { "single-instance lock could not be opened: ${e::class.simpleName}" }
            false
        }
    }

    /** Releases the lock and closes the channel. Safe to call repeatedly. */
    @Synchronized
    fun release() {
        try {
            lock?.release()
        } catch (e: IOException) {
            logger.warn { "single-instance lock release failed: ${e::class.simpleName}" }
        }
        try {
            channel?.close()
        } catch (e: IOException) {
            // best-effort close
        }
        lock = null
        channel = null
    }
}
