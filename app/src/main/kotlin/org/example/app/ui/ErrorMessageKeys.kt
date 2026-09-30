package org.example.app.ui

import org.example.app.domain.audio.AudioError
import org.example.app.domain.session.StorageError

/**
 * Structural sealed-type → localization-key mapping (§11, §7) for errors only screens show:
 * [TaskComponent.Content.Vocal]'s `Failed(AudioError)` and [SessionComponent.startError]'s
 * `StorageError?` are exposed as raw sealed values, so this one-to-one lookup — no branching
 * beyond the `when`, no business decisions — lives here instead of duplicating it per screen.
 * `ConfigError.messageKey()` lives with [org.example.app.domain.config.ConfigError] instead,
 * because a component (Settings) needs it too and components must not depend on `ui/` (§5.2).
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
