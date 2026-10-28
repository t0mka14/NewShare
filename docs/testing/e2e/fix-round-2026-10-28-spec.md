# Fix round 1 (2026-10-28): E2E majors F-123, F-124/125/126 + F-093, F-091

## Context
The 2026-10-26/28 E2E run left 6 open majors. This round fixes the 5 desktop-app ones, plus F-093,
which shares a cause with them. F-077 is an environment issue and is out of scope. We use the
e2e-fix-workflow:
- one Sonnet engineer agent per fix, one at a time;
- I review the diff and run `./gradlew.bat :app:test`;
- I record the fix in `findings.md` and the run report;
- a targeted Sonnet E2E re-test at the end.

Decisions the user made:
- majors first;
- a minimum window size that fits 1024×768 screens (not 1000×820);
- a take that crosses an interruption is dropped and the rest of the session is kept;
- the editor buttons are fixed;
- a targeted re-test.

## Spec

**Goal:** sessions with a microphone interruption can be uploaded again, and no screen loses a
button at any allowed window size.

**Deliverables:**
- code and tests in `app/`;
- updated `docs/testing/e2e/findings.md` and the run reports;
- a re-test report in `docs/testing/e2e/reports/2026-10-28-run-12-fix-retest/`;
- this spec copied to `docs/testing/e2e/fix-round-2026-10-28-spec.md`.

### Acceptance criteria (each one checkable)
1. **F-123 (path):** a 2-part session whose `partFile` is the bare name the writer stores
   (`session_master.part2.wav`) processes into a ZIP.
   - Each clip is cut from its own part.
   - The archived master is the two parts joined.
   - Shown by a real-I/O integration test that **fails before the fix**.
   - Legacy values like `master/session_master.part2.wav` still resolve.
2. **F-123 (crossing take):** a take whose range crosses a part boundary is skipped, not fatal.
   - The session ends `Processed` with every other clip.
   - The skipped take is logged (no patient code) and recorded in `examination.json`, using an
     existing field if one fits.
   - Unit test.
3. **F-123 (logging):** `processing failed: IoFailure` logs the exception type and the file *name*,
   with no session path or patient code (the F-059 rule).
   - Unit test, or the existing log-redaction test extended.
4. **Minimum window:** `Main.kt` sets a minimum window size of **960×720**, so a 1024×768 screen
   fits it. The app still opens at 1000×820.
5. **F-124, F-126:** at 960×720, every control on Sessions and Upload is displayed without
   clipping.
   - Back, Go to upload and Upload must be above 10 dp in height.
   - Each screen gets a `runDesktopComposeUiTest(960, 720)` test.
6. **F-125, F-093:** at 960×720 and at 1000×820, the editor shows Previous, Play, Reset, Next,
   Discard and Accept. Each label is on one line (the button is one line high) and nothing
   overlaps. Covered by a test.
7. **F-091:** at 960×720 and at 1000×820, the last option of a multiple-choice question can be
   reached and clicked. Covered by a test.
8. **No other screen regresses at 960×720:**
   - main menu, settings, protocol picker, patient form, calibration, speech task, video task,
     questionnaire;
   - guard tests at 960×720 use the `SettingsLayoutUiTest` pattern: reachable, either directly
     or by `performScrollTo`.
9. `./gradlew.bat :app:test` passes after each increment.
10. **Targeted E2E re-test** (Sonnet runner) shows all of the following:
    - the kept session `E2E1026-09-2` reprocesses into a ZIP, which is not uploaded;
    - a new interruption + Resume video session processes;
    - the window can't be shrunk below 960×720;
    - Sessions, Editor, Upload and the questionnaire pass C1–C4 at 960×720, 1024×768 and
      1000×820;
    - no new findings at major level or above.

### Verification plan
- **Criteria 1–9:**
  - I read each diff.
  - I confirm that the new tests fail on the old code: the engineer shows the red run first,
    or I check it with `git stash`.
  - I run `./gradlew.bat :app:test`.
- **Criterion 10:**
  - The runner works through the compose-hot-reload MCP, using the semantic-tree bounds and
    screenshots.
  - For the ZIP, the runner reads `examination.json` and the clip list.
  - For the window minimum, it calls `resize_window` to 640×480 and checks the reported size.
- **Critic:** I run `/verify` at the end against these criteria.

### Assumptions (any of these can be vetoed)
- 960×720 is the minimum, in dp, at 100 % scaling. On a 1024×768 screen the taskbar leaves about
  728 px, so 720 fits.
- Below the minimum nothing needs to work. Once the minimum holds, F-118 is fixed, and
  F-119/F-120/F-121 are closed as "not reachable". Their sizes are below the minimum.
- Sessions and Upload keep their fixed-height lists, made shorter or `heightIn` so they fit at
  720. No page scroll is needed there, except as a fallback the engineer documents.
- The 31 ms offset mismatch between `Interruption.sampleOffset` and the interruption events is
  not fixed here. It becomes new finding **F-129** (minor), and only the index row is added.
- Session 09-2 in `app/data` is reprocessed during the re-test, which changes its local files.
  It is not uploaded.
- Nothing is committed until the user asks. The E2E docs from runs 06–10 are still uncommitted.

### Key decisions
- **F-123:** fix the reader (`MasterPartMap` resolves the part against the master folder), not
  the writer. `RecoverSessionsUseCase.kt:160` matches part files by bare name, and
  `Examination.kt:37` documents a bare file name.
- **Layout:** a minimum window size plus content that fits at that size. Pinned buttons with a
  page scroll were the alternative; the user rejected it.
- **Fix order:** F-123 first (data loss), then the layout fixes.

## Increments

### 1. F-123 (domain-engineer, Sonnet)
Files:
- `domain/session/MasterPartMap.kt:57`: resolve against `defaultMasterFile.parent`, using the
  file name; a path already carrying the `master/` prefix still works.
- `ClipExportPlanner.kt:102-105` and `ProcessSessionUseCase.kt:179`: a `SpansPartBoundary`
  error skips the take instead of failing the session.
- `ProcessSessionUseCase.kt:92`, `fail()`: log the detail, redacted.

Tests:
- `ProcessSessionUseCaseIntegrationTest.kt:157` gets a variant using the bare name.
- Update the tests that hand-write `master/...` paths: `MasterPartMapTest`,
  `ClipExportPlannerTest:136`, `ProcessSessionUseCaseTest:267`.

**Checkpoint:** I show you the diff summary, the red-then-green tests, and `:app:test` green.

### 2. Minimum size + Sessions/Upload fit (ui-engineer, Sonnet)
Files:
- `Main.kt:97-106`: `window.minimumSize`.
- `SessionBrowserContent.kt:63-67`: the 360 dp list.
- `UploadContent.kt:72-77`: the 220 dp list.

Tests:
- new `SessionBrowserLayoutUiTest` and `UploadLayoutUiTest` at 960×720, with setup copied from
  `NewScreensSmokeTest.kt:240-291`;
- the 960×720 guard tests from criterion 8.

**Checkpoint:** diff, tests, green.

### 3. Editor buttons F-125/F-093 (ui-engineer, Sonnet)
File: `EditorContent.kt`.
- `EditorPanel`, with its minimum heights at :217/:224 lowered or made adaptive so the controls
  fit at 720.
- `EditorControls` at :484-546: `softWrap = false` / `maxLines = 1`, and the row wraps or
  shrinks spacing instead of squeezing the labels.

Test: `EditorLayoutUiTest` at 960×720 and 1000×820, with setup from `NewScreensSmokeTest.kt:61-171`.

**Checkpoint:** diff, tests, green.

### 4. F-091, last multiple-choice option (ui-engineer, Sonnet)
File: `TaskContent.kt:115-121, 156-159`.
- Reproduce with a test at 1000×820 using the run-03 questionnaire shape (4 questions, the last
  one multiple choice).
- Fix it so the last option scrolls into view and is clickable.

**Checkpoint:** diff, tests, green.

### 5. Record the fixes (me)
- In `findings.md`, set each row to "fixed (2026-10-28), not yet re-run".
- In each report, add a **Status:** line in the F-051 style: the cause plus the test names.
- Add the F-129 row.

### 6. Targeted re-test (Sonnet runner) = run 12
- Follow the test-plan rules: the runner only documents, and saves its scripts in
  `scripts/run-12/`. I save its report.
- Steps are those in criterion 10. The data stays in the current `app/data`; no upload.
- Afterwards: findings get "confirmed in run 12", and the summary is updated.

**Checkpoint:** results reported to you, then `/verify`.

## Out of scope
- Every minor, cosmetic and question finding except F-118–F-121, which the minimum size resolves.
- Web findings and F-077.
- The F-129 offset mismatch.
- Display scaling.
- Commits, unless you ask.
