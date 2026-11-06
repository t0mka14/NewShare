# Run 18: app-notes round (task layout, no protocol, Sessions text, filming, logs), live E2E

- **Spec:** [`SPEC-app-notes-round`](../../../../SPEC-app-notes-round.md), AC 1–5, 7–15.
- **Date:** 2026-11-06, about 04:55–05:20.
- **Runner:** Sonnet. The orchestrator saved this report from the runner's hand-back.
- **App commit:** `b687053` plus the uncommitted working tree (hotRun of the working tree).
- **Web:** `http://192.168.122.183:10001`.
- **Driving the app:** the `compose-hot-reload` MCP tools only. `status` connected on every start, no OS clicks were needed, and `type_text` worked for the patient code, so F-116 did not come up.
- **Window sizes:** content 944x681 is the 960x720 window, and 984x781 is the 1000x820 window.
- **Result:** **passed with findings.** No functional defect in AC 1–5 and 7–15 as exercised. Upload, and real failure paths for the log fixes, were not exercised; see [Not covered](#not-covered).

## Orchestrator's check of this report

Checked independently of the runner:
- `settings.json` and `config.json`: current SHA-256 hashes match the backup hashes (`6285b10e…`, `a20db5d3…`).
- No `MainKt` process, OBS or ffmpeg still running; no `main.pid`.
- The real site token and installation ID appear nowhere in the evidence or scripts.
- `app-log-run18.log`: 0 lines with `E2E18-FAKE`, 0 WARN or ERROR lines.
- `D4` viewed: the preview sits off-centre in Idle, as F-18-1 reports.
- The `HP985` session was created at 03:05:43 today, before this run, and start-up recovery handled it at 04:59:48 (`app.log`).

From reading `HEAD`, not from running it: the old code has the same empty indicator box beside the preview, so F-18-1 looks pre-existing and not caused by this round. The old layout was not run to confirm that.

The independent reviewer changed code after this run (see "Review fixes" in the spec), so this run tested the tree before those fixes. `hotrun-*.log` and `leak-scan.txt` had the local user name replaced with `<user>` after the run.

The reviewer also noted a related case: with CIRCLE on a filming task, the row grows from 560 to 900 dp at Stop when the trace replaces the circle, so the preview moves about 170 dp right. That is from reading the code; the case wasn't run.

## Steps

Evidence is in `screenshots/` and `logs/`.

| # | Step | Result | Evidence / notes |
|---|---|---|---|
| A1 | Sessions in EN: no Process/Retry sentence (AC 10) | pass | `A1-sessions-EN`. |
| A2 | Sessions in CS | pass | `A2-sessions-CS`. No "Zpracovat / Zpracovat znovu" sentence. The row button still shows on unprocessed (recovered) rows. |
| A3 | Failed row keeps its button | not covered | No failed row existed. |
| B1 | Config really has no protocols | pass | Clinic `E2E18 no protocol`: `/api/site-config/<token:E2E18 no protocol>` returns `"protocols": []` (`logs/site-config-noproto-redacted.json`), and the app's cached config too (`logs/app-config-noproto.json`). |
| B2 | EN menu on the no-protocol site | pass | `B1-noprotocol-menu-EN`. New examination has `enabled:false`, and the line reads "No protocol is currently configured for this site." |
| B3 | Click on the disabled button | pass | Tree unchanged, still on the main menu. |
| B4 | CS line | pass, with a caveat (F-18-3) | `B2`, `B3`. "Pro toto pracoviště není aktuálně nastaven žádný protokol." with "Nové vyšetření" disabled. |
| B5 | Line changes on a live config refresh | partly covered | A refresh after the site's `defaultLanguage` changed kept the line, now in Czech. The no-protocol to has-protocol direction was not tested live: the call linking a study to the clinic was refused by the permission classifier, and the runner did not work around it. Unit tests cover both directions. |
| B6 | Paris token, restart: no line, button enabled (AC 8/9) | pass | `B4-paris-menu-no-line`. |
| C1 | WAVEFORM, 960x720, long text, Idle/Capturing/Stopped | pass | `C01`. Instructions 904x412 in all three states; they scroll; no indicator (room 0). |
| C2 | WAVEFORM, 960x720, 72 dp text | pass | `C02`, `C03`. Instructions 72 dp in all states. Waveform outline 500x250, centred. |
| C3 | WAVEFORM, 1000x820, Stopped | pass | `C04`. Centred. |
| C4 | WAVEFORM, 1000x820, reading (298 dp), Capturing | pass | `C05`. Outline top is 8 dp under the card; outline 500x206, centred. |
| C5 | WAVEFORM, 960x720, reading, Stopped and Capturing | pass | `C06`, `C07`. Outline 106 high, 8 dp gap, centred; instructions stay 298 dp. The trace renders correctly at the new height. |
| C6 | CIRCLE, 960x720: Idle and Capturing | pass | `C10`, `C11`. 160x160, centred; instructions 72 dp. |
| C7 | CIRCLE, Stopped, 1000x820 | pass | `C12`. The take's trace at full size, centred. |
| C8 | CIRCLE, 1000x820, reading, Idle | pass | `C13`. 160x160, centred in the leftover. |
| C9 | CIRCLE, 960x720, reading, all states | pass | `C14`–`C16`. Circle scaled to 106x106, 8 dp under the card, ring inside the box. |
| C10 | Buttons fully visible | pass | Start/Stop/Retry and Next 91 dp high, inside the window, in every tree read. |
| C11 | Matrix gaps | not covered | See [Not covered](#not-covered). |
| C12 | Comparison with the note's `app-task-screen.png` | pass, partly | Circle and centring match. |
| D1 | OBS and camera set up | pass | `OBS Virtual Camera` picked in Settings. CABLE Output audio: mean −16.6 dB, max −8.7 dB. |
| D2 | Take with the preview **hidden**, 1000x820 | pass | `D1`–`D3`. No `task.videoPreview` in the tree; instructions 944x455 in all states. |
| D3 | Take with the preview hidden, 960x720 | pass | `D7`–`D9`. Instructions 904x412, identical across states. |
| D4 | Take with the preview **shown**, 1000x820 | pass, with F-18-1 | `D4`–`D6`. Preview 200x150 in all states; instructions 298 dp. |
| D5 | Video in the processed session (AC 4) | pass | See the table below and `logs/d-video-probe.txt`. |
| D6 | Upload and server ZIP download | not done | Upload would have sent 11 sessions, 10 of them older `E2E1026-*` ones (F-18-5). The local ZIP was inspected instead. |
| D7 | AC 4a, camera failure with the preview hidden | not covered | No safe way in the test plan. Unit tests only. |
| E | Log scan | pass | See below. |
| F1 | Questionnaire (RBD-SQ, 13 questions) | pass | `F1`, `F2`. Frame, dividers and scrollbar as in run 17; validation and finish work. |
| F2 | Standalone video task | not covered | The web doesn't emit one, and no seeded protocol has it. |
| F3 | Main menu and errors | pass | `get_ui_error` no error, `status` ok, 0 failed reloads. |

### Video with the preview hidden and shown (AC 4)

Local session `2026-11-06_E2E18-FAKE_6aa0f35e…`. The ZIP holds the master, three MP4s, three WAVs, `examination.json` (three `videoTakes`, 1280x720 at 30 fps) and `manifest.json`.

| Take | Preview | MP4 | WAV |
|---|---|---|---|
| Phonation | hidden | 21.57 s, 647 frames | 21.70 s |
| Reading | shown | 24.47 s, 734 frames | 24.50 s |
| Monologue | hidden | 19.70 s, 591 frames | 19.65 s |

All are 53–67 MB with mean luma about 69–71, so not black. Hidden and shown takes have the same frame rate, and video length matches audio.

### Log scan

Scope: `app/data/logs/app.log` from 04:59 onward (82 lines, `logs/app-log-run18.log`), plus the three `hotrun-*.log` files and `app/build/run/main/main.chr.log`. No rotated file exists for today.

| Needle | `app.log` (this run) | hotrun logs | `main.chr.log` |
|---|---|---|---|
| `E2E18-FAKE` | 0 | 0 | 3 |
| Paris, no-protocol and original tokens | 0 | 0 | 0 |
| Installation IDs | 0 | 0 | 0 |
| Session folder names | 0 | 0 | 0 |
| `C:\Users` or `C:/Users` | 0 | 3 per file | 1 |
| Session ID (32 hex) | 45 | 45 (hotrun-3, same lines) | 0 |

- The app log has no WARN or ERROR lines.
- Session IDs are logged by design; the contract doc lists them as a decision to make before sending.
- `C:\Users` hits are Gradle and JBR start-up output, not app logs.
- `E2E18-FAKE` in `main.chr.log` is the hot-reload tooling recording each `type_text` call.
- **Limit:** no failure path ran, so this only shows a normal run is clean. AC 11, 12, 14 and 15 rest on their unit tests.

## Findings

### F-18-1: with a filming task and the WAVEFORM indicator, the preview jumps right when a take starts

Index: F-149. **Minor.** Desktop app, `VocalMiddleArea` / `TaskLevelIndicator`.

Bounds from the tree, reading task at 1000x820:
- Idle: no indicator; preview at x=492, so its centre is at 592 on a 984-wide window.
- Capturing and Stopped: indicator 492x198 at x=46; preview at x=742.
- The preview moves 250 dp to the right when Start is pressed.

Repro: set WAVEFORM in Settings, open a filming task whose text leaves room for the preview, and compare Idle with Capturing (`D4`, `D5`, `D6`).

Inferred, not confirmed: `TaskLevelIndicator` draws nothing for WAVEFORM with an empty history, while the Row still holds its slot, so the width depends on the trace. CIRCLE beside a preview was not checked.

### F-18-2: the waveform fills down to the timer line with no gap

Index: F-150. **Question.** With a full leftover, the waveform outline's bottom edge meets the top of the timer box, so the digit sits directly under it (`C05`, `D5`). The 8 dp gap is only above the indicator, as specified.

### F-18-3: the Czech no-protocol line can't be reached by language choice on a protocol-less site

Index: F-151. **Question.** Settings' language options come from `languages` in the served config, and a protocol-less site serves only its default language. A saved `language: "cs"` was not applied. The Czech line showed only after the clinic's `defaultLanguage` was set to `cs` on the server. Low impact: this comes from how the web builds `languages`, not from the new line.

### F-18-4: the first start recovered a pre-existing interrupted session

**Environment.** Session `2026-11-06_HP985_cdaedf33…`, created at 03:05 today and not by this run, was handled by normal start-up recovery at 04:59:48. It now shows as Not processed / Recovered, and the recovery dialog appeared once (`B0-recovery-dialog`). No backup of the folder was taken, so the recovery can't be undone.

### F-18-5: the Upload screen can't upload a single session

**Environment, known from run 17.** Upload lists every ready session, so the server-side download check was not possible without sending 10 older sessions.

### Observations, not defects

- The monologue text on `E2E18 filming` reads "for at least seconds", because the runner's script omitted `duration`. It's a property of the test protocol.
- Phonation shows Play example with no example configured on these protocols. It wasn't pressed.

## Restoration

- App stopped; no `MainKt` process, no ffmpeg, `main.pid` deleted. OBS, started by the runner, was closed. `wmplayer` was already running and was left alone.
- `settings.json` and `config.json` restored and verified by SHA-256 (`logs/backup-hashes.txt`, `logs/restore-hashes.txt`). The backup copies are in the session scratchpad, since they hold the real token.
- Sessions created by this run stay, all processed and **not uploaded**:
  - `2026-11-06_E2E18-FAKE_6aa0f35efe574c9e8ae08b9233718c3c`
  - `2026-11-06_E2E18-FAKE-W_929d42e5a00740ea824a68770381f2e7`
  - `2026-11-06_E2E18-FAKE-C_dc050508be93478eb7bad5383324b478`
- `app/data/logs/app.log` was appended to.

## Left behind for the user

On the web server:
- Clinic `E2E18 no protocol`, id 8, no study. Its `config_json` is `{"defaultLanguage":"cs"}`.
- Protocol `E2E18 filming` in Test Study 001 (group 16): v1 (id 36, EN) and v2 (id 37, EN and CS). **It is served to every clinic of that study, not only Paris, and it added `cs` to Paris's served `languages`** (`configVersion` 2026-11-06.040122).
- Two new unlabelled computers: one registered to `E2E18 no protocol`, one to Paris. This machine's original device is untouched.

No uploads and no sessions on the server came from this run.

Locally: `docs/testing/e2e/scripts/run-18/`, run in this order: `create_filming_protocol.py`, `add_cs_variant.py`, `patch_noproto_cs.py`, `set_token.py`, `fetch_config.py`, `leak_scan.py`. They need `tokens.json` from run 17's `login.py`, and `site_tokens.json`.

## Not covered

- AC 4a, camera failure with the preview hidden: unit tests only.
- Upload of the filmed session and a server-side ZIP download.
- A failed Sessions row.
- The live line change between no-protocol and has-protocol.
- AC 5 at 784x561, and a leftover between 0 and 80 dp at 960x720: the window minimum prevents the size, and hidden-indicator cases seen were room 0 and about 57 dp.
- CIRCLE beside a filming preview; CIRCLE with reading at 1000x820 in Capturing and Stopped; WAVEFORM with reading at 960x720 in Idle.
- AC 11, 12, 14 and 15 against real failures.
- Standalone video task.

## Deviations from the brief

- No upload (F-18-5); the local ZIP was inspected.
- Backups kept in the scratchpad, hashes in the evidence folder.
- Screenshots showing the clear-text site token were deleted, as was one OBS frame grab. The filming screenshots that show a third-party TV frame in the preview (`D4`–`D6`, `run-18b/screenshots/`) are not committed; they are local only.
- A Czech variant of `E2E18 filming` was created and the test clinic's `defaultLanguage` changed, to reach the Czech line.
- The study-link call for the live-refresh test was refused, and its unused helper script deleted.
- The brief's "short text" take used the reading task (424 characters), the shortest available. Whether the preview shows depends on window size and text length, as intended.

## Run 18b: resizing during a filmed take

Added after the `VideoFrameHolder` fix, because resizing across the preview threshold during a take had never been run live. Runner: Sonnet. Evidence is in `run-18b/`.

**Result: passed.** No crash, no UI error, no `hs_err` file, no WARN or ERROR in the log.

Orchestrator's check: config hashes match the backup, no app, OBS or ffmpeg process remains, the evidence has no token or local user name, and the frame counts below match `app-log-run18b.log`.

| Window | Content | Preview | Indicator | Instructions |
|---|---|---|---|---|
| 1000x820 | 984x781 | present, 200x150 | 492x198 | 944x298 |
| 960x720 | 944x681 | absent | 492x98 | 904x298 |

| Take | Resizes | MP4 | WAV | Rate |
|---|---|---|---|---|
| Reading, resized, session B | 52, over about 38 s | 55.77 s, 1673 frames | 55.75 s | 30.02 fps against wall time |
| Reading, baseline, session A | 0 | 44.17 s, 1325 frames | 44.15 s | 30 fps nominal |
| Reading, resized, session A (rejected by Retry, raw file only) | 44, over about 52 s | 2134 packets | n/a | 30.01 fps against wall time |

- Tree reads showed the preview present at 1000x820 and absent at 960x720 every time, and the instructions at 298 dp.
- The picture stayed live when it came back: the presenter's pose differs between shots.
- Mean luma: resized 69.0–71.1, baseline 68.9–71.1.
- Working set rose from 518 to about 600 MB over the first take, then stayed at 566–606 MB. The samples are few and include encoding and GC, so no leak can be inferred or excluded.
- `app-log-run18b.log` (65 lines): 0 WARN or ERROR, 0 exceptions, 0 `E2E18-FAKE`, 0 `C:\Users`, 0 session folder names.

**Observations:**
- After rapid resizing the content settled 3 dp taller than requested, until the size was sent again. Instructions were unaffected. Not investigated.
- The preview jump (F-149) was seen again.

**Not covered:** intermediate sizes, resizing during Idle or Stopped, the CIRCLE indicator, a real drag of the window edge (`resize_window` sets the size directly), and a native-memory profile.

**Left behind:**
- Local sessions, processed and not uploaded: `2026-11-06_E2E18-FAKE-R_40e5fa1c42b14833a52f550a9cca619b` and `2026-11-06_E2E18-FAKE-R_fa3de1e3e8234a2cb783638dd2c1468b`.
- One more unlabelled device row on the server, registered to Paris.

The code changed once more after this run, but only `redactPaths` and tests, so no UI behaviour tested here changed.
