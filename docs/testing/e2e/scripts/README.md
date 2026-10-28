# E2E scripts

Python Playwright / HTTP scripts used by the runs. Run them with the venv interpreter, from a "state dir"
that holds `auth.json` and `tokens.json` (login tokens, never committed):

    set E2E_STATE_DIR=<scratchpad>
    set PYTHONPATH=docs\testing\e2e\scripts\common
    <venv>\Scripts\python.exe docs\testing\e2e\scripts\run-A\t1.py

Environment variables (all optional): `E2E_BASE` (server URL), `E2E_REPORTS` (reports dir),
`E2E_CONTEXT` / `E2E_SECRETS` (context and secrets JSON, default `reports/2026-10-26-*.json`),
`E2E_RUN_DIR` (where screenshots go).

`auth.json`: `{"master": {"user": <login response user>, "token": "..."}, "admin": {...}}`, made from one
`POST /api/auth/admin/login` per persona (rate limited); `tokens.json`: `{"master": "<token>"}`.
`common/ui.py` injects the session into localStorage (no UI login), `common/common.py` has `api()` and `tok()`,
`common/pe.py` has protocol-editor helpers (`new_protocol`, `add_task`, `add_questionnaire`).

Limitation: study / clinic / protocol ids in `run-A` scripts (study 3 and 4, clinic 4 and 5, protocol 1, 15)
are the ids of this run, listed in `reports/2026-10-26-context.json`; adjust them for another database.
Site tokens are read from the secrets file only.

run-A scripts: p1-p5b create users, studies, clinics, runs-at; b1 / b2 build the speech and video protocols;
p10 / p11 translate the CS variant; cfg checks and saves `/site-config`; l1-l7 library flow; e1 / u1 editors and
users; a1-a3 received persona (admin) API and UI checks; t1-t3 route tour, redirects, preview; i1 i18n tour;
r1 1024x768 layout; dbg2 real UI login and reload check; p6-p9 probes of the editor.

run-A2 scripts (personas with passwords; `E2E_PASSWORD` env var, `E2E_HEADLESS=1` for headless, `E2E_RUN_DIR` for screenshots):
`login.py` (one login per persona -> auth.json / tokens.json in E2E_STATE_DIR), `probe.py` (visible clinics/studies/library per persona),
`api_runsat.py` (§10.6 API, restores state), `api_editors.py` (§10.7, creates a throw-away study with the creator persona; running it twice
creates a second study), `api_library.py` (§10.16 with two users; `E2E_LIB_NAME` for the entry name), `api_scope.py` and `scope_data.py` (§10.8;
the second needs recordings), `tour.py` (persona route tour with screenshots), `ui_runsat.py`, `ui_editors.py`, `ui_library.py`
(`E2E_WITHDRAW=1` withdraws the creator's entry), `received_checks.py` (§10.4 API 403s), `i18n_personas.py` (CS/DE).
Ids come from the context file; `a2ids.json` / `a2lib.json` in the state dir hold the throw-away study and library ids.
The context file now has a UTF-8 BOM: scripts open it with `utf-8-sig`. `common/ui.py` honours `E2E_HEADLESS`.

run-06 scripts (upload and the server record, see `run-06/README.md`): `common06.py` helpers; `api_check.py`, `api_downloads.py`, `devices_check.py`, `reveal_check.py` (HTTP checks of the recordings,
downloads with SHA-256 and ffprobe, computers, reveal); `ui_recordings.py`, `ui_activity.py`, `ui_scope.py` (Playwright: list, detail, masked fields, activity, persona view);
`resend.py` (curl re-send 200 and 409); `scope_data.py` (A2 10.8 with data, fixed copy of the run-A2 script).

run-07 scripts (quarantine and computer assignment, see `run-07/README.md`): `env.sh` (Git Bash environment), `common07.py`, `server_state.py` (computers, recording, attempts), `ui_explore.py`, `ui_review_computers.py`, `ui_assign.py`, `ui_after_assign.py` (Playwright),
`zip_compare.py` (ZIP SHA-256 server vs local), `set_token.py` (writes the clinic token into `settings.json` with the app stopped; secrets never printed), `paste_token.ps1` (clipboard paste, did not work over RDP).

run-08 scripts (errors and robustness, see `run-08/README.md`): `mic-privacy.ps1` (read/set the microphone privacy value), `start-app.ps1` / `wait-ready.sh` / `stop-app.ps1` (start, wait for ready, stop the app), `kill-app.ps1` (simulated crash of the app JVM), `player.ps1` (pause/resume wmplayer by process suspend; the endpoint volume does not silence the cable), `signal-check.ps1` (ffmpeg volumedetect on the cable), `server_check.py` (server record, attempt count and ZIP SHA-256 per session).

run-09 scripts (window sizes and layout, see `run-09/README.md`): `tree_check.py` (C1-C3 checks on semantic-tree dumps per screen and size), `redact_tree.py` (blank tokens in Settings dumps), `move-window.ps1` (move the SHARE window to 0,0 before the full-screen size), `minimize-restore.ps1` (minimise and restore for the resize-during-use check). Reused: `run-08/start-app.ps1`, `wait-ready.sh`, `stop-app.ps1`, `run-02/front.ps1`, `run-02/redact.ps1`, `tools/Drag-InWindow.ps1`.

run-12 scripts (targeted fix re-test, see `run-12/README.md`): `check_session.py` (read-only check of processing status, clips, ffprobe durations and ZIP entries of a session folder). Reused: `run-08/start-app.ps1`, `wait-ready.sh`, `stop-app.ps1`, `mic-privacy.ps1`, `run-02/front.ps1`.
