# Run 12 scripts (targeted fix re-test)

- `check_session.py <session dir>`: read-only check of a processed session: `examination.json` processing status, `skippedTakes`, clip/video per task, ffprobe durations of master parts and clips, ZIP entries.

Reused unchanged: `run-08/start-app.ps1`, `wait-ready.sh`, `stop-app.ps1`, `mic-privacy.ps1`, `run-02/front.ps1`. The window and the UI were driven with the compose-hot-reload MCP tools (`resize_window`, `get_semantic_tree`, `click`, `scroll`, `type_text`); `type_text` did work for the participant code and questionnaire answers. The interruption attempt used a marker file asking for `Disable-PnpDevice` / `Enable-PnpDevice` on `*CABLE Output*` from an elevated shell.
