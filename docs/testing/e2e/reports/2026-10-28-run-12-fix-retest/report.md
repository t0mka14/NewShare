# Run 12: Targeted fix re-test (criterion 10 of [fix-round-2026-10-28-spec](../../fix-round-2026-10-28-spec.md))

- Date / time: 2026-10-28, about 22:45–23:25 local.
- Runner: Sonnet.
- App commit: `7cd7e47` plus the uncommitted fixes (hotRun of the working tree).
- Preconditions met: yes.
- Result: **passed with findings**. Step 2 (a new session with an interruption and Resume) was not covered.

The orchestrator saved this report from the runner's hand-back.

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | F-123: reprocess `E2E1026-09-2` (2-part master) | pass | See [step 1 detail](#step-1-detail). Screenshot `screenshots/step-1-reprocess-09-2.png`. |
| 2 | New video session `E2E1026-12-1` with an interruption and Resume | not covered | See [step 2 detail](#step-2-detail) and F-12-2. |
| 3 | Minimum window | pass | `resize_window` to 640x480 and to 800x600, repeated. `list_windows` reports 960x720. A read right after the first 640x480 call still showed 1000x820; a re-read a few seconds later showed 960x720. The window never got smaller. Screenshot `step-3-min-window-640x480-request.png` (client area 944x681, which is 960x720 outer). |
| 4 | Layout at 960x720, 1024x768 and 1000x820 | pass | See the [table below](#screens-x-sizes). Trees checked by hand against C1–C4; no tree dumps saved. |
| 5 | Smoke test at 960x720: main menu, Settings, calibration | pass | Main menu fits. Settings needs the known short scroll (F-063); "Save and go back" is reachable after one scroll (tree only, no screenshot). Calibration: every control inside 944x681. Screenshots `step-5-main-960x720.png`, `step-5-calibration-960x720.png`. |
| 6 | End state | pass | App stopped (process gone, `main.pid` removed). `obs64` and `wmplayer` running. Mic privacy `Allow`. Upload not pressed. |

### Step 1 detail

- The session ends "Processed, Not uploaded".
- `examination.json`: `processing.status` is `Done`, with no `skippedTakes`.
- All 4 kept tasks have a `clipFile`. 3 have an MP4; PATAKA has none.
- The ZIP is in `archive/`: manifest, examination and 7 clips.
- The new log has no "processing failed" line. The 3 such lines in the file are older (20:06 and 20:35).
- Clip durations match the timeline:

  | Clip | Duration |
  |---|---|
  | Phonation take 2 (in part 2) | 37.95 s (1,821,600 samples at 48 kHz) |
  | Monologue rep 1 | 8.70 s |
  | Monologue rep 2 | 8.95 s |
  | PATAKA | 9.20 s |

- Each MP4 is within 0.25 s of its WAV.
- Master parts: 50.53 s and 91.33 s.

### Step 2 detail

- **First attempt:** about 24 `resize_window` calls during a filmed phonation take, alternating 960x720, 1000x820, 1024x768 and 1100x800. There was no interruption and no READ_STARVATION in the log.
- **Second attempt:** the user ran Disable then Enable on `CABLE Output` once, with a 4 s gap. The device status was OK afterwards. The app did not interrupt the take.
- **Third attempt:** a second request, with a 15 s gap, was not answered within 15 minutes, so it was not run.
- **Outcome:** no interruption or Resume happened. The session was finished normally and processed: Processed, ZIP built, 4 clips, no failure.
- **Size:** the ZIP is 3.2 GB, because the take ran for 18 minutes while waiting.

## Screens x sizes

| Screen | 960x720 | 1024x768 | 1000x820 |
|---|---|---|---|
| Main menu | pass | n/d | pass |
| Sessions (every row's buttons, Go to upload, Back) | pass (see note) | pass (list 329 px) | pass (list 360 px) |
| Editor (session 09-1) | pass, two rows (see note) | pass, one row, bottom 701 < 729 | pass, two rows, bottom 753 < 781 |
| Upload (Upload, Back; Upload not pressed) | pass (Upload at y 390, Back at y 478) | pass | pass |
| Questionnaire (4 questions, "Previous task skipped" banner) | pass (scrollbar; one scroll shows the last option) | pass (scrollbar) | pass (scrollbar; a scroll is needed for the last option) |
| Calibration | pass | n/d | pass |
| Settings | pass with the known scroll (F-063) | n/d | n/d |

Notes for 960x720:

- **Sessions:** the list scrolls inside 281 px and shows about 3.5 rows. Go to upload is at y 517, Back at y 605–645.
- **Editor:** Previous, Play and Reset are on row 1; Next, Discard and Accept on row 2. Every label is on one line. The bottom is at 653, inside 681.

### Questionnaire scroll calls

All calls target node `questionnaire.container`.

- **960x720:**
  - `scroll(nodeId=1304, deltaY=300)` scrolled 184 px, the maximum.
  - The last option, `p1_t5_q4_o3`, then has bounds 32,480 24x24, inside the container (y 144–516).
  - A click turned it from Off to On.
  - Screenshots: `step-4-questionnaire-960x720-top.png`, `-scrolled.png`.
- **1000x820:**
  - At the top, o3 has bounds 0,0,0,0 and o2 is cut to 16 px high.
  - `scroll(1304, +300)` gave o3 bounds 32,580 24x24 (container y 144–616).
  - Clicks toggled it On, Off, On.
  - `scroll(1304, -500)` went back to the top. Then `type_text` into q1 enabled Next, and Finish processed session 12-2 (4 clips, ZIP).
  - Screenshots: `step-4-questionnaire-1000x820-top.png`, `-scrolled.png`.
- **1024x768:** the scrollbar shows here too; the content is slightly taller than the area.

## Previously fixed findings

| Finding | Result | Evidence |
|---|---|---|
| F-123: multi-part master processing failed | **confirmed fixed** | Step 1. `logs/09-2-examination-after-reprocess.json`, `screenshots/step-1-reprocess-09-2.png`. |
| F-124 (Sessions), F-125 (editor), F-126 (Upload Back) | **confirmed fixed** at 960x720, 1024x768 and 1000x820 | Screenshots of each screen. Smaller sizes can no longer be set. |
| F-093: editor labels wrapped | **confirmed fixed** | Labels on one line at all three sizes. The controls wrap into two rows at 960x720 and 1000x820. |
| F-091: last multiple-choice option unreachable | **confirmed fixed** at 960x720 and 1000x820 | The scrollbar shows; a scroll brings o3 into view with non-zero bounds, and it can be clicked. |
| F-118: window can shrink below a usable size | **confirmed fixed** | Minimum is 960x720 (step 3). |
| F-119, F-120, F-121: problems below the minimum | not covered | Those sizes can no longer be set, so the problems can't be reached. |
| F-122: filmed take interrupted after a resize | not covered | Not reproduced in about 24 resizes; it was intermittent. |

## Findings

### F-12-1: Editor controls are grouped oddly when the row wraps at 960x720 and 1000x820
Index ID: **F-130**
- **Severity:** cosmetic  **Area:** desktop-app  **Run / step:** run 12, step 4
- **Expected:** when the row wraps, Previous/Next and the segment actions stay in a readable order.
- **Actual:**
  - Row 1 has Previous on the left and Play and Reset segment on the right.
  - Row 2 has Next, Discard changes and Accept on the left.
  - So Previous and Next are on different rows, and Play sits on the right while the others sit on the left.
  - At 1024x768 all six are on one row, with only 3 px between Previous and Play.
- **Repro:** at 960x720 or 1000x820, go to Sessions and open the editor for 09-1.
- **Evidence:** `screenshots/step-4-editor-960x720.png`, `step-4-editor-1000x820.png`, `step-4-editor-1024x768.png`.
- **Notes:** every control is reachable and each label is on one line, so this is a judgement call.
- **Status:** fixed (2026-10-30), confirmed in run 16 (2026-10-30). Fix (NewShare `app/`, uncommitted): two groups that wrap as units, navigation (Previous, Next) and actions (Play, Reset, Discard, Accept), at least 8 dp apart. Tests: `EditorLabelAndGroupsUiTest` "F-130 ..." (3, red before with the old layout).

### F-12-2: Disabling the capture device for 4 s during a take does not interrupt the recording
Index ID: **F-131**
- **Severity:** question  **Area:** desktop-app  **Run / step:** run 12, step 2
- **Expected:** a lost microphone during a take shows the interruption dialog (spec §8, device loss).
- **Actual:**
  - During a take, the user ran `Disable-PnpDevice` then `Enable-PnpDevice` on `*CABLE Output*`, with a 4 s gap, from an elevated shell.
  - The take was not interrupted: the timer kept going, and the log has no READ_STARVATION or interruption line.
  - It is unclear whether the open JavaSound line survives a short endpoint disable (a property of the environment), or whether the app should have noticed.
  - The longer 15 s variant was not run.
- **Repro:** start a filmed take, then disable and re-enable `CABLE Output` (4 s gap) from an elevated shell.
- **Evidence:** `logs/app-redacted-run12.log` (no interruption line).
- **Notes:** the interruption, Resume and 2-part master path is therefore covered only by session 09-2 (reprocessed, F-123).
- **Status:** closed, environment (2026-10-29). A VB-Cable endpoint disable doesn't reach an open line; a real loss (stopping Audiosrv) interrupts correctly (run 13).

No new finding of major severity or above.

## Notes

- **Session 12-1:** its first take ran about 18 minutes while waiting for the user. The ZIP is 3.2 GB and processing took about 2 minutes. It is not uploaded; the local session can be deleted if disk space matters.
- **Sessions 12-1 and 12-2:** left processed and not uploaded.
- **Session 09-1:** its editor was left with "Discard changes", with nothing changed.
- **Questionnaire:** it shows its required-field errors (q1, q3) as soon as it opens, at every size. This is unchanged and is F-065.
- **Screenshots:** the orchestrator's terminal covered two of them; they were retaken or deleted. `step-2-session-12-1-done-no-interruption.png` was deleted because it shows only "Examination complete".
- **Secrets:** no tokens or installation IDs in the saved files. No Settings tree or Settings screenshot was saved.

## Context updates

New sessions, none uploaded:

| Patient | Session ID | State |
|---|---|---|
| `E2E1026-12-1` | `66aeb08e3d3e4deb8a4ef30200efe977` | video, processed, no interruption, 3.2 GB ZIP |
| `E2E1026-12-2` | `9dc2df4f48024666accf3ba9c6f2ef89` | speech, monologue skipped, processed |

`E2E1026-09-2` is now Processed with a ZIP, not uploaded.

## Scripts and logs

- Scripts: `scripts/run-12/check_session.py`, `scripts/run-12/README.md`, and a paragraph added to `scripts/README.md`.
- Logs: `logs/app-redacted-run12.log` (installation ID redacted), `logs/09-2-examination-after-reprocess.json`, `logs/hotrun.log`.
