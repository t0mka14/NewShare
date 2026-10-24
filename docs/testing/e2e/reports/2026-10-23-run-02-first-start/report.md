# Run 02: First start, configuration and device registration

- Date / time: 2026-10-23 23:58 – 2026-10-24 00:06   Runner: Sonnet   App commit: `59bb063`
- Preconditions met: yes. Run 01 passed, and the app was started on an empty `app/data`.
- Result: passed with findings

The orchestrator started the app, and stopped and restarted it for step 6 (§3.4). The runner's
tool could not write report files, so the orchestrator saved this report from the runner's text
and renumbered `F-02-n` to global IDs.

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Start the app with no token 📷 | pass | A blocking screen: "No site token is set. Open Settings and enter the token from your administrator." It has a Settings button and no Start button. No crash, no error dialog. `step-1-main-no-token.png` |
| 2 | Enter token A and save 📷 | pass with findings | "Refresh configuration" → "Configuration refreshed. Everything looks OK." The main menu shows "Site: E2E-1023-Site-A" and "App version: 1.0.0". `step-2-settings-after-refresh-masked.png` (token and installation ID blacked out), `step-2-main-after-token.png`. F-016, F-018, F-020 |
| 3 | Choose microphone and camera | partial | Microphone options: Primary Sound Capture Driver, Steam Streaming Microphone and CABLE Output. CABLE Output was chosen and saved as `micDeviceId`. No level meter (F-017). No camera control (F-019). `step-3-settings-mic-selected-masked.png` |
| 4 | Server device check 📷 | pass | Device **27**, matching this computer's installation ID, is on Site A (id 12) before any upload. App `1.0.0`, `lastConfigVersion` `2026-10-23.215209` (the served version), pending 0, failed 0, "No uploads yet". The admin page `/#/admin/uploads/devices` shows the same. `step-4-devices-page.png` |
| 5 | Language CS and back to EN 📷 | pass | The main menu, protocol picker and Settings are fully Czech, with no raw keys. Back to EN restores everything. Protocol names and descriptions are the same in CS because the web data has one name and an EN description (see F-012). `step-5-main-cs.png`, `step-5-picker-cs.png`, `step-5-main-back-en.png` |
| 6 | Close and restart | pass | Token, microphone and language (EN) are remembered, and the cached config shows with no visible wait. The server shows a new `lastConfigAt`/`lastSeenAt` from the restart's check-in, the device still on Site A, and pending/failed 0. The window size is not remembered (back to 800×600, the size where F-020 clips). Language was EN at shutdown, so its persistence is only weakly shown. `step-6-main-after-restart.png` |

Logs: `hotrun.log` (first start; copy in `logs/hotrun-first-start.log`) and `hotrun-restart.log`.
`app/data/logs/app.log` is 0 bytes (F-018).

## Findings

### F-016: The site token is shown in clear text in Settings

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 02, step 2
- **Expected:** the token is masked like a credential. Plan §5.4 assumes this. The spec may not
  require it, so this may be a question rather than a defect.
- **Actual:** the "Site token" field shows the whole token, on screen and in the semantic tree.
- **Repro:** set a token, then open Settings.
- **Evidence:** no raw screenshot was kept, deliberately, because it would show the secret. The
  saved Settings screenshots are blacked out.
- **Status:** closed 2026-10-24, by design (user decision). The site token is not treated as a
  secret in Settings. Reports still redact it (plan §5.4).

### F-017: Settings has no live microphone level, and the microphone list starts blank

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 02, step 3
- **Expected:** plan step 3 asks for a level meter, if there is one. On first start, the examiner
  should be able to see that a microphone is selected and that it works.
- **Actual:**
  - "Microphone level" is a disabled gain slider: "Microphone level can not be set for this
    device". Nothing in Settings moves with the audio.
  - With no saved microphone, the dropdown is an empty pill, and nothing says that none is
    selected.
- **Repro:** first start, then open Settings.
- **Evidence:** `screenshots/step-3-settings-mic-selected-masked.png`
- **Notes:** calibration is the only place that shows a level. This may be by design; it overlaps
  a spec question.
- **Status:** fixed 2026-10-24 (`11b365a`), not yet re-run. Settings now preselects the microphone a session would use: the saved device, then the device matching the config's `defaultMicName`, then the system default. A hint shows where the choice came from. The preselection is saved only when the examiner picks a device. The chain is shared with the session (`MicChoice`). Tests: four `SettingsComponentTest` cases. Checked in the app: "Primary Sound Capture Driver" with the hint "System default".

### F-018: The app's own log `app/data/logs/app.log` stays empty

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 02, steps 1–6
- **Expected:** the app logs to `app/data/logs/` (plan §3.5, spec logging setup), including the
  config fetch and the device check-in.
- **Actual:** `app.log` is 0 bytes after first start, the config fetch, check-in, a clean stop
  and a restart. Only the stdout of `hotRun` has log lines.
- **Repro:** start on an empty `app/data`, set the token, stop the app. Check `app.log`.
- **Evidence:** file size 0 (orchestrator check after stop, runner check after restart);
  `hotrun.log`.
- **Notes:** not a late flush. Under `hotRun` the file appender may not be attached. The
  `hotrun.log` files are the evidence source until this is fixed. Later runs may raise its
  severity if a real examiner would have no log.
- **Status:** withdrawn in run 06. The appender works: `app.log` got its first line at 00:07:02
  ("microphone opened", run 03). It was empty because nothing in runs 01–02 logs at all: config
  fetch, check-in and upload write no lines. Tracked as F-038.

### F-019: Settings has no camera selection

- **Severity:** question
- **Area:** desktop-app
- **Run / step:** run 02, step 3
- **Expected:** plan step 3 says to choose `OBS Virtual Camera`. Spec line ~935 says the camera
  comes from the saved `cameraDeviceId`, falling back to the first eligible one.
- **Actual:** Settings has microphone, language, indicator, site token and installation ID.
  There is no camera control or preview, and `settings.json` has no `cameraDeviceId`.
- **Repro:** open Settings.
- **Notes:** either a camera selector is missing, or the plan is wrong. Run 05 depends on the
  first-eligible fallback picking OBS Virtual Camera.
- **Status:** fixed 2026-10-25, not yet re-run. Decision (user): add a camera selector. Settings has a camera dropdown: "Automatic (first available camera)" (`cameraDeviceId = null`), then the cameras from `VideoInputDeviceProvider`. Cameras are listed in the background (IO dispatcher, never on the EDT) with a "Looking for cameras…" state, and re-listed by the refresh-devices button. No cameras shows a "No camera found" note. A saved camera that isn't connected shows as "(not connected)" and is never overwritten. Spec §1 item 1. Tests: 5 `SettingsComponentTest` cases (F-019), 2 `SettingsCameraUiTest` cases; the F-020 scroll guard still passes.

### F-020: At the default 800×600 window, Settings is clipped and its buttons are unreachable

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 02, steps 2 and 6
- **Expected:** every control is reachable at the default window size.
- **Actual:** at 800×600, "Refresh configuration" is 3 px high, and "Installation ID" and "Save
  and go back" have 0×0 bounds. Whether the screen scrolls was not tested. At 984×861 everything
  fits. The window size is not remembered, so every start is at 800×600.
- **Repro:** start the app (800×600), set a token, open Settings, read the semantic tree.
- **Evidence:** semantic tree bounds. No screenshot: at that size it would show the unmasked
  token (F-016).
- **Status:** closed 2026-10-24 (`60e82bf`), not a defect. Settings already scrolls (`verticalScroll`). The 3 px and 0×0 bounds were controls scrolled out of view, and run 02 did not try scrolling. Checked in the app at 800×600: every control, including "Save and go back", can be reached by scrolling. The default window is now 1000×820. Regression guard: `SettingsLayoutUiTest` "every settings control is reachable by scrolling at 800x600". (not fully verified: scrolling not tried)

## Context updates

- `device.serverDeviceId` 27, `device.appVersion` 1.0.0, `device.settings` (mic, language,
  camera note), `device.afterRestart`.
- `app.languageSwitch`, `app.window`, `app.windowSizeRemembered` false.

## Notes for later runs (MCP driving)

- Semantic node IDs change on every recomposition: re-read `get_semantic_tree` before each click.
- Popups appear as a second root, so the tree becomes an array.
- Resize to about 1000×900 with `resize_window` after each start.
- Test tags:
  - `mainMenu.startProtocolButton`, `mainMenu.languageSelector`
  - `protocolPicker.protocolButton.<project>/<protocol>`
  - `settings.siteTokenField`, `settings.refreshConfigButton`, `settings.deviceSelect`
- Python on this host prints in cp1252: write UTF-8 output to files.
