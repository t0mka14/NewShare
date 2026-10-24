package org.example.app.domain.video

private val WINDOWS_PATH = Regex("""[A-Za-z]:[\/][^\s"'<>|]*""")
private val UNC_PATH = Regex("""\\[^\s"'<>|]+""")
private val POSIX_PATH = Regex("""(?<![\w.:])/[^\s"':<>|]+""")

/**
 * Replaces every absolute file-system path in [text] with `<path>`. Capture errors name the file
 * ffmpeg could not open, and a session path embeds the patient code, so nothing taken from
 * ffmpeg's output may reach a log line or an uploaded hardware event unredacted.
 */
fun redactPaths(text: String): String =
    text.replace(UNC_PATH, "<path>").replace(WINDOWS_PATH, "<path>").replace(POSIX_PATH, "<path>")

/**
 * The last line of [lines] that says something went wrong, for a failure's `detail`: not the
 * `frame=` progress ffmpeg prints every half second, and not its `[q] command received` echo.
 * Paths are redacted before the 300-character cap.
 */
fun lastDiagnosticLine(lines: List<String>): String {
    val line = lines.lastOrNull {
        val t = it.trim()
        !t.startsWith("frame=") && !t.startsWith("size=") && !t.contains("[q] command received")
    }
    return redactPaths(line?.trim() ?: "the capture process ended").take(300)
}

/** A copy of a throwable for logging: see [redacted]. */
class RedactedThrowable(message: String, cause: Throwable?) : Throwable(message, cause)

/**
 * [t] as a throwable that is safe to log: the original class name, the original stack frames
 * (class, method, file, line; no session paths), and the messages of the throwable, its causes and
 * its suppressed exceptions run through [redactPaths]. Log this instead of [t] itself.
 */
fun redacted(t: Throwable): Throwable = redacted(t, HashSet())

private fun redacted(t: Throwable, seen: MutableSet<Throwable>): Throwable {
    val name = t::class.qualifiedName ?: t::class.simpleName ?: "Throwable"
    val message = name + (t.message?.let { ": ${redactPaths(it).take(500)}" } ?: "")
    val cause = t.cause?.takeIf { seen.add(it) }?.let { redacted(it, seen) }
    return RedactedThrowable(message, cause).also { copy ->
        copy.stackTrace = t.stackTrace
        t.suppressed.filter { seen.add(it) }.forEach { copy.addSuppressed(redacted(it, seen)) }
    }
}
