# Updater

Normative source: spec §9 (flow), §11 "Updater" (error taxonomy), §10.1 (required unit-test
items). Implemented in `:updater` (Phase 4); models shared with `:app` live in `:shared`.

Deliberately minimal — this is not a general-purpose updater. No UI, no delta updates, no
channels, no rollback history beyond one backup generation.

## Install layout

The updater assumes it runs from `<install_dir>`, laid out as:

```
<install_dir>/
  updater[.exe]
  updater.properties      optional — see "Configuration" below
  installed.json          what the updater believes is installed (see "Components")
  update_pending.json     present only between a replace and a confirmed app launch
  update-staging/         verified payloads, between download and swap; cleared every run
  runtime/bin/java[.exe]  bundled jlink JRE (built by :packaging:jlinkRuntime)
  app/
    app.jar
    version.json          { "version": 1, "appVersion": "x.y.z" }
  app.backup/             present only mid-update or after a failed launch
  data/                   NEVER read or written by the updater (§9 pt 5)
```

`InstallLayout` (`updater/src/main/kotlin/org/example/updater/InstallLayout.kt`) is the single
place these paths are derived from `installDir`.

## Flow (`Updater.run()`)

1. **Startup recovery** — before anything else, decide what to do about a marker/backup left
   from a previous run (see "Failed-launch recovery" below).
2. **Read the ledger** `installed.json` (missing/corrupt ⇒ treated as "nothing known", so every
   component reads as stale and the install re-syncs from the server once).
3. **Version check**: `GET <endpoint>?platform=<platform>` → `VersionCheckResponse { release,
   components[] }`. Unreachable, non-2xx, or a body that doesn't parse ⇒ `VersionCheckResult
   .Unreachable` — proceeds exactly like "no update" (§9 pt 4, §11 "version check unreachable ⇒
   proceed without updating"). A malformed `release` string in an otherwise-valid response is
   *also* treated as "not newer" (§10.1), not as an error. A release listed in the ledger's
   `failedReleases` is skipped (see "Failed-launch recovery").
4. **If newer**, for the components whose checksum differs from the ledger's:
   - Download to a temp file inside `install_dir` (same filesystem, for a cheap same-volume
     `ATOMIC_MOVE` later if ever needed). Download failure ⇒ discard, keep current app.
   - Verify SHA-256 against `checksum`. Mismatch ⇒ discard, keep current app (§11).
   - Write `update_pending.json` (the marker) — **before anything moves**; see the next section.
   - `Replacer.backup()`: rename `app/` → `app.backup/` (deletes any pre-existing stale
     backup first, defensively).
   - `Replacer.replace()`: unzip the downloaded package into a fresh `app/`. The package
     contains the whole new `app/`, including its `version.json`. On **any** failure
     (corrupt zip, zip-slip attempt, IO error) the replacer rolls back internally —
     `app/` is restored from `app.backup/` before `replace()` returns `false` (§11
     "replacement failure ⇒ rollback").
     A package that unzips to zero files is rejected as corrupt: `ZipInputStream` reports
     non-zip bytes as an immediate end-of-stream rather than throwing, so without that check a
     bad artifact with a matching checksum would install an empty `app/` and report success.
   - On success the marker stays put and `app.backup/` is deliberately **not** deleted; both
     survive until the next updater run confirms the new version launched successfully. If the
     apply was abandoned and rolled back, the marker is removed again — nothing is pending.
5. **Launch**: `runtime/bin/java -jar app/app.jar` via `ProcessBuilder`, working directory
   `install_dir`, output/error discarded, **not waited on** (§9 pt 3 — the updater exits
   right after starting the app). If `runtime/` doesn't exist (dev/unpackaged layout), falls
   back to the launching JVM's own `java` (`java.home`) — or, when there is no `java.home`
   because the updater is itself a native image, to whatever `java` is on `PATH`.

Every branch above funnels into exactly one `launcher.launch()` call at the end of `run()` —
there is no path that leaves the user without a launched app.

## Components

A release is a set of components, each one zip whose contents *are* its target directory:

| id | target | typically changes |
|---|---|---|
| `app` | `app` | most releases |
| `runtime` | `runtime` | rarely |
| `ffmpeg` | `native/ffmpeg/<platform>` | rarely |

There are deliberately **no per-component versions**. The `checksum` already identifies an
artifact exactly, and the updater records it in `installed.json` at install time, so "is this
stale?" is one string comparison — with no versioning scheme to invent for pieces that share none
(the JDK, ffmpeg's `7.1-1.5.11`, and the app).

Consequences worth knowing:

- **Only stale components are downloaded.** A release that only rebuilds the runtime leaves
  `app.zip` byte-identical, so the app is not re-fetched. That is why `releaseVersion` is separate
  from `appVersion` in `gradle.properties`: bumping the release alone is what makes such a release
  expressible.
- **Nothing is swapped until every stale payload is downloaded and verified.** Otherwise an
  `app.jar` could land while the runtime it needs never arrived. If any component fails, none are
  applied; if a swap fails midway, every component already swapped is rolled back in reverse.
- **`target` is a path from the network** and is validated by `ComponentTarget` before use —
  absolute paths, `..`, anything escaping the install directory, and anything under `data/` are
  rejected, and one bad target aborts the whole release rather than applying part of it.
- **Executable bits are restored after unzipping.** `java.util.zip` cannot read unix modes, so
  `runtime/bin/java` and `ffmpeg` would otherwise arrive non-executable and the launch would fail
  confusingly. `Replacer` marks anything directly under a `bin/` directory, plus `ffmpeg`.
- **Components are platform-specific**, so the updater sends `?platform=` (`HostPlatform`, the same
  vocabulary as `:app`'s `HostOs.nativeDirName`). An unknown platform gets no components rather
  than another platform's runtime.

## Failed-launch recovery (§11 "restore backup on next run")

This is the one genuinely stateful piece of the design, so it's worth spelling out precisely.

- **Before the first file is moved**, the updater writes `update_pending.json`
  (`{ "version": 1, "appliedVersion": "...", "appliedAt": "..." }`) at `install_dir` — **not**
  under `data/`. It therefore means "an apply was in flight", covering both "swapped, launch
  unconfirmed" and "died partway through the swap"; both want the same remedy from the next run.

  Writing it *after* the replacement instead — as this originally did — is what made the second
  case destructive: a backup with no marker reads as `DELETE_STALE_BACKUP`, so the next run
  deleted the only intact copy of `app/` while `app/` itself held a partial unzip. Regression
  test: `UpdaterTest."an apply interrupted mid-unzip is rolled back, not treated as a stale
  backup"`.
- `:app`'s `Main.kt` deletes that file once startup has gotten far enough to be considered
  successful (after the root Decompose component is constructed, before the Compose window is
  shown). The assumption, documented at the call site, is that the updater always launches the
  app with working directory `install_dir` (§9 layout), so
  `Path.of(System.getProperty("user.dir")).resolve("update_pending.json")` is the marker.
  **If packaging ever changes what CWD the launched `app.jar` sees, this must move to a path
  passed explicitly (e.g. a JVM system property or argument) instead of relying on CWD.**
- On its *next* run, the updater checks the marker/backup combination via a pure decision
  table (`BackupDecision.decide`, table-tested in `BackupDecisionTest`):

  | marker present | backup present | action |
  |---|---|---|
  | yes | yes | restore `<target>.backup` → `<target>` (the last update's launch was never confirmed) |
  | yes | no  | nothing to restore (anomaly — backup missing); marker is still deleted |
  | no  | yes | delete the stale backup (last update *was* confirmed; backup is just leftover) |
  | no  | no  | nothing to do |

  The table is unchanged from the single-component design — it is simply applied **once per
  target**, which is why `BackupDecision` and its table test needed no edits.

  The marker itself is always deleted once handled, regardless of which branch ran.

- **The failed release is recorded**, not just rolled back. Without that, recovery restores the
  backup and the very next `decideUpdate()` re-offers the same broken release, re-applies it, and
  fails again — rolling back and re-breaking on every launch, forever. The marker names the
  release and the ledger's `failedReleases` remembers it, so it is skipped until the server offers
  a different one. A *fixed* release under a new version still installs, so nobody is pinned to an
  old build; re-publishing different bytes under the *same* version is not detectable, and the
  escape hatch is to bump the version (or delete `installed.json`).

## Configuration

Two things vary per deployment; both are resolved once in `UpdaterConfig`, precedence high to
low:

- **Install dir**: `--install-dir=<path>` CLI arg, else the process's working directory
  (assumed to be `install_dir`, matching §9's launch convention).
- **Version-check endpoint**: `--endpoint=<url>` CLI arg, else `endpoint=<url>` in
  `updater.properties` next to the install dir, else none. "None" is handled identically to
  "unreachable" — the update check is skipped and the existing app launches. The real server
  contract (§13 open question 1) is still pending; this only fixes *where* the URL comes from
  so the contract can land later without touching call sites in `Updater`/`HttpVersionFetcher`.

## Why `java.net.http` instead of Ktor

`:updater` is a candidate for native-image packaging later (§9 mentions GraalVM native-image
as one option). To keep that door open, the module avoids Ktor entirely and uses the JDK's
built-in `HttpClient` for both the version check and the download. The only non-JDK runtime
dependency is `kotlinx-serialization-json`, needed to decode `VersionCheckResponse` /
`AppVersionFile` / `UpdateMarker`.

## Error handling (§11 "Updater")

| §11 error | Where handled |
|---|---|
| version check unreachable | `HttpVersionFetcher` → `VersionCheckResult.Unreachable` → `Updater` skips the update |
| download failed/incomplete | `Downloader.download()` returns `false` → `Updater` discards, keeps current app |
| checksum mismatch | `Sha256.matches()` → `Updater` discards, keeps current app |
| replacement failure (rollback) | `Replacer.replace()` catches `IOException`, restores backup internally, returns `false` |
| app failed to launch post-update (restore backup on next run) | `update_pending.json` marker + `BackupDecision` (see above) |

## Testing

`updater/src/test/kotlin/org/example/updater/`: `BackupDecisionTest` (decision table),
`Sha256Test` (known-answer digest), `ReplacerTest` (backup/replace/rollback/zip-slip against
`@TempDir` fake install layouts), `ProcessAppLauncherTest` (bundled-vs-fallback java path
selection, no process actually spawned), `HttpVersionFetcherTest` (JSON parsing against a real
loopback `com.sun.net.httpserver.HttpServer` — no mocking framework, no external network),
`UpdaterConfigTest`, and `UpdaterTest` (the full flow end-to-end through `FakeAppLauncher` /
`FakeDownloader`, real filesystem operations against `@TempDir`). `AppVersionTest` (parsing,
comparison, malformed-version handling) lives in `:shared` next to the model.

```bash
./gradlew :updater:test :shared:test
```

## Packaging (implemented 2026-09-08)

`./gradlew :updater:nativeCompile` produces a standalone GraalVM native binary at
`updater/build/native/nativeCompile/updater` (~28 MB; `ldd` shows only `libz` and `libc`, no
`libjvm`). GraalVM Community 25 is downloaded on demand by the foojay toolchain resolver — nothing
is installed system-wide. No `META-INF/native-image` config is needed: the module's only non-JDK
dependency is `kotlinx-serialization-json` and every call site uses an explicit `X.serializer()`,
so nothing is reached reflectively. `--no-fallback` enforces that.

Two things worth knowing:

- `System.getProperty("java.home")` **is null in a native image**, which is why
  `resolveJavaExecutable` takes a nullable `javaHome` and falls back to `PATH`. The §9 layout
  always has `runtime/`, so this is the half-assembled-install path — but before the fix it was
  an NPE, not a fallback.
- Gradle's JDK auto-provisioning does not preserve symlinks, so GraalVM's `bin/native-image`
  arrives as a zero-byte file. `:updater:repairGraalvmLaunchers` restores it from `lib/svm/bin/`
  and runs before `nativeCompile`. Without it the build fails with an opaque "A problem occurred
  starting process".

See `docs/dev/packaging-and-deployment.md` §1b for the full install layout and release flow.

## Known gaps / explicitly out of scope here

- **Windows and macOS packaging**, and all code signing / macOS notarization, remain untouched.
  Only linux-x64 is built and verified.
- **The endpoint is still the demo server.** `updater.properties` is generated from the root
  `updateEndpoint` property, defaulting to `http://192.168.122.183:10001/api/version/latest`
  (plain HTTP, no auth). The real contract is still §13 open question 1.
- **CWD assumption for the marker path** (see above) — revisit if packaging launches the app
  differently.
