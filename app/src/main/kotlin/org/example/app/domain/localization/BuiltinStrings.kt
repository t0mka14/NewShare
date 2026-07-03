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
        "upload.readyCount" to "{count} sessions ready to upload",
        "upload.successMessage" to "Files successfully uploaded.",
        "upload.failureMessage" to "Upload failed: {reason}",

        // Session browser (§8.11).
        "sessionBrowser.title" to "Sessions",
        "sessionBrowser.reprocessButton" to "Reprocess",
        "sessionBrowser.openEditorButton" to "Open editor",

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
