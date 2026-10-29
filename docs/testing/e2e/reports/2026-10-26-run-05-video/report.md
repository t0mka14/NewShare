# Run 05: Video session

- Date / time: 2026-10-26 19:05-19:12 local   Runner: Sonnet   App commit: `7cd7e47`
- Preconditions met: yes, after a first attempt that was blocked (F-05-1, resolved by the orchestrator). Re-check: `obs64.exe` running, the `OBS Virtual Camera` frame is a live talk-show scene (`screenshots/step-0-obs-frame.png`, viewed), wmplayer looping, volumedetect on CABLE Output mean -22.0 dB, max -13.1 dB.
- Result: passed with findings (all steps done; step 7 added in an addendum, 2026-10-26 late)

Report text saved by the orchestrator from the runner's hand-back. Patients `E2E1026-05-1` (main) and `E2E1026-05-3` (camera lost); clinic A, `E2E 1026 Video protocol`, configVersion `2026-10-26.172809`. The patient form has only the participant code. Settings, Camera: "Automatic (first available camera)" was used; the picker lists `Automatic` and `OBS Virtual Camera`; `examination.json` `cameraUses` shows OBS Virtual Camera, 1280x720@30. Nothing uploaded. App stopped at the end; `app/data` kept.

## Steps
| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Start the video protocol for `E2E1026-05-1` | pass | Picker lists both studies; only a participant code field; calibration Continue works. Counter "Task n/4" (repetitions count as tasks). |
| 2 | Phonation with video 📷 | pass | `step-2-phonation-video.png`: 200x150 live preview. In session 05-3 the preview showed "Starting camera..." and START was disabled until the preview ran (about 5 s), so "Start waits until previewing" holds. One take (13.65 s). |
| 3 | Monologue: rep 1 two takes, rep 2 one take | pass | Take 1 (about 12 s), Retry, take 2 (19.45 s), rep 2 one take (17.9 s). Preview on every filmed screen. |
| 4 | PATAKA without video 📷 | pass | `step-4-pataka-no-video.png`: no preview, plain speech screen; no ffmpeg process running. |
| 5 | Processing and ZIP | pass | "Session processed (4 clips)". ZIP `3b16f256...9413c`, 12 entries, manifest hashes match, manifest last. MP4 + WAV under the same name for phonation, monologue rep 1 (take 2 kept) and rep 2; no MP4 for PATAKA; no `video/*.mjpeg` in the ZIP. `videoTakes[]` lists the four filmed takes that were started (including the rejected monologue take 1), none for PATAKA. Tasks carry `videoFile`, `videoComplete: true`, `videoDurationMs`. `logs/inspect-05-1.txt` |
| 6 | MP4 check 📷 | pass | ffprobe: `mjpeg`, 1280x720, 30 fps, 409 / 586 / 537 frames; durations 13.633 / 19.533 / 17.900 s vs WAV 13.65 / 19.45 / 17.90 s (max difference 0.08 s). Frame at 5 s shows the OBS scene (`step-6-mp4-frame-monologue-rep2.png`, `...-phonation.png`). |
| 7 | No camera at start (`E2E1026-05-2`) | pass with finding | Done by the orchestrator after the user unregistered the OBS DirectShow filter (see addendum). Each filmed screen showed the notice immediately, with no wait: "Camera unavailable — it may be in use by another program." and "Camera not available — recording audio only." (`step-7-no-camera-phonation.png`; the second line is cut off at the bottom of the 200x150 box, same layout as F-106). Recorded phonation, monologue rep 1 and 2 and PATAKA (7 s each); START was enabled and every take worked. Processed: "Session processed (4 clips)" (`step-7-processed-audio-only.png`). ZIP: 4 WAV clips, no MP4, no `videoTakes[]`, no `videoFile`; `hardware` has only `micUses`. Log: WARN `protocol wants video but no camera was found`. See F-05-5. |
| 8 | Camera lost during a take (`E2E1026-05-3`) | pass with findings | Killed only the app's single ffmpeg.exe (parent = the app's java PID). App stayed responsive; timer and audio kept going (to 24 s); screen showed "The camera stopped unexpectedly." and "Camera not available - recording audio only." (F-05-2). Stop kept the take; session processed with the audio intact. ZIP `1560bf6d...b816`: phonation WAV 24.45 s plus a partial MP4 10.9 s (plays), `videoComplete: false`, `videoDurationMs 10900` vs `audioDurationMs 24450`, `hardware.cameraFaults` has `camera / capture_failed`. Later tasks audio-only, no MP4 (F-05-3). Log: one ERROR and two WARNs, no stack trace. `logs/inspect-05-3.txt`, `logs/app-redacted.log` |

## Findings
<a id="f-05-1"></a>

### F-05-1: OBS not running, virtual camera sends only the placeholder (precondition), resolved by orchestrator
- **Severity:** environment note  **Area:** environment  **Run / step:** run 05, precondition
- OBS had not been started; the device sent the OBS placeholder. Resolved by the orchestrator (OBS started with `--startvirtualcam`, crash-recovery dialog cleared); same cause as F-030. The orchestrator's own preflight had only checked that a frame arrived, not what it showed.
- **Status:** resolved

<a id="f-05-2"></a>

### F-05-2: Camera failure messages overlap each other and the frozen last frame in the preview box
- **Severity:** minor  **Area:** desktop-app  **Run / step:** run 05, step 8
- **Expected:** a readable "camera unavailable, recording audio only" notice.
- **Actual:** the two texts are laid out inside the 200x150 preview box over the last camera frame; the first wraps into the second; the frozen last frame stays as if live. Tree nodes `task.videoError` and `task.videoAudioOnly` at y 482 and 552 inside the 150 px box.
- **Repro:** start a filmed take, kill the app's ffmpeg process.
- **Evidence:** `screenshots/step-8-camera-lost.png`.
- **Notes:** related to F-031.
- **Status:** fixed (2026-10-29), confirmed in run 13 (2026-10-29). Cause: `VideoPreview` drew the error and the audio-only texts together inside the 200×150 picture, and `VideoSurface` kept the last decoded frame. Fix: the message sits below the picture and only one shows ("Camera lost — recording audio only." after a lost camera); the frame is cleared to black while the camera has failed and returns when it recovers. Tests: `VideoTaskScreenUiTest` "a camera lost during a VOCAL take shows one message and clears the frame at 944x681 / 984x781", "the preview shows frames again once the camera recovers" (red on the old layout). The preview texts were English-only; fixed as F-132.

<a id="f-05-3"></a>

### F-05-3: After the camera fails, later tasks stay audio-only and there is no way to retry
- **Severity:** question  **Area:** desktop-app  **Run / step:** run 05, step 8
- **Expected:** spec silent on whether a later filmed task retries the camera.
- **Actual:** the failure message stays on the later screens; no "Starting camera..." again, no preview, no MP4 (log: "ignoring video take 1: capture is Failed(...)" twice). Retry task on the failed phonation was not tried. One transient failure silently costs all later video of the session.
- **Evidence:** `logs/app-redacted.log` (19:10:38, 19:10:49), `logs/inspect-05-3.txt`.
- **Status:** open

<a id="f-05-4"></a>

### F-05-4: Filmed clips are large (about 2.7 MB/s)
- **Severity:** question  **Area:** desktop-app / integration  **Run / step:** run 05, step 5
- **Actual:** OBS Virtual Camera rejects MJPEG passthrough ("trying the next mode", then "capturing ... (encoded)"). The 05-1 ZIP is 146 MB for about 50 s of video (MP4s 37, 53, 49 MB; master WAV 11 MB). Matters for run 08.6 and server size limits; the spec allows an encoded fallback.
- **Evidence:** `logs/inspect-05-1.txt`, `logs/app-redacted.log` at 19:07:11.
- **Status:** open

### Duplicates seen again
F-019 (camera picker now exists: fixed), F-030, F-031, F-095.

## Context updates
`2026-10-26-context.json`: two `sessions[]` entries (`E2E1026-05-1`, `E2E1026-05-3`) with ZIP paths, SHA-256 and `hasVideo: true`.

## Notes for later runs
- Processed, not uploaded, with video: `E2E1026-05-1` (`f1b25e4ecd424436ac6753a43ccf09a2`, ZIP 146 MB, 3 MP4) and `E2E1026-05-3` (`d3b229fad6b442b9b171b12cde97fac3`, ZIP 37 MB, 1 partial MP4). With the run-03 and run-04 sessions that is four for run 06. Re-hash at upload time.
- The run-05 sessions have only `patient_code`, so the masking check needs the run-03 session.
- App stopped, `main.pid` removed; wmplayer still looping; OBS still running.
- Step 7 still needs the elevated shell: `Disable-PnpDevice` on `OBS Virtual Camera`, start a session `E2E1026-05-2`, then `Enable-PnpDevice`.
- Killing the capture: `Stop-Process` on the one ffmpeg whose `ParentProcessId` is the app's java PID (from `main.pid`).

## Scripts and logs
`docs/testing/e2e/scripts/run-05/` (check-env.sh, README.md); reused `run-04/start-app.ps1`, `run-02/front.ps1`, `run-03/inspect_zip.py`. Logs: `logs/inspect-05-1.txt`, `logs/inspect-05-3.txt`, `logs/app-redacted.log`, `hotrun.log`.

## Addendum: step 7 (no camera at start)

- The plan's `Disable-PnpDevice` does not work: the OBS Virtual Camera is a DirectShow COM filter, not a PnP device, and the Claude shell is not elevated. The user ran in an elevated shell `Stop-Process -Name obs64` and `regsvr32 /u /s ...\obs-virtualcam-module64.dll`; ffmpeg's dshow list then showed only `CABLE Output`. **The user must re-register it** (`regsvr32 /s ...\obs-virtualcam-module64.dll`) and start OBS again with `obs64.exe --startvirtualcam`.
- Session `E2E1026-05-2`, id `7f82ec98b70a45408dc0697920b3d8ea`, ZIP `app/data/sessions/2026-10-26_E2E1026-05-2_7f82ec98.../archive/E2E1026-05-2_7f82ec98....zip`, 7,267,489 bytes, SHA-256 `e3767edb2b5171af1d959022852a65e7503a55b4cccdc98619d2901651197fde` (re-hash before upload). Processed, not uploaded: the fifth waiting session for run 06.
- Entries: master WAV, 4 clips (`..._0_PHONATION_Rep1`, `_1_MONOLOGUE_Rep1`, `_2_MONOLOGUE_Rep2`, `_3_PATAKA_Rep1`), `examination.json`, `participant.json`, `task_configuration_snapshot.json`, `timeline_original.json`, `manifest.json`.
- Expected "within 10 s the screen says the camera is unavailable": it did, at once.

<a id="f-05-5"></a>

### F-05-5: Nothing in the archive says the camera was missing at start
- **Severity:** question  **Area:** desktop-app / integration  **Run / step:** run 05, step 7
- **Expected:** spec silent. For a protocol that asks for video, the server and the analyst can't tell a session without video by design from one where the camera failed.
- **Actual:** with no camera, `examination.json` has no `videoTakes`, no `videoFile` and no `hardware.cameraFaults`/camera entry; only the app log has `WARN ... protocol wants video but no camera was found`. In the camera-lost case (05-3) the archive does record `cameraFaults` and `videoComplete:false`.
- **Evidence:** `screenshots/step-7-no-camera-phonation.png`; inspection of the ZIP above.
- **Status:** open

## Orchestrator follow-up (2026-10-29 fix round 2)

### F-132: The video preview texts are English only
Index ID: **F-132**
- **Severity:** cosmetic  **Area:** desktop-app  **Run / step:** fix round 2 (F-106 review)
- **Actual:** `VideoPreview` (`VideoTaskBody.kt`) has hard-coded English texts ("Starting camera…", "Camera not available — recording audio only.", "Camera lost — recording audio only.", the camera error descriptions), so a CS examiner sees English on the filmed task screens.
- **Evidence:** code review of `VideoTaskBody.kt`; no `UiLocalization` reaches `VideoPreview`.
- **Status:** fixed (2026-10-29), confirmed in run 13 (2026-10-29). All `VideoPreview` / `VideoTaskBody` texts now come from `BuiltinStrings` (8 keys `task.video.*`, EN + CS) through `UiLocalization`. Tests: `VideoTaskScreenUiTest` "F-132 … is Czech in cs and English in en" (4 tests, red with the English literals).
