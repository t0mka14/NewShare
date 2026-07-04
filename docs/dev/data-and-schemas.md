# Data and schemas

Normative source: spec §8. Every persisted JSON has a `@Serializable` Kotlin model with a
`version` field; all timestamps are ISO-8601 UTC strings; folder-name dates are
clinic-local (§8.2). All writes go through atomic tmp+fsync+rename
(`infrastructure/persistence/AtomicFileWriter.kt`, `RawConfigCache` for the config).

## Directory layout (runtime `data/`)

```
data/
  app.lock                          single-instance lock (FileChannel.tryLock)
  config/
    config.json                     raw cached remote config (RawConfigCache)
    settings.json                   AppSettings — mic device id, installation ID, language
  sessions/<yyyy-MM-dd_Patient_SessionId>/
    participant.json                ParticipantRecord
    examination.json                Examination — updated after every task (atomic)
    task_configuration_snapshot.json  raw config the session ran with (immutable)
    timeline.events.jsonl           append-only event log, fsync per event, never deleted
    timeline_original.json          compacted on clean stop / recovery (immutable)
    timeline_edited.json            ONLY if an editor boundary moved; complete segment list
    master/session_master.wav       immutable; .partial.wav while recording; .partN.wav after
                                    device-loss resume (each part in its own negotiated format)
    waveform_cache/                 derived; versioned peak cache (regenerable)
    clips/                          derived; per recordingsFileName template (regenerable)
    archive/<Patient>_<SessionId>.zip  derived; ZIP + manifest (regenerable)
    metadata/upload_status.json     UploadStatus — the single authority for upload state
  logs/app.log                      rolling, no participant data / installation ID ever
```

There is no persisted upload queue (decision 34) — the upload screen computes its list on
demand (`EligibleUploadsQuery`).

## Models → files

| File | Model (in `domain/`) | Repository |
|---|---|---|
| `participant.json` | `session/ParticipantRecord.kt` | `SessionRepository` |
| `examination.json` | `session/Examination.kt` (+ `TaskRecord`, `Interruption`, `ProcessingInfo`) | `SessionRepository` |
| `timeline.events.jsonl` | `timeline/TimelineEvent.kt` + `TimelineEventCodec` | `TimelineRepository` |
| `timeline_original.json` | `timeline/TimelineOriginal.kt` (+ `TimelineCompactor`) | `TimelineRepository` |
| `timeline_edited.json` | `timeline/TimelineEdited.kt` | `TimelineRepository` |
| `upload_status.json` | `upload/UploadStatus.kt` | `UploadStatusRepository` |
| `config/config.json` | `config/RemoteConfig.kt` via `ConfigDecoder`/`ConfigValidator` | `ConfigurationRepository` |
| `config/settings.json` | `settings/AppSettings.kt` | `AppSettingsRepository` |
| `manifest.json` (in ZIP) | `session/SessionArchiveManifest.kt` | built by `ZipSessionArchiveService` |

## Timeline model (§8.3)

Ten event types (`SESSION_RECORDING_STARTED` … `SESSION_RECORDING_STOPPED`). Each event:
`type`, `sampleOffset` (from the recorder's continuous `writtenSamples` counter; `null` in
no-master sessions), `wallClock` (metadata only), `taskIndex` (0-based, position in the
expanded instance list), `repetition`, `take` (1-based per instance), optional `reason`
on `TAKE_REJECTED` (`EXAMINER_REPEAT` | `DEVICE_LOST`). A task with `nrepetition: N` is N
instances; only the last take per instance is exported; everything stays in the master +
log as audit trail. The JSONL reader tolerates a torn final line (power loss).

## Multi-part masters (§8.5, decisions 19/20/28)

Device loss mid-session: the abandoned part is finalized immediately; resume continues
into `session_master.part2.wav` in the *new* device's negotiated format. Each
`examination.interruptions[]` entry records `sampleOffset`, wall-clock start/end, device,
`partFile`, and that part's `captureFormat` — recovery rebuilds a partial part's WAV
header from the format of *that part*. `MasterPartMap` (domain/session) converts global
sample offsets to (part file, local offset) during processing.

## Upload status lifecycle (§8.9, decisions 34/35)

`NotUploaded` → `Uploading` → `Uploaded` (terminal) | `Failed` (manual retry only).
`attempts[]` + `attemptCount` are the audit trail. A leftover `Uploading` (crash
mid-upload) is listed as failed/"interrupted" and retryable. `Uploaded` sessions can be
reprocessed (regenerates derived artifacts) but never re-uploaded.

## Adding or changing a schema

See [`cookbook.md`](cookbook.md) recipe 5. Golden rules: bump `version` when the shape
changes incompatibly, add a migration, keep round-trip tests in
`app/src/test/.../infrastructure/persistence/`, and never remove fields the server
contract or recovery path still reads.
