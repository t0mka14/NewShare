# Run 16: desktop re-test of fix round 4, group C (criteria 14–26 of [fix-round-2026-10-30-group-c-spec](../../fix-round-2026-10-30-group-c-spec.md))

- **Date / time:** 2026-10-30, about 02:20–02:45 local.
- **Runner:** Sonnet.
- **App commit:** `84b801f` plus the uncommitted round 4 fixes (hotRun of the working tree).
- **Preconditions met:** yes. OBS and wmplayer running, mic privacy `Allow`, token set.
- **Result:** **passed**, with one new minor finding (F-16-1) and one cosmetic question (F-16-2).
  - **Confirmed live:** F-088, F-089, F-065, F-133 (EN and CS), F-127, F-025, F-105, F-028, F-130, F-064 (EN and CS), F-046, F-095.
  - **Older fixes confirmed:** F-034, F-066.
  - **Not verifiable live:** F-109. Unit tests cover it.
  - **Not run:** F-055, F-062, F-134, F-135, F-136.

The orchestrator saved this report from the runner's hand-back. The scripts are the reused ones listed in `scripts/run-16/README.md`.

## Steps

| # | Step | Result | Notes / evidence |
|---|---|---|---|
| 1 | F-088: Settings language options | pass | Radio buttons "Čeština" and "English". `step-1-settings-language.png` (token and installation ID blacked out) |
| 2 | F-089: no-token message centred | pass | See [step 2](#step-2). `step-2-no-token.png` |
| 3a | F-065: a fresh questionnaire shows no error | pass | No red text, no field in error, Next enabled. `step-3-questionnaire-fresh.png` |
| 3b | F-065: Next with required answers missing | pass | Errors on q1 and q3 only (q2 and q4 are optional); it stays on the questionnaire. `step-3-questionnaire-next-pressed.png` |
| 3c | F-065: answering lets Next continue | pass | After answering q3 and q1, Next went to "Examination complete". |
| 3d | F-065: an invalid answer shows only its own error | pass | q1 typed and then cleared (regex `.+`): only q1 shows its error. `step-3-questionnaire-only-q1-error.png` |
| 4a | F-133 EN at 960x720: bounds before and after "Camera lost" | pass | See [step 4](#step-4). `step-4-before-kill-en-960x720.png`, `step-4-camera-lost-en-960x720.png` |
| 4b | F-133 CS at 960x720 | pass | Two lines, not clipped, nothing moved. `step-4-camera-lost-cs-960x720.png` |
| 5a | F-127 at 1512x876 | pass | See [step 5](#step-5). `step-5-calibration-1512x876.png`, `step-5-task-filmed-1512x876.png` |
| 5b | F-127 at 960x720: calibration and filmed task fit | pass | See [step 5](#step-5) and F-16-2. `step-5-calibration-960x720.png`, `step-5-task-filmed-960x720.png` |
| 6a | F-025: repetition in the editor labels | pass | See [step 6](#step-6). |
| 6b | F-105: End and Length are rounded | pass | See [step 6](#step-6). |
| 6c | F-028: the position line follows the boundary | pass | See [step 6](#step-6). `step-6-f028-end-drag.png`, `step-6-f028-start-drag.png` |
| 6d | F-130: editor groups at 960x720, 1000x820 and 1024x768 | pass | See [step 6](#step-6). `step-6-f130-editor-1024x768.png` |
| 7 | F-064: exactly one session ready | pass | EN "1 session ready to upload" (`step-7-upload-one-en.png`), CS "Sezení připravené k odeslání: 1" (`step-7-upload-one-cs.png`); with 8 ready, "8 sessions ready to upload" (`step-7-upload-initial.png`). |
| 8 | F-046: progress bar after a failed upload | pass | The bar is red, not green. See [step 8](#step-8). `step-8-upload-failed-error-colour.png` |
| 9 | F-095: INFO lines for the session life cycle | pass | See [step 9](#step-9). `logs/f095-log-lines-run16-all.txt` |
| 10 | F-109: camera not found at session start | not verifiable live | See [step 10](#step-10). |
| 11 | Older fixes | F-034 pass, F-066 pass; the rest skipped | See [step 11](#step-11). |
| 12 | Regression | pass | See [step 12](#step-12). |

## Step 2

- **Blocking screen:** with a cached config, a removed token doesn't bring up the blocking screen. With the app stopped, the runner ran `set_token.py none` and moved `app/data/config/config.json` aside. The blocking screen then showed at start.
- **Centring:** the message "No site token is set. Open Settings and enter the token from your administrator." spans x 142–841 in a 984 dp window, a centre of 491.5 against 492. The title is centred above it.
- **Restore:** the token was restored with `set_token.py` and `config.json` was copied back. `settings.json` matches the copy taken at the start.

## Step 4

These were measured on the first task (phonation, filmed) at 960x720, a client area of 944x681. The camera was lost by stopping the app's child `ffmpeg.exe` about 4 s into the take.

| Element | Before the kill | After the kill ("Camera lost") |
|---|---|---|
| `task.instructions` | 20,104, 904x214 | 20,104, 904x214 |
| `task.levelIndicator` | 192,337, 160x160 | 192,337, 160x160 |
| `task.videoPreview` | 552,318, 200x150 | 552,318, 200x150 (black) |
| `task.videoAudioOnly` | not present | 552,476, 200x38 |

Nothing moved (0 dp difference). Both messages wrap to two lines and fit the reserved slot without clipping:
- EN: "Camera lost — recording audio only."
- CS: "Kamera byla ztracena — nahrává se pouze zvuk."

The CS run measured the same bounds.

## Step 5

- **1512x876** (client 1496x837):
  - Calibration content spans x 198–1298: 1100 dp wide and centred. The picture (x 490–775) and the level meter (x 847–1007) are 72 dp apart.
  - Task instructions span x 198–1298. `task.content` is 1140 dp wide, including its padding.
  - On the filmed task the meter and the picture share one centred row.
- **960x720:**
  - Calibration fits.
  - The filmed task's title, meter, preview, Start and Next are all visible.
  - "Play example" is cut off at the bottom of the scrollable instruction panel; see F-16-2.

## Step 6

The session was `E2E1030-16-2`: a video protocol with phonation (1 repetition), monologue (2 repetitions) and pataka. The editor was opened from Sessions.

- **F-025:**
  - "Recording 1 of 4 - PHONATION" has a single repetition, so it shows no repetition text.
  - The next label reads "Recording 2 of 4 - MONOLOGUE, repetition 1".
  - The Czech editor label was not checked.
- **F-105:** the readouts were compared with `timeline_original.json` (48000 Hz).

  | Segment | Exact (s) | Shown | Truncation would show |
  |---|---|---|---|
  | 2 start | 58.15 | 0:58.2 | 0:58.1 |
  | 2 end | 65.25 | 1:05.3 | 1:05.2 |
  | 2 length | 7.10 | 7.1 s | |

- **F-028:**
  - End handle: the runner stopped playback at 1:04.9 and dragged the end handle left, to 1:02.3. Position then read 1:02.3, with the red line on the end boundary.
  - Start handle: playback was at 0:59.3 and the start was dragged to 1:01.1. Position then read 1:01.1, with the line on the start.
- **F-130:** in each window, Previous and Next share one row. The groups are `editor.navigationGroup` and `editor.actionsGroup`.

  | Window (client) | Previous / Next | Gap between the groups |
  |---|---|---|
  | 960x720 (944x681) | one row | 8 dp (stacked) |
  | 1000x820 (984x781) | one row | 8 dp (stacked) |
  | 1024x768 (1008x729) | one row | 8 dp (stacked; both groups need 950 dp against 944) |

- **Accept and Discard:**
  - "Discard edits?" works both ways: Cancel keeps editing, Discard returns with the session unchanged.
  - Accept after a drag rebuilt the ZIP with `timeline_edited.json` and set `timelineUsed: edited`. The screen showed "Session … processed (4 clips)."

## Step 8

- **Methods that didn't fail the upload:**
  - A renamed archive ZIP was refused locally, but the screen said "Files successfully uploaded." That is F-16-1.
  - With the token removed in Settings, the upload still succeeded, because the device is already registered. This was a normal upload of 16-1 to Clinic A.
- **What made it fail:**
  - The runner reset that session's `upload_status.json` to `{}`, appended one byte to its ZIP, and pressed Upload.
  - The server answered 409 (conflict). The log reads `outcome=Conflict retry=none` and `upload batch: 1 session(s), 0 ok, 1 failed`.
  - The row reads "Upload failed: the session is already on the server with different content; contact your administrator".
  - The bar shows 1.0 and is **red**.
- **Restore:** the original ZIP and `upload_status.json` (Uploaded) were put back from copies.
- **Server effect:** one normal upload of 16-1 and one rejected re-upload attempt.

## Step 9

Session `E2E1030-16-1` was a speech protocol run in EN: 2 phonation takes, pataka, reading, a skipped monologue and a questionnaire. These INFO lines appeared, with the session ID only:

```
session created session=<sessionId> tasks=6
take started session=<sessionId> task=1/6 VocalTask rep=1 take=1
take stopped session=<sessionId> task=1/6 VocalTask rep=1 take=1
task skipped session=<sessionId> task=5/6 VocalTask rep=1
session finished session=<sessionId> tasks=6
processing started (session <sessionId>)
processing done: 4 clips (session <sessionId>)
```

- **Coverage:** the extract holds 39 lines over three sessions. None contains a patient code, a path or a device name.
- **Full log:** `logs/app-run16-redacted.log`, with session IDs as `<sessionId>`, patient codes as `<patient>` and the installation ID as `<installationId>`.
- **Device name in an older line:** the existing "microphone opened" line carries the microphone's name. It is not one of the F-095 lines.

## Step 10

- **No camera can be hidden:** `SessionComponent.bootstrap` takes the saved camera only if it is in the device list, and otherwise falls back to the first eligible camera. So a wrong ID in `settings.json` still picks OBS Virtual Camera, and Settings offers only "Automatic" and "OBS Virtual Camera".
- **Not tried:** unregistering the OBS DirectShow filter (it needs admin rights and the user) and changing the launcher's ffmpeg path.
- **What was checked:** `hardware.events` of 16-2 and 16-3 hold only the `camera`/`capture_failed` event from the kill and no `unavailable_at_start`, which is right when a camera is found.

## Step 11

- **F-034: pass.** On 16-2, with the camera killed in take 1, the phonation task has `videoComplete: false`. Its `videoDurationMs` is 10066, matching ffprobe's 10.067 s for the MP4, and its `audioDurationMs` is 26145, matching ffprobe's 26.145 s for the WAV. The later tasks are audio-only and carry none of these fields. 16-3 is the same.
- **F-066: pass.** Accept in the editor, opened from Sessions, rebuilt the ZIP and set the status to Done, with `timelineUsed: edited`.
- **Skipped:**
  - F-055 needs a crash and the recovery dialog.
  - F-062 needs a second microphone, and only CABLE exists.
  - F-134 to F-136 need a real device interruption; in run 13 that took the user stopping `Audiosrv`.

## Step 12

- **Checked and working:**
  - the questionnaire flow;
  - editor Accept and Discard;
  - the main menu at 1000x820 and 960x720;
  - the session browser at 960x720 (`step-12-sessions-960x720.png`);
  - the Upload screen with 8 sessions.
- **Screenshot retaken:** one screenshot taken while a terminal covered the window was deleted and retaken after `front.ps1`.

## New findings

### F-16-1: A missing archive makes the Upload screen say "Files successfully uploaded."
Index ID: **F-141**
- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 16, step 8
- **Expected:** when a session in the batch isn't uploaded, including when it is refused locally, the screen shows no success message, and the session is reported as failed.
- **Actual:**
  - `UploadSessionUseCase` answered `Refused("no processing archive ...")` for a session whose archive ZIP had been renamed.
  - `UploadComponent.onUploadClicked` reloaded the rows. The row was gone, because eligible rows need an archive, so `allUploaded = rows.isEmpty()` held.
  - The screen showed the green "Files successfully uploaded.", a full green bar and "No sessions are ready to upload."
  - The log says `upload batch: 1 session(s), 0 ok, 1 failed`. Nothing was uploaded.
- **Repro:** process a session, rename or delete its `archive/*.zip`, open Upload, and press Upload.
- **Evidence:** `screenshots/step-8-upload-missing-zip-cs.png` (Czech UI), `logs/app-run16-redacted.log`.
- **Notes:** this needs a missing file, which is unlikely without manual tampering. Possible fix: base the message on succeeded == total, not on "no rows left".
- **Status:** open

### F-16-2: "Play example" is cut off in the scrollable instruction panel at 960x720 on a filmed task
Index ID: **F-142**
- **Severity:** question
- **Area:** desktop-app
- **Run / step:** run 16, step 5
- **Expected:** to be decided. Is a half-visible button in the scrolling instruction panel acceptable at the minimum window size?
- **Actual:**
  - On a filmed VOCAL task at 960x720, the instruction panel is 214 dp high.
  - "Play example" shows 36 of its 51 dp, and the "Start now" block is below the fold.
  - The panel has a scrollbar.
  - CS ("Přehrát ukázku") is the same.
- **Repro:** start a video protocol, go to task 1, and resize the window to 960x720.
- **Evidence:** `screenshots/step-5-task-filmed-960x720.png`.
- **Notes:** this is probably intended, since the panel scrolls. The F-133 reserved slot takes some of the height.
- **Status:** open

## Seen again

- **F-107:** "Camera lost" and audio-only recording continue on the later tasks of the session. The partial MP4 stays, by design (F-034).
- **Stale tree:** right after a screen change, `get_semantic_tree` and `take_screenshot` can briefly show the previous screen. This is a test-tool effect, also noted in run 13.
- **Main-menu selector:** it still shows "cs" / "en" with flags. F-088 covers Settings only.

## State left

- **App:** stopped, with `main.pid` removed.
  - `settings.json` equals the copy taken at the start: token present, language `en`.
  - `config.json` was restored and then refreshed by the app.
- **Machine:** OBS and wmplayer are running. Mic privacy was not touched. No stray `ffmpeg.exe` is running. The window was set back to 1000x820 before the stop.
- **Sessions:** the older sessions were moved out during step 7 and then back. There are 16 folders now:
  - `E2E1030-16-1` is processed and **uploaded to Clinic A**; the server also saw one rejected re-upload (409).
  - `E2E1030-16-2` is processed twice (Accept in the editor) and not uploaded.
  - `E2E1030-16-3` (CS) is processed and not uploaded.
- **Server:** only the upload of 16-1 and the rejected attempt. Nothing was deleted.
- **Not touched:** `app/src`, `app/fake-sessions-root/` and `.env`. Nothing was committed.
