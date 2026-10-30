# Run 07: No token, quarantine and device assignment

- Date / time: 2026-10-24 00:45–00:55 local   Runner: Sonnet   App commit: `59bb063`
- Preconditions met: yes. Run 06 passed; fresh `app/data` with only the cached config.
- Result: passed with findings

The orchestrator did steps 1–2: stopped the app, moved `app/data` to `app/data.e2e-1023-run06`,
copied `config/` without `settings.json`, and started the app. The orchestrator also saved this
report from the runner's returned text, renumbered the findings, and redacted the run 02–06
installation ID from `logs/devices-*.json`.

Session: `E2E1023-07-1`, `d1edf0b1b2384d2b9b0f1912d8b481a1`. New device: server ID **37**
(`<installationId2>`).

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Fresh installation | pass (orchestrator) | New installation ID. The server has no record of it. |
| 2 | Cached config only, no token | pass | 📷 `step-2-fresh-start.png`, `step-2-settings-no-token.png`. With the cached config and no token, the app shows "Site: E2E-1023-Site-A" and can start protocols. The plan's fallback (token B) was **not needed**. Settings shows the red "No site token is set…". The microphone list starts blank (F-017). No check-in or config fetch reached the server. |
| 3 | Record and process `E2E1023-07-1` | pass | `year_of_birth` 1965. Phonation ×2, PATAKA, Reading, monologue skipped, Q1 and Q3. ZIP about 8.1 MB, Done, original timeline. |
| 4 | Upload without a token 📷 | pass with findings | The row shows "Upload failed: this computer is not registered to a site; contact your administrator". `upload_status.json` is `Failed`, `Rejected(DeviceNotRegistered)`. `app.log` has `upload rejected … HTTP 404`. On the server, attempt 14 is 404 `quarantined` with a matching sha. The upload has site null, status invalid, `unknown_device`, received 22:47:25.265Z. Device 37 was created with "No site (quarantine)". F-043, F-045, F-046. `step-4-upload-quarantined.png`, `step-4-devices-unassigned.png`, `step-4-uploads-list.png`, `step-4-upload-detail-quarantined.png` |
| 5 | Admin assigns the device to Site A 📷 | pass | Assign to site → Site A → "Yes, assign" ("Its quarantined sessions move to this site and are checked again."). Device 37 is now on site 12. The session moved to Site A straight away and is valid with 7/7 checks, `receivedAt` unchanged. `step-5-*.png` |
| 6 | Upload again from the app | pass | "Files successfully uploaded." The session is Uploaded. Locally the attempts are Rejected, then Success, with receipt `receivedAt` 22:47:25.265Z. On the server, attempt 15 is 200 `resent`, the session is valid on Site A, and **the original receipt time is kept**. `step-6-reupload.png`, `step-6-sessions-list.png` |
| 6b | Participant masking and reveal (F-039) | pass | The year of birth shows as `••••••`. "Show values" (`POST /api/uploads/<id>/reveal`) shows `1965`. The audit entry can't be seen (F-044). `step-6-detail-masked.png`, `step-6-detail-revealed.png` |
| 7 | Token B, then back to token A 📷 | pass with findings | With token B: "This computer is registered to another site. Contact your administrator." (`settings.otherSiteNote`). `app.log`: `WARN KtorDeviceCheckinApi … another site (HTTP 409)`. The server keeps device 37 on Site A. With token A the note goes away, and the device's `lastConfigAt`/`lastSeenAt` update to 22:52:51Z. F-041, F-042. `step-7-settings-other-site.png`, `step-7-settings-token-A-restored.png` (token and ID blacked out) |

## Findings

Repeats:

- F-016: the token shows in clear text.
- F-017: the microphone list starts blank.
- F-020: at 1000×820, "Save and go back" is clipped once a status line shows. Settings needs
  about 861 px of content height.
- F-038: rejected uploads and the 409 check-in are now logged. Successful uploads, config fetches
  and successful check-ins still write nothing.
- F-039: resolved by step 6b.

### F-041: With another site's token, the app switches to that site's config while the server keeps the device in Site A

- **Severity:** question (potentially major: wrong data)
- **Area:** integration
- **Run / step:** run 07, step 7
- **Expected:** the spec doesn't say what the app does with another site's config after a 409
  check-in (spec §6, `server-upload-integration.md`). Keeping the current config, or blocking new
  sessions, would match the "contact your administrator" note.
- **Actual:** after Refresh with token B, `config/config.json` is site B's (`siteName`
  E2E-1023-Site-B, `2026-10-23.215213`), and the app uses it at once. The device stays on Site A,
  so a session recorded now would use B's protocols and be filed under Site A. No session was
  recorded this way.
- **Repro:** on a device registered to Site A, enter token B → Refresh configuration → check the
  protocol picker or `config/config.json`.
- **Evidence:** `screenshots/step-7-settings-other-site.png`, `logs/devices-after-tokenB.json`,
  `app.log` 409 line.
- **Notes:** the server's `config_version` check on upload might reject such a session; not
  tested.
- **Status:** fixed (app side) 2026-10-25, not yet re-run. Decision (user): keep the old config. `RefreshConfigurationUseCase` now validates the fetched config, runs the device check-in, and only then persists and activates it. A 409 gives `Failed(ConfigError.RegisteredToAnotherSite)`: the previous config and its cache file stay as they were, a WARN line is logged, and Settings and the Blocking screen show the reworded other-site message. A network failure on the check-in, or a skipped check-in, still applies the config; the startup refresh follows the same rule. Spec §6 step 3 and decision 62, `docs/dev/server-upload-integration.md`. Tests: `ConfigRefreshOtherSiteTest` (6 cases, F-041), and `SettingsComponentTest` `F-041 a refresh whose check-in answers 409 shows the other-site key and keeps the old config`. Left as is: the typed token B stays saved while config A stays active, so uploads and check-ins use token B, which goes into the quarantine flow tested in run 07.

### F-042: Settings says "Everything looks OK" next to the red "registered to another site" warning

- **Severity:** minor
- **Area:** desktop-app
- **Run / step:** run 07, step 7
- **Expected:** one consistent status. The config fetch worked but the check-in was refused (409).
- **Actual:** "Configuration refreshed. Everything looks OK." sits directly above the red
  other-site line.
- **Evidence:** `screenshots/step-7-settings-other-site.png`
- **Status:** fixed 2026-10-24 (`11b365a`), not yet re-run. Cause: a successful refresh always showed "Everything looks OK", even when the check-in had said "another site". Fix: it now shows one warning status, `settings.refresh.otherSite`. Test: `SettingsComponentTest` "a successful refresh whose check-in says another site is one warning status, not an OK line". Not yet checked in the app, because it needs another site's token.

### F-043: The web session detail of a quarantined session has an empty site and a broken header

- **Severity:** cosmetic
- **Area:** web-ui
- **Run / step:** run 07, step 4
- **Expected:** a placeholder such as "No site (quarantine)", as the Devices page shows.
- **Actual:** the header reads "… · recorded at on 24. 10. 2026", and the SITE cell shows only a
  pin icon.
- **Evidence:** `screenshots/step-4-upload-detail-quarantined.png`, compared with
  `step-6-detail-revealed.png`
- **Status:** open

### F-044: The reveal audit entry can't be viewed anywhere

- **Severity:** question
- **Area:** web-ui
- **Run / step:** run 07, step 6b
- **Expected:** web spec §6: every reveal is recorded in `audit_log`. The UI text says "every
  change is written to the audit log".
- **Actual:** no audit view in the UI. `GET /api/audit` and `/api/audit-log` return 404. No
  system comment is posted.
- **Evidence:** `screenshots/step-6-detail-revealed.png`
- **Notes:** checking it needs database access. The same applies to the audit entries for edits,
  downloads and QC changes.
- **Status:** open

### F-045: A quarantined, unassigned device shows as "Healthy", and its rejected upload counts as an upload

- **Severity:** question
- **Area:** web-ui
- **Run / step:** run 07, step 4
- **Actual:** device 37 (no site) is "Healthy". The quarantined, invalid upload counts in
  "Uploads (30 d)" and "Last upload". "Last config fetch" is never.
- **Evidence:** `screenshots/step-4-devices-unassigned.png`, `logs/devices-after-quarantine.json`
- **Notes:** web spec §6 is silent on health for quarantined devices.
- **Status:** open

### F-046: The upload progress bar fills up green when the upload failed

- **Severity:** cosmetic
- **Area:** desktop-app
- **Run / step:** run 07, step 4
- **Actual:** a full green bar (1.0) above the red "Upload failed" row. The bar counts
  sessions done, failed ones included.
- **Evidence:** `screenshots/step-4-upload-quarantined.png`
- **Status:** fixed (2026-10-30), confirmed in run 16 (2026-10-30). Fix (NewShare `app/`, uncommitted): the upload progress bar uses the error colour when any row failed (`uploadHasFailure`); progress is counted as before. Test: `FixRound4DUiTest` "F-046 progress bar turns to the error colour when any session failed" (red before).

## Context updates

- `device2`: server ID 37, site 12 after assignment, microphone CABLE Output.
- `sessions[]`: run 07 (`d1edf0b1…`, ZIP sha256 `d47871bf…cfc304`, attempts 404/quarantined then
  200/resent).
- `run07` admin UI notes. `environment.appDataRun06Backup`.

## State left for run 08

- **App:** token A set, Site A config cached, microphone CABLE Output, main menu. The window is
  1000×900, which extends under the taskbar.
- **Server:** devices 27 and 37 are both on Site A. Nothing was deleted.
- **Local data:** `app/data/sessions/` holds only the run 07 session (Uploaded).
  `app/data.e2e-1023-run06` is untouched.
