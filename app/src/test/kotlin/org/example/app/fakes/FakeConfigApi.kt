package org.example.app.fakes

import org.example.app.domain.config.ConfigApi
import org.example.app.domain.config.ConfigFetchResult

/**
 * Programmable result queue standing in for the real Ktor [ConfigApi] (§10.3):
 * enqueue success JSON, [ConfigFetchResult.InvalidInstallationId], or
 * [ConfigFetchResult.NetworkUnavailable] to drive offline-config and
 * validation-error scenarios without any real network call.
 *
 * If the queue is empty, [fetchConfig] returns [ConfigFetchResult.NetworkUnavailable]
 * by default (the safest default for offline-fallback tests) rather than throwing,
 * so a test that forgets to enqueue a result fails on an assertion, not a crash.
 */
class FakeConfigApi : ConfigApi {
    private val queue = ArrayDeque<ConfigFetchResult>()

    /** Every installation ID passed to [fetchConfig], in call order — for asserting it's never logged/reused unexpectedly. */
    val requestedInstallationIds = mutableListOf<String>()

    fun enqueue(result: ConfigFetchResult) {
        queue.addLast(result)
    }

    fun enqueueSuccess(json: String) = enqueue(ConfigFetchResult.Success(json))

    fun enqueueInvalidInstallationId() = enqueue(ConfigFetchResult.InvalidInstallationId)

    fun enqueueNetworkUnavailable(detail: String = "FakeConfigApi: simulated network failure") =
        enqueue(ConfigFetchResult.NetworkUnavailable(detail))

    fun enqueueServerError(httpStatus: Int) = enqueue(ConfigFetchResult.ServerError(httpStatus))

    override suspend fun fetchConfig(installationId: String): ConfigFetchResult {
        requestedInstallationIds += installationId
        return if (queue.isNotEmpty()) {
            queue.removeFirst()
        } else {
            ConfigFetchResult.NetworkUnavailable("FakeConfigApi: queue empty, defaulting to offline fallback")
        }
    }
}
