# SHARE Clinical Recording App — Project Specification (Rewrite)

**Status:** Active · **Date:** 2026-07-02 (rev. 3, post second design review) · **Audience:** AI agents and developers working on this repository

This document is the single source of truth for the rewrite of the original `shareapp`
(located at `/home/tomas/IdeaProjects/shareapp`). It supersedes `specs.txt` in the repo root.
The remote configuration format is defined in
[Task_Configuration_JSON_Spec.md](Task_Configuration_JSON_Spec.md); §6 of this document
constrains it.

---

## 1. Product Overview

A clinical desktop tool (Compose Multiplatform for Desktop, Kotlin/JVM; **target platforms:
Windows and macOS**) for recording speech
data from participants. An examiner walks a participant through an ordered **protocol** of
tasks (sustained phonation, PATAKA, reading, monologue, questionnaires, …). The app records
one **continuous master WAV file** per session, marks task boundaries as timestamped events,
optionally lets the examiner refine boundaries in a waveform editor, exports per-task clips
from the unmodified master, archives the session as a ZIP, and uploads that ZIP to a REST
server.

The app is configured **remotely**: at startup it sends its **installation ID** to a server
and receives a single configuration JSON that defines protocols, tasks, application behavior,
and all localized strings. There is no local task editing.

### What we keep from the original shareapp

- The **UI look and screen set** (main menu, settings, patient info, calibration, task/protocol
  screen, waveform editor, upload screen), with the bugs listed in §4 fixed.
- The clinical workflow: patient info → calibration → guided tasks → (editor) → upload.
- Resources: fonts, drawables, example audio instruction WAVs, flag icons.

### What we replace

- All business logic, recording, persistence, networking, and localization internals are
  rewritten following the architecture in §5.
- `java.util.prefs.Preferences` as a data store → JSON files under an app data directory.
- Retrofit/OkHttp (`MarsApiService`) → Ktor Client.
- txt/Audacity timestamp files → structured JSON timeline (Audacity export may be kept as an
  optional export format, not as the primary data model).
- Local XML string resources → strings embedded in the server configuration JSON, with a small
  bundled fallback set (§7).

---

## 2. Goals and Non-Goals

**Goals**

1. Best-practice, layered, testable Kotlin codebase — while staying **simple**. Prefer fewer,
   well-named classes over ceremony; do not add a use-case class for a one-line repository call.
2. Every external system (microphone, filesystem, network, clock, ID generation) behind an
   interface with a fake implementation for tests.
3. Automated UI testing: scripted click-through workflows using Compose UI Test (§10).
4. Clinical data integrity: the master recording and original timeline are never modified;
   primary session data is never deleted after upload (§8.9).
5. Remote, per-installation configuration with offline fallback to the last cached config.

**Non-Goals (deferred)**

- **VIDEO tasks** (emotions recording, PTZ camera, native DLLs). The JSON schema reserves the
  `VIDEO` task type, but the app skips such tasks with a logged warning in this phase.
- Audacity integration for editing (the built-in editor replaces it).
- Full-featured updater UI; the updater stays minimal (§9).
- Encryption of local data at rest (open question, §13).

---

## 3. Critical Review of the Previous Plan (`specs.txt`)

`specs.txt` is architecturally sound (clean layering, interface-driven, strong testing focus)
but is retired for these reasons:

1. **It predates the remote-config model.** Its Settings sections (task management UI, local
   `tasks.json` editing, participant field configuration screens) conflict with server-pushed
   configuration. In the rewrite, Settings contains only: audio input device selection,
   installation ID, language, and a "refresh configuration" action.
2. **Localization mismatch.** It specifies bundled `strings_*.xml` files; the new model ships
   all strings inside the configuration JSON (§7).
3. **Missing concepts.** It has no notion of *protocols* (named ordered task lists selectable
   per installation), no installation-ID flow, and no QUESTIONNAIRE/INFO task types.
4. **Over-engineering vs. the "keep it simple" goal.** The 1:1 explosion of use cases and
   repositories (≈20 use-case classes) is trimmed: components may call repositories directly;
   use-case classes exist only where real business logic lives (session processing, upload
   orchestration, timeline validation, config refresh).
5. **Useful parts carried over** (and normative here): the continuous recording model (§8),
   the session directory layout (§8.2), the timeline event model, waveform editor
   requirements, data-integrity rules, the error taxonomy (§11), and the testing strategy
   skeleton.

---

## 4. Critical Review of the Original `shareapp` — Defects to Fix

The rewrite must not reproduce these problems (file references point into the original repo):

**Architecture / testability**

- `utils/Recorder.kt` is a global singleton `object` holding Compose `MutableState`, coroutine
  jobs, file writers, and timestamp bookkeeping. Untestable, not restartable, state leaks
  between sessions. → Replace with `ContinuousSessionRecorder` interface + injectable impl.
- `java.util.prefs.Preferences` is used as the database (protocol config, patient config as
  JSON strings, mic selection, flags) across components. Hidden global state; impossible to
  isolate in tests. → JSON files behind repositories, injected `AppDirectories`.
- `components/StandardProtocolComponent.kt` is a god class: UI state, recording control,
  audio playback (`LineListener`), timers, filename composition, and preference access in one
  448-line class. → Split per §5.
- Compose `MutableState` inside serializable domain classes (`data/Patient.kt`) with custom
  serializers — persistence and UI state entangled. → Plain serializable data classes; UI
  state lives in components.
- No tests except a placeholder `SampleTest.kt`.

**Concrete bugs**

- `StandardProtocolComponent.startButtonClicked()` → `StartButtonState.NOT_READY -> TODO()`
  crashes the app if reached.
- `recFilename` is a `var` appended with `+=` in `startRecording()`; a second invocation
  doubles the filename.
- Task navigation uses `savedData.indexOf(currentUIState.value)`; `Task` is a data class, so
  two tasks with equal fields resolve to the first occurrence — repetitions/duplicates break
  navigation.
- `Recorder.makeTimestamp()` calls `timestampsList.last()` — throws on an empty list if events
  arrive out of order; type dispatch on magic strings (`"start"`, `"stop"`, `"again"`, `"finish"`).
- WAV header is written only on successful completion (admitted in `Recorder`'s own doc
  comment): a crash mid-session leaves a headerless, hard-to-recover file.
- `Recorder` **deletes the session folder** after zipping (`File(parentPath).deleteRecursively()`)
  — data-loss risk that contradicts clinical-safety requirements.
- `exportTimestamps()` writes mixed separators in one line: `"...;${it.taskName};${it.stopTime},${it.endTime}"`.
- Hard-coded `"\\"` path separators in `Recorder.cutFile()`/`controlAudacity()` break on
  non-Windows; OS-specific trailing-slash hack in `getOutFileName()`.
- `utils/XMLUtils.getStringResource()` uses XPath `contains(@name,…)` — key `foo` also matches
  `foo_extended`; missing keys return the literal string `"ERROR"` to the UI.
- Level metering in `Recorder` mixes linear and logarithmic formulas depending on chunk size.
- `CoroutineScope(Dispatchers.Main)`/`Dispatchers.IO` scopes created ad hoc, never tied to
  component lifecycle, never cancelled.
- Hard-coded Czech literals in `data/TaskPool.kt` reading passages; `println` debugging in
  `PatientEntry.isCorrect()`.

---

## 5. Architecture

### 5.1 Modules (Gradle monorepo — already scaffolded in this repo)

```
:app       Compose Desktop application (all product code)
:shared    Version model + version-check API contract, used by :app and :updater
:updater   Minimal native updater (§9)
buildSrc / gradle/libs.versions.toml   shared build logic and version catalog
```

### 5.2 Layers inside `:app`

```
Compose UI (ui/, stateless, driven by component state)
  → Decompose components (navigation/, one per screen; hold UI state, call domain)
    → Domain (domain/): models, services, the few real use cases
      → Interfaces: repositories + system ports (audio, fs, network, clock, ids)
        → Infrastructure (infrastructure/): JVM implementations
```

Rules:

- UI files contain **no logic** — they render component state and forward events. Every
  interactive element gets a `Modifier.testTag(...)` (§10).
- Components are constructor-injected via the manual DI `AppContainer` (already present at
  `app/src/main/kotlin/org/example/app/AppContainer.kt`). No DI framework.
- Domain and components never touch `java.io.File` paths directly — always via
  `AppDirectories`.
- Every port interface has a `Fake*` in `app/src/test/.../fakes/`.
- **Session ownership:** a session-scoped `SessionComponent` (Decompose component spanning
  the whole examination flow) owns the `ContinuousSessionRecorder`, the timeline writer, and
  their coroutine scope, bound to its Decompose lifecycle (created on session start, cancelled
  and cleanly stopped on destroy). Task screens are children of it. No ad-hoc scopes.
- **Single instance:** at startup the app acquires an exclusive lock (lock file with
  `FileChannel.tryLock` in `data/`); if already held, it shows a localized message and exits.
  This prevents two instances from sharing the mic, the upload queue, and session dirs.
- **Dispatchers are injected:** `AppContainer` provides a `CoroutineDispatchers` holder
  (main/default/IO); components and services never reference `Dispatchers.*` directly, so
  tests can substitute deterministic dispatchers.
- **Dialogs are Compose-level:** all dialogs (including the blocking device-lost prompt and
  the single-instance message) render inside the main window's compose scene, not as
  separate OS windows — otherwise the UI tests (§10.3) cannot drive them.

### 5.3 System port interfaces

```
AppDirectories            data root, config/, sessions/, upload_queue/, logs/, sessionDir(id)
Clock                     now() (ISO-8601 UTC; clinic-local derivation for folder names, §8.2)
IdGenerator               session IDs: opaque, unique, filesystem-safe (e.g. UUIDv4 hex)
ContinuousSessionRecorder see §5.3.1
AudioInputDeviceProvider  enumerate/select input devices
AudioPlaybackService      example-audio and editor playback
ConfigApi                 fetch configuration JSON by installation ID (Ktor)
UploadApi                 multipart session upload (Ktor)
```

#### 5.3.1 ContinuousSessionRecorder

The recorder is the **single source of truth for audio time**. Requirements:

- `startMonitoring(device)` — opens the `TargetDataLine` and emits level values **without
  writing to disk** (used by the calibration screen).
- `startWriting(file)` — atomically switches from monitoring to writing **on the same open
  line** without dropping or duplicating samples (recording starts here).
- `stop()` — flushes, finalizes the WAV (§8.4), closes the line.
- `writtenSamples: StateFlow<Long>` — count of samples written to disk so far. **Every
  timeline event's offset is taken from this counter at event time** (§8.3); wall-clock time
  is stored as metadata only. Millisecond offsets, where needed, are derived from samples.
- `levels: Flow<Float>` — RMS level (linear, normalized 0.0–1.0 full scale, smoothed over a
  ~300 ms window; one formula, unlike the original) for calibration and the live indicator.
  There is no separate `AudioLevelMeter` interface; levels come from the recorder in both
  monitoring and writing modes.
- `state: StateFlow<RecorderState>` — `Idle / Monitoring / Writing / Interrupted / Stopped /
  Failed(error)`; disconnection detection per §8.5.
- **Capture architecture:** a dedicated capture thread reads fixed-size chunks (≤ 100 ms of
  audio) from the `TargetDataLine` — `read()` blocks, so it must not run on a shared
  coroutine dispatcher thread. A watchdog coroutine monitors the last-successful-read
  timestamp for starvation detection (§8.5). Line buffer + read chunk together stay
  ≤ 200 ms.
- **Offset skew:** `writtenSamples` counts samples handed to the file writer; audio at the
  instant of a button press may still be in the line/read buffer, so event offsets can lag
  the physical press by up to the buffer duration (bounded by the ~200 ms above). Cutting is
  sample-exact with respect to recorded offsets; this bounded skew is accepted clinically.
- **Format negotiation:** preferred format is PCM 48 kHz / 16-bit / mono. If the selected
  device does not support it, the recorder picks the nearest supported PCM format (higher
  sample rate preferred; stereo if mono is unavailable — processing downmixes). The actual
  capture format is stored in `examination.json` at session start (§8.10). If no PCM format
  is available, the device is reported ineligible in Settings. Resampling/downmixing happens
  only during processing (§8.8), never live in the recorder.

### 5.4 Repositories

```
ConfigurationRepository     cached remote config: load/save/activeConfig Flow
AppSettingsRepository       local-only settings: mic device, installation ID, language
SessionRepository           session metadata, participant.json, examination.json
TimelineRepository          event log + timeline_original.json / timeline_edited.json
WaveformCacheRepository     downsampled min/max peaks per session
UploadQueueRepository       pending/failed uploads
```

Upload state has one authority: the per-session `metadata/upload_status.json`.
`upload_queue/queue.json` is a **derived index** rebuilt from session metadata at startup;
if they disagree, session metadata wins.

An app-lifetime **UploadWorker** owned by `AppContainer` runs the retry loop: it scans the
queue, computes `nextAttemptAt` from each session's `attemptCount` and last attempt time
(both persisted in `upload_status.json`), and executes due uploads with exponential backoff
(§8.9). This makes retries restart-safe by construction — schedules are recomputed from
session metadata at startup.

### 5.5 Use cases (only where real logic exists)

```
RefreshConfigurationUseCase   fetch by installationId → validate → cache → activate
StartSessionUseCase           preflight checks, create session dir, snapshot config, start recorder
ProcessSessionUseCase         pick timeline (edited > original), cut clips, zip
UploadSessionUseCase          upload session ZIP, update statuses, queue retries
ValidateParticipantInfoUseCase regex validation per configured field
RecoverSessionsUseCase        startup crash recovery (§8.4)
```

---

## 6. Remote Configuration

### 6.1 Flow

1. Settings stores an **installation ID** (entered once at deployment; replaces the old
   `clinicId` preference).
2. On startup (and on manual "refresh" in Settings) the app sends a GET request containing
   the installation ID (endpoint path to be specified; placeholder
   `GET /api/config/{installationId}`). **The server validates the installation ID during
   this request** — there is no separate registration step; an unknown/disabled ID yields an
   error response, which the app surfaces on the configuration-required screen.
3. The response JSON is validated against `schemaVersion`, persisted **atomically** (write to
   `config.json.tmp`, fsync, rename over `data/config/config.json`), and becomes the active
   configuration.
4. **Offline fallback:** if the server is unreachable, the last cached config is used. If no
   cache exists (first run offline), the app shows a blocking "configuration required" screen
   (rendered from the bundled fallback strings, §7).
5. **Refresh semantics:** a fetched config takes effect for the **next session**. A running
   or recorded session always uses its own snapshot
   (`task_configuration_snapshot.json`) — later pushes never affect it.
6. **Versioning/migration:** each app release declares a supported `schemaVersion` range.
   A server response outside the range is rejected (logged; cache kept). A *cached* config
   below the range is migrated by versioned migration functions if possible, otherwise the
   app behaves as "no cache". The server response always replaces the cache regardless of
   `configVersion` ordering — the server is authoritative.
7. **Transport:** HTTPS with certificate validation for all `ConfigApi` and `UploadApi`
   calls. The installation ID acts as a bearer credential and must never appear in log
   files (§11).

### 6.2 Schema — normative corrections to the draft

`Task_Configuration_JSON_Spec.md` incorporates these corrections and is normative. For
traceability against the original draft:

| Draft | Correction | Why |
|---|---|---|
| `enableEditor: 0`, `showIndicator: 1`, `canRepeat: 1`, … | Real JSON booleans (`true`/`false`) | Type safety in kotlinx.serialization |
| `"type": "phonation"` inside a VOCAL example | `"type": "VOCAL"` discriminator + `"subtype": "PHONATION" \| "PATAKA" \| "READING" \| ...` | Consistent polymorphic decoding (sealed class + `classDiscriminator`) |
| `QUESTIONAIRE` | `QUESTIONNAIRE` | Spelling; lenient alias while the server migrates |
| `taskNumber: "1/10"` stored per task | **Removed** — derived from position in the protocol | Redundant, guaranteed to drift |
| `recordingsFilePath` (absolute path) | Removed; recordings always go under `AppDirectories.sessions` | Config is machine-independent |
| `recordingsFileName` template | Kept as the **authoritative** clip-name source; must include `${taskIndex}` (validated at config load) so names are unique | Two tasks with the same subtype must not collide |
| Legacy `_b`/`/b` markup in strings | `<bold>`/`<italic>` tags only; legacy markers are not recognized and render as literal text (§13 decision 26) | Single RichText parser |
| `clinicID` | `installationId` everywhere | One name for one concept |

Task types in scope: `VOCAL`, `QUESTIONNAIRE`, `CALIBRATION`, `INFO`.
`VIDEO` is reserved in the schema; the app skips such tasks with a logged warning.

`CALIBRATION` behavior: mandatory **when the protocol contains at least one VOCAL task** and
must precede the first VOCAL task (config validation rejects protocols where a VOCAL task
comes before calibration). It shows live input level against the configured `optimalLoudness`
range (linear RMS, 0.0–1.0 full scale) and must be confirmed before the master recording
starts. **Protocols with no VOCAL tasks (questionnaire/info-only) skip calibration and do not
record a master WAV at all.**

`indicatorType` selects the live recording feedback on VOCAL task screens: `CIRCLE` — a
pulsating circle whose radius/opacity follows the RMS level; `WAVEFORM` — a rolling ~3 s
amplitude envelope drawn on a canvas. Both consume `ContinuousSessionRecorder.levels`.

Config-level fields: `schemaVersion`, `configVersion`, `defaultLanguage`, `languages`,
`defaultMicName` (hint only — local Settings wins), `enableEditor`, `indicatorType`,
`protocols[]`, `strings{lang → {key → value}}`, and `patientFields[]` (participant-input
field definitions — name, label key, regex, required, useInFilename). The `${patientCode}`
template variable is **composed from the `useInFilename` fields**: their values, sanitized
to `[A-Za-z0-9_-]` (Windows/macOS-safe), joined with `_` in config order.

---

## 7. Localization

- User-facing strings come from the active config's `strings` map, **overlaid on a small
  bundled built-in string set (English)** compiled into the app. The built-ins cover everything that
  must render before/without a config: the configuration-required screen, Settings (device,
  installation ID, refresh), error dialogs, and the single-instance message. Precedence:
  config string → built-in → the key itself (logged; never a raw `"ERROR"`).
- **No hard-coded display literals in UI code** — UI references keys; the bundled fallback
  table is the only place built-in text lives.
- `LocalizedStringProvider` resolves `key → RichText` for the selected language, falling back
  to `defaultLanguage`, then to built-ins.
- `RichTextParser` handles `<bold>`/`<italic>` (nested allowed) — the only supported markup
  (§13 decision 26). Output is a `RichText` model rendered as `AnnotatedString`.
- Placeholder substitution uses named placeholders: `{vowel}`, `{length}`, `{version}`
  (replaces the original's `XX`/`xx`).
- Language switching UI (flag icons, as in the original) is kept, limited to `languages` from
  the config; the chosen language is persisted in `AppSettingsRepository`. Exact-match key
  lookup (fixes the `contains()` XPath bug).

---

## 8. Recording Model and Session Data

### 8.1 Core rules

- One continuous master WAV per session (only for protocols containing VOCAL tasks):
  preferred format PCM signed, 48 kHz, 16-bit, mono (negotiation per §5.3.1). Recording
  starts when calibration is confirmed and stops when the protocol ends.
- Start/Stop buttons on task screens create **timeline events**; they never touch the mic.
- The master recording and `timeline_original.json` are never modified after creation.
- **Expected session length: up to 30 minutes** (~165 MiB of audio at target format) — a
  sizing assumption for waveform-cache, memory, and upload budgets, **not enforced**: no
  warning, no hard stop; recording runs until the examiner ends the protocol.
- **Disk preflight:** `StartSessionUseCase` requires free space for a worst-case session
  computed from the **negotiated** capture format (30 min × bytes/s, with margin for clips
  + zip; at least 1 GiB at the target format) or refuses to start with a localized error.
  If the disk fills mid-session, the recorder stops cleanly, preserves everything written,
  and the session is marked incomplete (recoverable via §8.4).
- All timestamps in every JSON file are ISO-8601 UTC.

### 8.2 Session directory layout

```
data/
  config/        config.json, settings.json
  sessions/<yyyy-MM-dd_PatientCode_SessionId>/
    participant.json
    examination.json
    task_configuration_snapshot.json
    timeline.events.jsonl         (append-only event log, §8.3)
    timeline_original.json        (compacted on clean stop)
    timeline_edited.json          (only if editor was used)
    master/session_master.wav
    waveform_cache/master_waveform.cache
    clips/                        (per recordingsFileName template)
    archive/<PatientCode>_<SessionId>.zip
    metadata/upload_status.json
  upload_queue/queue.json         (derived index, §5.4)
  logs/app.log
```

Folder-name conventions: the date is **clinic-local time** (all JSON timestamps remain
ISO-8601 UTC); `PatientCode` is sanitized to `[A-Za-z0-9_-]` before use in any path or ZIP
name; `SessionId` comes from `IdGenerator` (§5.3).

### 8.3 Timeline: events, repetitions, takes

Terminology:

- **Task instance** — one screen in the protocol. A task with `nrepetition: N` expands into
  **N separate task instances** ("Rep 1" … "Rep N"), navigated like any other tasks.
- **Take** — one Start→Stop attempt within a task instance. If `canRepeat` is enabled, the
  examiner can press Repeat any number of times; each press rejects the current take and
  starts a new one. **Only the last take of each task instance is cut out during
  processing.** Earlier takes remain in the master recording and in the timeline (audit
  trail) but are not exported. Pressing Next accepts the current (last) take implicitly.
  Example: a task with 2 repetitions, each tried 3 times, produces 2 exported clips (the
  third take of each instance), with all 6 takes preserved in the master and timeline.

Events (JSONL, one object per line, **written and `FileChannel.force()`d after every
event** — at this event rate, fsync cost is negligible):

```
SESSION_RECORDING_STARTED · TASK_SCREEN_ENTERED · START_BUTTON_PRESSED ·
STOP_BUTTON_PRESSED · TAKE_REJECTED · TASK_SKIPPED · TASK_COMPLETED ·
RECORDING_INTERRUPTED · RECORDING_RESUMED · SESSION_RECORDING_STOPPED
```

Each event carries: `type`, `sampleOffset` (from `writtenSamples` at event time — the
**primary reference for cutting**; `null` in no-master sessions, §8.8), `wallClock`
(ISO-8601 UTC, metadata only), `taskIndex` (position in the expanded protocol),
`repetition`, `take` (1-based, **counted per task instance**).

On clean session stop the log is compacted into `timeline_original.json`:

```json
{
  "version": 1,
  "sessionId": "…",
  "sampleRate": 48000,
  "events": [ { "type": "START_BUTTON_PRESSED", "sampleOffset": 1234567,
                "wallClock": "2026-07-01T09:30:00Z",
                "taskIndex": 3, "repetition": 1, "take": 2 }, … ]
}
```

The JSONL log is kept (never deleted) as the raw audit record.

### 8.4 Crash safety and recovery

Both safeguards are mandatory (not alternatives):

1. **Valid-header partial file:** the recorder writes to `master/session_master.partial.wav`
   with a real WAV header patched periodically (every ~5 s) to match the bytes written. Data
   writes and header patches go through **one** `RandomAccessFile`/`FileChannel` handle
   (avoids Windows file-sharing conflicts). Atomic rename to `session_master.wav` on clean
   stop.
2. **Startup recovery (`RecoverSessionsUseCase`):** on launch, any session containing a
   `*.partial.wav` and/or an uncompacted `timeline.events.jsonl` is recovered: the WAV header
   is reconstructed from `captureFormat` in `examination.json` (written at session start,
   §8.10) + file length; the event log — tolerating a torn final line after power loss — is
   compacted into
   `timeline_original.json` (a synthetic `SESSION_RECORDING_STOPPED` is appended at the last
   written sample), and the session is marked `recovered: true` / incomplete in
   `examination.json`. Recovered sessions appear in the session browser and can be processed
   and uploaded like any other.

### 8.5 Device disconnection mid-session — continue with gap

Decision: **continue with gap**, do not abort.

- Detection (per the capture architecture, §5.3.1): (a) exceptions/closure reported by the
  line; (b) the watchdog on the last-successful-read timestamp (> 1 s starvation while
  `Writing`); (c) a secondary **silence heuristic** — on both target OSes a removed device
  can keep delivering zero-filled buffers, so sustained digital silence (> 5 s of all-zero
  samples while a take is open) raises a non-blocking "check microphone" warning rather
  than an interruption. Device hot-plug behavior on Windows and macOS must be verified with
  a **spike before this section is implemented** (`AudioSystem` mixer enumeration may be
  cached; the re-enumeration strategy is part of the spike's output).
- On detection: state → `Interrupted`; a `RECORDING_INTERRUPTED` event is logged at the
  current `sampleOffset`; the current take (if any) is auto-rejected (`TAKE_REJECTED`,
  reason `DEVICE_LOST`) and the task instance returns to `Idle` (§8.6); the UI shows a
  blocking Compose dialog prompting the examiner to reconnect or select another input
  device (via `AudioInputDeviceProvider`).
- On resume: the recorder opens the (possibly different) device and **continues into a new
  master part file** (`session_master.part2.wav`, …) in that device's negotiated format —
  no live resampling. `RECORDING_RESUMED` is logged and the examiner redoes the interrupted
  take. Processing (§8.8) cuts each take from the part its offsets fall in
  (`RECORDING_INTERRUPTED`/`RESUMED` events delimit the parts), converts per part
  (resample/downmix) to the target format, and concatenates the converted parts into the
  archived `session_master.wav`.
- The gap exists in wall-clock time, not in samples. Interruptions are summarized in
  `examination.json` (`interruptions[]`: sampleOffset, wallClock start/end, old/new device,
  new part file).
- Device loss while `Monitoring` (calibration — no session recording yet): no timeline is
  involved; the calibration screen shows the device-lost error and lets the examiner
  re-select a device and restart monitoring.

### 8.6 Task screen state machine

Navigation is by **index into the expanded task-instance list**, never by object identity.
Button states are a sealed hierarchy with an exhaustive `when` (no `TODO()` branches):

```
Idle        Start ✓   Stop ✗   Repeat ✗   Next ✗   (Skip ✓ if canSkip)
Capturing   Start ✗   Stop ✓   Repeat ✗   Next ✗
Stopped     Start ✗   Stop ✗   Repeat ✓*  Next ✓   (* if canRepeat)
Failed      Start ✓   Stop ✗   Repeat ✗   Next ✗   (+ error dialog)
```

(`Capturing` = an open take; the mic itself runs continuously.)

`QUESTIONNAIRE` tasks render their `questions[]` (OPEN with regex validation,
SINGLE_CHOICE, MULTIPLE_CHOICE); answers are stored in `examination.json` (§8.10). `INFO`
tasks are display-only with a Next button.

Example audio (`audioExamplePath`) is played through the system output on demand; during an
active recording this **will bleed into the master** — accepted; the UI disables example
playback while a take is open (`Capturing`), and instructions recommend headphones. Note
that widening editor boundaries into inter-take audio (§8.7) can pull such bleed into an
exported clip.

### 8.7 Editor

Shown after the protocol when `enableEditor` is true. A **segment** is the last take of one
VOCAL task instance; the editor shows one segment at a time.

- Waveform: downsampled min/max peaks, cached in `waveform_cache/` (the cache file format is
  an implementation detail), regenerated if missing; only the visible portion is rendered.
- **Initial boundary lines sit exactly at the take's START/STOP event offsets.** The ± 5 s
  is the *visible context window* around them (clamped to adjacent-take boundaries and file
  range), **not** a boundary offset — an untouched editor pass must yield clips identical to
  the no-editor path.
- Interactions: drag start/stop boundary lines, segment playback with a moving position
  line, previous/next segment navigation, accept.
- **`timeline_edited.json` is written only if at least one boundary was actually moved.** An
  untouched pass writes nothing and `timelineUsed` stays `"original"`.
- Boundary validation: start < stop, within recorded range, no overlap with adjacent
  segments' final boundaries.

### 8.8 Processing

`ProcessSessionUseCase`, behind a progress screen that blocks accidental navigation:

1. Choose `timeline_edited.json` if present, else `timeline_original.json`.
2. Cut the **last take** of each VOCAL task instance from the master (sample-accurate,
   resampling to target format if the capture format differs).
3. Write clips to `clips/` named by the protocol's `recordingsFileName` template
   (variables: `${installationId}`, `${patientCode}`, `${taskIndex}`, `${task.subtype}`,
   `${repetition}`).
4. Build `archive/<PatientCode>_<SessionId>.zip` containing: `participant.json`,
   `examination.json`, `task_configuration_snapshot.json`, `timeline_original.json`,
   `timeline_edited.json` (if present), `master/session_master.wav`, `clips/*`, and
   `manifest.json` (SHA-256 of every included file + sessionId + configVersion).
   **Excluded:** `archive/` itself, `waveform_cache/`, `timeline.events.jsonl`,
   `metadata/`.
5. Update `examination.json` with clip paths, processing status, `timelineUsed`.

**No-master sessions** (questionnaire/info-only protocols, §6.2): steps 2–3 are skipped and
there is no `master/` or `clips/`. The event log still exists (events carry
`"sampleOffset": null`); processing means building the ZIP + manifest from the JSON files
only.

Reprocessing (from the session browser) regenerates `clips/`, `archive/`, and
`waveform_cache/` — these are **derived artifacts**, exempt from the never-delete rule
(§8.9). Upload status resets to `NotUploaded` only if the session was never successfully
uploaded; **`Uploaded` is terminal** (§8.9) — reprocessing an already-uploaded session
regenerates local artifacts, but re-upload is blocked.

### 8.9 Upload

- **The upload payload is the session ZIP only** (plus form fields: `installationId`,
  `sessionId`, ZIP SHA-256). Nothing else is sent; the master is inside the ZIP (once).
- Authorization: **possession of the app and a valid installation ID** — the server validates
  the ID on upload exactly as on config fetch. No separate token/secret is provisioned.
  HTTPS required.
- Idempotency: `sessionId` is the idempotency key. **A successfully uploaded session cannot
  be uploaded again** — the server rejects a `sessionId` it has already accepted, and the
  app treats `Uploaded` as terminal (upload actions disabled in the UI).
- Statuses in `metadata/upload_status.json`: `NotUploaded`, `Uploading`, `Uploaded`
  (terminal), `Failed` (the single-ZIP payload removes `PartiallyUploaded`). Failures enter
  the retry queue run by the `UploadWorker` (§5.4): exponential backoff, max 5 automatic
  attempts, manual retry always available.
- **Primary session data is never deleted after upload** (master, timelines, JSONs,
  participant data). Derived artifacts may be regenerated (§8.8).

**Upload screen** (kept from the original app's `UploadScreen`/`UploadComponent`, modernized
per §5):

- Reached via an **Upload button on the main screen**.
- Shows: an instruction card, the **list of sessions eligible for upload** (status
  `NotUploaded` or `Failed`, i.e. processed sessions with an archive; replaces the
  original's scan for `*.zip` files) with a "N sessions ready to upload" count, a progress
  bar, an Upload button, and a Back button.
- Pressing **Upload** starts a sequential batch upload of all listed sessions (each via
  `UploadSessionUseCase`); the progress bar aggregates across sessions, fed by the Ktor
  multipart progress callback (per-session fraction + completed-sessions fraction, as in
  the original).
- Outcome display: on success, a "files successfully uploaded" state; on failure, a
  localized **error message with the reason** next to the affected session — a failed
  session does not stop the batch (remaining sessions still upload; the final state
  reflects partial success). Each session's result is persisted to its
  `upload_status.json` as it completes.
- Uploaded sessions disappear from the list (`Uploaded` is terminal); failed ones remain
  listed for manual retry, in addition to the `UploadWorker`'s automatic retries (§5.4).

### 8.10 File schemas

All persisted JSON has `@Serializable` models with a `version` field. **Write lifecycle:**
`participant.json` and `examination.json` (with `captureFormat`, `startedAt`,
`configVersion`) are created at **session start**; `examination.json` is updated
incrementally after every task completion via atomic tmp+rename — a crash never loses more
than the current task's answers, and recovery (§8.4) always finds `captureFormat`. Sketches
(normative field sets; exact Kotlin models live in `domain/`):

```jsonc
// participant.json
{ "version": 1, "fields": { "code": "HC001", "sex": "F", … }, "createdAt": "…" }

// examination.json
{
  "version": 1,
  "sessionId": "…", "installationId": "…",
  "protocolName": "Share", "configVersion": "2026-07-01.1",
  "startedAt": "…", "endedAt": "…",
  "captureFormat": { "sampleRate": 48000, "bits": 16, "channels": 1 },
  "recovered": false,
  "interruptions": [ { "sampleOffset": 0, "start": "…", "end": "…", "device": "…" } ],
  "tasks": [
    { "taskIndex": 3, "type": "VOCAL", "subtype": "PHONATION", "repetition": 1,
      "takes": 3, "skipped": false, "clipFile": "clips/…wav",
      "questionnaireAnswers": null },
    { "taskIndex": 5, "type": "QUESTIONNAIRE", "repetition": 1,
      "questionnaireAnswers": { "question_key": ["answer"] } }
  ],
  "processing": { "status": "Done", "processedAt": "…", "timelineUsed": "edited" }
}

// timeline_edited.json  (segment-shaped — directly consumable by the cutter)
{ "version": 1, "sessionId": "…", "sampleRate": 48000, "basedOn": "original",
  "segments": [ { "taskIndex": 3, "repetition": 1,
                  "startSample": 1230000, "stopSample": 1710000 } ] }

// metadata/upload_status.json  (authoritative, §5.4)
{ "version": 1, "status": "Uploaded", "zipSha256": "…", "uploadedAt": "…",
  "serverResponse": { … },
  "attempts": [ { "at": "…", "outcome": "…" } ], "attemptCount": 1 }

// upload_queue/queue.json  (derived index)
{ "version": 1, "entries": [ { "sessionId": "…", "attemptCount": 2, "nextAttemptAt": "…" } ] }
```

### 8.11 Session browser

Lists previous sessions (id, patient code, date, processing/upload status, recovered flag);
actions: open editor, reprocess, upload/retry. Metadata must remain loadable at any time;
any session is reprocessable from master + timeline.

---

## 9. Auto-Updater (simplified)

Kept as a separate `:updater` native executable (GraalVM native-image or Kotlin/Native), but
minimal:

1. `GET /api/version/latest` → `{ "version", "downloadUrl", "checksum" }` (models in `:shared`).
2. If newer than local `app/version.json`: download, verify SHA-256, back up `app/`, replace,
   restore backup on failure. The update package contains the new `app/version.json`.
3. Launch the app via the bundled jlink JRE (`runtime/bin/java -jar app/app.jar`) and exit.
4. Unreachable server ⇒ launch existing app without updating.
5. The updater never touches `data/`.

Install layout: `<install_dir>/{updater[.exe], runtime/, app/, data/}` — built for the two
target platforms, Windows and macOS (macOS distribution requires signing/notarization; a
packaging concern outside this document's scope). Anything beyond this (delta updates,
channels, UI) is out of scope.

---

## 10. Testing Strategy

### 10.1 Unit tests (pure JVM, no hardware/fs/network)

Config JSON parsing + validation (booleans, discriminators, `${taskIndex}` template check,
lenient enum aliases, calibration-ordering rule), **config
schema-migration functions**, RichText parsing, participant regex validation, filename
template rendering, task-instance expansion (`nrepetition`), take accept/reject logic,
timeline event compaction, sample-offset arithmetic, edited-boundary validation (inversion,
range, adjacent overlap), processing timeline selection, upload status state machine +
backoff, updater version comparison and backup/restore decision logic.

### 10.2 Integration tests (temp dirs, Ktor MockEngine)

Round-trip persistence of every §8.10 schema; session folder creation; clip cutting from a
bundled test WAV with known sample offsets (assert exact sample boundaries); resampling path;
waveform downsampling; zip creation incl. manifest hashes and exclusion list; **crash
recovery: run `RecoverSessionsUseCase` against fixture sessions containing a `.partial.wav`
+ uncompacted JSONL and assert reconstructed header, compacted timeline, `recovered` flag**;
config fetch/upload against `MockEngine` (success, invalid installation ID, HTTP errors,
network failure → queue, retry/backoff); atomic config write (no partial file after
simulated failure); single-instance lock.

### 10.3 UI tests — simulated click workflows

This is a first-class requirement. Approach:

- **Framework:** Compose UI Test for Desktop (`compose.uiTest` / `runComposeUiTest`,
  JUnit 5). CI runs headless with Skia **software rendering** (e.g. `-Dskiko.renderApi=SOFTWARE`
  plus a virtual display where required) — this must be part of the CI setup from day one.
- **Test tags:** every interactive composable gets a stable
  `Modifier.testTag("screen.element")`, e.g. `task.startButton`, `task.nextButton`,
  `calibration.confirmButton`, `patientInfo.field.code`. Tags are constants in a single
  `TestTags` object shared by prod and test code.
- **Full fake container:** `AppContainer` is constructable with all fakes
  (`FakeContinuousSessionRecorder` synthesizes PCM data, levels, and a `writtenSamples`
  counter that advances with `FakeClock`; `FakeConfigApi` serves a fixture config JSON;
  temp `AppDirectories`; **deterministic test dispatchers** via the injected
  `CoroutineDispatchers`, §5.2). UI tests set the real `RootComponent` content and drive it
  purely through clicks. Note there are **two clocks to coordinate**: the Compose test `mainClock`
  (animations/recomposition) and the injected `FakeClock` (domain time/sample counter);
  scenario helpers advance both together.
- **Desktop input:** pointer interactions use `performMouseInput` (click, press-drag-release
  for editor boundaries) — not `performTouchInput`.
- **Workflow scenarios** (the core suite):
  1. *Happy path:* launch → home → start protocol → fill patient fields → calibration confirm
     → for each task: Start → advance clocks → Stop → Next → processing completes → summary
     shows all clips → upload (MockEngine) succeeds. Assert the session directory contents,
     ZIP contents, and manifest afterwards.
  2. *Repeat flow:* two-repetition task, three takes each → assert 6 takes in the timeline,
     2 exported clips cut from takes 3 and 6.
  3. *Validation:* invalid visit number blocks continuation, error text shown.
  4. *Editor flow:* with `enableEditor: true`, drag a boundary via `performMouseInput`,
     accept, assert `timeline_edited.json` and that reprocessing uses it; also assert that
     an untouched pass writes no `timeline_edited.json` and exports identical clips.
  5. *Offline config:* no network + cached config → app usable; no cache → blocking screen
     (rendered from bundled fallback strings).
  6. *Device loss:* fake recorder signals `Interrupted` mid-take → dialog → select new fake
     device → resume → redo take → assert gap events and auto-rejected take in the timeline.
  7. *Questionnaire-only protocol:* no calibration screen, no master WAV created, answers in
     `examination.json`; assert the archive contains only the JSON files + manifest (§8.8).
  8. *Batch upload:* two eligible sessions, MockEngine fails the first and accepts the
     second → assert the error reason is shown for the first, the second still uploads,
     both `upload_status.json` files are correct, and the uploaded session leaves the list.
- Component-level tests (no Compose) remain the cheaper layer: instantiate a component with
  fakes, call its methods, assert state `Value`/`Flow` — see the existing
  `RecorderComponentTest` as the pattern.

### 10.4 Conventions

- Every bug listed in §4 gets a regression test in the rewrite.
- New features require: unit tests for logic, a component test for state, and — if the flow
  changes — an update to a workflow scenario.

---

## 11. Error Handling

Structured sealed error types per subsystem, mapped to localized user messages; technical
detail goes to `logs/app.log` (kotlin-logging + Logback). **Log lines must never contain
participant data** (patient code, field values, questionnaire answers) **or the installation
ID** (it acts as a bearer credential) — sessions are referenced by `sessionId` only. Never surface raw keys, stack traces, or placeholder text in
the UI.

Error taxonomy (normative, inlined from the old plan):

- **AudioError:** microphone unavailable/ineligible, recording start failed, device lost
  mid-session (§8.5), unsupported format with no fallback.
- **StorageError:** insufficient disk space (preflight and mid-session), disk write failure,
  missing or corrupt session metadata, waveform generation failure, clip export failure,
  ZIP creation failure, lock acquisition failure (second instance).
- **ConfigError:** installation ID rejected by server, network unreachable with no cache,
  schema version unsupported, config validation failure (bad discriminator, VOCAL before
  CALIBRATION, non-unique filename template).
- **UploadError:** network unavailable, server rejected submission, checksum mismatch,
  retry limit reached.
- **Updater:** version check unreachable (proceed without updating), download
  failed/incomplete, checksum mismatch (discard, keep current), replacement failure
  (rollback), app failed to launch post-update (restore backup on next run).

---

## 12. Repository Conventions for AI Agents

- **Modules:** product code in `:app` under `org.example.app`; packages `domain/`,
  `infrastructure/`, `navigation/` (Decompose components), `ui/` (composables only).
- **Build/test:** `./gradlew :app:test` (JUnit 5), `./gradlew :app:run` to launch.
- **Current state:** the repo contains a working skeleton — `RootComponent` with Home and a
  demo Recorder screen, `AudioRecorder` port + `JvmAudioRecorder`, `FakeAudioRecorder`, and
  one component test. Extend this skeleton toward §5; the demo `RecorderComponent` will be
  replaced by the session/task components.
- **Dependency injection:** manual, via `AppContainer` only. Do not add a DI framework.
- **Serialization:** kotlinx.serialization for every persisted or transferred JSON; all
  formats defined here get `@Serializable` models with `version` fields; ISO-8601 UTC
  timestamps.
- **No display literals in UI code** (keys + bundled fallback table only, §7), no
  `Preferences`, no singletons holding state, no `TODO()` in reachable branches, no
  participant data in logs.
- When the config schema changes, update `docs/Task_Configuration_JSON_Spec.md` and the
  parsing tests in the same change.

---

## 13. Resolved Decisions and Remaining Open Questions

**Resolved (2026-07-01):**

1. **Config fetch:** on start the app sends a GET request containing the set installation ID
   and the server returns the configuration JSON. The exact endpoint path will be specified
   later; code against the `ConfigApi` interface so the URL is a single configuration point.
2. **Installation-ID validation:** performed by the server during that same request (error
   response for unknown/disabled IDs). No separate registration step.
3. **Upload payload:** the session ZIP only; the master recording is included inside the ZIP.
4. **Questionnaire answers:** stored inside `examination.json`, uploaded within the ZIP.
5. **Mic disconnection:** continue with gap — examiner reconnects/reselects a device, the
   interrupted take is auto-rejected and redone; gap recorded in timeline + examination
   metadata (§8.5).
6. **Questionnaire/info-only protocols:** skip calibration and record no master WAV (§6.2).
7. **Session length:** maximum expected 30 minutes (soft limit with warning) (§8.1).
8. **Upload authorization:** anyone with the app and a valid assigned installation ID can
   upload; the ID is validated server-side on each upload. No separate credential (§8.9).
9. **Repetitions:** each repetition is a separate task instance; every one can be re-tried
   via Repeat when enabled; only the last take per instance is cut out, earlier takes remain
   in the master recording (§8.3).

**Resolved (2026-07-02, second review round):**

10. **Target platforms: Windows and macOS** (§1).
11. **Upload is once-only:** after a successful upload the session cannot be uploaded again —
    the server rejects the `sessionId`, and `Uploaded` is terminal in the app (§8.9).
12. **Bundled fallback strings are English** (§7).
13. **An untouched editor pass does not count as edited** — no `timeline_edited.json` is
    written; `timelineUsed` stays `original` (§8.7).
14. **Session folder dates use clinic-local time**; JSON timestamps remain UTC (§8.2).
15. **No 30-minute warning:** session length is unbounded in the UI; 30 minutes remains a
    sizing assumption only (§8.1).
16. **Upload UX follows the original app:** Upload button on the main screen → upload
    screen listing sessions ready for upload → Upload starts a sequential batch with
    aggregated progress → success message or per-session localized error reason (§8.9).

**Resolved (2026-07-03, Phase 1 implementation review):**

17. **Recorder failures are reported via state, not exceptions:** `startMonitoring`/
    `startWriting`/`resume` never throw on device/format/IO failure; they set
    `state = Failed(error)` (or `Interrupted`). Callers observe `state` (§5.3.1).
18. **Partial-file naming:** the staging file for any master (part) file is derived by
    stripping the `.wav` suffix and appending `.partial.wav`
    (`session_master.part2.wav` → `session_master.part2.partial.wav`) (§8.4).
19. **Part finalization on interruption:** when the device is lost, the abandoned part is
    finalized (final header patch + atomic rename) immediately at interruption time, not at
    session stop — a crash during the gap leaves only valid, non-partial part files (§8.5).
20. **`writtenSamples` across parts:** one continuous frame counter across all master parts,
    including a format-changing device swap. Per-part local offsets are derived by
    subtracting the counter value at the enclosing `RECORDING_INTERRUPTED`/`RESUMED`
    boundary; cutting always operates per part (§8.5), so no cross-rate global time
    interpretation is needed.
21. **`TAKE_REJECTED` events carry an optional `reason`** (`EXAMINER_REPEAT` for a Repeat
    press, `DEVICE_LOST` for the §8.5 auto-reject); all other event types leave it null (§8.3).
22. **`taskIndex` is 0-based** in all persisted data and domain models (position in the
    expanded instance list); UI-facing "task N of M" numbering renders `taskIndex + 1`.
23. **VIDEO tasks are excluded from the expanded task-instance list entirely** — they occupy
    no screen and consume no `taskIndex`; the skip is logged with a warning at expansion
    time (§6.2).
24. **RichText edge semantics:** an unresolved `{placeholder}` renders literally (visible
    failure, consistent with §7's key-fallback philosophy).
25. **Config fetch timeouts:** 15 s request / 10 s connect are the confirmed defaults for
    `ConfigApi` until the real server contract says otherwise.
26. **Legacy `_b`/`/b` markup support removed entirely (2026-07-03):** `<bold>`/`<italic>`
    tags are the only markup; legacy markers are not detected, not converted, and render as
    literal text. Configs must be authored in tag syntax. (Supersedes the auto-convert
    behavior originally specified in §6.2/§7 and the conversion semantics in decision 24.)

**Still open:**

1. Concrete server API contracts: config endpoint path, upload endpoint shape, and transport
   hardening (non-enumerable installation IDs, config authenticity/signing; whether the
   installation ID travels in the URL path — where reverse proxies log it — or in an
   `Authorization` header). The old Mars
   endpoints (`/recordings/`, `/task/`, `/log/` on `speech.fel.cvut.cz`) used secret+userid
   form fields; the new contract is pending.
2. Data retention / erasure: local data is never auto-deleted — is a manual
   retention/erasure procedure (GDPR requests, device decommissioning) or encryption at rest
   required? Currently out of scope (§2).
3. Is `patientCode` guaranteed pseudonymous? It appears in folder names, clip filenames, and
   the ZIP name.
