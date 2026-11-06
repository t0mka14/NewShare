package org.example.app.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LogFileExtensionTest {

    @Test
    fun `multi-part and single extensions are kept, the stem never is`() {
        assertEquals(".zip.tmp", logFileExtension("ABC123_s1.zip.tmp"))
        assertEquals(".zip", logFileExtension("""C:\data\sessions\2026-07-01_ABC123_s1\archive\ABC123_s1.zip"""))
        assertEquals(".partial.wav", logFileExtension("/d/master/session_master.partial.wav"))
        assertEquals(".wav", logFileExtension("session_master.part2.wav"))
        assertEquals(".json.tmp", logFileExtension("examination.json.tmp"))
    }

    @Test
    fun `a dot inside a rendered name does not let a piece of it through`() {
        assertEquals(".wav", logFileExtension("ABC.123_1_PHONATION.wav"))
        assertEquals(".tmp.mp4", logFileExtension("ABC123.tmp.mp4"))
    }

    @Test
    fun `a file without an extension has no name to log`() {
        assertNull(logFileExtension("ABC123_s1"))
        assertNull(logFileExtension("/data/sessions/ABC123_s1"))
        assertNull(logFileExtension("ABC123_s1."))
    }
}
