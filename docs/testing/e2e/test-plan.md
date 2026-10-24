# End-to-end test plan: web → desktop app → server

Version 1.1, 2026-10-24

Changes since 1.0 (from the 2026-10-23 run, see [`findings.md`](findings.md)):

- Site names without spaces (F-008); the right admin login path (F-015).
- No camera picker in Settings (F-019); the OBS camera-off steps replaced (F-030).
- Masking check names the field to mask (F-039); run 03 fills the optional patient field.
- Run 08: the firewall rule needs an elevated shell, and parts 4 and 3 run in one app instance.
- Window size, mouse-drag helper, screenshot focus and installation-ID redaction notes.
- New run 09 (window sizes and layout); the summary moves to run 10.

This plan covers the whole SHARE process against the real web backend:

1. An administrator sets up sites, projects and protocols in the web app.
2. The desktop app loads that configuration by site token.
3. An examiner runs speech and video sessions, edits and processes them, and uploads them.
4. The administrator finds the resulting records on the server and checks them.

It is written for an **orchestrator agent (Opus)** that launches one **runner agent (Sonnet)** per
run, sequentially, and collects their reports. Runners **find and document problems; they never
fix them** (no code edits, no commits, no config changes to the app or the web repo). Fixing is a
separate, later step that works from the findings index.

Related documents:

- [`../../Project_Specification.md`](../../Project_Specification.md): what the desktop app must do
  (§6 config, §8 recording, processing and upload, §11 errors).
- [`../../Task_Configuration_JSON_Spec.md`](../../Task_Configuration_JSON_Spec.md): what the
  config the app receives looks like.
- [`../../dev/server-upload-integration.md`](../../dev/server-upload-integration.md): the upload,
  config-fetch and device check-in contract, with the response table.
- [`../../dev/upload_data.md`](../../dev/upload_data.md): what is inside the uploaded ZIP.
- [`../../dev/hot-reload.md`](../../dev/hot-reload.md): driving the app through the MCP server.
- Web repo `../task_protocoller_web_app/docs/upload_management/specification.md`: the server
  side (§3 upload, §6 devices, §8 admin API, §9 downloads).

## 1. Roles and rules

### 1.1 Orchestrator (Opus)

- Runs §3 (preflight) itself, then launches the runs in §6 **one at a time**, in order. There is
  one machine, one app window, one virtual microphone and one virtual camera, so runs cannot run
  in parallel.
- Gives each runner: this file, its run ID, its run section from §6, and the run context file
  (§4.3) left by the previous runs.
- After each run:
  - reads the report,
  - removes duplicate findings (the same defect seen again links to the first finding's ID),
  - adds new findings to [`findings.md`](findings.md),
  - decides whether the next run can start. A **blocker** in a run that later runs depend on
    stops the sequence; the orchestrator reports to the user instead of carrying on.
- Does not fix anything either. It may restart the app or the environment (§3.4) between runs.
- Saves each runner's `report.md` from the text the runner returns, if the runner's tools can't
  write it, and redacts installation IDs from the run's logs and dumps (§5.4).
- Does steps the runner's permission policy refuses (for example the admin API login) only with
  the user's approval, and notes in the report who did them.

### 1.2 Runner (Sonnet)

- Follows its run section step by step, and records a result for every step (§5.2), not only the
  failures.
- For every problem: takes a screenshot **at the moment it is visible**, writes a finding (§5.3),
  and continues if the run can still go on. If it can't, it marks the remaining steps
  `blocked by F-xxx` and finishes.
- Never:
  - edits files under `app/`, `shared/`, `updater/` or the web repo,
  - commits,
  - changes `.mcp.json`, `gradle.properties` or Windows settings beyond what its run says,
  - deletes server records.
- Keeps credentials out of reports. Write the site token as `<token:SITE-NAME>` and the
  installation ID as `<installationId>`; the Paris token below is the only exception, since it is
  already published in the docs.
- When something looks wrong but the spec does not say what is right, records it as a
  `question` finding rather than guessing.

## 2. Environment

| Item | Value |
|---|---|
| Host | Windows Server 2022 VM (KVM), reached over RDP. The app window, the browser and OBS all live on the RDP desktop. |
| Desktop app | `./gradlew :app:hotRun --mainClass=org.example.app.MainKt --auto`. Window title `SHARE (v1.0.0)`. Data dir `app/data/`. Opens at 800×600 every time (the size isn't remembered); runs 02–08 resize it with `resize_window` to 1000×820 after each start (§3.4). |
| JDK | `C:\Users\koubato2\.jdks\ms-25.0.4.1` (`JAVA_HOME` and `org.gradle.java.home`); the app itself runs on the JetBrains Runtime 25 that the hot-reload plugin downloads. |
| Server API | `http://192.168.122.183:10001/api`, compiled into the app as `WEB_SERVER_BASE_URL`. |
| Admin UI | `http://192.168.122.183:10001`, master login `master@test.com` / `1234`. |
| Seeded site | Paris, token `paris000paris000paris000paris000`. |
| Microphone | VB-Audio Virtual Cable. Audio played into **CABLE Input** comes out of **CABLE Output**, which is the app's microphone. |
| Camera | OBS Studio Virtual Camera (`OBS Virtual Camera`, DirectShow). The app has no camera picker; it uses the first eligible camera (F-019), which on the VM is OBS. A **stopped** OBS virtual camera still exists and sends a placeholder picture, so it can't stand in for a missing camera (F-030). |
| Audio source | A player (Windows Media Player, `wmplayer`) looping speech audio into CABLE Input. |
| Screenshots | MCP `take_screenshot` for the app window. `tools/Capture-Screen.ps1` for the browser, OBS, Windows dialogs and as a fallback. |
| Mouse drag | `tools/Drag-InWindow.ps1`: a real OS mouse drag or click in the window's client coordinates (the semantic tree's bounds at 100 % scaling). The MCP tools can't drag, and the editor's boundary handles react only to a drag. |
| Web automation | Playwright for Python with Chromium (§3.2). |

## 3. Preflight (orchestrator, before run 1)

Record every check in the preflight section of the first report. If any check fails and can't
be fixed by the steps here, stop and report to the user.

### 3.1 Devices

1. **Microphone privacy.** `HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\microphone`
   `Value` must be `Allow`. When it is `Deny`, every capture device returns silence or an error,
   the virtual cable included.
2. **RDP audio.** Under RDP, only "Remote Audio" is visible unless the connection uses
   *Remote audio playback: Play on remote computer* and *Remote audio recording: Do not record*.
   The user sets this in mstsc; check that `ffmpeg -list_devices true -f dshow -i dummy` lists
   `CABLE Output (VB-Audio Virtual Cable)` and `OBS Virtual Camera`.
3. **Signal on the cable.** With the player looping into CABLE Input:

   ```powershell
   ffmpeg -hide_banner -f dshow -i "audio=CABLE Output (VB-Audio Virtual Cable)" -t 4 -af volumedetect -f null - 2>&1 | Select-String mean_volume,max_volume
   ```

   The mean must be above about −40 dB. Around −20 dB is fine.
4. **Camera running.** OBS is open with a non-black scene (a looping video, or a colour source with
   text), and **Start Virtual Camera** is on.

### 3.2 Tools

1. **Compose hot-reload MCP.** The checked-in `.mcp.json` runs `./gradlew`, which does not start
   on Windows. Ask the user to add a local override once, then restart Claude Code:

   ```powershell
   claude mcp add compose-hot-reload --scope local -e JAVA_HOME=C:\Users\koubato2\.jdks\ms-25.0.4.1 -- cmd /c C:\Users\koubato2\IdeaProjects\NewShare\gradlew.bat --no-daemon --quiet --console=plain hotMcpServer
   ```

   The `-e JAVA_HOME=…` is required: there is no system `JAVA_HOME` and no `java` on `PATH`, so
   without it `gradlew.bat` exits 1 before Gradle starts and Claude Code only reports
   `CONNECTION_CLOSED` (`org.gradle.java.home` is read too late to help).

   Check that `mcp__compose-hot-reload__status` answers. Without it runners can't click in the
   app; stop and report.
2. **Playwright.** In a venv in the orchestrator's scratchpad:
   `python -m venv e2e-venv`, `e2e-venv\Scripts\pip install playwright`,
   `e2e-venv\Scripts\python -m playwright install chromium`. Runners use it from there for the
   admin UI, with `headless=False` so the user can follow on the RDP desktop, and take web
   screenshots with `page.screenshot(path=…)`.
3. **Admin API token** for verification steps:

   ```powershell
   $login = Invoke-RestMethod -Method Post -Uri http://192.168.122.183:10001/api/auth/admin/login -ContentType application/json -Body '{"email":"master@test.com","password":"1234"}'
   $h = @{ Authorization = "Bearer $($login.token)" }
   ```

   Log in **once** per run and reuse `$h`, because the login endpoint is rate-limited. The
   orchestrator may log in during preflight and keep the token in its scratchpad (never in
   `reports/`), so runners whose permission policy refuses the login can still read the API.
   Other paths used by the runs: `/api/site-config/<token>`, `/api/devices`,
   `/api/uploads/<sessionId>/attempts`. Admin UI routes: `#/admin/uploads`,
   `#/admin/uploads/<sessionId>`, `#/admin/uploads/devices`.

### 3.3 App data

The app keeps its data in `app/data/`, which holds the user's own sessions and settings.
Before run 1:

1. Stop the app (§3.4).
2. Move `app/data` to `app/data.pre-e2e-<YYYYMMDD>`. The first start then behaves like a fresh
   installation, with a new installation ID and no token, which run 2 needs.
3. Check that `app/data.pre-e2e-*` is not deleted by anything later. It is restored after the
   last run (§7).

### 3.4 Starting and stopping the app

Start (PowerShell, background task, log into the run's folder):

```powershell
Remove-Item Env:NoDefaultCurrentDirectoryInExePath -ErrorAction SilentlyContinue
$env:JAVA_HOME = 'C:\Users\koubato2\.jdks\ms-25.0.4.1'
Set-Location C:\Users\koubato2\IdeaProjects\NewShare
.\gradlew.bat :app:hotRun --mainClass=org.example.app.MainKt --auto *> <run folder>\hotrun.log
```

- `NoDefaultCurrentDirectoryInExePath` is set in Claude Code's shells. If it reaches the app, the
  hot-reload recompiler fails with `'gradlew.bat' is not recognized`, and nothing reloads.
- Ready when `app/build/run/main/main.pid` exists and `app/build/run/main/main.chr.log` ends with
  `Waiting for changes to input files...`.
- Then `resize_window` to 1000×820. At the default 800×600, Settings is clipped (F-020) and the
  video preview has almost no room (F-031). Run 09 tests the sizes themselves. Keep the window
  above the taskbar: 1000×900 runs under it on the RDP desktop.

Stop: `TaskStop` on the background task, then check the PID in `main.pid` is gone
(`Get-Process -Id <pid>`). Kill it with `Stop-Process` if not, then delete `main.pid`. The app
holds `app/data/app.lock`, so a stray instance blocks the next start.

**Restart the app after changing any Windows audio or privacy setting.** A running JVM keeps the
access state it started with; a fresh one picks up the change.

### 3.5 Logs to collect

- `app/data/logs/` (the app's own log) and the run's `hotrun.log`: copy the relevant lines into
  findings, and attach the whole files to the run folder when a finding needs them.
- `app/build/run/main/main.chr.log`: hot-reload and recompiler problems.
- Server side: `GET /uploads/:sessionId/attempts` and `GET /devices` through the admin API.

## 4. Test data

### 4.1 Naming

Everything a run creates on the web gets the prefix `E2E <MMDD>` (for example
`E2E 1023 Speech protocol`), so it can be found and cleaned up later. **Site names** can't
contain spaces (1–32 letters, digits, `_` or `-`, F-008), so sites use `E2E-<MMDD>-Site-A`, and
the app shows "Site: E2E-<MMDD>-Site-A". Patient codes are `E2E<MMDD>-<run>-<n>` (for example
`E2E1023-03-1`).

### 4.2 What run 1 builds

Two sites, so the "device belongs to another site" checks have somewhere to go:

| Site | Projects |
|---|---|
| `E2E-<MMDD>-Site-A` | Speech project, Video project |
| `E2E-<MMDD>-Site-B` | Speech project |

Two projects, each with one protocol, both in EN and CS:

**`E2E <MMDD> Speech protocol`** (in project `E2E <MMDD> Speech project`):

| # | Web task | Expected app task | Settings to use |
|---|---|---|---|
| 1 | Info / intro (if the designer offers one) | `INFO` | — |
| 2 | Phonation | `VOCAL/PHONATION` | length 5 s, 2 repetitions, can repeat, indicator on, example audio if the designer offers it |
| 3 | PATAKA / DDK | `VOCAL/PATAKA` | length 5 s, 1 repetition |
| 4 | Reading | `VOCAL/READING` | 1 repetition, a short passage |
| 5 | Monologue or picture description | `VOCAL/MONOLOGUE` or `PICTURE_DESCRIPTION` | length 20 s, **can skip** on |
| 6 | Questionnaire | `QUESTIONNAIRE` | one OPEN required (`.+`), one OPEN optional, one single choice, one multiple choice |

Patient fields: at least one required text field with a pattern, and one optional field (for
example `year_of_birth`). The patient code is always shown on the server; only the other fields
are masked, so the masking check in run 06 needs the optional field filled (F-039).

**`E2E <MMDD> Video protocol`** (in project `E2E <MMDD> Video project`):

| # | Web task | Expected app task | Settings to use |
|---|---|---|---|
| 1 | Phonation with **record video** on | `VOCAL/PHONATION`, `recordVideo: true` | length 5 s, 1 repetition |
| 2 | Monologue with **record video** on | `VOCAL/MONOLOGUE`, `recordVideo: true` | length 15 s, 2 repetitions, can repeat |
| 3 | PATAKA with record video **off** | `VOCAL/PATAKA` | length 5 s, so one protocol mixes filmed and unfilmed takes |

The web designer does not produce the standalone `VIDEO` task type (the `EMOTIONS` camera task
with PTZ). Run 1 confirms this and records it as a coverage gap, not a defect. §8 lists what
stays untested because of it.

### 4.3 Run context file

The orchestrator keeps `reports/<YYYY-MM-DD>-context.json`, which runners read and append to:

```json
{
  "date": "2026-10-23",
  "sites": {"A": {"id": 0, "name": "…", "tokenRef": "<token:E2E-1023-Site-A>"}, "B": {}},
  "projects": {"speech": {"id": 0}, "video": {"id": 0}},
  "protocols": {"speech": {"id": 0, "configVersion": null}, "video": {"id": 0}},
  "device": {"installationIdRef": "<installationId>", "serverDeviceId": null},
  "sessions": [{"run": "03", "patient": "E2E1023-03-1", "sessionId": "…", "uploaded": false}]
}
```

Real tokens go into a separate file, `reports/<YYYY-MM-DD>-secrets.json`, which is in
`.gitignore` and never quoted in reports.

## 5. Reporting

### 5.1 Layout

```
docs/testing/e2e/
  test-plan.md              this file
  findings.md               index of every finding, kept by the orchestrator
  tools/Capture-Screen.ps1  desktop or window screenshot
  reports/
    <YYYY-MM-DD>-context.json
    <YYYY-MM-DD>-secrets.json        (gitignored)
    <YYYY-MM-DD>-run-<NN>-<slug>/
      report.md
      screenshots/F-<nnn>-<slug>.png, step-<n>-<slug>.png
      logs/                          (only excerpts a finding needs)
```

### 5.2 `report.md`

```markdown
# Run <NN>: <title>

- Date / time: …   Runner: Sonnet   App commit: <git rev-parse --short HEAD>
- Preconditions met: yes / no (which)
- Result: passed / passed with findings / blocked

## Steps
| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | … | pass / fail / blocked / skipped | F-012 |

## Findings
(one block per finding, §5.3)

## Context updates
(what was added to the run context file)
```

### 5.3 Finding

Finding IDs are global and never reused: `F-001`, `F-002`, … The orchestrator assigns the next
free number from `findings.md`; a runner without one uses `F-<NN>-<n>` (run, counter) and the
orchestrator renumbers it.

```markdown
### F-<nnn>: <one-line title>

- **Severity:** blocker | major | minor | cosmetic | question
- **Area:** desktop-app | web-ui | server-api | integration | environment | docs
- **Run / step:** run 03, step 7
- **Expected:** what the spec or the previous screen says should happen (cite §).
- **Actual:** what happened.
- **Repro:** numbered steps from a known state.
- **Evidence:** `screenshots/F-<nnn>-<slug>.png`; log lines (redacted); server response.
- **Notes:** suspected cause if obvious from the log, related findings. No fix.
- **Status:** open
```

Severity:

- **blocker:** the run, or the process for a real examiner, can't continue.
- **major:** wrong data, data lost, wrong upload status, or an error the examiner can't recover
  from.
- **minor:** works, but misleading, missing feedback, or a needless extra step.
- **cosmetic:** layout, text, translation.
- **question:** spec silent or ambiguous.

### 5.4 Screenshots

- Take one **for every finding**, while the problem is on screen. Also take one at each step
  marked 📷 in §6, even when it passes, so later runs have something to compare against.
- App window: `mcp__compose-hot-reload__take_screenshot` with `save_to` in the run's
  `screenshots/`.
- Browser: Playwright `page.screenshot(path=…, full_page=True)`.
- Anything else (Windows dialogs, OBS, a frozen window):

  ```powershell
  docs\testing\e2e\tools\Capture-Screen.ps1 -Path <run folder>\screenshots\F-nnn-x.png -WindowTitle "SHARE"
  ```

- `take_screenshot` captures screen pixels, so whatever covers the SHARE window ends up in the
  image (after a restart it is often the terminal). Bring SHARE to the front first, for example
  with a click at (5, 5) through `tools/Drag-InWindow.ps1`, and look at the image.
- Before saving a screenshot, check that it shows no secrets, and mention in the finding what to
  look at:
  - Settings shows the site token **in clear text** (F-016) and the installation ID. Black them
    out, or rely on the semantic tree instead of a screenshot.
  - The installation ID also shows on the web's Devices page, on the session detail page and in
    clip file names. Black it out or crop it.
  - The app's logs and the JSON dumps from `/api/devices` contain the installation ID. Replace it
    with `<installationId>` before saving them under `reports/`.

## 6. Runs

Each run lists its preconditions, its steps and what each step should show. "Check" lines are
pass/fail criteria. 📷 marks a step that always gets a screenshot.

### Run 01: Web setup (sites, projects, protocols)

Precondition: preflight passed; admin login works.

1. Log in to the admin UI as master. 📷 dashboard.
2. Create the two projects (§4.2). Check: they appear in project management.
3. Create both protocols with the tasks in §4.2, EN and CS. 📷 each protocol in the editor.
   - Check the task designer offers every flag the app needs: can repeat, can skip, show indicator,
     record video, repetitions, length.
   - Use the in-browser protocol preview once. Note in the report anything it shows that the
     desktop app would not (that is not a finding by itself).
   - Confirm whether a standalone camera / `VIDEO` task type exists (§4.2). Record the answer.
4. Create Site A and Site B, assign the projects (§4.2), and copy both tokens into the secrets
   file. 📷 site page with the token masked or cropped.
5. API check: `GET /api/site-config/<token A>`.
   - Check: both protocols are present, each tagged with its project, and the tasks map as in the
     "expected app task" columns (type, subtype, `recordVideo`, `nrepetition`, `length`,
     `canSkip`).
   - Check: `languages` contains EN and CS, and every `titleKey`/`instructionKeys` entry has a
     string in both.
   - Save the response to `logs/site-config-A.json`.
6. `GET /api/site-config/<token B>`. Check: only the speech protocol.

### Run 02: First start, configuration and device registration

Precondition: run 01 passed; the app was started on an empty `app/data` (§3.3).

1. Start the app. 📷 main screen.
   - Check: with no token, Start is disabled or explains that a site token is needed, and there's
     no crash or error dialog.
2. Settings: enter token A, save. 📷 settings after saving.
   - Check: config is fetched, and the main screen shows "Site: E2E-<MMDD>-Site-A".
3. Settings: choose `CABLE Output (VB-Audio Virtual Cable)` as microphone. Settings has no camera
   selection (F-019); the app takes the first eligible camera, and run 05 checks that it is OBS.
   Note whether a level meter is shown.
4. Server: `GET /api/devices` and the admin **Devices** page. 📷 Devices page.
   - Check: a device with this computer's installation ID is listed under Site A **before any
     upload**, with app version `1.0.0` and the served `configVersion`.
   - Check: pending and failed counts are 0 (the check-in after the config refresh).
   - Record the device's server ID in the context file.
5. Switch the app language to CS and back to EN. 📷 main screen in CS.
   - Check: built-in strings and protocol strings switch, and nothing is shown as a raw key.
6. Close and restart the app. Check: the token, devices and language are remembered, and the
   config loads without a visible wait.

### Run 03: Speech session, happy path

Precondition: run 02 passed; the player is looping speech audio into CABLE Input.

1. Start the protocol → pick `E2E <MMDD> Speech protocol` (if a picker is shown). 📷 picker.
2. Patient form. Check that validation blocks continuing:
   - with the required field empty,
   - with a value that breaks the pattern.

   Then enter patient code `E2E<MMDD>-03-1`, and fill the optional field too (run 06 checks that
   it is masked). 📷 the validation message.
3. Calibration. 📷 calibration screen.
   - Check: the level moves with the audio, and the band matches settings (`[0.2, 0.5]` by
     default).
   - Adjust the player volume until the level is inside the band, then confirm.
4. Info task (if present): continue.
5. Phonation, rep 1: Start, wait for the timer, Stop. 📷 after stop.
   - Check: the indicator moves while recording.
   - Press Repeat once (take 2) and stop again. Then rep 2: one take.
   - Check: the example audio button (if configured) plays and is not recorded.
6. PATAKA: one take.
7. Reading. Check: the passage (last instruction paragraph) is **not** shown on screen (§4.1
   READING rule). One take.
8. Monologue: press **Skip**. Check: the task is marked skipped, and the flow continues.
9. Questionnaire. Check that it blocks continuing with the required OPEN question empty. Then fill
   all four questions and continue. 📷 filled questionnaire.
10. End of protocol → editor (if `enableEditor`). 📷 editor with the waveform.
    - Check: one segment per kept take, and phonation rep 1 uses take 2.
    - Leave it unchanged and finish.
11. Processing. 📷 the result.
    - Check: it completes without error.
    - Check: the session shows in **Sessions** with status processed / not uploaded.
12. Record the session ID (from Sessions, or `app/data/sessions/<folder>/metadata/`) in the
    context file.
13. Local archive check: open the processed ZIP read-only and compare it with
    [`upload_data.md`](../../dev/upload_data.md). Check:
    - one clip per kept take (phonation ×2, PATAKA, reading), and none for the skipped monologue,
    - `examination.json` with the questionnaire answers,
    - the master WAV once.

    List any difference as a finding.

### Run 04: Speech session, editing and a second patient

Precondition: run 03 passed.

1. Run the speech protocol again for `E2E<MMDD>-04-1`. This time do not skip the monologue, and
   repeat PATAKA twice.
2. In the editor, move the boundary of one PATAKA segment. 📷 before and after.
   - The handles (`editor.startBoundaryHandle`, `editor.stopBoundaryHandle`) need a real mouse
     drag: read the handle's bounds from the semantic tree, then
     `tools\Drag-InWindow.ps1 -FromX <x + 12> -FromY <y> -ToX <new x>`.
   - Check: finishing writes the edited timeline, and processing uses the edited range (clip
     length differs from the unedited one).
3. Open Sessions. 📷 list.
   - Check: both sessions are listed with patient code, date and status.
   - Reopen the run-03 session in the editor and close it without changes. Check: nothing is
     reprocessed or changed (§10.3 scenario 4).
4. Reprocess the run-04 session from Sessions. Check: it completes, and the ZIP is regenerated
   with the same session ID.

### Run 05: Video session

Precondition: run 02 passed; OBS virtual camera on with a recognisable scene; player looping.

1. Start `E2E <MMDD> Video protocol` for `E2E<MMDD>-05-1`.
2. Phonation with video. 📷 task screen.
   - Check: a small live camera preview is shown, and Start waits until it is previewing.
   - Record one take.
3. Monologue with video: rep 1 with two takes (Repeat once), rep 2 with one take.
4. PATAKA without video. Check: no camera preview, and the screen is the plain speech screen.
5. Processing. Check the ZIP:
   - `clips/` has an MP4 next to the WAV, under the same name, for each filmed kept take
     (phonation, monologue rep 1 take 2, monologue rep 2), and **no** MP4 for PATAKA,
   - `examination.json` has `videoTakes[]` entries only for filmed takes,
   - the raw `video/*.mjpeg` takes are not in the ZIP.
6. Play one MP4 (`ffprobe` for duration, codec `mjpeg`, and a frame grab with `ffmpeg -frames:v 1`).
   Check: the frame shows the OBS scene, not black. Attach the frame.
7. Camera failure fallback (no camera at start). **Stopping the OBS virtual camera does not do
   this:** the device stays and sends a placeholder picture, which the app films (F-030). Use one
   of these, and ask the user first, since each changes the machine:
   - disable `OBS Virtual Camera` in Device Manager (needs an elevated shell:
     `Get-PnpDevice -FriendlyName 'OBS Virtual Camera' | Disable-PnpDevice`, and
     `Enable-PnpDevice` afterwards), or
   - on hardware with a real USB camera, unplug it.

   If neither is approved, mark the step `skipped (F-030)`. Otherwise start a second video session
   (`E2E<MMDD>-05-2`). 📷 the phonation screen.
   - Check: within 10 s the screen says the camera is unavailable, and the task records audio only.
   - Finish the session, process it, and check there are no MP4s and no `videoTakes[]`. Turn the
     camera back on.
8. Camera lost during a take: start the protocol again (`E2E<MMDD>-05-3`) and start a filmed
   take. Stopping OBS mid-take doesn't interrupt it (F-030), so kill the app's ffmpeg capture
   process instead (`Get-Process ffmpeg | Stop-Process`, only the one the app started).
   - Check: the take's video ends and the audio take is kept.
   - Check: the app says so, and there's no crash.

### Run 06: Upload and the server record

Precondition: runs 03–05 left at least four processed, not-uploaded sessions; token A set.

1. Main → Upload. 📷 upload screen.
   - Check: "N sessions ready to upload" matches the processed sessions.
   - Check: each session is listed once.
2. Press Upload. 📷 during upload (progress bar) and after.
   - Check: the progress bar advances.
   - Check: every session ends as uploaded and disappears from the list.
   - Check: no error message.
3. Sessions. Check: every uploaded session shows status Uploaded, and has no upload action (§8.9:
   `Uploaded` is terminal).
4. Admin UI → Uploads (Nahrávky). 📷 list filtered to `E2E`.
   - Check: every session is there under Site A, with the right protocol, patient code and
     recorded date.
   - Check: status ends as **valid**. Wait, then refresh, while it says validating; it must never
     stay validating (web spec "never stuck validating" rule).
   - Check: video sessions match the `hasVideo` filter.
5. Open each session's detail. 📷 run-03 and run-05 details.
   - Check: the patient fields other than the patient code (which is always shown, by design)
     are masked until "Show values", and show the entered value after it (run-03 session, F-039).
   - Check: the exam summary lists the tasks, repetitions and the skipped monologue (run 03), and
     the questionnaire answers.
   - Check: checks pass.
   - Check: the attempts list shows one attempt with HTTP 200.
6. Download from the detail page: one WAV clip, one MP4 clip, and the whole ZIP.
   - Check: the ZIP's SHA-256 equals the local one (`Get-FileHash`).
   - Check: the MP4 plays (ffprobe).
7. Admin → Devices. Check: this device's pending and failed are 0 again after the batch (the
   check-in after the upload), and its last-seen time is recent.
8. Re-send idempotency, through the API since the app forbids it. Re-post one uploaded ZIP with
   `curl` and the same form fields the app sends (field order as in
   `server-upload-integration.md` §1). Check: HTTP 200 with the original receipt.
   Then post a different ZIP under the same `sessionId` with its own correct hash. Check: 409.

### Run 07: No token, quarantine and device assignment

Precondition: run 06 passed.

1. Stop the app, move `app/data` to `app/data.e2e-<MMDD>-run06` (keep it), start the app. It is
   a fresh installation again, with a new installation ID.
2. Without entering a token there is no config, so restore the config only:
   - copy `config/` from the run-06 data,
   - leave `settings.json` out, so there's no token.

   If the app can't start a protocol this way, record a `question` finding and do steps 3–6 with
   token **B** first, removing it before uploading.
3. Record and process a short speech session `E2E<MMDD>-07-1`.
4. Upload. 📷 result.
   - Check: the session fails with the localized "this computer is not registered to a site;
     contact your administrator" message, and stays listed as Failed.
   - Server: the upload is visible as quarantined (Uploads or Devices), with the device unassigned.
5. Admin → Devices: assign this new device to Site A (master). 📷.
6. App: press Upload again. Check: 200, the session becomes Uploaded, and it shows under Site A on
   the server with its original receipt time.
7. Device belongs to another site: enter token **B** in Settings, which triggers a config refresh
   and check-in.
   - Check: Settings shows "This computer is registered to another site". 📷.
   - Server: the device stays in Site A.
   - Then set token A back, and check the note disappears after the next refresh.

### Run 08: Error handling and robustness

Precondition: run 02 passed. Each part is independent; restart the app between parts.

1. **Microphone access denied** (covers F-001):
   1. Set the microphone privacy value to `Deny` (§3.1), restart the app, and open the speech
      protocol up to calibration. 📷 calibration screen.
      - Check: the screen says the microphone is unavailable or access is denied, instead of
        showing a flat zero level.
      - Check: confirming calibration or starting a take is not possible, or fails with a message
        rather than an `IllegalStateException` in the log.
   2. Set it back to `Allow`, restart, and check calibration works again.

   Leaving calibration with Back keeps the microphone open until the app restarts (F-051), so
   restart the app after any Back on calibration before the next part needs the microphone.
2. **No audio on the line**: pause the player.
   - Check: calibration shows a zero level and won't let a too-quiet level pass silently (or
     warns); note what happens.
3. **Server unreachable** (parts 3 and 4 together). Needs a firewall rule, which needs an
   **elevated** PowerShell; Claude Code's shell normally isn't one. Ask the user first, and skip
   if not approved. Before blocking, record and process one session (`E2E<MMDD>-08-2`) and don't
   upload it. Then the user runs:

   ```powershell
   New-NetFirewallRule -DisplayName "E2E block SHARE server" -Direction Outbound -Protocol TCP -RemoteAddress 192.168.122.183 -RemotePort 10001 -Action Block
   ```

   Restart the app **after** the rule is in place, so no connection opened earlier survives it.
   The runs below use that one app instance, part 4 first.
4. **Server unreachable at start** (same app instance):
   - Check: the cached config is used, the main menu shows the site, and the protocols can still
     be started (§6, offline config, scenario 5). Note whether anything says the app is offline.
   - Settings → Refresh configuration. Check: a "could not reach the server" message, and the
     cached config is kept.

   Then part 3, still blocked:
   - Upload `E2E<MMDD>-08-2`. Check: a network failure message, and the session stays Failed and
     retryable.
   - The user removes the rule: `Remove-NetFirewallRule -DisplayName "E2E block SHARE server"`.
     Retry, and check it uploads, and the server has one attempt (the blocked one never arrived).
5. **App killed mid-take**: during a take, kill the app process (`Stop-Process`), then start it
   again.
   - Check: crash recovery offers the interrupted session, the master WAV is intact up to the
     kill, and the session is marked recovered in Sessions (§8.4).
6. **Long upload** (optional): a video session long enough for its ZIP to take more than 2 minutes
   to upload. On the test VM's LAN that needs a ZIP of about 11 GB, or about 70 minutes of filmed
   recording. Run only if the user asks; otherwise record it as not covered.

### Run 09: Window sizes and layout

Precondition: run 02 passed; token A set; player looping; OBS virtual camera on; at least three
sessions in Sessions, one of them failed or not uploaded (runs 07–08 leave these).

The spec says fixed-size layouts are bugs (§13 decision 36), and that a screen too short for its
content scrolls rather than clips (§8, main menu; verified down to 1280×800). This run checks
that on every screen. Known findings to link rather than report again: F-020 (Settings clipped
at 800×600), F-031 (video preview squeezed), and the window size not being remembered.

**Sizes** (outer window, set with `resize_window`):

| ID | Size | Why |
|---|---|---|
| W1 | 640×480 | Below the default: is a minimum size enforced, and does content scroll? |
| W2 | 800×600 | The size the app opens at |
| W3 | 1024×768 | Small laptop or projector |
| W4 | 1280×800 | The size the spec says is verified |
| W5 | 1366×768 | Common laptop: wide but short |
| W6 | Full work area (the RDP desktop is 2292×1039, less the taskbar) | Large screen: nothing should stretch to be unusable |

**Method.** Walk through one session (`E2E<MMDD>-09-1`) and stop on each screen below. On each
screen, go through W1–W6, then move on, so one session covers all sizes. At each size:

1. `get_semantic_tree`, and check every control on the screen (buttons, fields, checkboxes,
   the waveform and its handles):
   - **C1 reachable:** it lies inside the client area, or `scroll` brings it fully into view.
     A control with zero or clipped bounds that no scroll reaches fails.
   - **C2 full size:** it is not squashed. Compare with its bounds at W4; a control shrunk to
     less than half its W4 height (F-020's 3 px button) fails.
   - **C3 no overlap:** its bounds don't intersect another control's (F-031's level ring over
     the "Start now" card).
2. 📷 at W2, W4 and W6, and for every failure. Also look at the screenshot for text cut off
   mid-line or overlapping (**C4**), which the tree doesn't show.
3. At W6 (**C5**): the content stays usable, for example the main menu's buttons stay in a
   column capped at about 560 dp, and text lines don't run across the whole screen.

**Screens:**

1. Main menu.
2. Settings, after Refresh configuration so the status line shows. Use the tree only, or black
   out the token and the installation ID (F-016).
3. Protocol picker.
4. Patient form, with the validation messages showing (required field empty, pattern broken).
5. Calibration.
6. Speech protocol tasks: phonation (long instruction and example button), PATAKA, reading,
   monologue (skippable), questionnaire with its required-field message showing.
7. Video protocol (a second session, `E2E<MMDD>-09-2`): phonation and monologue with video.
   - **C6:** the preview stays 200×150, or at least keeps 4:3 and is big enough to see whether
     the participant is in frame (spec decision 60).
8. Editor, with the waveform and handles. At W2, move one boundary with
   `tools/Drag-InWindow.ps1`, and check the edit lands where it was dropped.
9. Upload screen, with a failed row's message showing.
10. Sessions, with every row's buttons.

**Resizing during use** (window starts at W4):

1. **During a take:** start a phonation take, resize to W2 and back to W4 while it records.
   - Check: the timer keeps going, the take is kept, no error dialog, and nothing new in the log.
   - Check, after processing: the clip's length matches the take (`ffprobe`).
2. **During a filmed take:** the same on the video protocol.
   - Check: the preview comes back at the new size.
   - Check: the MP4's duration matches the WAV's within about 0.5 s.
3. **In the editor:** move a boundary, then resize to W2 and to W6.
   - Check: the handles stay at the same time, not the same pixel position, and the edit is kept.
4. **Minimise and restore** during calibration (a real click on the title-bar button, or the
   user does it). Check: the level meter moves again after restore.
5. **Restart** at W4. Note the size the app opens at.

Optional, only if the user sets it up: **display scaling** of 125 % or 150 % (Windows Settings →
Display, which may need the RDP session to reconnect). Repeat screens 1, 5, 6 and 8 at W2 and
W4. `Drag-InWindow.ps1` and the semantic tree assume 100 %, so coordinates need scaling there.

Report: a table of screens × sizes, each cell `pass` or the finding ID, plus a row per resize
check. Don't upload the run's sessions.

### Run 10: Cleanup check and summary (orchestrator)

1. Compare `findings.md` against the reports: every finding has a screenshot or a reason why
   there isn't one, and duplicates are linked.
2. Write a summary at the top of `findings.md`: runs passed, blocked, and findings by severity
   and area.
3. Restore the user's data (§7).

## 7. After the last run

1. Stop the app (§3.4).
2. Move the current `app/data` to `app/data.e2e-<MMDD>-final`, then move `app/data.pre-e2e-<YYYYMMDD>`
   back to `app/data`. Tell the user where the E2E data dirs are; delete nothing.
3. Leave the `E2E <MMDD>` sites, projects and uploads on the server. List them in the summary, so
   the user can delete them in the admin UI when done.
4. Remind the user to set the microphone privacy setting and the RDP audio settings back, if they
   want them as before.

## 8. Coverage and known gaps

| Area | Covered by | Not covered |
|---|---|---|
| Web setup and config mapping | 01, 02 | Protocol PDF (`protocolInstructionsPdfUrl`) unless the designer offers it |
| Config fetch, device registration, check-in | 02, 06, 07 | — |
| Speech tasks: all VOCAL subtypes the web emits, repeats, skip, questionnaire, info | 03, 04 | `COUNTING`, `CUSTOM` unless the designer offers them |
| Video on voice tasks (`recordVideo`) | 05 | — |
| Standalone `VIDEO` task (`EMOTIONS`, PTZ) | — | Not produced by the web (config alignment table, row 12). PTZ also needs a PTZ camera. |
| Editor, processing, reprocessing | 03, 04 | — |
| Upload statuses, batch, quarantine, conflict | 06, 07, 08 | Long upload unless asked (08.6) |
| Server record: list, detail, downloads, devices | 06, 07 | QC review, comments, CSV export (web features, not part of this process) |
| Errors: mic denied, camera loss, network, crash recovery | 05, 08 | Microphone unplugged mid-take (a virtual cable can't be unplugged without admin rights). No camera at start, unless the user approves disabling the OBS device (05.7, F-030). |
| Window sizes, layout, live resize | 09 | Display scaling unless the user sets it up; macOS |
| Audit log (reveals, edits, downloads) | — | No API or UI shows it (F-044); needs database access |
| Auto-updater | — | Out of scope for this plan |
