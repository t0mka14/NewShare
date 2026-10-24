package org.example.app.infrastructure.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.app.domain.ErrorReporter
import org.example.app.domain.video.redacted
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/** Stack traces stay in the log; the session path in exception messages does not. */
class RedactedThrowableLoggingTest {

    private val folder = "2026-10-24_PATIENT-XY7_sess1"
    private val path = "C:/data/sessions/$folder/video/take.mjpeg"

    private fun failure(): Throwable {
        val cause = IOException("cannot read $path")
        cause.addSuppressed(IOException("close failed for $path"))
        return IllegalStateException("cannot write $path", cause)
    }

    private fun assertNoFolderButFrames(logs: List<ch.qos.logback.classic.spi.ILoggingEvent>) {
        val event = logs.single()
        assertNotNull(event.throwableProxy)
        val proxy = event.throwableProxy
        assertTrue(proxy.stackTraceElementProxyArray.isNotEmpty(), "stack frames are kept")
        assertTrue(proxy.stackTraceElementProxyArray.any { it.stackTraceElement.methodName.contains("failure") })
        assertTrue(proxy.className.endsWith("RedactedThrowable"))
        assertTrue(proxy.message.startsWith("java.lang.IllegalStateException: "), "original class name is kept")
        assertNotNull(proxy.cause)
        assertTrue(proxy.cause.suppressed.isNotEmpty(), "suppressed exceptions are kept")
        assertFalse(event.fullText.contains(folder))
        assertFalse(event.fullText.contains("PATIENT-XY7"))
        assertFalse(renderAll(proxy).contains(folder), "causes and suppressed messages are redacted too")
    }

    private fun renderAll(p: ch.qos.logback.classic.spi.IThrowableProxy): String =
        p.message + (p.cause?.let { renderAll(it) } ?: "") + p.suppressed.joinToString { renderAll(it) }

    @Test
    fun `redacted keeps the stack frames and redacts the message, cause and suppressed messages`() {
        val logger = KotlinLogging.logger {}
        val logs = captureLogs { logger.warn(redacted(failure())) { "something failed" } }

        assertNoFolderButFrames(logs)
        assertTrue(logs.single().throwableProxy.cause.message.contains("<path>"))
    }

    @Test
    fun `ErrorReporter logs the unhandled error with its stack and without the session path`() {
        val logs = captureLogs { ErrorReporter().report(failure()) }

        assertNoFolderButFrames(logs)
        assertEquals(ch.qos.logback.classic.Level.ERROR, logs.single().level)
    }
}
