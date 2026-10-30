# Run 01: Web setup (sites, projects, protocols)

- Date / time: 2026-10-23, ~23:50–00:05   Runner: Sonnet   App commit: `59bb063`
- Preconditions met: yes (preflight in [`preflight.md`](preflight.md))
- Result: passed with findings

The runner's tool could not write report files; the orchestrator saved this report from the
runner's returned text, and renumbered its run-local IDs (`F-01-n`) to global IDs. F-015 was
added by the orchestrator from the runner's notes.

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Log in as master, dashboard 📷 | pass | `screenshots/step-1-dashboard.png`; F-013 |
| 2 | Create the two projects | pass | Speech project id 13, Video project id 14; `step-2-projects-before.png`, `step-2-projects-after.png` |
| 3 | Create both protocols, EN and CS 📷 | pass with findings | `step-3-speech-protocol-editor.png`, `step-3-video-protocol-editor.png`; F-005, F-006, F-007, F-010, F-011, F-012 |
| 3a | Designer offers every flag the app needs | pass | See "Step 3 answers" |
| 3b | In-browser preview used once | pass | `step-3-preview-00.png` … `-10.png` |
| 3c | Standalone camera / `VIDEO` task exists? | pass (none exists) | Coverage gap per plan §4.2, not a defect |
| 4 | Sites A and B, projects assigned, tokens to secrets 📷 | pass with findings | Site A id 12, Site B id 13; `step-4-site-A-page.png`, `step-4-site-B-page.png` (token blurred); F-008, F-014 |
| 5 | `GET /api/site-config/<token A>` | pass with findings | HTTP 200; `logs/site-config-A.json` (token redacted); F-009, F-010 |
| 6 | `GET /api/site-config/<token B>` | pass | HTTP 200, speech protocol only; `logs/site-config-B.json` |

A site-list screenshot was deleted by the runner because it showed an 8-character token prefix.

## Step 3 answers

**Flags.** Every voice task offers Repetitions, Also record video, Allow repeat, Allow skip and
Show level indicator. Questionnaires offer Allow repeat and Allow skip only. Length is not one
uniform field:

- Phonation: Min and Max Duration.
- PATAKA and Picture description: Required Duration.
- Monologue and Retelling: Required Duration plus Min and Max.
- Reading: Min and Max Duration.

The config's `length` is `duration` if the task has one, else Min Duration, else Max Duration. So
Phonation's 5 s was set through Min Duration 5, and Reading gets `length: 150` (its Max Duration
default).

**Preview vs the desktop app.** "Show" opens `#/participant/test`. It shows things the app does
not:

- an audio-guide intro page,
- the Info page text,
- a participant-details page,
- a browser microphone-permission page before each task,
- the READING passage on screen,
- raw `{{example}}` placeholders and `[object Object]` in instruction cards (preview only; the
  site-config does not contain them),
- a "Responses saved" end page.

It does not show calibration, the level indicator, the example-audio button, Skip, Repeat or the
editor.

**Standalone camera / `VIDEO` task.** None. Record video exists only as a flag on voice tasks.
The available tasks are:

- Speech: phonation, syllable repetition, fairytale retelling, reading, monologue, picture
  description.
- Vision D-15.
- Thinking skills SDMT.
- Questionnaires.

The config generator silently skips the vision and cognitive tasks (not exercised).

## Step 5 mapping (site A)

Top level of the site A config:

- `schemaVersion` 1, `configVersion` `2026-10-23.215209`, `siteName` `E2E-1023-Site-A`.
- Languages: `defaultLanguage` en, `languages` `["cs","en"]`.
- Microphone: `defaultMicName` `""`, no `defaultMicGain`.
- Flags: `enableEditor` true, `indicatorType` CIRCLE, `useCalibration` true.
- Each protocol has an extra `version: 1` and a `descriptionKey`.

**Speech protocol** (project `E2E 1023 Speech project`). All tasks match the expected app tasks.
There is no INFO task (F-010).

| # | type / subtype | recordVideo | nrepetition | length | canSkip | canRepeat | showIndicator | Notes |
|---|---|---|---|---|---|---|---|---|
| 1 | VOCAL / PHONATION | false | 2 | 5 | false | true | true | `audioExamplePath` `…/audio/illustrations/phonation_a.wav` (HTTP 200, RIFF WAVE) |
| 2 | VOCAL / PATAKA | false | 1 | 5 | false | true | true | example audio `syllableRepeating_pataka.wav` |
| 3 | VOCAL / READING | false | 1 | 150 | false | true | false | F-009 |
| 4 | VOCAL / MONOLOGUE | false | 1 | 20 | true | true | false | |
| 5 | QUESTIONNAIRE | n/a | 1 | 300 (ignored) | false | true | n/a | Q1 OPEN `.+`, Q2 OPEN `.*`, Q3 SINGLE_CHOICE (2), Q4 MULTIPLE_CHOICE (3) |

**Video protocol** (project `E2E 1023 Video project`). All tasks match, and all have
`canSkip` false.

| # | type / subtype | recordVideo | nrepetition | length | canRepeat | showIndicator |
|---|---|---|---|---|---|---|
| 1 | VOCAL / PHONATION | true | 1 | 5 | true | true |
| 2 | VOCAL / MONOLOGUE | true | 2 | 15 | true | false |
| 3 | VOCAL / PATAKA | false | 1 | 5 | true | true |

**Patient fields** (both protocols, from the web's identifier catalogue, fixed regexes):

- `patient_code`: required, `[A-Za-z0-9_-]+`.
- `year_of_birth`: optional, `\d{4}`.

**File name template** (set by hand, see F-005 and F-006):
`${field.patient_code}_${installationId}_${taskIndex}_${task.subtype}_Rep${repetition}`.

**Strings.** The runner scripted a check of every `titleKey`, `instructionKeys`,
`questionTextKey`, `questionOptions`, field `labelKey`/`helpKey` and `descriptionKey`:

- 41 keys, none missing in cs or en.
- No `[object Object]`, `{{`, `XX` or legacy `_b` markup.
- The only empty values are the `year_of_birth` help strings.

**Site B.** `configVersion` `2026-10-23.215213`. Speech protocol only, with the same five tasks.

## Findings

### F-005: The web's default file-name template uses `${siteName}`, which the app doesn't substitute

- **Severity:** major
- **Area:** integration
- **Run / step:** run 01, step 3
- **Expected:** every variable the web offers in `recordingsFileName` is one the app renders
  (Task_Configuration_JSON_Spec, `recordingsFileName`; spec §8.8). Otherwise `ConfigValidator`
  rejects the config.
- **Actual:**
  - The web's default template is `${siteName}_${taskIndex}_…`, and its token picker offers
    `${siteName}`.
  - `RecordingsFileNameRenderer` (`app/src/main/kotlin/org/example/app/domain/config/RecordingsFileNameRenderer.kt:26-31`)
    substitutes only `field.*`, `installationId`, `taskIndex`, `task.subtype` and `repetition`.
    Clips would be named with a literal `${siteName}`.
  - The config validator accepts it, per the runner. The orchestrator did not check this.
- **Repro:**
  1. Create a protocol and keep the default file-name template.
  2. Assign it to a site.
  3. `GET /api/site-config/<token>` shows the template.
  4. Process a session in the app.
- **Evidence:** `screenshots/F-005-default-filename-template.png`; the renderer source above.
  The app path was not exercised: this run's protocols override the template.
- **Notes:** a web-side default problem, an app-side missing variable, or a validator gap. The
  spec decides which.
- **Status:** fixed 2026-10-24 in the app, confirmed in run 10 (`../2026-10-24-run-10-fix-retest/report.md`). The user decided the app should render
  the variable.
  - `RecordingsFileNameRenderer` now substitutes `${siteName}` with the config's `siteName`,
    sanitized like field values (empty when absent). The value is taken from the session's
    config snapshot, for both audio and video clips.
  - `ConfigValidator` rejects any other unknown `${...}` with `UnknownTemplateVariable`.
  - `Task_Configuration_JSON_Spec.md`, spec §3/§8.8 and `docs/dev/upload_data.md` list the
    variable.
  - The web default is unchanged.
  - Regression tests:
    - `RecordingsFileNameRendererTest`: "siteName is substituted and sanitized", "absent or blank
      siteName renders empty";
    - `ConfigValidatorTest`: "an unknown template variable is rejected", "every supported
      template variable is accepted, including siteName";
    - `ProcessSessionUseCaseTest`: "clips are named with the config snapshot's siteName for the
      siteName variable".

### F-006: "Create Protocol" makes a protocol with no patient identifiers and no patient code in its file name

- **Severity:** minor
- **Area:** web-ui
- **Run / step:** run 01, step 3
- **Expected:** a new protocol starts with the editor's defaults (patient identifier fields and a
  template with `${field.patient_code}`).
- **Actual:** the Create button sends only `{name, language, description}`, so the protocol has
  no patient fields and no patient code in its template. The runner added both by hand.
- **Repro:** Protocols → Create Protocol → open it in the editor.
- **Evidence:** none. It was seen while creating the protocols, before a screenshot was taken.
  The runner read it from the web UI's request payload.
- **Status:** fixed in the redesign (`376904b`, 2026-10-26); check in run 14. "Create and edit" builds a draft with a required `patient_code` field and the `${field.patient_code}_...` file name (`studyHelpers.js`, test `studyHelpers.test.js`). An API save without these fields still gets the server default.

### F-007: Boolean defaults are blank in the "Available Tasks" list

- **Severity:** cosmetic
- **Area:** web-ui
- **Run / step:** run 01, step 3
- **Actual:** in the left task list, the record video, repeat, skip and indicator defaults show
  as empty.
- **Evidence:** `screenshots/F-007-boolean-values-blank.png`
- **Status:** closed, gone in the current code (code check 2026-10-29). The old "Available Tasks" list was removed in the redesign; `TaskList.jsx` shows type labels only.

### F-008: Site names can't contain spaces, so the plan's naming doesn't work

- **Severity:** question
- **Area:** docs
- **Run / step:** run 01, step 4
- **Expected:** plan §4.1 and §4.2 name sites `E2E <MMDD> Site A`.
- **Actual:** the web rejects the name. Site names must be 1–32 characters of letters, digits,
  `_` or `-`. The sites were created as `E2E-1023-Site-A` and `E2E-1023-Site-B`.
- **Evidence:** `screenshots/F-008-site-name-with-spaces.png`
- **Notes:** later runs expect "Site: E2E-1023-Site-A". Either the plan changes, or the web is
  expected to allow display names; the spec decides which.
- **Status:** closed, gone in the web (2026-10-29). `isValidSiteName` allows spaces (clinic "E2E 1026 Clinic A"); `test-plan.md` §4.1 corrected the same day.

### F-009: READING has no passage in the config; the app would hide the "Start now" card instead

- **Severity:** minor
- **Area:** integration
- **Run / step:** run 01, step 5
- **Expected:** for `VOCAL/READING` the last instruction paragraph is the passage, which the app
  hides (project spec §4.1 READING rule).
- **Actual:** the web emits `instructionKeys` `[instr1, instr2]`, and `instr2` is the generic
  "Start now. Press START…" card, not a passage. The app will hide that card. Run 03 step 7
  ("passage not shown") would pass for the wrong reason.
- **Evidence:** `logs/site-config-A.json`, speech protocol task 3. No screenshot (API data).
- **Notes:** run 03 should check that only the first card shows and that "Start now" is missing.
- **Status:** fixed in the web repo (2026-10-29), not yet deployed or re-run. Decision (2026-10-29): the web sends the passage. Fix (web repo `task_protocoller_web_app` commit `44d8490`): every READING task gets one more instruction paragraph after the Start-now card: that language's topic text, else its topic label (cs/de carry labels only), else empty. So the app's "last paragraph is the passage" rule never drops Start now. Tests: `extConfig.test.js` READING cases (red before). Desktops apply it on their next fetch; `configVersion` does not change until a protocol or site row is saved.

### F-010: The designer's Info page and protocol Instructions aren't in the site-config

- **Severity:** question
- **Area:** integration
- **Run / step:** run 01, steps 3 and 5
- **Expected:** plan §4.2 expects an `INFO` task if the designer offers an intro.
- **Actual:** the designer has an "Info page" and protocol-level "Instructions" (rich text). The
  web preview shows them, but the site-config has no INFO task and no field for them.
- **Evidence:** `screenshots/F-010-info-page-not-emitted.png`, `logs/site-config-A.json`.
- **Notes:** run 03 step 4 will be skipped.
- **Status:** open

### F-011: Typo in the EN phonation instruction: "should least 5 seconds"

- **Severity:** cosmetic
- **Area:** web-ui
- **Run / step:** run 01, step 3
- **Actual:** "The phonation should least 5 seconds", in the preview and in config string
  `p13_t1_instr1`.
- **Evidence:** `screenshots/F-011-typo-should-least.png`
- **Status:** closed, gone in the current code (code check 2026-10-29). `frontend/src/i18n/en/tasks.json` now reads "should last at least ... seconds".

### F-012: CS questionnaire texts are a copy of the EN texts

- **Severity:** question
- **Area:** web-ui
- **Run / step:** run 01, step 3
- **Actual:** both language variants are saved from one editor session, so the CS questionnaire
  strings are English. The built-in task texts and patient field labels are proper Czech.
- **Evidence:** `logs/site-config-A.json` (cs strings of the QUESTIONNAIRE keys). No screenshot.
- **Notes:** this may be how the designer is meant to be used (translate per variant). Run 02
  will see English questionnaire text in CS.
- **Status:** open

### F-013: The dashboard is half Czech, half English with editor language Čeština

- **Severity:** cosmetic
- **Area:** web-ui
- **Run / step:** run 01, step 1
- **Evidence:** `screenshots/F-013-dashboard-partly-czech.png`
- **Status:** closed, gone in the current code (code check 2026-10-29). The old dashboard no longer exists; the redesigned shell has cs and de namespaces (and run 14 found no mixed languages).

### F-014: The site page counts language variants as protocols ("4 Inherited Protocols")

- **Severity:** cosmetic
- **Area:** web-ui
- **Run / step:** run 01, step 4
- **Actual:** site A shows "4 Inherited Protocols" for 2 protocols × 2 languages.
- **Evidence:** `screenshots/F-014-inherited-protocols-count.png`
- **Status:** closed, gone in the current code (code check 2026-10-29). No "Inherited Protocols" string or count exists any more.

### F-015: Plan §3.2.3 gives the wrong admin login path

- **Severity:** minor
- **Area:** docs
- **Run / step:** run 01, preflight 3.2.3
- **Expected:** `POST /api/admin/login` (plan §3.2.3).
- **Actual:** the endpoint is `/api/auth/admin/login`.
- **Evidence:** the runner's notes; the context file's `adminApi.loginPath`.
- **Status:** fixed in test plan 1.1 (2026-10-24): §3.2.3 now uses `/api/auth/admin/login`. Run 10
  logged in with it.

## Context updates

- Sites: A = id 12 `E2E-1023-Site-A` (speech and video projects), B = id 13 `E2E-1023-Site-B`
  (speech project). `configVersion` A `2026-10-23.215209`, B `2026-10-23.215213`.
- Projects: speech id 13, video id 14.
- Protocols: speech group 13 (EN 17, CS 18), video group 14 (EN 19, CS 20), with task summaries,
  patient fields and file-name template.
- `adminApi.loginPath` `/api/auth/admin/login`.
- Tokens in `2026-10-23-secrets.json` (gitignored). The orchestrator checked that no other report
  file contains them.
- Runner's Playwright helpers (`lib.py`) and admin JWT are in the orchestrator scratchpad under
  `e2e\`.
