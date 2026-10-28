# Run 09: Window sizes and layout

- Date / time: 2026-10-28, about 20:10 to 20:50 (RDP). Runner: Sonnet. App commit: 7cd7e47 (working tree has only the uncommitted E2E docs)
- Preconditions met: yes (token A, mic `CABLE Output`, wmplayer looping, OBS virtual camera on, preview showed a moving frame). Two deviations from the brief:
  - The work area is **1512x876** (screen 1512x916), not 2292x1039, so W6 = 1512x876.
  - The app **opens at 1000x820** (`Main.kt:103` `DpSize(1000.dp, 820.dp)`, checked by the orchestrator), not 800x600 as plan §2 says.
- Result: **passed with findings** (4 major, 4 minor, 2 cosmetic, 1 question). Index IDs: F-09-1..F-09-11 = **F-118..F-128**.
- Saved by the orchestrator from the runner's returned text; finding IDs renumbered and the severity count corrected by the orchestrator.

Sizes are outer window sizes. The client area is 16 px narrower and 39 px lower (W2 = 784x561). `resize_window` accepts every size down to 640x480, so **no minimum window size is enforced** (F-118).

Coverage:
- Tree dumps and screenshots were taken at W1, W2, W4 and W6 on the screens that matter, plus W3/W5 where height or width decided the outcome (Sessions, Editor, Upload).
- `pass*` = not dumped at that size; follows from a verified smaller size with the same or smaller height and width (not independent evidence). `n/d` = not dumped, no such argument.
- The MCP returns trees only to the agent, so the dumps in `logs/trees/` are hand-saved subsets, analysed by `scripts/run-09/tree_check.py`.
- W4 screenshots exist only for the post-take phonation, the questionnaire and the dialog; W2/W4/W6 were not all taken for every screen.

## Screens x sizes

| # | Screen | W1 640x480 | W2 800x600 | W3 1024x768 | W4 1280x800 | W5 1366x768 | W6 1512x876 |
|---|---|---|---|---|---|---|---|
| 1 | Main menu | pass (scrolls; buttons 3 and 4 need the scroll in a 175 px viewport, F-118) | pass | pass* | pass | pass* | pass (buttons capped at 560 px) |
| 2 | Settings (after Refresh) | pass (scroll; "Save and go back" only after scrolling, F-063 seen again) | pass (scroll, F-063 seen again; F-020 not reproduced) | pass* | pass* | pass* | pass (column about 1100 px) |
| 3 | Protocol picker | F-118 (list box 137 px, one protocol visible, button text wraps; reachable by scroll) | pass | pass* | pass | pass* | n/d (centred, as W4) |
| 4 | Patient form + validation messages | n/d | pass (with 3 errors the buttons need a scroll) | pass* | pass | pass* | pass (screenshot) |
| 5 | Calibration | **F-119** | pass | pass* | pass | pass* | pass, F-127 |
| 6a | Speech tasks (phonation, PATAKA, reading, monologue) | **F-120** | **F-121** (reading, monologue pass) | pass* | pass | pass* | pass, F-127 |
| 6b | Questionnaire + required messages | n/d | pass (last option inside the viewport) | pass* | pass (F-091 not reproduced) | pass* | n/d |
| 7 | Video tasks | **F-120** seen again | **F-121** seen again; C6 pass | pass* | pass; C6 pass | pass* | pass; C6 pass (stays 200x150) |
| 8 | Editor + drag | n/d (fails as W2) | **F-125** (drag test pass) | pass | pass | pass* | pass |
| 9 | Upload screen | n/d (fails as W2) | **F-126** | pass | pass | pass* | n/d |
| 10 | Sessions (6 rows) | n/d (fails as W2) | **F-124** | **F-124** | pass | pass | pass, F-127 |

C-checks:
- **C1 (reachable)** fails in F-124, F-125, F-126 (button 0x0, no scroll reaches it) and F-120 (instructions and "Play example" 0x0).
- **C2 (squashed)** fails in F-119 (Continue 52 px wide of 176; picture and meter 77 px high of 257) and F-124 at W3 (Back 17 px high of 40).
- **C3 (overlap)** found nothing. START and "Next task" touch at x 517 at W2 but do not overlap.
- **C4 (text cut mid-line)** shows in F-119, F-120, F-125.
- **C5 (W6)** is F-127.
- **C6** passes: the video preview is 200x150 at every size, including W6.

Scripted output: `logs/tree_check_output.txt`.

## Resizing during use

| # | Check | Result | Notes / finding |
|---|---|---|---|
| R1 | During a take (speech phonation, W4 -> W2 -> W4) | pass | Timer kept going, take kept, no dialog, no new log line. Clip `..._0_PHONATION_Rep1.wav` is 18.80 s (ffprobe), equal to timeline START 37.0 s to STOP 55.8 s. Session 09-1. |
| R2 | During a filmed take | partly pass, F-122, F-123 | Take 1 of 09-2: W4 -> W2 (about 10 s, one screenshot) -> W4 coincided with READ_STARVATION and the "microphone was disconnected" dialog (F-122). Take 2 of 09-2 and take 1 of the clean extra session **09-3** ran through the same resizes with no error; the preview came back at 200x150 each time. 09-3 after processing, MP4 vs WAV: PHONATION 17.70 vs 17.75 s (the resized take), MONOLOGUE rep1 9.23 vs 9.20 s, rep2 13.30 vs 13.45 s (all within 0.5 s). 09-2 could not be processed (F-123), so its MP4s are unchecked; its raw take 2 has 1146 frames = 38.2 s vs timeline 37.95 s. |
| R3 | In the editor (move boundary, then W2 and W6) | pass | Drag at W2 with `Drag-InWindow.ps1` from x 617 to 560 moved End from 0:55.8 to 0:53.4 (expected 53.45 from the tick scale), Length 16.4 s. At W6 and W3 End and Length are unchanged, and the handles sit at the same fraction of the canvas (stop 0.744, start 0.173 at W2 and W6). |
| R4 | Minimise and restore during calibration | pass | Done with `ShowWindow` (SW_MINIMIZE, 5 s, SW_RESTORE), not a title-bar click. After restore three screenshots show the level bar at different heights (about 0.05, 0.12, 0.11), so the meter moves (below the green band: F-092). |
| R5 | Restart at W4 | note | The app opens at 1000x820 after the restart (it was left at 1280x800), so the size is not remembered (known). The plan's "800x600" is outdated (F-128). |

## Findings

### F-09-1: No minimum window size; at 640x480 the main menu and protocol picker show only part of their list
Index ID: **F-118**
- **Severity:** question
- **Area:** desktop-app
- **Run / step:** run 09, W1, screens 1 and 3
- **Expected:** spec decision 36: a screen too short for its content scrolls. The spec does not say whether a minimum window size is enforced.
- **Actual:** `resize_window` 640x480 is accepted. Main menu: the button list is a 175 px viewport, so buttons 3 and 4 need a scroll (the 140 px SAMI logo takes the rest). Protocol picker: the list box is 137 px high, one protocol is visible, the button text wraps to two lines. Everything stays reachable by scroll (C1 passes), but the nested scroll boxes are awkward. The consequences of the missing minimum are F-119, F-120, F-124, F-125, F-126.
- **Repro:** `resize_window` 640x480, then Main menu, then New examination.
- **Evidence:** `screenshots/picker-W1.png`; `logs/trees/main_W1*.json`.
- **Notes:** decide whether to enforce a minimum (for example 800x600) or fix the screens below.
- **Status:** fixed (2026-10-28), confirmed in run 12 (2026-10-28): the window has a minimum size of 960×720 (`Main.kt`, `window.minimumSize`). Guard tests: `MinimumWindowSizeUiTest` (9 screens at the 944×681 content of a 960×720 window).

### F-09-2: Calibration at 640x480: "Continue" is squeezed to a sliver, picture and level meter shrink to 77 px
Index ID: **F-119**
- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 09, W1, screen 5
- **Expected:** all controls full size (C2), decision 36.
- **Actual:** "Continue" is 52 px wide (176 at W4) and its label is cut off. The microphone picture and level meter are 77 px high (257 at W4), and the meter's scale numbers overlap each other. The screen has no scroll.
- **Repro:** patient form -> Continue with the window at 640x480.
- **Evidence:** `screenshots/F-09-2-calibration-W1.png`; `logs/tree_check_output.txt`.
- **Notes:** at 800x600 the screen is fine.
- **Status:** closed (2026-10-28), not reachable: 640×480 is below the new minimum window size of 960×720 (F-118). Confirm in the re-test.

### F-09-3: Task screens at 640x480: instructions and "Play example" disappear, the "Next task" label is written vertically
Index ID: **F-120**
- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 09, W1, screens 6 and 7
- **Expected:** instructions readable or scrollable (C1, C4), decision 36.
- **Actual:** the instruction box collapses to 0x0, and "Play example" and the "Start now" card are 0x0 too. The disabled "Next task" button shows "N e x t" one letter per line. START and the level ring stay. The video protocol behaves the same (preview stays 200x150).
- **Repro:** start a session, open the phonation screen, `resize_window` 640x480.
- **Evidence:** `screenshots/F-09-3-phonation-W1.png`; `logs/trees/phon_W1.json`.
- **Notes:** related to F-031 (fixed): the layout gives the instructions whatever is left over.
- **Status:** closed (2026-10-28), not reachable: 640×480 is below the new minimum window size of 960×720 (F-118). Confirm in the re-test.

### F-09-4: At 800x600 the instruction box is only 132 px high while about 210 px below it stay empty; "Play example" and "Start now" are hidden behind a scroll
Index ID: **F-121**
- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 09, W2, screens 6 and 7
- **Expected:** the examiner sees the instructions and the example button without hunting for a scroll bar (spec §8, task screens).
- **Actual:** phonation, PATAKA and the video phonation show 4 lines of text, cut mid-line at the bottom of a 132 px box. "Play example" and the "Start now" card are only reachable by scrolling the box. The area between the box and the level ring (y 236 to 396) is empty. PATAKA's text is about 4x the box height (scrollbar thumb 33 of 132 px). Reading and monologue fit.
- **Repro:** window 800x600, phonation task.
- **Evidence:** `screenshots/phonation-W2.png`, `screenshots/video-phonation-W2.png`; `logs/trees/phon_W2.json` (the analyser flags the example button as unreachable because that dump is unscrolled).
- **Notes:** at W4 the box is 332 px and shows everything. After a take at W4 the "Start now" card shrinks to 3 px because the waveform takes the room (by design, same priority).
- **Status:** closed (2026-10-28), not reachable: 800×600 is below the new minimum window size of 960×720 (F-118); calibration and task screens pass the guard tests at 944×681. Confirm in the re-test.

### F-09-5: A filmed take was interrupted ("microphone was disconnected", READ_STARVATION) right after a resize
Index ID: **F-122**
- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 09, resize check R2, session 09-2
- **Expected:** resizing does not affect recording.
- **Actual:** take 1 started at 20:33:27, resized to W2, screenshot, back to W4 at about 20:33:46. At 20:33:47 the log has `recorder interrupted at sample=2424000 reason=READ_STARVATION`, and the timeline `TAKE_REJECTED reason DEVICE_LOST`. The dialog "The microphone was disconnected. Reconnect it or choose another device to continue." appeared with Reconnect and Resume. The cable and wmplayer were fine. Not reproduced: two further filmed takes with the same resizes and a speech take ran clean (1 in 4 attempts). Possibly a stall of the capture thread while ffmpeg encodes 1280x720 and the window relayouts.
- **Repro:** video protocol, START, resize 800x600, wait, resize 1280x800 (intermittent).
- **Evidence:** `screenshots/resize-2-filmed-take-back-W4.png`; `logs/app-redacted.log` (20:33:47); `logs/09-2-examination.json`.
- **Notes:** see F-123 for what the interruption did to the session.
- **Status:** open

### F-09-6: A session with a microphone interruption plus Resume cannot be processed ("Processing failed", IoFailure); Retry and Reprocess fail again
Index ID: **F-123**
- **Severity:** major
- **Area:** desktop-app
- **Run / step:** run 09, session 09-2 (video protocol), after the last task
- **Expected:** a session with a handled interruption (spec §8.4, multi-part master) is processed; at worst the rejected take is dropped.
- **Actual:** after the interruption in F-122 and Resume, three more tasks were recorded. The master is `session_master.wav` 50.5 s plus `session_master.part2.wav` 91.3 s, with 4 video files. Processing ended with "Processing failed - This session could not be processed. You can retry or go back." Retry failed again. The log has only `processing failed: IoFailure (session ...)` at 20:35:34 and 20:35:52, no detail. `examination.json` has `processing.status: Failed`. No ZIP is made, so the whole session is lost for upload. A clean video session, 09-3, recorded after a restart (with a resize during take 1) processed fine into 3 MP4 and 4 WAV clips, so the cause is the interruption (2-part master or the rejected take's video file), not video as such.
- **Repro:** video protocol; cause a mic interruption during a take (F-122, or disable the cable) and press Resume; finish the tasks.
- **Evidence:** `screenshots/F-09-6-processing-failed-09-2.png`; `logs/app-redacted.log`; `logs/09-2-examination.json`; `logs/09-2-timeline.events.jsonl`.
- **Notes:** `ProcessSessionUseCase` logs only the error class name; `IoFailure.detail` is dropped (see F-057). The run-08 crash-recovered sessions had a single part and processed, so the multi-part path may be untested. Session 09-2 (`579b05d4...`) is kept in `app/data/sessions` for the developers.
- **Status:** fixed (2026-10-28), confirmed in run 12 (2026-10-28). Cause: the writer stores `Interruption.partFile` as a bare file name (`session_master.part2.wav`, in `master/`), but `MasterPartMap.build` resolved it against the session folder, so cutting the first part-2 clip threw `NoSuchFileException` → `IoFailure`. Fix: part files resolve against the master folder (a legacy `master/` prefix still works); a take spanning a part boundary is now skipped and listed in `processing.skippedTakes` instead of failing the session; the failure log names the exception type and the file name only. Tests: `ProcessSessionUseCaseIntegrationTest` "multi-part master - each clip cuts from its own part…" (now with the bare name; red before the fix with the run-09 error), `ProcessSessionUseCaseTest` "a take spanning a part boundary is skipped and recorded…" and "a failure caused by a missing file logs the exception type and file name only…", `MasterPartMapTest` "a legacy master-prefixed partFile still resolves…".

### F-09-7: Sessions: "Go to upload" and "Back" are squeezed out at 800x600 and 1024x768
Index ID: **F-124**
- **Severity:** major
- **Area:** desktop-app
- **Run / step:** run 09, screen 10
- **Expected:** a screen too short for its content scrolls (decision 36), and the examiner can always leave the screen.
- **Actual:** with 6 sessions the list takes all the room. At 800x600 both buttons are 0x0 and the page does not scroll (only the list does), so there is **no way to leave Sessions** except enlarging the window. At 1024x768 "Go to upload" is fine but "Back" is a 17 px sliver without its label. At 1280x800, 1366x768 and 1512x876 the buttons are normal (list box 360 px). With few sessions the buttons fit, so the defect grows with the number of sessions.
- **Repro:** 6 or more sessions in Sessions, window 800x600 or 1024x768.
- **Evidence:** `screenshots/F-09-7-sessions-no-back-W2.png`, `screenshots/F-09-7-sessions-back-squashed-W3.png`; `logs/trees/sessions_W2.json`, `sessions_W3.json`.
- **Notes:** at the default 1000x820 (F-128) not checked exactly; Back fits at W4-W6 for the same list.
- **Status:** fixed (2026-10-28), confirmed in run 12 (2026-10-28). Cause: the session list had a fixed 360 dp height in a non-scrolling column, so below ~820 px the button row got no height. Fix: the list shrinks (`weight(1f, fill = false).heightIn(max = 360.dp)`), and the window has a minimum size of 960×720 (`Main.kt`). Test: `SessionBrowserLayoutUiTest` (960×720, 944×681 content, 800×600, 1000×820; red before the fix: Back 1 dp high).

### F-09-8: Editor at 800x600: "Accept" is not on screen (0x0), "Discard changes" wraps one letter per line (same root cause as F-093)
Index ID: **F-125**
- **Severity:** major
- **Area:** desktop-app
- **Run / step:** run 09, screen 8, W2
- **Expected:** all editor buttons reachable (C1), decision 36.
- **Actual:** the button row (Previous 145 + Play 109 + Reset 196 + Next 112 + Discard 209 + Accept 131 px plus gaps, about 1000 px) does not fit a 784 px client. "Accept" is 0x0 and "Discard changes" becomes a 134x104 px box with the label broken per letter. Nothing scrolls, so the examiner cannot accept an edit; leaving needs "Discard changes". At 1024x768 (client 1008) and above the row fits. F-093 ("Acc / ept" at 1000x820, client 984) is the same overflow with a smaller shortage.
- **Repro:** Sessions -> Open editor on 09-1, window 800x600.
- **Evidence:** `screenshots/F-09-8-editor-no-accept-W2.png`; `logs/trees/editor_W2.json`.
- **Notes:** the boundary drag still works at W2 (R3) and the handles keep their time position across sizes.
- **Status:** fixed (2026-10-28), confirmed in run 12 (2026-10-28). Cause: the controls were one `Row` of wrapping labels under a waveform panel with a 310 dp minimum and `weight(1f)`, so the labels squeezed ("Acc/ept") and at small heights the row got 0 height. Fix: labels on one line (`maxLines = 1, softWrap = false`), the controls row is a `FlowRow` that wraps whole groups, waveform minimum 220 → 150 dp; with the 960×720 minimum window. Test: `EditorLayoutUiTest` (1000×820, 984×781, 960×720, 944×681, 800×600, plus the no-segment state; red before the fix at all five sizes).

### F-09-9: Upload screen at 800x600: "Back" is not on screen (0x0)
Index ID: **F-126**
- **Severity:** major
- **Area:** desktop-app
- **Run / step:** run 09, screen 9, W2
- **Expected:** the examiner can leave the Upload screen without resizing the window.
- **Actual:** the session list box is a fixed 220 px, followed by the progress bar and the Upload button. "Back" ends up outside the 561 px client (0x0), with no scroll. At 1024x768 and above it fits. A failed row's message would need still more room (not checkable here).
- **Repro:** Main menu -> Upload at 800x600 with one ready session (09-1).
- **Evidence:** `screenshots/F-09-9-upload-no-back-W2.png`; `logs/trees/upload_W2.json`.
- **Notes:** no failed upload was pending; run 08's `reports/2026-10-26-run-08-errors/screenshots/step-3-upload-blocked.png` shows the failed state at 1000x820.
- **Status:** fixed (2026-10-28), confirmed in run 12 (2026-10-28). Same cause and fix as F-124 (`UploadContent` list 220 dp → shrinking; minimum window 960×720). Test: `UploadLayoutUiTest` (red at 800×600 before the fix).

### F-09-10: At 1512x876 content stretches: long text lines, picture and level meter far apart, fixed-height session list
Index ID: **F-127**
- **Severity:** cosmetic
- **Area:** desktop-app
- **Run / step:** run 09, W6 (C5)
- **Expected:** content stays usable and text lines do not run across the whole screen.
- **Actual:** main menu fine (buttons capped at 560 px). Calibration text lines run about 1350 px, with the picture on the left and the level meter about 700 px away. Task instruction lines are about 1180 px. The session list stays 360 px (4 rows), with 100 px unused below the buttons. The video preview stays 200x150 (decision 60, ok). Settings is capped at about 1100 px.
- **Repro:** window 1512x876, screens 5, 6, 10.
- **Evidence:** `screenshots/calibration-W6.png`, `phonation-W6.png`, `sessions-W6.png`, `settings-W6.png` (token and ID blacked out).
- **Notes:** nothing is unusable.
- **Status:** open

### F-09-11: Plan and notes say the app opens at 800x600; it opens at 1000x820
Index ID: **F-128**
- **Severity:** cosmetic
- **Area:** docs
- **Run / step:** run 09, R5
- **Expected:** `test-plan.md` §2 and §3.4 ("Opens at 800x600 every time", "resize to 1000x820 after each start").
- **Actual:** `Main.kt:103` `rememberWindowState(size = DpSize(1000.dp, 820.dp))`, and `list_windows` after each start says 1000x820. The window size is still not remembered between starts. F-124 to F-126 are therefore found below the default size.
- **Evidence:** `hotrun.log`; `app/src/main/kotlin/org/example/app/Main.kt`.
- **Status:** open (fix in test-plan v1.2)

Seen again, not re-reported:
- **F-063:** "Save and go back" needs a scroll at 800x600 and 640x480.
- **F-020:** not reproduced as a defect: Settings scrolls and all controls are full size once scrolled.
- **F-031:** not seen: the video phonation at W2, W4, W6 has the preview at 200x150 and the level ring apart.
- **F-091:** not reproduced at 800x600 and 1280x800 (last option inside the viewport); not tested at 1000x820.
- **F-093:** same root cause as F-125.
- **F-106, F-105:** not tested (no camera loss).
- **Window size not remembered:** confirmed (R5).

## Context updates
`reports/2026-10-26-context.json` (UTF-8 without BOM) has a new `run09` block: `E2E1026-09-1` (speech, processed, not uploaded, `e4b33758...`), `E2E1026-09-2` (video, **Processing failed**, `579b05d4...`), extra `E2E1026-09-3` (video, processed, not uploaded, `7ed01799...`), recorded to get a clean video session for R2. The Upload screen now offers 09-1 and 09-3. Nothing was uploaded.

## Notes for the wrap-up
- App stopped, `main.pid` removed. wmplayer looping, OBS virtual camera on, mic privacy `Allow`.
- "Upload" uploads every ready session at once. Do not press it with 09-1 and 09-3 present unless you want them on the server. 09-2 is not uploadable.
- The work area is 1512x876 (the handoff said 2292x1039).
- `redact.ps1` handles one rectangle per call here (`-Rects a,b` blacked out only the first).
- F-124, F-125, F-126 share a cause: bottom buttons placed after content that takes the free height, with no page-level scroll. One fix would cover them and F-093. F-123 needs a developer look at the 2-part master path in `ProcessSessionUseCase`.

## Scripts and logs
- Scripts (`docs/testing/e2e/scripts/run-09/`): `README.md`, `tree_check.py` (C1-C3 on tree dumps), `redact_tree.py`, `move-window.ps1`, `minimize-restore.ps1`; run-09 paragraph in `docs/testing/e2e/scripts/README.md`. Reused: `run-08/start-app.ps1`, `wait-ready.sh`, `stop-app.ps1`, `run-02/front.ps1`, `run-02/redact.ps1`, `tools/Drag-InWindow.ps1`.
- Logs (`logs/`): `app-redacted.log` (installation ID and token removed, 20:20 onwards), `tree_check_output.txt`, `trees/*.json` (trimmed; Settings dumps have the token removed), `09-2-examination.json` (redacted), `09-2-timeline.events.jsonl`. `hotrun.log`, `hotrun-2.log` in the run folder (UTF-16; checked for token and installation ID).
- Screenshots (`screenshots/`): `main-W2`, `main-W6`, `picker-W1`, `patient-W6`, `settings-W2`, `settings-W6` (redacted), `calibration-W2`, `calibration-W6`, `phonation-W2`, `phonation-W6`, `phonation-after-take-W4`, `questionnaire-W4`, `video-phonation-W2`, `video-phonation-W6`, `resize-2-filmed-take-W2`, `resize-2-filmed-take-back-W4`, `resize-4-calibration-after-restore`, `editor-W6`, `sessions-W6`, `F-09-2-calibration-W1`, `F-09-3-phonation-W1`, `F-09-6-processing-failed-09-2`, `F-09-7-sessions-no-back-W2`, `F-09-7-sessions-back-squashed-W3`, `F-09-8-editor-no-accept-W2`, `F-09-9-upload-no-back-W2`.

## Orchestrator follow-up (2026-10-28 fix round)

### F-129: The interruption record's sample offset disagrees with the interruption events by 1500 frames
Index ID: **F-129**
- **Severity:** minor  **Area:** desktop-app  **Run / step:** fix round for F-123, analysis of session 09-2
- **Actual:** `examination.json` `interruptions[0].sampleOffset` is 2424000 (the recorder's value), while the `RECORDING_INTERRUPTED` / `RESUMED` timeline events say 2425500, and part 1 holds 2425500 frames. The part map therefore cuts the last 1500 frames (about 31 ms) of part 1, and the two records disagree. Nothing fails.
- **Evidence:** `logs/09-2-examination.json`, `logs/09-2-timeline.events.jsonl`, part 1 WAV header (4851000 data bytes = 2425500 frames).
- **Status:** open
