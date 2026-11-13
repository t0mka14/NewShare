# Releasing a new version

How to build a new Windows release of SHARE and publish it, so that every installed client updates
itself on its next start. Background and design: [`docs/SPEC-release-pipeline.md`](../SPEC-release-pipeline.md),
[`updater.md`](updater.md), [`packaging-and-deployment.md`](packaging-and-deployment.md).

## TL;DR

From Git Bash in the repo root, on the Windows build machine:

```bash
./gradlew -PappVersion=1.0.2 -PserverBaseUrl=https://malenia.feld.cvut.cz/koubic :packaging:publishWindowsRelease
```

It ends with `==> published 1.0.2` and `BUILD SUCCESSFUL`. Anything else means nothing was
published, or the release was withdrawn again (see [When it fails](#when-it-fails)).

## How it fits together

```
build machine (Windows)                share_web container                 clinic PC
-----------------------                ---------------------------          ------------------
gradlew publishWindowsRelease   ssh    /srv/task-protocoller/releases/     updater.exe
  1. server checks  ─────────────────▶   1.0.1/  1.0.2/ ...                  GET .../api/version/latest
  2. build release                       read by the web backend  ◀───────── ?platform=windows-x86_64
  3. release.sh: upload, verify, mv      GET /api/version/latest            downloads changed parts,
  4. live check   ◀───────────────────   GET /api/version/download/<v>/..   starts the app
```

- Clients reach the server as `https://malenia.feld.cvut.cz/koubic`. malenia reverse-proxies that
  path to the `share_web` container (also reachable internally as `http://192.168.122.183:10001`).
  Publishing to `share_web` therefore publishes to every clinic.
- The backend reads the releases directory on every request. No restart, no database.
- A release is three components plus an install bundle: `app.zip`, `runtime-windows-x86_64.zip`,
  `ffmpeg-windows-x86_64.zip`, `install-windows-x64.zip`. Clients download only the components
  whose checksum changed. An app-only release costs them about 80 MB.

## Prerequisites (once per build machine)

| What | Check |
|---|---|
| Windows x64 host (jlink and native-image build only for the host OS) | — |
| Git for Windows (Git Bash: `bash`, `ssh`, `scp`, `sha256sum`, `curl`) | `bash --version` |
| Python 3 on `PATH` (release.sh uses it to read JSON and zips) | `python --version` |
| VS 2022 Build Tools, C++ workload (native `updater.exe`) | `"/c/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe" -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath` prints a path |
| `ssh share_web` works without a password prompt (`~/.ssh/config` alias, root key) | `ssh -o BatchMode=yes share_web true` |
| VPN MTU 1400 (larger MTU makes big uploads hang) | see the web repo's `docs/container_deployment.md` |

Install the Build Tools without winget (elevated PowerShell):

```powershell
$exe = "$env:TEMP\vs_BuildTools.exe"
Invoke-WebRequest https://aka.ms/vs/17/release/vs_BuildTools.exe -OutFile $exe
Start-Process $exe -Wait -ArgumentList '--quiet','--wait','--norestart','--add','Microsoft.VisualStudio.Workload.VCTools','--includeRecommended'
```

## Releasing step by step

### 1. Pick the version

- See what is live: `curl -s 'http://192.168.122.183:10001/api/version/latest?platform=windows-x86_64'`
  (or the malenia URL from a network that reaches it).
- The new version must be plain `x.y.z` and **strictly newer** than the live one. Clients ignore
  anything else, and the pipeline refuses it.
- **Normal release (the app changed):** pass only `-PappVersion=x.y.z`. The release version follows it.
- **Runtime- or ffmpeg-only release:** keep `appVersion` and pass `-PreleaseVersion=x.y.z`. Clients then
  skip re-downloading `app.zip`.
- Never set `releaseVersion` in `gradle.properties`. A pinned value silently overrides every
  `-PappVersion` (it once published app 1.0.1 as release 1.0.0).
- Published versions are immutable. To ship a fix, bump again.

### 2. Make sure the code is what you want to ship

- Work from the branch you release (`old_ui` today), with a clean tree, committed and pushed.
- Run the tests: `./gradlew :app:test :updater:test :shared:test`.

### 3. Publish

```bash
./gradlew -PappVersion=<x.y.z> -PserverBaseUrl=https://malenia.feld.cvut.cz/koubic :packaging:publishWindowsRelease
```

This takes about 1–3 minutes. In order, it:

1. **`checkReleasePublishable`**, seconds, before any build: confirms the server is reachable, the
   version is not published yet, and it is newer than the live release.
2. **`releaseWindowsX64`**: builds `packaging/build/release/<x.y.z>/` with every artifact and a
   `.sha256` sidecar for each.
3. **`tools/release/release.sh --no-build`**:
   1. checks that the build matches the server and version: the updater endpoint and the URL
      baked into `app.jar` must both be `-PserverBaseUrl`
   2. uploads only the declared artifacts to `releases/.incoming-<x.y.z>/` (invisible to clients)
   3. verifies the checksums on the server, then renames the directory to `<x.y.z>/`. This is the
      moment the release goes live.
   4. checks the live endpoint: the release number, every component's checksum and size, and
      the install bundle. If this fails, it **withdraws** the release by renaming it to
      `.failed-<x.y.z>-<timestamp>`.

Expected tail:

```
==> checking the live endpoint (http://192.168.122.183:10001/api)
release 1.0.2
  app: ok  .../api/version/download/1.0.2/app.zip  79775494 bytes
  runtime: ok  ...
  ffmpeg: ok  ...
  install-windows-x64.zip: ok  ...
==> published 1.0.2
```

### 4. Check through malenia

The build machine cannot reach malenia, so the pipeline verifies through the internal address.
From a network that reaches malenia, run:

```bash
curl 'https://malenia.feld.cvut.cz/koubic/api/version/latest?platform=windows-x86_64'
```

It must show the new `release`. `url` values are relative (`download/<v>/app.zip`). That is
intended: the updater resolves them against its endpoint.

### 5. What happens on clinic PCs

Each install updates itself the next time someone starts it through `updater.exe`. It downloads
the changed components, swaps them in with rollback, and starts the app. The window title shows
the new version (`SHARE (v1.0.2)`). If the new app does not start, the next run restores the
previous version and skips that release until a newer one appears.

**`updater.exe` itself is never updated** (it is not a component). Updater changes reach clinics
only through a fresh install bundle (next step).

### 6. New installs

Hand out `packaging/build/release/<x.y.z>/install-windows-x64.zip`. It is also downloadable from
`https://malenia.feld.cvut.cz/koubic/api/version/download/<x.y.z>/install-windows-x64.zip`.

1. Unzip it into a folder the user can write to (the app keeps `data/` next to itself; not
   `Program Files`).
2. Start `updater.exe` (pin a shortcut to it, not to the jar).
3. The first start may show a SmartScreen warning: the binaries are not signed yet.

## Test releases (test container)

To try the update flow without real clients picking up a test build:

```bash
./gradlew -PappVersion=9.0.0 -PallowTestPublish=true :packaging:publishWindowsRelease   # default serverBaseUrl = test container
```

- Without `-PallowTestPublish=true`, any non-`https://` server is refused. A test build on
  `share_web` would be offered to every malenia client and cut them off from their server.
- **Always remove a test release right after the test**, and pick a version far above
  production so it can't be confused with one:
  `ssh share_web 'rm -rf /srv/task-protocoller/releases/9.0.0'`.
- Test installs: unzip that version's `install-windows-x64.zip` into a scratch folder and run its
  `updater.exe`. Its log is `updater.log` next to it.

## Withdrawing a release

```bash
ssh share_web 'ls /srv/task-protocoller/releases/'
ssh share_web 'mv /srv/task-protocoller/releases/1.0.2 /srv/task-protocoller/releases/.withdrawn-1.0.2'
```

- The previous version becomes "latest" immediately.
- Clients that already installed 1.0.2 **stay on it**: the updater never downgrades. To move them
  on, publish a fixed 1.0.3.
- Do not delete and then re-publish the same number with different bytes. Clients that recorded a
  failed launch of it would still skip it, and the others would never notice the change.

## Running the script directly

`tools/release/release.sh` also runs on its own (Git Bash):

```bash
tools/release/release.sh 1.0.2 --check-only                  # only the server checks
tools/release/release.sh 1.0.2 --dry-run                     # build + local checks, no upload
tools/release/release.sh 1.0.2 --no-build                    # publish an existing build
tools/release/release.sh --help
```

Options: `--server URL` (default malenia), `--verify-api URL` (default internal :10001/api),
`--host ALIAS` (default `share_web`). Gradle equivalents: `-PserverBaseUrl`, `-PreleaseVerifyApi`,
`-PbashPath` (if Git Bash is not in `C:\Program Files\Git`).

## When it fails

| Message | Meaning / fix |
|---|---|
| `Refusing to publish a build for 'http://…'` | You forgot `-PserverBaseUrl=https://malenia.feld.cvut.cz/koubic`. For a test release, add `-PallowTestPublish=true`. |
| `x.y.z is already published` | Versions are immutable: bump. |
| `x.y.z is not newer than the live release` | Bump above the live version. |
| `cannot read the live release … (HTTP 000)` | Server unreachable: VPN down, or `share_web` is stopped. Nothing was built or uploaded. |
| `live endpoint answered 200 with an unreadable body` | `--verify-api` points at a non-API path (the frontend answers 200 with HTML), or nginx lost its `/api/` block. |
| `build does not match --server/version` | The details are printed above the message. Usually a stale build published with `--no-build`, or an app version newer than the release (`releaseVersion` pinned somewhere). |
| `releaseWindowsX64 must run on Windows` | Wrong host. |
| `nativeCompile` fails, `cl.exe` / MSVC not found | Install the Build Tools (prerequisites). |
| `… withdrawn to …/.failed-x.y.z-…` | Uploaded but the live check failed, so it was hidden again. Look at the check lines above; delete the `.failed-*` directory once understood. |
| Upload hangs | VPN MTU, see prerequisites. |

## Where things live

| Thing | Location |
|---|---|
| Build output | `packaging/build/release/<x.y.z>/` |
| Pipeline script | `tools/release/release.sh` |
| Gradle tasks | `packaging/build.gradle.kts` (`checkReleasePublishable`, `releaseWindowsX64`, `publishWindowsRelease`) |
| Server URL baked into the app | `-PserverBaseUrl` → `app` resource `org/example/app/server.json` → `ServerEndpoints.kt` |
| Updater endpoint in an install | `<install_dir>/updater.properties` |
| Releases on the server | `share_web:/srv/task-protocoller/releases/<x.y.z>/` |
| Backend code | web repo `backend/src/releases/releaseStore.js`, `backend/src/routes/version.js` |
| nginx block | `share_web:/etc/nginx/sites-enabled/task-protocoller`, `location /api/version/download/` |
| Updater log on a client | `<install_dir>/updater.log` |

Linux: `:packaging:releaseLinuxX64` still exists (on a Linux host), but there is no Linux publish
task. `release.sh` is Windows-only (`PLATFORM`/`BUNDLE` at its top).
