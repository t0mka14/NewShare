package org.example.app.ui

import org.example.app.domain.audio.AudioError
import org.example.app.domain.config.ConfigError
import org.example.app.domain.session.StorageError

/**
 * Structural sealed-type → localization-key mapping (§11, §7). [ConfigError] is shown by both
 * Settings and the blocking screen; [TaskComponent.Content.Vocal]'s `Failed(AudioError)` and
 * [SessionComponent.startError]'s `StorageError?` are exposed as raw sealed values, so this
 * one-to-one lookup — no branching beyond the `when`, no business decisions — lives here instead
 * of duplicating it per screen.
 */
fun ConfigError.messageKey(): String = when (this) {
    ConfigError.SiteTokenMissing -> "error.config.siteTokenMissing"
    ConfigError.SiteTokenRejected -> "error.config.siteTokenRejected"
    ConfigError.SiteDeactivated -> "error.config.siteDeactivated"
    ConfigError.RateLimited -> "error.config.rateLimited"
    ConfigError.NetworkUnavailableNoCache -> "error.config.networkUnavailable"
    is ConfigError.SchemaUnsupported -> "error.config.schemaUnsupported"
    is ConfigError.ValidationFailed -> "error.config.validationFailed"
    is ConfigError.Malformed -> "error.config.malformed"
    is ConfigError.ServerError -> "settings.refresh.failed"
}

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
