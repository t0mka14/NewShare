# Spec: questionnaire conditional rules + questionnaire formatting (desktop app)

## Context

The web side already emits `showIf` on questions (web commit 89a805f, contract fixed 2026-10-31). The app ignores it and shows every question, so a required conditional question always blocks Finish once a protocol uses rules. The desktop brief (`task_protocoller_web_app/docs/questionnaire_rules/03_desktop_brief.md`) defines the app work. The user notes (`docs/notes/notes_for_agent_app_web.md`) separately ask for clearer questionnaire formatting.

**Bundling decision: yes, but only the questionnaire formatting.**
- Both change `QuestionnaireBody` in `ui/TaskContent.kt`, and the brief (§2.6) already requires indentation.
- Questions appearing live make a question easier to miss, which is the risk the formatting addresses.
- Logic and formatting stay in separate increments and commits, so each can be reviewed or reverted alone.

## Spec

**Goal:** the app honours `showIf` per the contract, and examiners can clearly see each question, which ones are conditional, and where the pane ends.

**Deliverable:** code, tests and docs in NewShare on branch `old_ui`. No contract or web change.

### Acceptance criteria

Rules (brief §2, §3, §5):
1. All brief §5 test cases pass as automated tests, except #8 (see Verification): hidden at start, shown on selection, cleared when hidden, multiple-choice "any of", cascade through a chain, reappears empty and untouched, invalid rule treated as always shown.
2. A hidden required question does not block Finish. Once shown, it is validated as usual.
3. `questionnaireAnswers` has keys for visible questions only. Visible unanswered questions keep their existing empty-list entries.
4. An invalid rule logs a warning, the question is always shown, and the config still loads. `ConfigValidationResult.isValid` stays true.
5. The refreshed `web_questionnaires.json` fixture decodes with `showIf` on `q7` and `q8`.

Formatting (notes, "App → Questionnaires UI"):
6. A divider sits between adjacent visible questions, with none after the last. This stays correct as questions appear and disappear.
7. A visible frame surrounds the question pane, with the scrollbar inside it. It keeps its size when questions appear or disappear.
8. A conditional question displays directly below its source, after any earlier conditional questions of that source, and is indented one level per rule depth (visually capped at 3 levels). With the web sample, `q7` shows under `q3` and `q8` under `q4`.
9. Existing test tags still work, and the scrollbar still appears on short windows (F-091).

### Verification plan

- **Tests:** `./gradlew.bat :app:test`, counting the result XMLs rather than trusting the exit code.
- **Red checks:** confirm by hand that new tests fail against the old behaviour.
- **Brief case #8 (older builds):** decode the new fixture with the old `Question.kt` during a red run. It should decode and show all questions.
- **Live E2E:** a Sonnet runner uses the real app against a web `/site-config` with rules. It walks cases 1 to 5, takes screenshots for criteria 6 to 8, uploads, and checks that `examination.json` has no hidden keys. Report saved under `docs/testing/e2e/reports/` with the next free run number.
- **Hot reload unavailable:** the `compose-hot-reload` MCP server failed to connect this session, so the runner launches the app normally unless that is fixed.
- **Independent reviewer:** `/verify` critic before any commit.

### Assumptions (veto if wrong)

- Work continues on `old_ui`. The unrelated dirty files (`.idea/vcs.xml`, `app/data.e2e-1026-run06/`, `app/fake-sessions-root/`) are not committed.
- The established workflow applies, though this is a feature rather than a finding: one Sonnet agent per increment (`domain-engineer`, then `ui-engineer`), my diff review, red checks, E2E re-test, reviewer, and commit when asked.
- Answers are stored in config order, even though the display order differs.
- A rule targets the source's option keys only, as the contract says.

### Key decisions

| Decision | Choice | Rejected, and why |
|---|---|---|
| Bundle scope | Dividers, frame, indentation | Inline answers: the notes only half-endorse them, and they need rules for wrapping and translations. Rules only: reworks the same composable twice. |
| Display order | Conditional question moves under its source (user's choice) | Config order: `q7` would look like it belongs to `q6`. |
| Evaluation vs display order | Two separate functions | One shared order: the contract's forward pass relies on config order. |
| Invalid rules | New `warnings` list on `ConfigValidationResult`, plus a runtime check | Brief §4's "checks in `ConfigValidator`" read as errors: any entry in `errors` rejects the whole config, which contradicts brief §3. |
| Frame height | Fills the middle area | Wrapping the content: the frame would jump as questions appear. |
| Frame style | Existing `.border(1.dp, primary, RoundedCornerShape(12.dp))` from `PatientInfoContent.kt:60` | A new style. |

**Known divergence:** the web preview keeps config order and indents flat, so its order can differ from the app's. Display order is not in the contract. This gets recorded in NewShare's docs, and the web preview stays unchanged.

### Increments

**0. Save the spec** to `docs/SPEC-questionnaire-rules.md` so it survives context resets. → Checkpoint: file exists.

**1. Domain logic and decoding** (`domain-engineer`). → Checkpoint: rule tests green, red checks done, diff reviewed.
- `domain/config/Question.kt`: add `QuestionCondition` and `showIf: QuestionCondition? = null`.
- New pure helpers next to it, all treating an invalid rule as no rule:
  - `visibleQuestionKeys(questions, selections)`: forward pass in config order.
  - `displayedQuestions(questions)`: pre-order walk returning each question and its depth.
  - One shared validity check: source is an earlier choice question in the same task, and `optionKeys` is a non-empty subset of its options.
- `domain/config/ConfigValidator.kt`: add `warnings` to the result, leaving `errors` and `isValid` semantics alone.
- `infrastructure/config/JsonConfigurationRepository.kt` (validate calls at lines 80 and 115): log the warnings. Confirm its logging approach first.
- `navigation/TaskComponent.kt`:
  - After `onOptionToggled`, reset every hidden question to its `initialAnswers()` state (empty, untouched, `validWhenUnanswered()`). Only choice answers can change visibility, and one forward pass handles the cascade.
  - `allAnswersValid()` (line 652) checks visible questions only.
  - `questionnaireAnswers` (line 515) is filtered to visible keys.
  - `Content.Questionnaire` (line 704) publishes visible questions in display order, plus a new `indentLevels: Map<String, Int>`.
- Tests: `TaskComponentTest`, `ConfigDecoderTest`, `ConfigValidatorTest`, `WebSampleConfigsTest`, `ExampleConfigsTest`, `fakes/ConfigFixtures.kt`, and a new helper test. Copy the web sample over `app/src/test/resources/fixtures/config/web_questionnaires.json`.

**2. Formatting** (`ui-engineer`). → Checkpoint: UI tests green, screenshots reviewed by the user.
- `ui/TaskContent.kt`:
  - Frame on the `Box` at line 157, with inner padding for the questions.
  - In `QuestionnaireBody` (line 616), replace `spacedBy(20.dp)` with `HorizontalDivider` plus padding between questions, as already used in `SessionBrowserContent.kt:143`.
  - Add start padding per indent level, capped at 3.
- Add tags to `TestTags.Questionnaire` for the frame and the dividers.
- Compose tests: divider count follows visibility, a conditional question's left edge is further right than its source's and it sits directly below, the scrollbar still appears in a short window.

**3. Docs.** → Checkpoint: docs agree with the web contract.
- `docs/Task_Configuration_JSON_Spec.md` §4.2.1: port the `showIf` text.
- `docs/config_alignment_decision_table.md`: port row 16 and its note.
- `docs/dev/ui.md`: display order, indentation, frame.
- `docs/dev/data-and-schemas.md`: check it and update if it describes `Question`.

**4. Live E2E, then `/verify` reviewer.** → Checkpoint: report saved, any defects fixed.

**5. Commit when asked.** Suggested: logic, formatting, docs as three commits.

### Out of scope

- Inline answers on one line.
- Auto-scroll to a newly shown question or to the first error.
- A required-question marker.
- The notes' other items: logs upload, no-protocol info line, Sessions info text, task-screen indicator priority, waveform changes, all web items.
- Web changes, including the brief's "app is not implemented yet" status line and the web preview's open reviewer findings. I'll flag the status line for the user once this ships.
