# Packaging, signing, and the server contract

What has to happen to turn this repo into a shippable, self-updating install — and where
the pending server contract (spec §13 "Still open" q1) plugs into the code when it lands.
Nothing in this document is implemented yet; it is the worklist for the release engineer.

## 1. Target install layout (§9)

```
<install_dir>/
  updater[.exe]        the :updater entry point (native image or launcher script, §4 below)
  updater.properties   version-check endpoint (see §6)
  runtime/             jlink'd JRE used to run the app (and the updater, in option A)
  app/
    app.jar            the :app uber-jar
    version.json       {"version":1,"appVersion":"x.y.z"} — written at package time
  data/                created at first run; NEVER shipped, NEVER touched by the updater
```

## 2. Building the app

- **Uber-jar:** the Compose plugin already provides it — `./gradlew :app:packageUberJarForCurrentOS`
  (output under `app/build/compose/jars/`). Rename/copy to `app/app.jar` in the layout.
  The alternative `:app:packageMsi` / `:app:packageDmg` / `createDistributable` tasks exist
  (`app/build.gradle.kts` `nativeDistributions` block) but produce jpackage bundles with an
  *embedded* runtime — that conflicts with §9's shared `runtime/` + updater-replaceable
  `app/` split. Use the uber-jar path unless the §9 layout is renegotiated.
- **`version.json` + window title:** currently the window title hardcodes `AppVersion(1, 0, 0)`
  (`Main.kt`) and nothing generates `app/version.json`. Packaging must: set the Gradle
  project version as the single version source, generate `version.json` from it (a small
  Gradle task), and feed the same value to the app (simplest: a generated resource read at
  startup; replace the hardcoded constructor). Keep the `AppVersionFile` model (`:shared`)
  as the schema.

## 3. jlink runtime

Build per target OS, on that OS (jlink runtimes are platform-specific):

```bash
jdeps --print-module-deps --ignore-missing-deps app/app.jar   # compute the module list
jlink --add-modules <list>,jdk.unsupported --output runtime --strip-debug --no-header-files --no-man-pages
```

Notes: Skiko (Compose's renderer) needs `jdk.unsupported`; audio needs `java.desktop`
(pulls javax.sound); the updater additionally needs `java.net.http` if it runs on this
runtime. Verify by launching the packaged app from a clean VM — a missing module fails
fast at startup.

## 4. The updater executable

Two options, in order of pragmatism:

- **Option A — jar on the shared runtime (recommended first ship):** build `:updater` as a
  jar, ship `updater.bat`/`updater.sh` (or a tiny launcher) running
  `runtime/bin/java -jar updater.jar`. Zero native-image work; the §9 "native executable"
  wording is satisfied later.
- **Option B — GraalVM native-image (§9's stated goal):** `:updater` was deliberately kept
  dependency-light for this (`java.net.http`, kotlinx-serialization with compile-time
  serializers, no Ktor/logback). Use the `org.graalvm.buildtools.native` Gradle plugin on a
  GraalVM JDK; run the agent once (`-agentlib:native-image-agent`) against the test flow to
  collect any reflection/resource config kotlinx-serialization still wants, commit the
  generated config under `updater/src/main/resources/META-INF/native-image/`. Build per OS.

## 5. Code signing / notarization

- **Windows:** sign `updater.exe` (or the launcher), `app.jar` is not signed but the
  installer is — build an MSI/installer around the §1 layout and sign it with
  `signtool sign /fd SHA256 ...` using an EV or Azure Trusted Signing certificate.
  Unsigned installers trip SmartScreen on clinic machines.
- **macOS:** `codesign --deep --options runtime` everything executable (updater, `runtime/bin/*`)
  with a Developer ID Application certificate; **the app records audio, so the bundle's
  `Info.plist` needs `NSMicrophoneUsageDescription`** and, with hardened runtime, the
  `com.apple.security.device.audio-input` entitlement — without these macOS silently
  delivers no samples (which our silence heuristic §8.5 will surface as a mic warning, a
  confusing failure mode). Then `xcrun notarytool submit` + `xcrun stapler staple`.
  Distribution as a `.dmg` or `.pkg` wrapping the §1 layout.
- Re-sign on every release; the updater replaces `app/` contents, so on macOS prefer
  updating the whole signed bundle (or keep `app.jar` outside the sealed bundle — decide
  during the first macOS release; flag: notarization + self-update interact badly if the
  jar lives inside the signed `.app`).

## 6. Where the server contract lands (§13 open q1)

Every pending server decision has exactly one code seam. When the contract is agreed,
implement top to bottom and delete this table's "placeholder" column from reality:

| Contract item | Seam (the only place to touch) | Placeholder today |
|---|---|---|
| Config fetch URL | `AppContainer(configApi = KtorConfigApi(baseUrl=…, configPath=…))` — composition-root arg | `https://localhost/api` + `/config/{id}` |
| Upload URL + multipart shape | `KtorUploadApi(baseUrl=…, uploadPath=…)`; form-field names + response parsing inside `KtorUploadApi.uploadSession` | `/upload`, fields `installationId`/`sessionId`/`zipSha256` |
| Installation ID in path vs `Authorization` header | the `configPath` closure (drop the ID from the path) + an added header in both `KtorConfigApi`/`KtorUploadApi`. §11 still applies: the ID never reaches logs either way | ID in URL path / form field |
| Updater version endpoint | `updater.properties` beside the install dir or `--endpoint=` CLI arg (`UpdaterConfig.kt`); server must serve `VersionCheckResponse` JSON (`:shared`) | none configured ⇒ updater launches app without checking |
| Config authenticity / signing | `JsonConfigurationRepository.applyFetched` — the single ingestion point; verify a signature before the cache write | none |
| Internal CA / cert pinning | build a configured engine and inject it: `KtorConfigApi(engine = CIO.create { https { … } })` — no class changes needed | JVM default trust store |
| Response envelope changes (e.g. wrapped config JSON) | unwrap in `KtorConfigApi` before returning `ConfigFetchResult.Success(json)` — downstream stays byte-oriented | body = raw config |

The other open §13 questions (data retention/GDPR erasure procedure, `patientCode`
pseudonymity) are *policy* questions; if retention tooling is ever mandated, it lands as a
new use case in `domain/session/` plus a session-browser action — nothing existing needs
to change shape.

## 7. Release checklist

1. Bump the project version (single source, §2 above); update `docs/Project_Specification.md`
   §13 if any decision changed.
2. `./gradlew :app:test :updater:test :shared:test` — all green.
3. Per OS: build uber-jar → jlink runtime → updater → assemble §1 layout → sign/notarize.
4. Smoke-test on a clean machine: fresh install, config fetch, record a session, process,
   upload against staging, then publish an update package and watch the updater apply it
   (including one deliberately corrupted checksum to see it refuse).
5. Upload the package; update the server's `/api/version/latest` entry (`version`,
   `downloadUrl`, `checksum` = SHA-256 of the package).
