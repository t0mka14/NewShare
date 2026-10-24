# Server integration: what the desktop app still needs

Version 1.0, 2026-10-22

The web server side of session upload is built and running (phase B of the web app, deployed to
the test container `http://192.168.122.183:10001`). This document lists what the desktop app still
has to change to work with it fully: the exact request changes, how to read every response, and
where each change lands in this repo.

The server's normative contract lives in the web repo:
`task_protocoller_web_app/docs/upload_management/specification.md` (§3 upload, §6 devices, §12
desktop follow-ups). The archive format is unchanged: [`upload_data.md`](upload_data.md) still
describes exactly what the server reads.

## Summary

| # | Change | Why | Seam |
|---|---|---|---|
| 1 | Send `siteToken` and `appVersion` as upload parts | Without the token, the server can only attribute an upload to a device it already knows. A first upload from a new device is **quarantined** (404). | `UploadApi.uploadSession`, `KtorUploadApi`, `UploadSessionUseCase` |
| 2 | Raise the upload request timeout (2 min today) | Real sessions are hundreds of MB. Anything slower than 2 min fails on the client, even though the server accepts long uploads. | `KtorUploadApi` (`HttpTimeout`) |
| 3 | Map the new response codes (409, 503, 507, 404 quarantine) | 409 is currently a retryable `ServerError`, but retrying can never succeed. | `KtorUploadApi.uploadSession`, `UploadResult` |
| 4 | Send `installationId` and `appVersion` on the config fetch | Registers the device at the site **before** it uploads anything, and lets the admin UI show its app/config version. | `KtorConfigApi` (`configPath`) |
| 5 | Call `POST /device-checkin` after each config fetch and each upload batch | The admin "Devices" page shows how many sessions are waiting or failed on each clinic computer. Without check-ins the server only learns about a device when it uploads. | new `DeviceCheckinApi`, called from `RefreshConfigurationUseCase` and the upload screen |

Items 1–3 matter for correct uploads. Items 4–5 give admins visibility; the upload path works
without them.

## 1. Upload request: two more parts

`POST {baseUrl}/upload` (on the test container: `/api/upload`), `multipart/form-data`. The text
parts must still come **before** the `zip` part, as `KtorUploadApi` already sends them:

| Part | Status | Value |
|---|---|---|
| `installationId` | unchanged | `InstallationIdProvider` value (dashed UUID) |
| `sessionId` | unchanged | 32 lowercase hex |
| `zipSha256` | unchanged | 64 lowercase hex |
| `siteToken` | **new** | `AppSettings.siteToken`, the same token used for `GET /site-config/{token}`. 16–64 chars `[A-Za-z0-9_-]`. Omit the part if no token is set. |
| `appVersion` | **new** | `BuildInfo.appVersion` as text, ≤ 32 chars, no control characters |
| `zip` | unchanged | the archive, `application/zip`, ≤ 4 GiB (server default) |

The server rejects any other part name, a duplicated part, or a text part after the zip with
`400`. The token is a credential: like the installation ID, it must never appear in logs (§11).

**Device and site rules on the server**, which explain the 404 cases below:

- A valid token **adopts** a device that has no site yet.
- A valid token **never moves** a device that already belongs to a different site. Such an upload is
  quarantined. Moving a clinic computer to another clinic is done by a web master
  (`PUT /devices/:id/site`), not by entering another token.
- With no token, the device's known site is used. An unknown device is quarantined.

## 2. Upload timeout

`KtorUploadApi` installs `HttpTimeout { requestTimeoutMillis = 120_000 }`. That caps the whole
upload at 2 minutes. 239 MB (the size of the `upload_data.md` example session) needs about
3.5 minutes at 10 Mbit/s.

The server and its nginx allow 10 minutes without progress and no limit on the total duration. So:

- Set `requestTimeoutMillis` to `HttpTimeout.INFINITE_TIMEOUT_MS`, or to a value derived from the
  ZIP size (for example `size / (1 Mbit/s) + 60 s`).
- Set `socketTimeoutMillis` (an idle timeout) to something like 120 s, so a stalled connection
  still fails.
- Keep `connectTimeoutMillis` at 10 s.

## 3. Responses and how to map them

The receipt body on success is unchanged in shape: `{"status":"ok","sessionId","bytes","sha256",
"receivedAt"}`. Store it verbatim, as today.

| HTTP | Body `error` | Meaning | Map to | Examiner sees |
|---|---|---|---|---|
| 200 | — | Accepted. **Also** returned for a re-send of the same `sessionId` with identical bytes (`zipSha256` equal), with the original receipt. | `Success` | uploaded |
| 400 | `bad_request`, `checksum_mismatch`, `too_large` | Malformed request, or the bytes didn't hash to `zipSha256` (truncated transfer) | `ServerError(400)`, retryable | "upload failed, try again" |
| 404 | `device_not_registered` (`quarantined: true`) | Stored, but the device has no site: no or invalid token, or a token of a different site than the device's. A web master must assign the device. | `Rejected` (keep the current text, see the note) | "this computer is not registered to a site; contact your administrator" |
| 409 | `session_exists` | This `sessionId` is already on the server **with different bytes**. Re-sending can never succeed. | **new terminal outcome** (see below) | "already on the server with different content; contact your administrator" |
| 413 | — | nginx body limit (over 4 GiB) | `Rejected` | "session too large" |
| 429 | message | rate limit (300 uploads / 15 min per IP) | `ServerError(429)`, retryable | "server busy, try later" |
| 503 | `quarantine_full` | Quarantine storage limit reached. Nothing was stored. | `ServerError(503)`, retryable later | "server busy, try later" |
| 507 | `insufficient_storage` | Server disk below its free-space floor. Nothing was stored. | `ServerError(507)`, retryable later | "server busy, try later" |
| 5xx | `internal` | other | `ServerError`, retryable | "upload failed, try again" |

Today `KtorUploadApi.isRejection()` treats only 401, 403 and 404 as rejections, so **409 becomes a
`ServerError` that invites a retry which can never work**. Add a distinct terminal result, for
example `UploadResult.Conflict`, or `Rejected` with a reason that says the session already exists
with different content. Show it as something the examiner can't fix by pressing Upload again.

**Quarantine is recoverable.** A quarantined session is kept on the server. Once a master assigns
the device to a site, its quarantined sessions move there and are re-validated. Pressing Upload
again for a session that got 404 then returns **200 with the original receipt**, because it is the
same `sessionId` with the same bytes. So a 404 session should stay eligible for a manual retry, which
is what `Failed` already does today.

**Idempotency note for `upload_data.md` §1.** That document says the server "should reject a
`sessionId` it has already accepted". The server is more lenient: identical bytes get 200 and the
original receipt, and only different bytes get 409. An app that lost its `upload_status.json` can
therefore safely re-upload.

## 4. Config fetch: identify the device

`GET {baseUrl}/site-config/{siteToken}?installationId={id}&appVersion={version}`

Both query parameters are optional, and invalid values are ignored silently. The config response is
unchanged. With them, the server records:

- the device under the token's site (only if the device has no site yet, as in §1)
- its app version
- the `configVersion` it was served, and when

This is how a clinic computer appears on the admin "Devices" page before its first upload.

Seam: the `configPath` closure in `KtorConfigApi` (packaging-and-deployment §6 already names it as
the place where the installation ID goes). Add the query string there and URL-encode both values.

## 5. Device check-in (new endpoint)

`POST {baseUrl}/device-checkin`, `Content-Type: application/json`:

```json
{"siteToken":"…","installationId":"6f933f6e-c07a-414f-9800-47b37253d409",
 "appVersion":"1.4.0","pending":3,"failed":1}
```

| Field | Meaning |
|---|---|
| `pending` | sessions processed and waiting to upload (`NotUploaded` in `EligibleUploadsQuery`), integer 0–100000 |
| `failed` | sessions whose last upload attempt failed (`Failed`), integer 0–100000 |

| HTTP | Body | Meaning |
|---|---|---|
| 200 | `{"status":"ok"}` | recorded |
| 400 | `{"error":"invalid_request"}` | a field is missing or out of range |
| 404 | `{"error":"invalid_token"}` | unknown or deactivated site token |
| 409 | `{"error":"device_belongs_to_another_site"}` | this computer is registered to a different site than the token's. Nothing is recorded. Worth showing in Settings: "This computer is registered to another site." |
| 429 | message | rate limit (120 / 15 min per IP) |

When to call it: after every successful config refresh, and after every upload batch on the upload
screen (success or not). It is fire-and-forget. A failure must never block the UI or an upload, and
it should be logged without the token or the installation ID (§11).

What is logged (INFO for normal outcomes, WARN for failures; never the token, the installation ID,
a session folder name or a response body):

| Where | Line |
|---|---|
| config fetch (`KtorConfigApi`) | `config fetch: HTTP 200`, or the failure with its HTTP status or `transport error: <exception class>` |
| config refresh (`RefreshConfigurationUseCase`) | `config refresh: applied\|unchanged configVersion=… site=…`; WARN `network failure, using cached …`; WARN `rejected, <error class \| validation error class names \| HTTP n>` |
| check-in (`KtorDeviceCheckinApi`) | `device check-in: HTTP 200`, or `site token not recognized (HTTP 404)`, `registered to another site (HTTP 409)`, `failed: HTTP n`, `transport error` |
| check-in (`DeviceCheckinUseCase`) | `device check-in: ok \| registered to another site \| site token rejected \| failed \| skipped` |
| upload call (`KtorUploadApi`) | `upload ok for session=…: HTTP 200`, or `rejected` / `conflict` / `failed` / `transport error` with the HTTP status |
| upload attempt (`UploadSessionUseCase`) | `upload attempt: session=… attempt=n outcome=… retry=…` (WARN on failure; a 409 says `retry=none, needs an administrator`, other failures `retry=manual, no automatic retry`) |
| upload batch (`UploadComponent`) | `upload batch: n session(s), n ok, n failed` |

Uploads are manual, so the retry decision is always "manual" and there is no delay.

Implementation sketch: a `DeviceCheckinApi` interface in `domain/`, a `KtorDeviceCheckinApi` in
`infrastructure/network/` next to the other two, with its own short timeout (15 s like the config
fetch). The counts come from `EligibleUploadsQuery`.

## 6. Testing against the server

- Test container: base URL `http://192.168.122.183:10001/api`, site token of the seeded Paris site
  `paris000paris000paris000paris000`.
- Admin UI: `http://192.168.122.183:10001` (log in as `master@test.com` / `1234`). Uploads show up
  under "Nahrávky / Uploads", devices under "Zařízení / Devices".
- Things to check by hand:
  - the first upload of a fresh installation **with** a token is accepted (200) and the device
    appears under Paris
  - **without** a token it is quarantined (404), and becomes 200 on retry after a master assigns the
    device
  - pressing Upload twice on the same session gives 200 both times
  - a session larger than 2 minutes' worth of bandwidth completes (after change 2)
- `tools/mock-server` still implements the old contract (no `siteToken`, 409 for any repeat). Point
  the app at the real server for these checks, or update the mock to the table in §3.
