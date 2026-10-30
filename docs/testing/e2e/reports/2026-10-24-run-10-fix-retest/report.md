# Run 10: Fix re-test (F-051, F-001, F-052, F-053, F-005)

- Date / time: 2026-10-24, about 08:00–08:20 local   Runner: Sonnet   App commit: `59bb063` +
  uncommitted fixes
- Preconditions met: yes (orchestrator preflight: mic privacy Allow, CABLE Output −22.7 dB, OBS
  and player running, server 200; `app/data` a copy of `app/data.e2e-1023-final`)
- Result: passed with findings. All five fixes confirmed.
- Saved by the orchestrator from the text the runner returned (its Write tool was refused, §1.1).
  The orchestrator renumbered the run-local finding IDs: F-10-1 → F-055, F-10-2 → F-056,
  F-10-3 → duplicate of F-054, F-10-4 → F-057, F-10-5 → F-058 (+ F-006 seen again).
- Notes:
  - `main.pid` is a properties file; the PID is on its `pid=` line.
  - The level meter has no text value in the semantic tree, so "the level moves" was judged from
    successive screenshots.
  - Per-start logs: `hotrun.log` (part A), `hotrun-B.log`, `hotrun-B2.log`, `hotrun-C.log`,
    `hotrun-C2.log` … `hotrun-C6.log`, `hotrun-D.log`.

| Fix | Result |
|---|---|
| F-051 | pass |
| F-001 | pass (Retry in the same JVM after Allow still fails; a restart fixes it, as the plan allows) |
| F-052 | pass |
| F-053 | pass (one-clip and zero-clip cases) |
| F-005 | pass |

## Steps

### Part A (F-051)

| # | Step | Result | Notes / finding |
|---|---|---|---|
| A1 | Speech protocol, `E2E1024-10-1`, calibration; level moves 📷 | pass | The bar moved between screenshots, about 0.08 to 0.06. That is below the green band, as expected at −22.7 dB. `screenshots/A1-calibration-10-1.png`. "microphone opened" at 08:01:25 |
| A2a | Back, Speech protocol again, `E2E1024-10-2`, calibration; level moves 📷 | pass | `screenshots/A2-calibration-10-2.png`. Second "microphone opened" at 08:01:53 |
| A2b | No `startMonitoring failed` after A1's "microphone opened" | pass | No match in `app.log` or `hotrun.log` |
| A3 | Continue, first task, one take, then leave and restart | pass | Take started and stopped, and Continue was enabled |

### Part B (F-001)

| # | Step | Result | Notes / finding |
|---|---|---|---|
| B1 | Mic privacy `Deny`, start | pass | Registry value read back as `Deny` |
| B2a | `E2E1024-10-3`, calibration: message, Retry, Continue disabled 📷 | pass | Message: "The microphone could not be opened. Check that it is connected and that Windows microphone access ("Let apps access your microphone") is allowed, then press Retry or choose another device." Retry is shown; Continue is `enabled:false`. `screenshots/B2-calibration-mic-denied.png` |
| B2b | `startMonitoring failed` WARN; no `IllegalStateException` or "unhandled recoverable error" | pass | WARN at 08:03:16 (`DeviceUnavailable`, caused by `LineUnavailableException`). Neither of the other two in part B. F-056 is an unrelated line in part C |
| B3 | Retry while still Denied 📷 | pass | Second WARN at 08:03:23; same message, no crash or dialog. `screenshots/B3-retry-still-denied.png` |
| B4a | Allow, Retry in the same JVM | info | Still fails: third WARN at 08:03:35, the message stays, Continue is disabled. `screenshots/B4-retry-after-allow-same-jvm.png` |
| B4b | Restart, calibration: level moves, Continue enabled 📷 | pass | "microphone opened" at 08:04:17. `screenshots/B4-calibration-after-restart-allow.png` |
| B5 | Privacy ends as `Allow` | pass | Re-read at the end of the run |

### Part C (F-052, F-053)

| # | Step | Result | Notes / finding |
|---|---|---|---|
| C1 | `E2E1024-10-4`, calibration, Continue, Start, kill about 3 s in | pass | Take started at 06:05:03.35Z (sample 201600). Killed at 06:05:06.50Z |
| C2 | Files before restart | pass | `master/session_master.partial.wav` (715244 bytes) and `timeline.events.jsonl`. `logs/C-10-4-timeline.events.before-restart.jsonl` |
| C3a | Recovery dialog lists 10-4 with its start time, "Open Sessions" and "OK" 📷 | pass | "Interrupted sessions recovered"; "E2E1024-10-4 (24. 10. 2026 08:04)". `screenshots/C3-recovery-dialog-10-4.png` |
| C3b | `recovered interrupted session: sessionId=…` with no folder name or patient code | pass | Logs `sessionId=…` only. No `E2E1024` anywhere in the app log |
| C4a | `STOP_BUTTON_PRESSED` with `reason: "RECOVERED_INTERRUPTED"` at the same sampleOffset as the synthetic `SESSION_RECORDING_STOPPED` | pass | Both at 357600. `logs/C4-10-4-timeline_original.json`. Their `wallClock` is the recovery time: F-054 seen again |
| C4b | `examination.json`: `recovered: true` and a `tasks[]` record | pass | One PHONATION record (rep 1, 1 take). `logs/C4-10-4-examination.json` |
| C4c | Master finalized, no `.partial.wav` | pass | `session_master.wav`, 715244 bytes, the same size as the partial |
| C5 | "Open Sessions": 10-4 shows Recovered; no dialog back at the menu 📷 | pass | `screenshots/C5-sessions-recovered.png` |
| C6a | Reprocess 10-4: one clip about the take's length | pass | `clips/E2E1024-10-4_<installationId>_0_PHONATION_Rep1.wav`. ffprobe 3.250 s (357600 − 201600 = 156000 samples). Sessions kept "Not processed" until the restart (F-050) |
| C6b | `manifest.json` lists the clip | pass | ZIP holds the master, the clip, `examination.json`, `participant.json`, `task_configuration_snapshot.json`, `timeline_original.json` and `manifest.json` |
| C6c | Upload screen offers 10-4; not uploaded 📷 | pass | "2 sessions ready to upload": `E2E1023-08-5b` (left from run 08) and `E2E1024-10-4`. `screenshots/C6-upload-offers-10-4.png` |
| C7a | Zero-clip `E2E1024-10-5`: kill before Start, restart, dialog lists 10-5 📷 | pass on the 2nd attempt | The first attempt showed no dialog, probably dismissed by the runner's click at (5, 5): F-055. The repeat, with no click, listed "E2E1024-10-5 (24. 10. 2026 08:09)". `screenshots/C7-recovery-dialog-10-5.png` |
| C7b | Process 10-5: fails, no ZIP 📷 | pass | "Processing failed – This session could not be processed. You can retry or go back." The 10-5 folder has no `archive/`. `screenshots/C7-process-10-5-zero-clip.png`. F-057 |
| C7c | 10-5 not on the Upload screen 📷 | pass | `screenshots/C7-upload-no-10-5.png` |

### Part D (F-005)

| # | Step | Result | Notes / finding |
|---|---|---|---|
| D1 | Web: `E2E 1024 SiteName protocol` in `E2E 1023 Speech project`, default template, Phonation (5 s, 1 rep), EN 📷 | pass | The runner logged in once through the browser form. Project 13 was already assigned to Site A. **Default template: `${siteName}_${taskIndex}_${task.subtype}_Rep${repetition}`**; the web's example is `CLINIC-01_03_PHONATION_Rep1.wav`. Screenshots: `D1-protocol-editor-default-template.png`, `D1-phonation-modal.png`, `D1-after-save.png`. F-058 |
| D2 | `GET /api/site-config/<token A>` | pass | `configVersion` 2026-10-24.061507. The new protocol has the default template, one `VOCAL/PHONATION` task and `patientFields: []`. `logs/D2-site-config-A.json` (redacted) |
| D3 | Settings → Refresh configuration: config accepted, protocol in the menu 📷 | pass | "Configuration refreshed. Everything looks OK." The template uses only supported variables. `screenshots/D3-picker-new-protocol.png`. No Settings screenshot, because it shows the token |
| D4 | Run the protocol and process it; clip names checked | pass | No patient fields, so no patient form and no `E2E1024-10-6` (F-006). One 9.4 s take. Clip `E2E-1023-Site-A_0_PHONATION_Rep1.wav`, with no `${`. `processing.status` is Done. `logs/D4-clips-listing.txt`. Not uploaded |

## Findings

### F-055: The crash-recovery dialog closes when the examiner clicks outside it

- **Severity:** question
- **Area:** desktop-app
- **Run / step:** run 10, part C step 7
- **Expected:** the examiner reads the notice and presses "OK" or "Open Sessions".
- **Actual:**
  - Not confirmed, but likely.
  - On the first zero-clip restart (10-5) no dialog was shown, although `app.log` had the
    "recovered" line.
  - The runner had clicked at (5, 5) in the window, outside the dialog, to bring the window to
    the front.
  - On the repeat, with no click, the dialog was there.
- **Repro:** kill the JVM during a session, start the app, then click the dimmed area outside the
  dialog. The dialog is gone for the rest of the run.
- **Evidence:** `screenshots/C7-recovery-dialog-10-5.png`.
- **Notes:**
  - The dialog's `onDismissRequest` calls the same dismiss as "OK".
  - Sessions still shows the Recovered tag.
  - Whether an outside click should dismiss the dialog is a product choice.
- **Status:** fixed 2026-10-25, not yet re-run. Decision (user): only the buttons close the dialog. `RecoveryDialog` in `MainMenuContent.kt` now has a no-op `onDismissRequest`, the same pattern as `DeviceLostDialog`, plus `DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)`. Spec §8.4 item 2. Tests: `RecoveryDialogUiTest`: `F-055 Esc and an outside click leave the recovery dialog open` fails on the old code; the outside-click half is weaker evidence. `F-055 OK closes the recovery dialog`.

### F-056: A hot-reload window resize can log an "unhandled recoverable error"

- **Severity:** question
- **Area:** environment
- **Run / step:** run 10, part C step 3 (08:05:40)
- **Expected:** no ERROR line unless the app has a problem.
- **Actual:**
  - `app.log` has one line: `ErrorReporter - unhandled recoverable error: IllegalStateException: Reentry into ignoringRedrawRequests is not allowed`.
  - The stack goes through `org.jetbrains.compose.reload.jvm.WindowResizeHandlerKt.handleWindowResizeRequest`,
    which is the `resize_window` call made right after the start.
  - Seen once. It didn't happen in part B.
- **Repro:** start the app and call `resize_window` immediately. It happens only sometimes.
- **Evidence:** `logs/all-app-log-excerpt.log`, line 61.
- **Notes:** test tooling, not app code. It can confuse log-based checks such as F-001's.
- **Status:** closed, environment (2026-10-29). Only the hot-reload development tool logs it; not the shipped app.

### F-057: The zero-clip processing failure shows a generic message and writes no log line

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 10, part C step 7
- **Expected:** the examiner is told why ("nothing was recorded to process"), and the log has a
  line for it.
- **Actual:**
  - The screen says "This session could not be processed. You can retry or go back."
  - Retry fails the same way.
  - `app.log` has no line for it.
  - The F-053 behaviour itself is correct: there is no ZIP, and the session isn't uploadable.
- **Repro:** kill the app before the first Start, restart, then Sessions → Reprocess.
- **Evidence:** `screenshots/C7-process-10-5-zero-clip.png`.
- **Notes:**
  - `ProcessingError.NothingToProcess` is not mapped to its own message.
  - Same family as F-038 (no logging) and F-049 (terse errors).
- **Status:** fixed 2026-10-24 (`11b365a`), not yet re-run. Fix: `NothingToProcess` shows its own message, "Nothing was recorded in this session, so there is nothing to process." Processing failures are logged at WARN with the error type and the session id (no folder name or patient code). Tests: `ProcessSessionUseCaseTest` (logging) and two `ProcessingComponentTest` cases. Checked in the app.

### F-058: Web "Create Protocol" opens `.../protocols/undefined`

- **Severity:** cosmetic
- **Area:** web-ui
- **Run / step:** run 10, part D step 1
- **Actual:**
  - After "Create Protocol" the URL is `#/admin/projects/13/protocols/undefined`.
  - The editor works, and nothing is saved until "Save protocol".
- **Evidence:** `screenshots/D1-after-save.png`.
- **Notes:**
  - The new protocol also has no patient identifiers (F-006, seen again).
  - App-side effect of that: `patientFields: []`, so there is no patient form.
  - The session folder is `2026-10-24__<sessionId>`, and the ZIP is `_<sessionId>.zip`.
- **Status:** closed, gone in the current code (code check 2026-10-29). `ProtocolEditorPage.jsx` and `NewProtocolModal.jsx` never navigate to `/protocols/undefined`; run 14 used Create and edit.

### F-059: The video recorder logs the full session path, which contains the patient code

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** found by the orchestrator, 2026-10-24, while checking F-032 in the app
- **Expected:** log lines never contain the session folder name or the patient code (`StorageError` KDoc, §11/§12).
- **Actual:** `FfmpegSessionVideoRecorder` logs at INFO: `recording video to <dataDir>/sessions/2026-10-24_<patientCode>_<sessionId>/video/task00_rep01_take01.mjpeg` and `stopped recording <same path> after N frames`.
- **Notes:**
  - This was in the code before 2026-10-24.
  - Other INFO lines may do the same; group 4 (logging, F-038) checks every log call.
- **Status:** fixed 2026-10-24 (`7b52d28`), not yet re-run. Every log call in `app/src/main` was audited, and 28 calls were changed. They log file names only, never session paths. Exceptions go through `redacted()`, which keeps the class name and stack frames but runs every message through `redactPaths`. Tests: `FfmpegSessionVideoRecorderTest` "recording logs the file name only…" and `RedactedThrowableLoggingTest` (both shown to fail without the fix).

## Other observations (no finding)

- Sessions kept "Not processed" for 10-4 after Reprocess until the app restarted (F-050).
- At 1000×820, Settings clipped "Save and go back" while its status line showed (F-020). The
  button could still be clicked through the semantic tree.
- `TaskStop` kills the app. Session 10-2 (one take, then stop) was therefore recovered and listed
  in the dialog at the next start: an unplanned extra pass of F-052. The sessions left at
  calibration (10-1, 10-3) were not offered for recovery (F-047).
- Sessions shows `E2E1024-10-3` twice: 08:03 with the mic denied and 08:04 after the restart.

## Context updates

- Sessions 10-1, 10-2, 10-3 (two), 10-4, 10-5 (two) and the part D session (no patient code)
  were added to `reports/2026-10-23-context.json`. Nothing was uploaded.
- `protocols.siteName` and `environment.run10` were added.
- Left on the server: protocol `E2E 1024 SiteName protocol` in `E2E 1023 Speech project`. Site
  A's config version is now `2026-10-24.061507`.
- End state:
  - the app is stopped,
  - mic privacy is `Allow`,
  - OBS and the player are still running,
  - the Playwright profile in the scratchpad is still logged in as master.
