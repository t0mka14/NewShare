# Spec: Enhanced editor — cut out middle segments

## Context
During free speech the examiner sometimes has to ask the participant a supporting question. Today the editor can only trim the start and end of a clip (one `[startSample, stopSample)` range per VOCAL instance), so the question stays in the exported clip. The goal is to let the examiner select one or more stretches inside the clip and remove them, so the exported clip is shorter (spliced, not silenced). The feature sits behind a new local setting, "Enhanced editor", so the default editor is unchanged.

## Feasibility and complexity
**Feasible: medium complexity, about 6 small increments.** The audio work itself is easy. The cost comes from the "one contiguous range" assumption running through every layer.

| Layer | Today | Change | Size |
|---|---|---|---|
| Edit model `domain/timeline/TimelineEdited.kt` | `TimelineEditedSegment(taskIndex, repetition, startSample, stopSample)` | Add `cuts: List<SampleRange> = emptyList()` (global samples, stop exclusive). Defaults are not encoded, so old files and old readers are unaffected and `version` stays 1. | S |
| Validator `TimelineBoundaryValidator` | Checks inverted, out of range and overlapping neighbours | Add `validateCuts`: each cut lies strictly inside `[start, stop)`, cuts are sorted and don't overlap, and at least 0.5 s of audio is kept. A boundary drag may not move into a cut. | S |
| Export `ClipExportPlanner` + `ProcessSessionUseCase` | Single `localRange`; `cutClip(src, start, stop)` | The plan carries `keepRanges: List<LongRange>` (the segment minus its cuts, in local samples). `audioDurationMs` becomes the sum of the kept ranges. | M |
| Cutter `JvmAudioClipService` | One `readFrameRange` → convert → `writeWav` | New `cutClip(src, keepRanges, fmt, out)`: read and convert each range, concatenate raw (no fade; audio stays bit-exact), write atomically. | S |
| Zero-crossing snap (new) | — | `AudioClipService.nearestZeroCrossing(wav, sample, maxRadius)`: scans ±5 ms of the master part (mono mix if stereo) for a sign change and returns the closest sample, or the original sample if there is none. The editor applies it when a cut is created, so the stored cut, the preview and the export all use the same snapped points (automatic, like Audacity's "At Zero Crossings"). | S |
| Playback `AudioPlaybackService` | `playRange(file, start, stop)` | Add `playRanges(file, ranges)`: one line, frames written in sequence, and the reported position mapped back to source samples so the playhead jumps over cuts. | M |
| Editor component `navigation/EditorComponent.kt` | Segment has start/stop plus initial/opened copies | Segment gets `cuts`, `openedCuts`. State gets `selection`, `zoom`, `enhanced`. New actions: select, cut, restore cut, zoom, play selection. Reset clears cuts. "Edited" now means moved or cut. `needsReprocess` picks up cuts automatically through data-class equality. | M |
| Editor UI `ui/EditorContent.kt` | Fixed view (clip ± 5 s, 400 bars), handles, tap to seek | Drag to select, shaded cut overlays, a toolbar, zoom with a scrollbar, and duration info. | L |
| Settings `AppSettings`, `SettingsComponent`, `SettingsContent` | No boolean toggles yet | `enhancedEditor: Boolean? = null` (null means off). A new "Editor" section with a Switch, persisted with the `indicatorType` pattern. | S |
| Docs | `Project_Specification.md` §8.7/§8.8, `docs/dev/upload_data.md` §5.5 | Document the `cuts` field, the zero-crossing splice, and a new §13 decision. | S |

**Risks / notes**
- **Video:** the video is still exported whole (your choice: audio only). A cut makes the audio shorter than the video, and the existing `videoDurationMs`/`audioDurationMs` fields expose the difference. The editor shows an info note: "Video is not cut — audio will be shorter than video."
- **Clicks:** both edges of every cut are snapped to zero crossings, so joins are normally click-free. A join can still click if a cut lands inside loud noise with no crossing within ±5 ms. Cutting in the pauses around a question avoids this.
- **Resampling:** each kept piece is converted separately, so a join can differ by about 1 sample from a cut of the whole range. This can't be heard.
- **Turning the setting off later:** cuts already saved are still applied and still shown, and each can still be restored. Only creating new cuts and zoom are hidden.
- **Server:** the `cuts` field is additive, so old server code ignores it. Nothing in the manifest or upload contract depends on clip length.

## Proposed UI (enhanced mode)

```
┌ Task 3 · Free speech · rep 1/1 ──────────────────────── [◀ Prev] [Next ▶] ┐
│ Zoom: [−] [+] [Fit]                         Selection: 1:02.4 – 1:09.8 (7.4 s)│
│ ┌──────────────────────────────────────────────────────────────────────────┐ │
│ │░░│▁▃▅▇▅▃▁▃▅▇█▇▅▃▁│▓▓▓▓▓▓▓▓▓│▁▃▅▇▅▃▁▃▅│████████│▃▅▇█▇▅▃▁▃▅▇▅▃▁│░░        │ │
│ │  ▲start          │ cut −8.1s ⊗│        │selection│               ▲stop    │ │
│ └──────────────────────────────────────────────────────────────────────────┘ │
│ ◄═══════════════[■■■■■■■■■]══════════════════════════════════════►  scrollbar│
│ 0:00      0:30      1:00      1:30      2:00                    time ruler  │
│                                                                              │
│ [▶ Play]  [▶ Play selection]  [✂ Cut selection  Del]  [Clear  Esc]          │
│ Duration 1:46.9  (original 2:02.4, 2 cuts −15.5 s)                           │
│ ⓘ Video is not cut — audio will be shorter than video.   (video tasks only)  │
│                                              [Reset] [Discard] [Accept]      │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Interactions**
- **Select:** press and drag on the waveform between the start and stop handles to create a selection, shown as an accent-tinted band with two small edge handles you can drag to refine it. A plain click still seeks, as today. A selection is clamped to the clip and can't overlap an existing cut.
- **Cut:** the "Cut selection" button or the Del key turns the selection into a cut region. A cut is drawn in place as a dark hatched band labelled "cut −8.1 s" with a ⊗ restore button. It stays visible, so the context remains and the cut can be undone.
- **Play:** "Play" plays the clip as it will be exported, skipping cuts; the playhead jumps over the hatched bands. "Play selection" plays only the selection, to check what is about to be removed.
- **Zoom:** −/+ buttons and Ctrl+mouse wheel (centred on the cursor) zoom from "Fit" (the whole clip ± 5 s) down to about 2 s across the view. A horizontal scrollbar pans the view, and peaks are fetched for the visible window only (`WaveformService.peaks` already supports this).
- **Duration line:** shows the kept duration, the original duration and the total removed.
- **Reset:** clears the cuts as well as the boundaries. Accept writes `timeline_edited.json` if any segment was moved or cut.
- **Default mode (setting off):** looks exactly as it does today.
- **Settings:** a new "Editor" section with the switch "Enhanced editor (cut out parts of a recording, zoom)" and a one-line explanation. EN and CS strings go in `BuiltinStrings.kt`.

## Spec

```
Goal:        Let examiners remove interjections from the middle of a recording so the exported clip contains only participant speech.
Deliverable: Enhanced-editor setting + cut/zoom editor + spliced export + docs, on branch old_ui.
Acceptance criteria:
  1. Setting off (default): editor UI and behaviour unchanged; existing editor tests pass unmodified.
  2. Setting persists in config/settings.json as "enhancedEditor"; an old settings file without the field reads as off.
  3. With the setting on, the user can select a range, cut it, see it hatched with its length, and restore it with ⊗; multiple non-overlapping cuts per clip are possible.
  4. Exported clip length == (stop − start − Σcut lengths) at the target rate (±1 sample per join); when no conversion is needed, the clip is bit-identical to the source's kept ranges.
  4b. Cut edges stored in timeline_edited.json lie on zero crossings (within ±5 ms of the selection) whenever one exists.
  5. timeline_edited.json contains "cuts":[{startSample,stopSample}] (global samples) only when non-empty; files without cuts are byte-identical to today.
  6. examination.json audioDurationMs reflects the shortened length for video tasks; video unchanged.
  7. Preview playback skips cuts; the playhead never lands inside a cut.
  8. Reset clears cuts; Discard drops them; re-editing after Accept triggers reprocess (needsReprocess true).
  9. Invalid operations are rejected: cut outside the clip, overlapping cuts, keeping < 0.5 s, a boundary dragged into a cut.
Verification plan:
  - Unit: TimelineBoundaryValidatorTest (cuts), JsonTimelineRepositoryTest (round-trip, omitted when empty),
    ClipExportPlannerTest (keepRanges, part mapping), JvmAudioClipServiceTest (real WAV: length, bit-exact samples, zero-crossing snap incl. no-crossing fallback),
    ProcessSessionUseCaseTest (duration), JvmAudioPlaybackService position mapping, SettingsComponentTest + repo test.
  - Component: EditorComponentTest (select/cut/restore/reset/accept/needsReprocess, setting off hides tools).
  - UI: EditorLayoutUiTest cases with new testTags; EditorScenarioTest 4c "cut a middle segment → processed clip shorter".
  - Real app: hot-reload MCP screenshots of settings toggle, selection, cut overlay, zoom; listen-check one exported clip.
  - Final: /verify with an independent critic; Sonnet E2E re-test per the E2E workflow.
Assumptions:
  - The setting is local (AppSettings), off by default, and only matters when remote config enableEditor is true.
  - Cuts live inside one master part (the planner already skips segments that cross a part boundary).
  - Joins are raw splices at zero-crossing-snapped points (your choice; no fade).
  - Undo/redo is out of scope; ⊗ restore and Reset cover reversal.
Key decisions:
  - Cuts are shown in place (hatched), not collapsed out of the view: easier to reverse and keeps context. (Rejected: compacted view.)
  - Additive `cuts` field, version stays 1. (Rejected: version 2 / a separate file.)
  - Video audio-only mismatch accepted (your choice).
Increments:
  1. Domain: cuts field, validator, repository round-trip, docs §5.5 → checkpoint: tests green + JSON sample.
  2. Export: planner keepRanges, spliced cutClip, nearestZeroCrossing, duration → checkpoint: test WAVs + a real exported clip.
  3. Setting: AppSettings field, Settings section/switch → checkpoint: screenshot.
  4. Editor component: selection, cuts, playRanges, reset/accept → checkpoint: component tests.
  5. Editor UI: selection gesture, overlays, toolbar, zoom/scrollbar → checkpoint: hot-reload screenshots for your review.
  6. Scenario test, Project_Specification §8.7/§8.8/§13, E2E re-test → checkpoint: /verify report.
Out of scope: cutting video, undo/redo history, cuts across master-part boundaries, fades/crossfades, server-side changes.
```

## Critical files
- `app/src/main/kotlin/org/example/app/domain/timeline/TimelineEdited.kt`, `TimelineBoundaryValidator.kt`
- `.../domain/session/ClipExportPlanner.kt`, `ProcessSessionUseCase.kt` (lines ~177–218)
- `.../domain/audio/AudioClipService.kt`, `AudioPlaybackService.kt`; `.../infrastructure/audio/JvmAudioClipService.kt`, `JvmAudioPlaybackService.kt`
- `.../navigation/EditorComponent.kt`, `.../ui/EditorContent.kt`, `.../ui/TestTags.kt`, `.../domain/localization/BuiltinStrings.kt`
- `.../domain/settings/AppSettings.kt`, `.../navigation/SettingsComponent.kt`, `.../ui/SettingsContent.kt`, `.../navigation/RootComponent.kt` (passes the flag into DefaultEditorComponent)
- Test fakes: `FakeAudioClipService`, `FakeAudioPlaybackService` (new call types)
- Docs: `docs/Project_Specification.md` §8.7/§8.8/§13, `docs/dev/upload_data.md` §5.5

Implementation follows the existing workflow: specialist agents (domain-engineer, audio-engineer, ui-engineer) per increment, each reviewed and tested before the next.

## Status
All increments are done and verified (full test suite green, real-app check, independent review). After review the editor's Accept button was renamed Finish (CS "Dokončit"), and both editor modes use the same two-row button layout: Play (plus the selection tools in enhanced mode) on top, then Previous, Next, Reset, Discard, Finish spread evenly across the full width.

Implementation decisions that refine or deviate from the spec above:
- **Cuts may not touch the clip edges.** A cut must lie strictly inside `[start, stop)` (`start < cut.start < cut.stop < stop`); a selection that reaches an edge is rejected with a hint to move the boundary marker instead. Touching or overlapping cuts are merged when created.
- **Reset** returns the segment to the boundaries it had when the editor opened and clears all its cuts.
- **Selection anchor semantics.** `onSelect(anchor, moving)`: the anchor is the drag origin and stays fixed; the moving end is clamped to the clip and stops at the nearest cut in the drag direction. An anchor inside an existing cut is ignored.
- **The restore button** is a small circular x on the hatched band (not the larger symbol sketched above).
- **Delete and Esc** act only while a selection exists (cut / clear it).
- **Cut rejection reasons** are reported in editor state (`lastCutRejected`): empty selection, outside the segment (including edge-touching), kept audio under 0.5 s.
- **Setting `enhancedEditor`** is a nullable Boolean in `settings.json` (null or absent = off), read once when the editor is created.
