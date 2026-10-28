# Run 06 scripts (upload and the server record)

All take `E2E_STATE_DIR` (folder with `auth.json` / `tokens.json`) and `PYTHONPATH=docs\testing\e2e\scripts\common`. Run with the Playwright venv python
(UI scripts) or any python 3 (API scripts). `E2E_RUN_DIR` = run folder (default `reports/2026-10-26-run-06-upload`), logs go to `<run>/logs`
(installation IDs and tokens redacted), screenshots to `<run>/screenshots`. Session ids, clinic and ids come from the context file; the clinic token only from the secrets file.

- `common06.py`: helpers (sessions from the context file, local ZIP lookup in `app/data`, SHA-256, redaction, streaming download, DOM masking of installation IDs for screenshots).
- `api_check.py`: list (search `E2E`), `hasVideo=1`, detail, checks and attempts per session; compares bytes and ZIP SHA with the local ZIP.
- `api_downloads.py <dir>`: downloads every ZIP, one WAV and every MP4; SHA-256 vs the local ZIP, clip SHA vs the entry inside the local ZIP, `ffprobe` of each clip.
- `ui_recordings.py`: Recordings list filter, flag chips (Has video, Computer event), a detail screenshot per session, masked fields Show/Hide values and questionnaire answers, reload on a detail URL.
- `ui_activity.py`: Activity and Upload history sections, Camera details, play a WAV and an MP4 (`E2E_SESSIONS=<id,id>`, `E2E_LOG_NAME`).
- `devices_check.py`: `GET /devices` (pending, failed, last seen, versions).
- `reveal_check.py`: `POST /reveal` against the values in the context file (audited).
- `resend.py <keyA> <keyB>`: curl re-send of session A's ZIP (expect 200, original receipt) and of B's ZIP under A's id (expect 409). Field order as in `docs/dev/server-upload-integration.md` §1; token on stdin, installation ID from `app/data/config/settings.json` (`E2E_SETTINGS`).
- `scope_data.py`, `ui_scope.py`: Run A2 step 10.8 with data (fixed copy of `run-A2/scope_data.py`, which reads `sessionId` instead of `id` and posts `{ids}` instead of `{sessionIds}`).

The desktop upload itself is driven by hand through the compose-hot-reload MCP (Main -> Upload -> Upload); start with `run-04/start-app.ps1`.
