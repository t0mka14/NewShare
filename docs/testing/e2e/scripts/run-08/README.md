# Run 08 scripts (error handling and robustness)

All PowerShell scripts run from any directory; paths are derived from the script location. Nothing is hard-coded except the standard Windows registry key and the default cable device name (parameter).

- `mic-privacy.ps1 [-Set Allow|Deny]`: reads (and optionally sets) the microphone privacy value (plan 3.1.1). Always finish a run with `Allow`.
- `start-app.ps1 -Log <hotrun.log> [-PidFile <file>]`: wraps `run-04/start-app.ps1` as a minimized background PowerShell and prints/stores the launcher PID.
- `wait-ready.sh`: (Git Bash, from the repo root) waits for `main.pid` and "Waiting for changes", prints the app's `pid=` line. Then `resize_window` 1000x820.
- `stop-app.ps1 [-LauncherPid <pid>]`: kills the launcher tree, stops the JVM named in `main.pid` and deletes the file.
- `kill-app.ps1`: simulated crash. Force-kills the app JVM (pid from `main.pid`), prints the UTC kill time, deletes `main.pid`. The launcher keeps running; run `stop-app.ps1` before starting again.
- `player.ps1 -Action Pause|Resume [-Name wmplayer]`: pauses the signal source by suspending/resuming the process (`NtSuspendProcess`); no GUI needed. Setting the CABLE Input endpoint volume to 0 does NOT silence CABLE Output (checked: mean stayed about -19 dB), so use this.
- `signal-check.ps1 [-Device <dshow name>] [-Seconds 4]`: ffmpeg volumedetect on the cable (plan 3.1.3). Playing about -19 dB, paused -91 dB.

Reused: `run-02/front.ps1` (before every screenshot), `run-03/endpoint-volume.ps1`.
UI driving hint: node ids on the task screen follow a pattern (START id S, Next id S+7, next screen header = Next+11, START = header+27 with an example button, +17 without), but always check with `get_semantic_tree` when a click reports "not found".
- `server_check.py <label> <zip dir> <sessionId>[=<local zip path relative to repo>] ...`: server record, attempts (count) and ZIP SHA-256 comparison per session (admin API, master token from `E2E_STATE_DIR`). Env as for run 07: `. scripts/run-07/env.sh`, then add `scripts/run-08` to `PYTHONPATH`; `E2E_RUN_DIR` for logs. Output redacted to `logs/server-<label>.txt`.

