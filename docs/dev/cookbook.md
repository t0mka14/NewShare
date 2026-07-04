# Cookbook — how to change things

Worked recipes for the changes we expect. Each names the exact seam; if you find yourself
editing more files than the recipe lists, you're probably at the wrong layer. When a change
alters *behavior* described in the spec, update `docs/Project_Specification.md` (and §13)
in the same change — code never diverges from spec silently (§12).

## 1. Change where files are saved

Everything flows through the `AppDirectories` port — there is exactly one production
binding, in `AppContainer`:

```kotlin
// Main.kt / composition root — move the whole data root:
val container = AppContainer(
    directories = DefaultAppDirectories(dataRoot = Path.of("/srv/share/data")),
)
```

For a different *layout* (not just a different root), implement `AppDirectories`
(`domain/AppDirectories.kt`: `dataRoot`, `configDir`, `sessionsDir`, `logsDir`,
`sessionDir(folderName)`) and pass your implementation. Nothing else in the codebase
builds paths — if a grep for `Path.of(` outside `infrastructure/` + `Main.kt` finds a hit,
that's a §5.2 violation, fix it.
Caveat: `logback.xml` resolves the log dir independently (`${LOG_PATH:-${user.dir}/data/logs}`)
— pass `-DLOG_PATH=<newRoot>/logs` when you move the root, or the log file diverges.

## 2. Change API endpoints

Both APIs take the endpoint as constructor parameters (single configuration point,
spec §13 open q1 — the real server contract is still pending):

```kotlin
val container = AppContainer(
    configApi = KtorConfigApi(
        baseUrl = "https://api.clinic.example/v2",
        configPath = { installationId -> "/config/$installationId" },
    ),
    uploadApi = KtorUploadApi(
        baseUrl = "https://api.clinic.example/v2",
        uploadPath = { "/sessions" },
    ),
)
```

Both also take an injected Ktor `HttpClientEngine` (tests pass `MockEngine`; a custom
trust store for an internal CA goes in via a configured `CIO.create { ... }`). §11 rule
when touching these classes: the installation ID and raw exception messages never reach a
log line — keep using `LogPolicy.safeDescribe`, and keep the `io.ktor` INFO ceiling in
`logback.xml`.

## 3. Add a field to the remote config

1. Model: add it in `domain/config/RemoteConfig.kt` (or the task subtype in `Task.kt`)
   with a default so old cached configs still decode.
2. Validation rule (if any): `ConfigValidator.kt` + test in `ConfigValidatorTest`.
3. Spec: document it in `docs/Task_Configuration_JSON_Spec.md` **in the same change** (§12).
4. Fixtures: add it to `app/src/test/resources/fixtures/config/sample_config.json` (and
   variants) if scenarios should exercise it.
5. If the shape change is incompatible for *older* app versions' caches: bump
   `schemaVersion` handling — see recipe 4.

## 4. Add a config schema migration

`domain/config/ConfigSchemaMigrator.kt` holds a registry `Map<Int, (JsonObject) -> JsonObject>`
(from-version → transform). Widen `ConfigValidator.SUPPORTED_SCHEMA_VERSIONS`, register the
step, unit-test it in `ConfigSchemaMigratorTest`. Note §13 decision 30: the first real
migration must also start persisting the migrated result back to the cache (currently
migration is in-memory per startup because no migrations exist).

## 5. Add a persisted JSON file

1. `@Serializable` model in `domain/...` with a `version: Int = 1` field, ISO-8601 UTC
   strings for timestamps (spec §8.10).
2. Repository interface in `domain/`, JSON impl in `infrastructure/persistence/` writing
   via `AtomicFileWriter` and locating the file ONLY via `AppDirectories`.
3. `Fake<Name>Repository` in `app/src/test/.../fakes/` (in-memory, deterministic).
4. Round-trip test in a `@TempDir` (`infrastructure/persistence/...Test.kt` pattern).
5. Binding in `AppContainer` (body property).
6. Document it in `data-and-schemas.md` and, if user-visible, spec §8.10.

## 6. Add a task type

`domain/config/Task.kt` is a sealed hierarchy with a `type` discriminator. Add the
subclass (+ `ConfigDecoder` alias if the server spelling differs — see the QUESTIONAIRE
precedent), a `TaskComponent.Content` variant + state-machine behavior (`navigation/TaskComponent.kt`),
UI rendering (`ui/TaskContent.kt`), expansion behavior if it deviates
(`domain/timeline/TaskInstanceExpander.kt` — VIDEO is excluded there; that's the pattern
for non-navigable types), fixture + tests at each layer, and the JSON spec doc.

## 7. Add a screen

Checklist in [`ui.md`](ui.md). Short form: Decompose component in `navigation/` (fakes-only
component test) → stateless `ui/<Name>Content.kt` → tags in `ui/TestTags.kt` →
string keys in `domain/localization/BuiltinStrings.kt` (English fallback; config can
override per §7) → route in `RootComponent` → scenario update if the flow changed (§10.4).

## 8. Add / change display text or a language

Text lives in the config's `strings{lang → {key → value}}` map, overlaid on
`BuiltinStrings.en`. New key: add to `BuiltinStrings` (the only place built-in text may
live — no literals in UI code, §12) and reference it via `localization.resolve(key)`.
Markup: `<bold>`/`<italic>` only (decision 26 — legacy `_b`//`b` is dead). New language:
ship it in the config (`languages` + `strings.<lang>`); the app needs no code change.

## 9. Change the preferred audio format

`CaptureFormat.PREFERRED` (`domain/audio/CaptureFormat.kt`) — but read §13 decision 32
first: the entire pipeline (negotiation, level meter, cutting, waveform, playback) is
16-bit-PCM-only by design. Changing sample rate or channel count is a one-liner (all
exports resample to PREFERRED); changing bit depth is a cross-cutting project, not a
config tweak.

## 10. Change disk-preflight sizing

`domain/session/SessionDiskPreflight.kt` — margins are §13 decision 27 (2× worst case,
≥1 GiB target-format minimum, 10 MiB no-master). Update the decision when you change them.
