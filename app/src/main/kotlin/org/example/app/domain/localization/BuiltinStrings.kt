package org.example.app.domain.localization

/**
 * Bundled English fallback strings compiled into the app (§7). This is the **only** place
 * built-in display text lives — UI code references keys, never literals (§12).
 *
 * Coverage (§7 minimum + Phase 2 screen chrome, see the integration-engineer's task report for
 * the full rationale): everything that must render before/without a config (single-instance
 * message, configuration-required screen, its per-[org.example.app.domain.config.ConfigError]
 * detail messages), Settings labels, a generic error dialog, and the common action-button
 * labels (`action.*`) every Phase 2 screen needs — task instructions/titles themselves are
 * config-driven (`titleKey`/`instructionKeys`) and are never built-in.
 *
 * Key naming: dotted lowercase, `<area>.<element>[.<detail>]` (§ task instructions).
 * Placeholders use the named `{…}` syntax (§7), consistent with config strings.
 */
object BuiltinStrings {
    val en: Map<String, String> = mapOf(
        // Application chrome / single-instance lock (§5.2).
        "app.title" to "SHARE",
        "app.alreadyRunning" to "SHARE is already running. Close the other instance before starting a new one.",

        // Blocking "configuration required" screen (§6.1 pt 4) and its per-reason detail text.
        "error.config.required" to "Configuration required. Connect to the network and refresh, or contact your administrator.",
        "error.config.installationIdMissing" to "No installation ID is set. Open Settings and enter one.",
        "error.config.installationIdRejected" to "This installation ID was not accepted by the server. Contact your administrator.",
        "error.config.networkUnavailable" to "Could not reach the configuration server.",
        "error.config.schemaUnsupported" to "The server configuration is not compatible with this app version. Please update the app.",
        "error.config.validationFailed" to "The server configuration is invalid. Contact your administrator.",
        "error.config.malformed" to "The server configuration could not be read. Contact your administrator.",

        // Generic error dialog, used when a subsystem has no more specific localized message.
        "error.dialog.title" to "Error",
        "error.dialog.dismiss" to "OK",
        "error.generic.message" to "Something went wrong. Please try again.",

        // Settings screen (§3, local-only settings — never config-driven).
        "settings.title" to "Settings",
        "settings.device.label" to "Microphone",
        "settings.installationId.label" to "Installation ID",
        "settings.language.label" to "Language",
        "settings.refresh.button" to "Refresh configuration",
        "settings.refresh.success" to "Configuration refreshed.",
        "settings.refresh.failed" to "Could not refresh the configuration.",

        // Main menu.
        "mainMenu.title" to "SHARE",
        "mainMenu.startButton" to "New examination",
        "mainMenu.uploadButton" to "Upload",
        "mainMenu.settingsButton" to "Settings",
        "mainMenu.sessionBrowserButton" to "Sessions",

        // Patient info screen.
        "patientInfo.title" to "Patient information",
        "patientInfo.continueButton" to "Continue",

        // Upload screen (§8.9).
        "upload.title" to "Upload",
        "upload.instructions" to "Sessions listed below are ready to upload. Uploading may take a while depending on connection speed — please do not close the app.",
        "upload.readyCount" to "{count} sessions ready to upload",
        "upload.noSessions" to "No sessions are ready to upload.",
        "upload.successMessage" to "Files successfully uploaded.",
        "upload.failureMessage" to "Upload failed: {reason}",
        "upload.error.interrupted" to "the previous attempt was interrupted",
        "upload.error.network" to "network failure",
        "upload.error.server" to "server error",
        "upload.error.rejected" to "the server rejected the upload",
        "upload.error.generic" to "an unknown error occurred",

        // Session browser (§8.11).
        "sessionBrowser.title" to "Sessions",
        "sessionBrowser.noSessions" to "No sessions recorded yet.",
        "sessionBrowser.reprocessButton" to "Reprocess",
        "sessionBrowser.openEditorButton" to "Open editor",
        "sessionBrowser.retryUploadButton" to "Retry upload",
        "sessionBrowser.goToUploadButton" to "Go to upload",
        "sessionBrowser.recoveredLabel" to "Recovered",
        "sessionBrowser.processingStatus.NotProcessed" to "Not processed",
        "sessionBrowser.processingStatus.Processing" to "Processing…",
        "sessionBrowser.processingStatus.Done" to "Processed",
        "sessionBrowser.processingStatus.Failed" to "Processing failed",
        "sessionBrowser.uploadStatus.NotUploaded" to "Not uploaded",
        "sessionBrowser.uploadStatus.Uploading" to "Uploading…",
        "sessionBrowser.uploadStatus.Uploaded" to "Uploaded",
        "sessionBrowser.uploadStatus.Failed" to "Upload failed",

        // Waveform editor (§8.7), shown after the protocol when `enableEditor` is true.
        "editor.title" to "Review recordings",
        "editor.instructions" to "Drag the start and end markers to trim this recording. Play it back to check, then accept to continue.",
        "editor.segmentOfTotal" to "Recording {n} of {total}",
        "editor.noSegments" to "There is nothing to review for this session.",
        "action.accept" to "Accept",

        // Processing progress screen (§8.8) — blocks navigation while the session is processed.
        "processing.title" to "Processing",
        "processing.step.selectingTimeline" to "Preparing…",
        "processing.step.cuttingClips" to "Cutting recordings…",
        "processing.step.buildingArchive" to "Building archive…",
        "processing.step.updatingMetadata" to "Finishing up…",
        "processing.error.title" to "Processing failed",
        "processing.error.generic" to "This session could not be processed. You can retry or go back.",

        // Protocol picker (§3 follow-up) — shown on the main menu only when the config defines
        // more than one protocol; a single-protocol config skips straight to patient info.
        "protocolPicker.title" to "Choose a protocol",
        "protocolPicker.instructions" to "Select which protocol to run for this examination.",

        // Example-audio playback on VOCAL task screens (§8.6 follow-up).
        "task.playExample" to "Play example",
        "task.stopExample" to "Stop example",

        // Common action-button labels shared across main-menu/patient-info/calibration/task/
        // editor/upload screens (§8.6, §8.7, §8.9).
        "action.start" to "Start",
        "action.stop" to "Stop",
        "action.repeat" to "Repeat",
        "action.next" to "Next",
        "action.skip" to "Skip",
        "action.back" to "Back",
        "action.confirm" to "Confirm",
        "action.cancel" to "Cancel",
        "action.upload" to "Upload",
        "action.retry" to "Retry",
        "action.play" to "Play",
        "action.previous" to "Previous",
        "action.reconnect" to "Reconnect",
        "action.resume" to "Resume",
        "action.done" to "Done",

        // Patient info field validation (§8.10, generic — regex/required come from server config,
        // the message text does not).
        "patientInfo.error.required" to "This field is required.",
        "patientInfo.error.pattern" to "Please check the format of this field.",
        "patientInfo.error.summary" to "Please check the highlighted fields.",

        // Task screen chrome (§8.6). Titles/instructions themselves are config-driven
        // (`titleKey`/`instructionKeys`); these are the surrounding numbering/labels only.
        "task.numberLabel" to "Task {n}",
        "task.numberOfTotalLabel" to "Task {n} of {total}",
        "task.repetitionLabel" to "Repetition {n}",
        "task.takeLabel" to "Take {n}",

        // Questionnaire task rendering (§8.6 OPEN/SINGLE_CHOICE/MULTIPLE_CHOICE).
        "questionnaire.error.invalid" to "Please provide a valid answer.",

        // Device loss mid-session (§8.5) — shared between the calibration and task screens.
        "error.audio.deviceLost" to "The microphone was disconnected. Reconnect it or choose another device to continue.",
        "error.audio.deviceUnavailable" to "The selected microphone is unavailable.",
        "error.audio.noSupportedPcmFormat" to "This device does not support a compatible recording format.",
        "error.audio.recordingStartFailed" to "Recording could not be started.",
        "error.audio.diskWriteFailed" to "Recording could not be saved to disk.",

        // Session-start failures (§8.1 preflight, StartSessionUseCase.Outcome.Rejected).
        "error.storage.insufficientDiskSpace" to "Not enough free disk space to start a new recording.",
        "error.storage.writeFailed" to "Could not create the session folder.",
        "error.storage.corruptMetadata" to "Session data could not be read.",
        "session.failedTitle" to "Could not start the session",

        // Minimal post-protocol summary stub (§8.6 — the real summary with clip listing lands
        // with the processing/upload chunk).
        "sessionSummary.title" to "Examination complete",
        "sessionSummary.message" to "The examination has finished. Thank you.",

        // Blocking "configuration required" screen's way back into Settings (§6.1).
        "blocking.openSettingsButton" to "Open settings",

        // Screens not yet built in this phase (§8.9/§8.11 land in a later chunk).
        "placeholder.title" to "Coming soon",
        "placeholder.message" to "This screen isn't available yet.",
    )
}
