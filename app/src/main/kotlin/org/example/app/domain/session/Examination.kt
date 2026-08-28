package org.example.app.domain.session

import kotlinx.serialization.Serializable
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.video.VideoCaptureFormat

/** `processing.status` in `examination.json` (§8.8, §8.10). */
@Serializable
enum class ProcessingStatus { NotProcessed, Processing, Done, Failed }

/**
 * Which compacted timeline a processed session's clips were cut from (§8.7, §8.8): `edited`
 * only if the editor changed at least one boundary, `original` otherwise. Kept as plain
 * strings (matching [org.example.app.domain.timeline.TimelineEdited.basedOn]) rather than an
 * enum so the spec's literal `"original"`/`"edited"` values round-trip without a custom
 * serializer.
 */
object TimelineSource {
    const val ORIGINAL = "original"
    const val EDITED = "edited"
}

/** `examination.json.processing` (§8.10) — absent until `ProcessSessionUseCase` runs. */
@Serializable
data class ProcessingInfo(
    val status: ProcessingStatus,
    val processedAt: String? = null,
    val timelineUsed: String? = null,
)

/**
 * One `interruptions[]` entry (§8.5, §8.10): a device-loss gap. `sampleOffset` is the global
 * (cross-part) sample offset at which the gap occurred — the same value the
 * `RECORDING_INTERRUPTED`/`RECORDING_RESUMED` timeline events carry, per decision 20 (the gap
 * exists in wall-clock time, not in samples).
 *
 * `partFile` is the new master part's file name (`session_master.partN.wav`, §8.5) and
 * `captureFormat` is the format negotiated for the device that resumed into it (§13 decision
 * 28) — together these let `RecoverSessionsUseCase` reconstruct a crashed partial part's WAV
 * header with the *correct* format when a mid-session device swap changed it, instead of
 * assuming the session's top-level `captureFormat` applies to every part.
 */
@Serializable
data class Interruption(
    val sampleOffset: Long,
    val start: String,
    val end: String? = null,
    val oldDevice: String? = null,
    val newDevice: String? = null,
    val partFile: String,
    val captureFormat: CaptureFormat,
)

/**
 * One `tasks[]` entry in `examination.json` (§8.10): the outcome of one task instance.
 * `type`/`subtype` mirror the config's task discriminator/subtype (subtype is `null` for
 * non-VOCAL types). `takes` is the number of completed takes for this instance (§8.3);
 * `clipFile` is populated by `ProcessSessionUseCase`, `null` until then (or always `null` for
 * non-VOCAL / no-master sessions). `questionnaireAnswers` is `questionKey -> [answers]`,
 * populated for `QUESTIONNAIRE` instances only.
 */
@Serializable
data class TaskRecord(
    val taskIndex: Int,
    val type: String,
    val subtype: String? = null,
    val repetition: Int,
    val takes: Int = 0,
    val skipped: Boolean = false,
    val clipFile: String? = null,
    val questionnaireAnswers: Map<String, List<String>>? = null,
)

/**
 * One `videoTakes[]` entry in `examination.json`: a VIDEO capture that was opened for writing.
 *
 * Its reason to exist is [captureFormat]. A take is stored as a bare MJPEG elementary stream —
 * whole JPEGs back to back, no container — which carries no timestamps at all, so the rate it plays
 * back at is implicit in the file and recoverable from nothing else. Without this, a consumer of the
 * archive has to be told the frame rate out of band and guesses wrong the moment a camera opens at
 * anything other than the requested rate.
 *
 * Per take rather than per session, for the same reason [Interruption] carries its own
 * `captureFormat`: one session can hold several. Each take is its own file; the camera is released
 * when the last VIDEO screen closes and renegotiated from scratch on the next one; and a rung is
 * capped to the mode's own ceiling (`CameraMode.toFormat`), so two takes in one session can legally
 * differ.
 *
 * Written when the take opens, not when it closes, so a crash mid-take still leaves the rate on
 * record — which is exactly when nothing else can supply it. Entries therefore describe takes that
 * were *started*, including ones a later repeat rejected; the timeline says which take counts.
 */
@Serializable
data class VideoTakeRecord(
    /** Session-relative, `/`-separated, e.g. `video/task09_rep01_take01.mjpeg` — like `clipFile`. */
    val file: String,
    val taskIndex: Int,
    val repetition: Int,
    val take: Int,
    /** What the file *is*, which is not always what the camera advertised; see the class comment. */
    val captureFormat: VideoCaptureFormat,
)

/**
 * `examination.json` (§8.10): created at session start with `captureFormat`, `startedAt`,
 * `configVersion` populated; updated incrementally after every task completion via
 * [SessionRepository.writeExamination] (atomic tmp+rename, §8.10 write lifecycle) so a crash
 * never loses more than the current task's answers.
 *
 * `captureFormat` is `null` only for no-master (questionnaire/info-only) protocols (§6.2,
 * §8.8) — every session with at least one VOCAL task has it from creation, which is what
 * makes crash recovery (§8.4) able to reconstruct the WAV header unconditionally for
 * master-bearing sessions.
 */
@Serializable
data class Examination(
    val version: Int = 1,
    val sessionId: String,
    val installationId: String,
    val protocolName: String,
    val configVersion: String,
    val startedAt: String,
    val endedAt: String? = null,
    val captureFormat: CaptureFormat? = null,
    val recovered: Boolean = false,
    val interruptions: List<Interruption> = emptyList(),
    val tasks: List<TaskRecord> = emptyList(),
    /**
     * Empty for a protocol with no VIDEO task. Additive with a default, and `JsonSessionRepository`
     * reads with `ignoreUnknownKeys`, so this needs no [version] bump: sessions written before it
     * existed still decode, and one written now still decodes on a build without it.
     */
    val videoTakes: List<VideoTakeRecord> = emptyList(),
    val processing: ProcessingInfo? = null,
)
