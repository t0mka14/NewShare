# Run 04: Speech session, editing and a second patient

- Date / time: 2026-10-24 00:12–00:19 local   Runner: Sonnet   App commit: `59bb063`
- Preconditions met: yes. Run 03 passed; the window was resized to 1000×820.
- Result: passed with findings

The orchestrator saved this report from the runner's returned text and renumbered the
findings. F-029 was added by the orchestrator from a runner note, after checking the spec.

Session: `E2E1023-04-1`, ID `fb60e12fb4124064b8ba83c3543ee003`.

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1a | Start the speech protocol for `E2E1023-04-1` | pass | Same as run 03. Calibration below the band (F-021). |
| 1b | Phonation rep 1 and rep 2, one take each | pass | `step-1-phonation2-recording.png`. One capture only, so F-024 is still unconfirmed. |
| 1c | PATAKA, "repeat twice" (read as two retries: 3 takes, last kept) | pass | Timeline: take 1 and take 2 rejected (EXAMINER_REPEAT), take 3 kept. Each Retry started the next take at once (F-023). |
| 1d | Reading, one take | pass | 3.7 s |
| 1e | Monologue recorded, not skipped 📷 | pass | About 21 s, one take. `step-1-monologue-recorded.png` |
| 1f | Questionnaire (Q1, Q3) | pass | Q2 and Q4 stored as empty lists. |
| 2a | Editor: 5 segments | pass | PHONATION, PHONATION, PATAKA, READING, MONOLOGUE (F-025). |
| 2b | Move the PATAKA boundary 📷 before and after | pass with finding | The segment is take 3. Start 0:42.1 → **0:43.5**, end 0:45.7, length 3.6 s → **2.1 s**. Real mouse drag with `Drag-InWindow.ps1`. The edit survived Next then Previous. F-028. `step-2-before-drag.png`, `step-2-after-drag-start.png` |
| 2c | Finish: edited timeline written | pass | `timeline_edited.json` holds all 5 segments. Only PATAKA differs from the original: startSample 2020800 → 2092751, stop 2193600 unchanged. `step-2-after-accept.png` |
| 2d | ZIP contains the edit | pass | Entries: master, 5 clips, `examination.json`, `participant.json`, `task_configuration_snapshot.json`, `timeline_edited.json`, `timeline_original.json`, `manifest.json`. 11 manifest hashes, all match. `processing.timelineUsed` = `edited`. PATAKA `takes` = 3. |
| 2e | The PATAKA clip uses the edited range | pass | 2.101 s = 100,849 samples = 2193600 − 2092751. The take was 3.6 s. The other clips match their original take ranges. |
| 3a | Sessions list 📷 | pass | Newest first. "E2E1023-04-1 — 24. 10. 2026 00:14" and "E2E1023-03-1 — 24. 10. 2026 00:07", both Processed and Not uploaded. `step-3-sessions-list.png` |
| 3b | Reopen run 03 in the editor, close without changes (§10.3 scenario 4) | pass with finding | There is no way out except Accept on the last segment (F-027). After an untouched Next…Accept, everything is unchanged: ZIP SHA-256 `0a1cdd07…febe5`, ZIP mtime, `examination.json`, `processedAt`, no `timeline_edited.json`, and the same file list, sizes and mtimes. This Accept returns to Sessions, not "Examination complete". `step-3-run03-editor-opened.png`, `step-3-after-accept-untouched.png` |
| 4a | Reprocess run 04 from Sessions | pass | No visible feedback (F-026). `step-4-after-reprocess-click.png` |
| 4b | ZIP regenerated with the same session ID | pass with finding | Within about 1 s, the clips, `examination.json`, the ZIP and `upload_status.json` were rewritten. Same session ID and folder, no extra session. The edited timeline is still used, and the clips are byte-identical. `waveform_cache` was not regenerated (F-029). |
| 4c | Why the SHA-256 changed | pass | `a49670ed…ea5d` → `4170852d…8185c`, +1 byte. Only `examination.json` (`processing.processedAt`) and `manifest.json` (`generatedAt` and that file's hash) differ. Everything else is byte-identical. Expected. |

Log: `logs/hotrun-run04.log` (one new stdout line, "microphone opened"; `app.log` empty, F-018).

## Findings

Repeats, cited and not filed again: F-021 (step 1a), F-023 (step 1c, twice), F-025 (step 2a),
F-026 (steps 2c, 4a).

### F-027: The editor has no way out except Accept

- **Severity:** question
- **Area:** desktop-app
- **Run / step:** run 04, step 3
- **Expected:** §8.7 lists drag, playback, previous/next and accept. §10.3 scenario 4 describes an
  untouched pass that writes nothing. The spec does not say whether the examiner can leave
  without accepting, for example to drop an edit.
- **Actual:**
  - The editor has Previous, Play, Next and Accept. There is no Back or Cancel, and no
    confirmation.
  - An untouched pass is Next through every segment, then Accept, and that correctly changes
    nothing.
  - Once a boundary has moved, Accept is still the only way out, and it commits the edit. An
    accidental drag can only be undone by dragging it back by hand.
- **Repro:** Sessions → "Open editor" on any session → look for a way out.
- **Evidence:** `screenshots/step-3-run03-editor-opened.png`
- **Notes:** not tested whether Accept on a middle segment finishes.
- **Status:** fixed 2026-10-24, not yet re-run. Decision (user): add both Reset segment and Discard changes. **Reset segment** puts the current segment back to its boundaries when the editor opened, either the take's or a loaded prior edit; it is enabled only when the segment changed. **Discard changes** writes and deletes nothing. It exits at once if nothing changed, otherwise it asks "Discard edits?". From Sessions it pops; after a session it continues to Processing with what is on disk (`onDiscarded` callback). Spec §8.7. Tests: 9 `EditorComponentTest`, 1 `RootComponentTest`, 1 `NewScreensSmokeTest` (all named F-027).

### F-028: The editor's position line stays at the old start after the start boundary moves

- **Severity:** cosmetic
- **Area:** desktop-app
- **Run / step:** run 04, step 2
- **Expected:** after the start boundary moves, the position line and readout lie inside the
  selected range.
- **Actual:** after dragging Start from 0:42.1 to 0:43.5, "Position" still read 0:42.1. The red
  line stayed outside the shaded range. After Next then Previous it showed 0:43.5.
- **Repro:** open an editor segment, drag the start handle right, look at the position line.
- **Evidence:** `screenshots/step-2-after-drag-start.png`
- **Status:** open

### F-029: Reprocessing doesn't regenerate `waveform_cache/`

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 04, step 4
- **Expected:** §8.8: "Reprocessing (from the session browser) regenerates `clips/`, `archive/`,
  and `waveform_cache/`".
- **Actual:** after Reprocess, `waveform_cache` kept its mtime (00:16:08) while the clips and
  archive were rewritten.
- **Repro:** process a session, note the mtime of `waveform_cache/`, Reprocess from Sessions,
  compare.
- **Evidence:** file mtimes (runner's before/after listing in the scratchpad,
  `e2e\run03-before.txt`, and the run 04 checks). No screenshot (filesystem state).
- **Notes:**
  - The master does not change on reprocess, so the cache stays valid and there is no visible
    effect.
  - Either the implementation or the spec text should change.
- **Status:** fixed 2026-10-24 (`8aaa7c1`) in the spec, not yet re-run. As decided, §8.8 and `docs/dev/data-and-schemas.md` now say that reprocessing regenerates `clips/` and `archive/`, and that `waveform_cache/`, which is derived from the unchanged master, is kept. No code change.

## Context updates

- `sessions[]`: run 04, `E2E1023-04-1`, `fb60e12fb4124064b8ba83c3543ee003`, the folder, the ZIP,
  the SHA-256 after reprocessing (`4170852d…`) and before it, `timelineUsed` edited, processed,
  not uploaded.
- The run 03 session note says it was reopened untouched, with the ZIP unchanged.
- `app.editorNotes`.

## Notes for later runs

- **Drag helper:** `Drag-InWindow.ps1 -FromX 374 -FromY 420 -ToX 472 -ToY 420` worked first time.
  The handle centre is its bounds x + 12. At a 1000×820 window, the client area is 984×781 at
  origin (56,79).
- **Node IDs:** Stop turns into Retry under the same node ID, so check the button state before
  clicking.
- **Editor exits:** an editor opened from Sessions returns to Sessions. After a live session it
  ends at "Examination complete" → `sessionSummary.doneButton` → main menu.
- **Reprocessing:** it takes about 1 s with no UI feedback. Watch the file mtimes instead.
- **Timing:** the monologue is not enforced to its 20 s length (informational).
