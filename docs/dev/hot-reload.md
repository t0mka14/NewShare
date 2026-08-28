# Previews and hot reload

Two ways to look at a screen without clicking through the app, plus an MCP server that lets an
AI agent drive the running application.

- **`@Preview`** (`app/src/main/kotlin/org/example/app/ui/previews/`) — static render in the
  IntelliJ preview pane. Needs the Compose Multiplatform IDE plugin.
- **Compose Hot Reload** (plugin `org.jetbrains.compose.hot-reload` 1.2.0) — the app or a single
  composable runs in a real window and picks up code edits without restarting.

Hot reload runs on a **JetBrains Runtime**, which Gradle downloads automatically (JBR 25, via the
foojay resolver already applied in `settings.gradle.kts`). Nothing about the project's own
toolchain changes, and the first `hot*` task of a fresh checkout just takes a bit longer.

## The three ways to run it

```bash
# 1. one composable, no app around it — the closest thing to a live preview
./gradlew :app:hotDev --className=org.example.app.ui.previews.DevPreviewsKt \
                      --funName=CalibrationDev --auto

# 2. a preview harness: all states of a screen, with a chip row to switch between them
./gradlew :app:hotRun --mainClass=org.example.app.ui.previews.PreviewHarnessKt --auto
./gradlew :app:hotRun --mainClass=org.example.app.ui.previews.EditorPreviewHarnessKt --auto

# 2b. the VIDEO task body over a real camera, with capture statistics
./gradlew :app:hotRun --mainClass=org.example.app.ui.previews.CameraLiveHarnessKt --auto

# 3. the real app, real data directory, real config fetch
./gradlew :app:hotRun --mainClass=org.example.app.MainKt --auto
```

`--auto` recompiles and reloads on every save. Without it the app starts in *explicit reload
mode*: leave it running and apply changes with `./gradlew reload` whenever you want them.

### Where the entry points live

| Kind | Source set | Ships in the distribution? | Used by |
|---|---|---|---|
| `@Preview` functions | `app/src/main/.../ui/previews/` | yes (a few KB) | IDE preview pane |
| `@DevelopmentEntryPoint` functions | `app/src/dev/.../ui/previews/` | **no** | `hotDev`, IDE gutter icons |
| `PreviewHarness.main()` | `app/src/main/.../ui/previews/` | yes | `hotRun`, `./gradlew :app:previewCalibration` |
| `EditorPreviewHarness.main()` | `app/src/main/.../ui/previews/` | yes | `hotRun`, `./gradlew :app:previewEditor` |
| `CameraLiveHarness.main()` | `app/src/main/.../ui/previews/` | yes | `hotRun`, `./gradlew :app:previewCamera` |

`CameraLiveHarness` renders the production `VideoTaskBody` over a **real camera**, with the real
`FfmpegSessionVideoRecorder` and `FfmpegDeviceEnumerator` and no `AppContainer` (so no
`app/data/app.lock`). It is in `main` rather than `dev` because the MCP server only attaches to
`hotRun` — see the MCP section below.

Camera faults on this path are quiet: negotiation walks several modes, every failure is a log line
rather than a throw, and a capture that arrives far faster than the requested rate starves the UI
instead of reporting anything. So the harness shows — and **also logs once a second** — the
delivered frame rate and MB/s, the composition frame-clock interval and its worst case, the
negotiated format, and the resolved ffmpeg path.

It also names the **backend** (`avfoundation` / `dshow`, and whether that backend can do MJPEG
passthrough at all) and the **rung** the open capture is running on — copying the camera's own MJPEG
or letting ffmpeg encode. Neither is visible in the output, since both produce the same elementary
stream, but they decide where the CPU goes and whether the frame rate came from the camera or from
`-r`. A MB/s figure also means quite different things between the two, and note that it is
content-dependent either way: the same camera at a steady 30 fps measured 0.8 MB/s in a dark room
and 3.6 MB/s in daylight, because JPEG compresses detail. The frame rate is the number to watch. When the UI thread is the thing being starved, the
log is the only part still moving. The `decode on EDT` chip puts the JPEG decode back on the UI
thread, which is the shape the preview had when the VIDEO screen locked up.

It also runs headless-ish, driven from a terminal instead of by clicks:

```bash
./gradlew :app:previewCamera -Pautostart   # opens the camera as soon as one is found
```

The editor also has `EditorLiveDev` (dev source set only): the **real** `DefaultEditorComponent`
over the newest reviewable session in `app/data/sessions/`, with the production waveform and
playback services. Synthetic peaks cannot show whether the position line agrees with audio you can
hear, which is the one thing §8.7's position tracking has to get right — so check that here:

```bash
./gradlew :app:hotDev --className=org.example.app.ui.previews.DevPreviewsKt \
                      --funName=EditorLiveDev --auto
```

It reads sessions rather than driving one, builds no `AppContainer`, and so does not take
`app/data/app.lock` — but pressing Accept after moving a boundary does write that session's
`timeline_edited.json`, so point it at a session you do not mind re-editing.

The `dev` source set is created by the hot-reload plugin and sees `main`, so the
`@DevelopmentEntryPoint` functions just delegate to the `@Preview` ones — each screen state is
defined once. `hotDev` **only** accepts `@DevelopmentEntryPoint` functions from that source set;
pointed at a plain `@Preview` in `main` it starts and exits 1 without a message.

## What actually reloads

Verified on this project: the full app starts under `hotRun` on JBR 25, uses the normal
`app/data/` directory (it fetched its config from the mock server at startup like any other run),
and survives `./gradlew reload` while running. What a given edit does depends on what you changed:

- **Composable bodies** — the sweet spot. Layout, styling, text, state defaults in preview code.
- **New/changed classes and functions** — JBR's enhanced class redefinition handles far more than
  standard HotSwap (adding methods and fields included), but **objects that already exist keep the
  state they were constructed with**. Editing `AppContainer` wiring, a component's constructor, or
  anything built once at startup usually needs a restart to take effect.
- **Startup-time reads** — the cached config, settings, and the session's config snapshot are read
  once; reloading code does not re-read them.
- **Long-lived machinery** — the recorder's capture thread, an open `TargetDataLine`, coroutine
  scopes tied to a `SessionComponent`. Don't hot reload in the middle of a take; stop the session
  first.
- The `reset_ui` MCP tool (below) discards the composition and drops all `remember`-ed state,
  which is the cheap way to get a clean UI without losing the process.

Because option 3 runs against the real data directory, an edit-reload loop there acts on real
sessions in `app/data/`. For pure visual work prefer options 1 and 2 — they touch no microphone,
no filesystem, no server.

Only one instance can run at a time: the app holds an exclusive lock on `app/data/app.lock`
(spec §5.2), so stop an IDE-launched instance before starting a hot-reload one.

## MCP server — letting an agent drive the app

Compose Hot Reload ships an MCP server (stdio, protocol `2025-06-18`, experimental since 1.2.0)
that connects to the running application's orchestration layer. It is registered for Claude Code
in the project's [`.mcp.json`](../../.mcp.json):

```json
{
  "mcpServers": {
    "compose-hot-reload": {
      "command": "./gradlew",
      "args": ["--no-daemon", "--quiet", "--console=plain", "hotMcpServer"]
    }
  }
}
```

The agent starts and stops the server itself — you never run the task by hand. The unqualified
`hotMcpServer` name is enough: Gradle resolves it against the subprojects and matches the
target-specific task (here `:app:hotMcpServer`, since `:app` has a single JVM target).

**The MCP server only sees the `main` run scope.** Gradle starts it with
`-Dcompose.reload.pidFile=app/build/run/main/main.pid`, so it attaches to `hotRun` windows.
`hotDev` writes `app/build/run/dev/dev.pid` and its window never registers — `status` keeps
reporting `connected: false` with nothing wrong in the log. For agent-driven work use one of the
`hotRun` commands above; `hotDev` is for a human at the keyboard. The
[`hot-reload-screenshots`](../../.claude/skills/hot-reload-screenshots/SKILL.md) skill has the
full procedure.

The server does not need an application: it starts, waits for one to launch, and reconnects when
it restarts. So the workflow is:

1. start the app with hot reload (any of the three commands above),
2. the agent calls `status` — `connected: false` means no app is running **in the `main`
   scope** yet (see the warning above); it also
   reports `buildContinuous`, which says whether to use `reload` (explicit) or `await_reload`
   (started with `--auto`),
3. it inspects and drives the app.

Tools exposed:

| Tool | What it does |
|---|---|
| `status` | is an app connected, reload state, last error, reload counts |
| `reload` / `await_reload` | apply pending edits (`reload`), or wait for the `--auto` build to finish |
| `take_screenshot` | PNG of a window |
| `get_semantic_tree` | the semantics/accessibility tree — this is where `Modifier.testTag` values show up |
| `click` / `long_click` / `type_text` / `scroll` / `scroll_to_index` | drive the UI |
| `list_windows` / `resize_window` | window inventory and resizing (useful for the §13 decision 36 scaling rule) |
| `get_ui_error` / `get_logs` | the exception behind a window that fails to render, and recent app output |
| `restart` / `reset_ui` | restart the process, or drop the composition and all `remember`-ed state |

The same `TestTags` constants that drive the UI test suite (§10.3) are what make the app
addressable here — a tagged element is findable in the semantic tree, and `click`/`type_text`/
`scroll` take the `nodeId` from that tree (the node has to expose the matching semantic action:
`onClick`, `editableText`, `ScrollBy`, `ScrollToIndex`).

The window-targeting tools (`take_screenshot`, `get_semantic_tree`, `click`, `long_click`,
`type_text`, `scroll`, `scroll_to_index`, `resize_window`, `get_ui_error`) accept an optional
`window_id`; without it they act on the first registered window — the first `Window` /
`singleWindowApplication` / `DialogWindow` composed at startup. `list_windows` gives the ids.

**On Wayland:** `take_screenshot` is captured by the application itself, so it works even where
external tools (`grim`, etc.) are blocked. The input tools go through the desktop's remote-input
portal, so the first `click`/`type_text` raises a system "Allow Remote Interaction" dialog that
has to be granted before the agent can drive the UI.

## Known sharp edges

- Compilation errors surface as a failed `reload`; the app keeps running the previous code.
- A runtime exception thrown while rendering leaves that window blank rather than killing the
  app — `get_ui_error` (or the dev-tools overlay) has the stacktrace.
- The `hot*` tasks do not inherit the `--enable-native-access=ALL-UNNAMED` flag that
  `compose.desktop.application` sets, so the run prints JEP 472 warnings on JDK 24+. Harmless.
- Reloading across a change to a `@Composable`'s *signature* (or to a lambda inside one) can leave
  the running app holding a class that no longer matches, which shows up as a `ClassCastException`
  from somewhere innocent — `... cannot be cast to kotlinx.coroutines.flow.FlowCollector` is a
  characteristic one. It is a stale-class artifact, not a bug in the edit: `restart` clears it.
- Upstream's own list: <https://github.com/JetBrains/compose-hot-reload/blob/master/docs/Known_limitations.md>
