package org.example.app.domain.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun `redaction happens before the 300 character cap`() {
        val detail = lastDiagnosticLine(listOf("x".repeat(280) + """ C:\sessions\$folder.mjpeg"""))

        assertFalse(detail.contains("HC001"), detail)
        assertTrue(detail.length <= 300)
    }
}
