# Architecture

Normative source: spec §5. This page is the descriptive map for developers.

## Layers

```
Compose UI          ui/            stateless; renders component state, forwards events, testTag on
                                   every interactive element; strings by key only
Decompose components navigation/   one per screen; hold UI state, call domain; navigation by index
Domain              domain/        models, pure logic, use cases, ALL port + repository interfaces
Infrastructure      infrastructure/ JVM implementations of the ports (audio, persistence, network, lock)
```

Dependencies point downward only. UI never touches domain services directly — always through
a component. Domain never touches `java.io.File`/`Path` construction — always through
`AppDirectories`.

## Dependency injection — `AppContainer.kt`

Manual DI, no framework (§5.2). One production binding per port. Every hardware/network
port is a **constructor parameter with a production default**, so tests construct a
full-fake container (§10.3):

```kotlin
AppContainer(
    directories, clock, idGenerator, dispatchers,
    configApi, uploadApi,                       // network
    audioInputDeviceProvider, audioClipService, // audio
    waveformService, audioPlaybackService,
    sessionRecorderFactory,                     // () -> ContinuousSessionRecorder — session-scoped
)
```

Body properties (repositories, use cases) are derived from those. The recorder is a
*factory*, not an instance: `SessionComponent` owns one recorder per session, bound to its
Decompose lifecycle (§5.2 session ownership). Not injectable by design:
`sessionArchiveService`, `fileHashService` (pure, no hardware).

## Threading model

The rules that have actually bitten us — treat as law:

1. **Blocking audio I/O gets a dedicated `Thread`.** Capture
   (`JvmContinuousSessionRecorder`) reads ≤100 ms chunks on its own thread; playback
   (`JvmAudioPlaybackService`) writes on its own thread. Never a shared coroutine
   dispatcher thread.
2. **`Dispatchers.*` literals are banned** (§5.2). Everything takes the injected
   `CoroutineDispatchers` (`domain/CoroutineDispatchers.kt`); tests substitute
   deterministic ones. `Dispatchers.Main` on desktop exists only because
   `kotlinx-coroutines-swing` is on the classpath — removing that dependency crashes the
   app at startup (tests won't catch it; they inject test dispatchers).
3. **Decompose components live on the Swing EDT.** The root component is created via
   `runOnUiThread { ... }` in `Main.kt`; creating it on the JVM main thread throws
   `NotOnMainThreadException`. Navigation calls also happen on the EDT (Compose event
   handlers already are).
4. Timeline event offsets come from `ContinuousSessionRecorder.writtenSamples` at event
   time — one continuous counter across master parts (§13 decisions 17–20).

## Startup sequence (`Main.kt`)

single-instance lock (`SingleInstanceLock`, `FileChannel.tryLock` on `data/app.lock`)
→ `configurationRepository.loadCached()` (before first frame, so the initial route is
correct) → `recoverSessionsUseCase.recoverAll()` (§8.4 crash recovery) → async config
refresh → create root component on EDT → Compose window.

## Key flows

- **Config:** `KtorConfigApi` fetch → `JsonConfigurationRepository.applyFetched` (validate
  → atomic cache write → activate). Offline: last cache. Migration:
  `ConfigSchemaMigrator` registry (empty at schema v1). Refresh affects the *next* session
  — a running session uses its own snapshot.
- **Session:** `PatientInfoComponent` validates → `SessionComponent` negotiates the format
  (16-bit PCM only, decision 32), runs `StartSessionUseCase` (disk preflight, dir,
  participant/examination/snapshot writes), drives calibration → task instances by index →
  timeline events (JSONL, fsync per event) → compaction on stop.
- **Processing:** `ProcessSessionUseCase` — edited-over-original timeline, last take per
  instance, per-part cutting (`MasterPartMap`), everything exported at
  `CaptureFormat.PREFERRED`, ZIP + SHA-256 manifest. Derived artifacts are regenerable;
  master + `timeline_original.json` are immutable.
- **Upload (manual-only, decisions 34/35):** upload screen lists
  `EligibleUploadsQuery.eligibleSessions()`; Upload runs a sequential batch of
  `UploadSessionUseCase`; failures stay listed with a reason; `Uploaded` is terminal.
- **Localization:** `LocalizedStringProvider.resolve(key, language, config)` — config
  string → config default language → `BuiltinStrings.en` → the key itself. No display
  literals in UI code (§12).

## Logging (§11)

kotlin-logging + Logback. Never log: participant data, patient codes (also embedded in
session folder names and ZIP paths!), the installation ID (bearer credential), raw
exception messages (may embed URLs → the ID). Use `LogPolicy.safeDescribe(e)`. The
`io.ktor` logger is capped at INFO in `logback.xml` because Ktor's TRACE logging embeds
full request URLs — do not remove that ceiling.

## Error taxonomy

Sealed per subsystem: `AudioError` (domain/audio), `StorageError` (domain/session),
`ConfigError` (domain/config), `UploadResult` (domain/upload), `ProcessingError`
(domain/session). UI maps them to localized keys (`ui/ErrorMessageKeys.kt`).
