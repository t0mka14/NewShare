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
 * naming convention and the tags already known to be needed. As of this
 * writing, `HomeContent`/`RecorderContent` (the current skeleton screens, §12)
 * have no tags wired in yet; see the note in `ComposeUiHarnessSmokeTest`.
 */
object TestTags {

    /** Main menu (current skeleton: `ui/HomeContent.kt`, `navigation/HomeComponent.kt`). */
    object Home {
        const val RECORDER_BUTTON = "home.recorderButton"
    }

    /** Demo-era recorder screen (`ui/RecorderContent.kt`) — replaced by session/task screens per §12. */
    object Recorder {
        const val RECORD_BUTTON = "recorder.recordButton"
        const val STOP_BUTTON = "recorder.stopButton"
        const val START_AGAIN_BUTTON = "recorder.startAgainButton"
        const val SAVE_BUTTON = "recorder.saveButton"
        const val STATUS_TEXT = "recorder.statusText"
        const val BACK_BUTTON = "recorder.backButton"
    }

    /** Patient info screen (§8.10 participant.json; fields are config-driven, §6.2). */
    object PatientInfo {
        const val CONTINUE_BUTTON = "patientInfo.continueButton"
        const val ERROR_TEXT = "patientInfo.errorText"

        /** One text field per configured `PatientField.name`, e.g. `patientInfo.field.code`. */
        fun field(fieldName: String) = "patientInfo.field.$fieldName"
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
    }

    /** QUESTIONNAIRE task rendering (§8.6): one answer input per configured question. */
    object Questionnaire {
        fun answerField(questionKey: String) = "questionnaire.answer.$questionKey"
        const val VALIDATION_ERROR = "questionnaire.validationError"
    }

    /** Device-loss reconnect dialog (§8.5) — blocking Compose dialog, not an OS window (§5.2). */
    object DeviceLostDialog {
        const val RECONNECT_BUTTON = "deviceLostDialog.reconnectButton"
        const val DEVICE_SELECT = "deviceLostDialog.deviceSelect"
        const val RESUME_BUTTON = "deviceLostDialog.resumeButton"
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
    }

    /** Upload screen (§8.9), reached via the main-screen Upload button. */
    object Upload {
        const val UPLOAD_BUTTON = "upload.uploadButton"
        const val BACK_BUTTON = "upload.backButton"
        const val PROGRESS_BAR = "upload.progressBar"
        const val READY_COUNT_TEXT = "upload.readyCountText"

        /** One row per session eligible for upload; error reason is a child of the row, §8.9. */
        fun sessionRow(sessionId: String) = "upload.sessionRow.$sessionId"
        fun sessionErrorText(sessionId: String) = "upload.sessionErrorText.$sessionId"
    }

    /** Session browser (§8.11). */
    object SessionBrowser {
        fun sessionRow(sessionId: String) = "sessionBrowser.sessionRow.$sessionId"
        const val REPROCESS_BUTTON = "sessionBrowser.reprocessButton"
        const val OPEN_EDITOR_BUTTON = "sessionBrowser.openEditorButton"
        const val RETRY_UPLOAD_BUTTON = "sessionBrowser.retryUploadButton"
    }

    /** Settings screen (§3): mic device, installation ID, language, refresh config. */
    object Settings {
        const val DEVICE_SELECT = "settings.deviceSelect"
        const val INSTALLATION_ID_FIELD = "settings.installationIdField"
        const val LANGUAGE_SELECT = "settings.languageSelect"
        const val REFRESH_CONFIG_BUTTON = "settings.refreshConfigButton"
    }

    /** Blocking screens with no config / no cache (§6.1) and the single-instance message (§5.2). */
    object Blocking {
        const val CONFIGURATION_REQUIRED_MESSAGE = "blocking.configurationRequiredMessage"
        const val SINGLE_INSTANCE_MESSAGE = "blocking.singleInstanceMessage"
    }
}
