# Run 08: Error handling and robustness

- Date / time: 2026-10-24 01:20–01:52 local   Runner: Sonnet   App commit: `59bb063`
- Preconditions met: yes. Run 02 passed. Token A, Site A config, CABLE Output, player looping.
- Result: passed with findings. Parts 1–5 ran; part 6 is not covered.

Orchestrator notes:

- The runner did the run part by part. Between parts the orchestrator restarted the app and changed
  the system settings: microphone privacy Deny/Allow, and the firewall rule, which the user added
  and removed by hand from an elevated shell.
- The orchestrator saved this report from the runner's text and renumbered the findings.
- F-08-1 is the F-001 defect seen again, so it is merged into F-001 (see below) and gets no new ID.
- The suspected cause in F-051 comes from the orchestrator's reading of the code. Nothing was
  changed.

Deviations from the plan:

1. No restart between parts 1.2 and 2. Part 2 doesn't depend on a fresh JVM.
2. Parts 4 and 3 ran in one app instance, started with the server already blocked, so that no
   connection opened before the block could survive. This swaps the plan's order (3 before 4).
3. Part 6 (long upload) is not covered: user decision. It would need a ZIP of about 11 GB or more
   (about 70 min of filmed recording) to last over 2 minutes on this LAN.
4. Steps 3j and 3k were done by the orchestrator with the user's approval, because the permission
   policy denied the runner's admin login. Evidence: `logs/p3-server-checks.json`.
5. After the recovery restart, the MCP `take_screenshot` captured the terminal window instead of
   SHARE. `p5-recovery-start.png` is a cropped `Capture-Screen.ps1` capture.
6. The runner stopped once on an API error (529) during part 5 and was resumed. No step was
   repeated.

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1.1a | Microphone privacy Deny, fresh app. Speech protocol → `E2E1023-08-1` → calibration | pass | |
| 1.1b | 📷 Calibration: does it say the microphone is unavailable or denied? | **fail** | F-001. The level bar is flat and empty, with no message, and the device list still shows CABLE Output. `p1-1-calibration-denied.png` |
| 1.1c | Log: `startMonitoring failed` | confirmed | 01:23:53 WARN `DeviceUnavailable: line with format PCM_SIGNED 48000.0 Hz, 16 bit, mono … not supported` (F-002 too). `logs/p1-1-denied.txt` |
| 1.1d | Continue on calibration | **fail** | F-001. "Error" dialog: "Something went wrong. Please try again." plus the raw `IllegalStateException: startWriting is only valid from Monitoring, was Failed(…)`. `p1-1-after-continue.png` |
| 1.1e | Logs: `IllegalStateException`, `ErrorReporter` | confirmed | 01:24:01.765 ERROR `ErrorReporter - unhandled recoverable error` (in `app.log` and `hotrun-part1-deny.log`) |
| 1.1f | Start a take | not reachable | Calibration is the gate. |
| 1.2a–c | Privacy Allow, restart, calibration (`E2E1023-08-2`) 📷 | pass | The level moves again. "microphone opened" at 01:25:41, no failure. `p1-2-calibration-allow.png` |
| 2a–b | Pause the player; check for silence | pass | CABLE Output mean −91.0 dB |
| 2c | 📷 Calibration with silence | observed, no warning | F-021. Empty meter, no text. `p2-calibration-silence.png` |
| 2d | Continue with silence | **fail** (plan check) | F-021. Goes to task 1/6 within about 1 s, with no warning. The check "won't let a too-quiet level pass silently (or warns)" fails. |
| 2e | Resume the player | pass | −22.4 dB mean |
| 3-prep | Record and process `E2E1023-08-2`, don't upload 📷 | pass | Processed / Not uploaded, ID `6a6ab5c942ed4a0ca0ef63086cd59b7d`. 4 clips, no monologue clip. `p2-sessions-processed.png` |
| 4a | Firewall rule on, app started 📷 main menu | pass | "Site: E2E-1023-Site-A", no wait, no error. `p4-mainmenu-offline.png` |
| 4b | Offline indication on the main menu | **none** | F-048 |
| 4c–d | Settings, Refresh configuration 📷 | pass | Red "Could not reach the server. Using the saved configuration." The cached config is kept. `p4-settings-offline-redacted.png` |
| 4e–f | Picker, then calibration offline (`E2E1023-08-4`) | pass | Both protocols listed. The microphone opens. `p4-calibration-offline.png` |
| 4g | Back from calibration | **fail** | Leaves a "Not processed" session (F-047). Its microphone line is also never released (F-051). |
| 4h | Logs at offline start | observed | WARN `KtorConfigApi - config fetch failed, transport error: java.net.SocketException`. There is no "using saved config" line and no host name (F-038). |
| 3a | Upload screen 📷 | pass | Only `E2E1023-08-2` is offered. The abandoned `08-1` and `08-4` are not. `p3-upload-ready.png` |
| 3b | Upload while blocked 📷 | pass with findings | "Upload failed: network failure". The progress bar fills green (F-046). F-049. `p3-upload-failed.png` |
| 3c–d | Time to give up; status | pass | About 0.15 s. `upload_status.json`: Failed, `NetworkFailure`, `attemptCount` 1 |
| 3e | App log | observed | WARN upload transport error, WARN check-in transport error. Failures are logged. |
| 3f | Sessions 📷 | pass | "Processed  Upload failed". Retry is through Upload. `p3-sessions-failed.png` |
| 3g–h | Rule removed by the user; retry 📷 | pass | "Files successfully uploaded." `p3-retry-result.png` |
| 3i | `upload_status.json` | pass | Uploaded; attempts NetworkFailure, then Success; `attemptCount` 2; receipt 23:35:24.472Z, 8,351,200 bytes |
| 3j | Server attempts | pass (orchestrator, user-approved) | One attempt, 200 accepted, 23:35:24.472Z, declared SHA = computed SHA. The blocked attempt never reached the server. `logs/p3-server-checks.json` |
| 3k | Device 37 after the batch | pass (orchestrator, user-approved) | Site A, waiting 0, failed 0, Healthy |
| 3l | Log for the successful upload and check-in | F-038 | No lines. Only failures are logged. |
| 3m | Sessions after the retry | **fail** | F-050. It still shows "Upload failed" until reopened. `p3-sessions-after-retry.png`, `p3-sessions-after-retry-fresh.png` |
| 5a–b | Same app instance: `E2E1023-08-5`, then `08-6` → calibration → Continue | **fail** | `startMonitoring failed` at 01:37:32 and 01:38:27 with privacy Allow, then the F-001 dialog. F-051. `p5-mic-failed-after-allow.png` |
| 5c–d | Fresh app (PID 21164): `E2E1023-08-5b` → phonation → Start | pass | Microphone opened 01:40:27.6. Start at 23:40:42.23Z, offset 345600. |
| 5e | Master during the take | observed | `master/session_master.partial.wav`, 873,644 B at 01:40:44.1 |
| 5f | Kill, `Stop-Process -Force` | done | 01:40:45.35, about 3.3 s into the take |
| 5g | Master after the kill (copy) | pass | 1,017,644 B. The header is patched periodically and was 52,800 B (0.55 s) behind. Readable at 10.05 s; real signal (−22.2 dB). |
| 5h | Timeline after the kill | pass | `timeline.events.jsonl` on disk: recording started, task screen entered, Start. No stop event. |
| 5i | Restart 📷: is the interrupted session offered? | **fail** | F-052. Plain main menu, no dialog, no banner, no log line. `p5-recovery-start.png` |
| 5j | What recovery did | pass (data) | Silently renamed to `session_master.wav` with a **correct header covering all 1,017,600 data bytes** (10.6 s, nothing lost). Wrote `timeline_original.json` with `SESSION_RECORDING_STOPPED` (F-054). Set `examination.json` `"recovered": true`. The stale `app.lock` didn't block the start. |
| 5k | Sessions after recovery | pass | "E2E1023-08-5b — … 01:40", Not processed, red "Recovered" tag |
| 5l | Abandoned sessions at startup | observed | `08-1`, `08-4`, `08-5`, `08-6` are ignored: empty `master/`, Not processed (F-047). |
| 5m | Reprocess the recovered session | **fail** | F-053. "Done", but the ZIP has no clips. Sessions then shows "Processed  Not uploaded  Recovered", so it can be uploaded. Not uploaded. |
| 5n–o | Clean repro of F-051 in the fresh app: `08-7` calibration (mic opens 01:51:02) → Back → `08-8` calibration | **fail (reproduced)** | `startMonitoring failed` at 01:51:30. Back alone is enough. |
| 5p | Leave the app at the main menu | done | |
| 6 | Long upload | not covered | User decision (see deviations) |

## Findings

Repeats, not filed again:

- F-002: only one format is tried before giving up (1.1c).
- F-021: silence passes calibration (2c, 2d).
- F-038: only failures are logged (4h, 3l).
- F-046: a failed upload fills the progress bar green (3b).

### F-001 seen again (runner's F-08-1): the raw exception reaches the examiner

Part 1.1 reproduces F-001 exactly and adds the screenshots run 00 couldn't take:
`p1-1-calibration-denied.png` and `p1-1-after-continue.png`.

New detail for F-001:

- Continue opens an "Error" dialog: "Something went wrong. Please try again." followed by the raw
  `java.lang.IllegalStateException: startWriting is only valid from Monitoring, was Failed(error=DeviceUnavailable(deviceId=…))`.
- "Try again" can't help, and there is no hint about the Windows microphone permission.
- The same dialog appears for F-051 with privacy Allow.

### F-047: A session left at calibration stays in Sessions as an empty "Not processed" session

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 08, parts 1.1, 4g, 5l
- **Expected:** a session that never recorded anything (microphone failed, or the examiner pressed
  Back) is not kept, or is clearly marked empty or aborted. §8.4 doesn't cover this.
- **Actual:**
  - Entering calibration creates the session folder; the patient form doesn't. Back doesn't
    remove it, and a failed microphone open leaves one too.
  - Sessions lists `08-1`, `08-4`, `08-5`, `08-6`, `08-7` and `08-8` as "Not processed", each with
    an empty `master/` and Open editor / Reprocess buttons.
  - They are not offered for upload or recovery. Reprocess on them was not tried.
- **Repro:** enter calibration, press Back, open Sessions.
- **Evidence:** `screenshots/p2-sessions-processed.png`, `p3-sessions-failed.png`; the session
  folders.
- **Status:** fixed 2026-10-24 (`8aaa7c1`), not yet re-run. `EmptySessionCleanup` deletes a session folder only when every check shows it never recorded:
  - `examination.json` is readable, with no tasks, no end time, no processing and no archive;
  - there is no master audio;
  - the log holds only task-screen entries.
  - Anything else, or any error, keeps the folder.
  It runs when a session is left, and at startup after crash recovery. Each deletion logs only the session id. Checked in the app:
  - At startup, exactly the 9 empty E2E sessions were removed and the 8 with audio or recovery were kept.
  - Back on calibration removed that session's folder.
  Tests: `EmptySessionCleanupTest` (17 tests, most of them must-not-delete cases), `SessionComponentTest` (4) and `JsonSessionRepositoryTest`.

### F-048: With the server unreachable, the main menu gives no offline indication

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 08, part 4
- **Expected:** the examiner can tell that the saved config is in use and may be out of date.
- **Actual:** the main menu looks the same as online. "Could not reach the server. Using the saved
  configuration." appears only in Settings. On the positive side, the cached config works and
  protocols start (spec §6 offline scenario 5).
- **Evidence:** `screenshots/p4-mainmenu-offline.png`, `p4-settings-offline-redacted.png`
- **Status:** fixed 2026-10-24 (`11b365a`), not yet re-run. Fix: when a refresh falls back to the cached config, the main menu shows a muted line, "Offline – using the configuration saved on <date>". It clears after a successful refresh. Test: `MainMenuComponentTest` "the offline notice carries the cache date…". Not yet checked in the app, because it needs the server to be blocked.

### F-049: The upload failure message is terse

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 08, part 3, step 3b
- **Expected:** §11. Say the server couldn't be reached, and that the session is kept and can be
  retried later.
- **Actual:** small red text, "Upload failed: network failure". The retryable state itself is
  correct. The full green bar is F-046.
- **Evidence:** `screenshots/p3-upload-failed.png`
- **Status:** fixed 2026-10-24 (`11b365a`), not yet re-run. Fix: the message now reads "Upload failed: the server could not be reached. The session is kept and can be uploaded later - press Upload to try again", at body size. Tests: `UploadComponentTest` and `BatchUploadScenarioTest` scenario 8. Not yet checked in the app, because it needs the server to be blocked.

### F-050: Sessions shows a stale status after an upload or reprocess, until the screen is reopened

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 08, steps 3m and 5m
- **Actual:**
  - After Sessions → Go to upload → Upload (success) → Back, the row still says "Upload failed",
    though `upload_status.json` says Uploaded.
  - After Reprocess, the row kept "Not processed".
  - Reopening Sessions from the main menu shows the right status.
- **Repro:** Sessions → Go to upload → Upload → Back.
- **Evidence:** `screenshots/p3-sessions-after-retry.png` (stale), `p3-sessions-after-retry-fresh.png`
  (right)
- **Status:** fixed 2026-10-24 (`11b365a`), not yet re-run. Cause: nobody called the session browser's `refresh()`, and the browser stays on the stack under the other screens. Fix: it re-reads its rows when navigation returns to it. Test: `SessionBrowserComponentTest` "rows refresh by themselves when the screen is resumed…", shown to fail without the fix. Checked in the app: after a reprocess, the row changed to "Processing failed" without reopening the screen.

### F-051: After Back on calibration, the microphone can't be opened again until the app restarts

- **Severity:** major
- **Area:** desktop-app
- **Run / step:** run 08, steps 4g, 5a–b, 5n–o
- **Expected:** leaving calibration releases the capture device, and the next examination opens it
  (§8.5).
- **Actual:**
  - Clean repro in a fresh app, privacy Allow, no upload in between:
    1. `E2E1023-08-7` calibration: "microphone opened" at 01:51:02.
    2. Back.
    3. `E2E1023-08-8` calibration: `startMonitoring failed … DeviceUnavailable: line … not
       supported` at 01:51:30.
  - Every later examination fails the same way, and Continue shows the F-001 dialog.
  - ffmpeg can still record from the device at the same time.
  - Finishing an examination does release the microphone; only Back doesn't.
  - Restarting the app fixes it.
- **Repro:** New examination → speech → patient → calibration → Back → New examination → speech →
  patient → calibration. Check `app.log`.
- **Evidence:** `screenshots/p5-mic-failed-after-allow.png`; `app.log` at 01:32:13, 01:37:32,
  01:38:27, 01:51:02 and 01:51:30.
- **Notes:**
  - Suspected cause (orchestrator, from the code; not verified):
    - `SessionComponent.kt:206-210`: on destroy, the component calls
      `scope.launch(dispatchers.main) { r.stop() }`, then `scope.cancel()` at once.
    - Unless the main dispatcher runs immediately, the launched job is cancelled before it starts.
      `stop()` then never runs, and the DirectSound line stays open. A second open of that device
      then fails with "format not supported" (see F-002 for why the message is misleading).
    - The finish path calls `r.stop()` directly (line 551), which is why finishing works.
    - Line 209 has the same pattern for the camera's safety-net `v.stop()`.
  - Likely affects every Back out of a session that has opened the microphone, not just
    calibration.
- **Status:** fixed 2026-10-24, confirmed in run 10 (`../2026-10-24-run-10-fix-retest/report.md`). The suspected cause was confirmed: the stop job
  was launched into `scope` and cancelled before the Swing dispatcher ran it.
  `DefaultSessionComponent` now cancels `scope` first and then stops the recorder and camera in a
  separate `NonCancellable` scope. Regression test: `SessionComponentTest` "destroying the session
  stops the recorder even when main dispatches later". The test uses a queued main dispatcher
  (`TestCoroutineDispatchers(queuedMain = true)`), because the default unconfined one hid the bug.

### F-052: Crash recovery is silent: the examiner isn't told about the interrupted session

- **Severity:** major
- **Area:** desktop-app
- **Run / step:** run 08, steps 5i–5k
- **Expected:** §8.4: after a crash, the app tells the examiner that an interrupted session was
  found, and offers it or reports it as recovered.
- **Actual:**
  - After the kill and restart, the main menu is plain: no dialog, no banner, no log line.
  - Recovery runs silently and does keep the data: a finalized master with a correct header, all
    10.6 s, a `timeline_original.json`, and `"recovered": true`.
  - The only trace is a red "Recovered" tag in Sessions.
- **Repro:** start a take, kill the app process, start the app again.
- **Evidence:** `screenshots/p5-recovery-start.png`; the 5j file state (copies in the orchestrator
  scratchpad `p5\`).
- **Notes:** the data side of recovery passes. The finding is about the examiner not being told.
- **Status:** fixed 2026-10-24, confirmed in run 10 (`../2026-10-24-run-10-fix-retest/report.md`).
  - **Cause:** `Main.kt` ran `recoverAll()` inside `runCatching` and threw the outcomes away.
  - **Fix, storage and logging:** a new `StartupRecovery` holds the outcomes for the run and logs
    each one. Recovered is logged at INFO with the session id. Failed is logged at WARN with the
    error. Folder names are never logged, because they contain the patient code.
  - **Fix, dialog:** after a recovery, the main menu shows a one-time dialog. It lists the
    recovered sessions (patient code and start time) and, separately, those that could not be
    recovered, with "Open Sessions" and "OK". Dismissing it clears it for the rest of the run.
  - **Regression tests:**
    - `MainMenuComponentTest`: "recovery dialog state is present when a session was recovered or
      failed", "recovery dialog state is absent for no outcomes or only NotRecovered", "OK
      dismisses the recovery dialog and it does not return when the menu is recreated", "Open
      Sessions dismisses the recovery dialog and opens the session browser";
    - `RootComponentTest`: "recovery dialog Open Sessions routes to the session browser and the
      menu stays clean on return";
    - `StartupRecoveryTest` (4 tests).
  - **Not yet seen on screen:** the dialog has component tests only.

### F-053: A recovered session with no finished take processes into a ZIP with no clips, and is then uploadable

- **Severity:** major
- **Area:** desktop-app
- **Run / step:** run 08, step 5m
- **Expected:** §8.4. The interrupted take is either cut as a clip or reported as dropped. A
  session with nothing kept isn't marked Processed and uploadable like a normal examination, or is
  clearly flagged.
- **Actual:**
  - Reprocess gives `processing.status` "Done" and a 6-file ZIP: master, `examination.json`,
    `participant.json`, the config snapshot, `timeline_original.json` and the manifest.
  - `clips/` is empty, and nothing reports the interrupted take, which started at 7.2 s and is
    3.4 s long.
  - Sessions shows "Processed  Not uploaded  Recovered", so Upload would send it.
- **Repro:** kill mid-take, restart, Sessions → Reprocess on the recovered session, then open
  Upload.
- **Evidence:** the session's `archive/` ZIP (not uploaded, not copied to the repo); its
  `manifest.json` lists no clips.
- **Notes:** the session was left local and not uploaded.
- **Status:** fixed 2026-10-24, confirmed in run 10 (`../2026-10-24-run-10-fix-retest/report.md`). There were two causes:
  - Recovery added the synthetic `SESSION_RECORDING_STOPPED` but left the take open. Take
    selection needs a `STOP_BUTTON_PRESSED`, so the take had no range.
  - `examination.tasks[]` is written only when a task finishes. The interrupted VOCAL task had no
    record, so processing had nothing to cut and reported no error.

  Fix:
  - `TimelineCompactor` now closes each take still open at the crash. It adds a
    `STOP_BUTTON_PRESSED` at the last written sample, with `reason: "RECOVERED_INTERRUPTED"`.
  - `RecoverSessionsUseCase` adds the missing VOCAL task record from the config snapshot. The take
    is then cut as a partial clip.
  - A session that has a master but keeps no clip, video or questionnaire answer now fails
    processing with `ProcessingError.NothingToProcess`. It gets no ZIP and isn't uploadable.
  - Spec §8.4 and `docs/dev/data-and-schemas.md` are updated.

  `E2E1023-08-5b` was recovered by the old code, so its already processed ZIP stays as it is.
  Re-test with a fresh kill mid-take. Regression tests:
  - `TimelineCompactorTest`: "recovery closes a take left open at lastWrittenSample, flagged,
    before the synthetic stop", "recovery leaves already ended and rejected takes untouched",
    "recovery does not close a take that started at or after the last written sample";
  - `RecoverSessionsUseCaseTest`: "a take open at the crash is closed at the last written sample
    and gets a task record";
  - `ProcessSessionUseCaseTest`: "a recovered session whose interrupted take was closed by
    recovery produces one clip", "a session with a master but nothing kept is Failed, not Done,
    and gets no ZIP or upload status".

### F-054: The recovered timeline's stop event has the recovery time, not the crash time

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 08, step 5j
- **Actual:**
  - `SESSION_RECORDING_STOPPED` has sampleOffset 508800 (10.6 s, which is right) but wallClock
    23:42:31.24Z, the recovery time. The kill was at about 23:40:45.7Z.
  - The event type doesn't say the recording was interrupted.
- **Evidence:** the copy of `timeline_original.json` in the orchestrator scratchpad `p5\`
- **Notes:** tools that read `wallClock` get a recording that is 1 min 45 s too long.
- **Status:** fixed 2026-10-24 (`8aaa7c1`), not yet re-run. The synthetic stop events now get the wallClock of the last event that has both a clock and an offset, plus the samples written since. The recovery time is used only when no such event exists. Tests: `TimelineCompactorTest` (shown to fail without the fix) and `RecoverSessionsUseCaseTest` "recovered stop events carry the crash time derived from the samples".

## Context updates

- Sessions (local):
  - `E2E1023-08-2` (`6a6ab5c9…`): **Uploaded**, server-checked.
  - `E2E1023-08-5b` (`d2fa8362…`): Recovered, Processed with no clips. **Don't upload.**
  - `08-1`, `08-4`, `08-5`, `08-6`, `08-7`, `08-8`: empty, Not processed.
- Environment after the run:
  - microphone privacy Allow,
  - firewall rule removed (server answers 200),
  - player looping,
  - app at the main menu.
