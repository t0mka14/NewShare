package org.example.app.domain.config

/** §6.1 pt. 6, §11 (ConfigError: schema version unsupported / config validation failure). */
sealed interface ConfigValidationError {
    data class UnsupportedSchemaVersion(val schemaVersion: Int, val supportedRange: IntRange) : ConfigValidationError

    /** §3/§6.2: `recordingsFileName` must contain `${taskIndex}` for name uniqueness. */
    data class MissingTaskIndexPlaceholder(val protocolName: String) : ConfigValidationError

    /** Row 8: `recordingsFileName` references `${field.<name>}` but the protocol has no such field. */
    data class UnknownTemplateField(val protocolName: String, val fieldName: String) : ConfigValidationError

    /** §6.2/§13 decision 44: `defaultMicGain` is a percentage, 0..100. */
    data class InvalidDefaultMicGain(val value: Int) : ConfigValidationError
}

data class ConfigValidationResult(
    val errors: List<ConfigValidationError>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object ConfigValidator {
    /**
     * Schema versions this app release understands (§6.1 pt. 6). A server response outside
     * this range is rejected; a cached config below it is a candidate for migration
     * elsewhere, not here.
     */
    val SUPPORTED_SCHEMA_VERSIONS: IntRange = 1..1

    private const val TASK_INDEX_PLACEHOLDER = "\${taskIndex}"
    val MIC_GAIN_RANGE: IntRange = 0..100

    fun validate(config: RemoteConfig): ConfigValidationResult {
        val errors = mutableListOf<ConfigValidationError>()

        if (config.schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) {
            errors += ConfigValidationError.UnsupportedSchemaVersion(config.schemaVersion, SUPPORTED_SCHEMA_VERSIONS)
        }

        config.defaultMicGain?.let { gain ->
            if (gain !in MIC_GAIN_RANGE) errors += ConfigValidationError.InvalidDefaultMicGain(gain)
        }

        for (protocol in config.protocols) {
            if (!protocol.recordingsFileName.contains(TASK_INDEX_PLACEHOLDER)) {
                errors += ConfigValidationError.MissingTaskIndexPlaceholder(protocol.name)
            }
            val fieldNames = protocol.patientFields.mapTo(mutableSetOf()) { it.name }
            RecordingsFileNameRenderer.FIELD_VARIABLE.findAll(protocol.recordingsFileName)
                .map { it.groupValues[1] }
                .filterNot { it in fieldNames }
                .distinct()
                .forEach { errors += ConfigValidationError.UnknownTemplateField(protocol.name, it) }
        }

        return ConfigValidationResult(errors)
    }
}
