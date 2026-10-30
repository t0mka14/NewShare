# Run 16 scripts (desktop re-test of fix round 4, group C)

No new script was needed. Everything was done with reused scripts and the compose-hot-reload MCP tools.

Reused: `run-08/start-app.ps1`, `wait-ready.sh`, `stop-app.ps1`, `run-02/front.ps1` (also needed before a screenshot when a terminal covers the window), `run-02/redact.ps1`, `run-07/set_token.py` (with `. scripts/run-07/env.sh` for PYTHONPATH), `tools/Drag-InWindow.ps1` (editor handles).

Methods worth keeping:
- **No-token screen (F-089):** the blocking screen shows only with no cached config AND no token. With a cached config the main menu shows instead. So: stop the app, `set_token.py none`, move `app/data/config/config.json` aside, start; afterwards restore both.
- **Failed upload without admin or a stray server record (F-046):** after a normal upload of a session, reset its `metadata/upload_status.json` to `{}`, append one byte to its archive ZIP, press Upload. The server answers 409 "already on the server with different content" (a rejected attempt, no new record). Restore the ZIP and the status afterwards from a copy.
- **Killing the camera (F-133):** `Get-CimInstance Win32_Process` for `ffmpeg.exe` whose parent is the pid in `app/build/run/main/main.pid`, then `Stop-Process`.
- **Exactly one ready session (F-064):** with the app stopped, move the other session folders out of `app/data/sessions/` and move them back at the end.
