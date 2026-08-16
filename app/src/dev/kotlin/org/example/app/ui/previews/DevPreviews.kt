package org.example.app.ui.previews

import androidx.compose.runtime.Composable
import org.jetbrains.compose.reload.DevelopmentEntryPoint

/**
 * Hot-reload entry points: `./gradlew :app:hotDev --className=… --funName=… --auto` runs one of
 * these in its own window and re-renders it whenever a source file changes. In IntelliJ IDEA each
 * one also gets a run gutter icon.
 *
 * They live in the Compose Hot Reload `dev` source set (`app/src/dev/kotlin`), which sees `main`
 * but is **not** part of the distribution — unlike the `@Preview` functions in `main`, which the
 * IDE's preview pane requires to be there.
 */

@DevelopmentEntryPoint
@Composable
fun CalibrationDev() = CalibrationInRangePreview()

@DevelopmentEntryPoint
@Composable
fun CalibrationTooQuietDev() = CalibrationTooQuietPreview()

@DevelopmentEntryPoint
@Composable
fun CalibrationDeviceLostDev() = CalibrationDeviceLostPreview()
