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
        "calibration.micError" to "The microphone could not be opened. Check that it is connected and that Windows microphone access (\"Let apps access your microphone\") is allowed, then press Retry or choose another device.",
        "calibration.micErrorRestart" to "Still can\'t open the microphone. If you just allowed microphone access in Windows, close and restart the app, then start the examination again.",

        "settings.title" to "Settings",
        "settings.device.label" to "Microphone",
        "settings.device.refresh" to "Refresh microphones",
        "settings.device.default.config" to "Default from the site configuration",
        "settings.device.default.system" to "System default",
        "settings.camera.label" to "Camera",
        "settings.camera.automatic" to "Automatic (first available camera)",
        "settings.camera.loading" to "Looking for cameras…",
        "settings.camera.none" to "No camera found",
        "settings.camera.unavailable" to "{name} (not connected)",
        "settings.micGain.label" to "Microphone level",
        "settings.micGain.reset" to "Use configured default ({value})",
        "settings.micGain.unavailable" to "Microphone level can not be set for this device.",
        // Empty by design: a config may supply a hint (the legacy app's "recommended level is 63").
        "settings.micGain.hint" to "",
        "settings.siteToken.label" to "Site token",
        "settings.server.title" to "Server",
        "settings.installationId.label" to "Installation ID",
        "settings.otherSite" to "This computer is registered to another site. Contact your administrator.",
        "settings.language.label" to "Language",
        "settings.indicator.label" to "Recording indicator",
        "settings.indicator.CIRCLE" to "Circle",
        "settings.indicator.WAVEFORM" to "Waveform",
        "settings.indicator.reset" to "Use configured default ({value})",
        "settings.refresh.button" to "Refresh configuration",
        "settings.refresh.success" to "Configuration refreshed. Everything looks OK.",
        "settings.refresh.otherSite" to "The new configuration was not applied: this computer is registered to another site. Contact your administrator.",
        "settings.refresh.failed" to "Could not refresh the configuration.",
        "settings.refresh.offline" to "Could not reach the server. Using the saved configuration.",
        "settings.backButton" to "Save and go back",

        // Main menu.
        "mainMenu.title" to "Speech examination",
        "mainMenu.site" to "Site: {name}",
        "mainMenu.appVersion" to "App version: {version}",
        "mainMenu.offlineNotice" to "Offline – using the configuration saved on {date}",
        "mainMenu.startButton" to "New examination",
        "mainMenu.uploadButton" to "Upload",
        "mainMenu.settingsButton" to "Settings",
        "mainMenu.sessionBrowserButton" to "Sessions",
        "mainMenu.protocolPdfButton" to "Get protocol PDF",
        "mainMenu.recovery.title" to "Interrupted sessions recovered",
        "mainMenu.recovery.message" to "The application closed unexpectedly during a previous session. The interrupted sessions were found and their recordings were recovered.",
        "mainMenu.recovery.recoveredHeading" to "Recovered:",
        "mainMenu.recovery.failedHeading" to "Could not be recovered:",
        "mainMenu.recovery.entry" to "{patient} ({date})",
        "mainMenu.recovery.openSessions" to "Open Sessions",

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
        "upload.error.unreachable" to "the server could not be reached. The session is kept and can be uploaded later - press Upload to try again",
        "upload.error.server" to "server error",
        "upload.error.rejected" to "the server rejected the upload",
        "upload.error.notRegistered" to "this computer is not registered to a site; contact your administrator",
        "upload.error.conflict" to "the session is already on the server with different content; contact your administrator",
        "upload.error.tooLarge" to "the session is too large",
        "upload.error.busy" to "the server is busy, try again later",
        "upload.error.generic" to "an unknown error occurred",

        // Session browser (§8.11).
        "sessionBrowser.title" to "Sessions",
        "sessionBrowser.instructions" to "Edit recorded sessions. Open editor: lets you cut unwanted parts out of the session's audio. Process / Retry processing: builds the session's clips and archive from the recording — offered for sessions that are not processed yet or whose processing failed. Saving changes in the editor reprocesses the session by itself.",
        "sessionBrowser.noSessions" to "No sessions recorded yet.",
        "sessionBrowser.processButton" to "Process",
        "sessionBrowser.retryProcessingButton" to "Retry processing",
        "sessionBrowser.result.processed" to "Session {patient} processed.",
        "sessionBrowser.result.processed.one" to "Session {patient} processed (1 clip).",
        "sessionBrowser.result.processed.few" to "Session {patient} processed ({count} clips).", // same as other; CS distinguishes
        "sessionBrowser.result.processed.other" to "Session {patient} processed ({count} clips).",
        "sessionBrowser.result.noChanges" to "No changes – the session was not reprocessed.",
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
        "editor.reset" to "Reset segment",
        "editor.discard" to "Discard changes",
        "editor.discardConfirm.title" to "Discard edits?",
        "editor.discardConfirm.message" to "Your changes to the recordings will be lost.",
        "editor.discardConfirm.confirm" to "Discard",

        // Processing progress screen (§8.8) — blocks navigation while the session is processed.
        "processing.title" to "Processing",
        "processing.step.selectingTimeline" to "Preparing…",
        "processing.step.cuttingClips" to "Cutting recordings…",
        "processing.step.convertingVideo" to "Packaging videos…",
        "processing.step.buildingArchive" to "Building archive…",
        "processing.step.updatingMetadata" to "Finishing up…",
        "processing.error.title" to "Processing failed",
        "processing.error.generic" to "This session could not be processed. You can retry or go back.",
        "processing.error.nothingToProcess" to "Nothing was recorded in this session, so there is nothing to process.",

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
        // Replaces the first instruction card on repetitions 2+ (§13 decision 58); config-overridable.
        "task.repeatInstruction" to "Please perform the same task again.",
        "task.takeLabel" to "Take {n}",
        // Legacy prev/next task-button chrome (§13 decision 36): small label line above the
        // task name; `task.endOfProtocol` replaces the next task's name on the last task.
        "task.nextLabel" to "Next task",
        "task.prevLabel" to "Previous task",
        // Skip (F-022): its own secondary button, a confirmation, and a notice on the next task.
        "task.skipLabel" to "Skip task",
        "task.skipConfirm.title" to "Skip this task?",
        "task.skipConfirm.message" to "Nothing will be recorded for it, and it will be marked as skipped.",
        "task.skipConfirm.confirm" to "Skip",
        "task.previousSkipped" to "Previous task skipped",
        "task.endOfProtocol" to "Finish",
        "task.video.starting" to "Starting camera…",
        "task.video.audioOnly" to "Camera not available — recording audio only.",
        "task.video.cameraLost" to "Camera lost — recording audio only.",
        "task.video.error.backendUnavailable" to "Camera support is not installed.",
        "task.video.error.deviceUnavailable" to "Camera unavailable — it may be in use by another program.",
        "task.video.error.startFailed" to "The camera could not be started.",
        "task.video.error.interrupted" to "The camera stopped unexpectedly.",
        "task.video.error.diskWriteFailed" to "The video could not be written to disk.",

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
        "sessionSummary.processed" to "Session processed – ready to upload.",
        "sessionSummary.processed.one" to "Session processed (1 clip) – ready to upload.",
        "sessionSummary.processed.few" to "Session processed ({count} clips) – ready to upload.", // same as other; CS distinguishes
        "sessionSummary.processed.other" to "Session processed ({count} clips) – ready to upload.",

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
        "calibration.micError" to "Mikrofon se nepodařilo otevřít. Zkontrolujte, že je připojen a že je ve Windows povolen přístup k mikrofonu (\"Povolit aplikacím přístup k mikrofonu\"), a poté stiskněte Zkusit znovu nebo zvolte jiné zařízení.",
        "calibration.micErrorRestart" to "Mikrofon se stále nepodařilo otevřít. Pokud jste právě ve Windows povolili přístup k mikrofonu, zavřete aplikaci a spusťte ji znovu, poté zahajte vyšetření znovu.",

        "settings.title" to "Nastavení",
        "settings.device.label" to "Mikrofon",
        "settings.device.refresh" to "Obnovit seznam mikrofonů",
        "settings.device.default.config" to "Výchozí podle konfigurace pracoviště",
        "settings.device.default.system" to "Výchozí v systému",
        "settings.camera.label" to "Kamera",
        "settings.camera.automatic" to "Automaticky (první dostupná kamera)",
        "settings.camera.loading" to "Hledám kamery…",
        "settings.camera.none" to "Nebyla nalezena žádná kamera",
        "settings.camera.unavailable" to "{name} (nepřipojena)",
        "settings.micGain.label" to "Úroveň mikrofonu",
        "settings.micGain.reset" to "Použít nastavenou výchozí hodnotu ({value})",
        "settings.micGain.unavailable" to "Úroveň mikrofonu nelze pro toto zařízení nastavit.",
        "settings.micGain.hint" to "",
        "settings.siteToken.label" to "Token pracoviště",
        "settings.server.title" to "Server",
        "settings.installationId.label" to "ID instalace",
        "settings.otherSite" to "Tento počítač je registrován k jinému pracovišti. Kontaktujte správce.",
        "settings.language.label" to "Jazyk",
        "settings.indicator.label" to "Indikátor nahrávání",
        "settings.indicator.CIRCLE" to "Kruh",
        "settings.indicator.WAVEFORM" to "Průběh signálu",
        "settings.indicator.reset" to "Použít nastavenou výchozí hodnotu ({value})",
        "settings.refresh.button" to "Aktualizovat konfiguraci",
        "settings.refresh.success" to "Konfigurace byla aktualizována. Vše je v pořádku.",
        "settings.refresh.otherSite" to "Nová konfigurace nebyla použita: tento počítač je registrován k jinému pracovišti. Kontaktujte správce.",
        "settings.refresh.failed" to "Konfiguraci se nepodařilo aktualizovat.",
        "settings.refresh.offline" to "Server není dostupný. Používá se uložená konfigurace.",
        "settings.backButton" to "Uložit a vrátit se",

        "mainMenu.title" to "Vyšetření řeči",
        "mainMenu.site" to "Pracoviště: {name}",
        "mainMenu.appVersion" to "Verze aplikace: {version}",
        "mainMenu.offlineNotice" to "Offline – používá se konfigurace uložená {date}",
        "mainMenu.startButton" to "Nové vyšetření",
        "mainMenu.uploadButton" to "Odeslání nahrávek",
        "mainMenu.settingsButton" to "Nastavení",
        "mainMenu.sessionBrowserButton" to "Sezení",
        "mainMenu.protocolPdfButton" to "PDF manuál protokolu",
        "mainMenu.recovery.title" to "Obnovená přerušená sezení",
        "mainMenu.recovery.message" to "Aplikace byla při předchozím sezení neočekávaně ukončena. Přerušená sezení byla nalezena a jejich nahrávky byly obnoveny.",
        "mainMenu.recovery.recoveredHeading" to "Obnoveno:",
        "mainMenu.recovery.failedHeading" to "Nepodařilo se obnovit:",
        "mainMenu.recovery.entry" to "{patient} ({date})",
        "mainMenu.recovery.openSessions" to "Otevřít sezení",

        "patientInfo.title" to "Údaje o pacientovi",
        "patientInfo.continueButton" to "Pokračovat",

        "upload.title" to "Odeslání dat",
        "upload.instructions" to "Níže uvedená sezení jsou připravena k odeslání. Odesílání může podle rychlosti připojení chvíli trvat — prosím nezavírejte aplikaci.",
        "upload.readyCount" to "Sezení připravená k odeslání: {count}",
        "upload.noSessions" to "Žádná sezení nejsou připravena k odeslání.",
        "upload.successMessage" to "Soubory byly úspěšně odeslány.",
        "upload.failureMessage" to "Odeslání selhalo: {reason}",
        "upload.error.interrupted" to "předchozí pokus byl přerušen",
        "upload.error.unreachable" to "server není dostupný. Sezení zůstává uloženo a lze je odeslat později - zkuste to znovu tlačítkem Odeslat",
        "upload.error.server" to "chyba serveru",
        "upload.error.rejected" to "server odeslání odmítl",
        "upload.error.notRegistered" to "tento počítač není přiřazen k žádnému pracovišti; kontaktujte správce",
        "upload.error.conflict" to "sezení už je na serveru s jiným obsahem; kontaktujte správce",
        "upload.error.tooLarge" to "sezení je příliš velké",
        "upload.error.busy" to "server je přetížený, zkuste to později",
        "upload.error.generic" to "nastala neznámá chyba",

        "sessionBrowser.title" to "Sezení",
        "sessionBrowser.instructions" to "Editace jednotlivých sezení. Otevřít editor: umožní vystřihnout nežádoucí části ze zvukové nahrávky sezení. Zpracovat / Zpracovat znovu: vytvoří klipy a archiv z nahrávky — nabízí se u sezení, která ještě nejsou zpracovaná nebo jejichž zpracování selhalo. Uložení změn v editoru sezení zpracuje samo.",
        "sessionBrowser.noSessions" to "Zatím nebyla nahrána žádná sezení.",
        "sessionBrowser.processButton" to "Zpracovat",
        "sessionBrowser.retryProcessingButton" to "Zpracovat znovu",
        "sessionBrowser.result.processed" to "Sezení {patient} zpracováno.",
        "sessionBrowser.result.processed.one" to "Sezení {patient} zpracováno (1 nahrávka).",
        "sessionBrowser.result.processed.few" to "Sezení {patient} zpracováno ({count} nahrávky).",
        "sessionBrowser.result.processed.other" to "Sezení {patient} zpracováno ({count} nahrávek).",
        "sessionBrowser.result.noChanges" to "Beze změn – sezení nebylo znovu zpracováno.",
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
        "editor.reset" to "Obnovit úsek",
        "editor.discard" to "Zahodit změny",
        "editor.discardConfirm.title" to "Zahodit úpravy?",
        "editor.discardConfirm.message" to "Vaše změny nahrávek budou ztraceny.",
        "editor.discardConfirm.confirm" to "Zahodit",

        "processing.title" to "Zpracování",
        "processing.step.selectingTimeline" to "Příprava…",
        "processing.step.cuttingClips" to "Stříhání nahrávek…",
        "processing.step.convertingVideo" to "Ukládání videí…",
        "processing.step.buildingArchive" to "Vytváření archivu…",
        "processing.step.updatingMetadata" to "Dokončování…",
        "processing.error.title" to "Zpracování selhalo",
        "processing.error.generic" to "Toto sezení se nepodařilo zpracovat. Můžete to zkusit znovu, nebo se vrátit zpět.",
        "processing.error.nothingToProcess" to "V tomto sezení nebylo nic nahráno, takže není co zpracovat.",

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
        "task.repeatInstruction" to "Prosím, zopakujte úlohu ještě jednou.",
        "task.takeLabel" to "Pokus {n}",
        "task.nextLabel" to "Další úloha",
        "task.prevLabel" to "Předchozí úloha",
        "task.skipLabel" to "Přeskočit úlohu",
        "task.skipConfirm.title" to "Přeskočit tuto úlohu?",
        "task.skipConfirm.message" to "Nic se pro ni nenahraje a bude označena jako přeskočená.",
        "task.skipConfirm.confirm" to "Přeskočit",
        "task.previousSkipped" to "Předchozí úloha přeskočena",
        "task.endOfProtocol" to "Konec",
        "task.video.starting" to "Spouštím kameru…",
        "task.video.audioOnly" to "Kamera není k dispozici — nahrává se pouze zvuk.",
        "task.video.cameraLost" to "Kamera byla ztracena — nahrává se pouze zvuk.",
        "task.video.error.backendUnavailable" to "Podpora kamery není nainstalována.",
        "task.video.error.deviceUnavailable" to "Kamera není k dispozici — možná ji používá jiný program.",
        "task.video.error.startFailed" to "Kameru se nepodařilo spustit.",
        "task.video.error.interrupted" to "Kamera se nečekaně zastavila.",
        "task.video.error.diskWriteFailed" to "Video se nepodařilo zapsat na disk.",

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
        "sessionSummary.processed" to "Sezení zpracováno – připraveno k odeslání.",
        "sessionSummary.processed.one" to "Sezení zpracováno (1 nahrávka) – připraveno k odeslání.",
        "sessionSummary.processed.few" to "Sezení zpracováno ({count} nahrávky) – připraveno k odeslání.",
        "sessionSummary.processed.other" to "Sezení zpracováno ({count} nahrávek) – připraveno k odeslání.",

        "blocking.openSettingsButton" to "Otevřít nastavení",

        "placeholder.title" to "Již brzy",
        "placeholder.message" to "Tato obrazovka zatím není k dispozici.",
    )

    /** Built-in strings by language code; languages missing here fall back to [en]. */
    val byLanguage: Map<String, Map<String, String>> = mapOf("en" to en, "cs" to cs)
}
