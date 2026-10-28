# Run 02: First start, configuration and device registration

- Date / time: 2026-10-26, 18:38-18:47   Runner: Sonnet   App commit: 7cd7e47
- Preconditions met: yes (app/data absent at start; run 01 covered by Run A; token A valid)
- Result: passed with findings

Report text saved by the orchestrator from the runner's hand-back. Words follow the new web: clinic = site, study = project, computer = device. Token A is written `<token:E2E-1026-Clinic-A>`. Screenshots with the token field or installation ID are blacked out; logs have the ID replaced by `<installationId>`.

## Steps
| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Start the app, main screen without token (no crash or dialog) | pass | step-1-main-no-token.png. No Start button: a blocking screen says "No site token is set. Open Settings and enter the token from your administrator." with a Settings button. Log: WARN `config refresh: rejected, SiteTokenMissing` (expected). Message is left-aligned while title and button are centred (F-02-4). Window resized to 1000x820. |
| 2 | Settings: enter token A, save | pass | step-2-settings-after-save.png, step-2-main-with-site.png. "Configuration refreshed. Everything looks OK." Log: config fetch 200, check-in 200, `applied configVersion=2026-10-26.172809 site=E2E 1026 Clinic A`. Main shows "Site: E2E 1026 Clinic A". |
| 3 | Settings: microphone = CABLE Output; note camera and level meter | pass | step-3-camera-options.png. Mic saved and kept after restart. Settings now HAS a camera picker (Automatic / "OBS Virtual Camera"), so F-019 is fixed and the plan text is out of date. NO level meter in Settings; the "Microphone level" slider is disabled for both the default driver and CABLE. Language options are raw `cs` / `en` (F-02-3). At 1000x820 the Settings content (717 px) is taller than the window; "Save and go back" has zero bounds until scrolled (`scroll` works; known F-063). |
| 4 | Server: GET /api/devices and the admin Computers page | pass | step-4-computers-page.png, logs/api-devices-after-first-start.json. Before the app started: 0 devices. After step 2: one device, id 1, siteId 4 "E2E 1026 Clinic A", appVersion 1.0.0, lastConfigVersion 2026-10-26.172809 (= /site-config configVersion), pending 0, failed 0, neverUploaded true. Computers tab shows version, last seen, waiting 0, but not the config version (F-02-1). |
| 5 | Switch to CS and back to EN | pass with finding | step-5-main-cs.png. Main menu, Settings and picker are Czech, no raw keys. "Auto-created from AdminTaskEditor" stays English in CS because the served `strings.cs.p1_description` is the same English text (F-02-2 = F-069). |
| 6 | Close and restart the app | pass | step-6-after-restart.png. Czech main menu with "Pracoviště: E2E 1026 Clinic A" right after the window appeared (cached config). Background refresh 2-3 s later: `unchanged configVersion=2026-10-26.172809`, check-in 200. Token and mic remembered. Server still lists exactly one device (id 1). Language set back to en. |

Cleanup: app stopped, PID gone, no ffmpeg, main.pid removed. app/data/app.lock exists but is not held. app/data kept.

## Findings
<a id="f-02-1"></a>

### F-02-1: Computers tab gives no way to see the served config version
- **Severity:** minor. **Area:** web-ui. **Run / step:** run 02, step 4.
- **Expected:** plan step 4 expects the device's configVersion to be visible; the old Devices page showed it.
- **Actual:** the clinic's Computers table has only Computer, Status, App version, Last seen, Waiting to upload. `lastConfigVersion` / `lastConfigAt` exist in GET /api/devices but are not shown.
- **Repro:** master, open `#/clinics/4/computers`.
- **Evidence:** step-4-computers-page.png; logs/api-devices-after-first-start.json.
- **Notes:** check whether the redesign spec intends this.
- **Status:** open

<a id="f-02-2"></a>

### F-02-2: Protocol description not localised (duplicate of F-069)
- Server fills the default description "Auto-created from AdminTaskEditor" in both languages; shown in the CS picker. Evidence: screenshots/F-02-1-picker-description-english-in-cs.png; app/data/config/config.json. Not an app defect.

<a id="f-02-3"></a>

### F-02-3: Language options in Settings are raw codes `cs` / `en`
- **Severity:** cosmetic. **Area:** desktop-app. **Run / step:** run 02, steps 3 and 5.
- **Expected:** language names or flags, as on the main menu selector.
- **Evidence:** step-2-settings-after-save.png.
- **Status:** open

<a id="f-02-4"></a>

### F-02-4: "No site token" message is left-aligned
- **Severity:** cosmetic. **Area:** desktop-app. **Run / step:** run 02, step 1.
- **Expected:** centred like the title and the Settings button. **Actual:** text starts at x=32 while "SHARE" and the button are centred.
- **Evidence:** step-1-main-no-token.png.
- **Status:** open

<a id="f-02-5"></a>

### F-02-5: Computers tab "Connect a computer" shows Server address "./api"
- **Severity:** question. **Area:** web-ui. **Run / step:** run 02, step 4.
- **Expected:** an address the examiner can use. **Actual:** relative `./api` (the app has no server-address field; it is compiled in). Maybe VITE_API_BASE is unset on this deployment.
- **Evidence:** step-4-computers-page.png.
- **Status:** open

### Observations, not findings
- Plan text is out of date on the camera picker (step 3).
- Site token is shown in clear text in Settings (known F-016) and stored in clear in settings.json.
- Mic gain slider disabled for CABLE and the default driver; real hardware not checked.

## Context updates
context.json: device.serverDeviceId = 1, siteId = 4, micDevice = CABLE Output (VB-Audio Virtual Cable), appLanguage = en, configVersion = 2026-10-26.172809.

## Scripts
`docs/testing/e2e/scripts/run-02/` (front.ps1, redact.ps1, computers_page.py, README.md).

## Notes for later runs
- After stopping the app delete `app/build/run/main/main.pid`. Start takes about 15 s to main.pid and 25 s to "Waiting for changes".
- The terminal covers the SHARE window: run `scripts/run-02/front.ps1` right before every `take_screenshot`; `redact.ps1` blacks out rectangles (token and installation ID show in Settings).
- Settings needs `scroll` on its container node at 1000x820.
- Computers tab is `#/clinics/<id>/computers`.
