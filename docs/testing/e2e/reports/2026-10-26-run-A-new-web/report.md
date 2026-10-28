# Run A: New web UI

- Date / time: 2026-10-26, 18:00-19:20   Runner: Sonnet   App commit: 7cd7e47 (web bundle index-5Q2Z5Huk.js)
- Preconditions met: yes, except that persona users cannot be given a known password (F-A-11)
- Result: passed with findings (editor / no-grants persona steps and the recordings-scope data check blocked by F-A-11 and an empty DB)

Report text saved by the orchestrator from the runner's hand-back (the runner's Write tool refused report.md).

Logins: master once through the UI and once through the API; admin@test.com once through the API. Other sessions inject that token into localStorage. master created everything below. admin@test.com (seeded) was the only non-master login and was left unmodified.

## Created (for run 01)
- Studies: E2E 1026 Speech study = 3, E2E 1026 Video study = 4
- Clinics: E2E 1026 Clinic A (both studies) = 4, E2E 1026 Clinic B (speech only) = 5
- Protocols: E2E 1026 Speech protocol (EN+CS, v2, group 1), E2E 1026 Video protocol (EN+CS, v1, group 2)
- Library entry: E2E 1026 Speech protocol, id 15
- Users: e2e-{editor,received,nogrants,creator}-1026@test.com, ids 3-6. They cannot log in (F-A-11). Prepared states: editor of study 3 and assigned Clinic B; received = Clinic A; no grants; creator = can_create_projects.
- Stray records (cannot be deleted): clinic id 3 with a 64-character "xxx..." name (F-A-16); archived probe protocol id 18 in Test Study 001; archived "E2E 1026 Speech copy" in the Video study; withdrawn library entries.

site-config check (logs/site-config-A.json, -B.json):
- A has Speech v2 and Video v1; B has Speech v2 only. languages = cs, en.
- Every titleKey, instructionKey and question text has a string in en and cs.
- Task mapping matches the §4.2 settings entered: PHONATION 2 rep, length 5; PATAKA length 5; READING length 150; MONOLOGUE length 20, canSkip; QUESTIONNAIRE regex `.+` and `.*`, single and multiple choice; Video protocol: PHONATION recordVideo; MONOLOGUE recordVideo, 2 rep, length 15; PATAKA without video, length 5.
- Library entries never appear in a config.

### Designer coverage (feeds run 01)
- Offers: repetitions, length, can repeat, can skip, show level indicator, record video (all voice tasks); questionnaire (open / single / multiple / dropdown / emoji; "optional" checkbox); patient fields with regex and required flag; file-name template, info page, instructions page, PDF URL, EN/CS/DE.
- Length: phonation and reading use Min/Max Duration; the others use Required Duration. Config `length` = duration, else min, else max.
- Does not offer: a standalone INFO task (the Info page is saved but not sent, F-A-6); a standalone camera / VIDEO task; COUNTING / CUSTOM; an example-audio switch (the server sets audioExamplePath itself for phonation and PATAKA); a per-question regex.
- Preview shows the participant-web flow, not the desktop flow (F-A-19).

## Steps
| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Navigation (§10.1) | pass | 24 routes as master; nav testids present; editors use the collapsed shell; preview has no shell; direct preview link F-A-13 |
| 2 | Redirects (§10.2) | pass | 13 old routes plus `?site=&project=`; F-A-12 |
| 3 | Single primary action (§10.3) | pass | at most one `.ui-btn-primary` per route |
| 4 | Create content | pass with findings | F-A-2, 3, 5, 6, 7, 9, 10, 15, 16, 20 |
| 5 | Received study (§10.4) | pass | admin@test.com via Paris: lock badge, banner, no Save / New protocol / settings; API 403 on POST /protocols, PATCH /projects/1, POST /protocols/18/archive, archive study, editors, runs-at |
| 6 | One clinic (§10.5) | pass | `#/clinics` -> `#/clinics/1/studies`, "My clinic", no Clinic filter in Recordings |
| 7 | Runs at (§10.6) | partial | study-side toggle by master works and shows in `/site-config`; clinic side only read; non-editor 403; "editor who doesn't see the clinic 404" blocked (F-A-11) |
| 8 | Editors (§10.7) | partial | add by email, 404 `user_not_found`, 400 `cannot_remove_creator`, non-editor 403; `last_editor` unreachable (F-A-17) |
| 9 | Library (§10.16) | pass (master only) | share, copy, edit copy, update entry (copy keeps v1), withdraw keeps copies, `name_taken` 400, never in config, no-access user 403 and no nav item |
| 10 | Recordings scope (§10.8) | blocked | no recordings in the DB and no editor login (F-A-11) |
| 11 | i18n (§10.12) | pass with findings | EN/CS/DE on 16 routes and 3 editor modals; no raw keys, no "undefined"; F-A-3, F-A-4 |
| 12 | Responsive | pass with findings | 1440x900 and 1024x768 on 16 screens; F-A-8 |

## Criteria
10.1 pass; 10.2 pass; 10.3 pass; 10.4 pass; 10.5 pass; 10.6 partial (404 case untested, F-A-11); 10.7 partial (last_editor unreachable, F-A-17); 10.8 blocked (F-A-11); 10.12 pass (F-A-3, F-A-4); 10.16 pass, single user (F-A-11); F-008 (§10.17) pass: names with spaces accepted and returned unchanged as `siteName`, live hint "FakultninemocniceBrno", 65 characters or blank gives 400 `invalid_name`.

## Findings
Run/step "A", Status open. Screenshots: `screenshots/F-A-<n>-*.png`.

- <a id="f-a-1"></a>**F-A-1** minor, web-ui: relative and absolute times are shifted by the browser's UTC offset. A protocol saved 2 minutes ago shows "1 hour ago". The server sends zoneless UTC (`2026-10-26 17:22:34`); the CEST browser reads it as local. Recordings "Received" will be off the same way. Notes: `formatDate.js` calls `new Date(value)` on a zoneless string. Evidence: F-A-1-relative-time-offset.png.
- <a id="f-a-2"></a>**F-A-2** minor, web-ui: the save-confirm dialog uses old participant-link text ("participants ... automatically moved ... unique links"); also for library copies. Evidence: F-A-2-stale-save-confirm.png.
- <a id="f-a-3"></a>**F-A-3** minor, web-ui/server: a blank description is stored as "Auto-created from AdminTaskEditor"; shown in lists and library (also DE/CS) and in the desktop config as `p1_description`. Evidence: F-A-3-default-description.png.
- <a id="f-a-4"></a>**F-A-4** cosmetic: questionnaire modal has untranslated strings in CS/DE ("Allow write-in", "Remove option", "Exclusive Option (Clears other selections)", "-- None --"). Evidence: F-A-4-untranslated-questionnaire-modal.png.
- <a id="f-a-5"></a>**F-A-5** cosmetic: questionnaire modal off-style (purple full-width "+ Add question" pill, ALL-CAPS labels). Evidence: F-A-5-questionnaire-modal-style.png.
- <a id="f-a-6"></a>**F-A-6** question, integration: the Info page is saved but no INFO task or info string reaches `/site-config`; the stored HTML has every space as `&nbsp;`. Plan §4.2 expects INFO "if the designer offers one". Evidence: F-A-6-info-page-not-in-config.png.
- <a id="f-a-7"></a>**F-A-7** minor: the designer accepts contradictory durations (monologue Required 20 with Min 30 / Max 120) without a warning. Evidence: F-A-7-contradictory-durations.png.
- <a id="f-a-8"></a>**F-A-8** minor, layout: at 1024 px the Clinics table is 1300 px wide in a 1009 px page; the card is clipped and columns spill out. Studies overflows slightly (1035 px). Evidence: F-A-8-clinics-table-overflow-1024.png.
- <a id="f-a-9"></a>**F-A-9** question: the default file-name template is `${field.patient_code}_${siteName}_${taskIndex}_${task.subtype}_Rep${repetition}`; spec §6.4 says `${patientCode}_${taskIndex}_...`. Evidence: F-A-9-default-file-name-template.png.
- <a id="f-a-10"></a>**F-A-10** minor: custom patient field label and help have no per-language entry; `strings.cs` holds "Visit code" and "e.g. V01". Evidence: F-A-10-cs-variant-custom-labels.png.
- <a id="f-a-11"></a>**F-A-11** major, environment: persona users cannot be given a known password. Invited users get a random emailed temporary password; neither UI nor API can set one. Blocks the editor, no-grants and "editor without the clinic" checks (§10.6 404, §10.7 as editor, §10.8, library with two users). Options: set the password hashes of users 3-6 in the DB as the seed does, or change admin@test.com's clinics / editor rights. Evidence: F-A-11-personas-cannot-login.png.
- <a id="f-a-12"></a>**F-A-12** question: opening `#/login` while signed in clears `adminToken` and `adminUser`. No screenshot (state only).
- <a id="f-a-13"></a>**F-A-13** minor: a direct link to `#/studies/:id/protocols/:pid/preview` ends on `#/not-found`; only the Preview button works. Evidence: F-A-13-preview-direct-link.png.
- <a id="f-a-14"></a>**F-A-14** cosmetic: primary action reads "Invite user" / "Send invitation"; spec §6.8 says "New user". Evidence: F-A-14-invite-user-wording.png.
- <a id="f-a-15"></a>**F-A-15** cosmetic: the questionnaire row in the task list shows blank "Allow repeat: · Allow skip:". Evidence: F-A-15-questionnaire-row-blank-values.png.
- <a id="f-a-16"></a>**F-A-16** cosmetic: the clinic name input silently truncates at 64 characters (typing 65 created a 64-character clinic, stray id 3, no message); the API returns 400 for the same name. Evidence: F-A-16-clinic-name-truncation.png.
- <a id="f-a-17"></a>**F-A-17** question, server: `last_editor` fires only on seeded studies. The creator is always an editor and cannot be removed; seeded studies have no editor rows. Not tested, to avoid permanent edits to seed data. Evidence: F-A-17-editors-last-editor.png.
- <a id="f-a-18"></a>**F-A-18** cosmetic: the "Not available" page for Library says "only available to administrators. Ask a master user"; Library is for users who may write protocols. Evidence: F-A-18-not-available-copy.png.
- <a id="f-a-19"></a>**F-A-19** question: the preview shows the participant-web "Audio Guide" intro, not the desktop flow. Evidence: F-A-19-preview-participant-flow.png.
- <a id="f-a-20"></a>**F-A-20** minor, server: `POST /protocols` with a body lacking `required_identifiers` and the file-name template fields returns 500 `protocol_save_failed` instead of 400. No screenshot (API only).

## Context updates
`reports/2026-10-26-context.json` (study, clinic, protocol and library ids, persona states, stray records) and `reports/2026-10-26-secrets.json` (clinic tokens, gitignored).

## Scripts
`docs/testing/e2e/scripts/common/{common,ui,pe}.py`, `scripts/run-A/*.py` (33 files), `scripts/README.md`. Paths and base URL come from env vars (E2E_STATE_DIR, E2E_BASE, E2E_REPORTS, E2E_CONTEXT, E2E_SECRETS, E2E_RUN_DIR). Known deviation: run-A scripts still contain this run's literal ids (study 3/4, clinic 4/5, protocol 1/15); noted in the README. No tokens in scripts.
