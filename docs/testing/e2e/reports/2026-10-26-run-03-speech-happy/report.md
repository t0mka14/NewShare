# Run 03: Speech session, happy path

- Date / time: 2026-10-26 18:47-18:53 local   Runner: Sonnet   App commit: `7cd7e47`
- Preconditions met: yes (token A, CABLE Output mic and cached config from run 02; player looping; signal on the cable mean -22.3 dB, max -9.1 dB before calibration)
- Result: passed with findings

Report text saved by the orchestrator from the runner's hand-back. Patient code `E2E1026-03-1`, server = new web (clinic A, `E2E 1026 Speech protocol`, configVersion `2026-10-26.172809`). Session ID `6c08b80c07704a4d990c207fab344e7b`. Nothing was uploaded; status Processed / Not uploaded. App stopped at the end, `app/data` kept.

## Steps
| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Start the protocol, pick the speech protocol 📷 | pass | Picker lists both studies with headers; description shows the placeholder "Auto-created from AdminTaskEditor" (F-069). `step-1-picker.png` |
| 2 | Patient form validation 📷 | pass | Three fields: `patient_code` (required, `[A-Za-z0-9_-]+`), `visit_code` (required, `V[0-9]{2}`), `year_of_birth_opt` (optional, `(19\|20)[0-9]{2}`). Empty: "This field is required." plus "Please check the highlighted fields."; bad values: "Please check the format of this field."; Continue blocked both times. `E2E1026-03-1`, `V01`, `1975` accepted. `step-2-empty-required.png`, `step-2-bad-pattern.png` |
| 3 | Calibration 📷 | pass with finding | Band green 0.2-0.5; level bar moves (about 0.05-0.07) but stays below the band: player at 100 %, endpoints at 100 %, so it can't be raised (F-03-2). Continue not blocked (F-021). Picker shows CABLE Output. `step-3-calibration.png` |
| 4 | Info task | skipped | Config has no INFO task; first screen is "Task 1/6" (F-072). |
| 5 | Phonation rep 1 (2 takes), rep 2 (1 take) 📷 | pass | Example button plays ("Stop example"), disabled during a take. Timer counts; indicator ring changes about 2 px (not an obvious movement; F-024 closed). Waveform after Stop. Retry starts take 2 at once (F-023, by design). `step-5-*.png` |
| 6 | PATAKA, one take | pass | `step-6-pataka-after-stop.png` |
| 7 | Reading, one take 📷 | pass with known finding | Only instr1 shown; the web config has no passage (F-009). `step-7-reading.png` |
| 8 | Monologue: Skip | pass | "Skip task" with confirm dialog, then "Previous task skipped" notice (F-022 fixed). `step-8-monologue.png`, `step-8-skip-dialog.png` |
| 9 | Questionnaire 📷 | pass with findings | Empty: red errors on Q1, Q3 straight away (F-065), Next disabled. Filled: Next enabled. Q4 option 3 "Žádné" cut off and unreachable (F-03-1); set via the semantic action. `step-9-*.png`, `F-03-1-questionnaire-option-clipped.png` |
| 10 | End of protocol, editor 📷 | pass with finding | Config has `enableEditor: false`, so the editor isn't offered after the last task (F-03-4). Opened from Sessions: 4 segments (PHONATION 9.6 s = take 2, PHONATION, PATAKA, READING), none for the monologue; labels lack the repetition (F-025). "Discard changes" left the ZIP unchanged (same SHA-256 and mtime). "Accept" label wraps (F-03-3). `step-10-editor-segment1.png` |
| 11 | Processing 📷 | pass | "Processing" screen with progress (F-026 fixed), then "Session processed (4 clips) - ready to upload." Sessions row: Processed, Not uploaded. `step-11-*.png` |
| 12 | Record the session ID | pass | In the context file. |
| 13 | Local archive check | pass | Matches `upload_data.md`; no differences. |

### Step 13: ZIP contents
- `app/data/sessions/2026-10-26_E2E1026-03-1_6c08b80c07704a4d990c207fab344e7b/archive/E2E1026-03-1_6c08b80c07704a4d990c207fab344e7b.zip`, 13,712,393 bytes, SHA-256 `73a64e3aa666e155ba1887ab1b8eb404942b02623ea3bd8cad6c1903e21c9660`. Opened read-only with `scripts/run-03/inspect_zip.py`.
- Entries: `master/session_master.wav` (137.39 s, 48 kHz/16-bit/mono, once), 4 clips, `examination.json`, `participant.json`, `task_configuration_snapshot.json`, `timeline_original.json`, `manifest.json` last. No `timeline_edited.json`, no video, no raw parts.
- Clips (`<patient>_<siteName without spaces>_<index>_<SUBTYPE>_Rep<n>.wav`, ffprobe): `0_PHONATION_Rep1` 9.60 s (take 2), `1_PHONATION_Rep2` 8.70 s, `2_PATAKA_Rep1` 10.00 s, `3_READING_Rep1` 9.25 s; all pcm_s16le 48000 mono; durations equal STOP minus START of the kept take. None for the skipped monologue.
- Manifest: 9 files, all hashes match; `sessionId` equals `examination.json` and the folder; `configVersion` 2026-10-26.172809.
- `examination.json`: tasks 0-3 with `clipFile`, phonation rep 1 `takes: 2`, task 4 `skipped: true`, task 5 `questionnaireAnswers` {q1 "Dobre", q2 "Zadna poznamka", q3 [o2], q4 [o1, o3]}; `processing` Done, `timelineUsed: original`; contains `installationId` (redact if quoted).
- `participant.json`: `patient_code`, `visit_code` V01, `year_of_birth_opt` 1975 (filled for run 06's masking check).
- Snapshot byte-identical to `app/data/config/config.json`. Timeline: 25 events, `TAKE_REJECTED` (EXAMINER_REPEAT) for phonation take 1, `TASK_SKIPPED` for task 4. All clips have the same mean level (-22.3 dB): the loop source.

### Config the app received
- 5 config tasks (6 instances): PHONATION (nrepetition 2, length 5, canRepeat, showIndicator, `audioExamplePath` an absolute server URL), PATAKA (5 s), READING (length 150), MONOLOGUE (20 s, canSkip), QUESTIONNAIRE (OPEN `.+`, OPEN `.*`, SINGLE_CHOICE, MULTIPLE_CHOICE). Matches the plan except no INFO task (F-072).
- Top level: `enableEditor: false`, `useCalibration: true`, `indicatorType: CIRCLE`, languages `cs, en`, `defaultMicName ""`.
- `en` questionnaire strings are Czech (F-012). READING has `instructionKeys` [instr1, instr2] and no passage (F-009). `recordingsFileName` is `${field.patient_code}_${siteName}_${taskIndex}_${task.subtype}_Rep${repetition}`.

## Findings
<a id="f-03-1"></a>

### F-03-1: The last option of a multiple-choice question is cut off and cannot be reached at 1000x820
- **Severity:** major  **Area:** desktop-app  **Run / step:** run 03, step 9
- **Expected:** a screen too tall for its window scrolls (spec §8, §13 decision 36), so every option can be seen and ticked.
- **Actual:** the questionnaire container (y 144-616) shows Q4 options 1 and 2 only. Option 3 "Žádné" has zero bounds and scrolling does nothing. It can be ticked only through the semantic action. Next is enabled regardless.
- **Repro:** run the speech protocol with the 4-question questionnaire at 1000x820 and look at question 4.
- **Evidence:** `screenshots/F-03-1-questionnaire-option-clipped.png`.
- **Notes:** overlaps run 09 (layout). Four questions plus the "Previous task skipped" banner fill the height.
- **Status:** fixed (2026-10-28), confirmed in run 12 (2026-10-28). In tests the questionnaire does scroll: the last option has 0 bounds only because it is scrolled out (about 100 px overflow), and `performScrollTo` and the mouse wheel reach and toggle it. Nothing showed that more content follows, so a scrollbar now appears while the questions overflow (`TaskContent.kt`). "Scrolling does nothing" was not reproduced headless; the re-test checks it live with the MCP scroll tool. Test: `QuestionnaireLayoutUiTest` (8 tests at 984×781, 944×681, 1000×820, 960×720).

<a id="f-03-2"></a>

### F-03-2: The test audio source cannot reach the calibration band (environment)
- **Severity:** minor  **Area:** environment  **Run / step:** run 03, step 3
- **Expected:** adjusting the player volume brings the level into 0.2-0.5.
- **Actual:** wmplayer at full volume, CABLE endpoints 100 %. The looping file is `file_example_WAV_2MG` (12 s, 44.1 kHz stereo music sample, not speech), mean -22.3 dB; the bar sits at 0.05-0.07. The mic gain slider is disabled for this device.
- **Repro:** open calibration; `scripts/run-03/endpoint-volume.ps1` lists the volumes.
- **Evidence:** `screenshots/step-3-calibration.png`.
- **Notes:** a louder or speech source would fix it.
- **Status:** closed, environment (2026-10-29). Test setup: `test-plan.md` §3 now says to raise the player volume until calibration reaches the band; Continue works below the band by design (F-021).

<a id="f-03-3"></a>

### F-03-3: "Accept" in the editor wraps to "Acc / ept"
- **Severity:** cosmetic  **Area:** desktop-app  **Run / step:** run 03, step 10
- **Actual:** the button is 113x60 px and the label splits in two at 1000x820 (Discard is 209 px wide).
- **Evidence:** `screenshots/F-03-3-editor-accept-label-wrapped.png`.
- **Status:** fixed (2026-10-28) together with F-125, confirmed in run 12 (2026-10-28). Test: `EditorLayoutUiTest` "editor controls fit on one line at 1000x820" (red before the fix: Accept 60 dp high).

<a id="f-03-4"></a>

### F-03-4: The web config has `enableEditor: false`, so the normal flow skips the editor
- **Severity:** question  **Area:** integration  **Run / step:** run 03, step 10 (affects run 04)
- **Expected:** plan step 10 and run 04 expect the editor after the last task "if `enableEditor`".
- **Actual:** served config has `enableEditor: false`; nothing in the web UI seems to set it. The editor is reachable only via Sessions > Open editor.
- **Evidence:** `app/data/config/config.json`; `screenshots/step-11-processing-result.png`.
- **Status:** open

<a id="f-03-5"></a>

### F-03-5: The app log says nothing about the session (start, take, processing)
- **Severity:** question  **Area:** desktop-app  **Run / step:** run 03, step 11
- **Expected:** enough in the log to follow a session for support.
- **Actual:** `app.log` for the whole run has config fetch, check-in and one "microphone opened" line; nothing about session creation, processing or the editor. No patient code or path leaks (F-059 / F-060 fine).
- **Evidence:** `logs/app.log` (installation ID replaced).
- **Status:** fixed (2026-10-30), confirmed in run 16 (2026-10-30). Fix (NewShare `app/`, uncommitted): INFO lines with the session ID only: `session created`, `take started/stopped/rejected`, `task skipped`, `session finished`, plus the existing processing lines. Test: `SessionComponentTest` "F-095 app log records session lifecycle lines with the session id and no patient code" (red before).

### Duplicates seen again
F-009, F-012, F-025, F-065, F-069, F-072. Fixed items confirmed: F-022, F-026, F-027.

## Context updates
`2026-10-26-context.json`: `sessions[]` entry for run 03 (patient, session ID, ZIP path and SHA-256, `uploaded: false`, filled fields and answers) and a `configNotes` block.

## Notes for run 04
- Editor: Sessions > Open editor, then Reprocess. Boundary handles at y 187-640 (1000x820); Accept at (839,697, 113x60).
- Q4 option 3 only through the semantic action: node testTag `questionnaire.answer.p1_t5_q4.p1_t5_q4_o3`.
- Patient form: `patient_code`, `visit_code` (V\d\d), optional `year_of_birth_opt`; use `E2E1026-04-1` / `V01`.
- Skip opens a confirm dialog (`task.skipConfirmButton`). Run `scripts/run-02/front.ps1` before every screenshot. Start takes about 25 s. `type_text` replaces field content.

## Scripts and logs
`docs/testing/e2e/scripts/run-03/` (endpoint-volume.ps1, inspect_zip.py, README.md); `logs/app.log` (redacted), `hotrun.log`.
