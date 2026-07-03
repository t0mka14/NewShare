package org.example.app.ui

import org.example.app.domain.audio.AudioError
import org.example.app.domain.session.StorageError

/**
 * Structural sealed-type → localization-key mapping (§11, §7), analogous to
 * `SettingsComponent.configErrorKey` (§11 `ConfigError` → `error.config.*`) which chunk 1 already
 * put on the component side. [TaskComponent.Content.Vocal]'s `Failed(AudioError)` and
 * [SessionComponent.startError]'s `StorageError?` are exposed as raw sealed values instead
 * (no component-side mapping was added for those two), so this one-to-one lookup — no branching
 * beyond the `when`, no business decisions — lives here instead of duplicating it per screen.
 */
fun AudioError.messageKey(): String = when (this) {
    is AudioError.DeviceUnavailable -> "error.audio.deviceUnavailable"
    is AudioError.NoSupportedPcmFormat -> "error.audio.noSupportedPcmFormat"
    is AudioError.RecordingStartFailed -> "error.audio.recordingStartFailed"
    is AudioError.DiskWriteFailed -> "error.audio.diskWriteFailed"
}

fun StorageError.messageKey(): String = when (this) {
    is StorageError.InsufficientDiskSpace -> "error.storage.insufficientDiskSpace"
    is StorageError.WriteFailed -> "error.storage.writeFailed"
    is StorageError.CorruptSessionMetadata -> "error.storage.corruptMetadata"
}
