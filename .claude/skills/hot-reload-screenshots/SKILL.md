---
name: hot-reload-screenshots
description: Launch this project's Compose Desktop UI so the compose-hot-reload MCP tools can attach to it, then screenshot or drive it (before/after visual checks of a UI refactor, inspecting the semantic tree, clicking through a screen). Use whenever an MCP `compose-hot-reload` call is needed and `status` reports `connected: false`, or before starting any UI change worth eyeballing.
---

# Driving the app through the compose-hot-reload MCP server

## The one thing that will waste your time

**`hotDev` windows are invisible to the MCP server. Use `hotRun`.**

Claude Code starts the MCP server from `.mcp.json` (`./gradlew … hotMcpServer`), and Gradle
launches it pinned to one run scope:

```
-Dcompose.reload.pidFile=app/build/run/main/main.pid
```

That is the **`main`** scope. `hotDev` runs `@DevelopmentEntryPoint` functions out of the
`dev` source set and writes `app/build/run/dev/dev.pid` instead, so its window never
registers — `status` keeps returning `{"connected": false}` while a perfectly good window is
sitting on screen. Nothing in the log says so; do not go looking for a startup error.

Only entry points in the **main** source set can be driven by the MCP tools:

| Want | Command |
|---|---|
| Calibration, all 4 states + chip switcher | `./gradlew :app:hotRun --mainClass=org.example.app.ui.previews.PreviewHarnessKt --auto` |
| Editor, all states | `./gradlew :app:hotRun --mainClass=org.example.app.ui.previews.EditorPreviewHarnessKt --auto` |
| The real app (real `app/data/`, real config fetch) | `./gradlew :app:hotRun --mainClass=org.example.app.MainKt --auto` |

A screen with no main-source-set harness (Task, today) cannot be screenshotted without first
adding one — say so rather than silently skipping the visual check.

## Procedure

1. **Launch** in the background, redirecting to the scratchpad:

   ```bash
   ./gradlew :app:hotRun --mainClass=…Kt --auto > "$SCRATCH/hotrun.log" 2>&1
   ```

   (`Bash` with `run_in_background: true`.)

2. **Wait on the pid file, not the log.** The log's last line is a JEP 472 native-access
   warning, which arrives well before the window registers:

   ```bash
   until [ -f app/build/run/main/main.pid ]; do sleep 2; done
   ```

   `Bash` + `run_in_background` for this (one notification when the condition is met). First
   run of a fresh checkout downloads JBR 25 — allow several minutes.

3. `mcp__compose-hot-reload__status` → expect `{"connected":true,"buildContinuous":true,…}`.
   `buildContinuous: true` means it was started with `--auto`.

4. **Screenshot before you edit anything.** `take_screenshot` with `save_to` under the
   scratchpad. This is the whole point — you cannot re-create a "before" after the edit.

5. Edit sources. With `--auto`, call `await_reload` (not `reload`) and re-screenshot.
   `successfulReloads` in `status` should have gone up; a compile error shows as
   `failedReloads` with the app still running the old code.

6. **Stop it when done** — `TaskStop` on the background task, then confirm the pid file is
   gone. The app holds `app/data/app.lock`, so a stray instance blocks the next run and the
   `hotRun --mainClass=…MainKt` variant blocks the IDE too.

## Cleaning up a wrong-scope launch

If a `hotDev` process is already running (yours or the user's), it holds the lock without
being reachable:

```bash
pkill -f "compose.reload.pidFile=.*run/dev/dev.pid"
```

`pkill` exits 143/144 through the Bash tool when it signals a process — that is success, not
an error. Verify with `ls app/build/run/dev/dev.pid` (should be gone).

## Notes

- `status`/`list_windows` are cheap; call `status` first, always.
- The `PreviewSwitcher` chip row is outside `ShareTheme`, so its chips render in M3's purple
  baseline. That is the harness, not the screen — ignore it when comparing screenshots.
- Screenshots capture Compose content only, no window decorations, and work under Wayland
  (the app captures itself). `click`/`type_text` go through the remote-input portal and raise
  a one-time "Allow Remote Interaction" system dialog the user has to grant — avoid input
  tools when a screenshot alone answers the question.
- Deeper background: `docs/dev/hot-reload.md`.
