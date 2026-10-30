# Run 15: targeted re-test of web fix round 4, group C

- **Date / time:** 2026-10-30, about 02:05–02:20 local.
- **Runner:** Sonnet, Playwright (`headless=False`) plus API calls. Scripts in `scripts/run-15/` (README table, `env.sh`, `c1`..`c14`, `c99`).
- **Target:** the shared test container http://192.168.122.183:10001, deployed from the uncommitted web working tree on top of `e20f764`.
- **Spec:** [fix-round-2026-10-30-group-c-spec](../../fix-round-2026-10-30-group-c-spec.md), criteria 1–13.
- **Result:** **passed**.
  - 11 of the 13 web criteria are confirmed live.
  - F-115 is not verifiable, because no recording is in quarantine.
  - F-109 is deferred to run 16.
  - One new cosmetic finding (F-15-1, index F-140). No major or minor findings.
- **Created:**
  - The throw-away study `E2E 1030 run15 study` (id 8, creator persona). It is now archived.
  - Nothing was deleted.
  - The questionnaire modal and the clinic dialogs were cancelled, so nothing was saved.

## Steps

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | F-090: Computers tab, "Connect a computer", Server address | pass | As master on Clinic A (id 4), the field shows `http://192.168.122.183:10001/api` in EN, CS and DE, with no `./api`. The access token stayed masked (Show was not pressed). `1-connect-computer-en/cs/de`, `logs/c1.txt` |
| 2 | F-070: questionnaire modal strings in EN/CS/DE (study 3, protocol 1, task 4; Cancel) | pass | CS: "Povolit vlastní odpověď", "Odebrat možnost", "Výlučná možnost (zruší ostatní výběry)", "-- Žádná --". DE: "Freitext erlauben", "Option entfernen", "Exklusive Option (hebt andere Auswahlen auf)", "-- Keine --". EN: "Allow write-in", "Remove option", "Exclusive option (clears other selections)", "-- None --". The only English left in CS/DE is "None", "Hoarseness" and "Cough", which are the protocol's own option texts, not UI. Protocol 1's `updated_at` is unchanged. `2-questionnaire-modal-en/cs/de`, `logs/c2.txt` |
| 3 | F-071: "+ Add question" button and labels | pass | The button is `ui-btn qm-btn-add-question`: an outline button with a plus icon, a white background and a 6 px radius. It is 137 px wide in an 864 px parent, so not full width, and uses 14 px text at weight 500. No element in the modal has `text-transform: uppercase`. `3-add-question-button` |
| 4 | F-084: not-available page in EN/CS/DE | pass | Checked Library as nogrants, and Users as editor and as nogrants. The neutral text shows in all three languages, e.g. "You don't have access to this page. Ask an administrator if you need it." The creator sees the Library normally. `library-nogrants-*`, `users-editor-*`, `users-nogrants-*`, `logs/c4.txt` |
| 5 | F-098: empty Clinics page | pass | nogrants and creator see "No clinics yet / Ask an administrator to give you access to a clinic." (CS and DE likewise), with no "Create one". The text for a persona who can create clinics was not checked live, because no such persona has zero clinics; a component test covers it. `clinics-nogrants-*`, `clinics-creator-*` |
| 6 | F-101: hidden clinic URL as nogrants | pass | `#/clinics/4` and the unknown `#/clinics/99999` show "Clinic not found / It does not exist, or you do not have access to it. / Back to Clinics" (CS "Klinika nenalezena", DE "Klinik nicht gefunden"). This matches the style of a hidden study, and is not the generic 404. `hiddenclinic-nogrants-*`, `unknownclinic-nogrants-en`, `hiddenstudy-nogrants-en` |
| 7 | F-112: participant panel, recording 03-1, Show values | pass | EN "Year of birth", CS "Rok narození", DE "Geburtsjahr"; no "opt". "Visit code" is a custom label, shown as typed in every language, as F-076 documents. `7-participant-panel-en/cs/de` |
| 8 | F-115: quarantine banner | not verifiable | All 9 uploads are `valid` with a site. The run 07 device was assigned to Clinic A in run 07. No quarantine was created for this check. Unit test only. `logs/c13.txt` |
| 9 | F-082: clinic name length | pass | New clinic modal: at 60 characters the counter reads "60/64", at 63 "63/64", and at 64 "64/64 - Maximum length reached". Typing 70 characters stays at 64 with the same message. Cancelled, and the list is unchanged. Clinic A's settings show the same counter; the name was not saved. `9-new-clinic-60/64/70`, `9-clinic-settings-64`, `logs/c9.txt` |
| 10 | F-099: Recordings search placeholder at 1440x900 | pass | Received persona (one clinic), placeholder text width against the input's content width: EN 226 of 362 px, CS 243 of 483, DE 276 of 331. The master view also fits. `10-search-placeholder-*` |
| 11 | F-078: `#/login` while signed in | pass | Master and received land on `/recordings`, also after a reload on `#/login`, and keep `adminToken` and `adminUser`. Sign out lands on `/login` with both removed and empty fields. The pending-password-change case was not tested live. `11-login-redirect-*`, `11-after-signout-*` |
| 12 | F-100: add-editor API and UI toast | pass | Creator on study 8: adding user 3 gives **201** with `added_at` `2026-10-30T01:06:22Z`. Re-adding gives **200** with the same grant and `added_at`, also with an upper-case email, and the list still has 2 rows. The toast reads "E2E 1026 Editor is already an editor" (CS "už je editor", DE "ist bereits Bearbeiter"). Study 8 was then archived. `12-already-editor-toast-*`, `logs/c12.txt`, `logs/c12ui.txt` |
| 13 | F-109: web camera tile | deferred to run 16 | No live recording carries an `unavailable_at_start` event. The existing camera tile of 05-1 renders as before. `13-camera-tile-05-1-*` |
| 14 | Regression spot checks in EN/CS/DE | pass | Pages checked: recording detail, Recordings, Studies, study protocols, protocol editor, task modal, Clinics list and clinic studies. None shows "undefined", "NaN" or "[object". No console errors or page errors across 7 pages x 3 languages; the expected 403/404 calls on the not-available and not-found pages were left out. `14-*`, `logs/c14.txt` |

Installation IDs were masked with `redact.py` before every screenshot that shows computers. The logs hold no tokens or IDs.

## New findings

### F-15-1: "Add Instructions" in the protocol editor stays English in CS and DE
Index ID: **F-140**
- **Severity:** cosmetic
- **Area:** web-ui
- **Run / step:** run 15, steps 2 and 14
- **Expected:** the button in the Pages section of the protocol editor is translated.
- **Actual:** with the app language set to cs or de, the editor shows "Add Instructions" next to "Stránky"/"Seiten", "Upravit" and "Odebrat". The rest of the editor is translated.
- **Repro:** sign in as master, set CS or DE, open `#/studies/3/protocols/1`, and look at the Pages section.
- **Evidence:** `screenshots/2-questionnaire-modal-cs.png` (bottom right), `logs/c14.txt`.
- **Status:** open

## Seen again

None. The camera tile, the clip table, the Config column and the "Last changed" column render as in run 14.

## Not covered

- F-115 live, because no recording is in quarantine.
- F-109's web tile, which run 16 checks.
- F-098 for a persona who can create clinics.
- F-078 with a pending password change.
