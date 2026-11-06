package org.example.app.domain.video

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import java.time.Duration

/** A capture error names the file ffmpeg failed on; the session path embeds the patient code. */
class ErrorTextTest {

    private val folder = "2026-10-24_HC001_76a67cd8af73486da13a7c0e1509bd2f"

    @Test
    fun `an unexpected end whose last line names a Windows path keeps neither the code nor the folder`() {
        val detail = lastDiagnosticLine(
            listOf(
                "frame=  15 fps=15 q=3.0 size=  100kB time=00:00:01.00",
                """[out#0/mjpeg @ 000001] Error writing trailer of C:\Users\x\data\sessions\$folder\video\task00_rep01_take01.mjpeg: I/O error""",
                "frame=  16 fps=15 q=3.0 size=  110kB time=00:00:01.06",
            ),
        )

        assertTrue(detail.contains("<path>"), detail)
        assertFalse(detail.contains("HC001"), detail)
        assertFalse(detail.contains(folder), detail)
        assertTrue(detail.contains("I/O error"), "the reason survives: $detail")
    }

    @Test
    fun `UNC and POSIX paths are redacted too`() {
        assertEquals("open <path> failed", redactPaths("""open \srv\share\$folder\a.mjpeg failed"""))
        assertEquals("open <path> failed", redactPaths("open /home/u/sessions/$folder/a.mjpeg failed"))
        assertEquals("Error opening input: I/O error", redactPaths("Error opening input: I/O error"))
    }

    @Test
    fun `paths with spaces, relative paths and bare session file names are redacted`() {
        val cases = mapOf(
            "spaced Windows path" to """failed C:\Users\Jan Novak\data\sessions\ABC123_x\clip.wav now""",
            "relative path" to "cannot open data/sessions/ABC123_x/clip.wav now",
            "relative Windows path" to """cannot open data\sessions\ABC123_x\clip.wav now""",
            "bare archive name" to "cannot open ABC123_sess.zip now",
            "multi-part extension" to "cannot rename ABC123_sess.zip.tmp now",
            "quoted bare name" to "cannot open 'ABC123_sess.zip.tmp': denied",
            "UNC" to """cannot open \\srv\share\ABC123_x\clip.wav now""",
            "POSIX" to "cannot open /home/u/sessions/ABC123_x/clip.wav now",
            "trailing full stop" to "wrote ABC123_x.mp4.",
        )
        for ((name, input) in cases) {
            val out = redactPaths(input)
            assertFalse(out.contains("ABC123"), "$name leaked the code: $out")
            assertFalse(out.contains("Novak\\") || out.contains("Novak/"), "$name leaked a path tail: $out")
            assertTrue(out.contains("<path>"), "$name was not replaced: $out")
        }
        // Quotes and punctuation around a token stay.
        assertEquals("cannot open '<path>': denied", redactPaths("cannot open 'ABC123_sess.zip.tmp': denied"))
        assertEquals("wrote <path>.", redactPaths("wrote ABC123_x.mp4."))
        assertEquals("cannot open <path> now", redactPaths("cannot open data/sessions/ABC123_x/clip.wav now"))
    }

    @Test
    fun `a token with a separator is redacted whole whatever comes first`() {
        val cases = mapOf(
            """cannot open ABC123_x\clip.wav now""" to "cannot open <path> now",
            """ABC123_sess.zip\entry""" to "<path>",
            """C:\data\ABC123_x\clip.wav""" to "<path>",
            "C:/data/ABC123_x/clip.wav" to "<path>",
            """\server\share\ABC123_x\clip.wav""" to "<path>",
            // A spaced path is cut at the space; each piece with a separator goes whole.
            """failed C:\Users\Jan Novak\data\sessions\ABC123_x\clip.wav now""" to "failed <path> <path> now",
            "2026-07-01_ABC123_s1.zip" to "<path>",
            """2026-07-01_ABC123_s1\video\x.mjpeg""" to "<path>",
            "(2026-07-01_ABC123_s1/clip.wav)" to "(<path>)",
            """Could not open file : 2026-07-01_ABC123_s1\video\x.mjpeg""" to "Could not open file : <path>",
        )
        for ((input, expected) in cases) assertEquals(expected, redactPaths(input), input)
    }

    @Test
    fun `a placeholder is never redacted again and wrapping punctuation exposes nothing`() {
        assertEquals("<<path>>", redactPaths("<data/ABC123/x>"))
        assertEquals("<<path>", redactPaths("<path>/x"))
        assertEquals("<<path>", redactPaths("""<session>\video\x.mjpeg"""))
        assertEquals("open <<path> now", redactPaths("open <path>/x now"))
        assertEquals("<path>", redactPaths("<path>"))
        assertEquals("<session>", redactPaths("<session>"))
        val once = redactPaths("""a 2026-07-01_ABC123_s1\x b 2026-07-01_ABC123_s1 c""")
        assertEquals("a <path> b <session> c", once)
        assertEquals(once, redactPaths(once))
    }

    @Test
    fun `a long run of angle brackets does not slow redaction down`() {
        val input = "<".repeat(50_000)

        val out = assertTimeout(Duration.ofSeconds(5), ThrowingSupplier { redactPaths(input) })

        assertEquals(input, out)
    }

    @Test
    fun `a ratio is exempt only when it is as small as a frame rate`() {
        assertEquals("<path>", redactPaths("790101/1234")) // Czech birth number: six digits
        for (keep in listOf("30/1", "25.00/1", "30000/1001", "Input/output", "I/O", "N/A")) {
            assertEquals(keep, redactPaths(keep), keep)
        }
    }

    @Test
    fun `device strings and plain sentences without separators survive`() {
        val device = """Error opening input video=OBS Virtual Camera: Immediate exit requested"""
        assertEquals(device, redactPaths(device))
        val sentence = "Could not write the trailer because the disk is full, retrying twice."
        assertEquals(sentence, redactPaths(sentence))
        assertEquals("[out#0/mjpeg @ 000001] I/O error", redactPaths("[out#0/mjpeg @ 000001] I/O error"))
    }

    @Test
    fun `hardware diagnostics with a slash survive but path-like tokens do not`() {
        for (keep in listOf("Input/output", "30/1", "25.00/1", "N/A")) {
            assertEquals("fps $keep error", redactPaths("fps $keep error"), keep)
            assertEquals("fps $keep, error", redactPaths("fps $keep, error"), keep)
        }
        for (hide in listOf("data/ABC123/x", "1/ABC123", "in/out")) {
            val out = redactPaths("saw $hide here")
            assertEquals("saw <path> here", out, hide)
        }
    }

    @Test
    fun `a bare session folder name is redacted and a plain date or timestamp is not`() {
        val id = "6aa0f35efe574c9e8ae08b9233718c3c"
        assertEquals("<session>", redactPaths("2026-07-01_ABC123_$id"))
        assertEquals("<session>", redactPaths("2026-07-01_ABC123_s1"))
        assertEquals("cannot open <session> now", redactPaths("cannot open 2026-07-01_ABC123_s1 now"))
        assertEquals("cannot open '<session>': denied", redactPaths("cannot open '2026-07-01_ABC123_s1': denied"))
        assertEquals("folder <session>.", redactPaths("folder 2026-07-01_ABC123_$id."))
        assertEquals("(<session>)", redactPaths("(2026-07-01_ABC123_s1)"))
        val kept = listOf(
            "on 2026-07-01 the camera failed",
            "2026-07-01T10:15:30.123Z camera failed",
            "2026-07-01 10:15:30.123 [main] WARN camera failed",
        )
        for (text in kept) assertEquals(text, redactPaths(text))
    }

    private val patientCode = "ZQX-PATIENT-4417"

    private fun realDecodeFailure(): SerializationException {
        @Serializable
        data class Participant(val code: String, val age: Int)
        val json = """{"code":"$patientCode","age":"not a number"}"""
        return try {
            Json.decodeFromString<Participant>(json)
            error("decode should have failed")
        } catch (e: SerializationException) {
            e
        }
    }

    @Test
    fun `a serialization failure keeps its class name and loses the quoted JSON, also as a cause`() {
        val failure = realDecodeFailure()
        assertTrue(failure.message.orEmpty().contains(patientCode), "the raw message must quote the input: ${failure.message}")

        val direct = redacted(failure)
        assertTrue(direct.message.orEmpty().startsWith(failure::class.qualifiedName!!), direct.message)
        assertFalse(direct.message.orEmpty().contains(patientCode), direct.message)
        assertTrue(direct.stackTrace.isNotEmpty())

        val wrapped = redacted(IllegalStateException("could not load", failure))
        val cause = wrapped.cause!!
        assertFalse(cause.message.orEmpty().contains(patientCode), cause.message)
        assertTrue(cause.message.orEmpty().startsWith(failure::class.qualifiedName!!))

        val suppressing = IllegalStateException("outer").apply { addSuppressed(failure) }
        assertFalse(redacted(suppressing).suppressed.single().message.orEmpty().contains(patientCode))
    }

    @Test
    fun `redaction happens before the 300 character cap`() {
        val detail = lastDiagnosticLine(listOf("x".repeat(280) + """ C:\sessions\$folder.mjpeg"""))

        assertFalse(detail.contains("HC001"), detail)
        assertTrue(detail.length <= 300)
    }
}
