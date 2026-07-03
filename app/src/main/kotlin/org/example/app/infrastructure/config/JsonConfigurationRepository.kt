package org.example.app.infrastructure.config

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.example.app.domain.config.ConfigApplyResult
import org.example.app.domain.config.ConfigDecoder
import org.example.app.domain.config.ConfigSchemaMigrator
import org.example.app.domain.config.ConfigValidationError
import org.example.app.domain.config.ConfigValidator
import org.example.app.domain.config.ConfigurationRepository
import org.example.app.domain.config.RemoteConfig

private val logger = KotlinLogging.logger {}

/**
 * Production [ConfigurationRepository]: composes [RawConfigCache] + `ConfigDecoder` +
 * `ConfigValidator` + [ConfigSchemaMigrator] per §5.4/§6.1.
 *
 * Decoder warnings and validation/migration failures are logged here — never participant data
 * or the installation ID (§11); config JSON contains neither, only clinic-authored strings and
 * task structure, so it is safe to log validation error *shapes* (not raw config content).
 */
class JsonConfigurationRepository(
    private val cache: RawConfigCache,
    private val migrator: ConfigSchemaMigrator = ConfigSchemaMigrator.DEFAULT,
    private val supportedSchemaVersions: IntRange = ConfigValidator.SUPPORTED_SCHEMA_VERSIONS,
) : ConfigurationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val _activeConfig = MutableStateFlow<RemoteConfig?>(null)
    override val activeConfig: StateFlow<RemoteConfig?> = _activeConfig.asStateFlow()

    override fun loadCached(): RemoteConfig? {
        val raw = cache.read() ?: return null
        val migratedJson = migrateIfBelowRange(raw) ?: run {
            logger.warn { "cached config schema version is below the supported range and could not be migrated; treating as no cache (§6.1 pt 6)" }
            return null
        }

        val config = decodeAndValidate(migratedJson, source = "cached") ?: return null
        _activeConfig.value = config
        return config
    }

    override fun applyFetched(json: String): ConfigApplyResult {
        val decodeResult = try {
            ConfigDecoder.decode(json)
        } catch (e: SerializationException) {
            logger.warn { "fetched config failed to decode: ${e::class.simpleName}" }
            return ConfigApplyResult.Malformed(e.message ?: "malformed config JSON")
        } catch (e: ClassCastException) {
            // ConfigDecoder casts the root to JsonObject; a top-level JSON array/scalar throws this.
            logger.warn { "fetched config was not a JSON object: ${e::class.simpleName}" }
            return ConfigApplyResult.Malformed(e.message ?: "config JSON root must be an object")
        }
        logDecoderWarnings(decodeResult.warnings)

        val validation = ConfigValidator.validate(decodeResult.config)
        if (!validation.isValid) {
            logger.warn { "fetched config failed validation: ${validation.errors.describe()}" }
            return ConfigApplyResult.Rejected(validation.errors)
        }

        // Server response always replaces the cache regardless of configVersion ordering —
        // the server is authoritative (§6.1 pt 6).
        cache.write(json)
        _activeConfig.value = decodeResult.config
        return ConfigApplyResult.Applied(decodeResult.config)
    }

    /** Returns [raw] unchanged if its schemaVersion is already >= the supported floor (an
     * out-of-range-above or in-range version is left for [decodeAndValidate]/[ConfigValidator]
     * to judge); migrates it otherwise; `null` if migration is required but not possible. */
    private fun migrateIfBelowRange(raw: String): String? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return null
        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: return null

        if (schemaVersion >= supportedSchemaVersions.first) return raw

        val migrated = migrator.migrate(root, supportedSchemaVersions) ?: return null
        return json.encodeToString(JsonObject.serializer(), migrated)
    }

    private fun decodeAndValidate(rawJson: String, source: String): RemoteConfig? {
        val decodeResult = try {
            ConfigDecoder.decode(rawJson)
        } catch (e: SerializationException) {
            logger.warn { "$source config failed to decode; treating as no cache: ${e::class.simpleName}" }
            return null
        } catch (e: ClassCastException) {
            logger.warn { "$source config was not a JSON object; treating as no cache: ${e::class.simpleName}" }
            return null
        }
        logDecoderWarnings(decodeResult.warnings)

        val validation = ConfigValidator.validate(decodeResult.config)
        if (!validation.isValid) {
            logger.warn { "$source config failed validation; treating as no cache: ${validation.errors.describe()}" }
            return null
        }
        return decodeResult.config
    }

    private fun logDecoderWarnings(warnings: List<String>) {
        warnings.forEach { logger.warn { it } }
    }

    private fun List<ConfigValidationError>.describe(): String = joinToString { it::class.simpleName ?: "?" }
}
