# Preflight (orchestrator, 2026-10-23)

Recorded by the orchestrator before run 01 (plan §3). App commit `59bb063`.

| Check | Result | Notes |
|---|---|---|
| 3.1.1 Microphone privacy | pass | `Allow` |
| 3.1.2 RDP audio / dshow devices | pass | `CABLE Output (VB-Audio Virtual Cable)` and `OBS Virtual Camera` listed |
| 3.1.3 Signal on the cable | pass | mean −22.4 dB, max −9.1 dB (wmplayer looping) |
| 3.1.4 Camera running | pass | ffmpeg frame grab from `OBS Virtual Camera` shows a live video scene, not black |
| 3.2.1 compose-hot-reload MCP | pass, after a fix | Local override needed `-e JAVA_HOME=…` as well (no system JAVA_HOME, no `java` on PATH). Plan §3.2 and F-003 notes updated. User also set `JAVA_HOME` as a user variable. |
| 3.2.2 Playwright | pass | 1.64.1, Chromium 156 launches; venv in the orchestrator's scratchpad. pip saw transient DNS failures (`getaddrinfo failed`) but completed. |
| 3.2.3 Admin API | pending | Run 01 logs in |
| Server reachable | pass | `GET http://192.168.122.183:10001` → 200 |
| 3.3 App data | pass | `app/data` moved to `app/data.pre-e2e-20261023` (config, logs, sessions, app.lock). No app instance running. |
| Secrets gitignored | pass | `docs/testing/e2e/reports/*-secrets.json` matched by `.gitignore` |
