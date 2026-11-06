package org.example.app.domain.video

import kotlinx.serialization.SerializationException

private val TOKEN = Regex("""\S+""")
private const val TOKEN_LEADING = "\"'([{<"
private const val TOKEN_TRAILING = "\"')]}>.,;:!?"

/**
 * Names the app writes into a session folder or hands to the uploader: `.wav` (master, parts,
 * clips, `.partial.wav`), `.mp4` and `.mjpeg` (video takes and clips, `.tmp.mp4`), `.zip` (the
 * `<PatientCode>_<SessionId>.zip` archive, `.zip.tmp` while it is built) and `.json` / `.jsonl`
 * (`examination.json`, `timeline.events.jsonl`, `*.json.tmp`, ...). Multi-part names such as
 * `.zip.tmp` or `.partial.wav` end in one of these, so the last extension decides.
 */
private val SESSION_FILE_EXTENSIONS = listOf(".wav", ".mp4", ".mjpeg", ".zip", ".tmp", ".json", ".jsonl")

/**
 * A session folder name, `yyyy-MM-dd_<sanitised patient code>_<session id>` (see
 * `SessionFolderNaming.build`): a date prefix, then `[A-Za-z0-9_-]` only. It embeds the patient
 * code, so a bare name (no separator, no extension) must be caught on its own. A plain date or a
 * timestamp has no `_` after the date and is not matched.
 */
private val SESSION_FOLDER_NAME = Regex("""(?<![\w-])\d{4}-\d{2}-\d{2}_[A-Za-z0-9_-]+""")

/**
 * Tokens that contain a separator but are diagnostics, not paths: `I/O`, strerror's `Input/output`,
 * ffmpeg's `[out#0/mjpeg @ ..]` tags, bounded ratios such as a frame rate `30/1`, `25.00/1` or `30000/1001`, and
 * `N/A`. Matched against the whole token, so nothing path-like (`1/ABC123`, `in/out`) is exempt.
 */
private val NOT_A_PATH = Regex(
    """(?i)i/o|input/output|n/a|\d{1,5}(\.\d+)?/\d{1,4}|(in|out|vist|vost|aist|aost|dec|enc)#[\d:]+/[a-z0-9_]+""",
)

private val PLACEHOLDER_FRAGMENT = Regex("""<+(<(?:path|session)>)>*""")

private fun redactToken(token: String): String {
    val leading = token.takeWhile { it in TOKEN_LEADING }
    val trailing = token.substring(leading.length).takeLastWhile { it in TOKEN_TRAILING }
    val core = token.substring(leading.length, token.length - trailing.length)
    if (core.isEmpty() || NOT_A_PATH.matches(core)) return token
    val pathLike = core.any { it == '/' || it == '\\' } ||
        SESSION_FILE_EXTENSIONS.any { core.length > it.length && core.endsWith(it, ignoreCase = true) }
    val body = if (pathLike) "<path>" else core.replace(SESSION_FOLDER_NAME, "<session>")
    return "$leading$body$trailing"
}

/**
 * Replaces every file-system path in [text] with `<path>`. Capture errors name the file ffmpeg
 * could not open, and a session path embeds the patient code, so nothing taken from ffmpeg's
 * output may reach a log line or an uploaded hardware event unredacted.
 *
 * One pass over the whitespace-delimited tokens, so a placeholder is never redacted again. In a
 * token, leading `"'([{<` and trailing `"')]}>.,;:!?` are set aside and kept. The rest is:
 *  1. left alone when it is a diagnostic (`I/O`, `Input/output`, `N/A`, a ratio such as `30/1`,
 *     `25.00/1` or `30000/1001`, an ffmpeg `[out#0/mjpeg @ ..]` tag);
 *  2. replaced whole by `<path>` when it contains `/` or `\` (absolute or relative, drive, UNC or
 *     POSIX; whatever precedes the first separator goes too) or ends in a session file
 *     extension (bare names such as `ABC123_sess.zip.tmp`);
 *  3. otherwise has any session folder name (`2026-07-01_ABC123_<id>`) replaced by `<session>`.
 * A device string such as `video=OBS Virtual Camera` has no separator and survives. Finally a
 * placeholder that token punctuation wrapped, or that already stood in the input in front of a
 * separator (`<path>/x`), collapses to the single placeholder.
 *
 * Ratio bound: `\d{1,5}(\.\d+)?/\d{1,4}`. Five digits cover NTSC's `30000/1001`; a six-digit
 * numerator such as the Czech birth number `790101/1234` stays redacted.
 *
 * Accepted limits: a path with spaces is cut at the spaces and each piece holding a separator is
 * redacted, but a final fragment without one (the `Novak` of `C:\Users\Jan Novak`) survives; an
 * identifier shaped like a ratio with at most five digits before the slash and four after
 * (`12345/678`) is not recognised; and any other token with a slash is redacted, including
 * non-paths such as `and/or`.
 */
fun redactPaths(text: String): String =
    text.replace(TOKEN) { redactToken(it.value) }.replace(PLACEHOLDER_FRAGMENT, "$1")

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
 * its suppressed exceptions run through [redactPaths]. A `kotlinx.serialization.SerializationException`
 * (or subclass) keeps only its class name: its message quotes the JSON input, which can be
 * `participant.json`. Log this instead of [t] itself.
 */
fun redacted(t: Throwable): Throwable = redacted(t, HashSet())

private fun redacted(t: Throwable, seen: MutableSet<Throwable>): Throwable {
    val name = t::class.qualifiedName ?: t::class.simpleName ?: "Throwable"
    val message = if (t is SerializationException) {
        name
    } else {
        name + (t.message?.let { ": ${redactPaths(it).take(500)}" } ?: "")
    }
    val cause = t.cause?.takeIf { seen.add(it) }?.let { redacted(it, seen) }
    return RedactedThrowable(message, cause).also { copy ->
        copy.stackTrace = t.stackTrace
        t.suppressed.filter { seen.add(it) }.forEach { copy.addSuppressed(redacted(it, seen)) }
    }
}
