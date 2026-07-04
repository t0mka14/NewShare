# Testing dev notes

Terse practical companion to `docs/Project_Specification.md` §10 (the testing strategy — the
normative contract), §4 (every listed original-app defect gets a regression test), §8 (the
behaviors the workflow scenarios assert). This doc is factual/how-to; §10 is the rulebook.

## Layers and how to run them

All tests are JUnit 5, run via `./gradlew :app:test` (whole suite) or
`./gradlew :app:test --tests "org.example.app.some.PackageOrClass*"` (filtered — use this while
iterating, one full run at the end). No real time, no real audio, no real network anywhere: a
`delay`/timeout in a test is a defect, not a flake to retry.

1. **Unit** (`app/src/test/kotlin/org/example/app/domain/**`, `infrastructure/**` where pure) —
   plain JVM, no filesystem/hardware/network. Config parsing/validation, timeline math, filename
   templates, task-instance expansion, boundary validation, etc. (§10.1).
2. **Integration** (`infrastructure/**`, `domain/session/*IntegrationTest.kt`) — temp dirs
   (`@TempDir` + `TestAppDirectories`) and Ktor `MockEngine`. Persistence round-trips, clip
   cutting against a real bundled WAV, zip/manifest contents, crash recovery, config fetch/cache
   (§10.2).
3. **Component** (`navigation/*ComponentTest.kt`) — no Compose at all: build a `Default*Component`
   from fakes, call its methods directly, assert `Value`/`StateFlow` state. This is the cheap
   layer — prefer it over a scenario test whenever the assertion doesn't need pixels or click
   sequencing (§10.3, last bullet).
4. **Compose UI / smoke** (`ui/*SmokeTest.kt`) — `runComposeUiTest` rendering one screen at a
   time from fakes; catches composition crashes (`BoxWithConstraints`/`Canvas`/`pointerInput`)
   that a component test structurally cannot.
5. **Workflow scenarios** (`ui/scenario/*ScenarioTest.kt`) — the §10.3 "core suite," full
   click-through of `DefaultRootComponent`/`RootContent` from a fully-faked `AppContainer`,
   asserting on-disk session contents, not just UI state. See below.

## Headless Compose UI tests

`app/build.gradle.kts`'s `tasks.test` already bakes in the required JVM args
(`-Dskiko.renderApi=SOFTWARE -Djava.awt.headless=true`) — plain `./gradlew :app:test` is headless
by default, no extra flags needed locally or in CI.

**No-Xvfb finding:** on this Linux dev environment, `-Djava.awt.headless=true` plus Skia software
rendering is sufficient on its own — running the full scenario suite with `DISPLAY` unset
(`env -u DISPLAY ./gradlew :app:test ...`) passes with no virtual framebuffer at all. If a
different CI image ever fails with an AWT/X11 connection error despite `java.awt.headless=true`,
fall back to `xvfb-run -a ./gradlew :app:test` — the original §10.3 assumption — but treat that as
a property of the specific CI image, not something this project's tests require by default.

## Fakes inventory (`app/src/test/kotlin/org/example/app/fakes/`)

Every port `AppContainer` takes as a constructor parameter has a fake here, so a test container
can be assembled with zero real hardware/network/filesystem-adjacent-timing dependencies:

| Fake | Stands in for | Notable behavior |
|---|---|---|
| `FakeClock` | `Clock` | Manual `advance(duration)`; `onAdvance` listeners (see two-clock rule below) |
| `FakeIdGenerator` | `IdGenerator` | Sequential `session-0001`, `session-0002`, … — deterministic folder/sessionId assertions |
| `TestAppDirectories` | `AppDirectories` | Real `@TempDir`-backed paths, not in-memory — repositories under test are the real JSON ones |
| `TestCoroutineDispatchers` | `CoroutineDispatchers` | `main`/`default`/`io` share one `TestCoroutineScheduler`; one `scheduler.advanceUntilIdle()` drives everything |
| `FakeConfigApi` | `ConfigApi` | Enqueue `Success`/`InvalidInstallationId`/`NetworkUnavailable`/`ServerError` results |
| `FakeUploadApi` | `UploadApi` | `nextResult` for the common case; `respondTo(sessionId, result)` scripts per-session outcomes (batch-upload scenario needs "first fails, second succeeds") |
| `FakeAudioInputDeviceProvider` | `AudioInputDeviceProvider` | `DEFAULT_DEVICE`/`SECONDARY_DEVICE`/`INELIGIBLE_DEVICE`; `setDevices(...)` simulates a hot-plug |
| `FakeContinuousSessionRecorder` | `ContinuousSessionRecorder` | `writtenSamples` advances only while `Writing`, driven by `FakeClock.onAdvance` (`samples = elapsed * sampleRate`); `simulateInterruption`/`simulateFailure` drive device-loss scenarios |
| `FakeAudioClipService` | `AudioClipService` | Records every `cutClip`/`convert`/`concatenate` call **and** writes a real (header-only, zero-data) WAV stub to `output` — see note below |
| `FakeWaveformService` | `WaveformService` | Flat zero waveform by default, or `respondWith(peaks)` |
| `FakeAudioPlaybackService` | `AudioPlaybackService` | Records `playRange`/`stop` calls, fakes `isPlaying`/`positionSamples` |
| `FakeSessionRepository`, `FakeTimelineRepository`, `FakeUploadStatusRepository`, `FakeAppSettingsRepository`, `FakeConfigurationRepository` | corresponding repositories | In-memory; used by component tests that don't need a real temp dir |
| `FakeSessionArchiveService`, `FakeFileHashService`, `FakeDiskSpaceProvider` | archive/hash/disk-space ports | In-memory, no real ZIP/SHA-256/disk stat |
| `ConfigFixtures` | — | Raw JSON classpath fixtures: `fullProtocol` (2 protocols, `enableEditor: true`), `editorDisabled` (same, `enableEditor: false`), `questionnaireOnly` (1 protocol, no VOCAL/CALIBRATION anywhere) |

**Why `FakeAudioClipService` writes real files:** `AppContainer`'s `sessionArchiveService`
(`ZipSessionArchiveService`) and `fileHashService` (`Sha256FileHashService`) are **not**
constructor parameters — every scenario gets the real ones. The real archive builder reads every
clip/master path in its manifest straight off disk, so a clip-service fake that only recorded
calls without writing anything would fail archive building with `NoSuchFileException` on any
VOCAL-bearing scenario — a self-inflicted failure unrelated to the behavior under test. The fix
is `FakeAudioClipService` writing a real, minimal (`WavHeader.build(format, dataSize = 0)`) file
at `output` for every call while still recording call arguments for assertions. Sample-accurate
cutting/resampling itself stays covered by `JvmAudioClipServiceTest`/`ProcessSessionUseCaseIntegrationTest`.

## The two-clock rule

Every scenario test coordinates **two clocks**:

- The Compose test `mainClock` (`runComposeUiTest`'s own) — drives recomposition/animations.
- The injected `FakeClock` — drives domain time and `FakeContinuousSessionRecorder.writtenSamples`.

Advancing one without the other desyncs "wall-clock time passed" from "samples recorded."
`ScenarioHarness.advanceBoth(clock, millis)` (an extension on `ComposeUiTest`) advances both in
lockstep — this is the only way scenario tests simulate "N ms of recording":

```kotlin
onNodeWithTag(TestTags.Task.START_BUTTON).performClick()
advanceBoth(harness.clock, 500)
onNodeWithTag(TestTags.Task.STOP_BUTTON).performClick()
```

Separately, `TestCoroutineDispatchers.scheduler.advanceUntilIdle()` must be called after every
click that triggers a suspend chain (session start, task completion, editor accept, processing,
upload) — `main` is unconfined but `default`/`io` are `StandardTestDispatcher`s, so background
work only runs when explicitly advanced. The established idiom, repeated after nearly every
click in every scenario:

```kotlin
onNodeWithTag(...).performClick()
harness.dispatchers.scheduler.advanceUntilIdle()
waitForIdle()
```

## The full-fake `AppContainer` pattern

`AppContainer`'s constructor takes every hardware/network port as a parameter with a production
default (`configApi`, `uploadApi`, `audioInputDeviceProvider`, `audioClipService`,
`waveformService`, `audioPlaybackService`, `sessionRecorderFactory`) alongside
`directories`/`clock`/`idGenerator`/`dispatchers`. A test builds one real `AppContainer` with
every port swapped for its fake and drives the real `DefaultRootComponent`/`RootContent` on top —
there is no hand-assembled parallel component tree anymore. `ScenarioHarness` (below) is this
pattern, packaged for reuse.

Two things NOT swappable, by design (§5.2: "every port gets a production binding"), that
scenario authors must know about:

- `sessionArchiveService` / `fileHashService` are always real (`ZipSessionArchiveService`,
  `Sha256FileHashService`) — see the `FakeAudioClipService` note above for why that matters.
- Everything else that touches disk (`sessionRepository`, `timelineRepository`,
  `uploadStatusRepository`, `configurationRepository`, `appSettingsRepository`) is the real JSON
  implementation over `TestAppDirectories` — scenario assertions read genuinely-written files,
  not fake in-memory state.

## Adding a workflow scenario: step by step

1. **Pick or write a config fixture.** Reuse `ConfigFixtures.fullProtocol` /
   `.questionnaireOnly` / `.editorDisabled` if one already has the shape you need (protocol
   count, `enableEditor`, task types) — a new fixture is a new JSON file under
   `app/src/test/resources/fixtures/config/` plus an entry in `ConfigFixtures`.
2. **Build the harness and load the config**, before `setContent`:
   ```kotlin
   val harness = ScenarioHarness(tempDir)
   harness.loadConfig(ConfigFixtures.fullProtocol)
   ```
   `loadConfig` must run before `setContent { ScenarioApp(harness) }` —
   `DefaultRootComponent`'s constructor picks its initial screen (`MainMenu` vs the no-config
   `Blocking` screen) by reading `activeConfig.value` synchronously, and `ScenarioApp` builds the
   component lazily (`remember`) precisely so this ordering works.
3. **`setContent { ScenarioApp(harness) }`**, then drive it by `TestTags` only — no ad-hoc
   string literals. If a protocol config defines more than one protocol, Start opens the
   `TestTags.ProtocolPicker.protocolButton(name)` screen first (`ValidationAndOfflineConfigScenarioTest`
   and every scenario using `fullProtocol` show the pattern); a single-protocol config skips
   straight to patient info.
4. **Drive clocks and dispatchers together** per the two-clock rule above. If the config has
   `enableEditor: true`, every completed protocol lands on the editor screen before processing —
   press `TestTags.Editor.ACCEPT_BUTTON` (untouched, or after a `performMouseInput` boundary drag)
   to proceed; `EditorScenarioTest` is the reference for a drag.
5. **Assert on disk, not just UI state**: `harness.container.sessionRepository`/
   `.timelineRepository`/`.uploadStatusRepository` are the real repositories reading real files
   under the test's `@TempDir` — read `examination.json`/`timeline_original.json`/
   `timeline_edited.json`/`upload_status.json` back and assert their actual contents, per §10.3's
   own instruction ("assert the session directory contents... afterwards").
6. **If a new fake behavior is needed** (a port needs to simulate something it doesn't yet), add
   it to the fake in `fakes/`, not to the scenario test — fakes are shared, reusable, and it's
   the fake's job to be behaviorally faithful, not the test's job to work around it.
7. **Run it in isolation while iterating**:
   `./gradlew :app:test --tests "org.example.app.ui.scenario.YourScenarioTest"`, then the full
   suite once green.

`ScenarioHarness`/`ScenarioApp` live in
`app/src/test/kotlin/org/example/app/ui/scenario/ScenarioHarness.kt`; every scenario test in that
package uses them (or, for the two scenarios that never leave the main menu/blocking screen,
could use the real `DefaultRootComponent` directly — but use the harness anyway for consistency
unless there's a concrete reason not to).

## Scenario suite index (§10.3)

| # | Scenario | Test class |
|---|---|---|
| 1 | Happy path | `HappyPathScenarioTest` |
| 2 | Repeat flow (2×3 takes → 2 clips from takes 3) | `HappyPathScenarioTest` |
| 3 | Validation blocks continuation | `ValidationAndOfflineConfigScenarioTest` |
| 4 | Editor: drag writes complete edited timeline; untouched pass writes nothing | `EditorScenarioTest` |
| 5 | Offline config: cache vs blocking screen | `ValidationAndOfflineConfigScenarioTest` |
| 6 | Device loss mid-take → dialog → new device → gap + auto-reject | `DeviceLossScenarioTest` |
| 7 | Questionnaire-only: no calibration, no master, JSON-only archive | `QuestionnaireOnlyScenarioTest` |
| 8 | Batch upload: one failure, batch continues, retry targets only the failure | `BatchUploadScenarioTest` |

Regression tests for §4's original-app defects live alongside the unit/component test for the
code that fixes each one (e.g. task-instance navigation by `taskIndex` not object identity is
covered in `TaskInstanceExpanderTest`/`TaskComponentTest`, not a separate "regression" suite) —
there is no single `RegressionTest.kt` file to extend; grep §4's defect list against the relevant
subsystem's tests before assuming a defect is uncovered.
