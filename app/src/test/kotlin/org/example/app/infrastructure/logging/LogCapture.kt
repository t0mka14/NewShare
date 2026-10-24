package org.example.app.infrastructure.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
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

/** The formatted message plus the thrown exception's message, i.e. everything a log sink prints. */
val ILoggingEvent.fullText: String
    get() = formattedMessage + (throwableProxy?.let { " " + it.className + ": " + it.message } ?: "")
