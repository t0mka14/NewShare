package org.example.app.fakes

/**
 * Raw config JSON fixtures under `src/test/resources/fixtures/config/` (§10.1/§10.3).
 * These are plain classpath text, not typed models — the domain-engineer's config
 * models are built concurrently, so fixtures here don't depend on them.
 *
 * - [fullProtocol]: two protocols exercising the spec end to end — "Share" (
 *   CALIBRATION + VOCAL with `nrepetition: 2` + QUESTIONNAIRE + INFO) and
 *   "QuestionnaireOnly"; 2 languages (`cs`/`en`); `patientFields` with a regex
 *   field and a `useInFilename` mix; a `recordingsFileName` template containing
 *   `${taskIndex}`; strings exercising `<bold>` markup (`bold_notice` — the only
 *   supported markup, §6.2/§13 decision 26). `enableEditor: true`.
 * - [editorDisabled]: identical to [fullProtocol] except `enableEditor: false`,
 *   for scenarios that must not show the waveform editor.
 * - [questionnaireOnly]: single-protocol config containing *only* the
 *   questionnaire-only protocol — no CALIBRATION/VOCAL task anywhere — for §10.3
 *   workflow 7 (no calibration screen, no master WAV, JSON-only archive).
 */
object ConfigFixtures {
    val fullProtocol: String by lazy { readFixture("sample_config.json") }
    val editorDisabled: String by lazy { readFixture("sample_config_editor_disabled.json") }
    val questionnaireOnly: String by lazy { readFixture("questionnaire_only_config.json") }

    private fun readFixture(name: String): String {
        val path = "fixtures/config/$name"
        val stream = ConfigFixtures::class.java.classLoader.getResourceAsStream(path)
            ?: error("Missing test fixture on classpath: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
