package org.example.app.ui.previews

import androidx.compose.ui.window.singleWindowApplication

/**
 * Runs the [EditorPreviews] states in a real window: `./gradlew :app:previewEditor`, or with hot
 * reload `./gradlew :app:hotRun --mainClass=org.example.app.ui.previews.EditorPreviewHarnessKt
 * --auto`. The editor is the screen that most needs a live window rather than a static pane: the
 * waveform panel is sized by the window, the boundaries are dragged, and the position line moves.
 *
 * No session, no audio device, no filesystem — the peaks are synthetic. Playing back real audio
 * against the real component is `EditorLiveDev` in the `dev` source set.
 */
fun main() = singleWindowApplication(title = "Editor — preview harness") {
    PreviewSwitcher(
        "Idle" to { EditorIdlePreview() },
        "Playing" to { EditorPlayingPreview() },
        "Loading" to { EditorLoadingPreview() },
        "No segments" to { EditorNoSegmentsPreview() },
        "Dark" to { EditorDarkPreview() },
    )
}
