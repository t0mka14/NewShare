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
