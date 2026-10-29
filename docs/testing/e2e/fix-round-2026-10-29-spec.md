# Fix round 2 (2026-10-29): desktop minor findings F-104, F-106, F-122, F-129

## Context
Fix round 1 (commits `3e35f96`, `8940fe2`) fixed the E2E majors. This round covers the 4 open
**minor** desktop-app findings. The 13 web and 1 server minors come later, in a separate round in
`task_protocoller_web_app`.

The workflow is the same as round 1 (memory `e2e-fix-workflow`):
- one Sonnet engineer per fix, one at a time;
- I review the diff, check that each new test fails on the old code, and run `./gradlew.bat :app:test`;
- I record the fix in `findings.md` and in the finding's report;
- a targeted Sonnet E2E re-test (run 13) at the end;
- I commit only when asked.

Decisions the user made on 2026-10-29:
- **F-122:** investigate first, and fix only if there is a concrete cause. There is one, and the
  user picked "fix the cause and make the watchdog safer".
- **F-104:** Reprocess is shown only for failed or not-processed sessions, and Sessions shows a
  message after processing.

What the investigation found:
- **F-122:** in `JvmContinuousSessionRecorder.kt`, the capture thread runs `channel.force(true)`
  every 5 s inside `maybePatchHeader`, via `WavFileWriter.kt:50`. That disk sync can block it for
  more than 1 s. The watchdog (`:265-273`) then calls `onLineFailure(READ_STARVATION)` on any
  1000 ms gap. It does not allow for its own late wake-ups and does not check whether the device
  still exists. `TaskComponent.kt:609-618` then maps every interruption to `DEVICE_LOST`.
- **F-129:** `onLineFailure` (`:281`) reads `atSample` before it closes the line. The capture
  thread then writes one last partial chunk of 1500 frames, because `currentWriter` is cleared
  only at `:293` and the state changes at `:302`. The watchdog thread also finalizes the writer
  while the capture thread may still be writing, which is a race on a writer that is not
  thread-safe.
- **F-104:** `RootComponent.kt:376` simply pops back to Sessions when processing succeeds, so
  nothing is shown. Accept with no changes (`needsReprocess` false,
  `EditorComponent.kt:356-390`) pops without any message. `ProcessSessionUseCase` logs neither
  the start nor a success.
- **F-106:** in `VideoTaskBody.kt:74-118` (`VideoPreview`), the `VIDEO_ERROR` and
  `VIDEO_AUDIO_ONLY` texts are both shown, stacked inside the 200×150 box over the last frame.
  `VideoSurface.kt:55-68` keeps that frozen frame.

## Spec

**Goal:**
- The microphone is no longer falsely reported as lost.
- The interruption records agree with the audio files.
- The examiner sees what happened after processing from Sessions, and after a camera failure.

### Acceptance criteria
1. **F-129:** after an interruption, `Interruption.sampleOffset`, the `RECORDING_INTERRUPTED`
   event's sample and part 1's frame count are all equal.
   - Only the capture thread writes to the writer; no frame is written after the cut is recorded.
   - Test: a real `JvmContinuousSessionRecorder` with a fake `CaptureLineOpener` whose `read`
     blocks, then returns a partial chunk on `close`. It fails on the old code (2424000 vs 2425500).
2. **F-122, disk sync:** the periodic header patch no longer runs `force(true)` on the capture
   thread.
   - The sync is either dropped from the periodic patch and kept in `finalizeAndRename`, or moved
     off the capture thread.
   - Crash recovery still works: the existing recovery tests, and the run 08 part 5 behaviour
     (header patched, master intact).
3. **F-122, watchdog:**
   - A read gap counts as a lost microphone only if the device is gone or the line is closed, or
     if the gap lasts more than 3 s.
   - When the watchdog itself wakes up late (a GC or process pause), it resets its baseline
     instead of firing.
   - Tests with a fake clock or opener: a 1.5 s stall with the device present doesn't interrupt;
     a removed device interrupts within about 1 s; a 3 s+ stall interrupts.
   - The line buffer goes from 150 to **500 ms** (user decision, 2026-10-29). Spec §8 and the
     `ContinuousSessionRecorder` doc are updated: buffer plus chunk is at most about 600 ms.
     Normally the offset skew is still at most one chunk; it grows only right after a stall.
   - Test: a 400 ms stall with the device present loses no samples and doesn't interrupt.
4. **F-104, Reprocess:** on Sessions, the button shows only for rows that are `Processing failed`
   (labelled "Retry processing") or `Not processed` ("Process"). It is hidden for Processed and
   Uploaded rows.
   - New CS and EN strings in `BuiltinStrings`.
   - Test in `SessionBrowserComponentTest` or the UI test.
5. **F-104, feedback:**
   - After processing that was started from Sessions (Reprocess, or Accept with changes), Sessions
     shows one line, "Session <patient code> processed (N clips)", or the failure state as before.
   - After Accept with no changes it shows "No changes – the session was not reprocessed."
   - The message clears when you leave Sessions.
   - The log gets "processing started" and "processing done (N clips)" lines with the session ID
     only, never the patient code or a path (the F-059 rule).
   - Tests in `RootComponentTest` (extending the F-066 tests at `:477` and `:488`) plus a UI test.
6. **F-106:** a camera failure on a filmed task shows **one** readable message.
   - The message is either the error, or "Camera lost – recording audio only". It sits outside
     the 200×150 picture (below it) or replaces the picture. It never overlaps.
   - The frozen last frame is cleared to the black background.
   - Test in `VideoTaskScreenUiTest`: error and audio-only together, no overlap, and the text is
     inside the window at 944×681.
7. `./gradlew.bat :app:test` passes after each increment.
8. **Run 13 (Sonnet E2E re-test):**
   - Reprocess shows only on failed or not-processed rows, and the Sessions message appears.
   - Killing the app's ffmpeg mid-take (the plan's run 05 step 8 method) shows one message and
     no frozen frame.
   - About 20 resizes during filmed takes cause no false interruption.
   - Real-device interruption: if the user agrees, disable `CABLE Output` for 15 s.
   - No new finding at major level or above.

### Verification plan
- I read every diff.
- For each new test I confirm it fails on the old code, by putting back the old main file for one
  test run, as in round 1.
- I run `:app:test` and count the result XMLs.
- Run 13 report and its screenshots.
- `/verify` with an independent critic at the end.

### Assumptions (any of these can be vetoed)
- Sessions processed **before** this change keep the files they have; nothing is migrated.
- The message on Sessions may show the patient code, since the rows already show it. Log lines
  may not.
- F-106's frame clearing applies only after a failure; the live preview is unchanged.
- The 3 s threshold for a stall while the device is still present is new, and needs a one-line
  note in `docs/Project_Specification.md` §8 (device loss) in the same increment.

### Key decisions
- **F-122 / F-129:** the capture thread owns the writer. The watchdog only closes the line and
  signals. The capture thread finalizes the part and publishes `Interrupted` with the real
  sample count. This fixes both the off-by-1500 and the thread-safety race. The smaller patch
  (reorder plus `join`) was rejected because it keeps cross-thread finalizing.
- **F-104:** the button is hidden rather than disabled for Processed rows, since it has no use
  there.

## Increments
1. **F-129 + F-122 (audio-engineer, Sonnet).**
   - Files: `infrastructure/audio/JvmContinuousSessionRecorder.kt`, `WavFileWriter.kt`,
     `ContinuousSessionRecorder.kt` (state docs), possibly `TaskComponent.kt:609-618` (logging
     only).
   - Tests: new `JvmContinuousSessionRecorderInterruptionTest` with a fake `CaptureLineOpener`.
     `FakeContinuousSessionRecorder.simulateInterruption` stays.
   - Spec note: §8, the stall threshold.
   - **Checkpoint:** diff, the test failing on old code, then green.
2. **F-104 (ui-engineer, Sonnet).**
   - Files: `SessionBrowserContent.kt:99-110`, `SessionBrowserComponent.kt`,
     `RootComponent.kt:352-376` (pass a result message back to Sessions), `ProcessSessionUseCase`
     (the two log lines), `BuiltinStrings.kt`.
   - Tests: `RootComponentTest`, `SessionBrowserComponentTest`, `SessionBrowserLayoutUiTest`.
   - **Checkpoint:** as above.
3. **F-106 (ui-engineer, Sonnet).**
   - Files: `VideoTaskBody.kt:74-118`, `VideoSurface.kt:55-68`, possibly
     `TaskComponent.kt:351-372`.
   - Tests: `VideoTaskScreenUiTest`.
   - **Checkpoint:** as above.
4. **Record (me).**
   - `findings.md`: each row becomes "fixed (2026-10-29), not yet re-run".
   - The **Status:** line in run 04 (F-104), run 05 (F-106) and run 09 (F-122, F-129).
   - Save this spec as `docs/testing/e2e/fix-round-2026-10-29-spec.md`.
5. **Run 13 (Sonnet runner).**
   - Follows criterion 8. Scripts go in `scripts/run-13/`, and I save the report.
   - I watch for marker files so the user can do the 15 s cable disable. No upload.
   - **Checkpoint:** results, then `/verify`, then commit when asked.

## Out of scope
- The web, server and integration minors (the next round).
- The cosmetic and question findings, including F-130 and F-131.
