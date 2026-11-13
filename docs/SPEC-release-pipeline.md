# SPEC: release pipeline (desktop app → malenia, served from share_web)

Status: approved 2026-11-13. Increments tracked at the bottom.

## Context
The desktop updater works, but nothing can serve it. `GET http://192.168.122.183:10001/api/version/latest`
returns 404 (checked 2026-11-13), because port 10001 now runs the real web backend
(`../task_protocoller_web_app`, Express 5 behind nginx) instead of the mock server, and that backend
has no version route. The app's server URL is also a hard-coded constant, and packaging is
linux-only. Goal: one command builds a Windows release pointed at
`https://malenia.feld.cvut.cz/koubic` and publishes it to the backend in `share_web`. malenia
reverse-proxies `/koubic/` to that container with the prefix stripped
(`docs/container_deployment.md:12` in the web repo), so installed clients then update themselves.

Decisions taken with the user: **Windows x64**, **native GraalVM `updater.exe`** (install the MSVC
Build Tools), **publish by scp to a release dir** (no upload API).

```
Goal:        A clinic PC with SHARE installed gets every new release automatically from malenia.
Deliverable: (a) a version/download API in the web backend, deployed to share_web;
             (b) a Windows release build of the desktop app with a configurable server URL;
             (c) tools/release/release.sh <x.y.z> [--server URL] that builds, publishes and verifies.
```

### Acceptance criteria (checkable)
1. Backend `GET /api/version/latest?platform=windows-x86_64` returns 200 with
   `{"release":"<newest x.y.z>","components":[{id,target,url,checksum}]}`. With no release it
   returns 404 `{"error":"no release published"}`, a bad platform gets 400, and an unknown
   platform gets 200 with `components: []`. This is the same contract as `tools/mock-server/server.py`.
2. `GET /api/version/download/<v>/<artifact>` streams only files declared in that release's
   manifest (plus the manifest and install bundle), and returns 404 for anything else, including traversal.
   Backend vitest tests cover criteria 1 and 2, and `npm test` is green.
3. `./gradlew -PappVersion=X -PserverBaseUrl=https://malenia.feld.cvut.cz/koubic :packaging:releaseWindowsX64`
   produces `packaging/build/release/X/` containing `app.zip`, `runtime-windows-x86_64.zip`,
   `ffmpeg-windows-x86_64.zip`, `install-windows-x64.zip`, `manifest-windows-x86_64.json` and
   `.sha256` sidecars. The install bundle contains `updater.exe` (native, with no console window).
   Its `updater.properties` holds `https://malenia.feld.cvut.cz/koubic/api/version/latest`.
   The built `app.jar` uses `https://malenia.feld.cvut.cz/koubic/api` for config, upload, check-in and logs.
   Without `-PserverBaseUrl`, both still default to the test container.
4. `tools/release/release.sh X` builds, uploads to `share_web:/srv/task-protocoller/releases/X/`
   (atomic: copy to `.incoming-X`, verify sha256 on the remote, then `mv`), and finishes by asserting
   that the live endpoint reports `release: X` with checksums equal to the local sidecars.
5. End to end on this Windows machine against the test container: install 1.0.0 from
   `install-windows-x64.zip`, publish 1.0.1, start `updater.exe`. `installed.json` then says 1.0.1,
   the app launches showing 1.0.1 in its title, and only the components that changed were downloaded.
6. `:app:test :updater:test :shared:test` are green, and existing linux packaging is unchanged.

### Verification plan
- Criteria 1–2: vitest + supertest on a temp release dir, then `curl` against `192.168.122.183:10001` after deploy.
- Criterion 3: unzip and inspect the bundle, `strings`/run `app.jar` with the logged base URL, plus a
  `BuildInfoTest`-style test that the generated server-URL resource reaches the classpath.
- Criteria 4–5: real run, comparing `updater.log`, `installed.json` and the window title.
- Final: `/verify` with an independent critic. malenia is **unreachable from this machine** (curl
  timed out). The HTTPS check through malenia (`curl https://malenia.feld.cvut.cz/koubic/api/version/latest?platform=windows-x86_64`)
  must be run by the user from a network that reaches it.

### Key decisions
- **Download URLs: the server emits them relative (`download/<v>/<artifact>`), and the updater resolves
  them against its endpoint URI** (`URI.resolve`, so absolute URLs still work). One server then
  serves both the internal :10001 address and malenia's `/koubic` prefix, which the backend cannot
  see because malenia strips it. Rejected: a server-side public-URL env var (it would serve only one of the two origins).
- **Release files live in `/srv/task-protocoller/releases`** (env `RELEASES_PATH`). It sits outside
  `/opt` (rsync `--delete` is safe there), is read-only for the backend (no `ReadWritePaths`
  change), and is not in the DB. A new version directory is picked up on the next request, with no restart.
- **Downloads are streamed by Node** (reusing the range logic in `backend/src/controllers/downloadController.js`)
  behind a new nginx `location /api/version/download/` with `proxy_buffering off`. Rejected: nginx
  `alias`, which would lose the manifest allowlist and the tests.
- **Server URL at build time**: `-PserverBaseUrl` → a generated resource read by `ServerEndpoints`,
  following the `GenerateAppVersionJson`/`BuildInfo` pattern (`app/build.gradle.kts:115-144`).
  `updateEndpoint` defaults to `${serverBaseUrl}/api/version/latest`.
- **Windows console**: link `updater.exe` with `/SUBSYSTEM:WINDOWS` (it already logs to a file), and
  start the app with `javaw.exe` (`AppLauncher.kt:38`).

### Increments (each ends at a checkpoint)
1. **Backend version API** (web repo, branch off `newshare`): `backend/src/releases/releaseStore.js`
   (latest x.y.z directory, manifest, sha256 sidecars, allowlist) + `routes/version.js`, mounted in
   `server.js` next to the public routes. Add `RELEASES_PATH` to `.env` docs, plus tests.
   → checkpoint: `npm test` green and the diff reviewed.
2. **Deploy backend to share_web** (a shared server, so this needs the user's go): rsync per
   `docs/container_deployment.md`, add the nginx block, `install -d /srv/task-protocoller/releases`,
   restart. → checkpoint: curl to :10001 gives `404 no release published` (the route exists).
3. **Desktop server URL + updater fixes**: `serverBaseUrl` property/resource, relative-URL resolution
   in the updater, `javaw`, tests. → checkpoint: the three test suites green.
4. **Windows packaging**: the user installs VS 2022 Build Tools (C++ workload). Parameterize the
   platform in `packaging/build.gradle.kts` (hard-coded at line 255, `unix("0755")`, the tar bundle, the
   `updater` vs `updater.exe` copy at line 112), add `releaseWindowsX64`, make `repairGraalvmLaunchers`
   linux-only, and add the `/SUBSYSTEM:WINDOWS` link option. → checkpoint: the criterion 3 bundle inspected.
5. **Pipeline script** `tools/release/release.sh` (build → atomic scp via `ssh share_web` → live
   verify), replacing the mock-only `publish.sh` flow (keep the old one for the mock). → checkpoint:
   publish 1.0.0 to the test container.
6. **End-to-end update test** (criterion 5), using the test-container URL. Then a malenia build is published as
   the real release. → checkpoint: the user runs the malenia curl.
7. Docs: `docs/dev/packaging-and-deployment.md`, `docs/dev/updater.md`, and the web repo's
   `docs/container_deployment.md`. Then `/verify`.

Work runs in worktrees in both repos, committed per increment. Implementation goes to Sonnet engineer
agents (integration-engineer for the backend, updater and pipeline), with review and tests by me (memory: E2E fix workflow).

### Assumptions (veto any)
- Publishing to share_web is the same as publishing to malenia (malenia proxies to it). malenia's proxy
  passes ~80 MB downloads and has enough timeout. Unverified, and the user checks it.
- The pipeline runs on this Windows machine in Git Bash (ssh, scp, sha256sum, curl available) with the
  `share_web` ssh alias. VPN MTU 1400 is already set (large transfers hang otherwise).
- Clinic installs go to a user-writable folder (the app writes `data/` beside itself), so the install
  is a zip and not an MSI.
- No signing for now: SmartScreen will warn on the first run of the unsigned `updater.exe`.
- Releases are public (no auth on version/download), the same as the mock. The artifacts contain no secrets.

### Out of scope / follow-ups
- **Site-config audio URLs**: the backend builds them from `ASSET_BASE_URL` (the internal address), so
  malenia-configured clients would silently skip example audio. Separate fix (relative or per-origin URLs).
- MSI/installer, code signing, macOS/Linux release through this pipeline, an admin UI for releases,
  rollback/yank of a published release (manual delete of the directory for now), `trust proxy` behind malenia.
