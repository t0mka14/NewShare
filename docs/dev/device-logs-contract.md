# App logs: background for the upload contract

**Status: contract fixed 2026-11-10, being implemented.** This page is background: what the app logs and why. The contract itself lives in the web repo (see "Contract" below).

## Are the logs sent today?

**No.** App logs stay on the computer that wrote them.

- No app request carries them: not session upload, config fetch or device check-in.
- The session ZIP doesn't contain them either.
- The web has no endpoint for them. `POST /logs/frontend` is for browser logs only: it is public, limited to 16 KB, and has no link to a computer, so it can't be reused.

The closest existing thing is the `hardware` diagnostics inside `examination.json` (see `upload_data.md`). These are per-session, and the web shows them with the recording.

## What the app has

| Topic | Today |
|---|---|
| Files | `${LOG_PATH}/app.log`, rotated to `app.<yyyy-MM-dd>.<n>.log.gz` |
| Limits | 10 MB per file, 14 days, 50 MB in total |
| Timestamps | Local time with no offset, so the server needs the offset reported separately |
| Rotation | Happens on the next log event, so a rotated file can appear days late |
| Computer identity | `installationId`, a random UUID in `config/settings.json`; the readable name is the web's `devices.label` |
| Credential | The clinic's site token, shared by all of its computers. A token holder can claim any `installationId` in that clinic, so attribution to a computer is not proof. |
| Transport | `WEB_SERVER_BASE_URL` is plain HTTP today |

## Contract

The contract is fixed. It is normative in the web repo: `task_protocoller_web_app/docs/device_logs/01_spec.md`, with the app-side summary in `03_desktop_brief.md`. The round spec is [`../SPEC-device-logs.md`](../SPEC-device-logs.md).

Decided on 2026-11-10:
- Only a master can view logs.
- Rotated files are sent automatically, after a config refresh and after an upload batch, never during a session.
- The server keeps logs for 90 days, with a total storage cap.
- Transport is the same as `/upload`.
- There is no new redaction: the server is the same secured server as the session data, so the items below stay as they are.

## Identifying data still in the logs

The rule in `Project_Specification.md` bans participant data, session folder names and paths from logs. This round fixed the failure paths known to break it (AC 11–15 of `docs/SPEC-app-notes-round.md` and its review fixes), found by reading the code; no failure path was run live. Repeat that review before sending, because any new log line can reintroduce a leak.

These remain by design (kept, decision of 2026-11-10):

| Logged | Where | Why it matters off the computer |
|---|---|---|
| `sessionId` | Throughout | The server can link it to `patient_code` through `upload_sessions` |
| Site name | `RefreshConfigurationUseCase` | Identifies the clinic, which the server knows anyway |
| Microphone and camera names | `SessionComponent`, `MicGainApplier`, `JvmAudioInputGainControl`, `FfmpegSessionVideoRecorder` | Can include a person's name |
| ffmpeg path | `FfmpegBinaryLocator` | May contain a Windows user name |
| Example audio URLs | `KtorExampleAudioCache` | Server addresses, low risk |

**Known limits of `redactPaths`:**
- A path with spaces is cut at the spaces, so a final fragment with no separator can survive, e.g. `Novak` from `C:\Users\Jan Novak`.
- A bare file name with an extension outside the session list (e.g. `.bak`) survives; no current exception message was found to build one.
- A slash-formatted ID with up to 5 digits before the slash and 4 after, e.g. `12345/678`, looks like a frame-rate ratio and survives. Czech birth numbers have 6 digits before the slash, so they are redacted.

**Docs disagree on `installationId`:** the spec calls it a bearer credential that must not be logged, while `AppSettings.kt` and `config_alignment_decision_table.md` say it isn't one. It must stay out of log lines either way; this contract sends it only as a request field.

## Testing

- App: `MockEngine` for the Ktor client; a fake with a `calls` list and `nextResult`, as in `FakeDeviceCheckinApi`; `captureLogs` to assert nothing secret is logged.
- `tools/mock-server` still uses the old contract and would need this endpoint added.
