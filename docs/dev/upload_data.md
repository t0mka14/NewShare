# Upload data format

Version 1.0, 2026-10-15

What the app sends to the server for one session, and the exact format of every file in it.
It is written for two readers:

- **this repo:** anyone changing processing, the archive contents, or a schema that ends up in
  the ZIP must update this document in the same change;
- **the server:** what to expect in an upload and how to read it.

Out of scope: transport (URL, HTTPS, auth, timeouts, response codes). For those see
[`server-upload-integration.md`](server-upload-integration.md) and spec §13 decision 62. The
normative sources are
spec §8.8 (processing), §8.9 (upload) and §8.10 (schemas). The code is the source of truth
wherever the two disagree:

| Concern | Code |
|---|---|
| Request parts | `infrastructure/network/KtorUploadApi.kt`, `domain/upload/UploadSessionUseCase.kt` |
| What goes into the ZIP | `domain/session/ProcessSessionUseCase.kt`, `domain/session/SessionArchiveContents.kt` |
| ZIP + manifest writing | `infrastructure/persistence/ZipSessionArchiveService.kt`, `domain/session/SessionArchiveManifest.kt` |
| JSON models | `domain/session/{ParticipantRecord,Examination,SessionHardware}.kt`, `domain/timeline/{TimelineEvent,TimelineOriginal,TimelineEdited}.kt` |

The examples below are trimmed from a real session, `2026-10-13_testx_76a67cd8af73486da13a7c0e1509bd2f`.

---

## 1. The upload unit

**One request per session**, `multipart/form-data`, with these parts and nothing else. The
text parts come before `zip`:

| Part | Type | Value |
|---|---|---|
| `installationId` | text | The installation's ID (UUID with dashes), e.g. `6f933f6e-c07a-414f-9800-47b37253d409`. Same value as `examination.json.installationId`. |
| `sessionId` | text | The session ID: a random UUIDv4 as 32 lowercase hex chars, **no dashes**, e.g. `76a67cd8af73486da13a7c0e1509bd2f`. |
| `zipSha256` | text | SHA-256 of the whole ZIP file as 64 lowercase hex chars. Recomputed just before every upload attempt. |
| `siteToken` | text | The site token from Settings (a credential, never logged). Omitted when none is set. |
| `appVersion` | text | The app's version, e.g. `1.4.0`. |
| `zip` | file | `Content-Type: application/zip`, `filename="<patientCode>_<sessionId>.zip"`, e.g. `testx_76a67cd8af73486da13a7c0e1509bd2f.zip`. |

- `sessionId` is the **idempotency key**. The app uploads each session successfully at most
  once. The server answers a re-send with identical bytes with 200 and the original receipt, and
  only a re-send with different bytes with 409, so an app that lost `upload_status.json` can
  safely upload again.
- The file name embeds the participant's `patient_code`, so it is participant data. Don't
  log it on the server.
- Sizes: a session with video can be hundreds of MB. The example is about 239 MB uncompressed, almost
  all of it MP4.

## 2. ZIP layout

```
<patientCode>_<sessionId>.zip
├── master/session_master.wav            (if the protocol recorded audio)
├── clips/<name>.wav                     one per recorded VOCAL task instance
├── clips/<name>.mp4                     one per filmed task instance
├── examination.json
├── participant.json
├── task_configuration_snapshot.json
├── timeline_original.json
├── timeline_edited.json                 (only if the editor moved a boundary)
└── manifest.json                        always the last entry
```

| Entry | Present | Format | Content |
|---|---|---|---|
| `participant.json` | always | JSON | Patient info form values (§5.1). |
| `examination.json` | always | JSON | Session metadata, per-task outcomes, questionnaire answers, devices used (§5.2). |
| `task_configuration_snapshot.json` | always | JSON | The site config the session ran with, byte-for-byte (§5.3). |
| `timeline_original.json` | always | JSON | Compacted event log (§5.4). |
| `timeline_edited.json` | only if an editor boundary was moved | JSON | The edited clip boundaries (§5.5). |
| `master/session_master.wav` | if `examination.captureFormat` is non-null | WAV | The whole session's audio (§4.1). |
| `clips/*.wav` | per recorded VOCAL instance | WAV | One clip per task instance (§4.2). |
| `clips/*.mp4` | per filmed instance | MP4 | One video per task instance (§4.3). |
| `manifest.json` | always | JSON | Integrity list (§3). |

Entry ordering: the master, then the files under the session folder in filesystem-walk order,
then `manifest.json` last. Don't rely on any order except "manifest is last".

**Never included:** the raw master or its device-loss parts (`session_master.partN.wav`), the raw
`video/*.mjpeg` takes, `timeline.events.jsonl` (the raw event log, superseded by
`timeline_original.json`), `waveform_cache/`, `metadata/` (local upload bookkeeping), or
`archive/`.

**No-master sessions** (questionnaire/info-only protocols) have no `master/`. They have `clips/`
only if a VIDEO task produced MP4s. In this case the JSON files carry all the data.

ZIP details: standard ZIP (`java.util.zip`, DEFLATE, no encryption, no ZIP64-specific
features beyond what the JDK writes automatically for large entries), entry names are
forward-slash, relative, with no leading `/` and no directory entries.

## 3. `manifest.json`

Integrity and provenance, readable without unzipping anything else.

| Field | Type | Meaning |
|---|---|---|
| `version` | int | Schema version, currently `1`. **Omitted when 1** (see §6). |
| `sessionId` | string | Same as the `sessionId` form field. |
| `configVersion` | string | `configVersion` of the config the session ran with. |
| `generatedAt` | string | ISO-8601 UTC time the ZIP was built. |
| `files[]` | array | One `{ "path", "sha256" }` per entry **other than `manifest.json` itself**. `path` is the ZIP entry name; `sha256` is lowercase hex of the entry's **uncompressed** bytes. |

```json
{"sessionId":"76a67cd8af73486da13a7c0e1509bd2f","configVersion":"2026-10-05.080231",
 "generatedAt":"2026-10-13T07:56:26.245909500Z",
 "files":[
  {"path":"master/session_master.wav","sha256":"05699b94…677c95f"},
  {"path":"clips/6f933f6e-c07a-414f-9800-47b37253d409_testx_0_PHONATION_1.wav","sha256":"d60506bf…bae064"},
  …
  {"path":"timeline_original.json","sha256":"e74b820a…ffb3ef"}]}
```

Suggested server checks, in order:

1. SHA-256 of the received file equals the `zipSha256` form field (detects a truncated or corrupted transfer).
2. `manifest.json.sessionId` equals the `sessionId` form field, and equals `examination.json.sessionId`.
3. Every `files[]` entry exists and its hash matches. No ZIP entry is missing from `files[]`, except `manifest.json`.

## 4. Media formats

### 4.1 Master: `master/session_master.wav`

- PCM WAV, **48 kHz, 16-bit signed little-endian, mono**. This is always the target format, even when
  the microphone captured something else; in that case it was resampled/downmixed.
- One continuous file for the whole session, from `SESSION_RECORDING_STARTED` to
  `SESSION_RECORDING_STOPPED`. It includes the gaps between tasks and every rejected take, so it is the
  audit trail.
- If a microphone was lost and the session resumed (`examination.interruptions[]`), the parts
  are converted and concatenated into this single file. The time the device was missing is **not**
  represented in the audio, so samples continue straight on.

### 4.2 Audio clips: `clips/<name>.wav`

- Same format as the master (48 kHz / 16-bit / mono PCM WAV).
- One per **non-skipped VOCAL task instance**. It holds the **last take only**, cut sample-accurately at the
  take's START/STOP offsets from `timeline_original.json`, or at the segment boundaries from
  `timeline_edited.json` when that file exists.
- The task instance a clip belongs to is given by `examination.json.tasks[].clipFile` (e.g.
  `"clips/<name>.wav"`). Use that, not file-name parsing.

### 4.3 Video clips: `clips/<name>.mp4`

- MP4 container (`+faststart`, so `moov` comes first) wrapping the camera's **MJPEG** video stream
  copied as-is (no re-encode, every frame kept). It has **no audio track**; the audio is the matching
  `.wav` clip or the master.
- Resolution and frame rate are those in `examination.json.videoTakes[].captureFormat` for the kept
  take, typically 1920×1080 @ 30 fps.
- One per filmed task instance (a VIDEO task, or a VOCAL task with `recordVideo`). It holds the last take only.
  An instance whose camera failed (no frames) has no MP4, and its audio clip is still present.
- Linked from `examination.json.tasks[].videoFile`.

### 4.4 Clip file names

Clip names come from the protocol's `recordingsFileName` template in the config snapshot. The
variables are `${installationId}`, `${siteName}`, `${taskIndex}`, `${task.subtype}`, `${repetition}`, and
`${field.<name>}` (from `participant.json`), but the list can include other, custom variables. The `.wav` and `.mp4` files of one instance share a base name. For
example, the template `${installationId}_${field.patient_code}_${taskIndex}_${task.subtype}_${repetition}` gives
`6f933f6e-c07a-414f-9800-47b37253d409_testx_4_READING_1.wav` and `…_READING_1.mp4`. The
template is site-configurable, so the server must not hard-code it. Map files to tasks through
`clipFile`/`videoFile`.

## 5. JSON files

All JSON is UTF-8. Read §6 first for how optional fields are encoded.

### 5.1 `participant.json`

| Field | Type | Meaning |
|---|---|---|
| `version` | int | `1` (omitted when 1). |
| `fields` | object string→string | The protocol's `patientFields` values keyed by field `name`, all as strings. Which keys exist depends on the protocol (see the snapshot's `protocols[].patientFields`). |
| `createdAt` | string | ISO-8601 UTC time the session started. |

```json
{"fields":{"current_date":"2026-10-13","patient_code":"testx","sex":"female",
           "year_of_birth":"1111","examiner":"vi"},
 "createdAt":"2026-10-13T07:54:51.746661100Z"}
```

### 5.2 `examination.json`

Top level:

| Field | Type | Meaning |
|---|---|---|
| `version` | int | `1` (omitted when 1). |
| `sessionId` | string | 32-hex session ID. |
| `installationId` | string | Installation that recorded it. |
| `protocolName` | string | Protocol `name` in the config snapshot. |
| `protocolProject` | string? | Protocol `project`. Together with `protocolName` it identifies the protocol in the snapshot. Absent in older sessions. |
| `configVersion` | string | Snapshot's `configVersion`. |
| `startedAt` / `endedAt` | string / string? | ISO-8601 UTC. `endedAt` is absent if the session never finished cleanly. |
| `captureFormat` | object? | `{ sampleRate, bits, channels }` that the microphone actually delivered. This can differ from the archived 48k/16/1. Absent for no-master protocols. |
| `recovered` | bool | `true` if the session was rebuilt after an app crash. Absent means `false`. |
| `interruptions[]` | array | Microphone losses. See below. Absent means none. |
| `tasks[]` | array | One per task **instance**. See below. |
| `videoTakes[]` | array | Every filmed take that was started. See below. Absent means none. |
| `hardware` | object? | Devices actually used. See below. |
| `processing` | object | `{ status, processedAt, timelineUsed }`. In an upload, always `status: "Done"`, `processedAt` (ISO UTC), and `timelineUsed`, which is `"original"` or `"edited"`. |

`tasks[]` entries:

| Field | Type | Meaning |
|---|---|---|
| `taskIndex` | int | 0-based position in the **expanded** instance list. A config task with `nrepetition: N` becomes N consecutive instances. |
| `type` | string | Config task type: `VOCAL`, `QUESTIONNAIRE`, `VIDEO`, or `INFO`. |
| `subtype` | string? | VOCAL subtype, e.g. `PHONATION`, `PATAKA`, `READING`. |
| `repetition` | int | 1-based repetition of the config task. |
| `takes` | int | Takes recorded for this instance. Only the last one is exported. Absent means `0`. |
| `skipped` | bool | Examiner skipped the instance. Absent means `false`. |
| `clipFile` | string? | ZIP path of the audio clip, e.g. `clips/….wav`. |
| `videoFile` | string? | ZIP path of the MP4, e.g. `clips/….mp4`. |
| `questionnaireAnswers` | object? | QUESTIONNAIRE only: `questionKey → [answer, …]` (always a list, even for single-choice). |

`interruptions[]`: `sampleOffset` (global sample where the loss happened), `start`/`end`
(ISO UTC), `oldDevice`/`newDevice` (strings, optional), `partFile` (raw part name, which is not in the ZIP),
and `captureFormat` (the format of the part after resume).

`videoTakes[]`: `file` (raw take path, which is not in the ZIP), `taskIndex`, `repetition`, `take`,
`captureFormat: { width, height, fps }`. Every **started** take is listed, including rejected
ones. `timeline_original.json` tells which take was kept.

`hardware` (diagnostics):

- `configuredMicDeviceId` / `configuredCameraDeviceId`: the devices selected in Settings.
- `micUses[]`: `{ openedAt, deviceId, deviceName, description, inputGainPercent?, captureFormat }`, one per time the microphone was opened.
- `cameraUses[]`: `{ openedAt, deviceId, deviceName, nameOrdinal, platformIndex, captureFormat }`.
- `events[]`: `{ at, device: "microphone"|"camera", event, detail? }`, e.g. `interrupted`, `capture_failed`, `gain_changed`. `capture_failed` is only for a camera that ended unexpectedly: the app closing the camera itself (after the last filmed take) records nothing. Its `detail` is one short reason (at most 300 characters, the last meaningful ffmpeg error line), never ffmpeg's progress output.

```jsonc
{"sessionId":"76a67cd8af73486da13a7c0e1509bd2f",
 "installationId":"6f933f6e-c07a-414f-9800-47b37253d409",
 "protocolName":"Czech_prot_test","protocolProject":"Test Study 001",
 "configVersion":"2026-10-05.080231",
 "startedAt":"2026-10-13T07:54:51.746661100Z","endedAt":"2026-10-13T07:56:25.310278800Z",
 "captureFormat":{"sampleRate":48000,"bits":16,"channels":1},
 "tasks":[
   {"taskIndex":0,"type":"VOCAL","subtype":"PHONATION","repetition":1,"takes":1,
    "clipFile":"clips/6f933f6e-…_testx_0_PHONATION_1.wav"},
   {"taskIndex":4,"type":"VOCAL","subtype":"READING","repetition":1,"takes":1,
    "clipFile":"clips/6f933f6e-…_testx_4_READING_1.wav",
    "videoFile":"clips/6f933f6e-…_testx_4_READING_1.mp4"}, …],
 "videoTakes":[{"file":"video/task04_rep01_take01.mjpeg","taskIndex":4,"repetition":1,"take":1,
                "captureFormat":{"width":1920,"height":1080,"fps":30}}, …],
 "hardware":{"configuredMicDeviceId":"Microphone Array (AMD Audio Device)|Unknown Vendor|Unknown Version",
   "micUses":[{"openedAt":"2026-10-13T07:54:51.298964900Z","deviceId":"…","deviceName":"Microphone Array (AMD Audio Device)",
               "description":"Direct Audio Device: DirectSound Capture",
               "captureFormat":{"sampleRate":48000,"bits":16,"channels":1}}, …],
   "cameraUses":[{"openedAt":"2026-10-13T07:55:39.684307500Z","deviceId":"dshow:0:Integrated Camera",
                  "deviceName":"Integrated Camera","nameOrdinal":0,"platformIndex":0,
                  "captureFormat":{"width":1920,"height":1080,"fps":30}}]},
 "processing":{"status":"Done","processedAt":"2026-10-13T07:56:26.2Z","timelineUsed":"original"}}
```

### 5.3 `task_configuration_snapshot.json`

The raw remote config the session ran with, stored verbatim (pretty-printed as received). The format is
in [`docs/Task_Configuration_JSON_Spec.md`](../Task_Configuration_JSON_Spec.md). Use it to resolve the
session's protocol (`protocols[]` matched by `name` + `project`), its task list, the
`patientFields`, and the `recordingsFileName` template.

### 5.4 `timeline_original.json`

The event log of the session, compacted. It is the authoritative description of what happened when.

| Field | Type | Meaning |
|---|---|---|
| `version` | int | `1` (omitted when 1). |
| `sessionId` | string | |
| `sampleRate` | int | Sample rate that `sampleOffset` values are counted in (the capture rate). |
| `events[]` | array | In order. Fields below. |

Each event has the following fields. All keys are always present, with `null` used where a value doesn't apply.

- `type`: one of `SESSION_RECORDING_STARTED`, `TASK_SCREEN_ENTERED`, `START_BUTTON_PRESSED`,
  `STOP_BUTTON_PRESSED`, `TAKE_REJECTED`, `TASK_SKIPPED`, `TASK_COMPLETED`,
  `RECORDING_INTERRUPTED`, `RECORDING_RESUMED`, `SESSION_RECORDING_STOPPED`.
- `sampleOffset`: long or `null`. The master sample index at the event. `null` in no-master sessions.
- `wallClock`: ISO-8601 UTC. Informational only; cut by `sampleOffset`.
- `taskIndex` / `repetition` / `take`: int or `null`. `take` is 1-based per instance and `null` before a take
  starts (e.g. on `TASK_SCREEN_ENTERED`).
- `reason`: only on `TAKE_REJECTED`, either `EXAMINER_REPEAT` or `DEVICE_LOST`. Otherwise the key is absent.

A take is the span between `START_BUTTON_PRESSED` and `STOP_BUTTON_PRESSED` with the same
`(taskIndex, repetition, take)`. The exported take is the last take of each instance that was not
rejected.

```json
{"sessionId":"76a67cd8af73486da13a7c0e1509bd2f","sampleRate":48000,"events":[
 {"type":"SESSION_RECORDING_STARTED","sampleOffset":0,"wallClock":"2026-10-13T07:55:19.253586600Z","taskIndex":null,"repetition":null,"take":null},
 {"type":"TASK_SCREEN_ENTERED","sampleOffset":0,"wallClock":"…","taskIndex":0,"repetition":1,"take":null},
 {"type":"START_BUTTON_PRESSED","sampleOffset":76800,"wallClock":"…","taskIndex":0,"repetition":1,"take":1},
 {"type":"STOP_BUTTON_PRESSED","sampleOffset":144000,"wallClock":"…","taskIndex":0,"repetition":1,"take":1},
 {"type":"TASK_COMPLETED","sampleOffset":182400,"wallClock":"…","taskIndex":0,"repetition":1,"take":1},
 …
 {"type":"SESSION_RECORDING_STOPPED","sampleOffset":3170400,"wallClock":"…","taskIndex":null,"repetition":null,"take":null}]}
```

Mapping offsets onto the archived master: when `examination.captureFormat` is 48000/16/1 and
there were no interruptions (the normal case), `sampleOffset` indexes `master/session_master.wav`
directly. Otherwise, offsets are on one continuous counter across all master parts.

1. Part 1 starts at `0` in `examination.captureFormat`. Each `interruptions[i]` starts a new part at its
   `sampleOffset`, in its own `captureFormat`.
2. An offset's position in the archived master is `(offset − partStart) × 48000 / partRate`, plus the
   48 kHz lengths of all earlier parts.

The clips are already cut correctly, so this matters only for analyzing the master itself.

### 5.5 `timeline_edited.json` (optional)

Present only if the examiner moved at least one boundary in the editor. When present, it is the complete
segment list the clips were cut from, and `examination.processing.timelineUsed` is `"edited"`.

| Field | Type | Meaning |
|---|---|---|
| `version` | int | `1` (omitted when 1). |
| `sessionId` | string | |
| `sampleRate` | int | Units of the sample fields. |
| `basedOn` | string | `"original"` (omitted, since that is the default). |
| `segments[]` | array | `{ taskIndex, repetition, startSample, stopSample }`, one per exported VOCAL instance; `stopSample` is exclusive. |

```json
{"sessionId":"…","sampleRate":48000,
 "segments":[{"taskIndex":3,"repetition":1,"startSample":1230000,"stopSample":1710000}]}
```

## 6. Conventions for consumers

- **Default-valued fields are omitted.** The app serializes with kotlinx.serialization defaults
  (`encodeDefaults = false`), so a field equal to its default is not written. Read a missing
  `version` as `1`, a missing boolean as `false`, a missing list as `[]`, a missing count as `0`, and a missing
  nullable as `null`. Fields with no default, like the timeline event keys, are always written, even
  as `null`.
- **Ignore unknown keys.** New optional fields are added without a version bump. `version` is bumped
  only for incompatible changes.
- Timestamps are ISO-8601 UTC (`Z`), with 0–9 fractional-second digits.
- Indexing: `taskIndex` is 0-based; `repetition` and `take` are 1-based.
- IDs: `sessionId` is 32 hex chars without dashes; `installationId` is a dashed UUID.
- JSON files are compact (single line), except `task_configuration_snapshot.json`, which is stored as received.

## 7. Changing this format

Any change to the ZIP contents, the manifest, the media formats, or a schema in §5 must:

1. update this document;
2. follow [`cookbook.md`](cookbook.md) recipe 5 for schema changes (bump `version` on breaking changes);
3. be coordinated with the server before release, because the server reads these files.
