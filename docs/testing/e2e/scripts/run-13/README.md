# Run 13 scripts (targeted re-test of fix round 2)

- `check_interruption.py <session dir>`: read-only F-129 check. Prints examination.json interruptions, the interrupt/resume events of timeline.events.jsonl, and the frame count (data size / block align) of every master part, so `sampleOffset`, the event sample and part-1 frames can be compared. Redact the 32-hex session id when pasting.

Reused: `run-08/start-app.ps1`, `wait-ready.sh`, `stop-app.ps1`, `signal-check.ps1`, `run-02/front.ps1`, `run-12/check_session.py`, `tools/Drag-InWindow.ps1` (editor boundary drag, from 782,400 to 742,400 moved End 0:55.8 to 0:54.5). Everything else was done with the compose-hot-reload MCP tools (clicks, `type_text`, `resize_window`, `scroll`) and `Stop-Process` on the app's child `ffmpeg.exe` (parent = the app JVM, found with `Get-CimInstance Win32_Process`).
