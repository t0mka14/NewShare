# Fix round 3 (2026-10-29): web minor findings

## Context
Fix rounds 1 and 2 cleared every desktop major and minor. This round covers the 17 web-side
minors in `findings.md`: 13 web-ui, 1 server-api and 3 integration.

The fixes go in the web repo `C:\Users\koubato2\IdeaProjects\task_protocoller_web_app`, on branch
`newshare`, clean at `ced93e8`. Its frontend is **React** (react-router, vitest, Playwright), not
Vue. The backend is Node with vitest.

The workflow is the one in the memory note `e2e-fix-workflow`:
- one Sonnet `general-purpose` engineer per increment, one at a time;
- I review each diff and check myself that each new test fails on the old code;
- both suites must be green;
- I record each fix in `findings.md` and the finding's report;
- run 14 re-tests after a deployment, which needs your OK;
- the independent `/verify` reviewer runs before any commit;
- commits only when asked, with web-repo commits kept separate.

Three read-only searches confirmed every root cause below with file and line. Two findings turned
out to be fixed already, and F-086's real cause differs from what its report says.

**Your decisions (2026-10-29):**
- **F-037:** keep "download to play", plus a small tidy-up.
- **F-009:** the web sends the reading passage as the last paragraph.
- **F-097:** show "N clinics, M visible to you".
- **F-076:** custom field labels stay shared across languages; this is documented and the editor
  gets a hint.

## Spec

**Goal:** the admin web shows correct times, texts, counts and state, and sends the desktop a
config that matches the written contract.

**Deliverable:**
- fixes and tests in the web repo;
- findings and reports updated in NewShare;
- this spec saved as `docs/testing/e2e/fix-round-2026-10-29-web-spec.md`;
- the run 14 report.

### Acceptance criteria

1. **F-067, times shifted by the UTC offset.**
   - The protocol and library endpoints send `updated_at` and `created_at` as ISO-8601 UTC,
     using the existing `toIso` helper (`backend/src/controllers/uploadController.js:59`).
     Affected: `protocolController.js:497,530-532` and `libraryController.js:114,144`.
   - `frontend/src/utils/formatDate.js` reads a zoneless `YYYY-MM-DD HH:MM:SS` as UTC.
   - Tests:
     - a controller test checks that the value ends in `Z`;
     - a `formatDate.test.js` case under TZ=Europe/Prague: a time 2 minutes ago shows "2 minutes
       ago".
2. **F-069, default description text.**
   - A blank description is stored as `NULL` (`protocolController.js:273`).
   - `extConfig` also treats the old literal "Auto-created from AdminTaskEditor" as empty, so
     rows already saved with it stop sending it. **No data migration on the shared database.**
   - Tests in `protocolController.test.js` and `extConfig.test.js`.
3. **F-086, partial `POST /protocols` gives 500.**
   - Every task must have a positive whole-number `task_id`; otherwise the answer is 400
     `task_invalid`.
   - The catch also maps foreign-key error 1452 to 400.
   - Test in `backend/test/integration/accessModel.test.js`: a task without `task_id` gives 400.
   - Correct the cause stated in the F-086 report text.
4. **F-009, reading passage.**
   - For a READING task, `extConfig.js` (around `:304-315`) appends the topic's passage as the
     last instruction key, in every language. The "Start now" text stays the second-to-last key.
   - Test in `extConfig.test.js`: 3 keys, the last one equal to the topic text for en, cs and de.
   - The decision-table row stays as written, since the code now matches it.
5. **F-110, short video not shown.**
   - `sessionExtractor.js:271-281` keeps `videoComplete`, `videoDurationMs` and
     `audioDurationMs`.
   - `TaskTable.jsx` shows a "Video partial · 0:11 of 0:24" badge when `videoComplete === false`.
   - en, cs and de strings, passing the key-parity test.
   - `docs/upload_management/specification.md` is updated.
   - It is a badge only: no list flag and no "needs attention".
   - Tests: `sessionExtractor.test.js` and `Recording.test.jsx`.
6. **F-097, clinic count.**
   - `projectController.js:39-47` returns a scoped `visible_site_count` (via `scopeWhereClause`)
     and the total `site_count`. The raw `count_sites` is no longer leaked through `s.*`.
   - When they differ, the UI shows "Shared with N clinics, M visible to you"; otherwise it shows
     the old text. Affected: `StudyPage.jsx:115,130` and `StudiesPage.jsx:104`.
   - Tests:
     - `accessModel.test.js`: an editor assigned to 1 of 2 clinics gets total 2 and visible 1;
     - a master admin gets equal numbers.
7. **F-096, runs-at switches.**
   - `RunsAtList.jsx` draws no `Switch` for rows marked read-only, and `ClinicStudiesTab.jsx:61-69`
     marks received rows that way.
   - The "Received" subtitle stays. The study-side disabled switch is unchanged.
   - Test in `RunsAtList.test.jsx`: a read-only row has no switch, and the existing disabled case
     still passes.
8. **F-087, config version.**
   - `ClinicComputersTab.jsx` gets a "Config" column showing `lastConfigVersion`, with
     `lastConfigAt` as its tooltip.
   - en, cs and de strings.
   - Spec `02_spec.md` §6.7 is updated.
   - Test: a component test, or a Playwright assertion in `clinics-ui.spec.ts`.
9. **F-074, Clinics table overflow.**
   - `.ui-tbl-wrap` (`ui.css:27`) scrolls horizontally, so no table spills out of its card.
   - Test: a Playwright check at 1024 px on Clinics that the table wrapper is no wider than its
     card.
10. **F-079, direct preview link.**
    - Opening `…/protocols/:protocolId/preview` directly, or reloading it, loads the saved
        protocol (`getProtocolById` plus `mapProtocolWithNames`, as in `ProtocolsTab.jsx:102-106`).
    - For `new`, it redirects to the study's protocol list, never to `#/not-found`.
    - Test: a case in `frontend/e2e/redesign-routes.spec.ts`.
11. **F-068, F-073, F-076: designer texts and checks.**
    - **F-068:** `protocolEditor.confirmUpdateMsg` is rewritten in all 6 locale files, without the
      participant-link wording.
    - **F-073:** the task modal shows a **non-blocking warning** when min > max, or when the
      duration lies outside [min, max]. The task can still be saved.
    - **F-076:** the custom-field editor shows a hint that the label is the same in every
      language, and the decision table (row 7) says so.
    - Tests:
      - `editor.test.jsx`: the dialog contains no "unique links", and the warning appears and
        disappears;
      - `validation.test.js`.
12. **F-037, F-036 and F-111: clip and camera tile.**
    - `ClipPlayer.jsx` shows the "Download to play" fallback at once on
      `MEDIA_ERR_SRC_NOT_SUPPORTED`, with no retry and no new ticket. Other errors still retry
      once.
    - F-111: the camera name and the device notice are visually separated (`Recording.css` or
      `SessionPanel.jsx:37-39`).
    - F-037 is closed as accepted.
    - Tests in `Recording.test.jsx`.
13. **Suites green after each increment:**
    - backend `npm test`;
    - frontend `npm test`, `npm run lint` and `npm run test:e2e`.
14. **Run 14, a Sonnet runner with Playwright against the deployed container, after you OK the
    deployment.** It checks:
    - criteria 1, 5 (where a record shows it), 6, 7, 8, 9, 10, 11 and 12 live, as the right
      personas;
    - F-006 and F-035 are closed by a live check;
    - F-009: the site-config JSON has the passage last, and the desktop shows the "Start now"
      card on READING;
    - no new major finding.

### Verification plan
- I read every diff.
- I check that each new test fails on the old code, by putting the old file back with `git show`
  for one run.
- I run all suites and count the passing tests.
- Run 14: report and screenshots.
- `/verify` critic.
- Leak scan and link check before any docs commit.

### Assumptions (veto any)
- **F-068 wording:** "Saving changes this protocol for every clinic that runs it. Computers get
  the new version the next time they fetch their config." In cs and de as well.
- **F-073:** a warning, not a block. Only `duration` reaches the desktop, so it is not tightened
  further.
- **F-110:** existing records such as 05-3 keep their old summary, since nothing is re-extracted.
  The live check works only if a record with a partial video is uploaded or re-extracted.
  Otherwise unit tests are the evidence.
- **F-087:** an "outdated config" badge is out of scope; only the version is shown.
- **F-111** (cosmetic) rides along because it sits in the same tile as F-036.
- **Deployment:** I ask before running `docs/container_deployment.md` against the shared container.

### Key decisions
- **F-067:** fix at the source (ISO UTC from the API, per upload spec §395) and add a frontend
  safeguard, rather than only patching the frontend.
- **F-069:** filter in `extConfig` instead of a database `UPDATE`, which would mean writing to a
  shared server.
- **F-086:** validate `task_id`, since that is the real cause; the report blamed missing fields.

## Increments (one Sonnet engineer each; checkpoint = diff review, red check, green suites)
1. **Backend correctness:** F-067 (backend and `formatDate`), F-069, F-086.
2. **Config contract:** F-009 and the F-076 doc and hint.
3. **Upload record:** F-110 (extractor, badge, strings, spec).
4. **Access scope:** F-097 (query, UI label, strings).
5. **Clinics UI:** F-096, F-087 and F-074.
6. **Routing:** F-079.
7. **Designer:** F-068 and F-073.
8. **Recording view:** F-037 tidy-up and F-111.
9. **Record (me):** `findings.md` rows marked "fixed (2026-10-29), not yet re-run". F-006 and F-035
   become "fixed in the redesign, check in run 14". Save the spec file.
10. **Deployment:** ask you, then deploy per `docs/container_deployment.md`.
11. **Run 14:** I save the report. Then `/verify`, then commits when you ask.

## Out of scope
- web cosmetics and questions;
- transcoding video on the server;
- per-language custom labels;
- data migrations on the shared database;
- the desktop mutation-testing round;
- the F-134 to F-136 live re-test, which goes with the next desktop run.

## Review outcome (2026-10-29)

The independent reviewer found no major issue; all criteria were met in code. It found:

**Fixed in a follow-up increment:**
1. **Sticky table headers.** `overflow-x` on `.ui-tbl-wrap` broke them on every table.
2. **Menu focus.** A scroll moved focus back to the first item.
3. **`task_invalid` error.** It had no message text, and every foreign-key error 1452 was mapped
   to it.
4. **F-086 test location.** The test was not in `accessModel.test.js`.
5. **Legacy placeholder description.** It was still shown and re-saved in the admin UI.

**Accepted as is:**
- **Criterion 4, deliberate deviation:** cs and de send the topic **label** as the passage,
  because their `tasks.json` hold no passage text. The desktop drops that paragraph anyway while
  the passage panel is off.
- **`configVersion` does not change on deploy.**
  - Correction to an earlier note: desktops still apply the new config on their next fetch,
    because the app replaces its cache whatever the version.
  - Only traceability suffers: sessions from before and after the deploy carry the same version.
  - No bump is needed.
- **The preview waits on its skeleton if mappings never load.** This matches the editor page.

**Second critic pass** (on the follow-up fixes), also fixed:
- **Menu width.** The popup was measured at its static position; it now measures at 0/0 and is
  re-placed when its own size changes.
- **`task_id` range.** It is capped at 2^31-1.
- **The F-086 integration test** now hits the real MariaDB foreign key.

Accepted low edge cases:
- a menu is not re-placed when its row moves without a scroll;
- the library search still matches the hidden placeholder text;
- library copies keep the placeholder in the database, although reads hide it.

**Final evidence:**
- backend 777 tests, including 26 integration tests against the local test DB;
- frontend 304;
- Playwright 175/175;
- repo-wide lint unchanged at 36 errors and 21 warnings, all pre-existing.
