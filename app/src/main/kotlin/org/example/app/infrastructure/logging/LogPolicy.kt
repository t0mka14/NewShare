package org.example.app.infrastructure.logging

/**
 * Anchors the §11 logging rule in code.
 *
 * **§11 (normative):** log lines must never contain participant data (patient code,
 * field values, questionnaire answers) or the installation ID — the installation ID
 * acts as a bearer credential (§6.1). Sessions are referenced by `sessionId` only.
 *
 * Sinks are configured in `app/src/main/resources/logback.xml` (console + a rolling
 * file at `data/logs/app.log`, modest size cap). This object does not change that
 * wiring; it exists to (a) document the rule at the call sites most likely to violate
 * it, and (b) host small helpers that make the safe thing the easy thing.
 */
object LogPolicy {

    /**
     * Transport/IO exceptions from [org.example.app.infrastructure.network.KtorConfigApi]
     * and future `UploadApi` implementations can embed the request URL in
     * `Throwable.message` (e.g. `java.net.ConnectException: Connection refused: /api/config/<id>`).
     * For `ConfigApi` that URL contains the installation ID, so callers must never log
     * `Throwable.message` for those failures.
     *
     * Use this instead: it reduces an exception to its class name, which is safe to log
     * and still useful for diagnosing the failure category (timeout vs. connect-refused
     * vs. unknown host, etc.).
     */
    fun safeDescribe(t: Throwable): String = t::class.qualifiedName ?: t::class.simpleName ?: "UnknownError"
}
