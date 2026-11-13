# Handoff 2026-11-13: example-audio links behind malenia

For the next agent. Goal: clinics whose app talks to `https://malenia.feld.cvut.cz/koubic` must get
working example audio (the "play example" button on VOCAL tasks). Today they silently get none.
Read this, then write a short spec with the user (`/spec`) before changing code. The fix touches
both repos and the shared server.

## Background you need

- The desktop app is now released through `docs/dev/releasing.md`. Production builds use
  `serverBaseUrl=https://malenia.feld.cvut.cz/koubic`. Release **1.0.1** is live (2026-11-13).
- malenia reverse-proxies `https://malenia.feld.cvut.cz/koubic/` to the `share_web` container
  **with the `/koubic` prefix stripped**. The same container is also reached directly as
  `http://192.168.122.183:10001` (test clients, E2E runs, the admin UI on the internal network).
  One backend therefore serves **two origins**, and it cannot tell from a request which one the
  client used. The prefix is gone and `X-Forwarded-*` are rewritten by the inner nginx.
- The build machine cannot reach malenia (requests time out). Anything through malenia must be
  checked by the user.

## The bug

- **What the backend sends.** `GET /api/site-config/<token>` returns, for VOCAL tasks with an
  example, `"audioExamplePath": "http://192.168.122.183:10001/audio/illustrations/<key>.wav"`.
  - It is built in the web repo, `backend/src/utils/extConfig.js:259-261`:
    ```js
    return assetBaseUrl && key && AUDIO_EXAMPLES.has(key)
      ? `${assetBaseUrl.replace(/\/+$/, "")}/audio/illustrations/${key}.wav` : undefined;
    ```
  - `assetBaseUrl` comes from `process.env.ASSET_BASE_URL` (`backend/src/controllers/siteController.js:414`).
  - On `share_web`, `/opt/task-protocoller/backend/.env` has
    `ASSET_BASE_URL=http://192.168.122.183:10001` (and the same `FRONTEND_BASE_URL`).
- **What the app does with it.**
  - It downloads every `audioExamplePath` when a config is applied: `KtorExampleAudioCache.sync`,
    `app/src/main/kotlin/org/example/app/infrastructure/network/KtorExampleAudioCache.kt`.
  - It plays the local copy (`SessionComponent.kt:322`).
  - `fileNameFor` (`KtorExampleAudioCache.kt:131`) accepts only absolute `http(s)` URLs with a
    host. The cache file name is `sha256(url).<ext>`.
  - A failed download means no example button, logged as "example audio skipped". There is no
    error for the user.
- **Result.** A clinic PC outside the internal network gets the internal IP, cannot reach it,
  and shows no example button. Everything else works, because the API base URL is correct.
- **What's actually reachable.** The files are served by the frontend's nginx root
  (`/opt/task-protocoller/frontend/audio/illustrations/*.wav`). So
  `https://malenia.feld.cvut.cz/koubic/audio/illustrations/<key>.wav` should work through malenia.
  That's unverified; ask the user to curl it.

## Why the obvious fixes are wrong

- **Set `ASSET_BASE_URL=https://malenia.feld.cvut.cz/koubic` on the server.** One line, but it
  breaks audio for every internal client (test container builds, E2E runs, which can't reach
  malenia). It also bakes the public origin into server config again. At most an emergency
  stop-gap, and only with the user's OK.
- **Derive the origin from the request.** Impossible, for the reason above (the prefix is stripped).

## Recommended fix (same idea as the update URLs)

The update endpoint already solved this exact problem. The backend returns **relative** download
URLs (`download/<v>/app.zip`), and the client resolves them against the base URL it was configured
with (`updater/.../HttpVersionFetcher.kt`, `resolveComponentUrls`). Do the same for audio.

1. **App** (this repo):
   - Accept a relative `audioExamplePath`, for example `audio/illustrations/phonation_a.wav`.
   - Resolve it against the build's server base, `SERVER_BASE_URL` in
     `app/src/main/kotlin/org/example/app/infrastructure/network/ServerEndpoints.kt`
     (internal, no `/api`, no trailing slash): `URI("$SERVER_BASE_URL/").resolve(path)`.
   - Absolute `http(s)` URLs keep working unchanged.
   - Resolve **once, where the config is applied**, before `exampleAudioUrls()`
     (`domain/config/ExampleAudioCache.kt:30`), or inside the cache: `sync`, `localFileFor` and
     pruning must all see the same resolved string, because the cache key is the URL.
   - Reject anything that does not resolve to http(s), as `fileNameFor` does today.
   - Unit tests: relative, absolute, a leading `/` (decide: treat it as relative to the server base,
     not to the host root, or `/koubic` is lost), and `..` segments.
2. **Backend** (web repo):
   - Emit the relative path instead of `${assetBaseUrl}/…`.
   - Old clients only understand absolute URLs. 1.0.0 and 1.0.1 are installed in clinics, and
     internal test builds rely on audio. So switch on the version the client reports:
     site-config is called with `?installationId=…&appVersion=…` (`KtorConfigApi.kt`; the backend
     already reads it for device registration).
     - Clients at or above the first app version that resolves relative paths get relative paths.
     - Older ones keep the absolute `ASSET_BASE_URL` form, which is no worse than today.
   - Update `backend/src/utils/extConfig.js` and its tests.
3. **Specs/docs:**
   - `docs/Task_Configuration_JSON_Spec.md:184` and `docs/Project_Specification.md:1447` say
     "an absolute `http(s)` URL". Amend them to allow a path relative to the server base.
     Ask the user or tech-lead before amending the project specification.
   - Also note it in the web repo's `docs/device_logs`-style desktop brief, if one covers site-config.
4. **Order of rollout:**
   1. Release the app (the next version, e.g. 1.0.2) with relative-path support, via `docs/dev/releasing.md`.
   2. Then deploy the backend change.
   The version gate makes the order safe either way, but releasing the app first lets you test
   against real data.

## Things to watch

- **The cache is keyed by URL.** A client that switches from the absolute to the resolved
  relative form downloads each example once more and prunes the old file. That's fine; just
  don't let the key flip on every refresh. Resolve deterministically.
- **Tests that use the test-container URL** (E2E configs under `docs/testing/e2e/reports/*/site-config-*.json`)
  contain absolute internal URLs. Keep supporting absolute.
- **`share_web` is shared.** Deploying the backend needs the user's go-ahead. Follow the web repo's
  `docs/container_deployment.md` ("Routine redeploy", the tar variant on Windows). Before
  overwriting, check that the server's backend matches `newshare`. On 2026-11-13 it matched
  `newshare` at `2751c6c` exactly.
- **Version comparison on the backend:** `appVersion` arrives as a string. Use plain `x.y.z`
  integer comparison (the same rule as the release store's `latestRelease` in
  `backend/src/releases/releaseStore.js`). An unparseable or missing value means an old client,
  so send absolute URLs.
- **Commit and branch conventions:**
  - Desktop work happens on `old_ui`, web work on `newshare`.
  - Use worktrees, and branch from the right base. `EnterWorktree` starts from `origin/master`,
    which is old, so move it onto `old_ui` first.
  - Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Done when

1. An app build with `serverBaseUrl=https://malenia.feld.cvut.cz/koubic`, given a config with a
   relative `audioExamplePath`, resolves it to `https://malenia.feld.cvut.cz/koubic/audio/illustrations/<key>.wav`
   (unit test), and still accepts absolute URLs.
2. The backend returns relative paths to clients at or above the new version, and absolute ones to
   older or unknown clients. Covered by `extConfig` tests; `npm test` green.
3. Against the test container, a new-version test build gets audio: the example button plays.
   A 1.0.1 client still gets the absolute URL.
4. The user confirms, from outside the internal network, that a malenia-configured install shows
   and plays the example (or curls the resolved `.wav` URL with a 200).
5. Spec docs amended; `./gradlew :app:test :updater:test :shared:test` green.
