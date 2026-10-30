# Run 07: No token, quarantine and computer assignment

- Date / time: 2026-10-28, about 19:35 to 20:00 local (new web build, server `http://192.168.122.183:10001`). Runner: Sonnet. App commit: 7cd7e47
- Preconditions met: yes. Run 06 passed, the app was stopped, and the server and the audio source worked (wmplayer into CABLE Input, mean about -19 dB).
- Result: **passed with findings** (F-07-1 = F-113, F-07-2 = F-114, F-07-3 = F-115, F-07-4 = F-116). One deviation: token B and token A were not typed in Settings (see step 7 and F-116).
- Saved by the orchestrator from the runner's returned text; finding IDs renumbered by the orchestrator.

## Where things are at the end of the run
| Item | State |
|---|---|
| App | **Stopped.** Window gone, `main.pid` deleted, no ffmpeg. `app/data/app.lock` is a leftover file, as after earlier runs. |
| New installation | `app/data/`, which run 08 uses. Server computer id 21. |
| Old installation | `app/data.e2e-1026-run06/` (run 02 computer, server id 1, with the five uploaded sessions). **Not** moved back. |
| Token in the new `app/data` | **Token A** (`<token:E2E 1026 Clinic A>`, in `config/settings.json`). Last start: config fetch 200, check-in 200, "config refresh: unchanged". |
| Mic in the new settings | `CABLE Output (VB-Audio Virtual Cable)`, language en. |
| New session | `E2E1026-07-1`, sessionId `41ac1ef7821d4ceeba1c33eb257af68d`. Processed and Uploaded, ZIP 8 879 935 bytes, SHA-256 `05dada55641dfa31931548a3d28f321467de8413eaf305316c4088d13fbd0099`. |

## Steps
| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Stop the app, move `app/data` to `app/data.e2e-1026-run06`, start fresh | pass | New installation ID. Start took about 25 s to "Waiting for changes". Window resized to 1000x820. |
| 2 | Restore `config/` only, no token | pass | Copied before the first start: `config/config.json` (16 969 bytes) and `config/example_audio/` (2 WAV files). **Not copied:** `settings.json`, `logs/`, `sessions/`. Start-up log: `config refresh: rejected, SiteTokenMissing`, no network call. Settings showed "No site token is set...". Mic `CABLE Output` chosen in Settings and saved. Main menu showed "Site: E2E 1026 Clinic A" with no token (from the cached config). The protocol picker listed both studies, so the protocol started without a token; no fallback needed. |
| 3 | Short speech session `E2E1026-07-1` | pass | Patient code `E2E1026-07-1`, visit `V01`. Phonation x2, PATAKA and reading recorded, monologue skipped, questionnaire answered. "Session processed (4 clips) - ready to upload". |
| 4 | Upload (no token), 📷 | pass (F-114, F-115, seen-again list) | App: "Upload failed: this computer is not registered to a site; contact your administrator". The row stays listed and Upload stays enabled (retryable). Log: `upload rejected ... HTTP 404`, `attempt=1 outcome=Rejected(DeviceNotRegistered) retry=manual`, `device check-in: skipped, no site token`. Server: one attempt `code 404, outcome quarantined`, bytes 8 879 935, hash matching. Recording `status invalid`, `siteId null`. `GET /devices`: new computer id 21, `siteId null`, health `unassigned` "Needs assignment", `uploads30 0`, `neverUploaded true`; stats `unassigned: 1`. Old computer id 1 stayed Healthy in Clinic A. New web: detail page has a "Quarantined" badge, a banner, the **Assign computer...** button and the red check "Computer registered: no site for this device". Recordings list: clinic "No clinic", "Invalid / Device not registered", banner "1 computer is not assigned to a clinic... Review computers". The Computers tab of Clinic A does not list the new computer (expected). |
| 5 | Assign the new computer to Clinic A as master, 📷 | pass | Two discoverable paths: (a) recording detail "Assign computer..." (`recording-assign`) → dialog "Assign computer to a clinic" (`assign-clinic`, `assign-confirm`), toast "Computer assigned. 1 recording(s) released for checking."; (b) list banner "Review computers" → `#/clinics#unassigned-computers`, table "Unassigned computers (1)" with "Assign to clinic..." (`assign-21`). Used (a). The dialog does not name the computer being assigned. Right after assigning, before any app retry: recording `valid`, `siteId 4`, `receivedAt` unchanged; computer 21 in Clinic A, `unassigned 0`. |
| 6 | App Upload again; ZIP hash compare | pass (F-113) | HTTP 200, "Files successfully uploaded". Sessions: "Processed Uploaded". Log: `upload ok ... HTTP 200`, `attempt=2 outcome=Success`. Server: `valid` under Clinic A, **`receivedAt 2026-10-28T18:44:45.571Z` (the original receipt)**. Attempts newest first: id 9 `200 resent` (18:47:05) and id 8 `404 quarantined` (18:44:45); UI "HTTP 200 Re-sent (answered as success)", "HTTP 404 Held in quarantine". Flags "Skipped tasks", **"Re-sent"** (F-113). Downloaded ZIP SHA-256 **equals** the local `Get-FileHash` (`logs/zip-compare.txt`). Clinic A Computers tab now lists two computers, both Healthy, pending 0. |
| 7 | Token B, then token A | pass with deviation (F-116) | Token B written by `set_token.py` with the app stopped, then app started. Log: `config fetch: HTTP 200`, `device check-in: this computer is registered to another site (HTTP 409)`, `config from another site not applied (HTTP 409), keeping previous configuration`. Settings shows "The new configuration was not applied: this computer is registered to another site. Contact your administrator." (📷 `step-7-settings-other-site-note.png`, redacted). Manual "Refresh configuration" gave the same; Main menu stayed "Site: E2E 1026 Clinic A". Server: computer 21 **stays in Clinic A**. Token A then set the same way, app restarted: check-in 200, `config refresh: unchanged`; the note is gone (📷 `step-7-settings-token-a-note-gone.png`). Computer 21 `lastConfigVersion 2026-10-26.172809`, pending 0, failed 0. |

## Findings

### F-07-1: A recording released from quarantine and re-uploaded is flagged "Re-sent" (duplicate)
Index ID: **F-113**
- **Severity:** question
- **Area:** web-ui / server-api
- **Run / step:** run 07, step 6
- **Expected:** `server-upload-integration.md` §3 says a retry after quarantine "returns 200 with the original receipt". A normal retry should not read as a duplicate or something to review.
- **Actual:** the second attempt is recorded as `outcome: resent`, and the recording gets `issues: ["skipped","duplicate"]`; the detail page and the list show a "Re-sent" flag. Run 06 saw the same flag after a deliberate API re-send (05-2), so the flag cannot tell a harmless retry after quarantine from a real duplicate.
- **Repro:** upload with no token (404), assign the computer, press Upload again.
- **Evidence:** `screenshots/F-07-1-resent-flag-after-quarantine-retry.png`, `logs/state-3-after-reupload.txt`.
- **Notes:** the spec does not say whether the flag is intended here.
- **Status:** open

### F-07-2: A quarantined recording is shown as "Invalid" and counted in the Invalid KPI
Index ID: **F-114**
- **Severity:** question
- **Area:** web-ui
- **Run / step:** run 07, step 4
- **Expected:** quarantine is recoverable and has its own state ("Quarantined" badge on the detail page).
- **Actual:** in the list the server check is "Invalid / Device not registered" and the KPI tile "Invalid 1" counts it. `GET /uploads/<id>` says `status: invalid` and `quarantined: null`; the detail page says "Quarantined".
- **Evidence:** `screenshots/F-07-2-quarantined-counted-invalid.png`, `logs/state-1-after-quarantine.txt`.
- **Notes:** related F-045 (the computer-shows-Healthy part is fixed: "Needs assignment").
- **Status:** open

### F-07-3: The quarantine banner on the recording detail has no separator
Index ID: **F-115**
- **Severity:** cosmetic
- **Area:** web-ui
- **Run / step:** run 07, step 4
- **Expected:** "Quarantined: this computer isn't assigned to a clinic. Its recordings are kept..."
- **Actual:** the bold lead and the sentence run together: "...assigned to a clinic Its recordings are kept in quarantine."
- **Evidence:** `screenshots/F-07-3-quarantine-banner-no-separator.png`.
- **Notes:** same pattern as F-111.
- **Status:** fixed (2026-10-30), deployed; not verifiable live in run 15 (no recording in quarantine). Fix (web repo `task_protocoller_web_app`, uncommitted): the banner title is a block of its own (`recd-banner-lead`, `display: block`) above the sentence, like the F-111 notice. Test: `Recording.test.jsx` "F-115 the title is its own block above the body text" (red before).

### F-07-4: Synthetic keyboard input does not reach the app window in this RDP session, so a token cannot be typed or pasted
Index ID: **F-116**
- **Severity:** question (environment)
- **Area:** environment
- **Run / step:** run 07, step 7
- **Expected:** the runner enters the token in Settings and the refresh and check-in run.
- **Actual:** `SendKeys`, `keybd_event` Ctrl+V (clipboard held the token) and a long-press on the token field did nothing; the MCP `type_text` would have put the secret into a tool call, and reading the secret into the output was refused by the permission classifier. So the token was written into `settings.json` by script with the app stopped, and the app's start-up refresh and check-in were the trigger. The "type token and save in Settings" path is untested this run; "Refresh configuration" was pressed manually with token B and gave the same result as start-up.
- **Evidence:** `scripts/run-07/set_token.py`, `scripts/run-07/paste_token.ps1`, `logs/app.log`.
- **Notes:** for run 08 and later, tokens need the same route (or the user types them).
- **Status:** closed, environment (2026-10-29). Worked around with `scripts/run-07/set_token.py` (app stopped) and `type_text`.

### Seen again (no new finding)
- F-046: progress bar full green after the failed upload (`screenshots/F-07-seen-again-F-046-F-064.png`).
- F-064: "1 sessions ready to upload" (same screenshot).
- F-089: the "No site token" message is left-aligned.
- F-043 (empty site and broken header of a quarantined recording): **fixed in the new web** (badge, banner, Assign computer button).
- F-045: **fixed in the new web** (computer shows "Needs assignment", `uploads30 0`).
- F-041 and F-042: app side fix **confirmed** (config from the other site is not applied, the note shows).
- F-087: Computers tab still shows no config version (`GET /devices` has `lastConfigVersion`).

## Context updates
`reports/2026-10-26-context.json` (UTF-8 no BOM): the run-07 session was added (patient, sessionId, local ZIP path and SHA-256, 404 then 200 "resent", `receivedAt` kept, `serverClinicId 4`, uploaded, valid). `device2` describes the new installation (server id 21, Clinic A, data dir `app/data`, token A, last check-in, config version). The old `device` entry notes that its data lives in `app/data.e2e-1026-run06`.

## Notes for later runs (08 and 09)
- Use the **new `app/data`** (token A, mic `CABLE Output`, config cached, one uploaded session `E2E1026-07-1`). Nothing to upload; run 08 must record its own sessions. The old data and its five sessions are in `app/data.e2e-1026-run06/`.
- Do not use `E2E1026-07-1` as a clean example for the "Re-sent" count (F-113). Server computers: id 1 (old) and id 21 (new), both Clinic A, Healthy.
- To change the token: stop the app, `scripts/run-07/set_token.py <secrets key|none>` (e.g. `E2E-1026-Clinic-B`), start the app. Typing in Settings does not work here (F-116). `. scripts/run-07/env.sh` sets up Git Bash for the run-07 scripts.
- Stop procedure: `taskkill /PID <powershell running start-app.ps1> /T /F`, then `Stop-Process` the pid in `main.pid` (a properties file; use the `pid=` value), then delete `main.pid`.
- The Settings semantic tree prints the token in `editableText`; scroll the container first and redact screenshots with `run-02/redact.ps1`.
- Web test ids: `recordings-unassigned-banner`, `recording-assign`, `assign-clinic`, `assign-confirm`, `assign-<computerId>`; "Unassigned computers" is on `#/clinics#unassigned-computers`. After assigning, the detail page shows "Validating" for a few seconds with a stale red "Computer registered" check, then settles to Valid.
- Leftover Gradle and hot-reload JVMs may still be running; they did not disturb new starts.
- Run 08.3 (firewall): the computer is now assigned, so a firewall test needs a new session.

## Scripts and logs
Scripts (`docs/testing/e2e/scripts/run-07/`): `README.md`, `env.sh`, `common07.py`, `server_state.py`, `ui_explore.py`, `ui_review_computers.py`, `ui_assign.py`, `ui_after_assign.py`, `zip_compare.py`, `set_token.py`, `paste_token.ps1`; run-07 paragraph added to `scripts/README.md`. Reused: `run-04/start-app.ps1`, `run-02/front.ps1`, `run-02/redact.ps1`, `run-06/common06.py`, `common/ui.py`, `common/common.py`.
Logs (`logs/`, redacted and checked for the two clinic tokens and the installation ID prefix): `app.log`, `state-0-before.txt` to `state-5-final.txt`, `zip-compare.txt`, `ui-step-*.txt`. `hotrun.log`, `hotrun-2.log`, `hotrun-3.log` in the run folder.
Screenshots (`screenshots/`): `step-4-upload-failed-not-registered`, `step-4-recording-detail-quarantined`, `step-4-recordings-list`, `step-4-computers-clinic-a`, `step-5-review-computers-landing`, `step-5-dialog`, `step-5-clinic-chosen`, `step-5-after`, `step-6-upload-success`, `step-6-sessions-uploaded`, `step-6-detail`, `step-6-list`, `step-6-computers`, `step-6-clinics`, `step-7-settings-other-site-note` (redacted), `step-7-settings-token-a-note-gone` (redacted), `step-7-computers`, `step-7-clinics`, `step-7-detail`, `step-7-list`, `F-07-1-...`, `F-07-2-...`, `F-07-3-...`, `F-07-seen-again-F-046-F-064`.
