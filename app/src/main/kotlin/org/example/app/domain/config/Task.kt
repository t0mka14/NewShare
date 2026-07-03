package org.example.app.domain.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed task hierarchy, polymorphic on the `type` discriminator (§4 of
 * Task_Configuration_JSON_Spec.md, §6.2 of the project spec).
 *
 * Task types in scope: VOCAL, QUESTIONNAIRE, CALIBRATION, INFO. VIDEO is reserved for a
 * later phase — it must still decode successfully (no crash) so the app can skip it with a
 * logged warning; it is represented here as [VideoTask] so config parsing never throws on a
 * config that legitimately contains VIDEO tasks.
 *
 * The legacy `QUESTIONAIRE` (misspelled) discriminator value is accepted as an alias for
 * `QUESTIONNAIRE`; see [ConfigDecoder] which normalizes it before polymorphic decoding
 * (kotlinx.serialization's built-in sealed-class dispatch only matches exact discriminator
 * values, so the alias is handled as a JSON-tree preprocessing step, not here).
 *
 * Fields common to all task types (§4): `type`, `titleKey`, `canRepeat`, `canSkip`,
 * `nrepetition`. CALIBRATION and INFO examples in the spec omit `canRepeat`/`canSkip`/
 * `nrepetition`, so they default to `false`/`false`/`1` respectively.
 */
@Serializable
sealed interface Task {
    val titleKey: String
    val canRepeat: Boolean
    val canSkip: Boolean

    /** Number of repetitions; the task expands into this many task instances (§8.3). */
    val nrepetition: Int
}

/** `subtype` values for [VocalTask] (§4.1). */
@Serializable
enum class VocalSubtype {
    PHONATION, PATAKA, SYLLABLES, READING, MONOLOGUE, RETELLING, COUNTING, CUSTOM
}

@Serializable
@SerialName("VOCAL")
data class VocalTask(
    override val titleKey: String,
    val subtype: VocalSubtype,
    val instructionKeys: List<String> = emptyList(),
    /** Target duration in seconds. */
    val length: Int = 0,
    val showIndicator: Boolean = true,
    override val canRepeat: Boolean = false,
    override val canSkip: Boolean = false,
    /** Optional example audio, played on demand, never recorded. */
    val audioExamplePath: String? = null,
    override val nrepetition: Int = 1,
) : Task

@Serializable
@SerialName("QUESTIONNAIRE")
data class QuestionnaireTask(
    override val titleKey: String,
    val questions: List<Question> = emptyList(),
    val length: Int = 0,
    override val canRepeat: Boolean = false,
    override val canSkip: Boolean = false,
    override val nrepetition: Int = 1,
) : Task

@Serializable
@SerialName("CALIBRATION")
data class CalibrationTask(
    override val titleKey: String,
    val instructionKeys: List<String> = emptyList(),
    /** `[min, max]` linear RMS normalized to full scale (0.0-1.0), §4.3. */
    val optimalLoudness: List<Double>,
    override val canRepeat: Boolean = false,
    override val canSkip: Boolean = false,
    override val nrepetition: Int = 1,
) : Task {
    val minLoudness: Double get() = optimalLoudness.getOrElse(0) { 0.0 }
    val maxLoudness: Double get() = optimalLoudness.getOrElse(1) { 1.0 }
}

@Serializable
@SerialName("INFO")
data class InfoTask(
    override val titleKey: String,
    val instructionKeys: List<String> = emptyList(),
    override val canRepeat: Boolean = false,
    override val canSkip: Boolean = false,
    override val nrepetition: Int = 1,
) : Task

/**
 * Reserved for a later phase (§2 non-goals, §4.5). Must decode without crashing; the app
 * skips these tasks with a logged warning. Kept out of [org.example.app.domain.timeline.TaskInstanceExpander]'s
 * navigable output.
 */
@Serializable
@SerialName("VIDEO")
data class VideoTask(
    override val titleKey: String,
    val subtype: String? = null,
    val instructionKeys: List<String> = emptyList(),
    val length: Int = 0,
    override val canRepeat: Boolean = false,
    override val canSkip: Boolean = false,
    override val nrepetition: Int = 1,
    val havePTZ: Boolean = false,
) : Task
