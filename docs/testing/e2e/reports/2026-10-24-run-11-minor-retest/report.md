# Run 11: Minor-fix re-test

- Date / time: 2026-10-24, about 22:19–22:52 local   Runner: Sonnet   App commit: `a0ba34c` (`old_ui`)
- Preconditions met: yes (orchestrator preflight: mic privacy Allow, CABLE Output −21.7 dB, OBS
  virtual camera live, server 200, no firewall rule; `app/data` a copy of the run 10 E2E data with
  `micDeviceId` removed).
- Result: passed with findings. F-059 is only partly fixed (F-060). F-029 is a spec-only change
  and had no step.
- Saved by the orchestrator from the text the runner returned, because its Write tool was refused.
  The orchestrator renumbered the runner's run-local IDs:
  - F-11-1 → F-060
  - F-11-2 → F-061
  - F-11-3 → F-062
  - F-11-4 → F-063
  - the extra observations → F-064 and F-065, plus F-046 and F-006 seen again
- Outward-facing steps were approved by the user: one upload (part 9), the Site B token (part 8),
  killing the camera mid-take (part 5), and the server block (part 10). The user ran the firewall
  rule from an elevated shell and removed it afterwards.

## Result per finding

| ID | Result | Evidence |
|---|---|---|
| F-001 | pass | Mic privacy Deny: calibration shows the red "The microphone could not be opened… Windows microphone access… press Retry" with a Retry button. Retry then shows the restart hint. `step-2-deny-calibration.png`, `step-2-retry-hint.png` |
| F-002 | pass | 24 `capture format rejected:` lines (one per candidate), then `startMonitoring failed`, and no `IllegalStateException`. From the first rejection to the failure took 0.34 s; the message was visible in under 1 s. |
| F-005 | pass | Clip `E2E-1023-Site-A_0_PHONATION_Rep1.wav` |
| F-017 | pass | Settings preselects "Primary Sound Capture Driver" with the hint "System default". Leaving Settings does not write `micDeviceId`. See F-062. |
| F-020 | pass | At 800×600, after a scroll, "Save and go back" is 190×40 and reachable. See F-063 for 1000×820. |
| F-022 | pass | The "Skip this task?" dialog (Cancel / Skip) appears; the next screen shows "Previous task skipped". `step-3-skip-confirm.png`, `step-3-skipped-notice.png` |
| F-029 | not covered | Spec-only change; no step |
| F-031 | pass | Phonation screen at 1000×820 and 800×600: indicator 160×160, START and Next full size, and the instructions scroll with a visible scrollbar (about 130 px at 800×600). `step-3-phonation-1000x820.png`, `step-3-phonation-800x600.png` |
| F-032 | pass | The full video session 11-5 has no `hardware.events`, and the log has no "capture ended" or "camera failed" for it. |
| F-036 (app) | pass | Stopping OBS doesn't end the capture (F-030), so the runner killed the app's ffmpeg capture process. Result: one `capture_failed` with detail `capture interrupted: the capture process ended`. It is 46 chars on one line, with no `frame=`, no path and no patient code. `logs/11-6-examination.json` |
| F-038 | pass | Log lines for the upload attempt, upload batch, config refresh (configVersion, site) and check-in (HTTP status), on success and while the server was blocked. No token in `app.log`. |
| F-042 | pass | Site B shows one warning line, "Configuration refreshed, but this computer is registered to another site. Contact your administrator." Site A shows "…Everything looks OK." |
| F-047 | pass | First start: 17 folders → 8 kept, 9 removed (08-1, 08-4–08-8, 10-1, 10-3 ×2). The log lines carry the sessionId only. Back from calibration also deleted 11-1. |
| F-048 | pass | While the server was blocked: "Offline – using the configuration saved on 24. 10. 2026" on the main menu. Gone after the unblock. `step-10-offline-main-menu.png` |
| F-049 | pass | "Upload failed: the server could not be reached. The session is kept and can be uploaded later - press Upload to try again". The session stayed retryable and uploaded on retry after the unblock (attempt 2). `step-10-blocked-upload.png` |
| F-050 | pass | Sessions shows 11-4 as "Processed / Not uploaded" without reopening the screen. `step-3-sessions-processed.png` |
| F-051 | pass | Calibration for 11-2 opened the microphone after Back from 11-1, with no `startMonitoring failed`. |
| F-052 | pass | After the kill, the restart lists "E2E1024-11-7 (24. 10. 2026 22:30)" with OK and Open Sessions. `step-6-recovery-dialog.png` |
| F-053 | pass | 11-7 processed to exactly one clip. |
| F-054 | pass | Killed at 20:31:01.051Z, restarted about 20:31:20Z. The synthetic `STOP_BUTTON_PRESSED` and `SESSION_RECORDING_STOPPED` both read 20:31:01.010Z. `logs/11-7-timeline_original.json` |
| F-057 | pass | Reprocessing zero-clip `E2E1024-10-5` shows "Nothing was recorded in this session, so there is nothing to process." `step-6-reprocess-zero-clip.png` |
| F-059 | partial | The folder name and folder id have 0 hits in `app.log`, but the remux lines carry the clip name: F-060. |

## Notes on the run

- Default window size at start: 1000×820.
- Part 9: the Upload screen has no per-session choice, so the runner moved the other ready
  session folders into the orchestrator scratchpad, uploaded only 11-4, and moved them all back
  in part 11. All 14 folders were moved back, and the scratch folder is empty.
- Part 10: the runner used the no-code SiteName session `875547bd…` instead of a new 11-8. It was
  uploaded on retry after the unblock.
- Part 5: OBS was restarted by clearing `%APPDATA%\obs-studio\.sentinel\run_*` and running
  `obs64.exe --startvirtualcam --minimize-to-tray`. A frame grab afterwards shows the looping
  video.
- Secrets: the runner grepped the run folder for both site tokens and the installation ID. Hits in
  six saved log copies were redacted, and a re-grep shows 0 hits. No Settings screenshots were
  taken.

## Findings

### F-060: Video remux log lines carry the clip name, which holds the patient code and installation ID

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 11, parts 4 and 5
- **Expected:** F-059: no log line contains participant data or the installation ID.
- **Actual:** `FfmpegVideoRemuxService - remuxed task00_rep01_take01.mjpeg -> E2E1024-11-5_<installationId>_0_PHONATION_Rep1.mp4: 251 frames at 30 fps`. That is 3 lines for 11-5 and 1 for 11-6. Clip names come from `recordingsFileName`, so a file name is not automatically safe to log.
- **Evidence:** `logs/F-11-1-remux-log-lines.txt` (redacted)
- **Status:** fixed 2026-10-24 (`5e7abd3`), not yet re-run in the app. The remux line and the remux error messages now name the source take (`taskNN_repNN_takeNN.mjpeg`), never the rendered clip name, and processing logs `video of task N rep R converted`. Test: `ProcessSessionLogPrivacyTest`. It runs processing with the real ffmpeg and asserts that no patient code, installation ID, site name, clip name or ZIP name reaches the log, and it is shown to fail without the fix.

### F-061: The camera-failure text is drawn over the frozen preview and is hard to read

- **Severity:** cosmetic
- **Area:** desktop-app
- **Run / step:** run 11, part 5
- **Actual:**
  - After the camera fails, the preview keeps the last frame.
  - The red "The camera stopped unexpectedly." and the grey "Camera not available — recording
    audio only." are drawn on top of it, and the logo in the frame runs through the text.
  - The overlay stays on the following tasks.
  - A frozen frame next to the message can read as "camera still live".
- **Evidence:** `step-5-ffmpeg-killed.png`, `step-5-next-task-after-failure.png`
- **Notes:** related to F-033.
- **Status:** open

### F-062: A device picked on the calibration screen isn't remembered; the system default can be a silent device

- **Severity:** question
- **Area:** desktop-app
- **Run / step:** run 11, parts 0 and 1
- **Actual:**
  - With no saved microphone, the default is "Primary Sound Capture Driver". On this VM it maps
    to the silent Steam Streaming Microphone, so calibration shows a flat level with no message.
  - Picking CABLE Output on the calibration screen works for that session only. The next session
    starts on the default again.
- **Evidence:** `step-1-calib-11-1.png` (flat), `step-1-calib-11-1-cable.png`
- **Notes:**
  - A `defaultMicName` in the site config avoids the silent default (F-017).
  - Whether a calibration pick should be saved is a product decision.
- **Status:** fixed 2026-10-25, not yet re-run. Decision (user): a calibration pick is saved. `CalibrationComponent.onDeviceSelected` (explicit picks only, not the initial selection) calls a new `onDevicePicked`. `RootComponent` merge-writes `micDeviceId` through `AppSettingsRepository`, as Settings does. Spec: calibration paragraph. Tests: `CalibrationComponentTest` `F-062 an explicit device pick is reported for saving, the initial selection is not`; `RootComponentTest` `F-062 a mic picked on the calibration screen is saved as the default`. A saved mic being used at the next start is covered by the existing `RootComponentTest` case "a saved mic wins over defaultMicName".

### F-063: At 1000×820 "Save and go back" in Settings needs a short scroll

- **Severity:** cosmetic
- **Area:** desktop-app
- **Run / step:** run 11, part 0
- **Actual:**
  - The button is 190×8 until scrolled: 749 px of content in a 717 px viewport.
  - A status line adds 32 px more.
  - F-020 itself, at 800×600, is fixed.
- **Evidence:** semantic tree only (Settings shows the token)
- **Status:** open

### F-064: "1 sessions ready to upload"

- **Severity:** cosmetic
- **Area:** desktop-app
- **Run / step:** run 11, part 10
- **Actual:** the ready-count text uses the plural for one session.
- **Status:** open

### F-065: A fresh questionnaire shows red validation errors before any input

- **Severity:** cosmetic
- **Area:** desktop-app
- **Run / step:** run 11, part 3
- **Actual:** required questions show "Please provide a valid answer." in red as soon as the questionnaire opens, before the examiner has touched anything.
- **Notes:** also visible in the orchestrator's group 1 check (questionnaire after a skipped monologue).
- **Status:** open

Seen again:

- **F-046:** after the blocked upload failed, the progress bar was full and green.
- **F-006:** the no-code SiteName sessions have an empty name in the Upload and Sessions rows.

## Reported by the user after run 11

### F-066: After an edit from Sessions, Accept leaves the session "Processed" with the old ZIP

- **Severity:** major
- **Area:** desktop-app
- **Run / step:** user report 2026-10-24, confirmed in code by the orchestrator
- **Expected:** §8.8 step 1 uses `timeline_edited.json` when it exists; §8.11 says the rows are never stale. An edit should end in clips and a ZIP cut from the edited timeline.
- **Actual:**
  - Sessions → Open editor → move a boundary → Accept returns straight to Sessions; the row still shows "Processed".
  - `RootComponent.buildEditorChild` only pops when `returnToBrowser` is true. `timeline_edited.json` is written, but `examination.json` keeps `Done` / `timelineUsed: "original"` and `archive/*.zip` keeps the old clips.
  - The session stays upload-eligible, so the stale ZIP is what gets uploaded.
- **Repro:** process a session, open it in the editor from Sessions, drag a boundary, press Accept.
- **Status:** fixed 2026-10-24, not yet re-run. Cause: `buildEditorChild` popped back to Sessions without reprocessing. Fix: `EditorComponent.onAccept` reports `needsReprocess`. It is true when the edit on disk changed, or when the last `Done` processing's `timelineUsed` doesn't match the timeline now in effect, which also repairs sessions left stale by the bug. From Sessions, that replaces the editor with Processing (`returnToBrowser = true`). "Open editor" is disabled for `Uploaded` sessions, and the component ignores the action. Spec §8.7, §8.11. Tests: 7 `EditorComponentTest` cases, 1 `SessionBrowserComponentTest`, 2 `RootComponentTest` (all named F-066).

## Context updates

- Sessions added to `reports/2026-10-23-context.json`:
  - 11-4: uploaded.
  - 11-5, 11-6, 11-7 and the F-005 no-code session `174472eb…`: processed, not uploaded.
  - The no-code session `875547bd…`: its first upload failed while the server was blocked; it uploaded on retry.
- Run 11 data is kept in `app/data.e2e-1024-run11`. The user's data is restored to `app/data`.
- End state:
  - app stopped;
  - mic privacy Allow;
  - OBS and the player running;
  - firewall rule removed;
  - server 200.
