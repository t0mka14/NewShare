# Run 04: Speech session, editing and a second patient

- Date / time: 2026-10-26 18:55-19:02 local   Runner: Sonnet   App commit: `7cd7e47`
- Preconditions met: yes (run 03 passed with findings; wmplayer looping into CABLE Input; volumedetect before the session: mean -21.8 dB, max -11.6 dB; calibration still below the band, F-092, Continue works)
- Result: passed with findings

Report text saved by the orchestrator from the runner's hand-back. Patient `E2E1026-04-1`, visit `V01`, year of birth `1980`; clinic A, `E2E 1026 Speech protocol`, configVersion `2026-10-26.172809`. Session ID `11e79754eb664c368bd7bc52412477db`. Nothing uploaded. App stopped at the end; `app/data` kept (run-03 session untouched, step 3).

## Steps
| # | Step | Result | Notes / finding |
|---|---|---|---|
| 1 | Run the protocol, monologue not skipped, PATAKA repeated twice | pass | PATAKA: take 1, Retry, take 2, Retry, take 3 (canRepeat is on). Monologue about 22 s. Questionnaire answered (Q4 option 3 clipped again, F-091). Finishing processed the session at once ("Session processed (5 clips)"); editor not offered because `enableEditor: false` (F-094). `step-2-finished-processed.png`. Baseline ZIP: 5 clips, `timelineUsed: original`, PATAKA clip = take 3, 10.40 s, `takes: 3`, two `TAKE_REJECTED` (EXAMINER_REPEAT). `logs/inspect-baseline.txt` |
| 2 | Open editor from Sessions, move a PATAKA boundary 📷 | pass with findings | "Recording 3 of 5 - PATAKA": 0:53.9-1:04.3, 10.4 s. Real mouse drag of the stop handle (x 766 -> 700 at y 200): end 1:02.8, length 8.9 s, Reset segment enabled. `step-2-editor-pataka-before.png`, `-after.png`. F-04-1, F-04-3 |
| 2b | Accept writes the edited timeline and processing uses it | pass | Accept on segment 3 closed the editor and **processed the session automatically** (ZIP rewritten 18:59:29). `timeline_edited.json` (448 bytes; PATAKA `stopSample` 3018566, other segments equal the original) is in the ZIP; `timelineUsed: "edited"`. PATAKA clip 10.40 s -> 8.987 s; the other four clips byte-identical to baseline. Manifest 11 files, hashes match. `logs/inspect-after-accept.txt` |
| 3 | Sessions list 📷; reopen run-03 session and Discard | pass | Both sessions listed (patient, date, Processed, Not uploaded; Open editor / Reprocess). `step-3-sessions-list.png`. Run-03 editor closed with "Discard changes": before/after listing of all 26 files (size, mtime, SHA-256) identical; ZIP SHA-256 still `73a64e3a...c9660` (§10.3 scenario 4 holds). `logs/run03-session-*.txt` |
| 4 | Reprocess the run-04 session | pass with finding | Finished in seconds; ZIP regenerated in place, same session ID; differs only in `generatedAt` / `processedAt`. PATAKA clip still 8.99 s, `timelineUsed: edited`, hashes match. No feedback in the UI (F-04-2). `step-4-after-reprocess.png` |
| 5 | Run-03 ZIP untouched at the end | pass | SHA-256 `73a64e3aa666e155ba1887ab1b8eb404942b02623ea3bd8cad6c1903e21c9660`. |

Clip lengths: `2_PATAKA_Rep1` (take 3) 10.400 s before, 8.987 s after Accept, 8.99 s after Reprocess; PHONATION 9.95 / 8.80, READING 8.95, MONOLOGUE 24.40 unchanged and byte-identical.

Other checks: app log has config refresh, check-in and "microphone opened" only (F-095 again); no errors in the log or `hotrun.log`. `participant.json`: E2E1026-04-1, V01, 1980. `examination.json`: monologue has a `clipFile` and no `skipped`.

## Findings
<a id="f-04-1"></a>

### F-04-1: Accept on one segment finishes the whole editor and triggers processing
- **Severity:** question  **Area:** desktop-app  **Run / step:** run 04, step 2b
- **Expected:** editor text says "Drag the start and end markers to trim this recording. Play it back to check, then accept to continue.", which reads like a per-segment step. Plan expects "finishing writes the edited timeline and processing uses it".
- **Actual:** Accept on "Recording 3 of 5" closed the editor, wrote `timeline_edited.json` for all five segments (4 and 5 never looked at) and re-processed at once. Result is correct; wording and flow unclear, and Accept is the only way out with the edit kept.
- **Repro:** Sessions > Open editor, segment 3, move a handle, Accept.
- **Evidence:** `screenshots/step-2-editor-pataka-after.png`; folder timestamps.
- **Notes:** related to F-094.
- **Status:** open

<a id="f-04-2"></a>

### F-04-2: No feedback while or after Accept / Reprocess from Sessions
- **Severity:** minor  **Area:** desktop-app  **Run / step:** run 04, steps 2b and 4
- **Expected:** progress or a result, like the "Processing" screen after a session (F-026).
- **Actual:** Accept returns to the Sessions list with nothing shown; Reprocess leaves the row unchanged (no spinner, no message); nothing in the log.
- **Evidence:** `screenshots/step-4-after-reprocess.png`.
- **Status:** open

<a id="f-04-3"></a>

### F-04-3: The editor shows end time and length truncated, not rounded
- **Severity:** cosmetic  **Area:** desktop-app  **Run / step:** run 04, step 2
- **Actual:** editor showed End `1:02.8` and Length `8.9 s`; stored stop sample 3018566 is 62.887 s and the clip is 8.987 s (9.0 s rounded).
- **Evidence:** `screenshots/step-2-editor-pataka-after.png`, `timeline_edited.json`.
- **Status:** open

### Duplicates seen again
F-091 (Q4 option 3 clipped), F-093 ("Acc / ept"), F-025, F-095, F-094, F-072, F-092, F-023.

## Context updates
`2026-10-26-context.json`: `sessions[]` entry for run 04.

## Notes for later runs
- Processed, not-uploaded: run 03 (`6c08b80c...`, ZIP SHA `73a64e3a...c9660`) and run 04 (`11e79754...`, edited, regenerated twice; ZIP currently `738080c5...698a7f`). Take hashes with `Get-FileHash` at upload time.
- The run-04 ZIP has `timeline_edited.json`, `timelineUsed: edited`, PATAKA `takes: 3`, a monologue clip and `year_of_birth_opt` 1980 (run 03 has 1975): the server detail page can be checked against these.
- Accept reprocesses by itself (F-04-1); Reprocess has no feedback (F-04-2): wait and check the ZIP mtime.
- Semantic tree node ids change on every screen; Start keeps its id as Stop then Retry task. Editor handle: grab at its top end (y about 200).

## Scripts and logs
`docs/testing/e2e/scripts/run-04/` (start-app.ps1, README.md); reused `run-02/front.ps1`, `run-03/inspect_zip.py`, `tools/Drag-InWindow.ps1`. Logs: `logs/app.log` (redacted), `logs/inspect-*.txt`, `logs/run03-session-*.txt`, `hotrun.log`.
