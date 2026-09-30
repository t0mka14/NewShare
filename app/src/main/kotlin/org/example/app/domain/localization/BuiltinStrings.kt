package org.example.app.domain.localization

/**
 * Bundled fallback strings compiled into the app (§7), in English ([en]) and Czech ([cs]). This
 * is the **only** place built-in display text lives — UI code references keys, never literals
 * (§12). [cs] must define exactly the keys of [en] (`BuiltinStringsTest`).
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
        "error.config.siteTokenMissing" to "No site token is set. Open Settings and enter the token from your administrator.",
        "error.config.siteTokenRejected" to "The server did not recognise this site token. Check it in Settings or contact your administrator.",
        "error.config.siteDeactivated" to "This site has been deactivated on the server. Contact your administrator.",
        "error.config.rateLimited" to "Too many failed attempts. Wait a few minutes and try again.",
        "error.config.networkUnavailable" to "Could not reach the configuration server.",
        "error.config.schemaUnsupported" to "The server configuration is not compatible with this app version. Please update the app.",
        "error.config.validationFailed" to "The server configuration is invalid. Contact your administrator.",
        "error.config.malformed" to "The server configuration could not be read. Contact your administrator.",

        // Generic error dialog, used when a subsystem has no more specific localized message.
        "error.dialog.title" to "Error",
        "error.dialog.dismiss" to "OK",
        "error.dialog.exit" to "Close application",
        "error.generic.message" to "Something went wrong. Please try again.",

        // Settings screen (§3, local-only settings — never config-driven).
        // Calibration screen (config alignment row 5: not a config task, so its text is built in;
        // a config's `strings` may override these keys). Text from the legacy strings_eng.xml.
        "calibration.title" to "Calibration screen",
        "calibration.instructions" to "Please calibrate the microphone position. Make sure that the microphone is placed according to the picture below. Then ask the participant to perform a phonation of the vowel /a/ in a natural voice. If the signal is outside the bounds of the green area move the microphone closer to mouth or otherwise. Then, continue to the first task.",
        "calibration.continueButton" to "Continue",

        "settings.title" to "Settings",
        "settings.device.label" to "Microphone",
        "settings.micGain.label" to "Microphone level",
        "settings.micGain.reset" to "Use configured default ({value})",
        // Empty by design: a config may supply a hint (the legacy app's "recommended level is 63").
        "settings.micGain.hint" to "",
        "settings.siteToken.label" to "Site token",
        "settings.installationId.label" to "Installation ID",
        "settings.language.label" to "Language",
        "settings.refresh.button" to "Refresh configuration",
        "settings.refresh.success" to "Configuration refreshed. Everything looks OK.",
        "settings.refresh.failed" to "Could not refresh the configuration.",

        // Main menu.
        "mainMenu.title" to "Speech examination",
        "mainMenu.startButton" to "New examination",
        "mainMenu.uploadButton" to "Upload",
        "mainMenu.settingsButton" to "Settings",
        "mainMenu.sessionBrowserButton" to "Sessions",
        "mainMenu.protocolPdfButton" to "Get protocol PDF",

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
        // Readouts under the waveform: the trimmed range and where the position line sits.
        "editor.startLabel" to "Start",
        "editor.stopLabel" to "End",
        "editor.durationLabel" to "Length",
        "editor.positionLabel" to "Position",
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
        // The task screen's state button keeps the original's shouty labels (its
        // `standard_protocol_*_button_text` strings) — no other screen uses these three keys.
        "action.start" to "START",
        "action.stop" to "STOP",
        "action.repeat" to "Retry task",
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
        "patientInfo.error.option" to "Please choose one of the options.",
        "patientInfo.error.summary" to "Please check the highlighted fields.",
        // Label of a choice field's dropdown before anything is selected.
        "patientInfo.chooseOption" to "Choose…",

        // Option labels of the identifier catalogue's choice fields (config alignment row 7a; the
        // web sends no options). Stored values are the raw options; see PatientFieldCatalogue.
        "patientField.sex.male" to "Male",
        "patientField.sex.female" to "Female",
        "patientField.education.less_than_upper_secondary" to "Less than upper secondary",
        "patientField.education.upper_secondary_and_vocational" to "Upper secondary and vocational",
        "patientField.education.tertiary_education" to "Tertiary education",

        // Task screen chrome (§8.6). Titles/instructions themselves are config-driven
        // (`titleKey`/`instructionKeys`); these are the surrounding numbering/labels only.
        "task.numberLabel" to "Task {n}",
        "task.numberOfTotalLabel" to "Task {n}/{total}",
        "task.repetitionLabel" to "Repetition {n}",
        "task.takeLabel" to "Take {n}",
        // Legacy prev/next task-button chrome (§13 decision 36): small label line above the
        // task name; `task.endOfProtocol` replaces the next task's name on the last task.
        "task.nextLabel" to "Next task",
        "task.prevLabel" to "Previous task",
        "task.endOfProtocol" to "Finish",

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

    val cs: Map<String, String> = mapOf(
        "app.title" to "SHARE",
        "app.alreadyRunning" to "SHARE už běží. Před spuštěním nové instance zavřete tu původní.",

        "error.config.required" to "Je potřeba konfigurace. Připojte se k síti a aktualizujte ji, nebo kontaktujte správce.",
        "error.config.siteTokenMissing" to "Není nastaven token pracoviště. Otevřete Nastavení a zadejte token od správce.",
        "error.config.siteTokenRejected" to "Server tento token pracoviště nerozpoznal. Zkontrolujte ho v Nastavení nebo kontaktujte správce.",
        "error.config.siteDeactivated" to "Toto pracoviště bylo na serveru deaktivováno. Kontaktujte správce.",
        "error.config.rateLimited" to "Příliš mnoho neúspěšných pokusů. Počkejte několik minut a zkuste to znovu.",
        "error.config.networkUnavailable" to "Nepodařilo se spojit s konfiguračním serverem.",
        "error.config.schemaUnsupported" to "Konfigurace ze serveru není kompatibilní s touto verzí aplikace. Aktualizujte prosím aplikaci.",
        "error.config.validationFailed" to "Konfigurace ze serveru je neplatná. Kontaktujte správce.",
        "error.config.malformed" to "Konfiguraci ze serveru se nepodařilo načíst. Kontaktujte správce.",

        "error.dialog.title" to "Chyba",
        "error.dialog.dismiss" to "OK",
        "error.dialog.exit" to "Zavřít aplikaci",
        "error.generic.message" to "Něco se pokazilo. Zkuste to prosím znovu.",

        "calibration.title" to "Kalibrační obrazovka",
        "calibration.instructions" to "Tato obrazovka slouží ke kalibraci nasazeného mikrofonu. Přesvědčte se, že mikrofon je umístěn stejně jako na obrázku níže. Poté požádejte účastníka, ať provede fonaci hlásky /a/ v pro něj přirozené hlasitosti. Pokud je signál mimo zelenou oblast, posuňte mikrofon blíže k ústům, případně dál. Poté pokračujte na první úlohu.",
        "calibration.continueButton" to "Pokračovat",

        "settings.title" to "Nastavení",
        "settings.device.label" to "Mikrofon",
        "settings.micGain.label" to "Úroveň mikrofonu",
        "settings.micGain.reset" to "Použít nastavenou výchozí hodnotu ({value})",
        "settings.micGain.hint" to "",
        "settings.siteToken.label" to "Token pracoviště",
        "settings.installationId.label" to "ID instalace",
        "settings.language.label" to "Jazyk",
        "settings.refresh.button" to "Aktualizovat konfiguraci",
        "settings.refresh.success" to "Konfigurace byla aktualizována. Vše je v pořádku.",
        "settings.refresh.failed" to "Konfiguraci se nepodařilo aktualizovat.",

        "mainMenu.title" to "Vyšetření řeči",
        "mainMenu.startButton" to "Nové vyšetření",
        "mainMenu.uploadButton" to "Odeslání nahrávek",
        "mainMenu.settingsButton" to "Nastavení",
        "mainMenu.sessionBrowserButton" to "Sezení",
        "mainMenu.protocolPdfButton" to "PDF manuál protokolu",

        "patientInfo.title" to "Údaje o pacientovi",
        "patientInfo.continueButton" to "Pokračovat",

        "upload.title" to "Odeslání dat",
        "upload.instructions" to "Níže uvedená sezení jsou připravena k odeslání. Odesílání může podle rychlosti připojení chvíli trvat — prosím nezavírejte aplikaci.",
        "upload.readyCount" to "Sezení připravená k odeslání: {count}",
        "upload.noSessions" to "Žádná sezení nejsou připravena k odeslání.",
        "upload.successMessage" to "Soubory byly úspěšně odeslány.",
        "upload.failureMessage" to "Odeslání selhalo: {reason}",
        "upload.error.interrupted" to "předchozí pokus byl přerušen",
        "upload.error.network" to "chyba sítě",
        "upload.error.server" to "chyba serveru",
        "upload.error.rejected" to "server odeslání odmítl",
        "upload.error.generic" to "nastala neznámá chyba",

        "sessionBrowser.title" to "Sezení",
        "sessionBrowser.noSessions" to "Zatím nebyla nahrána žádná sezení.",
        "sessionBrowser.reprocessButton" to "Znovu zpracovat",
        "sessionBrowser.openEditorButton" to "Otevřít editor",
        "sessionBrowser.retryUploadButton" to "Odeslat znovu",
        "sessionBrowser.goToUploadButton" to "Přejít k odeslání",
        "sessionBrowser.recoveredLabel" to "Obnoveno",
        "sessionBrowser.processingStatus.NotProcessed" to "Nezpracováno",
        "sessionBrowser.processingStatus.Processing" to "Zpracovává se…",
        "sessionBrowser.processingStatus.Done" to "Zpracováno",
        "sessionBrowser.processingStatus.Failed" to "Zpracování selhalo",
        "sessionBrowser.uploadStatus.NotUploaded" to "Neodesláno",
        "sessionBrowser.uploadStatus.Uploading" to "Odesílá se…",
        "sessionBrowser.uploadStatus.Uploaded" to "Odesláno",
        "sessionBrowser.uploadStatus.Failed" to "Odeslání selhalo",

        "editor.title" to "Kontrola nahrávek",
        "editor.instructions" to "Posunutím počáteční a koncové značky nahrávku zkraťte. Přehrajte si ji pro kontrolu a poté ji potvrďte.",
        "editor.segmentOfTotal" to "Nahrávka {n} z {total}",
        "editor.noSegments" to "V tomto sezení není co kontrolovat.",
        "editor.startLabel" to "Začátek",
        "editor.stopLabel" to "Konec",
        "editor.durationLabel" to "Délka",
        "editor.positionLabel" to "Pozice",
        "action.accept" to "Potvrdit",

        "processing.title" to "Zpracování",
        "processing.step.selectingTimeline" to "Příprava…",
        "processing.step.cuttingClips" to "Stříhání nahrávek…",
        "processing.step.buildingArchive" to "Vytváření archivu…",
        "processing.step.updatingMetadata" to "Dokončování…",
        "processing.error.title" to "Zpracování selhalo",
        "processing.error.generic" to "Toto sezení se nepodařilo zpracovat. Můžete to zkusit znovu, nebo se vrátit zpět.",

        "protocolPicker.title" to "Vyberte protokol",
        "protocolPicker.instructions" to "Zvolte protokol pro toto vyšetření.",

        "task.playExample" to "Přehrát ukázku",
        "task.stopExample" to "Zastavit ukázku",

        "action.start" to "START",
        "action.stop" to "STOP",
        "action.repeat" to "Opakovat úlohu",
        "action.next" to "Další",
        "action.skip" to "Přeskočit",
        "action.back" to "Zpět",
        "action.confirm" to "Potvrdit",
        "action.cancel" to "Zrušit",
        "action.upload" to "Odeslat",
        "action.retry" to "Zkusit znovu",
        "action.play" to "Přehrát",
        "action.previous" to "Předchozí",
        "action.reconnect" to "Znovu připojit",
        "action.resume" to "Pokračovat",
        "action.done" to "Hotovo",

        "patientInfo.error.required" to "Toto pole je povinné.",
        "patientInfo.error.pattern" to "Zkontrolujte prosím formát tohoto pole.",
        "patientInfo.error.option" to "Vyberte prosím jednu z možností.",
        "patientInfo.error.summary" to "Zkontrolujte prosím zvýrazněná pole.",
        "patientInfo.chooseOption" to "Vyberte…",

        "patientField.sex.male" to "Muž",
        "patientField.sex.female" to "Žena",
        "patientField.education.less_than_upper_secondary" to "Nižší než středoškolské",
        "patientField.education.upper_secondary_and_vocational" to "Středoškolské a odborné",
        "patientField.education.tertiary_education" to "Vysokoškolské",

        "task.numberLabel" to "Úloha {n}",
        "task.numberOfTotalLabel" to "Úloha {n}/{total}",
        "task.repetitionLabel" to "Opakování {n}",
        "task.takeLabel" to "Pokus {n}",
        "task.nextLabel" to "Další úloha",
        "task.prevLabel" to "Předchozí úloha",
        "task.endOfProtocol" to "Konec",

        "questionnaire.error.invalid" to "Zadejte prosím platnou odpověď.",

        "error.audio.deviceLost" to "Mikrofon byl odpojen. Připojte ho znovu nebo pro pokračování zvolte jiné zařízení.",
        "error.audio.deviceUnavailable" to "Zvolený mikrofon není dostupný.",
        "error.audio.noSupportedPcmFormat" to "Toto zařízení nepodporuje kompatibilní formát nahrávání.",
        "error.audio.recordingStartFailed" to "Nahrávání se nepodařilo spustit.",
        "error.audio.diskWriteFailed" to "Nahrávku se nepodařilo uložit na disk.",

        "error.storage.insufficientDiskSpace" to "Na disku není dost místa pro zahájení nového nahrávání.",
        "error.storage.writeFailed" to "Nepodařilo se vytvořit složku sezení.",
        "error.storage.corruptMetadata" to "Data sezení se nepodařilo načíst.",
        "session.failedTitle" to "Sezení se nepodařilo zahájit",

        "sessionSummary.title" to "Vyšetření dokončeno",
        "sessionSummary.message" to "Vyšetření skončilo. Děkujeme.",

        "blocking.openSettingsButton" to "Otevřít nastavení",

        "placeholder.title" to "Již brzy",
        "placeholder.message" to "Tato obrazovka zatím není k dispozici.",
    )

    /** Built-in strings by language code; languages missing here fall back to [en]. */
    val byLanguage: Map<String, Map<String, String>> = mapOf("en" to en, "cs" to cs)
}
