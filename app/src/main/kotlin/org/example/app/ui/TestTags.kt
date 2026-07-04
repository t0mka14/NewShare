package org.example.app.ui

/**
 * Stable `Modifier.testTag(...)` constants shared by prod and test code (§10.3).
 * Every interactive composable gets a tag from here — no ad-hoc string literals
 * at call sites, so a rename is a one-place edit and UI tests can't typo a tag.
 *
 * Structure/ownership (§10 boundary): QA owns this object's shape — one nested
 * object per screen, `SCREAMING_SNAKE_CASE` constants holding `"screen.element"`
 * strings (dot-separated, matching the §10.3 examples: `task.startButton`,
 * `calibration.confirmButton`, `patientInfo.field.code`). Per-item/dynamic tags
 * (e.g. one text field per configured patient field) are functions, not
 * constants, since the set of items comes from the remote config (§6) and isn't
 * known at compile time.
 *
 * UI-engineer adds tags here as each screen is built and wires the matching
 * `Modifier.testTag(...)` into the composable — this object only reserves the
 * naming convention and the tags already known to be needed. `Upload`/
 * `SessionBrowser`/`Editor` tags are reserved ahead of their chunk (placeholder
 * screens exist for the first two in the meantime, §12 "current state").
 */
object TestTags {

    /** Main menu (§3): New examination / Upload / Settings / Sessions. */
    object MainMenu {
        const val START_PROTOCOL_BUTTON = "mainMenu.startProtocolButton"
        const val UPLOAD_BUTTON = "mainMenu.uploadButton"
        const val SETTINGS_BUTTON = "mainMenu.settingsButton"
        const val SESSION_BROWSER_BUTTON = "mainMenu.sessionBrowserButton"
    }

    /** Patient info screen (§8.10 participant.json; fields are config-driven, §6.2). */
    object PatientInfo {
        const val CONTINUE_BUTTON = "patientInfo.continueButton"
        const val BACK_BUTTON = "patientInfo.backButton"
        const val ERROR_TEXT = "patientInfo.errorText"

        /** One text field per configured `PatientField.name`, e.g. `patientInfo.field.code`. */
        fun field(fieldName: String) = "patientInfo.field.$fieldName"

        /** Validation error text for one configured field, child of [field]. */
        fun fieldError(fieldName: String) = "patientInfo.fieldError.$fieldName"
    }

    /** Calibration screen (§6.2 CALIBRATION task, mandatory before the first VOCAL task). */
    object Calibration {
        const val CONFIRM_BUTTON = "calibration.confirmButton"
        const val LEVEL_INDICATOR = "calibration.levelIndicator"
        const val DEVICE_LOST_ERROR = "calibration.deviceLostError"
        const val DEVICE_SELECT = "calibration.deviceSelect"
    }

    /** Task screen state machine (§8.6): Idle/Capturing/Stopped/Failed button set. */
    object Task {
        const val START_BUTTON = "task.startButton"
        const val STOP_BUTTON = "task.stopButton"
        const val REPEAT_BUTTON = "task.repeatButton"
        const val NEXT_BUTTON = "task.nextButton"
        const val SKIP_BUTTON = "task.skipButton"
        const val LEVEL_INDICATOR = "task.levelIndicator"
        const val ERROR_DIALOG = "task.errorDialog"
        const val ERROR_DIALOG_DISMISS_BUTTON = "task.errorDialogDismissButton"
        const val DEVICE_LOST_ERROR = "task.errorDialog.deviceLost"

        /** Example-audio playback button (§8.6, follow-up) — disabled while `Capturing`. */
        const val EXAMPLE_AUDIO_BUTTON = "task.exampleAudioButton"
    }

    /** QUESTIONNAIRE task rendering (§8.6): one answer input per configured question. */
    object Questionnaire {
        fun answerField(questionKey: String) = "questionnaire.answer.$questionKey"

        /** One option toggle (radio/checkbox) within a SINGLE_CHOICE/MULTIPLE_CHOICE question. */
        fun answerOption(questionKey: String, option: String) = "questionnaire.answer.$questionKey.$option"

        /** Per-question validation error text, child of [answerField]. */
        fun validationError(questionKey: String) = "questionnaire.validationError.$questionKey"
    }

    /** Device-loss reconnect dialog (§8.5) — blocking Compose dialog, not an OS window (§5.2). */
    object DeviceLostDialog {
        /** One-click retry with the same device that was in use before the loss. */
        const val RECONNECT_BUTTON = "deviceLostDialog.reconnectButton"
        const val DEVICE_SELECT = "deviceLostDialog.deviceSelect"

        /** Confirms a device picked via [DEVICE_SELECT] (two-step select-then-resume flows). */
        const val RESUME_BUTTON = "deviceLostDialog.resumeButton"
    }

    /** Session-start failure and the post-protocol summary stub (§8.6 Bootstrapping/Failed). */
    object Session {
        const val FAILED_MESSAGE = "session.failedMessage"
        const val BACK_TO_MENU_BUTTON = "session.backToMenuButton"
    }

    /** Minimal post-protocol summary shown before returning to the main menu. */
    object SessionSummary {
        const val DONE_BUTTON = "sessionSummary.doneButton"
    }

    /** Waveform boundary editor (§8.7), shown when `enableEditor` is true. */
    object Editor {
        const val WAVEFORM_CANVAS = "editor.waveformCanvas"
        const val START_BOUNDARY_HANDLE = "editor.startBoundaryHandle"
        const val STOP_BOUNDARY_HANDLE = "editor.stopBoundaryHandle"
        const val PLAY_SEGMENT_BUTTON = "editor.playSegmentButton"
        const val PREVIOUS_SEGMENT_BUTTON = "editor.previousSegmentButton"
        const val NEXT_SEGMENT_BUTTON = "editor.nextSegmentButton"
        const val ACCEPT_BUTTON = "editor.acceptButton"
        const val SEGMENT_LABEL = "editor.segmentLabel"
    }

    /** Processing progress screen (§8.8) — blocks navigation while `ProcessSessionUseCase` runs. */
    object Processing {
        const val STEP_LABEL = "processing.stepLabel"
        const val PROGRESS_BAR = "processing.progressBar"
        const val ERROR_TEXT = "processing.errorText"
        const val RETRY_BUTTON = "processing.retryButton"
        const val BACK_BUTTON = "processing.backButton"
    }

    /** Protocol picker (§3 follow-up) — shown on the main menu only when the config has more
     * than one protocol; a single-protocol config skips straight to patient info. */
    object ProtocolPicker {
        fun protocolButton(protocolName: String) = "protocolPicker.protocolButton.$protocolName"
        const val BACK_BUTTON = "protocolPicker.backButton"
    }

    /** Upload screen (§8.9), reached via the main-screen Upload button. */
    object Upload {
        const val UPLOAD_BUTTON = "upload.uploadButton"
        const val BACK_BUTTON = "upload.backButton"
        const val PROGRESS_BAR = "upload.progressBar"
        const val READY_COUNT_TEXT = "upload.readyCountText"
        const val SUCCESS_MESSAGE = "upload.successMessage"

        /** One row per session eligible for upload; error reason is a child of the row, §8.9. */
        fun sessionRow(sessionId: String) = "upload.sessionRow.$sessionId"
        fun sessionErrorText(sessionId: String) = "upload.sessionErrorText.$sessionId"
    }

    /** Session browser (§8.11). */
    object SessionBrowser {
        const val BACK_BUTTON = "sessionBrowser.backButton"
        const val GO_TO_UPLOAD_BUTTON = "sessionBrowser.goToUploadButton"

        fun sessionRow(sessionId: String) = "sessionBrowser.sessionRow.$sessionId"
        fun reprocessButton(sessionId: String) = "sessionBrowser.reprocessButton.$sessionId"
        fun openEditorButton(sessionId: String) = "sessionBrowser.openEditorButton.$sessionId"
        fun retryUploadButton(sessionId: String) = "sessionBrowser.retryUploadButton.$sessionId"
    }

    /** Settings screen (§3): mic device, installation ID, language, refresh config. */
    object Settings {
        const val DEVICE_SELECT = "settings.deviceSelect"
        const val INSTALLATION_ID_FIELD = "settings.installationIdField"
        const val LANGUAGE_SELECT = "settings.languageSelect"
        const val REFRESH_CONFIG_BUTTON = "settings.refreshConfigButton"
        const val BACK_BUTTON = "settings.backButton"

        /** One selectable item per enumerated device within [DEVICE_SELECT]'s dropdown. */
        fun deviceOption(deviceId: String) = "settings.deviceOption.$deviceId"

        /** One selectable item per configured language within [LANGUAGE_SELECT]'s dropdown. */
        fun languageOption(language: String) = "settings.languageOption.$language"
    }

    /** Blocking screens with no config / no cache (§6.1) and the single-instance message (§5.2). */
    object Blocking {
        const val CONFIGURATION_REQUIRED_MESSAGE = "blocking.configurationRequiredMessage"
        const val SINGLE_INSTANCE_MESSAGE = "blocking.singleInstanceMessage"
        const val OPEN_SETTINGS_BUTTON = "blocking.openSettingsButton"
    }
}
