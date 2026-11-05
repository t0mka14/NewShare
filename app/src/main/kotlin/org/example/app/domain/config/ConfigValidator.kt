package org.example.app.domain.config

/** §6.1 pt. 6, §11 (ConfigError: schema version unsupported / config validation failure). */
sealed interface ConfigValidationError {
    data class UnsupportedSchemaVersion(val schemaVersion: Int, val supportedRange: IntRange) : ConfigValidationError

    /** §3/§6.2: `recordingsFileName` must contain `${taskIndex}` for name uniqueness. */
    data class MissingTaskIndexPlaceholder(val protocolName: String) : ConfigValidationError

    /** Row 8: `recordingsFileName` references `${field.<name>}` but the protocol has no such field. */
    data class UnknownTemplateField(val protocolName: String, val fieldName: String) : ConfigValidationError

    /** `recordingsFileName` contains a `${...}` that is neither a supported variable nor `${field.<name>}`. */
    data class UnknownTemplateVariable(val protocolName: String, val variable: String) : ConfigValidationError

    /** §6.2/§13 decision 44: `defaultMicGain` is a percentage, 0..100. */
    data class InvalidDefaultMicGain(val value: Int) : ConfigValidationError
}

/**
 * Findings that do not reject the config (brief §3: a bad rule must not cost the whole config).
 * Logged by the repository; never part of [ConfigValidationResult.errors].
 */
sealed interface ConfigValidationWarning {
    /** A `showIf` that does not name an earlier choice question / non-empty subset of its options; the question is always shown. */
    data class InvalidShowIf(val protocolName: String, val taskTitleKey: String, val questionKey: String) : ConfigValidationWarning
}

data class ConfigValidationResult(
    val errors: List<ConfigValidationError>,
    val warnings: List<ConfigValidationWarning> = emptyList(),
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
        val warnings = mutableListOf<ConfigValidationWarning>()

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
            RecordingsFileNameRenderer.ANY_VARIABLE.findAll(protocol.recordingsFileName)
                .map { it.groupValues[1] }
                .filterNot { it in RecordingsFileNameRenderer.PLAIN_VARIABLES || it.startsWith("field.") }
                .distinct()
                .forEach { errors += ConfigValidationError.UnknownTemplateVariable(protocol.name, it) }
            for (task in protocol.tasks.filterIsInstance<QuestionnaireTask>()) {
                questionsWithInvalidRule(task.questions).forEach {
                    warnings += ConfigValidationWarning.InvalidShowIf(protocol.name, task.titleKey, it.questionKey)
                }
            }
        }

        return ConfigValidationResult(errors, warnings)
    }
}
