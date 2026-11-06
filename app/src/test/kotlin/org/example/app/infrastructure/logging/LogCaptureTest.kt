package org.example.app.infrastructure.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class LogCaptureTest {

    @Test
    fun `fullText includes every nested cause and suppressed exception`() {
        val root = java.nio.file.NoSuchFileException("deep-cause-file")
        val middle = IOException("middle-message", root)
        middle.addSuppressed(IllegalStateException("suppressed-message"))
        val top = RuntimeException("top-message", middle)
        val logger = KotlinLogging.logger {}

        val text = captureLogs { logger.warn(top) { "context" } }.single().fullText

        for (part in listOf("context", "top-message", "middle-message", "suppressed-message", "deep-cause-file", "NoSuchFileException", "IllegalStateException")) {
            assertTrue(part in text, "'$part' missing from: $text")
        }
    }

    @Test
    fun `a cause cycle does not loop`() {
        val a = RuntimeException("a-message")
        val b = RuntimeException("b-message", a)
        a.initCause(b)
        val logger = KotlinLogging.logger {}

        val text = captureLogs { logger.warn(a) { "cyclic" } }.single().fullText

        assertTrue("a-message" in text && "b-message" in text, text)
    }
}
