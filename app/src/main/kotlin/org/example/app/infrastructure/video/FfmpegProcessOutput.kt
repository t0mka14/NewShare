package org.example.app.infrastructure.video

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Runs a short-lived ffmpeg query and returns everything it printed, giving up after [timeoutMs].
 *
 * The obvious spelling of this — `readText()` and then `waitFor(timeout)` — cannot time out at
 * all: `readText()` returns only at end of stream, so a child that never closes its stdout blocks
 * there forever and the guard below it is never reached. Both call sites used to be written that
 * way, and one of them (camera enumeration) is reached from a Decompose `childFactory`, i.e. on
 * the Swing EDT, where it would freeze the whole UI with nothing logged.
 *
 * So the read happens on its own thread and the *wait* is what is bounded. Killing the process
 * closes the pipe, which ends the read.
 *
 * Partial output is a normal result, not a failure: both callers want a listing that ffmpeg prints
 * before it exits non-zero, and a listing truncated by a timeout still parses into whatever modes
 * or devices made it through.
 *
 * @param what how to name this query in a timeout warning.
 */
internal fun readWithTimeout(command: List<String>, timeoutMs: Long, what: String): String {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    // Nothing is ever sent to these queries, and an unclosed stdin makes some ffmpeg builds wait.
    runCatching { process.outputStream.close() }

    val collected = StringBuilder()
    val reader = Thread({
        runCatching {
            process.inputStream.reader().use { source ->
                val chunk = CharArray(READ_CHUNK_CHARS)
                while (true) {
                    val read = source.read(chunk)
                    if (read < 0) break
                    synchronized(collected) {
                        // A device listing is a few kilobytes. Anything past the cap is a process
                        // streaming rather than reporting, and is not worth holding in memory.
                        if (collected.length < MAX_CHARS) collected.append(chunk, 0, read)
                    }
                }
            }
        }
    }, "ffmpeg-query-reader").apply { isDaemon = true; start() }

    if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
        logger.warn { "$what timed out after ${timeoutMs}ms; terminating ffmpeg" }
        process.destroyForcibly()
        process.waitFor(KILL_GRACE_MS, TimeUnit.MILLISECONDS)
    }
    reader.join(READER_JOIN_MS)

    return synchronized(collected) { collected.toString() }
}

private const val READ_CHUNK_CHARS = 8 * 1024

/** Far above any real listing; a runaway process must not be able to fill the heap. */
private const val MAX_CHARS = 1 shl 20

private const val KILL_GRACE_MS = 500L
private const val READER_JOIN_MS = 500L
