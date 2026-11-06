# App logs: current state and proposed upload contract

**Status: proposal.** Nothing here is implemented, on the app or on the web. It answers the question in `task_protocoller_web_app/docs/notes/notes_for_agent_app_web.md`, and is the starting point for building the feature on both sides.

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

## Proposed endpoint

```
POST /device-logs        multipart/form-data, file part last
```

| Part | Meaning |
|---|---|
| `siteToken` | The credential, as for `/upload` and `/device-checkin` |
| `installationId` | Which computer sent it |
| `appVersion` | The sending build |
| `utcOffset` | The computer's offset, e.g. `+01:00`, so the server can place the timestamps |
| `fileName` | The rotated file's name; validated against `app.<date>.<n>.log.gz` |
| `sha256` | Hash of the file, also used for idempotency |
| `file` | The file itself |

| Response | Meaning | App behaviour |
|---|---|---|
| 200 | Stored, or already stored (same `installationId` + `sha256`) | Mark as sent |
| 401 / 403 | Invalid or revoked token | Keep the file, try again later |
| 409 | The computer belongs to another clinic (same rule as check-in) | Keep the file, try again later |
| 413 | File too large | Mark as not sendable, log it, move on |
| 429 / 5xx / network | Temporary failure | Keep the file, try again later |

### App side

- Send only rotated `.gz` files. Logback holds `app.log` open.
- Don't put logs in the session ZIP: the server returns 409 if a `sessionId` arrives with different bytes, and logs would become tied to participant data and session access rights.
- Keep a local record of what was sent. Files older than 14 days are deleted, so a computer that's offline for longer loses them.
- Don't compete with a recording. There is no app-wide "session active" signal yet, so one is needed if sending is automatic.
- The app has no periodic network task today, so this would be a new spec decision. Decision 34 removed the upload worker.
- `updater.log` is out of scope: it's a separate file with no rotation.

### Web side

- Mount the route before the JSON parsers, as `/upload` is.
- Add its own nginx location, or confirm files stay under the 6 MB `/api/` limit.
- Add a `device_logs` table to the schema scripts.
- Extend `fileStore.js`, whose `KEY` pattern only allows session ZIPs.
- Export `belongsToOtherSite` from `deviceController.js` and reuse its rule. Refuse computers with no clinic rather than building a second quarantine.
- Add a `logs` tab to the clinic page: `TABS` in `ClinicPage.jsx`, modelled on `ClinicComputersTab.jsx`.

## Decisions needed before building

| # | Decision | Options | Suggestion |
|---|---|---|---|
| 1 | Who can view logs? "Privileged users" isn't an existing concept; the roles are `master` and `admin`, with clinic access through `user_sites`. | Master only; master plus clinic creator; anyone with clinic access | Master only: matches computer management, and logs hold stack traces and device details |
| 2 | Automatic or on request? | Automatic for rotated files; a "send logs" action | Automatic captures what led up to a problem, but needs clear legal grounds. A manual action is easier to justify but misses computers nobody is looking at. |
| 3 | How long does the server keep them? No retention policy exists. | Match the app's 14 days; longer | 90 days, subject to your data-protection requirements, with a total storage cap and a decision on backups |
| 4 | HTTPS? | Require it first; accept HTTP for now | Require it: logs and the token would otherwise travel unencrypted |
| 5 | Identifying data still logged on purpose (below) | Keep, or reduce | Decide item by item |

## Identifying data still in the logs

The rule in `Project_Specification.md` bans participant data, session folder names and paths from logs. This round fixed the failure paths known to break it (AC 11–15 of `docs/SPEC-app-notes-round.md` and its review fixes), found by reading the code; no failure path was run live. Repeat that review before sending, because any new log line can reintroduce a leak.

These remain by design, and become more sensitive once they leave the computer:

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
