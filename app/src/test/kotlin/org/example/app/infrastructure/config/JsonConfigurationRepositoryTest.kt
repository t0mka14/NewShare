package org.example.app.infrastructure.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.example.app.domain.config.ConfigApplyResult
import org.example.app.domain.config.ConfigSchemaMigrator
import org.example.app.domain.config.ConfigValidationError
import org.example.app.fakes.ConfigFixtures
import org.example.app.fakes.TestAppDirectories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class JsonConfigurationRepositoryTest {

    @Test
    fun `loadCached returns null and leaves activeConfig null when no cache exists`(@TempDir tempDir: Path) {
        val repo = JsonConfigurationRepository(RawConfigCache(TestAppDirectories(tempDir)))

        assertNull(repo.loadCached())
        assertNull(repo.activeConfig.value)
    }

    @Test
    fun `startup path - cache to active config`(@TempDir tempDir: Path) {
        val cache = RawConfigCache(TestAppDirectories(tempDir))
        cache.write(ConfigFixtures.fullProtocol)
        val repo = JsonConfigurationRepository(cache)

        val loaded = repo.loadCached()

        assertEquals(1, loaded?.schemaVersion)
        assertEquals(2, loaded?.protocols?.size)
        assertEquals(loaded, repo.activeConfig.value)
    }

    @Test
    fun `loadCached treats a malformed cache as no cache`(@TempDir tempDir: Path) {
        val cache = RawConfigCache(TestAppDirectories(tempDir))
        cache.write("this is not json at all")
        val repo = JsonConfigurationRepository(cache)

        assertNull(repo.loadCached())
        assertNull(repo.activeConfig.value)
    }

    @Test
    fun `loadCached treats a cache that fails validation as no cache`(@TempDir tempDir: Path) {
        val cache = RawConfigCache(TestAppDirectories(tempDir))
        // recordingsFileName is missing the required ${taskIndex} placeholder -> validation error.
        val invalid = ConfigFixtures.questionnaireOnly.replace("\${taskIndex}", "")
        cache.write(invalid)
        val repo = JsonConfigurationRepository(cache)

        assertNull(repo.loadCached())
    }

    @Test
    fun `loadCached treats an unmigratable stale schema version as no cache`(@TempDir tempDir: Path) {
        val cache = RawConfigCache(TestAppDirectories(tempDir))
        val staleJson = ConfigFixtures.fullProtocol.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 0")
        cache.write(staleJson)
        // Empty migrator: nothing can bring schemaVersion 0 up to the supported range.
        val repo = JsonConfigurationRepository(cache, migrator = ConfigSchemaMigrator(emptyMap()))

        assertNull(repo.loadCached())
    }

    @Test
    fun `loadCached applies a registered migration for a stale cached schema version`(@TempDir tempDir: Path) {
        val cache = RawConfigCache(TestAppDirectories(tempDir))
        val staleJson = ConfigFixtures.fullProtocol.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 0")
        cache.write(staleJson)
        val migrator = ConfigSchemaMigrator(
            mapOf(
                0 to { obj: JsonObject -> JsonObject(obj.toMutableMap().apply { put("schemaVersion", JsonPrimitive(1)) }) },
            ),
        )
        val repo = JsonConfigurationRepository(cache, migrator = migrator)

        val loaded = repo.loadCached()

        assertEquals(1, loaded?.schemaVersion)
    }

    @Test
    fun `applyFetched validates before persisting, then activates`(@TempDir tempDir: Path) {
        val cache = RawConfigCache(TestAppDirectories(tempDir))
        val repo = JsonConfigurationRepository(cache)

        val result = repo.applyFetched(ConfigFixtures.fullProtocol)

        assertInstanceOf(ConfigApplyResult.Applied::class.java, result)
        assertEquals(ConfigFixtures.fullProtocol, cache.read())
        assertEquals((result as ConfigApplyResult.Applied).config, repo.activeConfig.value)
    }

    @Test
    fun `applyFetched rejects invalid config without touching the cache or activeConfig`(@TempDir tempDir: Path) {
        val cache = RawConfigCache(TestAppDirectories(tempDir))
        cache.write(ConfigFixtures.fullProtocol) // pre-existing good cache
        val repo = JsonConfigurationRepository(cache)
        repo.loadCached()

        val invalid = ConfigFixtures.questionnaireOnly.replace("\${taskIndex}", "")
        val result = repo.applyFetched(invalid)

        assertInstanceOf(ConfigApplyResult.Rejected::class.java, result)
        assertTrue((result as ConfigApplyResult.Rejected).errors.any { it is ConfigValidationError.MissingTaskIndexPlaceholder })
        assertEquals(ConfigFixtures.fullProtocol, cache.read(), "cache must be untouched by a rejected fetch")
        assertEquals(2, repo.activeConfig.value?.protocols?.size, "activeConfig must be untouched by a rejected fetch")
    }

    @Test
    fun `applyFetched rejects malformed json without touching the cache`(@TempDir tempDir: Path) {
        val cache = RawConfigCache(TestAppDirectories(tempDir))
        cache.write(ConfigFixtures.fullProtocol)
        val repo = JsonConfigurationRepository(cache)

        val result = repo.applyFetched("{ not valid json")

        assertInstanceOf(ConfigApplyResult.Malformed::class.java, result)
        assertEquals(ConfigFixtures.fullProtocol, cache.read())
    }

    @Test
    fun `applyFetched always replaces the cache on success, regardless of configVersion ordering`(@TempDir tempDir: Path) {
        val cache = RawConfigCache(TestAppDirectories(tempDir))
        val repo = JsonConfigurationRepository(cache)
        repo.applyFetched(ConfigFixtures.fullProtocol) // configVersion "2026-07-01.1"

        // An "older" configVersion string is still accepted and replaces the cache — the
        // server is authoritative (§6.1 pt 6), the app never compares configVersion itself.
        val olderVersion = ConfigFixtures.editorDisabled.replace(
            "\"configVersion\": \"2026-07-01.1-editor-disabled\"",
            "\"configVersion\": \"2020-01-01.0\"",
        )
        val result = repo.applyFetched(olderVersion)

        assertInstanceOf(ConfigApplyResult.Applied::class.java, result)
        assertEquals(olderVersion, cache.read())
        assertEquals("2020-01-01.0", repo.activeConfig.value?.configVersion)
    }

    @Test
    fun `fetch path leaves no partial cache file after a rejected apply`(@TempDir tempDir: Path) {
        val dirs = TestAppDirectories(tempDir)
        val cache = RawConfigCache(dirs)
        val repo = JsonConfigurationRepository(cache)

        repo.applyFetched("not json")

        assertNull(cache.read())
        assertTrue(!java.nio.file.Files.exists(dirs.configDir.resolve("config.json.tmp")))
    }

    @Test
    fun `an invalid showIf is logged as a warning and the config still applies`(@TempDir tempDir: Path) {
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.ALL
        try {
            val withBadRule = ConfigFixtures.questionnaireOnly.replaceFirst(
                "\"questionKey\"",
                "\"showIf\": { \"questionKey\": \"nope\", \"optionKeys\": [\"x\"] }, \"questionKey\"",
            )
            assertTrue(withBadRule != ConfigFixtures.questionnaireOnly, "fixture has no question to modify")
            val repo = JsonConfigurationRepository(RawConfigCache(TestAppDirectories(tempDir)))

            assertInstanceOf(ConfigApplyResult.Applied::class.java, repo.applyFetched(withBadRule))
            assertTrue(appender.list.any { it.level == Level.WARN && it.formattedMessage.contains("invalid showIf") })
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
        }
    }

    private fun applyWithShowIf(tempDir: Path, showIfJson: String?): Pair<ConfigApplyResult, List<String>> {
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.ALL
        try {
            val withRule = ConfigFixtures.questionnaireOnly.replaceFirst(
                "\"questionKey\"",
                "\"showIf\": $showIfJson, \"questionKey\"",
            )
            val repo = JsonConfigurationRepository(RawConfigCache(TestAppDirectories(tempDir)))
            val result = repo.applyFetched(withRule)
            return result to appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
        }
    }

    @Test
    fun `every malformed showIf shape still loads, warns, and shows the question`(@TempDir tempDir: Path) {
        val shapes = listOf(
            "{}",
            """{"optionKeys": ["x"]}""",
            """{"questionKey": "symptoms"}""",
            """{"questionKey": "symptoms", "optionKeys": null}""",
            """{"questionKey": null, "optionKeys": ["x"]}""",
            """{"questionKey": "symptoms", "optionKeys": "x"}""",
            """{"questionKey": "symptoms", "optionKeys": [1, 2]}""",
            """{"questionKey": 5, "optionKeys": ["x"]}""",
            "\"q1\"",
            "[]",
            "true",
        )
        for (shape in shapes) {
            val dir = java.nio.file.Files.createTempDirectory(tempDir, "m")
            val (result, warnings) = applyWithShowIf(dir, shape)
            assertInstanceOf(ConfigApplyResult.Applied::class.java, result, shape)
            assertTrue(warnings.any { it.contains("invalid showIf") }, "no warning for $shape")
            val task = (result as ConfigApplyResult.Applied).config.protocols.flatMap { it.tasks }
                .filterIsInstance<org.example.app.domain.config.QuestionnaireTask>().first()
            assertEquals(
                task.questions.map { it.questionKey },
                org.example.app.domain.config.displayedQuestions(task.questions).map { it.question.questionKey },
                shape,
            )
            assertEquals(1, org.example.app.domain.config.questionsWithInvalidRule(task.questions).size, shape)
        }
    }

    @Test
    fun `showIf null is no rule and logs no warning`(@TempDir tempDir: Path) {
        val (result, warnings) = applyWithShowIf(tempDir, "null")
        assertInstanceOf(ConfigApplyResult.Applied::class.java, result)
        assertTrue(warnings.none { it.contains("showIf") })
    }
}
