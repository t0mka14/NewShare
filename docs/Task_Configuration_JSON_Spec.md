# Remote Configuration — JSON Specification

**Status:** Normative · **Date:** 2026-07-01 · **Target:** SHARE clinical recording app (rewrite)

Companion to [Project_Specification.md](Project_Specification.md) (§6). This document defines
the configuration JSON pushed from the server to the app.

## 1. Purpose and fetch flow

On startup (and on manual refresh in Settings) the app sends a **GET request containing its
installation ID** and receives a single configuration JSON.

- Endpoint path: **to be specified** (placeholder used in this doc:
  `GET /api/config/{installationId}`).
- **Validation happens during this request:** the server validates the installation ID and
  either returns the configuration or an error response (unknown/disabled ID). There is no
  separate registration step.
- On success the app validates `schemaVersion`, caches the JSON to `data/config/config.json`,
  and activates it.
- If the server is unreachable, the last cached config is used. With no cache (first run
  offline) the app shows a blocking "configuration required" screen.
- Every recorded session stores a snapshot of the active config
  (`task_configuration_snapshot.json`).

The configuration fully describes:

- one or more **protocols** (ordered lists of configured tasks),
- each **task** (one screen in the protocol),
- the **application config** (participant input fields, editor on/off, locale, …),
- all **localized strings**.

## 2. Top-level structure

```json
{
  "schemaVersion": 1,
  "configVersion": "2026-07-01.1",
  "defaultLanguage": "cs",
  "languages": ["cs", "en"],
  "defaultMicName": "USBAudioDevice",
  "enableEditor": false,
  "indicatorType": "CIRCLE",
  "patientFields": [ /* PatientField objects, §5 */ ],
  "protocols": [ /* Protocol objects, §3 */ ],
  "strings": { /* localization, §6 */ }
}
```

| Field | Type | Notes |
|---|---|---|
| `schemaVersion` | int | Parser compatibility; app rejects unknown major versions |
| `configVersion` | string | Server-side revision identifier, stored in session snapshots |
| `defaultLanguage` | string | Fallback language |
| `languages` | string[] | Languages selectable in the UI |
| `defaultMicName` | string | Hint only — the locally selected device in Settings wins |
| `enableEditor` | boolean | Show waveform boundary editor after the protocol |
| `indicatorType` | enum | `CIRCLE` (pulsating circle following RMS level) \| `WAVEFORM` (rolling ~3 s amplitude envelope) — live feedback on VOCAL task screens |

All flags are real JSON booleans (`true`/`false`), never `0`/`1`.

## 3. Protocol

A protocol is a named, ordered list of configured tasks. Task numbering ("task 3 of 10") is
**derived from list position** and is not stored in the JSON.

```json
{
  "name": "Share",
  "manualFilePath": "protocol_manuals/MDSE_app_manual 2024.pdf",
  "recordingsFileName": "${installationId}_${patientCode}_${taskIndex}_${task.subtype}_Rep${repetition}",
  "tasks": [ /* Task objects, §4 */ ]
}
```

- `recordingsFileName` — clip filename template, the authoritative source of clip names.
  Supported variables: `${installationId}`, `${patientCode}`, `${taskIndex}` (position in
  the expanded task list), `${task.subtype}`, `${repetition}`. The template **must include
  `${taskIndex}`** so that two tasks with the same subtype cannot produce colliding
  filenames; config validation rejects templates without it.
- Recordings are always stored under the app data directory
  (`data/sessions/<session>/…`); the config never contains filesystem paths for output
  (machine independence).
- If the protocol contains at least one `VOCAL` task, a `CALIBRATION` task must precede the
  first `VOCAL` task (validated at config load). Protocols with no `VOCAL` tasks
  (questionnaire/info-only) contain no calibration and produce no master recording.

## 4. Task

Tasks are polymorphic on the `type` discriminator:

`VOCAL` · `QUESTIONNAIRE` · `CALIBRATION` · `INFO` · `VIDEO` (reserved)

- `VOCAL` — audio recording tasks (phonation, DDK/PATAKA, reading, monologue, …), refined by
  `subtype`.
- `QUESTIONNAIRE` — form screens (spelling normalized; the parser accepts the legacy alias
  `QUESTIONAIRE` with a logged warning during server migration).
- `CALIBRATION` — mandatory microphone level check, normally the first task.
- `INFO` — display-only screen (e.g., final screen).
- `VIDEO` — reserved for a later phase (emotions, PTZ camera). The current app skips these
  tasks with a logged warning.

Fields common to all task types:

| Field | Type | Notes |
|---|---|---|
| `type` | enum | Discriminator, see above |
| `titleKey` | string | Localization key |
| `canRepeat` | boolean | Repeat/Again button available |
| `canSkip` | boolean | Skip button available |
| `nrepetition` | int | Number of repetitions; the task expands into this many **separate task instances** ("Rep 1" … "Rep N") in the protocol flow |

Repetition semantics: with `canRepeat`, each task instance can be re-tried any number of
times (each Repeat press rejects the current take and starts a new one). **Only the last
take of each instance is cut out during processing**; earlier takes remain in the master
recording and timeline as an audit trail. E.g. two repetitions tried three times each yield
two exported clips.

### 4.1 VOCAL

```json
{
  "type": "VOCAL",
  "subtype": "PHONATION",
  "titleKey": "phonation_title",
  "instructionKeys": ["phonation_instructions1", "phonation_instructions2"],
  "length": 10,
  "showIndicator": true,
  "canRepeat": true,
  "canSkip": false,
  "audioExamplePath": "audio_instructions/aaa.wav",
  "nrepetition": 1
}
```

- `subtype`: `PHONATION` | `PATAKA` | `SYLLABLES` | `READING` | `MONOLOGUE` | `RETELLING` |
  `COUNTING` | `CUSTOM`.
- `length` — target duration in seconds.
- `instructionKeys` — one key per instruction paragraph (replaces the draft's `nTextFields`;
  the count is the array length).
- `audioExamplePath` — optional example audio, played on demand, never recorded.

### 4.2 QUESTIONNAIRE

```json
{
  "type": "QUESTIONNAIRE",
  "titleKey": "questionnaire_title",
  "questions": [ /* Question objects, §4.2.1 */ ],
  "length": 200,
  "canRepeat": true,
  "canSkip": false,
  "nrepetition": 1
}
```

Answers are stored inside the session's **`examination.json`** and uploaded with it (no
separate answers file or endpoint).

#### 4.2.1 Question

```json
{
  "questionType": "OPEN",
  "questionKey": "question_key",
  "questionTextKey": "question_text_key",
  "questionRegex": ".*",
  "questionOptions": ["optionA_key", "optionB_key"]
}
```

- `questionType`: `OPEN` | `SINGLE_CHOICE` | `MULTIPLE_CHOICE`.
- `questionRegex` — validation for `OPEN` answers only.
- `questionOptions` — localization keys, required for choice types, absent for `OPEN`.

### 4.3 CALIBRATION

```json
{
  "type": "CALIBRATION",
  "titleKey": "calibration_title",
  "instructionKeys": ["calibration_instructions"],
  "optimalLoudness": [0.2, 0.5]
}
```

Mandatory whenever the protocol contains `VOCAL` tasks (must precede the first one); the
examiner must confirm the observed level within `optimalLoudness` `[min, max]` before the
master recording begins. Units: linear RMS normalized to full scale (0.0–1.0), smoothed over
a ~300 ms window.

### 4.4 INFO

```json
{
  "type": "INFO",
  "titleKey": "info_title",
  "instructionKeys": ["info_instructions1", "info_instructions2"]
}
```

### 4.5 VIDEO (reserved, deferred)

```json
{
  "type": "VIDEO",
  "subtype": "EMOTIONS",
  "titleKey": "emotions_title",
  "instructionKeys": ["emotions_instructions"],
  "length": 30,
  "canRepeat": true,
  "canSkip": false,
  "nrepetition": 1,
  "havePTZ": false
}
```

## 5. PatientField

Participant-input configuration comes from the server (replaces the local patient
configuration screens of the original app).

```json
{
  "name": "visitNumber",
  "labelKey": "patient_visit_number_label",
  "helpKey": "patient_visit_number_help",
  "placeholder": "V0",
  "regex": "V\\d+",
  "required": true,
  "useInFilename": true
}
```

- `name` — stable identifier used in `participant.json`.
- `regex` — validation pattern; empty means free text.
- `useInFilename` — value (sanitized to `[a-zA-Z0-9_-]`) participates in
  `${patientCode}`-style filename composition.

## 6. Strings / localization

One map per language: `strings.<lang>.<key> → value`.

- Inline markup: `<bold>text</bold>` and `<italic>text</italic>` (nesting allowed). Legacy
  `_b` / `/b` tags are **not valid**; the app does not recognize them and they render as
  literal text.
- Placeholders use named syntax: `{vowel}`, `{length}`, `{version}` (replaces the old
  `XX`/`xx` conventions).
- Missing key resolution: selected language → `defaultLanguage` → the key itself (logged).

## 7. Minimal end-to-end example

```json
{
  "schemaVersion": 1,
  "configVersion": "2026-07-01.1",
  "defaultLanguage": "cs",
  "languages": ["cs"],
  "defaultMicName": "USBAudioDevice",
  "enableEditor": false,
  "indicatorType": "CIRCLE",
  "patientFields": [
    {
      "name": "code",
      "labelKey": "patient_code_label",
      "helpKey": "patient_code_help",
      "placeholder": "HC001",
      "regex": "[a-zA-Z0-9_-]+",
      "required": true,
      "useInFilename": true
    }
  ],
  "protocols": [
    {
      "name": "Share",
      "manualFilePath": "protocol_manuals/MDSE_app_manual 2024.pdf",
      "recordingsFileName": "${installationId}_${patientCode}_${taskIndex}_${task.subtype}_Rep${repetition}",
      "tasks": [
        {
          "type": "CALIBRATION",
          "titleKey": "calibration_title",
          "instructionKeys": ["calibration_instructions"],
          "optimalLoudness": [0.2, 0.5]
        },
        {
          "type": "VOCAL",
          "subtype": "PHONATION",
          "titleKey": "phonation_title",
          "instructionKeys": ["phonation_instructions1"],
          "length": 10,
          "showIndicator": true,
          "canRepeat": true,
          "canSkip": false,
          "audioExamplePath": "audio_instructions/aaa.wav",
          "nrepetition": 1
        },
        {
          "type": "INFO",
          "titleKey": "info_title",
          "instructionKeys": ["info_done"]
        }
      ]
    }
  ],
  "strings": {
    "cs": {
      "calibration_title": "Kalibrace",
      "calibration_instructions": "Mluvte běžnou hlasitostí a sledujte ukazatel úrovně.",
      "patient_code_label": "Kód pacienta",
      "patient_code_help": "Např. HC001",
      "phonation_title": "Prodloužená fonace",
      "phonation_instructions1": "<bold>Začněte teď.</bold> Stiskněte START pro zahájení nahrávání.",
      "info_title": "Hotovo",
      "info_done": "Vyšetření je dokončeno."
    }
  }
}
```
