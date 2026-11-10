# Spec: sending app logs to the server

## Changes during implementation

- **Settling time.** A rotated file modified in the last 60 s is skipped (not hashed, not recorded, counted as "not settled"), because Logback's async compressor writes straight into the final `.gz` name. The use case takes an injected clock.
- **Per-file failures.** `DeviceLogResult` has a fourth case, `FileFailed`: a transport exception or an unreadable file. It records nothing and the run goes on with the next file; the file is retried at the next trigger.
- **`RetryLater` ends the run.** It is kept for HTTP answers that concern the whole device (404, 409, 429, 5xx, any other non-2xx except 400 and 413). They hit every following file alike, so the run stops instead of trying each one.
- **Upload trigger order.** `UploadComponent` triggers the log run right after the batch, before the awaited check-in, so leaving the screen during the check-in does not lose it.
- **`utcOffset` type.** `DeviceLogApi.send` takes a `ZoneOffset`; `KtorDeviceLogApi` formats it (`+01:00`, `Z` becomes `+00:00`).

## Context

The app's logs never leave the computer. The user's note (`task_protocoller_web_app/docs/notes/notes_for_agent_app_web.md`) asks for them to reach the server, tied to a computer and readable on the clinic page. Background on what the app logs is in [`dev/device-logs-contract.md`](dev/device-logs-contract.md).

The contract is normative in the web repo:
- `task_protocoller_web_app/docs/device_logs/01_spec.md`
- the app summary in `03_desktop_brief.md` there

**Goal:** rotated app logs reach the server automatically, attributed to the computer, and a master can read them on the clinic page.

**Deliverable:**
- NewShare, branch `old_ui`: the sender, its triggers and tests, plus the endpoint in `tools/mock-server`.
- Web, branch `newshare`: the endpoint, storage, purge, admin API, Logs tab and tests.

## Decisions already made (user, 2026-11-10)

| Topic | Decision | Rejected |
|---|---|---|
| Who views | Master only | Anyone with clinic access |
| When sent | Automatically, rotated files only, after a config refresh and after an upload batch, never during a session | A manual "send logs" button; a periodic worker |
| Retention | 90 days plus a 2 GiB total cap, purged daily | Keep forever; 14 days |
| Redaction | No new redaction: the logs go to the same secured server as the session data | Reducing the identifying data that is logged on purpose |
| Transport | Same as `/upload`; no HTTPS work | — |
| Unregistered device | 404, the app retries later; no quarantine for logs | Quarantining logs |
| Backups | Logs are excluded | — |

## App design

| Piece | Responsibility |
|---|---|
| `domain/logs/DeviceLogApi` | Interface. `send(file, fileName, sha256, siteToken, installationId, appVersion, utcOffset)` returns `Sent \| Unsendable \| RetryLater` and never throws. |
| `infrastructure/network/KtorDeviceLogApi` | Multipart request with the `file` part last. Timeouts and patterns follow `KtorUploadApi`. Errors go through `safeDescribe`. |
| `domain/logs/SendDeviceLogsUseCase` | See the steps below this table. |
| Session-active signal | An app-wide `StateFlow<Boolean>` set by the session lifecycle. Nothing like it exists today. |
| Triggers | Fire-and-forget on the app scope: after `RefreshConfigurationUseCase` succeeds, and after an upload batch in `UploadComponent`. |
| Wiring | `AppContainer` passes the resolved `LOG_PATH`, using the same resolution as `logback.xml`. |
| `tools/mock-server/server.py` | Accepts `POST /device-logs` and returns 200. |

`SendDeviceLogsUseCase` does this, one run at a time behind a `Mutex`:
1. Lists `app.*.log.gz` in the log directory. It never takes `app.log`.
2. Reads the record in `config/device_logs_sent.json` and skips files already recorded as sent or unsendable.
3. Sends the remaining files sequentially, oldest first.
4. Before each file, stops if a session is running.
5. Writes the record atomically after each result.
6. Drops record entries whose file is gone.
7. Does nothing when there is no site token.
8. Logs one summary line with counts only.

## Acceptance criteria

1. Both repos are clean and pushed before the feature work starts. **Done 2026-11-10.**
2. The contract docs exist in web `docs/device_logs/`, and `dev/device-logs-contract.md` points to them.
3. **Server, `/device-logs`:** integration tests cover every response row of web spec §2.3, plus:
   - a resend returns 200 + `duplicate` without a second row or file;
   - the file lands at `DATA_PATH/device-logs/<device_id>/<sha256>.log.gz`;
   - the tmp file is removed on every failure.
4. **Server, purge:**
   - a row older than 90 days and its file are deleted, while a newer one stays;
   - the cap deletes the oldest first;
   - a missing file still drops its row.
5. **Server, admin API:**
   - list, view and download return 404 for a non-master;
   - a master gets the list scoped to `siteId`, gunzipped text from view, and the `.gz` from download;
   - each view and download writes an audit row.
6. **Server, UI:** the Logs tab shows only for a master and lists the files grouped by computer (frontend vitest).
7. **App, `KtorDeviceLogApi`:** a MockEngine test checks the part order (`file` last), every field value, and the status mapping for 200, 400, 413, 404, 409, 429, 500 and a network failure.
8. **App, `SendDeviceLogsUseCase`:**
   - `app.log` is never sent;
   - sent files are not sent again;
   - unsendable files are not retried;
   - a RetryLater file is retried on the next run;
   - a changed hash counts as a new file;
   - with no token, the API is never called;
   - an active session stops the run before the next file;
   - the record survives a restart (a new instance reads it);
   - files go oldest first.
9. **App, triggers:** a successful refresh and an upload batch each start a run; a failed refresh does not.
10. **App, log privacy:** a `captureLogs` test shows that no line from the sender contains the token, the `installationId` or a path.
11. **Red check:** the new tests fail on the old code. The full `:app:test` and the backend and frontend `npm test` pass, checked by counting result files rather than trusting the exit code.
12. **Live check:** a real rotated `.gz` from the app reaches a locally run web backend. The DB row and the file exist, and the master's Logs tab shows the file and opens it.

## Verification plan

- **AC 3–11:** I review the diffs, do the red runs myself and run the suites.
- **AC 12:**
  1. Run the web backend locally and point the app at it.
  2. Put a real rotated file in `LOG_PATH`.
  3. Refresh the config in Settings.
  4. Check the DB row, the file and the Logs tab.
  This needs the compose-hot-reload MCP server; if it is down, it gets fixed first.
- **Before committing:** an independent `/verify` critic.

## Out of scope

- New redaction
- HTTPS
- A "send logs" button
- Log search or parsing on the server
- `updater.log`
- Merging to `master`
