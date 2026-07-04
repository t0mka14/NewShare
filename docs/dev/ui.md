# UI dev notes

Terse reference for `ui/`/`navigation/` work. See `docs/Project_Specification.md` §5.2 (layers),
§10.3 (testTags), §13 decision 36 (legacy 1:1 restyle) for the normative rules; this is the
practical companion.

## Visual language (extracted from `/home/tomas/IdeaProjects/shareapp`)

- **Theme**: `org.example.app.ui.theme.ShareTheme` (Material2). Colors are the legacy Material3
  seed values verbatim (`shareapp/src/main/kotlin/materials/Color.kt`): primary `#00668A`,
  secondary `#006E2A` (light) / `#7BD0FF`, `#64DF7A` (dark). `ShareAccentOrange`/
  `ShareAccentOrangeContainer` are the legacy `SoundLevelBar` accent family, used for the
  calibration loudness band.
- **Type scale**: proportioned like the legacy's (`headlineLarge` 45sp titles → `displayMedium`
  35sp task titles → `labelMedium` 30sp buttons → `bodyMedium` 20sp instructions → `displaySmall`
  12sp help text) but capped below those literal sizes: `h4` 40sp, `h5` 32sp, `h6` 22sp,
  `subtitle1` 18sp, `body1` 20sp, `body2` 16sp, `button` 18sp, `caption` 13sp. The cap is a
  deliberate scaling fix (§13 decision 36) — the legacy only ever laid out 2-3 buttons per row;
  this app's task screen has five (Start/Stop/Repeat/Skip/Next) side by side, so the literal 30sp
  button text would overflow.
- **Shapes**: `small` 8dp corners, `medium` 16dp, `large` = pill (`RoundedCornerShape(50)`),
  mirroring the legacy's `materials/Shapes.kt`. Used on Back/navigation buttons across screens.
- **Spacing**: content columns are fraction-width (`fillMaxWidth(0.6-0.85f)`), never fixed dp
  widths — this avoids the *actual* legacy scaling bug (fixed `.width(350.dp)` buttons regardless
  of window size). Screen padding is 24-32dp; section spacing is 16-32dp; card padding 16dp.
- **No custom font, no icon pack**: the legacy's Roboto bundling and Material icons were not
  carried over — `org.jetbrains.compose.material:material-icons-core`/`-extended` aren't
  resolvable in this sandbox (network-gated, see git history on `TaskContent`/`EditorContent`),
  so all buttons are text-only. If icons are wanted later, add
  `org.jetbrains.compose.material:material-icons-core` to `libs.versions.toml` in an environment
  with registry access and verify `:app:compileKotlin` before relying on it in CI.
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
navigable instances, the device list + device-in-use, and per-content-type fields
(`Content.Vocal.showIndicator`/`exampleAudioAvailable`, `Content.Questionnaire.questions`) — computed
once by `SessionComponent` from the expanded task-instance list it already holds. There is no
re-expansion in the UI layer.

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
