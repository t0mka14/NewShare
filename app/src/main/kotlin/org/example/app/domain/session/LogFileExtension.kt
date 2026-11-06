package org.example.app.domain.session

/** Segments that can precede the last extension in a name the app writes: `.zip.tmp`, `.partial.wav`, `.tmp.mp4`, `.events.jsonl`. */
private val COMPOUND_EXTENSION_PARTS = setOf("zip", "json", "partial", "events", "tmp", "mp4", "wav")

private val EXTENSION_SEGMENT = Regex("[A-Za-z0-9]{1,8}")

/**
 * The extension of [path]'s file name for a log line, or null when it has none. A file name here
 * embeds the patient code (`<PatientCode>_<SessionId>.zip.tmp`, rendered clip names), so a log may
 * carry the extension and nothing else: `X_Y.zip.tmp` gives `.zip.tmp`, `session_master.part2.wav`
 * gives `.wav`, `ABC123_x` gives null. Only known compound parts are kept, so a dot inside a
 * rendered name never lets a piece of it through.
 */
internal fun logFileExtension(path: String): String? {
    val name = path.substringAfterLast('/').substringAfterLast('\\')
    val segments = name.split('.')
    if (segments.size < 2) return null
    val last = segments.last().takeIf { EXTENSION_SEGMENT.matches(it) } ?: return null
    val extension = ArrayDeque<String>().apply { addFirst(last) }
    var index = segments.size - 2
    // segments[0] is the stem, never part of the extension.
    while (index >= 1 && segments[index].lowercase() in COMPOUND_EXTENSION_PARTS) {
        extension.addFirst(segments[index])
        index--
    }
    return "." + extension.joinToString(".")
}
