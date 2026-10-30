# Run 03: Speech session, happy path

- Date / time: 2026-10-24 00:07–00:10 local   Runner: Sonnet   App commit: `59bb063`
- Preconditions met: yes (app running with token A, CABLE Output mic, EN; player looping)
- Result: passed with findings

The orchestrator saved this report from the runner's returned text and renumbered `F-03-n` to
global IDs. The runner's local copy of the ZIP was moved out of the repo into the orchestrator's
scratchpad, because the clip names inside it contain the installation ID.

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Start protocol, pick the speech protocol 📷 | pass | The picker shows both projects with headers and descriptions. `step-1-picker.png` |
| 2 | Patient form validation 📷 | pass | Empty required field: "This field is required." and "Please check the highlighted fields." `bad code`: "Please check the format of this field." Continue was blocked both times. `E2E1023-03-1` was accepted. `step-2-empty-required.png`, `step-2-bad-pattern.png` |
| 3 | Calibration 📷 | pass with finding | The bar moves with the audio but stays at about 0.03–0.07, below the green 0.2–0.5 band. Continue is not blocked and gives no warning. The player volume was not changed. F-021. `step-3-calibration.png` |
| 4 | Info task | skipped | No INFO task in the config (F-010). Calibration goes straight to task 1/6. |
| 5 | Phonation: rep 1 with 2 takes, rep 2 with 1 take 📷 | pass with findings | The example plays ("Stop example" for about 8 s) and is disabled while recording, so it is not in any clip. Start, timer and Stop work, and the waveform shows after Stop. "Retry task" starts take 2 straight away (F-023). The indicator did not visibly change (F-024). `step-5-*.png` |
| 6 | PATAKA, one take | pass | Example button and both instruction cards shown. `step-6-pataka.png` |
| 7 | READING 📷 | pass, see F-009 | Only instr1 is shown, with no passage. The generic "Start now…" card is hidden (F-009). There is no indicator (`showIndicator` false). One take. `step-7-reading.png` |
| 8 | Monologue: Skip | pass with finding | The skip control is a "Next task E2E questionnaire" button (`task.skipButton`). The screen doesn't mark the task as skipped, which shows only as `skipped:true` in `examination.json` and `TASK_SKIPPED` in the timeline. F-022. `step-8-monologue.png` |
| 9 | Questionnaire 📷 | pass | Empty form: Q1 and Q3 show "Please provide a valid answer." and Next is disabled. Q2 (optional) and Q4 show no error. Filled in, Next is enabled. `step-9-questionnaire-empty.png`, `step-9-questionnaire-filled.png` |
| 10 | Editor 📷 | pass with finding | "Review recordings" has 4 segments: PHONATION, PHONATION, PATAKA, READING. That's one per kept take, with no monologue. Phonation rep 1 is take 2 (0:44.2–0:53.9, the same as take 2 in the timeline). Accept on the last segment finished the session. F-025. `step-10-editor.png`, `step-10-editor-last-segment.png` |
| 11 | Processing 📷 | pass with finding | No processing screen. "Examination complete" shows at once, and the ZIP was ready about 1 s later. Sessions shows "E2E1023-03-1 — 24. 10. 2026 00:07", **Processed**, **Not uploaded**, with Open editor and Reprocess. F-026. `step-11-after-accept.png`, `step-11-sessions.png` |
| 12 | Record the session ID | pass | `60cf6e0af3ed40c599d15d65e5765b51` (context file) |
| 13 | Local ZIP check | pass | Matches `upload_data.md`; details below |

### Step 13: ZIP contents

- **File:** `archive/E2E1023-03-1_60cf6e0af3ed40c599d15d65e5765b51.zip`, 13,374,949 bytes, SHA-256
  `0a1cdd07f162657f04146818c2de99394650b20d37ea880ca36e5601213febe5`.
- **Entries, in order:**
  - `master/session_master.wav` (135.3 s, 48 kHz/16-bit/mono)
  - the 4 clips
  - `examination.json`, `participant.json`, `task_configuration_snapshot.json`,
    `timeline_original.json`
  - `manifest.json`, last
  - No `timeline_edited.json` (nothing was edited), which is correct.
- **Clips:** `E2E1023-03-1_<installationId>_<taskIndex>_<SUBTYPE>_Rep<rep>.wav`:
  - `0_PHONATION_Rep1`: 9.65 s, take 2
  - `1_PHONATION_Rep2`: 8.3 s
  - `2_PATAKA_Rep1`: 6.75 s
  - `3_READING_Rep1`: 11.0 s
  - None for the skipped monologue. All are 48 kHz/16-bit/mono.
- **Manifest:** lists every entry except itself, and all SHA-256 values match. `sessionId` is the
  same in the manifest, `examination.json` and the folder name.
- **`examination.json`:**
  - Tasks 0–3 have a `clipFile`. Task 4 has `skipped:true`. Task 5 has `questionnaireAnswers`
    (Q1 and Q2 text, Q3 `o2`, Q4 `o1` and `o3`).
  - It also has `takes` (phonation rep 1 = 2), `hardware.micUses` and
    `processing: Done, timelineUsed: original`.
- **`participant.json`:** only `patient_code`. The empty year of birth is omitted.
- **Timeline:** 25 events, with `TAKE_REJECTED` (EXAMINER_REPEAT) for phonation take 1 and
  `TASK_SKIPPED` for the monologue.

**Environment note** (not a finding): the master contains the phonation example audio (seconds
8–18, peak about 0.58). The default playback device on this VM is CABLE Input, so anything the app
plays goes into the microphone. No clip contains it.

## Findings

### F-021: Calibration lets the examiner continue outside the band, with no warning

- **Severity:** question
- **Area:** desktop-app
- **Run / step:** run 03, step 3
- **Expected:** the calibration text says "If the signal is outside the bounds of the green area
  move the microphone…". Plan step 3 confirms only once the level is in the band. The spec isn't
  clear on whether Continue should block or warn.
- **Actual:**
  - The level was about 0.03–0.07, orange, below the 0.2–0.5 band. Continue worked at once,
    with no message.
  - `calibration.levelIndicator` exposes no value in the semantic tree.
- **Repro:** start the speech protocol, enter a patient code, and press Continue in calibration
  with a quiet input (the cable is at about −22 dB mean, −9 dB max).
- **Evidence:** `screenshots/step-3-calibration.png`
- **Notes:**
  - It's unclear what the bar measures. A mean of −22 dBFS is about 0.08 RMS, which fits the
    reading, so it's probably RMS.
  - To reach 0.2–0.5 RMS, the input would need about −14 to −6 dBFS mean, which would clip this
    source.
  - Compare with run 08 step 2 (no audio).
- **Status:** closed 2026-10-24, by design (user decision): Continue stays allowed outside the band, with no warning.

### F-022: A skippable task shows only "Next task X", with no Skip label and no skipped state

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 03, step 8
- **Expected:** plan step 8: press Skip, and the task is marked skipped.
- **Actual:** the skip control reads "Next task E2E questionnaire" and looks like the normal
  forward button. It is enabled before anything is recorded. There is no confirmation, and the
  screen doesn't mark the task as skipped. Only `examination.json` and the timeline record it.
- **Repro:** reach the monologue (`canSkip`) and look at the bottom right.
- **Evidence:** `screenshots/step-8-monologue.png`
- **Notes:** one mis-click skips a task without the examiner noticing.
- **Status:** fixed 2026-10-24 (`11b365a`), not yet re-run. Cause: the skip control was the Next button relabelled. Fix: a distinct outlined "Skip task" button with a confirm dialog ("Skip this task?"). The next task shows a "Previous task skipped" badge. Tests: four `TaskComponentTest` cases and one `SessionComponentTest` case. Checked in the app on the monologue task.

### F-023: "Retry task" starts the new take straight away

- **Severity:** question
- **Area:** desktop-app
- **Run / step:** run 03, step 5
- **Expected:** not specified. The plan says "press Repeat once (take 2) and stop again".
- **Actual:** Retry rejects take 1 and starts take 2 at the same sample (2124000), with no Start
  press. The screen goes straight to Stop with the timer running, so the examiner can't
  re-instruct the participant first.
- **Repro:** record a take, then press Retry task.
- **Evidence:** the session's `timeline.events.jsonl` (`TAKE_REJECTED` and `START_BUTTON_PRESSED`
  at 2124000). No screenshot of the transition.
- **Status:** closed 2026-10-24, by design (user decision): Retry starting the new take at once is intended.

### F-024: The recording indicator didn't visibly change during a take (unconfirmed)

- **Severity:** question
- **Area:** desktop-app
- **Run / step:** run 03, step 5
- **Expected:** plan: "the indicator moves while recording" (`showIndicator` true, CIRCLE).
- **Actual:** the ring and inner disc were the same size in two screenshots about 6 s apart. The
  ring is slightly bigger than in the ready state.
- **Repro:** start a phonation take and compare screenshots.
- **Evidence:** `screenshots/step-5-phonation-recording.png` (the second capture wasn't saved).
- **Notes:** the two samples may have hit equal levels, and with a level of about 0.05 (F-021)
  the movement may be too small to see. Recheck in run 04 or 05.
- **Status:** closed 2026-10-24, not reproducible (user decision). The low input level (F-021) likely kept the movement too small to see.

### F-025: Editor segment labels don't say which repetition a segment is

- **Severity:** cosmetic
- **Area:** desktop-app
- **Run / step:** run 03, step 10
- **Actual:** segments 1 and 2 are both "Recording n of 4 - PHONATION", with no repetition or
  take number. Accept on the last segment ends the session; Next and Previous only navigate.
- **Evidence:** `screenshots/step-10-editor.png`
- **Status:** fixed (2026-10-30), confirmed in run 16 (2026-10-30). Fix (NewShare `app/`, uncommitted): segment labels add ", repetition N" (CS ", opakování N") when the task has more than one repetition. Tests: `EditorLabelAndGroupsUiTest` "F-025 ..." (3).

### F-026: No processing feedback after the editor

- **Severity:** question
- **Area:** desktop-app
- **Run / step:** run 03, step 11
- **Expected:** the plan expects a processing result to screenshot.
- **Actual:** "Examination complete. The examination has finished. Thank you." shows at once.
  Processing runs in the background, and only Sessions shows "Processed".
- **Evidence:** `screenshots/step-11-after-accept.png`, `step-11-sessions.png`
- **Notes:** if processing fails, it's unclear whether the examiner would see it anywhere other
  than Sessions.
- **Status:** fixed 2026-10-24, not yet re-run. Decision (user): keep the auto-advance, and the summary states the result. A processing failure already stays on the processing screen with Retry/Back. `Config.SessionSummary(folderName)` now carries the folder name. A new `SessionSummaryComponent` reads `examination.json`, and the summary shows "Session processed (N clips) – ready to upload." (EN one/other, CS one/few/other forms). A no-master session gets no count, and a session that isn't `Done` gets no processed line. Spec §8.8. Tests: 4 `SessionSummaryComponentTest` cases (F-026), plus `PROCESSED_TEXT` assertions in `HappyPathScenarioTest` and `QuestionnaireOnlyScenarioTest`.

## Context updates

- `sessions[]`: run 03, `E2E1023-03-1`, `60cf6e0af3ed40c599d15d65e5765b51`, the folder, the ZIP
  path and SHA-256, processed, not uploaded.
- `app.editor`: editor test tags.

## Notes for later runs

- **Test tags:**
  - Task screen: `task.startButton`, `task.stopButton`, `task.repeatButton` ("Retry task"),
    `task.nextButton`, `task.skipButton`, `task.exampleAudioButton`.
  - Patient and calibration: `patientInfo.field.patient_code`, `patientInfo.continueButton`,
    `calibration.confirmButton`.
  - Questionnaire: `questionnaire.answer.<key>`.
  - Sessions: `sessionBrowser.openEditorButton.<id>`, `sessionBrowser.reprocessButton.<id>`.
- **Editor handles:** `editor.startBoundaryHandle` and `editor.stopBoundaryHandle` react only to
  a pointer drag (`detectDragGestures`), and the MCP tools can't drag. The orchestrator's
  `scratchpad\e2e\Drag-InWindow.ps1` does a real mouse drag in window client coordinates. At 96
  DPI these equal the semantic tree's bounds.
- **Timing:** each MCP call takes 2–3 s, so allow for that when timing takes.
