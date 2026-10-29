# Run 06: Upload and the server record

- Date / time: 2026-10-28, 19:23 to 19:40 local (sessions were recorded 2026-10-26). Runner: Sonnet. App commit: 7cd7e47 (working tree with uncommitted E2E docs)
- Preconditions met: yes. Five processed, not-uploaded sessions; token A; ZIP hashes equal to the context file.
- Result: **passed with findings** (F-06-1 = F-110, F-06-2 = F-111, F-06-3 = F-112; F-037 and F-109 seen again). The upload itself works end to end. The A2 step 10.8 rerun passed.
- App state at the end: **stopped** (hotRun task killed, java PID from `main.pid` gone, `main.pid` deleted; `app.lock` is still in `app/data` as before). Playwright browsers closed. Nothing edited under `app/`, `shared/`, `updater/` or the web repo; nothing committed; no server record deleted.
- Note: the server records are in the state "5 valid recordings under E2E 1026 Clinic A". The re-send and 409 test added 2 attempts to session `7f82ec98...` (`resent` 200 and `conflict` 409), and the record flag `duplicate` ("Re-sent" chip) is now on that recording.
- Saved by the orchestrator from the runner's returned text; finding IDs renumbered by the orchestrator (F-06-n → F-110..F-112).

## Local ZIPs (SHA-256 taken with `sha256sum` right before the upload; unchanged after it)
| Session | sessionId | ZIP | Local SHA-256 | SHA-256 of the ZIP downloaded from the server |
|---|---|---|---|---|
| E2E1026-03-1 | 6c08b80c07704a4d990c207fab344e7b | E2E1026-03-1_6c08b80c...zip (13,712,393 B) | 73a64e3aa666e155ba1887ab1b8eb404942b02623ea3bd8cad6c1903e21c9660 | same (EQUAL) |
| E2E1026-04-1 | 11e79754eb664c368bd7bc52412477db | E2E1026-04-1_11e79754...zip (13,205,891 B) | 738080c5edb0792e29e70a01570b2bcbdb84931145d5c6f26f44e0027e698a7f | same (EQUAL) |
| E2E1026-05-1 | f1b25e4ecd424436ac6753a43ccf09a2 | E2E1026-05-1_f1b25e4e...zip (152,458,476 B) | 3b16f25626ce201f19043b1afd5c4cf9323c082ad54597ab8a156ff409c9413c | same (EQUAL) |
| E2E1026-05-2 | 7f82ec98b70a45408dc0697920b3d8ea | E2E1026-05-2_7f82ec98...zip (7,267,489 B) | e3767edb2b5171af1d959022852a65e7503a55b4cccdc98619d2901651197fde | same (EQUAL) |
| E2E1026-05-3 | d3b229fad6b442b9b171b12cde97fac3 | E2E1026-05-3_d3b229fa...zip (38,663,316 B) | 1560bf6dbc8495e32e77e3315690e6beb18d3c693a0f2ebc5363003c6f20b816 | same (EQUAL) |

The server-side `zipSha256` in each detail also equals the local hash. Every WAV and MP4 clip downloaded from the server has the same SHA-256 as the entry in the local ZIP.

## Steps
| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Main -> Upload (📷 `step-1-upload-screen.png`) | pass | "5 sessions ready to upload"; E2E1026-03-1, 04-1, 05-1, 05-2, 05-3, each listed once, all "Not uploaded", dates 26. 10. 2026. |
| 2 | Press Upload (📷 `step-2-during-upload.png`, `step-2-during-upload-b.png`) | pass, progress bar not observed mid-way | The whole batch (about 212 MB) took about 3 s on the LAN (app log: 19:24:31 to 19:24:34, "upload batch: 5 session(s), 5 ok, 0 failed", all HTTP 200, attempt=1). The first screenshot right after the click shows Upload disabled and the bar at 0; the next, 8 s later, shows the bar full, green "Files successfully uploaded.", "No sessions are ready to upload." and an empty list. The advance of the bar could not be caught, too fast. No error message. Log: `logs/app-upload.log` (redacted). Device check-in "ok" before and after the batch. |
| 3 | Sessions (📷 `step-3-sessions-uploaded.png`) | pass | All five show "Processed  Uploaded"; "Open editor" is disabled. Reprocess stays enabled on uploaded rows; the spec (§8.9, 8.11: "reprocessing an already-uploaded session regenerates local artifacts, but re-upload is blocked") allows that, so no finding. The Upload screen lists none of them and has no per-row upload action. |
| 4 | Recordings list, search `E2E` (📷 `step-4-list-filter-E2E.png`) | pass | 5 of 5 under E2E 1026 Clinic A; Speech protocol for 03-1 and 04-1, Video protocol for 05-1/2/3; patient codes right; recorded date (detail header "recorded on Oct 26, 2026", times 18:48, 18:56, 19:07, 19:34, 19:09 match the app) right; received 2026-10-28 19:24 UTC+1 (07:24 PM in the UI). Status **valid for all five** (KPIs valid 5 / invalid 0 / attention 0 / unreviewed 5), no "validating" and no invalid reason. First read was about 3 min after the upload, so the validating-to-valid transition itself was not observed; the status was never validating at any read. `hasVideo` (API `hasVideo=1` and the "Has video 2" chip, 📷 `step-4-filter-has-video.png`) returns exactly 05-1 and 05-3. 05-2 (video protocol, no camera) is correctly not a hasVideo row. Flags shown: 03-1 Skipped tasks + Retakes; 04-1 Retakes + Edited timeline; 05-1 Retakes; 05-3 Computer event (📷 `step-4-filter-computer-event.png`); 05-2 none. |
| 5 | Detail pages (📷 `step-5-detail-03-1*.png`, `step-5-detail-05-1.png`, `-05-2`, `-05-3`, `-04-1`) | pass with findings | **03-1:** Participant shows Patient code E2E1026-03-1; Visit code and Year of birth opt are `••••••` (`masked-visit_code`, `masked-year_of_birth_opt`). "Show values" gives V01 and 1975 (= context values), "Hide values" masks them again (📷 `step-5-detail-03-1-values-shown.png`, `-hidden-again.png`). Same via API `POST /reveal` for 03-1 and 04-1 (V01/1975 and V01/1980: MATCH). Exam summary: 6 tasks, "5 of 6 recorded", Monologue "Skipped", Phonation 2 takes "last kept", Reading, Pataka; questionnaire "Show answers (4)" modal matches q1..q4 of the context file (📷 `step-5-detail-03-1-answers.png`). Timeline block present with the rejected take. Server checks: all 7 ok (ZIP checksum, archive, manifest, IDs, computer registered, config version, session complete). **Upload history: exactly one entry, "HTTP 200 Accepted · 13.7 MB"** (API `/attempts`: one row, code 200, outcome accepted, `shaDeclared` = `shaComputed`). **04-1:** timeline used "edited", flag "Edited timeline", 5 audio clips, YoB 1980 revealed. **05-1:** 3 MP4 buttons (phonation, monologue rep 1, rep 2), none for Pataka, camera OBS Virtual Camera 1280x720 30 fps. **05-3 (partial MP4):** see F-110. **05-2 (no camera at start):** server shows a normal audio-only recording, no flag, no camera block; see F-109. Seen again: F-037 (video clips can't play in the browser, now with a "This clip can't play in the browser. Download to play." notice; WAV plays: audio element readyState 4, duration 9.6 s). F-112 (label). Activity and Upload history are collapsed by default (not a finding). Deep link and reload on a detail URL work. No console errors, no 4xx/5xx from the UI. |
| 6 | Downloads (log `logs/downloads.txt`) | pass | ZIP of all five: HTTP 200, `Content-Disposition: attachment; filename="<sessionId>.zip"`, SHA-256 equals the local one (table above). WAV (task 0 of every session): HTTP 200, SHA equals the entry in the local ZIP, ffprobe pcm_s16le (e.g. 03-1 9.60 s). MP4 (05-1 x3, 05-3 x1): HTTP 200, SHA equals the entry in the ZIP; ffprobe: codec **mjpeg**, 1280x720, durations 13.63 s, 19.53 s, 17.90 s (05-1) and 10.90 s (05-3; its WAV is 24.45 s). ffprobe reads them, but MJPEG in MP4 does not play in browsers (F-037). |
| 7 | Computers (📷 `step-7-computers-page.png`, `logs/devices.json`) | pass | Clinic A `#/clinics/4/computers`: one computer, Healthy, app 1.0.0, last seen "10 minutes ago", Waiting to upload 0. `GET /devices`: pending 0, failed 0, `lastSeenAt` 18:24:34Z (check-in right after the batch), `lastUpload` 18:24:34Z, `uploads30` 5, `lastConfigVersion` 2026-10-26.172809. Config version not shown in the tab: F-087 (seen again). Installation ID shown in full: F-102 (seen again; masked in the screenshot via the DOM). |
| 8 | Re-send idempotency and 409 (log `logs/resend.txt`) | pass | `curl` multipart in the order installationId, sessionId, zipSha256, siteToken, appVersion, zip (token via stdin, installation ID from `settings.json`, neither printed). (a) Re-post of the 05-2 ZIP with its own id and hash: **HTTP 200**, body `{"status":"ok","sessionId":"7f82ec98...","bytes":7267489,"sha256":"e3767edb...","receivedAt":"2026-10-28T18:24:33.904Z"}`; `receivedAt` is the original one. (b) ZIP of 03-1 (its own correct hash) posted under 05-2's sessionId: **HTTP 409 `{"error":"session_exists"}`**. Afterwards the record is unchanged (valid, 7,267,489 B, original SHA); attempts: 200 accepted, 200 resent, 409 conflict; the UI shows "HTTP 409 Conflict · 13.7 MB", "HTTP 200 Re-sent (answered as success)", "HTTP 200 Accepted" (📷 `step-8-resend-detail-05-2.png`) and a duplicate flag. |
| extra | Activity / audit (F-044) | pass: F-044 fixed in the web | 03-1 "Activity · 2 events": "System Master revealed participant fields" and "System Master downloaded the ZIP"; every other session "downloaded the ZIP" (📷 `step-5-activity-03-1.png`, etc.). Clip downloads are not listed, as the spec says (§9: "playback is not a download"). |
| extra | Run A2 step 10.8 rerun (`logs/scope-data.txt`, 📷 `step-A2-10.8-editor-recordings.png`, `-editor-deeplink.png`) | **pass** | Master sees 5. **editor** (not assigned to clinic A): list 0 of 5; detail, attempts, activity, comments, zip, clip and bulk download all **404**. **received** (assigned to clinic A): lists 5; detail, attempts, activity, comments, zip, clip all **200**. nogrants and creator: list 0, everything 404. UI as editor: empty Recordings list; deep link shows "Not found. This recording does not exist or is outside your access." Bulk was not run for received (it would stream a bundle). |

## Findings

### F-06-1: Server record does not show that a video is shorter than the audio (videoComplete:false is ignored)
Index ID: **F-110**
- **Severity:** minor (question whether the spec wants it shown)
- **Area:** web-ui / server-api
- **Run / step:** run 06, step 5
- **Expected:** `docs/dev/upload_data.md` says `videoComplete` is `false` "if the camera failed during the kept take (MP4 shorter than the audio; the take is still kept)" and "the server may ignore it". An analyst opening the record should be able to see that the clip is partial.
- **Actual:** 05-3's ZIP has the partial MP4 (10.90 s vs the 24.45 s WAV) and `videoComplete:false`; the web record shows status Valid, "Tasks 4 of 4 recorded", the flag "Computer event" and, under Camera, "The camera could not capture video." with Details "capture interrupted: the capture process ended". `videoComplete` is not in `GET /uploads/:id` at all, and no clip duration is shown, so nothing says that the Phonation video stops after 10.9 s. 05-2 (camera missing at start) has no flag and no camera information (see F-109).
- **Repro:** 1. Upload `d3b229fa...` (05-3). 2. Recordings -> E2E1026-05-3. 3. Look at Tasks and Session -> Camera.
- **Evidence:** `screenshots/F-06-1-partial-video-not-flagged.png`; `logs/api-check.json`, `logs/downloads.txt` (ffprobe durations).
- **Notes:** related F-109. The "Computer event" chip is the only hint.
- **Status:** fixed in the web repo (2026-10-29), not yet deployed or re-run. Cause: `sessionExtractor` built `exam_summary.tasks[]` from a fixed field list without `videoComplete`, `videoDurationMs` and `audioDurationMs`. Fix (web repo `task_protocoller_web_app` commit `44d8490`): the fields are kept; `TaskTable` shows a warning badge "Video partial · 0:11 of 0:24" (badge only, no list flag); upload spec updated. Existing records keep their old summary (no backfill). Tests: `sessionExtractor.test.js` "F-110: carries videoComplete ...", `Recording.test.jsx` badge cases (red before).

### F-06-2: Camera text on the detail page runs together ("OBS Virtual CameraThe camera could not capture video.")
Index ID: **F-111**
- **Severity:** cosmetic
- **Area:** web-ui
- **Run / step:** run 06, step 5 (05-3)
- **Expected:** the camera name and the failure notice are separate lines or have a separator.
- **Actual:** the Session card shows "OBS Virtual CameraThe camera could not capture video." with the notice in a smaller font directly after the name; the DOM text has no space or newline between them.
- **Repro:** open the 05-3 detail page (any recording with a camera event).
- **Evidence:** `screenshots/F-06-2-camera-text-run-together.png` (also in `step-5-detail-05-3.png`).
- **Status:** fixed in the web repo (2026-10-29), not yet deployed or re-run. Fix (web repo `task_protocoller_web_app` commit `44d8490`): the device notice is a block element (`DeviceEvents` uses a `div`, `.recd-event` is `display: block`) under the device name. Test: `Recording.test.jsx` "F-111: the notice is a block of its own ..." (red before).

### F-06-3: Participant field label shown as "Year of birth opt" (the raw field key, humanized)
Index ID: **F-112**
- **Severity:** cosmetic
- **Area:** web-ui
- **Run / step:** run 06, step 5 (03-1, after Show values)
- **Expected:** a readable label for the patient field (the protocol's field label, e.g. "Year of birth", in the viewer's language).
- **Actual:** the card shows "Year of birth opt" (key `year_of_birth_opt` with underscores replaced and the first letter capitalized); "Visit code" is right.
- **Repro:** 03-1 detail -> Participant -> Show values.
- **Evidence:** `screenshots/F-06-3-year-of-birth-opt-label.png`.
- **Notes:** related F-076 (custom patient field label has no per-language entry); may be the same root cause (the web does not use the config's field labels).
- **Status:** open

### Seen again (not new)
- F-037: MJPEG-in-MP4 clips can't play in the admin UI (`screenshots/step-6-play-video-05-3.png`, now with a "Download to play" notice and a "Download clip" link).
- F-087: Computers tab shows no config version (`screenshots/step-7-computers-page.png`).
- F-102: Computers tab shows full installation IDs (masked in the screenshots).
- F-109: no signal anywhere that the camera was missing at start (05-2 shows as an ordinary audio recording).
- F-044: **fixed in the web** (reveals and ZIP downloads appear under Activity).
- Script defect in run A2 (not a product finding): `run-A2/scope_data.py` reads `x['sessionId']` (rows have `id`) and `api_scope.py` posts `{"ids":[..]}` (the API wants `sessionIds`, so it answered 400 instead of 404). A fixed copy is `run-06/scope_data.py`.

## Context updates
`reports/2026-10-26-context.json` (UTF-8, no BOM, kept): the sessions list is de-duplicated (the run-04 session was listed twice); every session now has `uploaded: true`, `status: uploaded`, `serverStatus: valid`, `uploadedBy`; `device.lastCheckin`, `pending: 0`, `failed: 0`; new `run06` block (the re-send of 05-2 and the 409 conflict attempt, a note that the ZIPs remain under `app/data/sessions/*/archive`).

## Notes for later runs (07 to 09)
- All five sessions are uploaded to Clinic A and `valid`; the app lists nothing to upload now. Run 07 (quarantine) needs a fresh installation as planned; the existing device is registered at Clinic A (server device id 1). The local ZIPs are still on disk.
- Web recordings routes: `#/recordings` and `#/recordings/<sessionId>`; test ids: `recordings-search`, `recordings-more-filters` (flag chips "Has video", "Computer event", "Re-sent" appear after clicking it), `recordings-table`, `task-row`, `recording-activity`, `masked-<field>`, buttons "Show values" / "Hide values" / "Show answers". Activity and Upload history are `<details>` that start closed. API: `GET /uploads?q=&hasVideo=1&pageSize=`, rows have `id` (not `sessionId`), bulk body is `{sessionIds:[...]}`, clip path is `/uploads/:id/clips/<file name without the clips/ prefix>`.
- The 212 MB batch needs about 3 s on this LAN, so the progress bar can't be seen unless the network is throttled or the firewall rule is used (run 08).
- `curl` re-send recipe: `scripts/run-06/resend.py <keyA> <keyB>` (token on stdin, installation ID from `app/data/config/settings.json`). A repeat run adds more attempts to the same recording.
- The 05-2 recording now carries the duplicate flag ("Re-sent"); do not use it as a clean example for the "Re-sent 0" count.
- App stopped; a Gradle daemon may still be running (harmless). Start with `scripts/run-04/start-app.ps1`. Settings still shows the token and installation ID in clear text (F-016).

## Scripts and logs
Scripts (`docs/testing/e2e/scripts/run-06/`): `README.md`, `common06.py`, `api_check.py`, `api_downloads.py`, `devices_check.py`, `reveal_check.py`, `resend.py`, `scope_data.py` (fixed A2 10.8 script), `ui_recordings.py`, `ui_activity.py`, `ui_scope.py`; run-06 paragraph added to `scripts/README.md`. Reused: `run-04/start-app.ps1`, `run-02/front.ps1`, `run-02/computers_page.py`, `common/ui.py`, `common/common.py`, `run-A2/api_scope.py` (unchanged, no-data probe re-run, `logs/scope-nodata.txt`).
Logs (`logs/`, redacted, checked for tokens and installation IDs): `local-sha256.txt`, `app-upload.log`, `api-check.json`, `downloads.txt`, `devices.json`, `resend.txt`, `reveal-check.txt`, `scope-data.txt`, `scope-nodata.txt`, `detail-*.txt`, `ui-recordings.txt`, `ui-activity.txt`, `ui-activity-after-resend.txt`; `hotrun.log` in the run folder. Downloaded files (ZIPs, clips) are in the orchestrator scratchpad, not in the repo.
Screenshots (`screenshots/`): step-1, step-2 (x2), step-3, step-4 (list, has-video, computer-event), step-5 (detail per session, 03-1 answers / values-shown / hidden-again, activity per session), step-6 (play audio/video), step-7-computers-page, step-8-resend-detail-05-2, step-A2-10.8 (editor recordings / deeplink), F-06-1, F-06-2, F-06-3.
