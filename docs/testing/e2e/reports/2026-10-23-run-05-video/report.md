# Run 05: Video session

- Date / time: 2026-10-24 00:20–00:31 local   Runner: Sonnet   App commit: `59bb063`
- Preconditions met: yes. OBS virtual camera on with a video scene, player looping, window
  1000×820.
- Result: passed with findings. Steps 7 and 8 could not run as written (F-030). The mid-take loss
  was covered with a substitute: killing the app's own ffmpeg capture process.

The orchestrator saved this report from the runner's returned text and renumbered the
findings. It also redacted the installation ID from `logs/hotrun-run05.log` (9 occurrences in
clip paths). After the run, the orchestrator grabbed a frame with ffmpeg and checked that the
virtual camera shows the live scene again.

Sessions, all three processed and not uploaded:

| Patient | Session ID |
|---|---|
| `E2E1023-05-1` | `c54d0d83b29245cbbc08c44ae830a633` |
| `E2E1023-05-2` | `edb79f0539504d788ae1675b279b76bb` |
| `E2E1023-05-3` | `fbd7b3f6eafe46b58e016769f47cc325` |

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Start the video protocol for `E2E1023-05-1` | pass | As in runs 03 and 04. Calibration was below the band (F-021). `step-1-calibration.png` |
| 2 | Phonation with video 📷 | pass with finding | The live preview shows the scene. The camera opened at 00:21:13; OBS rejected mjpeg passthrough, so frames are encoded to mjpeg. Recording started at 00:21:21. One take, 8.5 s. The preview and indicator are squeezed into a 23 px strip (F-031). The indicator didn't visibly change (F-024). The first Start was pressed about 14 s after the screen opened, so a refused Start was never seen. `step-2-phonation-ready.png`, `-recording.png`, `-after-stop.png` |
| 3 | Monologue with video: rep 1 with 2 takes, rep 2 with 1 take | pass | Retry started take 2 at once (F-023). The preview was 200×84 on rep 1 and 200×150 on rep 2. One camera session for the whole examination. `step-3-*.png` |
| 4 | PATAKA without video | pass | No preview, no camera widget, the plain speech screen. The indicator ring overlaps the bottom of the instruction card (the layout problem in F-031). No video file. `step-4-pataka-no-video.png` |
| 5 | Processing and ZIP check | pass with finding | Details below. Accept on the **first** of 4 editor segments ended the session (added to F-027). A `capture_failed` event was recorded at a normal camera close (F-032). `step-5-examination-complete.png` |
| 6 | MP4 check | pass | All MP4s: codec `mjpeg`, 1280×720, 30 fps. The frame grab shows the OBS scene, not black. `step-6-mp4-frame-monologue-rep1.png` |
| 7 | Camera off before session `-05-2` 📷 | not testable as written (F-030) | A stopped OBS virtual camera still serves a placeholder image (OBS logo and a camera-off icon). The app previewed and filmed it, and showed no "camera unavailable" message at 3 s or at 15 s. All 3 filmed tasks have placeholder MP4s and `videoTakes`. So the audio-only start path was **not exercised**. The session was finished and processed. `step-7-phonation-camera-off-t3s.png`, `-t15s.png`, `step-7-monologue-camera-off.png`, `step-7-examination-complete.png` |
| 8A | OBS camera stopped mid-take (`-05-3`, monologue rep 1) | not testable as written (F-030) | The take kept recording the placeholder, with no message. After the virtual camera was started again, the next screen showed the real scene: the capture picked it up again. `step-8-obs-stopped-still-recording.png` |
| 8B | Simulated loss: kill the app's ffmpeg capture 5 s into monologue rep 2 | pass with findings | Within about 2 s: red "The camera stopped unexpectedly." (`task.videoError`) and "Camera not available — recording audio only." (`task.videoAudioOnly`). The timer kept running, and Stop, Retry and Next worked. The audio take was kept (WAV 12.2 s). The video ends at the loss (MP4 5.23 s, 157 frames). No crash, and the session processed. Log: `ERROR capture ended while Recording`, `WARN camera failed: CaptureInterrupted`. F-033, F-034. `step-8-after-capture-killed.png`, `step-8-after-stop.png`, `step-8-sessions-list.png`, `step-8-and-7-mp4-frames-grid.png` |

### Step 5: ZIP of `-05-1`

- `archive/E2E1023-05-1_c54d0d83b29245cbbc08c44ae830a633.zip`, 132,913,303 bytes, SHA-256
  `894254a6ddd217291dfdd9e34e1f25bc9998177d853eb286969bcef4d1fbaf26`.
- Entries:
  - the master WAV,
  - 4 WAVs,
  - 3 MP4s (PHONATION_Rep1, MONOLOGUE_Rep1, MONOLOGUE_Rep2), each with the same base name as its
    WAV,
  - `examination.json`, `participant.json`, `task_configuration_snapshot.json`,
    `timeline_original.json`,
  - `manifest.json`.
- There is no MP4 for PATAKA and no `video/*.mjpeg` entry. The 4 raw takes are on disk only.
- The manifest has 12 entries and every SHA-256 matches. The session ID is the same in the
  manifest, `examination.json` and the folder name.
- `videoTakes[]` has 4 entries: phonation, monologue rep 1 takes 1 and 2, and monologue rep 2.
  The rejected take is included, as the spec requires ("every filmed take that was started").
  Each has `captureFormat` 1280×720@30. There is none for PATAKA.
- The monologue rep 1 MP4 is take 2.
- `videoFile` is set on the 3 filmed tasks.
- MP4 vs WAV durations: 8.53/8.5 s, 19.30/19.2 s, 16.80/16.7 s.
- `hardware.cameraUses` is OBS Virtual Camera, `dshow:0`.
- `-05-2`: 7.8 MB, 3 placeholder MP4s, 3 `videoTakes`, manifest matches.
- `-05-3`: 66 MB, manifest matches, no mjpeg entries.

## Findings

Repeats, cited and not filed again:

- F-018: `app.log` is still empty.
- F-019: the camera was chosen by fallback, as expected.
- F-021: calibration below the band.
- F-023: Retry starts the take at once.
- F-024: the indicator didn't visibly change.
- F-026: the first session had no processing screen. For `-05-2` and `-05-3` a "Processing /
  Finishing up…" screen showed briefly.
- F-027: Accept on a middle segment ends the editor.

### F-030: Stopping the OBS virtual camera doesn't make the camera unavailable, so the audio-only fallback can't be tested this way

- **Severity:** question
- **Area:** environment
- **Run / step:** run 05, steps 7 and 8
- **Expected:** plan steps 7 and 8 assume that "Stop Virtual Camera" removes the device or stops its
  frames, so the app reports the camera unavailable within 10 s (§8.9) and records audio only.
- **Actual:** the stopped OBS virtual camera still exists as a DirectShow device and serves a
  placeholder image. The app can't tell that from a real picture, so it previews and films it, with
  no message. Stopping it mid-take doesn't interrupt the take either.
- **Repro:**
  1. Stop the OBS virtual camera.
  2. `ffmpeg -f dshow -i "video=OBS Virtual Camera" -frames:v 1 x.png` returns the placeholder.
  3. Start the video protocol.
- **Evidence:** `screenshots/step-7-phonation-camera-off-t15s.png`, `step-7-monologue-camera-off.png`,
  `step-8-obs-stopped-still-recording.png`, `step-8-and-7-mp4-frames-grid.png`.
- **Notes:**
  - Probably correct app behaviour. It is a test-method gap.
  - The **audio-only start path** (no camera, failed, or not previewing after 10 s) is untested in
    the real app.
  - The mid-take loss was covered by killing the capture process (step 8B).
  - Ways to cover the start path: disable the device (needs admin) or unregister the OBS DirectShow
    filter, or test with a real USB camera unplugged. Both are left for the user to decide. Plan
    §6 run 05 needs updating either way.
- **Status:** open

### F-031: On the phonation screen, the camera preview and level indicator are squeezed into a 23 px strip

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 05, step 2
- **Expected:** a 200×150 preview beside the indicator (spec, video task layout, decision 60).
- **Actual:**
  - At 1000×820, the long instruction text and the example button leave only 23 px for the middle
    area. `task.videoPreview` is 200×23 and `task.levelIndicator` is 160×23, so the preview shows
    a thin stripe and the indicator ring overlaps the "Start now" card.
  - The monologue gets 200×84 (rep 1) and 200×150 (rep 2). PATAKA's ring also overlaps its card.
  - The examiner can't check that the participant is in frame.
- **Repro:** open the video protocol's phonation screen at 1000×820.
- **Evidence:** `screenshots/step-2-phonation-ready.png`, `step-2-phonation-recording.png`,
  `step-3-monologue-ready.png`, `step-4-pataka-no-video.png`.
- **Notes:** compare at a taller window. The text card probably doesn't scroll and takes the space
  instead.
- **Status:** fixed 2026-10-24 (`60e82bf`), not yet re-run.
  - **Cause:** the middle area had `weight(1f)` and the instruction cards had none, so the cards took all the height they wanted.
  - **Fix:**
    - The instruction column takes the height it needs and scrolls, with a visible scrollbar, only when the window is short. It always keeps at least 110 dp.
    - The middle area sizes to its content: 160 dp, or 250 dp for the WAVEFORM indicator.
    - The default window is 1000×820.
  - **Checked in the app with the OBS camera:**
    - at 800×600 the instructions are readable with a scrollbar, the preview is 200×150 and the indicator 160×160;
    - at 1000×820 the scrollbar shows that the "Start now" card continues;
    - the waveform after Stop fits.
  - **Tests:** `TaskLayoutUiTest`, 6 tests, shown to fail without the fix.

### F-032: A normal camera close is recorded as `capture_failed`

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 05, step 5 (also in `-05-2` and `-05-3`)
- **Expected:** when the session leaves its last video task, the app closes the camera quietly.
  `examination.json.hardware.events` lists only real failures.
- **Actual:** about 5 s after the last filmed take, the log shows
  `ERROR … capture ended while Previewing` and `WARN camera failed`. `hardware.events` gets
  `{device: camera, event: capture_failed, detail: CaptureInterrupted(…)}`. The detail ends with
  `[q] command received. Exiting.`, so the app itself told ffmpeg to quit. A perfect session
  carries a camera failure, and in the data it can't be told apart from the real failure in 8B.
- **Repro:** record a filmed task, go Next to a non-video task, wait about 5 s. Check the log and
  `hardware.events`.
- **Evidence:** `logs/hotrun-run05.log` (00:22:42, 00:26:17). `examination.json` of `-05-1`
  (event at 22:22:42Z). No screenshot: the UI doesn't show it.
- **Notes:** the intentional close probably runs through the unexpected-end path.
- **Status:** fixed 2026-10-24 (`8aaa7c1`), not yet re-run. Cause: `stop()` sent `q`, and the reader thread saw the pipe end while the state was still Previewing, so it reported Failed. Fix: a closing flag is set before `q`, so an intentional close records no event and logs no ERROR or WARN. Checked in the app: after a full video session (OBS camera), `hardware.events` is empty and there are no "capture ended" or "camera failed" lines. Tests: `FfmpegSessionVideoRecorderTest` "closing the camera on purpose never passes through Failed" (shown to fail without the fix) and `SessionComponentTest`.

### F-033: The "camera stopped" messages are drawn over the frozen last frame and are hard to read

- **Severity:** cosmetic
- **Area:** desktop-app
- **Run / step:** run 05, step 8B
- **Actual:** the red and the gray message sit on the last video frame inside the 200×150 preview.
  They wrap, and the second line is cut off at the bottom.
- **Evidence:** `screenshots/step-8-after-capture-killed.png`, `step-8-after-stop.png`
- **Status:** open

### F-034: A partially filmed take isn't marked as partial in the data

- **Severity:** question
- **Area:** desktop-app
- **Run / step:** run 05, step 8B
- **Expected:** §8.9: a mid-take camera failure stops only the video, and the take is kept. The
  spec doesn't say how the incomplete video is marked.
- **Actual:**
  - The 12.2 s audio take has a 5.23 s MP4, included in the ZIP with `videoFile` set.
  - Nothing in `examination.json` marks the video as partial. The `capture_failed` event is the
    same one a normal close produces (F-032).
  - An analyst can only tell by comparing durations.
- **Evidence:** the session's `examination.json` and `clips/*MONOLOGUE_Rep2.*` (in the
  orchestrator's scratchpad copy).
- **Status:** fixed 2026-10-25, not yet re-run. Decision (user): add `videoComplete` and the durations. Each task record with a `videoFile` now gets three fields:
  - `videoComplete`: false iff a `camera`/`capture_failed` entry in `hardware.events` falls inside the kept take's START..STOP wall-clock range (since F-032 a normal close writes none; durations are never compared).
  - `videoDurationMs`: the remux's verified frame count / fps.
  - `audioDurationMs`: clip source samples / rate.
  
  An audio-only fallback gets none of them, and old files still load. Spec §8.8 step 5, `docs/dev/upload_data.md`. Tests: 5 `ProcessSessionUseCaseTest` cases (F-034).

## Context updates

- `sessions[]`: the three sessions with `hasVideo: true`, processed, not uploaded, folder, ZIP and
  SHA-256. Notes say `-05-2` was filmed with the placeholder and `-05-3` had the simulated loss.
- `app.camera`: the capture and codec path, the placeholder behaviour, how to toggle OBS (client
  coordinates; click once to focus first), how to find the app's ffmpeg PID.
- `environment.zipsScratch`: the ZIP copies are in the orchestrator scratchpad.

## Notes for later runs

- **Large ZIPs:** `-05-1` is 133 MB and `-05-3` is 66 MB, because OBS frames are encoded to mjpeg
  at about 22 Mbit/s. That's relevant to the upload in run 06; the server's size limit is untested.
- **Screenshots:** `take_screenshot` captures screen pixels. If another window covers SHARE, the
  image shows that window. Click (5,5) in the SHARE window with `Drag-InWindow.ps1` first.
- **OBS clicks:** the first real click after SHARE had focus may only focus OBS. Check the result
  with `Capture-Screen.ps1`.
