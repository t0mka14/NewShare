package org.example.app.domain.localization

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.richtext.RichText
import org.example.app.domain.richtext.RichTextNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class LocalizedStringProviderTest {

    private val provider = LocalizedStringProvider(builtins = mapOf("builtin.key" to "Builtin value"))

    private fun configWith(
        defaultLanguage: String = "cs",
        strings: Map<String, Map<String, String>>,
    ): RemoteConfig = RemoteConfig(
        schemaVersion = 1,
        configVersion = "v1",
        defaultLanguage = defaultLanguage,
        strings = strings,
    )

    @Test
    fun `resolves from the selected language when present`() {
        val config = configWith(
            defaultLanguage = "cs",
            strings = mapOf(
                "cs" to mapOf("greeting" to "Ahoj"),
                "en" to mapOf("greeting" to "Hello"),
            ),
        )

        assertEquals("Hello", provider.resolveRaw("greeting", "en", config))
        assertEquals("Ahoj", provider.resolveRaw("greeting", "cs", config))
    }

    @Test
    fun `falls back to defaultLanguage when the selected language is missing the key`() {
        val config = configWith(
            defaultLanguage = "cs",
            strings = mapOf(
                "cs" to mapOf("only_in_default" to "Pouze v cs"),
                "en" to emptyMap(),
            ),
        )

        assertEquals("Pouze v cs", provider.resolveRaw("only_in_default", "en", config))
    }

    @Test
    fun `falls back to builtin when neither language has the key`() {
        val config = configWith(defaultLanguage = "cs", strings = mapOf("cs" to emptyMap()))

        assertEquals("Builtin value", provider.resolveRaw("builtin.key", "en", config))
    }

    @Test
    fun `falls back to the key itself when nothing resolves it`() {
        assertEquals("totally.unknown.key", provider.resolveRaw("totally.unknown.key", "en", null))
    }

    @Test
    fun `null config still resolves builtins`() {
        assertEquals("Builtin value", provider.resolveRaw("builtin.key", "en", null))
    }

    @Test
    fun `exact match only, no substring or contains matching`() {
        val config = configWith(
            defaultLanguage = "cs",
            strings = mapOf("cs" to mapOf("greeting_extra" to "should not match 'greeting'")),
        )

        assertEquals("greeting", provider.resolveRaw("greeting", "cs", config))
    }

    @Test
    fun `config string wins over builtin even when both define the key`() {
        val provider = LocalizedStringProvider(builtins = mapOf("shared.key" to "Builtin"))
        val config = configWith(defaultLanguage = "en", strings = mapOf("en" to mapOf("shared.key" to "From config")))

        assertEquals("From config", provider.resolveRaw("shared.key", "en", config))
    }

    @Test
    fun `resolve parses rich text markup`() {
        val config = configWith(defaultLanguage = "en", strings = mapOf("en" to mapOf("bolded" to "<bold>Hi</bold>")))

        val result: RichText = provider.resolve("bolded", "en", config)

        assertEquals(1, result.nodes.size)
        val styled = result.nodes[0] as RichTextNode.Styled
        assertEquals(RichTextNode.Text("Hi"), styled.children[0])
    }

    @Test
    fun `missing key never renders a raw ERROR string`() {
        val raw = provider.resolveRaw("nope", "en", null)

        assertTrue(raw.equals("nope"))
        assertTrue(!raw.contains("ERROR", ignoreCase = true))
    }

    @Test
    fun `missing key is logged`() {
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.ALL
        try {
            val freshProvider = LocalizedStringProvider(builtins = emptyMap())
            freshProvider.resolveRaw("unlogged.key.test", "en", null)

            assertTrue(appender.list.any { it.formattedMessage.contains("unlogged.key.test") })
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
        }
    }

    @Test
    fun `missing key is logged at most once`() {
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.ALL
        try {
            val freshProvider = LocalizedStringProvider(builtins = emptyMap())
            repeat(5) { freshProvider.resolveRaw("repeat.missing.key", "en", null) }

            val occurrences = appender.list.count { it.formattedMessage.contains("repeat.missing.key") }
            assertEquals(1, occurrences)
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
        }
    }
}
