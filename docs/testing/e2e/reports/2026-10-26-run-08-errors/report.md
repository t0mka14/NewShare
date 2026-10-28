# Run 08: Error handling and robustness

- Date / time: 2026-10-28, about 19:58-20:25 local (phase 1: parts 1, 2, 5 and prep; phase 2: parts 4 and 3 with the firewall rule added and removed by the user in an elevated shell). Runner: Sonnet. App commit: `7cd7e47` (plus uncommitted docs only)
- Preconditions met: yes (app stopped, `app/data` = run-07 installation, token A, mic `CABLE Output`, cached config; mic privacy `Allow` before the run; wmplayer looping at about -19 dB; OBS running)
- Result: **passed with findings** (parts 1-5; part 6 not covered by user decision). F-08-1 = **F-117**; F-08-2 is a duplicate of **F-046** (seen again).
- File times are local (UTC+1); timeline `wallClock` values are UTC.
- Saved by the orchestrator from the runner's returned text; finding IDs renumbered by the orchestrator.

## Steps
| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1a | Mic privacy `Deny` (read back `Deny`), restart, speech protocol (`E2E1026-08-1`) up to calibration 📷 | pass | Red message "The microphone could not be opened. Check that it is connected and that Windows microphone access ("Let apps access your microphone") is allowed, then press Retry or choose another device." Retry shown, **Continue disabled**. No level bar (not a flat zero). `screenshots/step-1-calibration-mic-denied.png` |
| 1b | Log: a warning, no `IllegalStateException` | pass | `startMonitoring failed ... DeviceUnavailable` WARN (caused by `LineUnavailableException`) after per-format "capture format rejected" INFO lines. `IllegalState` count: 0. `logs/app-redacted.log` |
| 1c | Privacy back to `Allow` (read back), restart, calibration works 📷 | pass | Level bar moves (about 0.1, below the green band as in earlier runs: F-092), Continue enabled. `screenshots/step-1-calibration-after-allow.png` |
| 2a | No audio: wmplayer paused by suspending its process (`scripts/run-08/player.ps1`), cable -91.0 dB mean | pass (method) | Setting the CABLE Input endpoint volume to 0 did not change the cable (mean -18.9 dB), so that is not a way to silence it. |
| 2b | Calibration with silence 📷 | **F-117** | Bar at zero, no message, no warning, **Continue enabled**. `screenshots/step-2-calibration-no-signal.png` (F-021 seen again for the out-of-band case, closed by design) |
| 2c | Start a take on silence, stop it | **F-117** | The take runs (timer, ring), Stop shows a flat waveform, Next/Retry enabled, no warning. `screenshots/F-08-1-silent-take-no-warning.png`, `screenshots/step-2-silent-take-stopped.png` |
| 2d | Restore the signal | pass | Player resumed: cable mean -19.8 dB right after, -18.8 dB at the end of the run. |
| 2e (bonus) | Killed the app on that silent session (`E2E1026-08-1`), restarted | pass | Recovery dialog "Interrupted sessions recovered ... E2E1026-08-1 (28. 10. 2026 19:59)", OK / Open Sessions; Sessions row "Not processed  Recovered". Master 38.85 s (start 19:00:51.2Z, kill 19:01:30.08Z = 38.87 s). `SESSION_RECORDING_STOPPED` wallClock 19:01:30.089Z equals the kill time (F-054 fix holds). `screenshots/step-2b-*.png` |
| 5a | `E2E1026-08-5`: calibration, Continue, START (19:02:48Z), kill at 19:02:58.17Z (`kill-app.ps1`) 📷 | pass | Before restart: `session_master.partial.wav` (1,747,244 bytes) and `timeline.events.jsonl`; header says 15.2 s (patched about every 5 s). `screenshots/step-5-take-running-before-kill.png`, `logs/part5-08-5-timeline.events.before-restart.jsonl` |
| 5b | Restart: recovery dialog lists `E2E1026-08-5 (28. 10. 2026 20:02)` 📷 | pass | `screenshots/step-5-recovery-dialog-08-5.png`. Log: `recovered interrupted session: sessionId=...` only; no patient code in the app log. |
| 5c | Master intact up to the kill | pass | `session_master.wav` 18.200 s (18.14 s between session start and kill; the extra 0.06 s is buffered audio). Header rebuilt, `.partial` renamed. |
| 5d | Timeline and examination | pass | `STOP_BUTTON_PRESSED` with `reason: "RECOVERED_INTERRUPTED"` and synthetic `SESSION_RECORDING_STOPPED` at sample 873600, wallClock 19:02:58.222Z (kill 19:02:58.169Z). `examination.json`: `recovered: true`, PHONATION record (1 take). `logs/part5-08-5-timeline_original.json`, `logs/part5-08-5-examination.json` |
| 5e | Sessions shows Recovered; Reprocess gives a clip 📷 | pass | Row "Processed  Not uploaded  Recovered" at once. One clip `..._0_PHONATION_Rep1.wav` 9.95 s. ZIP: master, clip, examination, participant, snapshot, timeline_original, manifest. `screenshots/step-5-after-reprocess-08-5.png` |
| 5f | Upload screen offers `08-5` (it has a clip) 📷 | pass | `screenshots/step-5-upload-offers-08-5.png`. Not uploaded. F-064 seen again ("1 sessions ready to upload"). |
| 5g | Zero-clip case A: `E2E1026-08-6`, calibration only, kill | pass | No master written yet, so the next start silently deleted the folder (EmptySessionCleanup, spec §8.4.3); no dialog. |
| 5h | Zero-clip case B: `E2E1026-08-6` again, Continue to the first task, no START, kill, restart 📷 | pass | Dialog lists 08-6. `screenshots/step-5-recovery-dialog-08-6-zero-clip.png` |
| 5i | Process 08-6 📷 | pass | "Processing failed - Nothing was recorded in this session, so there is nothing to process." Row "Processing failed  Recovered", no `archive/`, not on the Upload screen. `screenshots/step-5-reprocess-08-6-zero-clip.png`, `step-5-sessions-08-6-processing-failed.png`, `step-5-upload-no-08-6.png` |
| prep | `E2E1026-08-2`: full speech protocol, processed, NOT uploaded | pass | "Session processed (4 clips) - ready to upload." sessionId `b2182fdad9e944c682aa019f18ea42fe`. ZIP `app/data/sessions/2026-10-28_E2E1026-08-2_b2182fdad9e944c682aa019f18ea42fe/archive/E2E1026-08-2_b2182fdad9e944c682aa019f18ea42fe.zip`, 9,394,995 bytes, SHA-256 `90a21a4f847b18dd1a54983ac9573ff1b34098266a4540f89a8de19842309370`. |
| P2-4a | **Part 4**: app started with the rule in place (verified `Enabled True, Block`) 📷 | pass | Main menu shows "Site: E2E 1026 Clinic A" and a grey notice "Offline – using the configuration saved on 28. 10. 2026" (`mainMenu.offlineNotice`). `screenshots/step-4-main-menu-offline-start.png`. Log: `config fetch failed, transport error: java.net.SocketException`; `config refresh: network failure, using cached configVersion=2026-10-26.172809 site=E2E 1026 Clinic A`. |
| P2-4b | Start a protocol offline (patient `E2E1026-08-3`, patient form, calibration), then Back 📷 | pass | Protocol list from the cached config, patient form, calibration with a moving level and Continue enabled. Back to the main menu; the empty session was removed (`EmptySessionCleanup`). `screenshots/step-4-calibration-offline.png`. |
| P2-4c | Settings -> Refresh configuration 📷 | pass | Red text "Could not reach the server. Using the saved configuration."; cached config kept. The message already showed on opening Settings (left over from the start-up refresh). `screenshots/step-4-settings-refresh-unreachable.png` (token redacted). |
| P2-3a | **Part 3**: Upload offers 08-2 and 08-5 ("2 sessions ready to upload"); press Upload 📷 | pass (F-046 seen again) | Both rows "Upload failed" with "Upload failed: the server could not be reached. The session is kept and can be uploaded later - press Upload to try again". Upload stays enabled. Progress bar full green (F-046). `screenshots/step-3-upload-blocked.png`. |
| P2-3b | Log lines for the blocked upload | pass | `KtorUploadApi - upload failed, transport error ... SocketException`; `attempt=1 outcome=NetworkFailure retry=manual, no automatic retry` for both; `upload batch: 2 session(s), 0 ok, 2 failed`; `device check-in failed, transport error`. |
| P2-3c | Sessions stay failed and retryable across a restart 📷 | pass (deviation) | The app instance and its launcher disappeared between 20:15:36 and about 20:20 while the runner polled for the rule removal; no crash entry in the app log, no `hs_err`, nothing in the hotrun log. Cause unverified (likely external to the app). After a restart, Upload still lists both with "Upload failed", Upload enabled: the failed state is persisted. The retry therefore ran in a new instance, not the same one as the plan says. `screenshots/step-3-upload-after-restart-before-retry.png` |
| P2-3d | Rule removed by the user; Upload (retry) | pass | `upload ok ... HTTP 200` twice, `attempt=2 outcome=Success`, `upload batch: 2 session(s), 2 ok, 0 failed`; "Files successfully uploaded." `screenshots/step-3-upload-retry-success.png` |
| P2-3e | Server record per session (`scripts/run-08/server_check.py`) | pass | 08-2: `valid`, issues `["skipped"]`, `recovered: false`, **1 attempt** (200 accepted), 9,394,995 bytes. 08-5: `valid`, issues `["recovered"]`, **`recovered: true`**, **1 attempt** (200 accepted), 2,259,488 bytes. The blocked attempts never reached the server. `logs/server-after-unblock.txt` |
| P2-3f | ZIP of 08-2 downloaded and compared | pass | Local and server SHA-256 both `90a21a4f847b18dd1a54983ac9573ff1b34098266a4540f89a8de19842309370`; server `shaDeclared` = `shaComputed`. |
| 6 | Long upload | not covered (user decision) | |

## Findings

### F-08-1: A completely silent microphone signal is not detected anywhere (calibration, take, finish)
Index ID: **F-117**
- **Severity:** question (leaning minor)
- **Area:** desktop-app
- **Run / step:** run 08, step 2
- **Expected:** the spec is silent on a dead line. Plan §6 run 08.2 expects calibration to show a zero level and not let a too-quiet level pass silently (or warn). F-021 (Continue allowed outside the band) was closed as by design, but an all-zero signal means nothing is recorded at all, for example a muted or unplugged microphone that Windows still lists.
- **Actual:** with the player suspended (cable mean -91 dB) the calibration bar stays at zero with no message, Continue is enabled, a take can be started and stopped (flat waveform, no warning), and the examination continues and is processed into valid clips. Nothing in the UI, the log or `examination.json` says the signal was silent.
- **Repro:** 1. Pause the audio source (`scripts/run-08/player.ps1 -Action Pause`). 2. Start the speech protocol, patient `E2E1026-08-1`, calibration. 3. Continue, START, wait 5 s, STOP.
- **Evidence:** `screenshots/step-2-calibration-no-signal.png`, `screenshots/F-08-1-silent-take-no-warning.png`, `screenshots/step-2-silent-take-stopped.png`. Cable -91.0 dB mean with ffmpeg.
- **Notes:** related F-021 (closed by design for out-of-band levels) and F-092. A "no signal for N seconds" notice on calibration or the take would be the cheap fix; product decision.
- **Status:** open

### F-08-2: The upload progress bar fills completely when every upload failed (duplicate of F-046)
- **Severity:** cosmetic  **Area:** desktop-app  **Run / step:** run 08, part 3
- **Actual:** after the blocked upload, both rows say "Upload failed" in red but the bar is full and dark green (progress 1.0 in the semantic tree; 0.0 before pressing Upload).
- **Evidence:** `screenshots/step-3-upload-blocked.png`.
- **Status:** duplicate, recorded as seen again under **F-046** by the orchestrator.

Seen again (no new finding): F-046 (see F-08-2), F-064. F-001, F-051, F-052, F-053 confirmed fixed again (steps 1 and 5). Offline behaviour (offline notice, cached config, clear network-failure messages, retryable sessions) works as the plan expects.

## Context updates
`reports/2026-10-26-context.json` has a new `run08` object: the silent session `E2E1026-08-1`, the recovered session with a clip `E2E1026-08-5` (Processed, not uploaded), the zero-clip session `E2E1026-08-6` (Processing failed), and `E2E1026-08-2` (id, ZIP path, SHA-256). Both 08-2 and 08-5 were **uploaded** in phase 2 (valid, 1 attempt each). UTF-8 without BOM (verified).

## Notes for run 09
- Server now holds two new recordings from run 08: `E2E1026-08-2` (valid, issue "skipped") and `E2E1026-08-5` (valid, issue "recovered"). Local sessions: 07-1, 08-2 and 08-5 uploaded; 08-1 Not processed / Recovered; 08-6 Processing failed / Recovered. Nothing is waiting on the Upload screen.
- Upload uploads every ready session at once; there is no per-session choice.
- Start with `scripts/run-08/start-app.ps1 -Log <hotrun.log> -PidFile <file>`, `scripts/run-08/wait-ready.sh`, `resize_window` 1000x820, `scripts/run-02/front.ps1` before screenshots; stop with `scripts/run-08/stop-app.ps1 -LauncherPid <pid>`.
- The Settings semantic tree prints the site token and the installation ID; redact screenshots (`run-02/redact.ps1 -Rects "32,588,920,56"` at 1000x820 after the refresh message) and never paste the tree.
- Tree node ids follow patterns (START id S, Next S+7, new header = Next+11), but a stale id gives "not found"; a click right after a screen change may need a 2-3 s wait.
- An app process can vanish (as in phase 2) without any log line; if the MCP reports "No application is currently connected", check `main.pid` and restart.
- End state: mic privacy `Allow`, firewall rule removed, wmplayer playing (about -19 dB), OBS running with the virtual camera on, app stopped.

## Scripts and logs
- Scripts: `docs/testing/e2e/scripts/run-08/` (`README.md`, `mic-privacy.ps1`, `start-app.ps1`, `wait-ready.sh`, `stop-app.ps1`, `kill-app.ps1`, `player.ps1`, `signal-check.ps1`, `server_check.py`); run-08 paragraph in `docs/testing/e2e/scripts/README.md`. Reused: `run-04/start-app.ps1`, `run-02/front.ps1`, `run-02/redact.ps1`, `run-03/endpoint-volume.ps1`, `run-07/env.sh`, `run-06/common06.py`.
- Logs (`logs/`, installation ID redacted, checked for the site tokens): `app-redacted.log` (whole run), `server-after-unblock.txt`, `part2-08-1-timeline.events.before-restart.jsonl`, `part5-08-5-timeline.events.before-restart.jsonl`, `part5-08-5-timeline_original.json`, `part5-08-5-examination.json`. Start logs: `hotrun-1-deny.log` … `hotrun-8-unblocked.log`.
- Screenshots (`screenshots/`): `step-1-calibration-mic-denied`, `step-1-calibration-after-allow`, `step-2-calibration-no-signal`, `F-08-1-silent-take-no-warning`, `step-2-silent-take-stopped`, `step-2b-recovery-dialog-08-1`, `step-2b-sessions-recovered-08-1`, `step-5-take-running-before-kill`, `step-5-recovery-dialog-08-5`, `step-5-after-reprocess-08-5`, `step-5-upload-offers-08-5`, `step-5-recovery-dialog-08-6-zero-clip`, `step-5-reprocess-08-6-zero-clip`, `step-5-sessions-08-6-processing-failed`, `step-5-upload-no-08-6`, `step-4-main-menu-offline-start`, `step-4-calibration-offline`, `step-4-settings-refresh-unreachable` (token redacted), `step-3-upload-blocked`, `step-3-upload-after-restart-before-retry`, `step-3-upload-retry-success`.
