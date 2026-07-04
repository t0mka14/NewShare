package org.example.app.domain.session

import org.example.app.domain.config.RecordingsFileNameRenderer
import org.example.app.domain.timeline.TakeSelector
import org.example.app.domain.timeline.TimelineEditedSegment
import org.example.app.domain.timeline.TimelineEvent

/**
 * One clip to cut from a master part into `clips/` (§8.8): the last take of one VOCAL task
 * instance, already resolved to (part file, local sample range, output file name).
 */
data class ClipExportPlan(
    val taskIndex: Int,
    val repetition: Int,
    val subtype: String,
    val sourcePart: MasterPart,
    /** `[start, stop)` frame offsets local to [sourcePart]'s own file. */
    val localRange: LongRange,
    /** File name only (no `clips/` prefix), e.g. `"install1_HC001_3_PHONATION_1.wav"`. */
    val clipFileName: String,
)

/** Why a VOCAL task instance could not be planned into a [ClipExportPlan] — always fatal to
 * processing (§8.8: clinical data is never silently dropped or guessed at). */
sealed interface ClipPlanningError {
    data class MissingSubtype(val taskIndex: Int, val repetition: Int) : ClipPlanningError

    /** `timeline_edited.json` exists but has no segment for this instance — the editor
     * contract (§8.7) requires a segment for every VOCAL instance once the file is written at
     * all, so this indicates a producer bug, not normal data. */
    data class MissingEditedSegment(val taskIndex: Int, val repetition: Int) : ClipPlanningError

    /** No last-take `[start, stop)` range could be derived from `timeline_original.json`
     * (§8.3) for a non-skipped VOCAL instance recorded with at least one take. */
    data class MissingTakeRange(val taskIndex: Int, val repetition: Int) : ClipPlanningError

    /** The resolved sample range does not fit entirely inside one master part (§8.5) — should
     * never happen since a take is always auto-rejected before an interruption gap. */
    data class SpansPartBoundary(val taskIndex: Int, val repetition: Int, val startSample: Long, val stopSample: Long) : ClipPlanningError
}

data class ClipPlanningResult(val plans: List<ClipExportPlan>, val errors: List<ClipPlanningError>)

/**
 * Pure computation of "which last-take ranges get cut into which clip files" (§8.3, §8.8) —
 * no I/O, no repositories, so it is unit-testable with plain in-memory data. [ProcessSessionUseCase]
 * is the only caller; it supplies already-loaded timeline/examination data and the resolved
 * [MasterPart] list ([MasterPartMap]).
 */
object ClipExportPlanner {
    /**
     * @param vocalTaskRecords the session's `examination.tasks[]` entries already filtered to
     *   `type == "VOCAL" && !skipped` — every other task type or a skipped instance produces no
     *   clip and is not passed in.
     * @param editedSegments non-null (even if empty) iff `timeline_edited.json` exists — its
     *   presence, not its content, is what selects the edited timeline over the original one
     *   (§8.7 decision 13); `null` means "use `timeline_original.json`" for every instance.
     */
    fun plan(
        vocalTaskRecords: List<TaskRecord>,
        originalEvents: List<TimelineEvent>,
        editedSegments: List<TimelineEditedSegment>?,
        masterParts: List<MasterPart>,
        recordingsFileNameTemplate: String,
        installationId: String,
        patientCode: String,
    ): ClipPlanningResult {
        val plans = mutableListOf<ClipExportPlan>()
        val errors = mutableListOf<ClipPlanningError>()

        for (record in vocalTaskRecords) {
            val subtype = record.subtype
            if (subtype == null) {
                errors += ClipPlanningError.MissingSubtype(record.taskIndex, record.repetition)
                continue
            }

            val range: LongRange = if (editedSegments != null) {
                val segment = editedSegments.firstOrNull {
                    it.taskIndex == record.taskIndex && it.repetition == record.repetition
                }
                if (segment == null) {
                    errors += ClipPlanningError.MissingEditedSegment(record.taskIndex, record.repetition)
                    continue
                }
                segment.startSample until segment.stopSample
            } else {
                val lastTake = TakeSelector.lastTake(originalEvents, record.taskIndex, record.repetition)
                val sampleRange = lastTake?.let {
                    TakeSelector.takeSampleRange(originalEvents, record.taskIndex, record.repetition, it)
                }
                if (sampleRange == null) {
                    errors += ClipPlanningError.MissingTakeRange(record.taskIndex, record.repetition)
                    continue
                }
                sampleRange
            }

            val resolved = MasterPartMap.resolve(masterParts, range.first, range.last + 1)
            if (resolved == null) {
                errors += ClipPlanningError.SpansPartBoundary(record.taskIndex, record.repetition, range.first, range.last + 1)
                continue
            }
            val (part, localRange) = resolved

            val clipBaseName = RecordingsFileNameRenderer.render(
                template = recordingsFileNameTemplate,
                installationId = installationId,
                patientCode = patientCode,
                taskIndex = record.taskIndex,
                subtype = subtype,
                repetition = record.repetition,
            )
            plans += ClipExportPlan(
                taskIndex = record.taskIndex,
                repetition = record.repetition,
                subtype = subtype,
                sourcePart = part,
                localRange = localRange,
                clipFileName = "$clipBaseName.wav",
            )
        }

        return ClipPlanningResult(plans.sortedBy { it.taskIndex }, errors)
    }
}
