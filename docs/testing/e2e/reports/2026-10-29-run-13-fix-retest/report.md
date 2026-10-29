# Run 13: Targeted re-test of fix round 2 (criterion 8 of [fix-round-2026-10-29-spec](../../fix-round-2026-10-29-spec.md))

- **Date / time:** 2026-10-29, about 01:18–01:40 local.
- **Runner:** Sonnet.
- **App commit:** `8940fe2` plus the uncommitted fix round 2 (hotRun of the working tree).
- **Preconditions met:** yes. OBS and wmplayer running, mic privacy `Allow`, token set.
- **Result:** **passed**.
  - F-104, F-106, F-122, F-129 and F-132 are confirmed fixed. For F-132 the Czech texts were not seen live; see the findings table.
  - No new finding of major severity or above.
  - One cosmetic finding (F-13-1).

The orchestrator saved this report from the runner's hand-back.

## Steps

| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1a | F-104: the Reprocess button shows only on failed or not-processed rows 📷 | pass | See [step 1a](#step-1a-reprocess-button). |
| 1b | Press Reprocess 📷 | pass | Both rows failed as expected, because they hold no audio. The success path was covered by 13-2. See [step 1b](#step-1b-press-reprocess). |
| 1c | Editor on 09-1: Accept with no change, then with a boundary change 📷 | pass | See [step 1c](#step-1c-editor-on-09-1). |
| 1d | The message clears after leaving Sessions | pass | After the message: Back to the main menu, then Sessions again. There is no `sessionBrowser.resultMessage` node in the tree. `step-1d-message-cleared.png`. |
| 1e | Czech and English labels and message | pass | Czech seen live; the not-processed label was not seen. See [step 1e](#step-1e-czech-labels). |
| 1f | Log lines | pass | See [step 1f](#step-1f-log-lines). `logs/app-step1.log`, IDs redacted. |
| 2 | F-106 and F-132: kill the app's ffmpeg mid-take (13-1, English) 📷 | pass | One message below the picture, no overlap, picture cleared. See [step 2](#step-2-camera-killed-mid-take). |
| 3 | F-122: 21 resizes during filmed takes (13-2) | pass | See [step 3](#step-3-resizes-during-filmed-takes). `step-3-after-resizes.png`. |
| 4a | Device loss, first attempt on 13-2: disable the `CABLE Output` endpoint | not effective | The app was later found closed, probably by accident. No finding recorded. See [step 4a](#step-4a-first-device-loss-attempt). |
| 4b | Crash recovery of 13-2 | pass | Recovered, master intact, processed. See [step 4b](#step-4b-crash-recovery-of-13-2). |
| 4c | Real device loss, second attempt on 13-3: filmed take, `Audiosrv` stopped by the user for 15 s 📷 | pass | Interruption dialog, no crash, Resume worked, session processed. See [step 4c](#step-4c-real-device-loss-on-13-3). |
| 4d | F-129 numbers | pass | The three values all equal 2,791,200. See [step 4d](#step-4d-f-129-numbers). |
| 5 | End state | pass | See [step 5](#step-5-end-state). |

### Step 1a: Reprocess button

- **Processed and Uploaded rows** show only "Open editor", disabled on the Uploaded rows. These are 12-2, 12-1, 09-3, 09-2, 09-1, 08-2, 08-5 and 07-1.
- **08-6** (Processing failed) shows "Retry processing".
- **08-1** (Not processed) shows "Process".
- Screenshots: `step-1a-sessions.png`, `step-1a-sessions-scrolled-reprocess-buttons.png`.

### Step 1b: Press Reprocess

- **08-6 and 08-1:** "Retry processing" and "Process" both went to the Processing screen, which then failed with "Nothing was recorded in this session, so there is nothing to process." Back and Retry were shown. These two recovered sessions hold no audio.
  - After Back there is no result line, which is the intended behaviour after a failure.
  - 08-1's row then read "Processing failed", with "Retry processing".
  - Screenshot: `step-1b-08-6-retry-failed.png`.
- **The success path** was covered by 13-2 (see step 4b):
  - recovered, shown as "Not processed" with "Process";
  - pressing Process led to Sessions showing "Session E2E1026-13-2 processed (4 clips)."
  - Screenshot: `step-1b-process-13-2-result.png`.

### Step 1c: Editor on 09-1

- **No change:** "No changes – the session was not reprocessed." (`step-1c-no-changes.png`).
- **With a change:**
  - A drag with `Drag-InWindow.ps1` from (782,400) to (742,400) moved End from 0:55.8 to 0:54.5.
  - Accept then showed "Session E2E1026-09-1 processed (5 clips)."
  - Screenshots: `step-1c-editor-after-drag.png`, `step-1c-processing.png`.
  - Processing took about 2 s, so the Processing screen itself was not captured.

### Step 1e: Czech labels

- **Seen in Czech:**
  - "Sezení";
  - "Otevřít editor";
  - "Zpracovat znovu" on failed rows;
  - the updated info text;
  - the message "Beze změn – sezení nebylo znovu zpracováno."
- Screenshots: `step-1e-sessions-cs.png`, `step-1e-message-cs.png`.
- **Not seen:** the Czech "Zpracovat" label (for not-processed rows), because no such row existed at the time.
- The language was switched back to English afterwards.

### Step 1f: Log lines

- `processing started (session <32-hex id>)`.
- `processing done: 5 clips (session ...)` on success.
- `processing failed: NothingToProcess (session ...)` on failure.
- Only the session ID appears: no patient code and no path.

### Step 2: Camera killed mid-take

- **How:** the app JVM is the parent of `ffmpeg`. It was stopped with `Stop-Process` 9 s into the take.
- **Message:** the tree and the screenshot show exactly one text, `task.videoAudioOnly`: "Camera lost — recording audio only."
- **Position:**
  - The message sits below the 200x150 box: the box spans y 420–570, the text y 578–616.
  - It wraps to two lines.
  - It does not overlap the picture, the level meter, the timer, STOP or Next.
- **Picture:** the preview's `stateDescription` is `empty` and the box is black, so the frozen frame is cleared.
- Screenshot: `step-2-camera-lost-en.png`.
- **Log:** one ERROR line and one WARN line, with no stack trace.
- **Czech text:** not run live. The Czech strings `task.video.cameraLost` ("Kamera byla ztracena — nahrává se pouze zvuk.") and `error.*` exist in `BuiltinStrings.kt`.
- **Session 13-1:**
  - finished as Processed (4 clips);
  - the phonation clip has a partial MP4: 8.97 s against a 21.15 s WAV;
  - the later tasks are audio-only.
  
  This matches run 05 step 8 and findings F-034 and F-107, so it is not a new finding.

### Step 3: Resizes during filmed takes

- 21 `resize_window` calls during the phonation take, alternating 960x720, 1000x820, 1024x768 and 1100x800.
- 4 more during the monologue take.
- No dialog.
- The log has no "recorder interrupted", "watchdog" or READ_STARVATION line, and no "watchdog tick late" warning.

### Step 4a: First device-loss attempt

- **What didn't work:**
  - The orchestrator reported that disabling the `CABLE Output` endpoint did nothing, the same as F-131.
  - Disabling the driver failed with "Generic failure", because the driver is in use.
- **Audio service stop:** the user then stopped Windows Audio (`Audiosrv`) from 01:30:38 to 01:30:53.
- **What was found afterwards:**
  - The app was closed.
  - `app.log` has no line after 01:27:49. That alone is not abnormal: PATAKA takes write no log lines.
  - `hotrun.log` ends with `^C`.
- **Conclusion:** the user may have closed the app by accident, so no finding is recorded.
- **For the record:**
  - 13-2's master holds 246.05 s of audio. It started at 01:26:26, so it ends at about 01:30:32, within one 5 s header-patch interval of the `Audiosrv` stop.
  - That timing can't be told apart from an accidental close.
  - The same `Audiosrv` stop in step 4c did not kill the app.

### Step 4b: Crash recovery of 13-2

- **Restart:** `stop-app.ps1` was run (stale `main.pid` removed) and the app restarted.
- **Recovery dialog:** "Interrupted sessions recovered", listing `E2E1026-13-2`, with Open Sessions.
- **Row:** "Not processed" + "Recovered" + "Process".
- **Master intact:** header data size 23,620,800 = file size 23,620,844 minus the 44-byte header.
- **Processing:** pressing Process gave "Session E2E1026-13-2 processed (4 clips)."
- Screenshot: `step-4-recovered-13-2-sessions.png`.
- **Audio:** the signal was back after the restart (max -14.9 dB, wmplayer still running).

### Step 4c: Real device loss on 13-3

- **Log:**
  - "recorder interruption requested reason=READ_STARVATION" at 01:34:44.254;
  - then "recorder interrupted at sample=2791200" on the `audio-capture` thread.
- **Dialog:** "The microphone was disconnected. Reconnect it or choose another device to continue.", with Reconnect and Resume (`step-4-interruption-dialog.png`). The app did not crash.
- **Resume:** after the service was back, Resume reopened the microphone at 01:36:17 and the task returned to START (`step-4-after-resume.png`).
- **Completion:** the phonation take was redone and all tasks finished. Processed (4 clips) (`step-4-13-3-processed.png`).
- **2-part master:**

  | Part | Frames | Duration |
  |---|---|---|
  | `session_master.wav` | 2,791,200 | 58.15 s |
  | `session_master.part2.wav` | 3,385,500 | 70.53 s |

- **Clips:** for the three filmed tasks, MP4 and WAV are within 0.15 s of each other.

### Step 4d: F-129 numbers

| Source | Sample |
|---|---|
| `examination.json`, `interruptions[0].sampleOffset` | 2,791,200 |
| `RECORDING_INTERRUPTED` event in `timeline.events.jsonl` | 2,791,200 |
| Part-1 frames (data size 5,582,400 / block align 2) | 2,791,200 |

All three are equal. `RECORDING_RESUMED` is at 2,793,600, 50 ms later. Evidence: `logs/13-3-interruption-check.txt`.

### Step 5: End state

- App stopped: process gone, `main.pid` removed, `shutdown.log` present.
- `obs64` and `wmplayer` running.
- Mic privacy `Allow`.
- `CABLE Output` status OK; `Audiosrv` running.
- Upload never pressed.
- The orchestrator deleted the WAITING marker files.

## Previously fixed findings

| Finding | Verdict | Evidence |
|---|---|---|
| F-104: Reprocess button and Sessions feedback | **confirmed fixed** | Steps 1a–1f and 4b. See [the F-104 detail](#f-104-confirmation-detail). |
| F-106: camera failure texts overlap, frozen frame | **confirmed fixed** | Step 2: one message below the picture, no overlap, picture black. |
| F-122: false "microphone disconnected" interruption | **confirmed fixed** (not reproduced) | Step 3: 25 resizes over two filmed takes, no interruption, no watchdog line. The finding was intermittent: 0 out of about 24 attempts in run 12 too. So this is supporting evidence, not proof; the proof is in the unit tests. |
| F-129: interruption sample offset off by 1500 frames | **confirmed fixed** | Step 4d: on a real interruption, `sampleOffset`, the event sample and the part-1 frame count are all 2,791,200. |
| F-132: video texts in Czech and English | **confirmed fixed in English**; Czech **not covered live** | The English text was seen in step 2. The Czech keys for `cameraLost`, `audioOnly` and all `error.*` are in `BuiltinStrings.kt` (lines 463–470), and unit tests cover them. The Czech Sessions texts were seen live in step 1e. |
| F-131: an endpoint disable does not interrupt | still open, unchanged | Disabling the `CABLE Output` endpoint again had no effect. Stopping `Audiosrv` is the method that reaches an open line (step 4c). |

### F-104 confirmation detail

- **The button** appears only on failed or not-processed rows, with the right labels.
- **The result line:**
  - "processed (N clips)" after both the Process button (13-2) and Accept with changes (09-1);
  - the "No changes" message after an unchanged Accept;
  - cleared when you leave Sessions.
- **Czech strings** shown.
- **Log lines** carry the session ID only.

## Findings

### F-13-1: The task layout shifts when the "Camera lost" message appears
Index ID: **F-133**
- **Severity:** cosmetic
- **Area:** desktop-app
- **Run / step:** run 13, step 2
- **Expected:** the failure message does not move other controls.
- **Actual:**
  - When the message appears, the instruction panel shrinks from 352 to 316 px high.
  - The level meter moves up, from y 456 to y 438.
  - The message sits between the picture and the timer; its bottom edge, at y 616, touches the top of the timer, also at y 616.
  - Nothing overlaps and everything stays readable.
- **Repro:** start a take on a filmed task, then kill the app's `ffmpeg.exe`.
- **Evidence:** `screenshots/step-2-camera-lost-en.png`.
- **Notes:** very minor. The layout has no spare room for a 200 px-wide message line.
- **Status:** open

Seen again, not new:
- The "Camera lost" message and audio-only recording stay on all later tasks of that session (F-107).
- The failed take still gets a partial MP4, which is by design (F-034).

Observation, not a finding: right after a screen change, `get_semantic_tree` and `take_screenshot` can briefly return the previous screen. Wait about 1–2 s after a click before reading. Two screenshots were retaken for that reason.

## Notes

- **13-2, first attempt:** ended with the app closed, possibly by accident (step 4a). Its recording was recovered and processed.
- **Sessions 13-1, 13-2 and 13-3:** left Processed, not uploaded.
- **Session 09-1:** its editor was changed (End now 0:54.5) and the session reprocessed, so it has a different boundary and 5 clips.
- **Disk:** none of the takes ran long. The largest ZIP is a few tens of MB.
- **Secrets:** no tokens or installation IDs saved. Session IDs in saved logs are redacted as `<sid>`. No Settings screenshot or tree was taken. One screenshot that showed the orchestrator's terminal was deleted.
- **Czech camera test, live:** not run, because it would need a second filmed session. The strings are checked in the source and by unit tests only.

## Context updates

New sessions, none uploaded:

| Patient | Session ID | State |
|---|---|---|
| `E2E1026-13-1` | `995ffb616adc47a095d4e2ef2998ccf6` | Video; camera killed in take 1; processed. Partial phonation MP4 (8.97 s against a 21.15 s WAV); later tasks audio-only. |
| `E2E1026-13-2` | `3b5a4018d24d422f9bb59f29a92d6fad` | Video; app closed during PATAKA; recovered; processed with the Process button (4 clips). |
| `E2E1026-13-3` | `26afec68628144f08c61d035f32fe155` | Video; real interruption (`Audiosrv` stop) and Resume; 2-part master; processed (4 clips). |

Other changes:
- `E2E1026-09-1` was reprocessed after a boundary change.
- `E2E1026-08-1` and `-08-6` are both "Processing failed", because nothing was recorded in them.
- The app language is English.

## Scripts and logs

- **Scripts:** `docs/testing/e2e/scripts/run-13/check_interruption.py` and `README.md`. A run 13 paragraph was added to `docs/testing/e2e/scripts/README.md`.
- **Logs** in `logs/`:
  - `app-step1.log`
  - `app-redacted-run13-after-restart.log`
  - `13-3-interruption-check.txt`
  - `hotrun.log`
  - `hotrun2.log`
- **Screenshots** (16) in `screenshots/`: `step-1a-*`, `step-1b-*`, `step-1c-*`, `step-1d-*`, `step-1e-*`, `step-2-camera-lost-en.png`, `step-3-after-resizes.png`, `step-4-*`.
- **Not changed:** nothing under `app/src`. No commits, no uploads, no server deletions.

## Orchestrator follow-up: independent review of fix round 2 (2026-10-29)

A fresh reviewer checked criteria 1–8 (all PASS) and found three defects in the recording path, fixed the same day before the commit.

### F-134: The RECORDING_RESUMED event carries a later offset than the cut
Index ID: **F-134**
- **Severity:** minor  **Area:** desktop-app  **Run / step:** review of fix round 2 (seen in run 13 step 4d: resumed 2,793,600 vs cut 2,791,200)
- **Actual:** `SessionComponent.onResumeRecorded` logged `RECORDING_RESUMED` with the live `writtenSamples`, already past the cut once the new part was being written. `RecoverSessionsUseCase` used that value as the base of the newest part, so a crash after a Resume shifted recovered offsets.
- **Status:** fixed (2026-10-29), not yet re-run. The event carries the cut (`pending.sampleOffset`); recovery bases the newest part on `examination.interruptions.last().sampleOffset` (fallback: the event, then 0). Tests: `SessionComponentTest` "RECORDING_RESUMED carries the cut offset…" (red: 98400 vs 96000), `RecoverSessionsUseCaseTest` "crash after a resume bases the newest part on the cut…" (red: 2798400 vs 2796000).

### F-135: A wedged capture thread from an old episode could write into the resumed part
Index ID: **F-135**
- **Severity:** minor  **Area:** desktop-app  **Run / step:** review of fix round 2
- **Actual:** after the watchdog finalized a wedged episode, `resume()` started a new episode without retiring the old thread; when its read returned it could write into the new part, update the counter and raise a false Interrupted. `stop()` racing the wedged finalize could finalize one writer twice.
- **Status:** fixed (2026-10-29), not yet re-run. Episode id + lock: a capture thread acts only while its episode is current; each writer is finalized exactly once. Tests: `JvmContinuousSessionRecorderInterruptionTest` "an old capture thread released after the watchdog finalized its wedged episode writes nothing into the resumed part" (red without the token), "stop racing the watchdog's wedged finalize never ends in Failed" (regression guard; the race was not hit on the old code).

### F-136: Consecutive late watchdog ticks could hide a real device loss
Index ID: **F-136**
- **Severity:** minor  **Area:** desktop-app  **Run / step:** review of fix round 2
- **Actual:** every late tick reset the read baseline, so under sustained load a lost device was never reported.
- **Status:** fixed (2026-10-29), not yet re-run. A late tick still checks the device; at most 3 consecutive resets. Tests: "F-122 sustained late watchdog ticks with the device gone still interrupt", "…cannot hide a stall longer than the limit…" (both red with the old reset-always rule).

