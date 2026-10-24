# E2E findings index

Kept by the orchestrator (see [`test-plan.md`](test-plan.md) §1.1 and §5.3). Each row links to
the full finding in its run report. IDs are never reused; a duplicate seen in a later run is
added to the original row's "Seen in" column rather than getting a new ID.

## Summary

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
| [F-006](reports/2026-10-23-run-01-web-setup/report.md#f-006-create-protocol-makes-a-protocol-with-no-patient-identifiers-and-no-patient-code-in-its-file-name) | minor | web-ui | "Create Protocol" makes a protocol with no patient identifiers and no patient code in its file name | run 01, 10, 11 | open |
| [F-007](reports/2026-10-23-run-01-web-setup/report.md#f-007-boolean-defaults-are-blank-in-the-available-tasks-list) | cosmetic | web-ui | Boolean defaults are blank in the "Available Tasks" list | run 01 | open |
| [F-008](reports/2026-10-23-run-01-web-setup/report.md#f-008-site-names-cant-contain-spaces-so-the-plans-naming-doesnt-work) | question | docs | Site names can't contain spaces, so the plan's naming doesn't work | run 01 | open |
| [F-009](reports/2026-10-23-run-01-web-setup/report.md#f-009-reading-has-no-passage-in-the-config-the-app-would-hide-the-start-now-card-instead) | minor | integration | READING has no passage in the config; the app would hide the "Start now" card instead | run 01 | open |
| [F-010](reports/2026-10-23-run-01-web-setup/report.md#f-010-the-designers-info-page-and-protocol-instructions-arent-in-the-site-config) | question | integration | The designer's Info page and protocol Instructions aren't in the site-config | run 01 | open |
| [F-011](reports/2026-10-23-run-01-web-setup/report.md#f-011-typo-in-the-en-phonation-instruction-should-least-5-seconds) | cosmetic | web-ui | Typo in the EN phonation instruction: "should least 5 seconds" | run 01 | open |
| [F-012](reports/2026-10-23-run-01-web-setup/report.md#f-012-cs-questionnaire-texts-are-a-copy-of-the-en-texts) | question | web-ui | CS questionnaire texts are a copy of the EN texts | run 01 | open |
| [F-013](reports/2026-10-23-run-01-web-setup/report.md#f-013-the-dashboard-is-half-czech-half-english-with-editor-language-čeština) | cosmetic | web-ui | The dashboard is half Czech, half English with editor language Čeština | run 01 | open |
| [F-014](reports/2026-10-23-run-01-web-setup/report.md#f-014-the-site-page-counts-language-variants-as-protocols-4-inherited-protocols) | cosmetic | web-ui | The site page counts language variants as protocols ("4 Inherited Protocols") | run 01 | open |
| [F-015](reports/2026-10-23-run-01-web-setup/report.md#f-015-plan-323-gives-the-wrong-admin-login-path) | minor | docs | Plan §3.2.3 gives the wrong admin login path | run 01 | fixed in test plan 1.1 |
| [F-016](reports/2026-10-23-run-02-first-start/report.md#f-016-the-site-token-is-shown-in-clear-text-in-settings) | minor | desktop-app | The site token is shown in clear text in Settings | run 02, 07 | closed, by design (user decision) |
| [F-017](reports/2026-10-23-run-02-first-start/report.md#f-017-settings-has-no-live-microphone-level-and-the-microphone-list-starts-blank) | minor | desktop-app | Settings has no live microphone level, and the microphone list starts blank | run 02, 07, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-018](reports/2026-10-23-run-02-first-start/report.md#f-018-the-apps-own-log-appdatalogsapplog-stays-empty) | minor | desktop-app | The app's own log `app/data/logs/app.log` stays empty | run 02, 05 | withdrawn, see F-038 |
| [F-019](reports/2026-10-23-run-02-first-start/report.md#f-019-settings-has-no-camera-selection) | question | desktop-app | Settings has no camera selection | run 02, 05 | fixed (2026-10-25), not yet re-run |
| [F-020](reports/2026-10-23-run-02-first-start/report.md#f-020-at-the-default-800600-window-settings-is-clipped-and-its-buttons-are-unreachable) | minor | desktop-app | At the default 800×600 window, Settings is clipped and its buttons are unreachable | run 02, 07, 11 | closed, not a defect (Settings scrolls; guard test added) |
| [F-021](reports/2026-10-23-run-03-speech-happy/report.md#f-021-calibration-lets-the-examiner-continue-outside-the-band-with-no-warning) | question | desktop-app | Calibration lets the examiner continue outside the band, with no warning | run 03, 04, 05, 08 | closed, by design (user decision) |
| [F-022](reports/2026-10-23-run-03-speech-happy/report.md#f-022-a-skippable-task-shows-only-next-task-x-with-no-skip-label-and-no-skipped-state) | minor | desktop-app | A skippable task shows only "Next task X", with no Skip label and no skipped state | run 03, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-023](reports/2026-10-23-run-03-speech-happy/report.md#f-023-retry-task-starts-the-new-take-straight-away) | question | desktop-app | "Retry task" starts the new take straight away | run 03, 04, 05 | closed, by design (user decision) |
| [F-024](reports/2026-10-23-run-03-speech-happy/report.md#f-024-the-recording-indicator-didnt-visibly-change-during-a-take-unconfirmed) | question | desktop-app | The recording indicator didn't visibly change during a take (unconfirmed) | run 03, 05 | closed, not reproducible (user decision) |
| [F-025](reports/2026-10-23-run-03-speech-happy/report.md#f-025-editor-segment-labels-dont-say-which-repetition-a-segment-is) | cosmetic | desktop-app | Editor segment labels don't say which repetition a segment is | run 03, 04 | open |
| [F-026](reports/2026-10-23-run-03-speech-happy/report.md#f-026-no-processing-feedback-after-the-editor) | question | desktop-app | No processing feedback after the editor | run 03, 04, 05 | fixed (2026-10-24), not yet re-run |
| [F-027](reports/2026-10-23-run-04-speech-editing/report.md#f-027-the-editor-has-no-way-out-except-accept) | question | desktop-app | The editor has no way out except Accept | run 04, 05 | fixed (2026-10-24), not yet re-run |
| [F-028](reports/2026-10-23-run-04-speech-editing/report.md#f-028-the-editors-position-line-stays-at-the-old-start-after-the-start-boundary-moves) | cosmetic | desktop-app | The editor's position line stays at the old start after the start boundary moves | run 04 | open |
| [F-029](reports/2026-10-23-run-04-speech-editing/report.md#f-029-reprocessing-doesnt-regenerate-waveform_cache) | minor | desktop-app | Reprocessing doesn't regenerate `waveform_cache/` | run 04 | fixed in spec (2026-10-24; no run step needed) |
| [F-030](reports/2026-10-23-run-05-video/report.md#f-030-stopping-the-obs-virtual-camera-doesnt-make-the-camera-unavailable-so-the-audio-only-fallback-cant-be-tested-this-way) | question | environment | Stopping the OBS virtual camera doesn't make the camera unavailable, so the audio-only fallback can't be tested this way | run 05 | open |
| [F-031](reports/2026-10-23-run-05-video/report.md#f-031-on-the-phonation-screen-the-camera-preview-and-level-indicator-are-squeezed-into-a-23-px-strip) | minor | desktop-app | On the phonation screen, the camera preview and level indicator are squeezed into a 23 px strip | run 05, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-032](reports/2026-10-23-run-05-video/report.md#f-032-a-normal-camera-close-is-recorded-as-capture_failed) | minor | desktop-app | A normal camera close is recorded as `capture_failed` | run 05, 06, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-033](reports/2026-10-23-run-05-video/report.md#f-033-the-camera-stopped-messages-are-drawn-over-the-frozen-last-frame-and-are-hard-to-read) | cosmetic | desktop-app | The "camera stopped" messages are drawn over the frozen last frame and are hard to read | run 05 | open |
| [F-034](reports/2026-10-23-run-05-video/report.md#f-034-a-partially-filmed-take-isnt-marked-as-partial-in-the-data) | question | desktop-app | A partially filmed take isn't marked as partial in the data | run 05, 06 | fixed (2026-10-25), not yet re-run |
| [F-035](reports/2026-10-23-run-06-upload/report.md#f-035-the-web-session-detail-shows-input-gain-undefined-) | minor | web-ui | The web session detail shows "input gain undefined %" | run 06 | open |
| [F-036](reports/2026-10-23-run-06-upload/report.md#f-036-the-camera-tile-shows-raw-ffmpeg-output-as-a-captureinterrupted-warning-even-for-a-normal-close) | minor | integration | The camera tile shows raw ffmpeg output as a "CaptureInterrupted" warning, even for a normal close | run 06, 11 | open (app side fixed, confirmed in run 11; web tile open) |
| [F-037](reports/2026-10-23-run-06-upload/report.md#f-037-the-admin-ui-cant-play-the-video-clips-mjpeg-in-mp4) | minor | integration | The admin UI can't play the video clips (MJPEG in MP4) | run 06 | open |
| [F-038](reports/2026-10-23-run-06-upload/report.md#f-038-config-fetch-device-check-in-and-upload-write-no-log-lines) | minor | desktop-app | Config fetch, device check-in and upload write no log lines | run 06, 07, 08, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-039](reports/2026-10-23-run-06-upload/report.md#f-039-the-participant-masking-check-cant-run-because-no-test-session-has-a-hidden-field) | question | docs | The participant-masking check can't run, because no test session has a hidden field | run 06, 07 | resolved in run 07 (masking and reveal work) |
| [F-040](reports/2026-10-23-run-06-upload/report.md#f-040-the-questionnaire-summary-shows-internal-keys-instead-of-question-and-answer-text) | question | web-ui | The questionnaire summary shows internal keys instead of question and answer text | run 06 | open |
| [F-041](reports/2026-10-23-run-07-quarantine/report.md#f-041-with-another-sites-token-the-app-switches-to-that-sites-config-while-the-server-keeps-the-device-in-site-a) | question | integration | With another site's token, the app switches to that site's config while the server keeps the device in Site A | run 07 | fixed, app side (2026-10-25), not yet re-run |
| [F-042](reports/2026-10-23-run-07-quarantine/report.md#f-042-settings-says-everything-looks-ok-next-to-the-red-registered-to-another-site-warning) | minor | desktop-app | Settings says "Everything looks OK" next to the red "registered to another site" warning | run 07, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-043](reports/2026-10-23-run-07-quarantine/report.md#f-043-the-web-session-detail-of-a-quarantined-session-has-an-empty-site-and-a-broken-header) | cosmetic | web-ui | The web session detail of a quarantined session has an empty site and a broken header | run 07 | open |
| [F-044](reports/2026-10-23-run-07-quarantine/report.md#f-044-the-reveal-audit-entry-cant-be-viewed-anywhere) | question | web-ui | The reveal audit entry can't be viewed anywhere | run 07 | open |
| [F-045](reports/2026-10-23-run-07-quarantine/report.md#f-045-a-quarantined-unassigned-device-shows-as-healthy-and-its-rejected-upload-counts-as-an-upload) | question | web-ui | A quarantined, unassigned device shows as "Healthy", and its rejected upload counts as an upload | run 07 | open |
| [F-046](reports/2026-10-23-run-07-quarantine/report.md#f-046-the-upload-progress-bar-fills-up-green-when-the-upload-failed) | cosmetic | desktop-app | The upload progress bar fills up green when the upload failed | run 07, 08, 11 | open |
| [F-047](reports/2026-10-23-run-08-errors/report.md#f-047-a-session-left-at-calibration-stays-in-sessions-as-an-empty-not-processed-session) | minor | desktop-app | A session left at calibration stays in Sessions as an empty "Not processed" session | run 08, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-048](reports/2026-10-23-run-08-errors/report.md#f-048-with-the-server-unreachable-the-main-menu-gives-no-offline-indication) | minor | desktop-app | With the server unreachable, the main menu gives no offline indication | run 08, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-049](reports/2026-10-23-run-08-errors/report.md#f-049-the-upload-failure-message-is-terse) | minor | desktop-app | The upload failure message is terse | run 08, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-050](reports/2026-10-23-run-08-errors/report.md#f-050-sessions-shows-a-stale-status-after-an-upload-or-reprocess-until-the-screen-is-reopened) | minor | desktop-app | Sessions shows a stale status after an upload or reprocess, until the screen is reopened | run 08, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-051](reports/2026-10-23-run-08-errors/report.md#f-051-after-back-on-calibration-the-microphone-cant-be-opened-again-until-the-app-restarts) | major | desktop-app | After Back on calibration, the microphone can't be opened again until the app restarts | run 08, 10, 11 | fixed (2026-10-24), confirmed in runs 10 and 11 |
| [F-052](reports/2026-10-23-run-08-errors/report.md#f-052-crash-recovery-is-silent-the-examiner-isnt-told-about-the-interrupted-session) | major | desktop-app | Crash recovery is silent: the examiner isn't told about the interrupted session | run 08, 10, 11 | fixed (2026-10-24), confirmed in runs 10 and 11 |
| [F-053](reports/2026-10-23-run-08-errors/report.md#f-053-a-recovered-session-with-no-finished-take-processes-into-a-zip-with-no-clips-and-is-then-uploadable) | major | desktop-app | A recovered session with no finished take processes into a ZIP with no clips, and is then uploadable | run 08, 10, 11 | fixed (2026-10-24), confirmed in runs 10 and 11 |
| [F-054](reports/2026-10-23-run-08-errors/report.md#f-054-the-recovered-timelines-stop-event-has-the-recovery-time-not-the-crash-time) | minor | desktop-app | The recovered timeline's stop event has the recovery time, not the crash time | run 08, 10, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-055](reports/2026-10-24-run-10-fix-retest/report.md#f-055-the-crash-recovery-dialog-closes-when-the-examiner-clicks-outside-it) | question | desktop-app | The crash-recovery dialog closes when the examiner clicks outside it | run 10 | fixed (2026-10-25), not yet re-run |
| [F-056](reports/2026-10-24-run-10-fix-retest/report.md#f-056-a-hot-reload-window-resize-can-log-an-unhandled-recoverable-error) | question | environment | A hot-reload window resize can log an "unhandled recoverable error" | run 10 | open |
| [F-057](reports/2026-10-24-run-10-fix-retest/report.md#f-057-the-zero-clip-processing-failure-shows-a-generic-message-and-writes-no-log-line) | minor | desktop-app | The zero-clip processing failure shows a generic message and writes no log line | run 10, 11 | fixed (2026-10-24), confirmed in run 11 |
| [F-058](reports/2026-10-24-run-10-fix-retest/report.md#f-058-web-create-protocol-opens-protocolsundefined) | cosmetic | web-ui | Web "Create Protocol" opens `.../protocols/undefined` | run 10 | open |
| [F-059](reports/2026-10-24-run-10-fix-retest/report.md#f-059-the-video-recorder-logs-the-full-session-path-which-contains-the-patient-code) | minor | desktop-app | The video recorder logs the full session path, which contains the patient code | orchestrator 2026-10-24, run 11 | fixed together with F-060 (`5e7abd3`) |
| [F-060](reports/2026-10-24-run-11-minor-retest/report.md#f-060-video-remux-log-lines-carry-the-clip-name-which-holds-the-patient-code-and-installation-id) | minor | desktop-app | Video remux log lines carry the clip name, which holds the patient code and installation ID | run 11 | fixed (2026-10-24, `5e7abd3`; unit-tested with real ffmpeg, not yet re-run) |
| [F-061](reports/2026-10-24-run-11-minor-retest/report.md#f-061-the-camera-failure-text-is-drawn-over-the-frozen-preview-and-is-hard-to-read) | cosmetic | desktop-app | The camera-failure text is drawn over the frozen preview and is hard to read | run 11 | open |
| [F-062](reports/2026-10-24-run-11-minor-retest/report.md#f-062-a-device-picked-on-the-calibration-screen-isnt-remembered-the-system-default-can-be-a-silent-device) | question | desktop-app | A device picked on the calibration screen isn't remembered; the system default can be a silent device | run 11 | fixed (2026-10-25), not yet re-run |
| [F-063](reports/2026-10-24-run-11-minor-retest/report.md#f-063-at-1000820-save-and-go-back-in-settings-needs-a-short-scroll) | cosmetic | desktop-app | At 1000×820 "Save and go back" in Settings needs a short scroll | run 11 | open |
| [F-064](reports/2026-10-24-run-11-minor-retest/report.md#f-064-1-sessions-ready-to-upload) | cosmetic | desktop-app | "1 sessions ready to upload" | run 11 | open |
| [F-065](reports/2026-10-24-run-11-minor-retest/report.md#f-065-a-fresh-questionnaire-shows-red-validation-errors-before-any-input) | cosmetic | desktop-app | A fresh questionnaire shows red validation errors before any input | run 11 | open |
| [F-066](reports/2026-10-24-run-11-minor-retest/report.md#f-066-after-an-edit-from-sessions-accept-leaves-the-session-processed-with-the-old-zip) | major | desktop-app | After an edit from Sessions, Accept leaves the session "Processed" with the old ZIP | user report 2026-10-24 | fixed (2026-10-24), not yet re-run |
