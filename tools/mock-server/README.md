# SHARE mock configuration server

A small demo server that plays the role of the (still unspecified, spec §13 open q1) SHARE backend:
it hands out a configuration JSON per installation ID and accepts session ZIP uploads.

- **Source:** `server.py` — one file, Python 3 standard library only (the container has no pip).
- **Runs on:** the demo Ubuntu container, as `share-mock-server.service` on port 80.
- **Reachable at:** `http://192.168.122.183:10001` (host port 10001 → container port 80).
- **No authentication.** Private-network demo tool only.

## Web UI — `http://192.168.122.183:10001/config`

One page with four sections:

1. **Assignments** — every installation ID that has a config, with its schema/config version, the
   protocols inside it, advisory validation warnings, an enable/disable toggle and a remove button.
2. **Assign a configuration** — enter an installation ID, then either upload a `.json` file or paste
   the JSON into the textarea. The body is stored byte-for-byte and served back unchanged.
3. **Recent app requests** — the last requests the app made, with the installation ID it sent and the
   status it got. Unknown IDs get an "assign…" button that prefills the form — this is how you find
   out which ID a freshly installed app is using.
4. **Received session uploads** — session ID, installation ID, size, SHA-256 verification, download link.

Invalid JSON is rejected outright. Everything else (unsupported `schemaVersion`, a
`recordingsFileName` without `${taskIndex}`, a VOCAL task before CALIBRATION — the rules from
`ConfigValidator.kt`) is stored anyway and shown as a warning, so broken configs can be served on
purpose to exercise the app's error paths.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/config/{installationId}` | **What the app calls.** 200 + config JSON verbatim; 404 if the ID is unknown or has no config; 403 if the assignment is disabled. Both map to `InvalidInstallationId` in the app. |
| GET | `/serve/{installationId}` | Alias of the above (also `/serve?installationId=…`). Convenient for `curl`. |
| POST | `/api/upload` (alias `/upload`) | **What the app calls on upload.** multipart: `installationId`, `sessionId`, `zipSha256`, `zip`. |
| GET | `/health` | `{"status":"ok"}` |
| GET | `/config`, POST `/config`, `/config/delete`, `/config/toggle`, GET `/config/raw/{name}` | web UI |
| GET | `/uploads/{sessionId}/session.zip` | download a received session |
| GET | `/api/version/latest` | **What the updater calls** (spec §9). `{version, downloadUrl, checksum}` for the highest published release; 404 if nothing is published. |
| GET | `/api/version/download/{version}[/{artifact}]` | The release payload. Artifact defaults to `app.zip` (the §9 update package); `install-linux-x64.tar.gz` is the full first-install bundle. |
| GET | `/api/version/releases` | Everything published, for scripts. |

Upload responses: `200` with `{"status":"ok","sessionId":…,"bytes":…,"sha256":…,"receivedAt":…}`
(stored verbatim by the app in `metadata/upload_status.json`); `404` unknown installation ID;
`409` the session ID was already uploaded (once-only, spec decision 11); `400` missing fields or
SHA-256 mismatch. Uploads are streamed to disk, never buffered in memory.

## Data on the container

```
/opt/share-mock-server/
  server.py
  data/
    assignments.json          {installationId: {config, enabled, note, updatedAt}}
    configs/<name>.json       stored configuration bodies (verbatim)
    uploads/<sessionId>/      session.zip + meta.json
    requests.jsonl            rolling log of the last 200 app requests
    releases/<x.y.z>/         published app packages + .sha256 sidecars (see tools/release/publish.sh)
```

Everything survives restarts. To wipe the demo state: `rm -rf /opt/share-mock-server/data` and restart.

## Deploying / updating

```bash
tools/mock-server/deploy.sh          # copy server.py + unit, disable apache2, restart, health check
```

Overridable: `SSH_HOST`, `SSH_PORT`, `SSH_KEY`, `REMOTE_DIR`.

Operations on the container:

```bash
systemctl status share-mock-server
journalctl -u share-mock-server -f     # request log
systemctl restart share-mock-server
```

Run it locally instead (no root needed):

```bash
python3 tools/mock-server/server.py --port 8080 --data /tmp/share-mock-data
```

## Self-test

```bash
python3 tools/mock-server/server.py --selftest
```

Parses the multipart shapes real clients actually send — Ktor's unquoted `name=zip; filename="…"`,
the browser/curl fully quoted form, `Content-Disposition` split across two header lines, and a
chunked body — and asserts the part names, file names and streamed bytes. `deploy.sh` runs it on the
container before restarting the service.

It exists because the first real upload from the app failed with `400 missing fields`: the part-name
regex matched inside `filename="…"`, so the `zip` part was filed under the ZIP's file name. curl
never showed it (curl quotes the name), which is why only the app hit it. A `400` from the upload
endpoint now lists the part names it did receive, in the response and in the request log.

## App side

`KtorConfigApi` and `KtorUploadApi` default their `baseUrl` to `http://192.168.122.183:10001/api`,
so the app talks to this server out of the box. Set the same installation ID in the app's Settings as
the one you assigned here. The real server contract is still open (spec §13 open q1) — when it lands,
only those two constructor defaults change.

## Publishing an app release

Releases are *content*, not code: the server reads `data/releases/` on every request, so
publishing needs no restart (unlike `deploy.sh`, which replaces `server.py` itself).

```bash
./gradlew -PappVersion=1.0.1 :packaging:releaseLinuxX64
tools/release/publish.sh 1.0.1
curl -s http://192.168.122.183:10001/api/version/latest
```

`latest` is the numeric maximum of the `x.y.z` directory names under `data/releases/`, matching
`AppVersion.compareTo` in `:shared` — so `1.0.10` outranks `1.0.9`, which neither string sorting
nor a naive `max()` would get right.

The advertised `checksum` comes from the `.sha256` sidecar beside each artifact when one is
present, and is computed on demand otherwise. That is deliberate: editing a sidecar on the server
is how the updater's checksum-mismatch path (§11) gets exercised without building a corrupt
artifact.

`downloadUrl` is built from the request's `Host` header — the authority the client actually
dialed, which is not the one the service binds (container `:80` vs. published `:10001`).
`--public-base-url` overrides it if something ever fronts this server.
