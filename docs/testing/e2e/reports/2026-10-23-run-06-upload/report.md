# Run 06: Upload and the server record

- Date / time: 2026-10-24 00:34–00:41 local (22:34–22:41 UTC)   Runner: Sonnet   App commit: `59bb063`
- Preconditions met: yes. 5 processed, not-uploaded sessions; token A.
- Result: passed with findings

The orchestrator saved this report from the runner's returned text and renumbered the
findings. The orchestrator checked F-038 against the log files and withdrew F-018 because of it.

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Upload screen 📷 | pass | "5 sessions ready to upload". `03-1`, `04-1`, `05-1`, `05-2` and `05-3` are each listed once. `step-1-upload-screen.png` |
| 2 | Press Upload 📷 | pass | The batch took about 4 s on the LAN, one session at a time. The progress bar is per batch: the tree showed 0.6 with "3 sessions ready". The Upload button is disabled during the batch. No pixel screenshot caught the bar part-way. It ended with "No sessions are ready to upload." and "Files successfully uploaded." No error. On disk, all 5 `upload_status.json` read `Uploaded`, `attemptCount` 1, `Success`, with the receipt stored. `step-2-during-upload.png`, `step-2-during-upload-b.png` |
| 3 | Sessions after upload 📷 | pass | All 5 show "Processed Uploaded" and have no per-row upload action. Open editor and Reprocess stay (§8.5). `step-3-sessions-after-upload.png` |
| 4 | Admin Uploads, filtered to `E2E` 📷 | pass | 5 rows under E2E-1023-Site-A, with the right protocol, patient code and date. All **valid** within a minute; none stayed validating. The `hasVideo` filter returns exactly `05-1`, `05-2` and `05-3`. Flags: `03-1` skipped and retakes; `04-1` retakes and edited; `05-*` "Device event" (from F-032, see F-036). `step-4-uploads-filtered-E2E.png`, `step-4-uploads-hasvideo.png` |
| 5 | Details for runs 03 and 05 📷 | pass with findings | `03-1`: Phonation Rep1 2 takes, last kept; the monologue is **SKIPPED**; the questionnaire is DONE with 4 answers (F-040). All 7 server checks are green. Attempts: one, HTTP 200. `05-1`: 4/4 tasks, 3 MP4s, OBS camera 1280×720@30. The masking check can't be done (F-039). F-035, F-036. `step-5-detail-*` |
| 6 | Downloads: WAV, MP4, ZIP | pass with finding | All 5 ZIPs downloaded (UI button or API). Each SHA-256 equals the local archive and the context file; `04-1` matches its reprocessed hash. The WAV is pcm_s16le 48 kHz mono, 9.65 s. The MP4 passes ffprobe and decodes completely: mjpeg 1280×720, 19.3 s, no audio stream. It won't play in the web UI (F-037). Downloads are in the scratchpad only. |
| 7 | Devices | pass | Device 27, Site A: waiting 0, failed 0, 5 uploads in 30 days, last upload 22:34:29Z, last check-in 22:34:30Z, "Healthy". The app has no log line for the check-in (F-038). `step-7-devices.png` |
| 8 | Re-send idempotency (curl) | pass | The unchanged `04-1` ZIP gave **200** with the original receipt (`receivedAt` 22:34:26.798Z). A modified ZIP (extra entry, its own correct hash) under the same `sessionId` gave **409** `{"error":"session_exists"}`. Attempts: 200 accepted, 200 resent, 409 conflict. The record is unchanged. Side effect: `04-1` now has the "Re-sent" flag. `logs/step-8-resend-curl.txt`, `logs/step-8-attempts-04-1.json` (redacted). |

### Server records

Every upload ID equals its `sessionId`. All are on site 12, status valid, HTTP 200.

| Session | sessionId | bytes | receivedAt (UTC) |
|---|---|---|---|
| E2E1023-03-1 | `60cf6e0af3ed40c599d15d65e5765b51` | 13,374,949 | 22:34:26.453Z |
| E2E1023-04-1 | `fb60e12fb4124064b8ba83c3543ee003` | 10,294,736 | 22:34:26.798Z |
| E2E1023-05-1 | `c54d0d83b29245cbbc08c44ae830a633` | 132,913,303 | 22:34:27.551Z |
| E2E1023-05-2 | `edb79f0539504d788ae1675b279b76bb` | 7,814,984 | 22:34:28.563Z |
| E2E1023-05-3 | `fbd7b3f6eafe46b58e016769f47cc325` | 66,044,799 | 22:34:29.114Z |

The server does not flag `05-3`'s partial video take (F-034).

## Findings

Repeats: F-032, seen as the "Device event" flag (now F-036). F-018 was withdrawn and replaced by
F-038.

### F-035: The web session detail shows "input gain undefined %"

- **Severity:** minor
- **Area:** web-ui
- **Run / step:** run 06, step 5
- **Expected:** `upload_data.md` marks `micUses[].inputGainPercent` as optional. When it's absent,
  the UI shows nothing or "n/a".
- **Actual:** the AUDIO tile shows "input gain undefined %" for every session.
  `frontend/src/pages/SessionDetailPage.jsx:178` interpolates the value whenever `micUses` is
  non-empty. The app doesn't send the field on this device (its gain can't be set, F-017).
- **Repro:** open `#/admin/uploads/60cf6e0af3ed40c599d15d65e5765b51` and look at the AUDIO tile.
- **Evidence:** `screenshots/F-06-1-input-gain-undefined-and-raw-keys.png`
- **Status:** open

### F-036: The camera tile shows raw ffmpeg output as a "CaptureInterrupted" warning, even for a normal close

- **Severity:** minor
- **Area:** integration
- **Run / step:** run 06, steps 4 and 5
- **Expected:** a normal camera close doesn't look like a device failure, and event details are
  readable.
- **Actual:**
  - All three video sessions carry the "Device event" flag.
  - `05-1`'s CAMERA DEVICE tile shows a warning followed by about 15 lines of ffmpeg progress
    output ("frame= 2539 fps= 30 …", "[q] command received. Exiting."). This is the Kotlin
    `toString` of `CaptureInterrupted`.
  - This is how F-032 surfaces on the server. The app also sends the event detail unbounded.
- **Repro:** upload a run 05 session, then open its detail page.
- **Evidence:** `screenshots/F-06-2-raw-ffmpeg-text-camera.png`, `step-4-uploads-filtered-E2E.png`
- **Notes:** related to F-032.
- **Status:** app side fixed 2026-10-24 (`8aaa7c1`): a real failure's detail is now one readable line, with progress lines skipped, paths replaced by `<path>`, the folder name replaced by `<session>`, and at most 300 characters. A normal close no longer produces the event at all (F-032). Still open for the web: how the camera tile shows the detail. Tests: `FfmpegSessionVideoRecorderTest` "an unexpected end reports a short detail without progress lines", `ErrorTextTest`, `SessionComponentTest` "a camera failure detail never carries the session folder name".

### F-037: The admin UI can't play the video clips (MJPEG in MP4)

- **Severity:** minor
- **Area:** integration
- **Run / step:** run 06, step 6
- **Expected:** the web spec says clips are played from the server copy on the detail page.
- **Actual:** the MP4 decodes with ffmpeg. In the browser, the "Play video clip" button's
  `<video>` fails with `MEDIA_ERR_SRC_NOT_SUPPORTED` (code 4) in Chromium and Edge: browsers don't
  decode MJPEG in MP4. The format follows `upload_data.md` §4.3, so this is a design gap, not an
  app bug.
- **Repro:** open `#/admin/uploads/c54d0d83b29245cbbc08c44ae830a633` and play task 0's video.
- **Evidence:** `screenshots/F-06-3-video-clip-not-playable.png` (Edge), `step-6-video-playback.png`
  (Chromium)
- **Notes:** download-and-play-locally works. The MP4 has no audio track; the audio is the
  separate WAV.
- **Status:** open

### F-038: Config fetch, device check-in and upload write no log lines

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 06, steps 2 and 7 (also runs 02 and 03)
- **Expected:** `server-upload-integration.md` §5 says the check-in "should be logged without
  the token or the installation ID". Upload attempts and config refreshes are worth logging too.
- **Actual:**
  - `app/data/logs/app.log` works, but its first line is the 00:07:02 "microphone opened" (run
    03), and its last is 00:30:01 (run 05 remux).
  - Nothing is logged for the config fetches and check-ins in run 02 (23:58, 00:04), or for the
    upload batch and check-in at 00:34.
  - The stdout log has no lines for them either.
  - The check-in can only be inferred from the server's last-seen time.
- **Repro:** set a token or upload a session, then read `app.log`.
- **Evidence:** `logs/app-log-tail.txt`. The orchestrator checked: `app.log` was created at
  23:58:49, and the date rollover at 00:07 produced an empty `app.2026-10-23.0.log.gz`
  (20 bytes).
- **Notes:** this replaces F-018. F-018's "app.log stays empty" was caused by the same thing: until
  run 03 the app had logged nothing.
- **Status:** fixed 2026-10-24 (`7b52d28`), not yet re-run. Cause: the network APIs logged failures only, and the use cases logged nothing. Fix: INFO/WARN lines for the config refresh (outcome, configVersion, site), the config fetch and device check-in (HTTP status, outcome), each upload attempt (session id, attempt, outcome, retry decision; retry is manual, there is no automatic retry) and each upload batch. The token and the installation ID are never logged. `server-upload-integration.md` §5 lists what is logged. Tests: `NetworkLoggingTest` (6 tests, 5 shown to fail without the fix).

### F-039: The participant-masking check can't run, because no test session has a hidden field

- **Severity:** question
- **Area:** docs
- **Run / step:** run 06, step 5
- **Expected:** plan step 5: "participant data is masked until Reveal".
- **Actual:**
  - The patient code is always visible, by design; the panel says so.
  - Runs 03–05 left the optional `year_of_birth` empty, so there's nothing to mask. "Show values"
    is disabled.
  - The Reveal flow and its audit entry are unverified.
- **Repro:** open any of the five details.
- **Evidence:** `screenshots/step-5-detail-03-1-masked.png`
- **Notes:** run 07 enters a `year_of_birth`. The plan should say "fields other than the patient
  code".
- **Status:** open

### F-040: The questionnaire summary shows internal keys instead of question and answer text

- **Severity:** question
- **Area:** web-ui
- **Run / step:** run 06, step 5
- **Expected:** a reviewer reads the questions and the chosen options. The ZIP's
  `task_configuration_snapshot.json` contains the strings.
- **Actual:** "Show answers (4)" lists `p13_t5_q1`…`q4` as the questions. Free-text answers show
  as typed, and the choice answers show as option keys (`p13_t5_q3_o2`).
- **Repro:** open the `03-1` detail and press "Show answers (4)".
- **Evidence:** `screenshots/F-06-1-input-gain-undefined-and-raw-keys.png` (Tasks panel)
- **Notes:** the web spec is silent.
- **Status:** open

## Context updates

- `sessions[]`: all five have `uploaded: true` and a `server` block (upload ID, site 12, valid,
  bytes, receivedAt, 200, receipt, `downloadedSha256Matches`). `04-1` has a note about the
  re-send.
- `device.afterUpload`: last seen 22:34:30Z, last upload 22:34:29Z, pending 0, failed 0, 5 uploads,
  Healthy.
- `run06`: scratchpad paths and web UI notes.

## Notes for run 07

- **Device registration:** a fresh installation is a new device. Without a token its upload should
  get 404 `device_not_registered` (quarantine). After it's assigned to site A, pressing Upload
  again should return 200 with the original receipt.
- **Masking:** enter a `year_of_birth` so the masking check (F-039) can run.
- **Progress bar:** uploads are fast (133 MB in about 1.5 s), so the bar can't be caught between
  MCP calls.
- **Web routes:**
  - Uploads list: `#/admin/uploads` (`?q=E2E&hasVideo=1`).
  - Session detail: `#/admin/uploads/<sessionId>`.
  - Devices: `#/admin/uploads/devices`.
  - The API search parameter is `q`.
  - Downloads take a bearer token or a ticket (`POST /api/uploads/ticket`).
