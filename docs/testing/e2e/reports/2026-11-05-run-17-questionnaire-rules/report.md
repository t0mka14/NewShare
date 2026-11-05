# Run 17: questionnaire rules (`showIf`) and formatting, live E2E

- **Spec:** [`SPEC-questionnaire-rules`](../../../../SPEC-questionnaire-rules.md), criteria 1–9.
- **Date:** 2026-11-05.
- **Runner:** Sonnet. The orchestrator saved this report from the runner's hand-back.
- **App commit:** `f13e5c7` plus the uncommitted `showIf` and formatting work (hotRun of the working tree).
- **Web:** container `share_web`, commit `89a805f` deployed.
- **Driving the app:** the `compose-hot-reload` MCP server was not connected, so the runner used OS clicks (`tools/Drag-InWindow.ps1`), `SendInput` for typing and `tools/Capture-Screen.ps1`. Scripts are in `scripts/run-17/`.
- **Result:** **passed** for everything exercised live, from the web editor through `/site-config` and the real app to upload, the server ZIP and the web detail page. No app defect. Three low-severity findings: F-17-1 (question), F-17-2 (environment), F-17-3 (tooling).

## Orchestrator's check of this report

Checked independently of the runner:
- `site-config-paris.json`: `showIf` on exactly Q5–Q8, with the expected source and option keys.
- `examination.redacted.json`: answers for Q1–Q5 only.
- Screenshots B5, B8, B20 and B24 viewed: order, indentation, frame and scrollbar as reported.
- `settings.json` and `config.json` compared byte for byte with the backup: identical.
- Leak scan: 417 files, 0 hits. Its needles do not appear to include the seeded Paris token, which `scripts/run-17/` holds in plain text (`a3_site_config.py`, `set_paris_token.py`). That token is also published in the test plan.
- The database has 1 protocol task with a rule, the protocol created by this run.

Corrections to the runner's hand-back: `screenshots/` holds 68 files, not about 80.

## Steps

Evidence is in `screenshots/` unless noted.

| # | Step | Result | Evidence |
|---|---|---|---|
| 1 | Create the protocol in the web editor (real UI login, EN) | pass | A1-*, A1-modal-q1..q8. Created in study 1 "Test Study 001". The editor's defaults left every question non-optional. |
| 2 | Save, reload, rules persisted | pass | A2-after-reload, A2-reloaded-modal-full, A2-reloaded-q5..q8. Q5 = Q2 in {Yes, Maybe}, Q6 = Q3 in {A}, Q7 = Q2 in {Yes}, Q8 = Q7 in {Yes}. |
| 3 | `/api/site-config/<token:Paris>` | pass | `site-config-paris.json`. `showIf` on exactly Q5–Q8; absent, not null, on Q1–Q4. `configVersion` went from 2026-10-29.134904 to 2026-11-05.221421. |
| 4 | Pre-checks, backup, token | pass, with a deviation (F-17-2) | No `main.pid`. Backups taken first. Paris token set while the app was stopped. |
| 5 | Start the app, open the protocol | pass | B0, B1-protocol-choice, B2-patient-form. Fake patient code `E2E17-FAKE`. No audio task, so no microphone or calibration needed. |
| 6a | Fresh task: only Q1–Q4, no errors, frame, dividers | pass | B3, B4 |
| 6b | Case 1: Q1, Q2=No, Q4, then Finish | pass | C1–C3, in a separate session. Local `examination.json` had keys Q1, Q2, Q3 (empty list) and Q4 only. |
| 6c | Case 2: Q2=Yes | pass | B5: Q5 and Q7 directly under Q2, indented, above Q3. B6 (top, mid, bottom): Finish blocked, errors on Q1, Q5, Q7, Q4. B7: Q1 and Q5 filled. |
| 6d | Chain | pass | B8: Q7=Yes shows Q8 under Q7, one level further in (x=92 vs 68). Its error shows at once because a submit had been attempted. |
| 6e | Case 4, multiple choice | pass | B10: B only, Q6 hidden. B11: A and B, Q6 under Q3, indented. B12: A unticked, Q6 hidden. B13–B13c: Q6=Up cleared and back empty. |
| 6f | Cases 3, 5, 6 | pass | B16, B16b: Q2=No hides Q5, Q7, Q8. B17, B17b: Q2=Yes, Q5 and Q7 back empty, Q8 hidden. B18: Q7=Yes, Q8 back empty. Reappearing required questions show errors after the earlier submit attempt (allowed by brief case 6). |
| 6g | Scrolling, resize, frame | pass, partly limited | B19, B20, B20b at 960x720; B4, B9 at 1000x820. Heights below 960x720 can't be reached: `Main.kt` sets a minimum (B21). 1000x820 restored (B22). |
| 6h | Frame stability | pass | At 1000x820 the frame stays at y=136–646 with 4 or 7 questions visible; at 960x720, y=136–546. |
| 7 | Final state, Finish, upload | pass | B23, B24: Q1, Q2=Maybe, Q3=B+C, Q4, Q5 answered; Q6–Q8 hidden after Q7 and Q8 had been answered. B25, B28, B29: upload HTTP 200. |
| 8 | Server ZIP and web detail | pass | `examination.redacted.json`: answers for Q1–Q5 only, no keys for Q6–Q8. Status `valid`. C4-web-recording-detail: "Show answers (5)" lists Q1–Q5 with readable values. |
| 9 | App log | pass | `logs/app-log-excerpt.log`: 0 `showIf` or "invalid" lines, 0 exceptions, 3 WARN lines, all F-17-2. |
| 10 | Clean-up | pass | See [Restoration](#restoration). |

## Findings

### F-17-1: a non-optional multiple choice doesn't block when unanswered

- **Severity:** question, low.
- **Observed:** the web editor leaves "Optional" unticked by default, so Q3 reads as required. In the app, an unanswered Q3 shows no error (B6 bottom) and Finish is accepted (C3), stored as `"q3": []`.
- **Why it isn't a `showIf` defect:** this matches §4.2.1, where `MULTIPLE_CHOICE` accepts none.
- **Decision (user, 2026-11-05):** it stays as it is. A non-optional multiple choice does not block Finish.

### F-17-2: this machine's device is registered to another clinic

- **Severity:** environment, low.
- **Observed:** the existing installation ID belongs to device 21, E2E 1026 Clinic A. The first start with the Paris token logged `device check-in: HTTP 409` and "config from another site not applied" (`logs/hotrun-1-device-409.log`).
- **Workaround:** with the app stopped, `drop_installation_id.py` removed `installationId` from `settings.json`. The app generated a new one and registered as new Paris device 89. The original settings were restored afterwards.

### F-17-3: synthetic mouse wheel doesn't scroll the questionnaire pane

- **Severity:** tooling, low.
- **Observed:** `mouse_event` WHEEL (`wheel.ps1`) did nothing under RDP. Scrolling was done by dragging the scrollbar thumb.

### Observations, not defects

- The divider above an indented question spans the full width, by design.
- `Drag-InWindow.ps1` needs `-ToX` and `-ToY` for a plain click: `-ToX` defaults to 0, so leaving it out makes a drag.

## Left on the server (all fake data)

- Protocol "E2E run 17 questionnaire rules", `#/studies/1/protocols/35`, v1, in Test Study 001. **It is served to all clinics of that study, not only Paris.** `configVersion` is now 2026-11-05.221421.
- Session `d8880a615d564a77b4549ec598d6976a`, patient code `E2E17-FAKE`, valid, Paris.
- New Paris computer, device 89, no label (F-17-2).

Session B (case 1, `E2E17-FAKE-B`) was never uploaded.

## Restoration

- The app is stopped, and `main.pid` is gone.
- `app/data/config/settings.json` and `config.json` are restored and byte-identical to the backup.
- The Upload screen uploads every ready session, so the 19 older session folders were moved out for the run and moved back afterwards.
- `app/data/sessions` now also holds the uploaded `2026-11-05_E2E17-FAKE_d8880a61…`. `app/data/logs/app.log` was appended to.
- **An older session was changed (found by the independent critic, missing from the runner's hand-back):**
  - The first app start ran normal startup recovery on the interrupted session `2026-10-31_KD206_f2ffacfa…` (`logs/app-log-excerpt.log`, first line). This happened at 23:18.
  - It wrote `examination.json` and `timeline_original.json`, and `master/session_master.wav` also has that modified time.
  - No backup of that session was taken, so this can't be undone. Any later app start would have run the same recovery.
  - The session was not uploaded in this run: the log shows one upload batch of one session, `d8880a61…`.
- Session B was moved out of `app/data/sessions` to the session scratchpad, so it can't be uploaded by accident.

## Known issues left open

From the two independent critics. All need a hand-edited config; the web can't emit them. The user chose on 2026-11-06 to leave the duplicate-key problem as it is. These are not recorded in `findings.md`.

- **Duplicate `questionKey`:** copies share one answer, so Finish can pass with a required question empty or an answer lost. Mostly older than this change.
- **`questionRegex: "["`:** crashes task entry, even for a hidden question. Older than this change.
- **Required `SINGLE_CHOICE` with no options:** can never be answered. Older than this change.
- **Non-string `questionKey` on a question:** rejects the whole config; the previous config stays in use. Older than this change.
- **Bad-rule warnings:** repeat up to 3 times per launch. Log noise only.

## Not verified live

This run predates the fixes for malformed `showIf` values and duplicate keys. Those fixes add a serializer that also reads valid configs, and it is covered by tests with the web sample fixture, not by a live run.


- Real mouse-wheel scrolling (F-17-3).
- Window heights below 960x720, because of the window minimum.
- A warning for an invalid `showIf` (brief case 7): unit tests only.
- Brief case 8 (older app build): covered by decoding the new fixture with the old code, not by a live run.
