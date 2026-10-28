# Run 07 scripts (no token, quarantine, computer assignment)

Git Bash: `. scripts/run-07/env.sh` at the repo root (sets `PY` = Playwright venv python, `E2E_STATE_DIR`, `PYTHONIOENCODING`, `PYTHONPATH` = common + run-07; edit `SCRATCH` if the scratchpad moved).
Run folder default: `reports/2026-10-26-run-07-quarantine` (`E2E_RUN_DIR` overrides); logs are redacted (installation IDs, tokens). Git Bash rewrites a leading `/` in arguments, so routes are passed without it.

- `common07.py`: helpers on top of `run-06/common06.py` (run-07 session from the context file, own installation ID from `app/data/config/settings.json`, `red()` redaction).
- `server_state.py <label>`: computers (`GET /devices`), the recording, its attempts and list row; writes `logs/state-<label>.txt`.
- `ui_explore.py <route> <shot>`: screenshot (installation IDs masked in the DOM) + headings / buttons / test ids of any admin route.
- `ui_review_computers.py <shot>`: follows the "Review computers" banner of the Recordings list (lands on `#/clinics#unassigned-computers`).
- `ui_assign.py <recording|clinics> <prefix> [--confirm]`: opens the assign dialog (recording detail "Assign computer..." or Clinics > Unassigned computers) and with `--confirm` assigns to Clinic A (name from the context file).
- `ui_after_assign.py <prefix>`: recording detail (Upload history opened), Recordings list, Computers tab of Clinic A, Clinics page; screenshots and text dumps.
- `zip_compare.py <dir>`: downloads the recording's ZIP and compares SHA-256 with the local ZIP.
- `set_token.py <secrets key|none>`: writes (or removes) `siteToken` in `app/data/config/settings.json` while the app is STOPPED; the secret is read from the secrets file and never printed. Used because synthetic keyboard input (SendKeys, keybd_event, long-press menu) did not reach the Compose window in this RDP session and the token must not pass through a tool call. The trigger for refresh + check-in is then the app's start-up refresh.
- `paste_token.ps1`: clipboard + Ctrl+V attempt (did NOT work here; kept for reference).

App start/stop: `run-04/start-app.ps1`; `run-02/front.ps1` before screenshots; `run-02/redact.ps1` for the token/installation ID in Settings screenshots (rects `32,<tokenY>,920,56` and `32,<idY>,400,24`).
