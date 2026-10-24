# Run 00: Environment bring-up (manual, before the plan existed)

- Date / time: 2026-10-23, 06:20–20:25
- Runner: Opus, interactively with the user, not a Sonnet run
- App commit: `59bb063`
- Preconditions met: n/a, this run set the environment up
- Result: passed with findings

Purpose: get the desktop app running with hot reload on the test VM, with a virtual microphone
and camera, so the plan's runs can drive it. Nothing was fixed in the app; the steps below are
environment changes only.

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Start the app with `hotRun --auto` from Git Bash | pass, with a problem | Window opens; recompiler fails, see F-004 |
| 2 | Connect the compose-hot-reload MCP server | fail | F-003 |
| 3 | Start a session on a host with no capture device | fail | F-001 (log only) |
| 4 | Install VB-Audio Virtual Cable 4.5 and OBS Studio 32.2.2 (signed installers, elevated by the user) | pass | — |
| 5 | Make the cable visible in the RDP session | pass | Needed RDP audio settings "Play on remote computer" / "Do not record" (plan §3.1) |
| 6 | Record from CABLE Output | fail, then pass | Microphone privacy was `Deny` for the user; set to `Allow` (plan §3.1) |
| 7 | Monitor CABLE Output in the app that was started before step 6 | fail | F-001, F-002 |
| 8 | Restart the app; monitor CABLE Output | pass | No `startMonitoring failed` after restart |
| 9 | `ffmpeg -list_devices` shows `OBS Virtual Camera` and `CABLE Output` | pass | — |
| 10 | Java Sound (`:app:listAudioPorts`) lists `CABLE Output (VB-Audio Virtual Cable)` | pass | — |
| 11 | `GET /api/site-config/<Paris token>` | pass | HTTP 200 |
| 12 | Desktop capture with `tools/Capture-Screen.ps1` | pass | 2292×1039, RDP desktop |

## Findings

### F-001: A microphone that fails to open is not shown; starting the recording then throws

- **Severity:** major
- **Area:** desktop-app
- **Run / step:** run 00, steps 3 and 7
- **Expected:**
  - When the capture device can't be opened (no device, device busy, access denied), the
    calibration and task screens say so (§8.5, §11), and the examiner can pick another device or
    retry.
  - Recording can't be started from a failed recorder.
- **Actual:**
  - `startMonitoring` fails and is only logged as a WARN.
  - The calibration screen shows a flat zero level, as if the room were silent.
  - Confirming calibration calls `startWriting` on the failed recorder, which throws
    `IllegalStateException`. `ErrorReporter` logs it as "unhandled recoverable error", and the
    examiner gets no explanation.
- **Repro:**
  1. Make the microphone unusable. Either way works:
     - no capture device at all (seen at 06:27 with `device=none`), or
     - Windows Settings → Privacy → Microphone → "Let apps access your microphone" off (seen at
       20:06).
  2. Start the app, Start the protocol (speech), fill the patient form.
  3. Calibration shows level 0. Confirm. Nothing visible happens, and the log has the exception
     below.
- **Evidence:** no screenshot at the time: the compose-hot-reload MCP server was not connected
  (F-003). **Reproduced in run 08, part 1.1:**
  `reports/2026-10-23-run-08-errors/screenshots/p1-1-calibration-denied.png` (flat level, no
  message) and `p1-1-after-continue.png`. The second shows that the raw `IllegalStateException`
  text reaches the examiner in the "Something went wrong. Please try again." dialog. The same
  dialog follows F-051 (microphone not released after Back) with privacy Allow. Log, from `hotrun.log`:

  ```
  06:27:31.407 WARN  o.e.a.i.a.JvmContinuousSessionRecorder - startMonitoring failed for device=none
  ...AudioException: DeviceUnavailable: DeviceUnavailable(deviceId=none)
  06:27:35.374 ERROR o.example.app.domain.ErrorReporter - unhandled recoverable error
  java.lang.IllegalStateException: startWriting is only valid from Monitoring, was Failed(error=DeviceUnavailable(deviceId=none))
      at org.example.app.infrastructure.audio.JvmContinuousSessionRecorder$startWriting$2.invokeSuspend(JvmContinuousSessionRecorder.kt:120)

  20:06:32.307 WARN  o.e.a.i.a.JvmContinuousSessionRecorder - startMonitoring failed for device=CABLE Output (VB-Audio Virtual Cable)|Unknown Vendor|Unknown Version
  ...AudioException: DeviceUnavailable: line with format PCM_SIGNED 48000.0 Hz, 16 bit, mono, 2 bytes/frame, little-endian not supported.
  Caused by: javax.sound.sampled.LineUnavailableException: line with format PCM_SIGNED 48000.0 Hz, 16 bit, mono, 2 bytes/frame, little-endian not supported.
      at java.desktop/com.sun.media.sound.DirectAudioDevice$DirectDL.implOpen(DirectAudioDevice.java:480)
  ```

- **Notes:**
  - `SessionComponent.bootstrap()` (`app/src/main/kotlin/org/example/app/navigation/SessionComponent.kt:222`)
    calls `startMonitoring` and continues whatever the resulting state.
  - `startRecording()` (same file, around line 519) calls `startWriting` unconditionally.
  - `CalibrationComponent` sets `deviceLost` only for `RecorderState.Interrupted`, not for
    `Failed`, so the screen has no failed state to show.
  - The message "format … not supported" is misleading here. A fresh JVM opened the same device
    in the same format fine once access was allowed. Windows reports a denied capture as a failed
    open.
  - The same JVM kept failing after access was allowed. Only a restart of the app helped, so
    "retry" in the app may not be enough for this cause. A message telling the examiner to check
    the Windows microphone permission would be.
- **Status:** fixed 2026-10-24, confirmed in run 10 (`../2026-10-24-run-10-fix-retest/report.md`). The cause was as noted above: calibration
  ignored `RecorderState.Failed`, and `startRecording()` called `startWriting` whatever the
  state. Calibration now shows "microphone could not be opened" (check the connection and the
  Windows "Let apps access your microphone" setting) with a Retry button. Continue is disabled
  unless the recorder is `Monitoring`. `startRecording()` refuses to start from any other state.
  A failed open with `useCalibration=false` now lands on calibration too, so the error and Retry
  are shown. The "still failing after access was allowed" seen in the same JVM was probably
  F-051 (the line left open). Regression tests:
  - `CalibrationComponentTest`: "a failed mic open shows the mic error, not the device-lost
    state, and blocks Continue", "retry reopens the selected device and clears the mic error",
    "selecting another device after a failed open clears the mic error";
  - `SessionComponentTest`: "confirming calibration while the recorder is Failed does not start
    writing or navigate", "without useCalibration a failed mic open still lands on Calibration
    with micFailed and never writes", "a failed open shows micFailed on Calibration and Retry
    then Continue starts the recording".
  - Follow-up after run 10 (part B step 4: Retry in the same JVM keeps failing after access is
    allowed): a Retry or device re-select that fails again now adds "close and restart the app"
    (`calibration.micErrorRestart`). Tests in `CalibrationComponentTest`: "first mic failure shows
    no restart hint", "retry that fails again shows the restart hint", "retry that succeeds clears
    the error and the restart hint". Not yet seen on screen.

### F-002: Capture format negotiation trusts `isLineSupported`, which DirectSound answers yes for everything

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 00, step 7
- **Expected:** if the negotiated format can't actually be opened, the next candidate format is
  tried (§5.3.1 format negotiation).
- **Actual:**
  - `CaptureFormatNegotiator.negotiate` picks the first candidate for which
    `mixer.isLineSupported(...)` is true.
  - On Windows' DirectSound mixers that check accepted 48 kHz/16-bit/mono. When `open()` then
    failed, the recorder gave up with `DeviceUnavailable` instead of trying the next candidate.
- **Repro:** same as F-001, second variant. Watch which format the log says failed. Only one
  format is ever tried.
- **Evidence:** the 20:06 log lines under F-001. The code is in
  `JvmContinuousSessionRecorder.openLine()` (around line 376).
- **Notes:**
  - In this case no other format would have helped, because the cause was the denied access.
  - This is unconfirmed on real hardware. A device that refuses mono but takes stereo would hit
    it.
  - A scratch probe (`LineProbe.java`) opened 48 k and 44.1 k, mono and stereo, on CABLE Output
    once access was allowed.
- **Status:** fixed 2026-10-24 (`a0ba34c`), not yet re-run. Each §5.3.1 candidate now gets exactly one real `open()`. The first format that opens is used and recorded, for both startMonitoring and resume. `DeviceUnavailable` comes only after every candidate is rejected. `isLineSupported` stays as a pre-filter. The javax calls sit behind a new `CaptureLineOpener` seam. Tests: `JvmContinuousSessionRecorderFormatFallbackTest`, 3 tests that fail on the old single-open logic.

### F-003: The compose-hot-reload MCP server does not start on Windows

- **Severity:** minor
- **Area:** environment
- **Run / step:** run 00, step 2
- **Expected:** the project's `.mcp.json` starts the MCP server on every developer OS
  (`docs/dev/hot-reload.md`, MCP section).
- **Actual:** Claude Code reports `compose-hot-reload (CONNECTION_CLOSED): Connection closed`.
  `.mcp.json` runs `./gradlew`, a POSIX shell script, which Windows can't execute directly.
- **Repro:** open the project in Claude Code on Windows and run `/mcp`.
- **Evidence:** the session's MCP status message; `.mcp.json`.
- **Notes:**
  - Workaround: a local-scope override with `cmd /c <abs path>\gradlew.bat …`, in plan §3.2.
  - Use the absolute path. A bare `gradlew.bat` also fails under Claude Code, for the reason in
    F-004.
  - The override also needs `-e JAVA_HOME=<jdk>`. With no system `JAVA_HOME` and no `java` on
    `PATH`, `gradlew.bat` exits 1 with "JAVA_HOME is not set" before Gradle can read
    `org.gradle.java.home`; Claude Code shows only `CONNECTION_CLOSED` (found in preflight,
    2026-10-23).
- **Status:** closed 2026-10-24, worked around (user decision). The test plan §3.2 local-scope MCP override (`cmd /c <abs>\gradlew.bat`, `-e JAVA_HOME`) is how every E2E run since has started the server; `.mcp.json` is unchanged.

### F-004: The hot-reload recompiler can't find `gradlew.bat` when started from Claude Code

- **Severity:** minor
- **Area:** environment
- **Run / step:** run 00, step 1
- **Expected:** with `hotRun --auto`, saving a source file recompiles and reloads the app.
- **Actual:** `main.chr.log` shows the recompiler failing at start:

  ```
  Recompiler | Running 'Gradle'...
  'gradlew.bat' is not recognized as an internal or external command, operable program or batch file.
  ```

  The window runs, but nothing ever reloads.
- **Repro:** from a Claude Code shell (PowerShell or Git Bash), run
  `gradlew.bat :app:hotRun --mainClass=org.example.app.MainKt --auto`.
- **Evidence:**
  - Claude Code's shells set `NoDefaultCurrentDirectoryInExePath=1`, which stops `cmd` from
    running programs from the current directory.
  - The app inherits it, and the recompiler runs `gradlew.bat` relative to
    `gradle.build.root`.
  - Clearing the variable before launch fixed it: `BUILD SUCCESSFUL`, then
    `Waiting for changes to input files...`.
- **Notes:**
  - It doesn't affect launches from a normal terminal or the IDE.
  - The workaround is in plan §3.4.
  - It could also be reported to Compose Hot Reload: invoking the wrapper by absolute path would
    be robust.
- **Status:** closed 2026-10-24, worked around (user decision). The test plan §3.4 clears `NoDefaultCurrentDirectoryInExePath` before `hotRun`; reloads have worked in every run since.

## Context updates

None. This run made no web data.

Environment state left behind:
- VB-Cable and OBS installed.
- Microphone privacy `Allow` for the user.
- RDP audio set to "Play on remote computer" / "Do not record".
- The app running from `hotRun` with hot reload working.
