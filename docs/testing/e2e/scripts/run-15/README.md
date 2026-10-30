# Run 15 scripts (web re-test of fix round 4, group C, 2026-10-30)

Playwright (Python, `headless=False`) and API checks against http://192.168.122.183:10001 (web working tree on top of `e20f764`).
Helpers come from `../common/`; personas log in with `../run-A2/login.py` (`E2E_PASSWORD=1234`, run once into `state15`).
`env.sh` sets the env vars (state dir `state15` in the scratchpad, run folder, PYTHONPATH). `redact.py` masks installation ids before screenshots.
Tokens are read from the state dir into variables and never printed.

| Script | Check |
|---|---|
| `c1.py` | 1 (F-090, Connect a computer panel, EN/CS/DE) |
| `c2.py` | 2 (F-070, questionnaire modal EN/CS/DE) and 3 (F-071, Add question button, text-transform); modal cancelled |
| `c4.py` | 4 (F-084 not-available page), 5 (F-098 empty Clinics), 6 (F-101 hidden/unknown clinic) |
| `c7.py` | 7 (F-112 participant panel, recording 03-1) |
| `c9.py` | 9 (F-082 New clinic modal and clinic settings counter; nothing created or saved) |
| `c10.py` | 10 (F-099 placeholder fit at 1440x900) |
| `c11.py` | 11 (F-078 `#/login` while signed in, then Sign out) |
| `mk_study.py`, `c12.py`, `c12ui.py` | 12 (F-100: creates `E2E 1030 run15 study`, API 201/200, UI toast) |
| `c13.py` | 13 (F-109 web tile: camera tile of 05-1 still renders; deferred to run 16) and 8 (looks for a quarantined session: none) |
| `c14.py` | 14 (regression spot checks, console errors) |
| `c99.py` | archives the throw-away study |
