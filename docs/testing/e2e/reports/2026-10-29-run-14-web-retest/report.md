# Run 14: Targeted re-test of web fix round 3 (web commit `44d8490`)

- **Date / time:** 2026-10-29, about 06:00–06:20 local (CET, UTC+1).
- **Runner:** Sonnet, Playwright (headless=False) plus API calls. Scripts in `scripts/run-14/`.
- **Target:** shared test container http://192.168.122.183:10001, deployed from web commit
  `44d8490` the same morning.
- **Spec:** [fix-round-2026-10-29-web-spec](../../fix-round-2026-10-29-web-spec.md), criterion 14.
- **Result:** **passed**. Fourteen of the fifteen findings re-tested are confirmed fixed; F-110 is
  not covered. One minor and one cosmetic new finding (F-14-1, F-14-2). No major.
- **Created:** throw-away study `E2E 1026 run14 study` (id 7) with one protocol,
  `E2E 1026 run14 protocol` (id 28). The study is archived. Nothing deleted.

The orchestrator saved this report from the runner's hand-back. Before saving, it removed one
unredacted screenshot (`12-1-recording-05-1.png`, which showed an installation-id prefix).

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | F-067: the protocol list shows "Last changed" correctly; the API sends `...Z`; Library likewise | pass | Saved protocol 28 at 05:05:11Z (06:05 local): the list says "now", 9 minutes later "9 minutes ago". `GET /projects/7/protocols` gives `updated_at` and `created_at` `2026-10-29T05:05:11Z`. Study 3 protocols and `GET /library` give `...Z`. `1-2`, `1-5`, `1-4`. |
| 2 | F-069: no "Auto-created" text | pass | Protocol 28 (empty description) has `description: null` and the list shows no placeholder (`2-1`). The site-config JSON for Clinic A (`<token:E2E 1026 Clinic A>`) is 17 KB and holds no "Auto-created". |
| 3 | F-086: `POST /api/protocols` with a task lacking `task_id`, master, with Origin header | pass | `400 {"error":"task_invalid","index":0}`. Nothing created. |
| 4 | F-009: the READING instruction keys in the site-config JSON | pass | See [step 4](#step-4-f-009-keys). |
| 5 | F-110 | not covered | See the findings table. |
| 6 | F-097 as the editor persona (Clinic B only) and as master | pass | See [step 6](#step-6-f-097). |
| 7 | F-096 as the received persona on `#/clinics/4/studies` | pass | No switch on the rows (0 `[role=switch]`); each row shows "Received" and "Managed by its editors. You can review its recordings." Master on the same page sees `clinic-studies-switch-3` and `-4` and the label "Yours". `7-received-clinic4-studies`, `7-master-clinic4-studies`. |
| 8 | F-087: Clinic A, Computers, "Config" column | pass | Column header "Config" (cs "Konfigurace", de "Konfiguration"); value `2026-10-26.172809` on both computers; the cell's `title` is the fetch time ("Oct 29, 2026, 01:32 AM" and "Oct 28, 2026, 07:23 PM"). Installation ids redacted as `<installationId>`; the token "Show" button was not pressed. `8-clinic4-computers-en/cs/de`. |
| 9 | F-074: 1024x768 | pass | See [step 9](#step-9-f-074). Cosmetic note: F-14-2. |
| 10 | F-079: direct and reloaded preview URL | pass | `#/studies/7/protocols/28/preview` in a fresh tab renders the preview ("Audio Guide ..."), and after reload too. `#/studies/7/protocols/new/preview` lands on `#/studies/7/protocols` (direct and after reload). `10-preview_*`. |
| 11a | F-068: save-confirm dialog, cancelled | pass | EN: "Saving changes this protocol for every clinic that runs it. Computers get the new version the next time they fetch their config."; CS and DE equivalent; no "unique links". After Cancel nothing changed (API `updated_at` unchanged). `11-F068-confirm-en/cs/de`. |
| 11b | F-073: min 70 > max 60 in the task modal | pass | The warning "Minimum duration is greater than maximum duration." shows (role=alert), Save stays enabled; it disappears when min is fixed. CS "Minimální délka je větší než maximální délka.", DE "Die Mindestdauer ist größer als die Höchstdauer."; Save enabled in both. `11-F073-*`, `14-cs/de-duration-warning`. |
| 11c | F-076: the custom field editor hint | pass | "Shown as typed in every language." (cs "Zobrazí se ve všech jazycích tak, jak je zadáno.", de "Wird in allen Sprachen so angezeigt, wie eingegeben."). `11-F076-custom-field-hint`, `14-cs/de-F076-hint`. Not saved. Side observation: F-14-1. |
| 12 | F-037, F-111, F-036, F-035 on recordings with video | pass | See [step 12](#step-12-clip-and-camera-tile). |
| 13 | F-006: "Create and edit" in the throw-away study | pass | See [step 13](#step-13-f-006). |
| 14 | i18n spot check, CS and DE | pass | See [step 14](#step-14-i18n). |
| 15 | End state | pass | Study 7 archived (`POST /projects/7/archive` 200; it no longer appears in the active list). Nothing deleted. |

### Step 4: F-009 keys

Clinic A's config holds two protocols: Speech (id 3, v2) and Video. Only the Speech protocol has a
READING task (`p1_t3`). It has 3 `instructionKeys`:

| key | en | cs |
|---|---|---|
| `p1_t3_instr1` | `<bold>Now I will ask you to read a short passage of text. If...` (250 chars) | `<bold>Nyní Vás poprosím o přečtení krátkého úryvku textu. Po...` (240) |
| `p1_t3_instr2` (second to last, "Start now") | `<bold>Start now.</bold>\n\n<italic>Press the START button to b...` (179) | `<bold>Začněte teď.</bold>\n\n<italic>Stiskněte tlačítko START ...` (186) |
| `p1_t3_instr3` (last, passage) | `The North Wind and the Sun were disputing which was the stro...` (599, the topic text) | `Severní vítr a Slunce` (21, the topic name) |

All other VOCAL tasks (Speech and Video) keep 2 keys. The desktop "Start now" card was not
re-checked here. Log: `logs/f009-keys.txt`.

### Step 6: F-097

- **Editor** (Clinic B only), study 3:
  - study page: "Shared with 2 clinics, 1 visible to you";
  - Clinics tab: "2 (1 visible)";
  - Studies list: "2 clinics (1 visible)";
  - cs: "Sdílená se 2 klinikami, pro vás viditelné: 1", "Kliniky 2 (viditelné: 1)";
  - de: "Mit 2 Kliniken geteilt, 1 für Sie sichtbar", "2 (1 sichtbar)".
- **Master:** "Shared with 2 clinics", "2 clinics", with no "visible" text.
- **API:**
  - the editor's `/api/projects` carries study 3 with `site_count` 2 and `visible_site_count` 1;
  - master has 2 and 2;
  - no `count_sites` key for any row, for either persona.
- Screenshots `6-*`.

### Step 9: F-074

- **Clinics at 1024x768:**
  - `documentElement.scrollWidth` 1024 = `clientWidth` 1024, so there is no horizontal page scroll;
  - the table is 1033 px wide inside a `.ui-tbl-wrap` that is 690 px wide and has
    `overflow-x: auto`, and the wrapper's box equals the card's box (284 to 976), so the table
    scrolls inside its card.
- **Row menu:**
  - the Clinics list has no row menu;
  - the Computers and study-protocols row menus were used instead;
  - the last Computers row's menu sits at x 756–959 and y 654–734, inside the 1024x768 viewport.
- **Sticky header** at 1440x420, Recordings (a list that fits, wrapper `overflow: visible`): the
  `th` is at top 0 after a 400 px scroll. `9-4-sticky-1440-recordings`.
- Screenshots `9-*`.

### Step 12: clip and camera tile

- **Recording E2E1026-05-3** (session `d3b229fa`, a video clip on the Phonation row, camera
  notice):
  - F-037: pressing the video button shows "This clip can't play in the browser. Download to
    play." with "Download clip" and "Close video" within 0.1 s, with no wait or retry. `12-3-video-0`.
  - F-111: under "Camera: OBS Virtual Camera" the notice "The camera could not capture video." sits
    on its own lines, with a "Details" expander below ("capture interrupted: the capture process
    ended"). `12-2-recording-d3b229fa-session`, `12-4-camera-details-open`.
- **F-036:** the notice is a short sentence, not raw ffmpeg output.
- **F-035:** none of the 8 recordings shows an input gain line, and "undefined" appears on none of
  the 8 pages (all details expanded). The old text can't be seen, but it is not there.
- **CS and DE:** "Tento klip nelze přehrát v prohlížeči. Pro přehrání jej stáhněte." and "Kamera
  nedokázala zachytit video."; "Dieser Clip lässt sich im Browser nicht abspielen. Zum Abspielen
  herunterladen." and "Die Kamera konnte kein Video aufnehmen." `14-recording-05-3-cs/de`.

### Step 13: F-006

"Create and edit" opens `#/studies/7/protocols/new` with "Participant code · required" (field
`patient_code`, placeholder HC001) and the file name template
`${field.patient_code}_${siteName}_${taskIndex}_${task.subtype}_Rep${repetition}` (example
`HC001_CLINIC-01_03_PHONATION_Rep1.wav`). The modal text reads "It starts with a patient code field
and a standard file name." `13-2`, `13-4`, `13-5`. The protocol was then saved once, with one
phonation task, as protocol 28 (it feeds steps 1, 2, 10 and 11).

### Step 14: i18n

CS and DE were scanned for "undefined" and raw keys on:
- Clinics;
- Clinic A Computers (Config column);
- Studies list and study 3 as the editor (visible counts);
- study 3 Clinics tab as master;
- the throw-away study's protocol list;
- Recordings;
- recording 05-3;
- the protocol editor (duration warning, custom-field hint).

Result: no "undefined" and no raw key. The only dotted hit is the literal template text
`${task.subtype}`. The "Video partial" strings and the `task_invalid` toast were not shown (no
record, no UI path). `14-*`.

## Previously fixed findings

| Finding | Result | Evidence |
|---|---|---|
| F-067 times shifted by the UTC offset | **confirmed fixed** | Step 1; API `...Z`; list "now", then "9 minutes ago"; library `...Z`; Computers "Last seen 5 hours ago" agrees with its tooltip 01:32 AM. |
| F-069 default description text | **confirmed fixed** | Step 2. No existing row carries the placeholder in Clinic A's config, so the legacy-row filter wasn't exercised. |
| F-086 partial POST gives 500 | **confirmed fixed** | Step 3: 400 `task_invalid`. |
| F-009 READING passage | **confirmed fixed** (live JSON) | Step 4. cs sends the topic name (the accepted deviation). The desktop card isn't re-checked. |
| F-110 short video not shown | **not covered** | Needs an upload with a partial video; none of the 8 existing records shows the "Video partial" badge. Unit tests are the evidence. |
| F-097 clinic count | **confirmed fixed** | Step 6. |
| F-096 runs-at switches | **confirmed fixed** | Step 7. |
| F-087 config version | **confirmed fixed** | Step 8. |
| F-074 Clinics table overflow | **confirmed fixed** | Step 9. See F-14-2 for the sticky-header caveat. |
| F-079 direct preview link | **confirmed fixed** | Step 10. |
| F-068 confirm text | **confirmed fixed** | Step 11a, in EN, CS and DE. |
| F-073 duration warning | **confirmed fixed** | Step 11b. |
| F-076 custom label hint | **confirmed fixed** (hint present) | Step 11c. Not saved. |
| F-037 / F-111 / F-036 | **confirmed fixed** | Step 12. |
| F-035 gain "undefined" | **confirmed fixed** | Step 12: no gain line and no "undefined" on any record. |
| F-006 patient_code | **confirmed fixed** | Step 13. |

## Findings

### F-14-1: Cancelling the patient-field editor keeps a blank custom field
Index ID: **F-137**
- **Severity:** minor
- **Area:** web-ui
- **Run / step:** run 14, step 13
- **Expected:** Cancel discards what was added in the modal, so the protocol settings look as
  before.
- **Actual:** In a new protocol, open "Edit fields", press "Add custom field", press Cancel. The
  settings now show an empty chip beside "Participant code · required" and the red text "Identifier
  names must be unique, lower-case (a-z, 0-9, _), and custom fields need a label.". The blank field
  stays in the editor state. The runner didn't check whether Save is blocked by it (it was disabled
  anyway because the protocol had no task), and doesn't know whether this predates the fix round.
- **Repro:**
  1. `#/studies/<id>/protocols`, "New protocol", "Create and edit".
  2. "Edit fields", "Add custom field".
  3. "Cancel".
- **Evidence:** `screenshots/13-6-after-cancel-custom-field.png`; `scripts/run-14/c13c.py` prints
  the settings text before and after.
- **Status:** fixed (2026-10-29, web `69f956e`), confirmed in run 14b.
  - **Cause:** the identifiers dialog edited the live protocol, and its Cancel, Esc and backdrop close
    only closed it.
  - **Fix:** the hook `useSnapshotModal` snapshots the fields a dialog writes when it opens, and puts
    them back on every close except Save. Save stays blocked while a field is invalid.
  - **Tests** (both failed before the fix):
    - `editor.test.jsx` "identifiers modal draft (F-137)", 3 cases;
    - Playwright `protocol-manager.spec.ts` "cancelling the participant-fields editor discards a
      just-added custom field (F-137)".

### F-14-2: At 1024 px the sticky table header does not stick on overflowing tables
Index ID: **F-138**
- **Severity:** cosmetic
- **Area:** web-ui
- **Run / step:** run 14, step 9
- **Expected (criterion 9):** the header stays visible when the page scrolls, "on a list that fits".
- **Actual:**
  - Where the table fits (1440 px, Recordings) the header sticks (top 0 after 400 px of scroll).
  - At 1024x420, Recordings, whose wrapper has `overflow: auto` because the table is wider than the
    card, the header scrolls away (top -42 at scrollY 400). Clinics and Users behave the same.
  - This follows from the F-074 fix: `overflow-x` on the wrapper makes it the sticky container.
    It matches the criterion's wording and the trade-off recorded in the spec's review outcome, so
    it is recorded as a cosmetic note rather than a regression.
- **Repro:** Recordings at 1024x420, scroll the page by 400 px.
- **Evidence:** `screenshots/9-4-sticky-1024-recordings.png`, `9-4-sticky-1440-recordings.png`;
  `scripts/run-14/c9b.py`.
- **Status:** closed, accepted (2026-10-29). The trade-off accepted for F-074: only tables wider than their card scroll sideways and lose the sticky header.

### F-139: Cancelling the info page or instructions editor keeps the typed text
Index ID: **F-139**
- **Severity:** minor
- **Area:** web-ui
- **Run / step:** found by the engineer while fixing F-137 (2026-10-29)
- **Expected:** Cancel, Esc or a backdrop click discards text typed or a template loaded in the info
  page and instructions dialogs.
- **Actual:** both dialogs wrote straight into the protocol (`updateProtocolField`) and their close
  only closed them, so cancelled text stayed and the protocol became dirty.
- **Status:** fixed (2026-10-29, web `69f956e`), confirmed in run 14b. The fix is the same
  `useSnapshotModal` hook as F-137 (keys `info_text`, `instructions_text`). Tests: `editor.test.jsx`
  multi-key case, Playwright `protocol-manager.spec.ts` "cancelling the info page editor ..." and
  "cancelling the instructions editor ..." (red before: the add button stayed hidden because the
  cancelled text was kept).

No new finding of major severity or above.

## Follow-up: run 14b (2026-10-29, about 06:40)

A short live check after deploying web commit `69f956e` (frontend only; the served bundle was
`index-aZA6QrXk.js`, the same as the local build). Playwright, headless, 1440x900, EN, study 3,
protocol 3. Nothing was saved. Scripts are in `scripts/run-14b/` and screenshots are `14b-*`.

| Check | Result | Evidence |
|---|---|---|
| F-137: Add custom field, then Cancel | pass | No empty chip or red text afterwards, and Save stays disabled. `14b-1-4-after-cancel` |
| F-137: toggle "required", then Esc, then reopen | pass | The original state is back. `14b-1-5` to `14b-1-8` |
| F-139: info page, type text, then Cancel | pass | The row is unchanged, Save is disabled, and the dirty note is gone. `14b-2-info-*` |
| F-139: instructions, type text, then Cancel | pass | "Add Instructions" is still shown and Save is disabled. `14b-2-instr-*` |
| API: protocol 3 before and after | pass | `updated_at`, `version`, `info_text` and `instructions_text` are identical. |

**Note, not a finding:** while a dialog is open, the page behind it already shows the draft (the
empty chip and its validation text, "Unsaved changes", and an enabled Save). The dialogs still edit
the live protocol and are reverted on Cancel, by design of this fix. Everything goes back on Cancel
or Esc.

## Context updates

- Throw-away study `E2E 1026 run14 study` (id 7) is **archived**; it holds protocol id 28
  `E2E 1026 run14 protocol` (v1, EN, one phonation task, description empty).
- No clinics, users, recordings or library entries were created or changed. The persona passwords
  (1234) still work for all five personas.
- Clinic A's config version is still `2026-10-26.172809`; both computers show it in the new Config
  column.
- The two Clinic A computers are shown as `<installationId>`; their last-seen tooltips are Oct 29
  01:32 AM and Oct 28 07:23 PM.
- F-110 stays open for a live check until a partial-video upload exists.
