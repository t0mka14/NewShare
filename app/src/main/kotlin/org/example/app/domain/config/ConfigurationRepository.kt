package org.example.app.domain.config

import kotlinx.coroutines.flow.StateFlow

/**
 * Cached remote configuration (§5.4, §6.1). Owns the single in-memory "active configuration"
 * the rest of the app reads from — a running/recorded session never observes this once
 * started (it holds its own `task_configuration_snapshot.json`, §6.1 pt 5).
 *
 * Refresh-takes-effect-next-session (§6.1 pt 5): this repository itself has no notion of
 * sessions — it simply always reflects the newest successfully validated config, cached or
 * fetched. It is the *caller's* responsibility (a session-start use case) to read
 * [activeConfig] only at session-start time and snapshot it, so a later [applyFetched] during
 * a running session cannot affect it.
 */
interface ConfigurationRepository {
    /** `null` until a valid config has been loaded from cache or fetched (§6.1 pt 4). */
    val activeConfig: StateFlow<RemoteConfig?>

    /**
     * Startup path (§6.1): read the cache, migrate it if its schemaVersion is below
     * [ConfigValidator.SUPPORTED_SCHEMA_VERSIONS] (§6.1 pt 6), decode, validate, and — if all
     * of that succeeds — activate it (updates [activeConfig]) and return it.
     *
     * An absent cache, a cache that fails to parse/migrate, or a cache that fails validation
     * all behave identically: `null` is returned and [activeConfig] is left as it was (i.e.
     * still `null` on a fresh startup call, §6.1 pt 6 "behaves as no cache").
     */
    fun loadCached(): RemoteConfig?

    /**
     * Fetch path (§6.1 pt 3): validate [json] first; only on success is it persisted
     * atomically (replacing the cache regardless of `configVersion` ordering — the server is
     * always authoritative, §6.1 pt 6) and activated. A rejected or malformed fetch never
     * touches the cache or [activeConfig].
     */
    fun applyFetched(json: String): ConfigApplyResult
}

/** Outcome of [ConfigurationRepository.applyFetched]. */
sealed interface ConfigApplyResult {
    data class Applied(val config: RemoteConfig) : ConfigApplyResult

    /** [json] does not parse or decode against the config schema at all (§11: config validation failure). */
    data class Malformed(val detail: String) : ConfigApplyResult

    /** Parsed but failed [ConfigValidator] (includes unsupported schema version, §6.1 pt 6). */
    data class Rejected(val errors: List<ConfigValidationError>) : ConfigApplyResult
}
