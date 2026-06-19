# Remote Task Configuration — JSON Specification (Draft)

**Status:** Draft · **Date:** 2026-06-10 · **Target:** Next version of the SHARE clinical recording app

## 1. Purpose

The app sends its **clinicID** and gets a single **configuration JSON** from a web server at
startup (or on demand). This file fully describes:

- one or more **protocols** (ordered lists of configured tasks),
- the **task** (eq. one screen in the protocol),
- the **application config** (patient info, editor on/off, locale, ...),

## 2. Top-level structure

```json
{
  "schemaVersion": 1,
  "configVersion": "2026-06-10.1",
  "defaultLanguage": "cs",
  "defaultMicName" : "USBAudioDevice",
  "enableEditor" : 0,
  "indicatorType" : enum(CIRCLE, WAVEFORM),
  "languages": ["cs", "en"],  
  "protocols": [ /* Protocol objects, §3 */ ],
  "strings": { /* localization, */ }
}
```

## 3. Protocols

A protocol is a named, ordered list of configured tasks

```json
{
  "name": "Share",
  "manualFilePath": "protocol_manuals/MDSE_app_manual 2024.pdf",  
  "tasks": [ /* Task objects, §3 */],
  "recordingsFilePath": "/home/tomas/share",
  "recordingsFileName": "${Task.name}_${Task.nrepetition}"
}
```

## 4. Task

`VOCAL`, `QUESTIONAIRE`,`VIDEO`,`CALIBRATION`,`INFO`

VOCAL = ddk, phonation etc.
VIDEO = emotions
INFO = final screen..

```json
{
  "type": "phonation",
  "nTextFields": 2,
  "taskNumber": "1/10",
  "titleKey": "phonation_title",
  "instructionKeys": ["phonation_instructions1", "phonation_instructions2"],
  "length": 10,
  "showIndicator": 1,
  "canRepeat": 1,
  "canSkip": 0,  
  "audioExamplePath": "audio_instructions/aaa.wav",
  "nrepetition": 1
}
```
## 5. Minimal end-to-end example

```json
{
  "schemaVersion": 1,
  "configVersion": "2026-06-10.1",
  "defaultLanguage": "cs",
  "defaultMicName": "USBAudioDevice",
  "enableEditor": 0,
  "indicatorType": "CIRCLE",
  "languages": ["cs"],
  "protocols": [
    {
      "name": "Share",
      "manualFilePath": "protocol_manuals/MDSE_app_manual 2024.pdf",
      "tasks": [
        {
          "type": "VOCAL",
          "nTextFields": 2,
          "taskNumber": "1/1",
          "titleKey": "phonation_title",
          "instructionKeys": ["phonation_instructions1"],
          "length": 10,
          "showIndicator": 1,
          "canRepeat": 1,
          "canSkip": 0,
          "audioExamplePath": "audio_instructions/aaa.wav",
          "nrepetition": 1
        }
      ],
      "recordingsFilePath": "/home/user/recordings",
      "recordingsFileName": "${Task.name}_${Task.nrepetition}"
    }
  ],
  "strings": {
    "cs": {
      "phonation_title": "Prodloužená fonace",
      "phonation_instructions1": "_bZačněte teď./bStiskněte START pro zahájení nahrávání."
    }
  }
}
```
