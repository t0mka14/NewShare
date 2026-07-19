# UI dev notes

Terse reference for `ui/`/`navigation/` work. See `docs/Project_Specification.md` §5.2 (layers),
§10.3 (testTags), §13 decision 36 (legacy 1:1 restyle) for the normative rules; this is the
practical companion.

## Visual language (extracted from `/home/tomas/IdeaProjects/shareapp`)

- **Material3 only** (the Material2 stack was dropped 2026-07-16 at user request; there must
  be no `androidx.compose.material.*` widget imports — `androidx.compose.material.icons.*`
  is the icon artifacts' namespace and is fine). **Two M3 themes.** App-wide:
  `org.example.app.ui.theme.ShareTheme`, a full `ColorScheme` from the legacy seed values
  (`shareapp/src/main/kotlin/materials/Color.kt`) with `secondary`/`tertiary` remapped to the
  legacy tertiary green — this app's screens use `secondary` as the green accent: primary
  `#00668A`, secondary `#006E2A` (light) / `#7BD0FF`, `#64DF7A` (dark). `ShareAccentOrange`/
  `ShareAccentOrangeContainer` are the legacy `SoundLevelBar` accent family (calibration bar).
  Task + Calibration screens additionally wrap themselves in `ui.theme.ShareLegacyM3Theme` —
  the legacy `materials/{Color,Typography,Shapes,Theme}.kt` verbatim — because those two
  screens are 1:1 copies of the legacy composables. `Main.kt` applies `ShareTheme` around
  `RootContent`; the test `ScenarioApp` does the same, so scenario screenshots match
  production.
- **Type scale**: the app-wide `ShareTheme` is proportioned like the legacy's but capped; the
  former M2 slots map onto M3 at identical sizes (`headlineLarge` 40sp ←h4, `headlineMedium`
  32sp ←h5, `headlineSmall` 22sp ←h6, `titleMedium` 18sp ←subtitle1, `bodyLarge` 20sp ←body1,
  `bodyMedium` 16sp ←body2, `labelLarge` 18sp ←button, `bodySmall` 13sp ←caption), all Roboto
  (M3 has no `defaultFontFamily`; every slot sets it explicitly). The Task/Calibration screens
  use the legacy literal sizes through `ShareLegacyM3Theme` (`headlineLarge` 45sp,
  `displayMedium` 35sp task titles, `labelMedium` 30sp buttons, `labelSmall` 20sp button
  top-lines, `bodyMedium` 20sp instructions, `bodySmall` 18sp button subtitles, `bodyLarge`
  16sp instruction-card text, `displaySmall` 12sp) — with the legacy 3-button task row the
  30sp button text fits again.
- **Shapes**: app theme `small` 8dp corners, `medium` 16dp, `large` = pill
  (`RoundedCornerShape(50)`); legacy theme `medium` 16dp, `large` `RoundedCornerShape(200.dp)`
  — both mirror the legacy's `materials/Shapes.kt`. Used on Back/navigation buttons.
- **Spacing**: content columns are fraction-width (`fillMaxWidth(0.6-0.9f)`) or weighted, never
  fixed dp widths — this fixes the *actual* legacy scaling bugs (fixed `.width(350.dp)` buttons,
  `fillMaxSize(0.5f)` canvases). Screen padding is 20-32dp; card padding 16dp.
- **Fonts and icons**: the legacy Roboto TTFs are classpath resources (`fonts/roboto/`), loaded
  by both themes (the original's working-directory `File` loading was a packaging bug).
  Material icon packs are bundled (`material-icons-extended` 1.7.3, the final release of the
  icon artifacts — icons stopped being versioned with Compose there); the legacy task screen's
  Play/Close/Refresh/arrows/volume icons render 1:1.
- **Dialogs**: always Compose-level (`AlertDialog` inside the window scene), never a second OS
  window — required for `runComposeUiTest` to drive them (§5.2, §10.3).

## Screen inventory

| Screen | Component (`navigation/`) | Content (`ui/`) | testTag prefix |
|---|---|---|---|
| Main menu | `MainMenuComponent` | `MainMenuContent` | `mainMenu.*` |
| Protocol picker (>1 protocol) | `ProtocolPickerComponent` | `ProtocolPickerContent` | `protocolPicker.*` |
| Settings | `SettingsComponent` | `SettingsContent` | `settings.*` |
| Patient info | `PatientInfoComponent` | `PatientInfoContent` | `patientInfo.*` |
| Calibration | `CalibrationComponent` (child of `SessionComponent`) | `CalibrationContent` | `calibration.*` |
| Task (VOCAL/QUESTIONNAIRE/INFO) | `TaskComponent` (child of `SessionComponent`) | `TaskContent` + `TaskIndicators` | `task.*`, `questionnaire.*` |
| Device-lost dialog | n/a (driven by `TaskComponent`/`CalibrationComponent` state) | `DeviceLostDialog` | `deviceLostDialog.*` / `*.errorDialog.deviceLost` |
| Editor | `EditorComponent` | `EditorContent` | `editor.*` |
| Processing | `ProcessingComponent` | `ProcessingContent` | `processing.*` |
| Session summary | n/a (stateless stub) | `SessionSummaryContent` | `sessionSummary.*` |
| Upload | `UploadComponent` | `UploadContent` | `upload.*` |
| Session browser | `SessionBrowserComponent` | `SessionBrowserContent` | `sessionBrowser.*` |
| Blocking (no config) | n/a | `BlockingContent` | `blocking.*` |

`RootComponent`/`RootContent` own top-level navigation (`Config` sealed interface → `Child`
sealed class); `SessionComponent`/`SessionContent` own the in-progress-examination sub-stack
(Bootstrapping → Calibration → TaskScreen×N → Failed). Post-protocol flow:
`SessionComponent.onSessionEnded(folderName)` → `Editor` (only if `enableEditor`) → `Processing`
→ `SessionSummary`. From the session browser, Editor/Processing are pushed with
`returnToBrowser = true` and pop back instead.

`TaskComponent.State` carries everything the task screen needs directly — position/total among
navigable instances, the device list + device-in-use, `taskLengthSeconds` (timer highlight),
`nextTaskTitleKey` (legacy next-task button label), and per-content-type fields
(`Content.Vocal.showIndicator`/`exampleAudioAvailable`, `Content.Questionnaire.questions`) — computed
once by `SessionComponent` from the expanded task-instance list it already holds. There is no
re-expansion in the UI layer.

### Legacy task-screen layout and tag-role mapping (§13 decision 36)

`TaskContent` is a 1:1 copy of the legacy `StandardProtocolScreen`: title block ("Task X/Y"
over the 35sp title, repetition appended to the title line), instruction card(s) with the
example-audio row between them (hidden on repetitions > 1), a weighted middle area (level
circle/waveform or the questionnaire), the legacy timer card (invisible until a take runs,
green once `taskLengthSeconds` is reached), and the legacy 3-button bottom row:

- *Left slot*: empty. The legacy prev-task button was gated behind a `showBackButton`
  preference defaulting to false; this app has no task back-navigation. The slot keeps the
  state button centered.
- *Center*: the legacy Start→Stop→Again cycling button. Its testTag follows its current role:
  `task.startButton` (Idle/Failed → `onStart`), `task.stopButton` (Capturing → `onStop`),
  `task.repeatButton` (Stopped → `onRepeat`, disabled when `!canRepeat`). VOCAL only.
- *Right slot*: the legacy next-task button ("Next task" over the upcoming task's title, or
  `task.endOfProtocol` on the last). On an unrecorded skippable task it performs `onSkip` and
  carries `task.skipButton`; otherwise `task.nextButton` → `onNext`. The legacy app had no
  skip concept — this mapping keeps `canSkip` reachable without a fourth button.

`CalibrationContent` is the legacy `CalibrationScreen` (mic-position photo + vertical
`SoundLevelBar` with the configured loudness band, pill Back / Continue buttons). Back aborts
to the main menu via `SessionContent`'s `onBackToMenu` (the session component's destroy stops
the recorder). The device dropdown has no legacy counterpart and stays below the bar row
(§8.5 requires device selection here).

## Add-a-screen checklist

1. **Component** (`navigation/XyzComponent.kt`): interface with `state: Value<State>` + event
   methods; `DefaultXyzComponent` constructor-injected from `AppContainer`'s ports/use cases
   (never wires new ports itself — if a new port is needed, that's a cross-team interface change,
   coordinate through the lead). No `java.io.File`; `Path` via the repositories is fine.
2. **Content** (`ui/XyzContent.kt`): stateless `@Composable fun XyzContent(component: XyzComponent,
   localization: UiLocalization, ...)`. Read `component.state.subscribeAsState()`, render, forward
   events — no logic, no display literals (`localization.resolve("key")` only).
3. **testTags**: add a nested `object Xyz` to `TestTags.kt`; one tag per interactive element.
   Per-row/dynamic items are functions (`fun rowTag(id: String) = "xyz.row.$id"`), never
   string-interpolated ad hoc at the call site.
4. **Strings**: add every new key to `BuiltinStrings.en` (dotted `area.element[.detail]`,
   placeholders as `{name}`) — config-driven text (task titles/instructions) uses
   `titleKey`/`instructionKeys` from the config model instead, never a built-in key.
5. **Wiring**: add the `Config`/`Child` cases in `RootComponent` (or the owning parent component)
   and the `when` branch in the corresponding `Content` dispatcher (`RootContent`/`SessionContent`).
6. **Tests**: a component-level test (`navigation/XyzComponentTest.kt`, fakes only, no Compose) per
   state transition — this is the primary bar. Add a Compose smoke test
   (`ui/NewScreensSmokeTest.kt` or a dedicated file) only for screens with non-trivial rendering
   code (custom `Canvas`/`pointerInput`/gesture handling) that a component test can't exercise.
7. Run `./gradlew :app:test` before calling it done.
