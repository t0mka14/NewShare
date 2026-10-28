# Run A2: Persona checks (editor, received, nogrants, creator)

- Date / time: 2026-10-26, about 19:30-20:30   Runner: Sonnet   App commit: 7cd7e47
- Preconditions met: yes
- Result: passed with findings (§10.8 data check not covered: no recordings in the DB)

Report text saved by the orchestrator from the runner's hand-back. The runner logged in once per persona by API (master, editor, received, nogrants, creator) and reused the tokens; no UI login, so the login-page flow for these personas is untested here (Run A covered it for master; F-078 stays open). Playwright headless, 1440x900 full page. No desktop screenshots; the SHARE app was not touched.

State at the end:
- Clinic A runs Speech and Video; Clinic B runs Speech (as in the context file; B toggled off and on and `/site-config` checked each time).
- A throw-away study, id 6, was created with the creator persona and archived at the end. In it: protocols 20, 22 and 24-27; library entries 21, 23 and 27, all withdrawn. The editor persona is still an editor of study 6. nogrants was added to study 6 and removed again. Nothing was deleted.

## Steps
| # | Step | Result | Notes |
|---|---|---|---|
| 1 | §10.6 study side, editor, visible clinic B | pass | DELETE then PUT `/projects/3/sites/5` 200, confirm dialog before stopping. B's config went to `[]`, then Speech again. |
| 2 | §10.6 clinic side, editor | pass | The switch only turns a study off (confirm, DELETE 200, B config `[]`). Turning it on goes through "Add study" (PUT 200, config restored). |
| 3 | §10.6 non-editor 403 | pass | received PUT and DELETE `/projects/3/sites/4`: 403 `forbidden`. |
| 4 | §10.6 editor without the clinic 404 | pass | editor PUT and DELETE `/projects/3/sites/4` (A, not assigned): 404 `not_found`; A's config unchanged. `GET /sites/4` 404. Invisible study (PUT `/projects/4/sites/5`) 404, also for nogrants. |
| 5 | §10.7 add by email, UI and API | pass | UI: add and remove with confirm. API: 201; unknown or partial email 404 `user_not_found`; empty body 400 `invalid_email`. See F-A2-6. |
| 6 | §10.7 `cannot_remove_creator` | pass | co-editor, creator and master all get 400. The creator row has no Remove button. |
| 7 | §10.7 `last_editor` | not covered | Unreachable because the creator is always an editor (F-083). |
| 8 | §10.7 non-editor 403 | pass | received (sees study 3 through clinic A): GET, POST, DELETE editors all 403 `forbidden`. A user who can't see the study gets 404. |
| 9 | §10.8 strict recordings scope | not covered: no data | See below. |
| 10 | §10.16 library, two users | pass | See below. |
| 11 | §10.16 no access | pass | received and nogrants: `GET /library` 403, share 404, copy 403; no Library nav item. |
| 12 | §10.4 received persona | pass with finding | See below. |
| 13 | §10.5 one clinic | pass | `#/clinics` redirects to `#/clinics/4/studies` (received) and `#/clinics/5/studies` (editor); "My clinic"; no Clinic filter in Recordings. Clinic column not checkable (no rows). nogrants and creator have 0 clinics (F-A2-4). |
| 14 | Landing after login | pass | `#/` goes to `#/recordings` for all four. nogrants sees "No recordings yet", disabled Download, empty Studies and Clinics, no Library or Users item. |
| 15 | Sanity tour of Recordings, Studies, Clinics | pass with findings | 4 personas x 20 routes. Nav correct (received, nogrants: Recordings, Studies, Clinics; editor, creator also Library; nobody sees Users, `#/users` shows "Not available"). No raw keys, no "undefined", at most one primary button, no failed API calls except expected 403/404. F-A2-2, 3, 4, 5, 7, 8. |
| 16 | CS and DE on persona screens | pass | 35 screens, no raw keys, no "undefined", no English leftovers. |

§10.8 detail: the DB has 0 recordings. Every persona gets empty lists from `/uploads`, `/uploads/summary` and `/devices`. An unknown id gives 404 `not_found` on open, zip, clip, attempts, activity, comments and reveal; bulk `POST /uploads/download` with a fake id gives 400. To finish this step a clinic-A recording is needed (desktop upload in run 06); then run `scripts/run-A2/scope_data.py`: editor (not assigned to A) must get 404 everywhere, received must get 200.

§10.16 detail: the editor persona stands in as the second user (the creator is the only `can_create_projects` user). The creator shares a protocol (entry v1); the editor adds a copy to study 6 (201), a second add gives 400 `name_taken`, adding to study 4 gives 404, a withdraw by the editor gives 403. The editor saves a new version of the copy; the entry stays v1. The creator updates the entry to v2; the copy stays at its old version and `copied_from_id` points at library v1. A library entry never appears in `/site-config`. Withdraw through the UI confirms that copies stay; they do.

§10.4 detail (received): lock badge "Received" and banner "Managed by System Master..."; tabs Protocols, Recordings, About only (`/editors` and `/clinics` tab routes land on Protocols); no Save, New protocol, Archive or Add editor control; row menu holds only Preview; direct editor link opens read-only. 403 on POST `/protocols`, PATCH `/projects/3`, protocol archive, study archive, share, runs-at PUT, PATCH `/sites/4` (also with `access_token`), POST `/sites`, `/projects`, `/users`. `GET /sites/4` carries no `access_token`.

## Criteria
| Criterion | Result |
|---|---|
| 10.4 received studies | pass (F-A2-1) |
| 10.5 one clinic | pass (Clinic column not checkable) |
| 10.6 runs at, incl. 404 | pass |
| 10.7 editors | pass; `last_editor` unreachable (F-083) |
| 10.8 strict recordings scope | not covered: no data (empty lists and 404 on unknown ids verified) |
| 10.12 i18n, personas | pass |
| 10.16 library, two users | pass |

## Findings (Status open; run/step A2)
- <a id="f-a2-1"></a>**F-A2-1** (minor, web-ui, step 12). Spec §4 says edit controls on a received study are absent, not disabled. At `#/clinics/4/studies` the received persona sees disabled, dimmed switches (`clinic-studies-switch-3`, `-4`) with "Managed by its editors. You can review its recordings." Evidence: `screenshots/received-runsat-clinic-A-switches.png`, `received-clinics.png`.
- <a id="f-a2-2"></a>**F-A2-2** (minor, web-ui/server-api, step 15). The editor, assigned to Clinic B only, sees "Shared with 2 clinics" and tab count "Clinics 2" on study 3, but the tab lists only B; the count reveals a clinic they can't see (spec §4). Evidence: `screenshots/editor-studies_3_clinics.png`, `editor-studies.png`.
- <a id="f-a2-3"></a>**F-A2-3** duplicate of F-090: Connect card server address is the relative `./api`, for master and non-master alike. Evidence: `screenshots/received-clinics_4_computers.png`, `master-clinic-A-computers.png`.
- <a id="f-a2-4"></a>**F-A2-4** (cosmetic, web-ui, step 13). nogrants and creator see "No clinics yet... Create one to get an access token" without a New clinic button; the text should tell them to ask an administrator. Evidence: `screenshots/nogrants-clinics.png`, `creator-clinics.png`.
- <a id="f-a2-5"></a>**F-A2-5** (cosmetic, web-ui, step 15). Recordings search placeholder for the received persona reads "Patient code, session ID or compute", cut off by the "All studies" select at 1440. Evidence: `screenshots/received-recordings.png`.
- <a id="f-a2-6"></a>**F-A2-6** (question, server-api, step 5). Spec §4 says users are found by exact email. A second POST of the same editor email returns 201 (not 200/409) with `added_at: null`; `E2E-EDITOR-1026@TEST.COM` also matches; partial emails correctly give 404. API only.
- <a id="f-a2-7"></a>**F-A2-7** (cosmetic, web-ui, step 15). A hidden study shows "Study not found... you do not have access to it"; a hidden clinic shows the generic "Page not found". Evidence: `screenshots/nogrants-studies_3.png`, `nogrants-clinics_4.png`, `received-clinics_5_studies.png`.
- <a id="f-a2-8"></a>**F-A2-8** (question, web-ui/server-api, step 15). The received persona's Computers tab names the computer by its full installation ID and `GET /devices` returns `installationId` to non-master users. Plan §5.4 treats installation IDs as values to black out; the spec doesn't say who may see them. Redact `screenshots/received-clinics_4_computers.png` before sharing.

Existing findings seen again: F-084, F-067, F-083, F-086. F-077 is resolved for these four personas.

## Context updates
Study 6 (archived, protocols 20, 22, 24-26), library entries 21, 23, 27 (withdrawn), editor persona also editor of study 6; library entry 15 untouched; clinic ids unchanged.

## Scripts
`docs/testing/e2e/scripts/run-A2/`: login.py, probe.py, api_runsat.py, api_editors.py, api_library.py, api_scope.py, scope_data.py, tour.py, ui_runsat.py, ui_editors.py, ui_library.py, received_checks.py, i18n_personas.py. Ids come from the context file; password from `E2E_PASSWORD`. `common/ui.py` now reads `E2E_HEADLESS`. Running `api_editors.py` twice creates a second throw-away study. Log: `logs/persona-tour.txt`.
