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
  update_pending.json     present only between a replace and a confirmed app launch
  runtime/bin/java[.exe]  bundled jlink JRE (packaging concern, out of scope here)
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
2. **Read local version** from `app/version.json` (missing/corrupt ⇒ treated as "no local
   version," so any parseable remote version is accepted).
3. **Version check**: `GET <endpoint>` → `VersionCheckResponse { version, downloadUrl,
   checksum }`. Unreachable, non-2xx, or a body that doesn't parse ⇒ `VersionCheckResult
   .Unreachable` — proceeds exactly like "no update" (§9 pt 4, §11 "version check unreachable ⇒
   proceed without updating"). A malformed `version` string in an otherwise-valid response is
   *also* treated as "not newer" (§10.1), not as an error.
4. **If newer:**
   - Download to a temp file inside `install_dir` (same filesystem, for a cheap same-volume
     `ATOMIC_MOVE` later if ever needed). Download failure ⇒ discard, keep current app.
   - Verify SHA-256 against `checksum`. Mismatch ⇒ discard, keep current app (§11).
   - `Replacer.backup()`: rename `app/` → `app.backup/` (deletes any pre-existing stale
     backup first, defensively).
   - `Replacer.replace()`: unzip the downloaded package into a fresh `app/`. The package
     contains the whole new `app/`, including its `version.json`. On **any** failure
     (corrupt zip, zip-slip attempt, IO error) the replacer rolls back internally —
     `app/` is restored from `app.backup/` before `replace()` returns `false` (§11
     "replacement failure ⇒ rollback").
   - On success, write `update_pending.json` (the marker) — see next section. `app.backup/`
     is deliberately **not** deleted yet; it survives until the next updater run confirms
     the new version launched successfully.
5. **Launch**: `runtime/bin/java -jar app/app.jar` via `ProcessBuilder`, working directory
   `install_dir`, output/error discarded, **not waited on** (§9 pt 3 — the updater exits
   right after starting the app). If `runtime/` doesn't exist (dev/unpackaged layout), falls
   back to the launching JVM's own `java` (`java.home`).

Every branch above funnels into exactly one `launcher.launch()` call at the end of `run()` —
there is no path that leaves the user without a launched app.

## Failed-launch recovery (§11 "restore backup on next run")

This is the one genuinely stateful piece of the design, so it's worth spelling out precisely.

- Right after a successful `replace()`, the updater writes `update_pending.json`
  (`{ "version": 1, "appliedVersion": "...", "appliedAt": "..." }`) at `install_dir` — **not**
  under `data/`.
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
  | yes | yes | restore `app.backup/` → `app/` (the last update's launch was never confirmed) |
  | yes | no  | nothing to restore (anomaly — backup missing); marker is still deleted |
  | no  | yes | delete the stale backup (last update *was* confirmed; backup is just leftover) |
  | no  | no  | nothing to do |

  The marker itself is always deleted once handled, regardless of which branch ran.

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

## Known gaps / explicitly out of scope here

- **Packaging**: bundling a jlink `runtime/`, native-image compilation, code signing and
  macOS notarization are all separate concerns (§9 says as much) and untouched by this work.
- **No real endpoint yet**: `updater.properties`/`--endpoint=` is plumbing, not a working URL —
  depends on §13 open question 1.
- **`app/version.json` isn't produced by anything yet**: no build step currently generates it
  or ships it inside `app/`; a real update package will need one. `:app`'s window title still
  hardcodes `AppVersion(1, 0, 0)` (`Main.kt`) rather than reading `app/version.json` — wiring
  those together is a follow-up, not done here.
- **CWD assumption for the marker path** (see above) — revisit if packaging launches the app
  differently.
