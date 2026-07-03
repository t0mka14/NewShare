package org.example.app.domain.session

/**
 * Storage error taxonomy (§11 `StorageError`) for the pieces owned here: preflight/mid-session
 * disk space, write failures, and missing/corrupt session metadata. Waveform generation, clip
 * export, ZIP creation, and lock acquisition failures belong to other subsystems
 * (`ProcessSessionUseCase`/audio-engineer/integration-engineer respectively) and are not
 * modeled by this sealed type.
 *
 * None of these carry participant data or raw session folder names in a form meant for
 * logging (§11/§12) — callers that log an error should reference [Examination.sessionId]
 * where available, never a folder name (which embeds the sanitized patient code, §8.2).
 */
sealed interface StorageError {
    data class InsufficientDiskSpace(val requiredBytes: Long, val availableBytes: Long) : StorageError
    data class WriteFailed(val detail: String) : StorageError
    data class CorruptSessionMetadata(val detail: String) : StorageError
}
