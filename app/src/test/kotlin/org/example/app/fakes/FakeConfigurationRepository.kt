package org.example.app.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.app.domain.config.ConfigApplyResult
import org.example.app.domain.config.ConfigurationRepository
import org.example.app.domain.config.RemoteConfig

/**
 * In-memory [ConfigurationRepository] test double: no filesystem, no decode/validate — the
 * caller programs exactly what [applyFetched] should return via [nextApplyResult], and can
 * seed/observe [activeConfig] directly. Used to unit-test callers (e.g.
 * `RefreshConfigurationUseCase`) in isolation from the real cache/decode/validate pipeline,
 * which is covered separately by `JsonConfigurationRepositoryTest`.
 */
class FakeConfigurationRepository(initialConfig: RemoteConfig? = null) : ConfigurationRepository {

    private val _activeConfig = MutableStateFlow(initialConfig)
    override val activeConfig: StateFlow<RemoteConfig?> = _activeConfig

    /** Queue of results [applyFetched] returns, in order; defaults to `Applied` with a decoded
     * config when empty is never used — callers must enqueue explicitly. */
    private val queue = ArrayDeque<ConfigApplyResult>()

    /** Every raw JSON string passed to [applyFetched], in call order. */
    val appliedJson = mutableListOf<String>()

    fun enqueueApplyResult(result: ConfigApplyResult) {
        queue.addLast(result)
    }

    fun setActiveConfig(config: RemoteConfig?) {
        _activeConfig.value = config
    }

    override fun loadCached(): RemoteConfig? = _activeConfig.value

    override fun applyFetched(json: String): ConfigApplyResult {
        appliedJson += json
        val result = queue.removeFirstOrNull()
            ?: error("FakeConfigurationRepository: no ConfigApplyResult enqueued for applyFetched")
        if (result is ConfigApplyResult.Applied) {
            _activeConfig.value = result.config
        }
        return result
    }
}
