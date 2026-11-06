# Spec: remaining app items from the 30. 10. 2026 notes

## Changes during implementation

The text below is the spec as approved. These changes came after approval:

- **8 dp gap (user request, 2026-11-06):** a gap between the instructions and the indicator or preview. It collapses at the thresholds, so the 80 dp and 198 dp limits are unchanged. A version that added the gap to the thresholds hid the preview at the 944x681 minimum window, so it was dropped. This changes AC 2's "full size if it fits": with 160–167 dp of room the circle is drawn at 152–159 dp, and with 250–257 dp the waveform at 242–249 dp. They reach full size at 168 and 258 dp.
- **Review fixes (independent reviewer, 2026-11-06, after run 18):**
  - `SessionComponent` logs a video or recorder state through `describeVideoState` and `describeRecorderState`. Disk write and recorder errors lose their detail. Capture start and interruption errors keep ffmpeg's text after `redactPaths`, because it's the diagnostic, and the same text goes into uploaded hardware events.
  - `redacted()` drops serialization messages, which quote file content such as `participant.json`.
  - `redactPaths` is now one pass over whitespace-separated tokens, replacing the path regexes, because the old UNC rule could leave a path's first segment behind. It also catches bare session folder names, and keeps `I/O`, `Input/output`, `N/A` and bounded ratios such as `30/1`. Its limits are listed in its KDoc and in `docs/dev/device-logs-contract.md`.
  - `RootComponent` refuses to start with zero protocols, closing a one-event window after a config change.
  - `VideoFrameHolder` makes decode and close mutually exclusive, because hiding the preview during a take could otherwise free bitmaps during a decode.
- **`TaskPreviewHarness` added:** a dev-only harness in `ui/previews`, so the Task screen can be captured in any state without writing sessions to `app/data`. `previewTask` runs it.
- **`heightIn(min = 110.dp)` removed from the instructions:** it padded short cards above their natural height.
- **Log fixes went slightly further:** `MicGainApplier` also redacts `result.detail`, and `redactPaths` exempts exact `I/O` and ffmpeg component tags such as `out#0/mjpeg`.
- **`LogCapture.fullText`** walks suppressed exceptions as well as causes.
- **Local tooling, not in the repo:** compose-hot-reload needs `cmd /c .\gradlew.bat ...` on this machine, set as a local-scope MCP entry.

## Context

`task_protocoller_web_app/docs/notes/notes_for_agent_app_web.md` lists app and web changes. The questionnaire and waveform items are already done. This round covers the remaining app items:

- the task screen gives the indicator priority over the instructions;
- a clinic with no protocol reaches a blank Patient info page, then a misleading "Configuration required" screen;
- the Sessions info text explains a process button that most rows no longer show;
- nobody has answered whether logs are sent. They are not, and some log paths could carry patient codes.

**Goal:** examiners can always read the instructions, never hit the no-protocol dead end, and aren't misled by the Sessions text. The logs are also safe to send once the web endpoint exists.

**Deliverable:** code, tests and docs in NewShare on branch `old_ui`. No web repo changes.

## Decisions already made

| Topic | Decision | Rejected |
|---|---|---|
| Task layout | Instructions first; indicator fills what's left, shrinks, and hides below 80 dp | No-shrink hiding; always keeping a compact indicator |
| Filming VOCAL task | Camera preview also yields to the instructions and hides when it doesn't fit | Always reserving the preview's space |
| No protocol | Line on the main menu, New examination disabled | Line on the Patient info page |
| Sessions | Drop the process/retry sentence, keep the button (only retry path for failed sessions) | Rewording it; removing the button |
| Logs | Leak fixes and a contract doc now; sender built later with the web endpoint | Building the sender now; deferring everything |

## Acceptance criteria

**Task screen (VOCAL tasks)**
1. Instructions take their natural height, limited only by the space left after the title, timer and buttons. They scroll only if they exceed that space.
2. The indicator gets the leftover height: full size if it fits (circle 160 dp, waveform 250 dp), scaled down if not, and absent from the UI below 80 dp.
3. Instruction height is identical in Idle, Capturing and Stopped, for both indicator types. This also removes today's jump at Stop with the circle type.
4. In a filming VOCAL task, the 200x150 camera preview shows only if the leftover height fits it and its message line (198 dp). Otherwise it is absent from the UI, and the indicator follows AC 2 on its own. Hiding or showing the preview never starts, stops or changes the video capture.
4a. If the preview is hidden and the camera fails or the take is audio-only, the same message stays visible as a line in the middle area. This is the one case where instructions may lose height, and they scroll if they must.
5. Start/Stop and Next stay visible at 944x681 (minimum window) and at 784x561.
6. The layout of questionnaire, video and info tasks does not change.

**No protocol**

7. With a config that has zero protocols, the main menu shows one info line and New examination is disabled.
8. With no config at all, the menu behaves as today (disabled, no line). With one or more protocols there is no line and the button is enabled.
9. The line updates when the config changes. Its key exists in EN and CS, so `BuiltinStringsTest` passes.

**Sessions**

10. `sessionBrowser.instructions` (EN and CS) no longer mentions Process / Retry. The row buttons behave as they do today.

**Logs**

11. A failed recovery logs only the exception type, never its message.
12. A processing failure logs only the file's extension, not its name.
13. `redactPaths` handles paths containing spaces, relative paths and bare session file names, and the existing ffmpeg redaction tests still pass.
14. `MicGainApplier` and `JvmAudioInputGainControl` log throwables through `redacted()`.
15. `LogCapture.fullText` includes causes, so privacy tests also see nested exception messages.
16. `docs/dev/device-logs-contract.md` states that logs aren't sent today, and proposes `POST /device-logs`. It also lists the open decisions: who can view logs, automatic vs on-request sending, retention, HTTPS, and the identifying data still logged on purpose.

## Design

**Task layout** (`ui/TaskContent.kt`, VOCAL branch only)
- Compose measures unweighted children first, in order, so simply removing the weight from the instructions would squeeze the timer and buttons.
- Instead, put the instructions and middle area in one `Modifier.weight(1f)` container with a small custom `Layout`:
  - measure the instructions with the container's full height, minus one message line only while the camera message of AC 4a is showing;
  - give the middle area the remaining height and centre it there.
- The middle area uses `BoxWithConstraints`:
  - it omits the indicator below a new `MIN_INDICATOR_HEIGHT = 80.dp`;
  - it omits the preview below `150.dp + CAMERA_MESSAGE_SLOT_NARROW`, without shrinking it.
- Remove `indicatorHeight` and `minHeight`.
- The filming branch's fixed `height(indicatorHeight)` also goes. This fixes the trace being clamped to 160 dp with the circle type.

**Camera message** (`ui/VideoTaskBody.kt`)
- The audio-only, camera-lost and error line is currently drawn inside `VideoPreview` (`:116-135`). Extract it into its own composable, used by `VideoPreview` and by the middle area when the preview is hidden.
- `VideoSurface` only reads `feed.frames`. Confirm the capture doesn't depend on that collector, and cover it with a test (AC 4).

**Indicators** (`ui/TaskIndicators.kt`)
- The waveform already respects incoming constraints, because `size()` is clamped.
- The circle uses `size(160.dp)` with fixed radii, so scale its radii by `size.minDimension / 160dp` when drawing.

**No protocol**
- `MainMenuComponent.State` gets `noProtocolConfigured`, computed in `stateOf` as `config != null && config.protocols.isEmpty()`.
- `startEnabled` also requires at least one protocol.
- `MainMenuContent.MenuButtons` shows the line, with a new test tag.
- New key `mainMenu.noProtocolConfigured`, added to both maps in `BuiltinStrings.kt`:
  - EN: "No protocol is currently configured for this site."
  - CS: "Pro toto pracoviště není aktuálně nastaven žádný protokol."
- Expect to update test fixtures that use a protocol-less `RemoteConfig` and expect Start to be enabled.

**Sessions**
- Edit `BuiltinStrings.kt:122` and `:372`. New EN text: "Edit recorded sessions. Open editor: lets you cut unwanted parts out of the session's audio. Saving changes in the editor reprocesses the session by itself."
- Check that the web side doesn't override this key.

**Log fixes**
- `RecoverSessionsUseCase.kt:118`: store `e::class.simpleName` only. Parser messages can also quote file content, so redacting isn't enough. The UI summary uses the folder name, not this text.
- `ProcessSessionUseCase.kt:94-95`: reduce `fileName` to its extension.
- `domain/video/ErrorText.kt` `redactPaths`: after the existing rules, also replace any whitespace-delimited token that contains `/` or `\`, or that ends in a session file extension. Accepted limits: a one-word fragment of a spaced path can survive, and strings like `N/A` get redacted.

## Assumptions (veto any)

- **Waveform border:** the note's question about dropping the full boundary is treated as closed with the waveform work. The code still draws the full border (`TaskIndicators.kt:126`), so it stays unless you say otherwise.
- **Timer:** stays in place and keeps its reserved line, so the buttons don't jump. It and the Stop button are the recording signs when the indicator is hidden.
- **Non-VOCAL tasks:** the note's screenshot is a VOCAL task, so the others keep their current layout. This includes standalone video tasks, where the preview is the main content.
- **Framing on small windows:** with long instructions on a small window, the examiner can't see the shot while filming. You accepted this by asking for the preview to hide; AC 4a only keeps camera failures visible.
- **Empty protocols:** a protocol with no patient fields, or a config with protocols but no tasks, is not treated as "no protocol".
- **Updater log:** `updater.log` isn't touched. The contract proposes sending only rotated `app.*.log.gz` files.

## Increments

Each follows the agreed flow: one Sonnet agent of the matching type, my diff review, a red run on the old behaviour, then `./gradlew.bat :app:test` with the result XMLs counted.

0. Save this spec as `docs/SPEC-app-notes-round.md`. → checkpoint: file exists.
1. **Sessions text** (`ui-engineer`). → checkpoint: AC 10.
2. **No-protocol line** (`ui-engineer`). → checkpoint: AC 7–9.
3. **Task layout** (`ui-engineer`), the riskiest step. Deliberately change the tests that encode the old priority: in `TaskLayoutUiTest`, `a PATAKA indicator keeps its size with long instructions` and the `keeps its preview and indicator` cases; in `MinimumWindowSizeUiTest:215,223`, the preview checks; in `FixRound4D4UiTest:108-134`, the no-movement checks, which need a size or text where the preview still fits. Add the missing full-`TaskContent` waveform coverage. → checkpoint: AC 1–6, plus screenshots for you at three window sizes.
4. **Log leak fixes** (`domain-engineer`), starting with the `LogCapture` change so new failures show up. → checkpoint: AC 11–15.
5. **Docs:** the contract doc (AC 16), `Project_Specification.md` task layout and main menu sections, and findings entries with causes and regression test names. → checkpoint: docs reviewed.
6. **Verification:** Sonnet E2E re-test, then the independent reviewer. → checkpoint: report saved, results shown to you.
7. **Commit** only when you ask: one commit for code, one for docs, staging explicit paths. `.idea/` changes and the untracked `app/data.e2e-1026-run06/` and `app/fake-sessions-root/` stay out.

## Verification

| Criteria | How it's checked |
|---|---|
| 1–6 | `TaskLayoutUiTest` at several sizes, for both indicator types and all screen states; new tests must fail on the old layout |
| 7–9 | `MainMenuComponent` and UI tests for zero, one and no config, plus a config change while open; `BuiltinStringsTest` |
| 10 | New test on both strings; existing `SessionBrowserLayoutUiTest` for the buttons |
| 11–15 | Failure-path privacy tests using a fake patient code, including a path with a space and a nested cause; each must fail on the old code |
| 16 | Reviewed against the code and web references it cites |
| All | Full `:app:test` with XMLs counted; E2E re-test in the real app; `/verify` independent critic |

**E2E re-test (run 18):**
- a small window with long instructions, for both indicator types, through Idle, Capturing and Stopped;
- a filming VOCAL task on a small window with the preview hidden: the uploaded session still contains the video;
- a site with no protocol;
- the Sessions text in EN and CS.

**Tooling gate:** the screenshots and the E2E re-test use the compose-hot-reload MCP server, with no fallback. It failed to connect at the start of this session (`CONNECTION_CLOSED`). Check it before increment 1, and again before any step that uses it. If it doesn't work, stop and tell the user, so it can be fixed together before continuing.

## Out of scope

- Web items, including the web `Logs` folder and endpoint.
- The log sender.
- Questionnaire and waveform items, already done.
- Changing the logging of site name, device names and `sessionId`. The contract doc records these as decisions to make before sending.
