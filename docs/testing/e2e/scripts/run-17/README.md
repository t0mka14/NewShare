# Run 17 scripts (questionnaire rules `showIf` and questionnaire formatting, desktop app)

Run from the repo root in Git Bash after `. docs/testing/e2e/scripts/run-17/env.sh` (venv Python with Playwright, `E2E_STATE_DIR` = scratchpad,
`E2E_RUN_DIR` = this run's report folder). `login.py` needs `E2E_PASSWORD` and writes `auth.json` / `tokens.json` into the state dir.

Web: `login.py` (one master login), `a1_create_protocol.py` (real UI login, creates protocol "E2E run 17 questionnaire rules" in study 1 with Q1-Q8 and rules, saves, reloads and checks the rules; `E2E_SAVE=0` is a dry run),
`a3_site_config.py <out.json>` (GET /site-config of Paris, saves the raw JSON, prints showIf per question), `c8_server_check.py <sessionId> <dir>` (ticket + ZIP download, redacted examination.json, questionnaireAnswers),
`c8_web_detail.py <sessionId>` (web recording detail screenshot with the answers dialog; the computer id is blacked out).

App (all with the app stopped unless noted): `set_paris_token.py` (writes the Paris token into settings.json without printing it), `drop_installation_id.py` (removes installationId so the app registers as a new device;
needed because this machine's device is registered to another clinic and Paris config is rejected with HTTP 409).
While the app runs (real OS input, window screenshot coordinates): `click.sh X Y` (click at image coordinates of `shot.sh` screenshots), `shot.sh <name>`, `type-text.ps1 -Text ...` (SendInput KEYEVENTF_UNICODE; this DID reach the Compose field in this RDP session),
`resize.ps1 -W -H` (the window has a minimum of 960x720, `Main.kt`), `wheel.ps1` / `wheel.sh` (mouse wheel; did NOT scroll the pane, use the scrollbar drag via `tools/Drag-InWindow.ps1` instead).
Reused: `run-08/start-app.ps1`, `wait-ready.sh`, `stop-app.ps1`, `run-02/front.ps1`, `tools/Capture-Screen.ps1`, `tools/Drag-InWindow.ps1` (pass -ToX/-ToY for a plain click).
Method note: the Upload screen uploads every ready session, so the older local sessions were moved out of `app/data/sessions/` (app stopped) and back afterwards.
