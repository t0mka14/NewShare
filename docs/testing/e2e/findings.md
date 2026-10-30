# E2E findings index

Kept by the orchestrator (see [`test-plan.md`](test-plan.md) §1.1 and §5.3). Each row links to
the full finding in its run report. IDs are never reused; a duplicate seen in a later run is
added to the original row's "Seen in" column rather than getting a new ID.

## Summary

### E2E run of 2026-10-26/28 (new web build, app commit `7cd7e47`)

Runs 00, A, A2 and 02–09 were completed (run 01 is covered by run A); run 10 is this summary.
Every run **passed with findings**. None was blocked. 62 new findings (F-067 to F-128): 6 major, 19 minor,
18 cosmetic, 19 questions.

The whole process works end to end on the new web UI:

- setting up clinics, studies and protocols, the config by clinic token, and the computer check-in,
- speech and video sessions, the editor, processing,
- upload with a valid server record and matching SHA-256 (8 recordings, one attempt each),
- re-sending the same ZIP (200, original receipt) and a conflicting ZIP (409),
- quarantine (404) → assigning the computer in the web → re-upload, and a token from another clinic is not applied,
- offline start with the cached config, a network failure that can be retried, crash recovery of the master WAV.

The redesigned web passed spec criteria §10.1–8, 12 and 16 (run A, A2). The §10.8 recordings
scope passed on the rerun in run 06.

**Majors found in this run:**

1. ~~**F-123 (desktop-app):** a session with a microphone interruption and Resume (a 2-part master)
   can't be processed (`IoFailure`), so it can never be uploaded.~~ Fixed 2026-10-28, confirmed in
   run 12: session `E2E1026-09-2` reprocessed into a ZIP.
2. ~~**F-124, F-125, F-126 (desktop-app):** below the 1000×820 default, Sessions, the editor and
   Upload lose Back or Accept, and nothing scrolls them back.~~ Fixed 2026-10-28, together with
   F-093 and F-118, by a minimum window size of 960×720 and layouts that fit at that size.
   Confirmed in run 12.
3. ~~**F-091 (desktop-app):** the last option of a multiple-choice question is cut off at
   1000×820.~~ Fixed 2026-10-28: a scrollbar now shows. Confirmed in run 12.
4. ~~**F-077 (environment):** persona users can't be given a known password.~~ Closed as worked
   around (2026-10-29): run A2 set the persona passwords in the database over SSH, and the test plan's
   Run A precondition now says so. A web "set password" action remains an optional feature request.

**Recurring themes:**

- **Layout:** fixed heights instead of scrolling (F-091, F-093, F-118–F-127). Fixed in fix round 1, except F-127 and F-130 (cosmetic).
- **Feedback to the examiner:**
  - a silent microphone is not detected (F-117);
  - no feedback after Accept or Reprocess (F-104);
  - camera failure texts overlap (F-106).
- **Web wording and i18n** after the redesign: F-068, F-070, F-080, F-084, F-098, F-101, F-112, F-115.
- **Web: what a recording record shows:**
  - times shifted by the UTC offset (F-067);
  - `videoComplete:false` ignored (F-110);
  - quarantine counted as Invalid and a release counted as Re-sent (F-113, F-114).

**Fixes confirmed in this run:**

- F-019 (camera picker), F-026, F-027, F-060 (runs 02–03);
- F-041, app side (run 07);
- F-048 (run 08);
- F-043, F-044, F-045 (web).

**Not covered:**

- run 08 part 6, the long upload (user decision);
- display scaling;
- the standalone `VIDEO`/`EMOTIONS` task (the web doesn't produce it);
- a microphone unplugged mid-take;
- the audit log;
- the editor and no-grants personas in the browser (F-077).

**Evidence check (run 10):**

- Every finding F-067 to F-128 has a screenshot, or its report gives the evidence instead: a log, an API answer, or source code. These are F-095, F-107, F-108, F-128 and the API-only F-083, F-086, F-100.
- Every index link resolves. Anchors were added to the reports for runs 02–05, A and A2.
- Duplicates are recorded under "Seen in": F-08-2 under F-046; seen again in this run: F-037, F-042, F-046, F-063, F-064, F-087, F-089, F-093, F-102, F-109.
- Reports and scripts were scanned for both clinic tokens and both installation IDs: none found.

**Left behind for the user (nothing deleted):**

- **Server**, all checked through the admin API on 2026-10-28:
  - clinics `E2E 1026 Clinic A` (4) and `E2E 1026 Clinic B` (5), plus the stray clinic 3, named with 64 × `x`;
  - studies `E2E 1026 Speech study` (3), `E2E 1026 Video study` (4) and `E2E 1026 A2 throwaway study` (6), with their protocols;
  - protocol 18 (archived, in Test Study 001);
  - library entry 15;
  - computers 1 and 21;
  - recordings `E2E1026-03-1`, `-04-1`, `-05-1`, `-05-2`, `-05-3`, `-07-1`, `-08-2`, `-08-5`;
  - users `e2e-editor-1026`, `e2e-received-1026`, `e2e-nogrants-1026` and `e2e-creator-1026@test.com` (ids 3–6).

  Delete them in the admin UI when done.
- **Local:**
  - `app/data.e2e-1026-run06` holds the run 02–06 installation.
  - `app/data` holds the run 07–09 installation: token A, sessions 07-1, 08-x and 09-x. 09-1 and 09-3 are processed and not uploaded; 09-2 is the F-123 sample.
  - The user's own pre-E2E data was not kept, so there is nothing to restore.
- **Machine:**
  - mic privacy `Allow`;
  - the firewall rule removed;
  - OBS (virtual camera on) and wmplayer still running.

| Open by area | blocker | major | minor | cosmetic | question | Total |
|---|---|---|---|---|---|---|
| desktop-app | 0 | 0 | 1 | 1 | 4 | 6 |
| web-ui | 0 | 0 | 0 | 1 | 4 | 5 |
| integration | 0 | 0 | 0 | 0 | 3 | 3 |
| server-api | 0 | 0 | 0 | 0 | 1 | 1 |
| environment | 0 | 0 | 0 | 0 | 0 | 0 |
| docs | 0 | 0 | 0 | 0 | 0 | 0 |
| **Total** | 0 | 0 | 1 | 2 | 12 | **15** |

Counts are open findings across both E2E runs. The triage of 2026-10-29
([triage](triage-2026-10-29.md)) closed groups A and B (21). Fix round 4
([spec](fix-round-2026-10-30-group-c-spec.md)) fixed group C (25) on 2026-10-30: runs 15 (web) and
16 (desktop) confirmed 23 live; F-115 (no recording in quarantine) and F-109 (no camera can be hidden
without admin rights) rest on unit tests. The 15 left are group D (12 product questions, waiting for
the user) and three new ones: F-140 (cosmetic, run 15), F-141 (minor, run 16) and F-142 (question,
run 16).
Fixed but not re-run yet: F-055, F-062, F-134, F-135, F-136 (they need a crash, a second microphone or
a real device interruption). F-110 is deployed but its live check needs a partial-video upload. F-009
is confirmed in the served config; the desktop card was not re-checked.

### E2E run of 2026-10-23/24 (app commit `59bb063`)

## Progress of the 2026-10-26 run (new web build on the container, app commit `7cd7e47`)

Completed 2026-10-28. Reports are in `reports/2026-10-26-run-*`, scripts in `scripts/`, findings F-067 to F-136 below.

| Run | State |
|---|---|
| 00 preflight | done |
| A new web UI, A2 personas | done (F-067 to F-102); A2 step 10.8 (recordings scope) rerun in run 06: **pass** (fixed script `scripts/run-06/scope_data.py`) |
| 01 web setup | covered by run A |
| 02, 03, 04 | done |
| 05 video | done, incl. step 7 (F-106..F-109). The OBS virtual camera is still **unregistered**: the user must `regsvr32 /s` the DLL and restart OBS before run 09 |
| 06 upload | done 2026-10-28, passed (F-110..F-112): 5 sessions uploaded, all valid, ZIP SHA-256 equal, 1 attempt each, re-send 200 / conflict 409; F-044 confirmed fixed in the web |
| 07 quarantine | done 2026-10-28, passed (F-113..F-116): quarantine 404, assignment in the web, re-upload 200 with original receipt, SHA-256 equal, other-clinic token not applied. New installation in `app/data` (token A); old data in `app/data.e2e-1026-run06`. Token set by script, not typed (F-116) |
| 08 errors | done 2026-10-28, passed (F-117; F-046 seen again): mic denied message, offline start with cached config and notice, network failure retryable, retry uploads with one server attempt and equal SHA-256, crash recovery with intact master, zero-clip session not uploadable. Part 6 not covered (user decision) |
| 09 layout | done 2026-10-28, passed with findings (F-118..F-128: 4 major, 4 minor, 2 cosmetic, 1 question). Below the 1000x820 default, Sessions, Editor and Upload lose their Back/Accept buttons; a mic interruption + Resume makes a video session unprocessable (F-123). Work area 1512x876 |
| 12 fix re-test | done 2026-10-28, passed with findings (F-130, F-131): F-123, F-124, F-125, F-126, F-093, F-091, F-118 confirmed fixed; new interruption session not covered (could not trigger a device loss) |
| 13 fix re-test | done 2026-10-29, passed (F-133 cosmetic): F-104, F-106, F-122, F-129, F-132 confirmed; real interruption via Audiosrv stop: sampleOffset = event = part-1 frames |
| 14 web re-test | done 2026-10-29, passed (F-137 minor, F-138 cosmetic): web fix round 3 confirmed live (F-009 config, F-067, F-068, F-069, F-073, F-074, F-079, F-086, F-087, F-096, F-097, F-111; F-006, F-035, F-036 redesign); F-110 not covered (needs a partial-video upload) |
| 14b live check | done 2026-10-29, passed: F-137 and F-139 (web `69f956e`) confirmed; dialogs show the draft behind them while open (by design, reverted on Cancel) |
| 10 summary and restore | done 2026-10-28 by the orchestrator: summary above, evidence and links checked, server records listed, test plan 1.2 |


**E2E run of 2026-10-23/24, app commit `59bb063`.** All runs 01–08 completed, with no blockers.
Every run passed with findings. Run 09 (this summary and the data restore) was done by the
orchestrator.

The full process works end to end:

- web setup → config by site token → device check-in,
- speech and video sessions, the editor, processing and reprocessing,
- upload with the right server record and matching SHA-256,
- idempotent re-send and 409 conflict,
- quarantine → assignment → re-upload,
- offline start, network-failure retry, crash recovery of the master WAV.

Most important open defects, all in the desktop app:

1. ~~**F-051 (major):** Back on calibration leaves the microphone open, so every later examination
   fails until the app restarts.~~ Fixed 2026-10-24, confirmed in run 10.
2. ~~**F-001 (major):** a microphone that can't open isn't shown in calibration. Continue then
   puts a raw `IllegalStateException` in front of the examiner.~~ Fixed 2026-10-24, confirmed in run 10.
3. ~~**F-053 (major):** a crash-recovered session processes into a ZIP with no clips and becomes
   uploadable.~~ Fixed 2026-10-24, confirmed in run 10.
4. ~~**F-052 (major):** crash recovery happens silently.~~ Fixed 2026-10-24, confirmed in run 10.
5. ~~**F-005 (major, integration):** the web's default file-name template uses `${siteName}`,
   which the app doesn't fill in.~~ Fixed 2026-10-24, confirmed in run 10.

Recurring themes:

- **Too little feedback to the examiner:** calibration accepts any level, including silence
  (F-021); Skip isn't labelled (F-022); no processing screen (F-026); no offline sign (F-048);
  terse errors (F-049); stale Sessions rows (F-050).
- **Logging only on failure:** F-038.
- **A normal camera close recorded as a failure:** F-032 / F-036.

| Open by area | blocker | major | minor | cosmetic | question | Total |
|---|---|---|---|---|---|---|
| desktop-app | 0 | 0 | 0 | 8 | 0 | 8 |
| integration | 0 | 0 | 3 | 0 | 1 | 4 |
| web-ui | 0 | 0 | 2 | 6 | 4 | 12 |
| environment | 0 | 0 | 0 | 0 | 2 | 2 |
| docs | 0 | 0 | 0 | 0 | 1 | 1 |

**Not covered:**

- run 08 part 6, the long upload (user decision);
- the audio-only fallback when no camera is present at start (F-030: a stopped OBS virtual camera
  still serves frames);
- the standalone `VIDEO`/`EMOTIONS` task, which the web doesn't produce;
- the server audit log for reveals (F-044: no API or UI to view it);
- a microphone unplugged mid-take.

**Evidence check (run 09 step 1):**

- Every finding has a screenshot, or its report says why not: API or filesystem evidence, or a
  screenshot that would expose the token.
- Duplicates are recorded in the "Seen in" column, not given new IDs. The runner's F-08-1 was
  merged into F-001.
- The repo's report files were scanned for both site tokens and both installation IDs: none found.
  Some web-UI screenshots (session detail and upload pages in runs 06–07) still show the run 02–06
  installation ID in the Device field and in clip file names. The two Devices-page screenshots
  were blacked out.

**Left behind for the user:**

- Server: sites `E2E-1023-Site-A` (id 12) and `E2E-1023-Site-B` (id 13); projects
  `E2E 1023 Speech project` (13) and `E2E 1023 Video project` (14); protocols
  `E2E 1023 Speech protocol` (group 13) and `E2E 1023 Video protocol` (group 14); devices 27 and
  37; uploads `E2E1023-03-1`, `-04-1`, `-05-1`, `-05-2`, `-05-3`, `-07-1` and `-08-2`. Delete them
  in the admin UI when done.
- Local: `app/data.e2e-1023-run06` (runs 02–06) and `app/data.e2e-1023-final` (runs 07–08). Your
  own data is restored to `app/data`.

| Runs | Passed | Passed with findings | Blocked |
|---|---|---|---|
| 10 (run 00 manual, runs 01–08, run 10 re-test) | 0 | 10 | 0 |

| Severity | Open |
|---|---|
| blocker | 0 |
| major | 0 |
| minor | 5 |
| cosmetic | 14 |
| question | 8 |

Withdrawn: 1 (F-018, superseded by F-038). Resolved: 1 (F-039, covered in run 07). Closed: 2 (F-015 fixed in test plan 1.1; F-016 by design). Fixed and confirmed in run 10: 5 (F-001, F-005, F-051, F-052, F-053). Fixed and confirmed in run 11: 13 (F-002, F-017, F-022, F-031, F-032, F-038, F-042, F-047, F-048, F-049, F-050, F-054, F-057), plus the app side of F-036; F-029 fixed in the spec. F-059 + F-060 fixed in `5e7abd3` (unit-tested with real ffmpeg; next run re-checks the log). Closed as not a defect: F-020. F-066 (user report), F-027 and F-026 fixed 2026-10-24, F-055, F-062, F-019, F-034 and F-041 (app side) fixed 2026-10-25; not yet re-run. Closed by user decision 2026-10-24: F-021 and F-023 (by design), F-024 (not reproducible), F-003 and F-004 (worked around in the test plan). Counts are open findings only.

Test plan 1.1 (2026-10-24) changes the plan for F-008, F-015, F-019, F-030 and F-039, and adds
run 09 (window sizes). Their status here stays as it was until the next run confirms the plan.

**Run 10 (fix re-test), 2026-10-24, app `59bb063` + uncommitted fixes:**
[report](reports/2026-10-24-run-10-fix-retest/report.md).

- All five major fixes are confirmed live: F-001, F-005, F-051, F-052 and F-053.
- New findings: F-055 to F-058.
- F-054 and F-006 were seen again.
- Run 10 data is kept in `app/data.e2e-1024-run10`. Your own data is restored to `app/data`.
- Left on the server: the protocol `E2E 1024 SiteName protocol`.

**Run 11 (minor-fix re-test), 2026-10-24, app `a0ba34c`:**
[report](reports/2026-10-24-run-11-minor-retest/report.md).

- Every fixed minor finding is confirmed live, and the five majors are confirmed again.
- F-059 is only partly fixed; the remaining leak is F-060.
- New findings: F-060 to F-065.
- Run 11 data is kept in `app/data.e2e-1024-run11`.

## Findings

| ID | Severity | Area | Title | Seen in | Status |
|---|---|---|---|---|---|
| [F-001](reports/2026-10-23-run-00-environment/report.md#f-001-a-microphone-that-fails-to-open-is-not-shown-starting-the-recording-then-throws) | major | desktop-app | A microphone that fails to open is not shown; starting the recording then throws | run 00, 08, 10, 11 | fixed (2026-10-24), confirmed in runs 10 and 11 |
| [F-002](reports/2026-10-23-run-00-environment/report.md#f-002-capture-format-negotiation-trusts-islinesupported-which-directsound-answers-yes-for-everything) | minor | desktop-app | Capture format negotiation trusts `isLineSupported`, which DirectSound answers yes for everything | run 00, 08, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-003](reports/2026-10-23-run-00-environment/report.md#f-003-the-compose-hot-reload-mcp-server-does-not-start-on-windows) | minor | environment | The compose-hot-reload MCP server does not start on Windows | run 00 | closed, worked around (test plan §3.2; user decision) |
| [F-004](reports/2026-10-23-run-00-environment/report.md#f-004-the-hot-reload-recompiler-cant-find-gradlewbat-when-started-from-claude-code) | minor | environment | The hot-reload recompiler can't find `gradlew.bat` when started from Claude Code | run 00 | closed, worked around (test plan §3.4; user decision) |
| [F-005](reports/2026-10-23-run-01-web-setup/report.md#f-005-the-webs-default-file-name-template-uses-sitename-which-the-app-doesnt-substitute) | major | integration | The web's default file-name template uses `${siteName}`, which the app doesn't substitute | run 01, 10, 11 | fixed (2026-10-24), confirmed in runs 10 and 11 |
| [F-006](reports/2026-10-23-run-01-web-setup/report.md#f-006-create-protocol-makes-a-protocol-with-no-patient-identifiers-and-no-patient-code-in-its-file-name) | minor | web-ui | "Create Protocol" makes a protocol with no patient identifiers and no patient code in its file name | run 01, 10, 11, run 14 (2026-10-29) | fixed in the redesign, confirmed in run 14 |
| [F-007](reports/2026-10-23-run-01-web-setup/report.md#f-007-boolean-defaults-are-blank-in-the-available-tasks-list) | cosmetic | web-ui | Boolean defaults are blank in the "Available Tasks" list | run 01 | closed, gone in the current code (code check 2026-10-29) |
| [F-008](reports/2026-10-23-run-01-web-setup/report.md#f-008-site-names-cant-contain-spaces-so-the-plans-naming-doesnt-work) | question | docs | Site names can't contain spaces, so the plan's naming doesn't work | run 01 | closed, gone in the web (2026-10-29) |
| [F-009](reports/2026-10-23-run-01-web-setup/report.md#f-009-reading-has-no-passage-in-the-config-the-app-would-hide-the-start-now-card-instead) | minor | integration | READING has no passage in the config; the app would hide the "Start now" card instead | run 01, run 03, run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 (served config; desktop card not re-checked) |
| [F-010](reports/2026-10-23-run-01-web-setup/report.md#f-010-the-designers-info-page-and-protocol-instructions-arent-in-the-site-config) | question | integration | The designer's Info page and protocol Instructions aren't in the site-config | run 01 | open |
| [F-011](reports/2026-10-23-run-01-web-setup/report.md#f-011-typo-in-the-en-phonation-instruction-should-least-5-seconds) | cosmetic | web-ui | Typo in the EN phonation instruction: "should least 5 seconds" | run 01 | closed, gone in the current code (code check 2026-10-29) |
| [F-012](reports/2026-10-23-run-01-web-setup/report.md#f-012-cs-questionnaire-texts-are-a-copy-of-the-en-texts) | question | web-ui | CS questionnaire texts are a copy of the EN texts | run 01, run 03 | open |
| [F-013](reports/2026-10-23-run-01-web-setup/report.md#f-013-the-dashboard-is-half-czech-half-english-with-editor-language-čeština) | cosmetic | web-ui | The dashboard is half Czech, half English with editor language Čeština | run 01 | closed, gone in the current code (code check 2026-10-29) |
| [F-014](reports/2026-10-23-run-01-web-setup/report.md#f-014-the-site-page-counts-language-variants-as-protocols-4-inherited-protocols) | cosmetic | web-ui | The site page counts language variants as protocols ("4 Inherited Protocols") | run 01 | closed, gone in the current code (code check 2026-10-29) |
| [F-015](reports/2026-10-23-run-01-web-setup/report.md#f-015-plan-323-gives-the-wrong-admin-login-path) | minor | docs | Plan §3.2.3 gives the wrong admin login path | run 01 | fixed in test plan 1.1 |
| [F-016](reports/2026-10-23-run-02-first-start/report.md#f-016-the-site-token-is-shown-in-clear-text-in-settings) | minor | desktop-app | The site token is shown in clear text in Settings | run 02, 07 | closed, by design (user decision) |
| [F-017](reports/2026-10-23-run-02-first-start/report.md#f-017-settings-has-no-live-microphone-level-and-the-microphone-list-starts-blank) | minor | desktop-app | Settings has no live microphone level, and the microphone list starts blank | run 02, 07, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-018](reports/2026-10-23-run-02-first-start/report.md#f-018-the-apps-own-log-appdatalogsapplog-stays-empty) | minor | desktop-app | The app's own log `app/data/logs/app.log` stays empty | run 02, 05 | withdrawn, see F-038 |
| [F-019](reports/2026-10-23-run-02-first-start/report.md#f-019-settings-has-no-camera-selection) | question | desktop-app | Settings has no camera selection | run 02, 05, run 05 | fixed (2026-10-25), confirmed in run 02 (2026-10-26): Settings has a camera picker |
| [F-020](reports/2026-10-23-run-02-first-start/report.md#f-020-at-the-default-800600-window-settings-is-clipped-and-its-buttons-are-unreachable) | minor | desktop-app | At the default 800×600 window, Settings is clipped and its buttons are unreachable | run 02, 07, 11 | closed, not a defect (Settings scrolls; guard test added) |
| [F-021](reports/2026-10-23-run-03-speech-happy/report.md#f-021-calibration-lets-the-examiner-continue-outside-the-band-with-no-warning) | question | desktop-app | Calibration lets the examiner continue outside the band, with no warning | run 03, 04, 05, 08 | closed, by design (user decision) |
| [F-022](reports/2026-10-23-run-03-speech-happy/report.md#f-022-a-skippable-task-shows-only-next-task-x-with-no-skip-label-and-no-skipped-state) | minor | desktop-app | A skippable task shows only "Next task X", with no Skip label and no skipped state | run 03, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-023](reports/2026-10-23-run-03-speech-happy/report.md#f-023-retry-task-starts-the-new-take-straight-away) | question | desktop-app | "Retry task" starts the new take straight away | run 03, 04, 05, run 04 | closed, by design (user decision) |
| [F-024](reports/2026-10-23-run-03-speech-happy/report.md#f-024-the-recording-indicator-didnt-visibly-change-during-a-take-unconfirmed) | question | desktop-app | The recording indicator didn't visibly change during a take (unconfirmed) | run 03, 05 | closed, not reproducible (user decision) |
| [F-025](reports/2026-10-23-run-03-speech-happy/report.md#f-025-editor-segment-labels-dont-say-which-repetition-a-segment-is) | cosmetic | desktop-app | Editor segment labels don't say which repetition a segment is | run 03, 04, run 03, run 04 | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-026](reports/2026-10-23-run-03-speech-happy/report.md#f-026-no-processing-feedback-after-the-editor) | question | desktop-app | No processing feedback after the editor | run 03, 04, 05 | fixed (2026-10-24), confirmed in run 03 (2026-10-26) |
| [F-027](reports/2026-10-23-run-04-speech-editing/report.md#f-027-the-editor-has-no-way-out-except-accept) | question | desktop-app | The editor has no way out except Accept | run 04, 05 | fixed (2026-10-24), confirmed in run 03 (2026-10-26) |
| [F-028](reports/2026-10-23-run-04-speech-editing/report.md#f-028-the-editors-position-line-stays-at-the-old-start-after-the-start-boundary-moves) | cosmetic | desktop-app | The editor's position line stays at the old start after the start boundary moves | run 04 | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-029](reports/2026-10-23-run-04-speech-editing/report.md#f-029-reprocessing-doesnt-regenerate-waveform_cache) | minor | desktop-app | Reprocessing doesn't regenerate `waveform_cache/` | run 04 | fixed in spec (2026-10-24; no run step needed) |
| [F-030](reports/2026-10-23-run-05-video/report.md#f-030-stopping-the-obs-virtual-camera-doesnt-make-the-camera-unavailable-so-the-audio-only-fallback-cant-be-tested-this-way) | question | environment | Stopping the OBS virtual camera doesn't make the camera unavailable, so the audio-only fallback can't be tested this way | run 05, run 05 | closed, environment (2026-10-29) |
| [F-031](reports/2026-10-23-run-05-video/report.md#f-031-on-the-phonation-screen-the-camera-preview-and-level-indicator-are-squeezed-into-a-23-px-strip) | minor | desktop-app | On the phonation screen, the camera preview and level indicator are squeezed into a 23 px strip | run 05, 11, run 05 | fixed (2026-10-24), confirmed in run 11 |
| [F-032](reports/2026-10-23-run-05-video/report.md#f-032-a-normal-camera-close-is-recorded-as-capture_failed) | minor | desktop-app | A normal camera close is recorded as `capture_failed` | run 05, 06, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-033](reports/2026-10-23-run-05-video/report.md#f-033-the-camera-stopped-messages-are-drawn-over-the-frozen-last-frame-and-are-hard-to-read) | cosmetic | desktop-app | The "camera stopped" messages are drawn over the frozen last frame and are hard to read | run 05 | closed, gone in the current code (code check 2026-10-29) |
| [F-034](reports/2026-10-23-run-05-video/report.md#f-034-a-partially-filmed-take-isnt-marked-as-partial-in-the-data) | question | desktop-app | A partially filmed take isn't marked as partial in the data | run 05, 06 | fixed (2026-10-25), confirmed in run 16 (2026-10-30) |
| [F-035](reports/2026-10-23-run-06-upload/report.md#f-035-the-web-session-detail-shows-input-gain-undefined-) | minor | web-ui | The web session detail shows "input gain undefined %" | run 06, run 14 (2026-10-29) | fixed in the redesign, confirmed in run 14 |
| [F-036](reports/2026-10-23-run-06-upload/report.md#f-036-the-camera-tile-shows-raw-ffmpeg-output-as-a-captureinterrupted-warning-even-for-a-normal-close) | minor | integration | The camera tile shows raw ffmpeg output as a "CaptureInterrupted" warning, even for a normal close | run 06, 11, run 14 (2026-10-29) | fixed in the redesign, confirmed in run 14 |
| [F-037](reports/2026-10-23-run-06-upload/report.md#f-037-the-admin-ui-cant-play-the-video-clips-mjpeg-in-mp4) | minor | integration | The admin UI can't play the video clips (MJPEG in MP4) | run 06, run 06 (2026-10-28) | closed, accepted (2026-10-29) |
| [F-038](reports/2026-10-23-run-06-upload/report.md#f-038-config-fetch-device-check-in-and-upload-write-no-log-lines) | minor | desktop-app | Config fetch, device check-in and upload write no log lines | run 06, 07, 08, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-039](reports/2026-10-23-run-06-upload/report.md#f-039-the-participant-masking-check-cant-run-because-no-test-session-has-a-hidden-field) | question | docs | The participant-masking check can't run, because no test session has a hidden field | run 06, 07 | resolved in run 07 (masking and reveal work) |
| [F-040](reports/2026-10-23-run-06-upload/report.md#f-040-the-questionnaire-summary-shows-internal-keys-instead-of-question-and-answer-text) | question | web-ui | The questionnaire summary shows internal keys instead of question and answer text | run 06 | closed, gone in the current code (code check 2026-10-29) |
| [F-041](reports/2026-10-23-run-07-quarantine/report.md#f-041-with-another-sites-token-the-app-switches-to-that-sites-config-while-the-server-keeps-the-device-in-site-a) | question | integration | With another site's token, the app switches to that site's config while the server keeps the device in Site A | run 07, run 07 (2026-10-28): confirmed | fixed, app side, confirmed in run 07 (2026-10-28) |
| [F-042](reports/2026-10-23-run-07-quarantine/report.md#f-042-settings-says-everything-looks-ok-next-to-the-red-registered-to-another-site-warning) | minor | desktop-app | Settings says "Everything looks OK" next to the red "registered to another site" warning | run 07, 11, run 07 (2026-10-28) | fixed (2026-10-24), confirmed in run 11 |
| [F-043](reports/2026-10-23-run-07-quarantine/report.md#f-043-the-web-session-detail-of-a-quarantined-session-has-an-empty-site-and-a-broken-header) | cosmetic | web-ui | The web session detail of a quarantined session has an empty site and a broken header | run 07, run 07 (2026-10-28): fixed in the new web | fixed (web) |
| [F-044](reports/2026-10-23-run-07-quarantine/report.md#f-044-the-reveal-audit-entry-cant-be-viewed-anywhere) | question | web-ui | The reveal audit entry can't be viewed anywhere | run 07, run 06 (2026-10-28): fixed in the new web, reveals and ZIP downloads listed under Activity | fixed (web) |
| [F-045](reports/2026-10-23-run-07-quarantine/report.md#f-045-a-quarantined-unassigned-device-shows-as-healthy-and-its-rejected-upload-counts-as-an-upload) | question | web-ui | A quarantined, unassigned device shows as "Healthy", and its rejected upload counts as an upload | run 07, run 07 (2026-10-28): computer shows "Needs assignment"; quarantined recording still counted Invalid (F-114) | fixed (web) |
| [F-046](reports/2026-10-23-run-07-quarantine/report.md#f-046-the-upload-progress-bar-fills-up-green-when-the-upload-failed) | cosmetic | desktop-app | The upload progress bar fills up green when the upload failed | run 07, 08, 11, run 07 (2026-10-28), run 08 (2026-10-28) | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-047](reports/2026-10-23-run-08-errors/report.md#f-047-a-session-left-at-calibration-stays-in-sessions-as-an-empty-not-processed-session) | minor | desktop-app | A session left at calibration stays in Sessions as an empty "Not processed" session | run 08, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-048](reports/2026-10-23-run-08-errors/report.md#f-048-with-the-server-unreachable-the-main-menu-gives-no-offline-indication) | minor | desktop-app | With the server unreachable, the main menu gives no offline indication | run 08, 11, run 08 (2026-10-28) | fixed (2026-10-24), confirmed in run 11 and run 08 (2026-10-28) |
| [F-049](reports/2026-10-23-run-08-errors/report.md#f-049-the-upload-failure-message-is-terse) | minor | desktop-app | The upload failure message is terse | run 08, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-050](reports/2026-10-23-run-08-errors/report.md#f-050-sessions-shows-a-stale-status-after-an-upload-or-reprocess-until-the-screen-is-reopened) | minor | desktop-app | Sessions shows a stale status after an upload or reprocess, until the screen is reopened | run 08, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-051](reports/2026-10-23-run-08-errors/report.md#f-051-after-back-on-calibration-the-microphone-cant-be-opened-again-until-the-app-restarts) | major | desktop-app | After Back on calibration, the microphone can't be opened again until the app restarts | run 08, 10, 11 | fixed (2026-10-24), confirmed in runs 10 and 11 |
| [F-052](reports/2026-10-23-run-08-errors/report.md#f-052-crash-recovery-is-silent-the-examiner-isnt-told-about-the-interrupted-session) | major | desktop-app | Crash recovery is silent: the examiner isn't told about the interrupted session | run 08, 10, 11 | fixed (2026-10-24), confirmed in runs 10 and 11 |
| [F-053](reports/2026-10-23-run-08-errors/report.md#f-053-a-recovered-session-with-no-finished-take-processes-into-a-zip-with-no-clips-and-is-then-uploadable) | major | desktop-app | A recovered session with no finished take processes into a ZIP with no clips, and is then uploadable | run 08, 10, 11 | fixed (2026-10-24), confirmed in runs 10 and 11 |
| [F-054](reports/2026-10-23-run-08-errors/report.md#f-054-the-recovered-timelines-stop-event-has-the-recovery-time-not-the-crash-time) | minor | desktop-app | The recovered timeline's stop event has the recovery time, not the crash time | run 08, 10, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-055](reports/2026-10-24-run-10-fix-retest/report.md#f-055-the-crash-recovery-dialog-closes-when-the-examiner-clicks-outside-it) | question | desktop-app | The crash-recovery dialog closes when the examiner clicks outside it | run 10 | fixed (2026-10-25), not yet re-run |
| [F-056](reports/2026-10-24-run-10-fix-retest/report.md#f-056-a-hot-reload-window-resize-can-log-an-unhandled-recoverable-error) | question | environment | A hot-reload window resize can log an "unhandled recoverable error" | run 10 | closed, environment (2026-10-29) |
| [F-057](reports/2026-10-24-run-10-fix-retest/report.md#f-057-the-zero-clip-processing-failure-shows-a-generic-message-and-writes-no-log-line) | minor | desktop-app | The zero-clip processing failure shows a generic message and writes no log line | run 10, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-058](reports/2026-10-24-run-10-fix-retest/report.md#f-058-web-create-protocol-opens-protocolsundefined) | cosmetic | web-ui | Web "Create Protocol" opens `.../protocols/undefined` | run 10 | closed, gone in the current code (code check 2026-10-29) |
| [F-059](reports/2026-10-24-run-10-fix-retest/report.md#f-059-the-video-recorder-logs-the-full-session-path-which-contains-the-patient-code) | minor | desktop-app | The video recorder logs the full session path, which contains the patient code | orchestrator 2026-10-24, run 11 | fixed together with F-060 (`5e7abd3`) |
| [F-060](reports/2026-10-24-run-11-minor-retest/report.md#f-060-video-remux-log-lines-carry-the-clip-name-which-holds-the-patient-code-and-installation-id) | minor | desktop-app | Video remux log lines carry the clip name, which holds the patient code and installation ID | run 11 | fixed (2026-10-24, `5e7abd3`), confirmed in run 03 (2026-10-26): no patient code or path in the log |
| [F-061](reports/2026-10-24-run-11-minor-retest/report.md#f-061-the-camera-failure-text-is-drawn-over-the-frozen-preview-and-is-hard-to-read) | cosmetic | desktop-app | The camera-failure text is drawn over the frozen preview and is hard to read | run 11 | closed, gone in the current code (code check 2026-10-29) |
| [F-062](reports/2026-10-24-run-11-minor-retest/report.md#f-062-a-device-picked-on-the-calibration-screen-isnt-remembered-the-system-default-can-be-a-silent-device) | question | desktop-app | A device picked on the calibration screen isn't remembered; the system default can be a silent device | run 11 | fixed (2026-10-25), not yet re-run |
| [F-063](reports/2026-10-24-run-11-minor-retest/report.md#f-063-at-1000820-save-and-go-back-in-settings-needs-a-short-scroll) | cosmetic | desktop-app | At 1000×820 "Save and go back" in Settings needs a short scroll | run 11, run 09 (2026-10-28) | open |
| [F-064](reports/2026-10-24-run-11-minor-retest/report.md#f-064-1-sessions-ready-to-upload) | cosmetic | desktop-app | "1 sessions ready to upload" | run 11, run 07 (2026-10-28), run 08 (2026-10-28) | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-065](reports/2026-10-24-run-11-minor-retest/report.md#f-065-a-fresh-questionnaire-shows-red-validation-errors-before-any-input) | cosmetic | desktop-app | A fresh questionnaire shows red validation errors before any input | run 11, run 03 | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-066](reports/2026-10-24-run-11-minor-retest/report.md#f-066-after-an-edit-from-sessions-accept-leaves-the-session-processed-with-the-old-zip) | major | desktop-app | After an edit from Sessions, Accept leaves the session "Processed" with the old ZIP | user report 2026-10-24 | fixed (2026-10-24), confirmed in run 16 (2026-10-30) |
| [F-067](reports/2026-10-26-run-A-new-web/report.md#f-a-1) | minor | web-ui | Relative and absolute times shifted by the browser's UTC offset (zoneless server time) | run A (F-A-1), run A2, run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 |
| [F-068](reports/2026-10-26-run-A-new-web/report.md#f-a-2) | minor | web-ui | Save-confirm dialog still uses old participant-link text | run A (F-A-2), run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 |
| [F-069](reports/2026-10-26-run-A-new-web/report.md#f-a-3) | minor | web-ui | Blank protocol description stored as "Auto-created from AdminTaskEditor" and sent to the desktop config | run A (F-A-3), run 02 (F-02-2), run 03, run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 |
| [F-070](reports/2026-10-26-run-A-new-web/report.md#f-a-4) | cosmetic | web-ui | Questionnaire modal has untranslated strings in CS/DE | run A (F-A-4) | fixed (2026-10-30), confirmed in run 15 (2026-10-30) |
| [F-071](reports/2026-10-26-run-A-new-web/report.md#f-a-5) | cosmetic | web-ui | Questionnaire modal is off-style (purple pill, all-caps labels) | run A (F-A-5) | fixed (2026-10-30), confirmed in run 15 (2026-10-30) |
| [F-072](reports/2026-10-26-run-A-new-web/report.md#f-a-6) | question | integration | Info page is saved but no INFO task or string reaches /site-config | run A (F-A-6), run 03, run 04 | open |
| [F-073](reports/2026-10-26-run-A-new-web/report.md#f-a-7) | minor | web-ui | Designer accepts contradictory durations without a warning | run A (F-A-7), run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 |
| [F-074](reports/2026-10-26-run-A-new-web/report.md#f-a-8) | minor | web-ui | At 1024 px the Clinics table overflows its card | run A (F-A-8), run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 |
| [F-075](reports/2026-10-26-run-A-new-web/report.md#f-a-9) | question | integration | Default file-name template differs from spec §6.4 | run A (F-A-9) | closed, spec updated (2026-10-29) |
| [F-076](reports/2026-10-26-run-A-new-web/report.md#f-a-10) | minor | web-ui | Custom patient field label and help have no per-language entry | run A (F-A-10), run 14 (2026-10-29) | closed, by design (2026-10-29); hint confirmed in run 14 |
| [F-077](reports/2026-10-26-run-A-new-web/report.md#f-a-11) | major | environment | Persona users cannot be given a known password; editor / no-grants checks blocked | run A (F-A-11) | closed, worked around (run A2: persona passwords set in the DB over SSH; test plan Run A precondition; user decision 2026-10-29) |
| [F-078](reports/2026-10-26-run-A-new-web/report.md#f-a-12) | question | web-ui | Opening #/login while signed in clears the session | run A (F-A-12) | fixed (2026-10-30), confirmed in run 15 (2026-10-30) |
| [F-079](reports/2026-10-26-run-A-new-web/report.md#f-a-13) | minor | web-ui | Direct link to protocol preview ends on #/not-found | run A (F-A-13), run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 |
| [F-080](reports/2026-10-26-run-A-new-web/report.md#f-a-14) | cosmetic | web-ui | "Invite user" wording differs from spec "New user" | run A (F-A-14) | closed, spec updated (2026-10-29) |
| [F-081](reports/2026-10-26-run-A-new-web/report.md#f-a-15) | cosmetic | web-ui | Questionnaire row in the task list shows blank values | run A (F-A-15) | closed, gone in the current code (code check 2026-10-29) |
| [F-082](reports/2026-10-26-run-A-new-web/report.md#f-a-16) | cosmetic | web-ui | Clinic name input silently truncates at 64 characters | run A (F-A-16) | fixed (2026-10-30), confirmed in run 15 (2026-10-30) |
| [F-083](reports/2026-10-26-run-A-new-web/report.md#f-a-17) | question | server-api | last_editor error unreachable on normal studies | run A (F-A-17), run A2 | closed, by design (2026-10-29) |
| [F-084](reports/2026-10-26-run-A-new-web/report.md#f-a-18) | cosmetic | web-ui | Library "Not available" page text is wrong for the audience | run A (F-A-18), run A2 | fixed (2026-10-30), confirmed in run 15 (2026-10-30) |
| [F-085](reports/2026-10-26-run-A-new-web/report.md#f-a-19) | question | web-ui | Preview shows the participant-web flow, not the desktop flow | run A (F-A-19) | open |
| [F-086](reports/2026-10-26-run-A-new-web/report.md#f-a-20) | minor | server-api | POST /protocols with a partial body returns 500 instead of 400 | run A (F-A-20), run A2, run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 |
| [F-087](reports/2026-10-26-run-02-first-start/report.md#f-02-1) | minor | web-ui | Computers tab does not show the config version a computer got | run 02 (F-02-1), run 06 (2026-10-28), run 07 (2026-10-28), run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 |
| [F-088](reports/2026-10-26-run-02-first-start/report.md#f-02-3) | cosmetic | desktop-app | Language options in Settings are raw codes cs / en | run 02 (F-02-3) | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-089](reports/2026-10-26-run-02-first-start/report.md#f-02-4) | cosmetic | desktop-app | "No site token" message is left-aligned | run 02 (F-02-4), run 07 (2026-10-28) | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-090](reports/2026-10-26-run-02-first-start/report.md#f-02-5) | question | web-ui | Connect-a-computer panel shows server address ./api | run 02 (F-02-5), run A2 (F-A2-3) | fixed (2026-10-30), confirmed in run 15 (2026-10-30) |
| [F-091](reports/2026-10-26-run-03-speech-happy/report.md#f-03-1) | major | desktop-app | Last option of a multiple-choice question is cut off and unreachable at 1000x820 | run 03 (F-03-1), run 04, run 12 (2026-10-28) | fixed (2026-10-28, scrollbar), confirmed in run 12: scroll reaches and ticks the last option |
| [F-092](reports/2026-10-26-run-03-speech-happy/report.md#f-03-2) | minor | environment | Test audio source (quiet music sample) cannot reach the calibration band | run 03 (F-03-2), run 04 | closed, environment (2026-10-29) |
| [F-093](reports/2026-10-26-run-03-speech-happy/report.md#f-03-3) | cosmetic | desktop-app | Editor "Accept" label wraps to "Acc / ept" | run 03 (F-03-3), run 04, run 09 (2026-10-28, same cause as F-125), run 12 (2026-10-28) | fixed (2026-10-28), confirmed in run 12 |
| [F-094](reports/2026-10-26-run-03-speech-happy/report.md#f-03-4) | question | integration | Web config has enableEditor=false so the editor is skipped | run 03 (F-03-4), run 04 | open |
| [F-095](reports/2026-10-26-run-03-speech-happy/report.md#f-03-5) | question | desktop-app | App log has nothing about the session (start, take, processing) | run 03 (F-03-5), run 04, run 05 | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-096](reports/2026-10-26-run-A2-personas/report.md#f-a2-1) | minor | web-ui | Received persona sees disabled runs-at switches instead of none (spec §4) | run A2 (F-A2-1), run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 |
| [F-097](reports/2026-10-26-run-A2-personas/report.md#f-a2-2) | minor | web-ui | Editor sees a clinic count that includes clinics they cannot see | run A2 (F-A2-2), run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 |
| [F-098](reports/2026-10-26-run-A2-personas/report.md#f-a2-4) | cosmetic | web-ui | Empty Clinics page tells users who cannot create clinics to create one | run A2 (F-A2-4) | fixed (2026-10-30), confirmed in run 15 (2026-10-30) |
| [F-099](reports/2026-10-26-run-A2-personas/report.md#f-a2-5) | cosmetic | web-ui | Recordings search placeholder cut off for one-clinic users | run A2 (F-A2-5) | fixed (2026-10-30), confirmed in run 15 (2026-10-30) |
| [F-100](reports/2026-10-26-run-A2-personas/report.md#f-a2-6) | question | server-api | Re-adding an existing editor returns 201 with added_at null; email match is case-insensitive | run A2 (F-A2-6) | fixed (2026-10-30), confirmed in run 15 (2026-10-30) |
| [F-101](reports/2026-10-26-run-A2-personas/report.md#f-a2-7) | cosmetic | web-ui | Hidden study says "Study not found", hidden clinic says "Page not found" | run A2 (F-A2-7) | fixed (2026-10-30), confirmed in run 15 (2026-10-30) |
| [F-102](reports/2026-10-26-run-A2-personas/report.md#f-a2-8) | question | server-api | Non-master users see full installation IDs in the UI and GET /devices | run A2 (F-A2-8), run 06 (2026-10-28) | open |
| [F-103](reports/2026-10-26-run-04-speech-editing/report.md#f-04-1) | question | desktop-app | Accept on one editor segment finishes the editor and reprocesses at once | run 04 (F-04-1) | open |
| [F-104](reports/2026-10-26-run-04-speech-editing/report.md#f-04-2) | minor | desktop-app | No feedback while or after Accept / Reprocess from Sessions | run 04 (F-04-2), run 13 (2026-10-29) | fixed (2026-10-29), confirmed in run 13 |
| [F-105](reports/2026-10-26-run-04-speech-editing/report.md#f-04-3) | cosmetic | desktop-app | Editor shows end time and length truncated, not rounded | run 04 (F-04-3) | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-106](reports/2026-10-26-run-05-video/report.md#f-05-2) | minor | desktop-app | Camera failure messages overlap each other and the frozen frame in the preview box | run 05 (F-05-2), run 13 (2026-10-29) | fixed (2026-10-29), confirmed in run 13 |
| [F-107](reports/2026-10-26-run-05-video/report.md#f-05-3) | question | desktop-app | After a camera failure later tasks stay audio-only with no retry | run 05 (F-05-3) | open |
| [F-108](reports/2026-10-26-run-05-video/report.md#f-05-4) | question | desktop-app | Filmed clips are large (about 2.7 MB/s); 50 s of video gives a 146 MB ZIP | run 05 (F-05-4) | closed, by design (2026-10-29) |
| [F-109](reports/2026-10-26-run-05-video/report.md#f-05-5-nothing-in-the-archive-says-the-camera-was-missing-at-start) | question | desktop-app | Nothing in the archive says the camera was missing at start | run 05 (F-05-5), run 06 (2026-10-28) | fixed (2026-10-30), deployed; not verifiable live in run 16 (no camera can be hidden without admin rights); unit tests only |
| [F-110](reports/2026-10-26-run-06-upload/report.md#f-06-1-server-record-does-not-show-that-a-video-is-shorter-than-the-audio-videocompletefalse-is-ignored) | minor | web-ui | Server record does not show that a video is shorter than the audio (videoComplete:false ignored) | run 06 (F-06-1) | fixed in the web repo (2026-10-29, `44d8490`), deployed; live check not covered in run 14 (needs a partial-video upload) |
| [F-111](reports/2026-10-26-run-06-upload/report.md#f-06-2-camera-text-on-the-detail-page-runs-together-obs-virtual-camerathe-camera-could-not-capture-video) | cosmetic | web-ui | Camera name and failure notice run together on the detail page | run 06 (F-06-2), run 14 (2026-10-29) | fixed (2026-10-29, web `44d8490`), confirmed in run 14 |
| [F-112](reports/2026-10-26-run-06-upload/report.md#f-06-3-participant-field-label-shown-as-year-of-birth-opt-the-raw-field-key-humanized) | cosmetic | web-ui | Participant field label shown as "Year of birth opt" (raw key) | run 06 (F-06-3) | fixed (2026-10-30), confirmed in run 15 (2026-10-30) |
| [F-113](reports/2026-10-26-run-07-quarantine/report.md#f-07-1-a-recording-released-from-quarantine-and-re-uploaded-is-flagged-re-sent-duplicate) | question | web-ui | Recording released from quarantine and re-uploaded is flagged "Re-sent" | run 07 (F-07-1) | open |
| [F-114](reports/2026-10-26-run-07-quarantine/report.md#f-07-2-a-quarantined-recording-is-shown-as-invalid-and-counted-in-the-invalid-kpi) | question | web-ui | Quarantined recording shown as "Invalid" and counted in the Invalid KPI | run 07 (F-07-2) | open |
| [F-115](reports/2026-10-26-run-07-quarantine/report.md#f-07-3-the-quarantine-banner-on-the-recording-detail-has-no-separator) | cosmetic | web-ui | Quarantine banner lead and sentence run together | run 07 (F-07-3) | fixed (2026-10-30), deployed; not verifiable live in run 15 (no recording in quarantine) |
| [F-116](reports/2026-10-26-run-07-quarantine/report.md#f-07-4-synthetic-keyboard-input-does-not-reach-the-app-window-in-this-rdp-session-so-a-token-cannot-be-typed-or-pasted) | question | environment | Synthetic keyboard input doesn't reach the app under RDP; token set by script | run 07 (F-07-4) | closed, environment (2026-10-29) |
| [F-117](reports/2026-10-26-run-08-errors/report.md#f-08-1-a-completely-silent-microphone-signal-is-not-detected-anywhere-calibration-take-finish) | question | desktop-app | A completely silent microphone signal is not detected anywhere (calibration, take, finish) | run 08 (F-08-1) | open |
| [F-118](reports/2026-10-26-run-09-layout/report.md#f-09-1-no-minimum-window-size-at-640x480-the-main-menu-and-protocol-picker-show-only-part-of-their-list) | question | desktop-app | No minimum window size; at 640x480 main menu and picker lists shrink to one or two items | run 09 (F-09-1), run 12 (2026-10-28) | fixed (2026-10-28), confirmed in run 12 |
| [F-119](reports/2026-10-26-run-09-layout/report.md#f-09-2-calibration-at-640x480-continue-is-squeezed-to-a-sliver-picture-and-level-meter-shrink-to-77-px) | minor | desktop-app | Calibration at 640x480: Continue squeezed, picture and meter shrink | run 09 (F-09-2) | closed (2026-10-28), below the 960×720 minimum window (F-118); confirm in re-test |
| [F-120](reports/2026-10-26-run-09-layout/report.md#f-09-3-task-screens-at-640x480-instructions-and-play-example-disappear-the-next-task-label-is-written-vertically) | minor | desktop-app | Task screens at 640x480: instructions and Play example disappear | run 09 (F-09-3) | closed (2026-10-28), below the 960×720 minimum window (F-118); confirm in re-test |
| [F-121](reports/2026-10-26-run-09-layout/report.md#f-09-4-at-800x600-the-instruction-box-is-only-132-px-high-while-about-210-px-below-it-stay-empty-play-example-and-start-now-are-hidden-behind-a-scroll) | minor | desktop-app | At 800x600 the instruction box is 132 px while space below is empty; Play example behind a scroll | run 09 (F-09-4) | closed (2026-10-28), below the 960×720 minimum window (F-118); confirm in re-test |
| [F-122](reports/2026-10-26-run-09-layout/report.md#f-09-5-a-filmed-take-was-interrupted-microphone-was-disconnected-read_starvation-right-after-a-resize) | minor | desktop-app | Filmed take interrupted (READ_STARVATION) right after a resize, intermittent | run 09 (F-09-5), run 13 (2026-10-29) | fixed (2026-10-29), confirmed in run 13 |
| [F-123](reports/2026-10-26-run-09-layout/report.md#f-09-6-a-session-with-a-microphone-interruption-plus-resume-cannot-be-processed-processing-failed-iofailure-retry-and-reprocess-fail-again) | major | desktop-app | Session with a mic interruption + Resume (2-part master) cannot be processed (IoFailure) | run 09 (F-09-6), run 12 (2026-10-28) | fixed (2026-10-28), confirmed in run 12 |
| [F-124](reports/2026-10-26-run-09-layout/report.md#f-09-7-sessions-go-to-upload-and-back-are-squeezed-out-at-800x600-and-1024x768) | major | desktop-app | Sessions: Go to upload and Back squeezed out at 800x600 and 1024x768 | run 09 (F-09-7), run 12 (2026-10-28) | fixed (2026-10-28), confirmed in run 12 |
| [F-125](reports/2026-10-26-run-09-layout/report.md#f-09-8-editor-at-800x600-accept-is-not-on-screen-0x0-discard-changes-wraps-one-letter-per-line-same-root-cause-as-f-093) | major | desktop-app | Editor at 800x600: Accept off screen, Discard wraps per letter (cause of F-093) | run 09 (F-09-8), run 12 (2026-10-28) | fixed (2026-10-28), confirmed in run 12 |
| [F-126](reports/2026-10-26-run-09-layout/report.md#f-09-9-upload-screen-at-800x600-back-is-not-on-screen-0x0) | major | desktop-app | Upload screen at 800x600: Back off screen | run 09 (F-09-9), run 12 (2026-10-28) | fixed (2026-10-28), confirmed in run 12 |
| [F-127](reports/2026-10-26-run-09-layout/report.md#f-09-10-at-1512x876-content-stretches-long-text-lines-picture-and-level-meter-far-apart-fixed-height-session-list) | cosmetic | desktop-app | At full screen content stretches (long lines, meter far from picture) | run 09 (F-09-10) | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-128](reports/2026-10-26-run-09-layout/report.md#f-09-11-plan-and-notes-say-the-app-opens-at-800x600-it-opens-at-1000x820) | cosmetic | docs | Plan says the app opens at 800x600; it opens at 1000x820 | run 09 (F-09-11) | closed, gone in the current code (code check 2026-10-29) |
| [F-129](reports/2026-10-26-run-09-layout/report.md#f-129-the-interruption-records-sample-offset-disagrees-with-the-interruption-events-by-1500-frames) | minor | desktop-app | Interruption record's sample offset disagrees with the interruption events by 1500 frames (31 ms of part 1 dropped) | fix round 2026-10-28 (orchestrator), run 13 (2026-10-29) | fixed (2026-10-29), confirmed in run 13 |
| [F-130](reports/2026-10-28-run-12-fix-retest/report.md#f-12-1-editor-controls-are-grouped-oddly-when-the-row-wraps-at-960x720-and-1000x820) | cosmetic | desktop-app | Editor controls grouped oddly when the row wraps (Previous and Next on different rows) | run 12 (F-12-1) | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-131](reports/2026-10-28-run-12-fix-retest/report.md#f-12-2-disabling-the-capture-device-for-4-s-during-a-take-does-not-interrupt-the-recording) | question | desktop-app | Disabling the capture device for 4 s during a take does not interrupt the recording | run 12 (F-12-2), run 13 (2026-10-29) | closed, environment (2026-10-29) |
| [F-132](reports/2026-10-26-run-05-video/report.md#f-132-the-video-preview-texts-are-english-only) | cosmetic | desktop-app | Video preview texts are English only | fix round 2026-10-29 (orchestrator), run 13 (2026-10-29) | fixed (2026-10-29), confirmed in run 13 (EN live; CS by unit tests) |
| [F-133](reports/2026-10-29-run-13-fix-retest/report.md#f-13-1-the-task-layout-shifts-when-the-camera-lost-message-appears) | cosmetic | desktop-app | Task layout shifts when the "Camera lost" message appears | run 13 (F-13-1) | fixed (2026-10-30), confirmed in run 16 (2026-10-30) |
| [F-134](reports/2026-10-29-run-13-fix-retest/report.md#f-134-the-recording_resumed-event-carries-a-later-offset-than-the-cut) | minor | desktop-app | RECORDING_RESUMED event carries a later offset than the cut; crash recovery after a Resume shifted offsets | review of fix round 2 (2026-10-29) | fixed (2026-10-29), not yet re-run |
| [F-135](reports/2026-10-29-run-13-fix-retest/report.md#f-135-a-wedged-capture-thread-from-an-old-episode-could-write-into-the-resumed-part) | minor | desktop-app | A wedged capture thread from an old episode could write into the resumed part | review of fix round 2 (2026-10-29) | fixed (2026-10-29), not yet re-run |
| [F-136](reports/2026-10-29-run-13-fix-retest/report.md#f-136-consecutive-late-watchdog-ticks-could-hide-a-real-device-loss) | minor | desktop-app | Consecutive late watchdog ticks could hide a real device loss | review of fix round 2 (2026-10-29) | fixed (2026-10-29), not yet re-run |
| [F-137](reports/2026-10-29-run-14-web-retest/report.md#f-14-1-cancelling-the-patient-field-editor-keeps-a-blank-custom-field) | minor | web-ui | Cancelling the patient-field editor keeps a blank custom field | run 14 (F-14-1), run 14b (2026-10-29) | fixed (2026-10-29, web `69f956e`), confirmed in run 14b |
| [F-138](reports/2026-10-29-run-14-web-retest/report.md#f-14-2-at-1024-px-the-sticky-table-header-does-not-stick-on-overflowing-tables) | cosmetic | web-ui | At 1024 px the sticky table header does not stick on overflowing tables | run 14 (F-14-2) | closed, accepted (2026-10-29) |
| [F-139](reports/2026-10-29-run-14-web-retest/report.md#f-139-cancelling-the-info-page-or-instructions-editor-keeps-the-typed-text) | minor | web-ui | Cancelling the info page or instructions editor keeps the typed text | fix of F-137 (engineer, 2026-10-29), run 14b (2026-10-29) | fixed (2026-10-29, web `69f956e`), confirmed in run 14b |
| [F-140](reports/2026-10-30-run-15-web-retest/report.md#f-15-1-add-instructions-in-the-protocol-editor-stays-english-in-cs-and-de) | cosmetic | web-ui | "Add Instructions" in the protocol editor stays English in CS and DE | run 15 (F-15-1) | open |
| [F-141](reports/2026-10-30-run-16-desktop-retest/report.md#f-16-1-a-missing-archive-makes-the-upload-screen-say-files-successfully-uploaded) | minor | desktop-app | A missing archive makes the Upload screen say "Files successfully uploaded." | run 16 (F-16-1) | open |
| [F-142](reports/2026-10-30-run-16-desktop-retest/report.md#f-16-2-play-example-is-cut-off-in-the-scrollable-instruction-panel-at-960x720-on-a-filmed-task) | question | desktop-app | "Play example" is cut off in the scrollable instruction panel at 960x720 on a filmed task | run 16 (F-16-2) | open |
