package org.example.app.domain.config

/** §6.1 pt. 6, §11 (ConfigError: schema version unsupported / config validation failure). */
sealed interface ConfigValidationError {
    data class UnsupportedSchemaVersion(val schemaVersion: Int, val supportedRange: IntRange) : ConfigValidationError

    /** §3/§6.2: `recordingsFileName` must contain `${taskIndex}` for name uniqueness. */
    data class MissingTaskIndexPlaceholder(val protocolName: String) : ConfigValidationError

    /** §3/§6.2: a VOCAL task exists without a preceding CALIBRATION task in the same protocol. */
    data class MissingCalibrationBeforeVocal(val protocolName: String) : ConfigValidationError
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

    fun validate(config: RemoteConfig): ConfigValidationResult {
        val errors = mutableListOf<ConfigValidationError>()

        if (config.schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) {
            errors += ConfigValidationError.UnsupportedSchemaVersion(config.schemaVersion, SUPPORTED_SCHEMA_VERSIONS)
        }

        for (protocol in config.protocols) {
            if (!protocol.recordingsFileName.contains(TASK_INDEX_PLACEHOLDER)) {
                errors += ConfigValidationError.MissingTaskIndexPlaceholder(protocol.name)
            }

            val firstVocalIndex = protocol.tasks.indexOfFirst { it is VocalTask }
            if (firstVocalIndex >= 0) {
                val firstCalibrationIndex = protocol.tasks.indexOfFirst { it is CalibrationTask }
                if (firstCalibrationIndex < 0 || firstCalibrationIndex > firstVocalIndex) {
                    errors += ConfigValidationError.MissingCalibrationBeforeVocal(protocol.name)
                }
            }
        }

        return ConfigValidationResult(errors)
    }
}
