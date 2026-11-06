package org.example.app.infrastructure.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/** Runs [block] with a logback appender on the root logger and returns what was logged. */
fun captureLogs(block: () -> Unit): List<ILoggingEvent> {
    val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    val appender = ListAppender<ILoggingEvent>().also { it.start() }
    root.addAppender(appender)
    val previousLevel = root.level
    root.level = Level.ALL
    try {
        block()
    } finally {
        root.level = previousLevel
        root.detachAppender(appender)
    }
    return appender.list.toList()
}

/**
 * The formatted message plus the class and message of the thrown exception, every nested cause and
 * every suppressed exception, i.e. what a log sink prints. Cycles in the cause graph are cut.
 */
val ILoggingEvent.fullText: String
    get() {
        val out = StringBuilder(formattedMessage)
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<IThrowableProxy, Boolean>())
        fun walk(proxy: IThrowableProxy?) {
            if (proxy == null || !seen.add(proxy)) return
            out.append(' ').append(proxy.className).append(": ").append(proxy.message)
            proxy.suppressed?.forEach { walk(it) }
            walk(proxy.cause)
        }
        walk(throwableProxy)
        return out.toString()
    }
