# Run 14 scripts (web re-test of fix round 3, 2026-10-29)

Playwright (Python, `headless=False`) and API checks against http://192.168.122.183:10001 (web commit 44d8490).
Helpers come from `../common/` (`common.py`, `ui.py`); personas log in with `../run-A2/login.py` (`E2E_PASSWORD=1234`).
`env.sh` sets the env vars (state dir in the scratchpad, run folder, PYTHONPATH) for Git Bash on Windows.
Tokens are read from the secrets file into variables and never printed; `redact.py` masks installation ids in the DOM before screenshots.

| Script | Check |
|---|---|
| `api_checks.py`, `show_keys.py` | 1 (API Z), 2 (no "Auto-created"), 3 (F-086), 4 (F-009 keys), 6 (API counts) |
| `mk_study.py` | creates the throw-away study `E2E 1026 run14 study` (id 7) |
| `c13.py`, `c13b.py`, `c13c.py`, `c13d.py` | 13 (F-006), 11 (F-073, F-076); c13d saves the one protocol (id 28) |
| `c1.py`, `c15.py` | 1 (Last changed), 2 (UI), 10 (F-079); c15 archives the study |
| `c11.py` | 11 (F-068 dialog EN/CS/DE, cancelled) |
| `c6.py`, `c7.py`, `c8.py` | 6 (F-097), 7 (F-096), 8 (F-087) |
| `c9.py`, `c9b.py` | 9 (F-074) |
| `c12.py`..`c12d.py` | 12 (F-037, F-111, F-036, F-035) |
| `c14.py`, `c14b.py` | 14 (CS/DE spot check) |

Run order is as numbered; ids of the throw-away study are in `$E2E_STATE_DIR/ids14.json`.
