# Preflight (orchestrator, 2026-10-26)

App commit `7cd7e47`, web repo `ced93e8` (branch `newshare`). Target server: `http://192.168.122.183:10001` (new web build deployed by the user, bundle `index-5Q2Z5Huk.js`).

| Check | Result | Notes |
|---|---|---|
| 3.1.1 Microphone privacy | pass | `Allow` |
| 3.1.2 dshow devices | pass | `CABLE Output (VB-Audio Virtual Cable)`, `OBS Virtual Camera` |
| 3.1.3 Signal on the cable | pass | mean -22.0 dB, max -14.4 dB (wmplayer running) |
| 3.1.4 Camera | pass | ffmpeg grabbed a 171 KB frame from OBS Virtual Camera (not verified visually non-black; run 05 step 6 checks it) |
| 3.2.1 compose-hot-reload MCP | pass | `status` answers `connected:false` (no app yet, expected) |
| 3.2.2 Playwright | pass | new venv in the orchestrator scratchpad, Chromium installed |
| 3.2.3 Admin API login | pass | `master@test.com` works on the container |
| New web UI | pass | login at `#/login` lands on `#/recordings`; nav shows recordings, studies, clinics, library, users (`screenshots/login.png`, `home-master.png`) |
| Server data | note | Paris site config has `protocols: []`: fresh DB, run 01 builds the content |
| 3.3 App data | pass | user approved deletion: `app/data`, four `app/data.e2e-*` dirs and `app/fake-sessions-root` deleted; no app instance running |
| Glossary | note | New web: site = clinic, project = study, device = computer, upload = recording |
