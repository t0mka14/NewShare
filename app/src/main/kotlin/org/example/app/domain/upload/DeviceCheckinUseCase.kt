package org.example.app.domain.upload

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.settings.AppSettingsRepository
import org.example.app.domain.settings.InstallationIdProvider

private val logger = KotlinLogging.logger {}

/**
 * §13 decision 62: reports this computer's upload backlog to the web backend after every
 * successful config refresh ([org.example.app.domain.config.RefreshConfigurationUseCase]) and
 * after every upload batch (upload screen). It is best effort: [checkIn] never throws and its
 * result never changes what the caller does. Skipped while no site token is set, since the
 * server cannot attribute the report without one.
 */
class DeviceCheckinUseCase(
    private val settingsRepository: AppSettingsRepository,
    private val installationIdProvider: InstallationIdProvider,
    private val eligibleUploadsQuery: EligibleUploadsQuery,
    private val api: DeviceCheckinApi,
    /** `BuildInfo.appVersion` as text. */
    private val appVersion: String,
    private val dispatchers: CoroutineDispatchers,
) {
    private val _registeredToAnotherSite = MutableStateFlow(false)

    /** The last answered check-in said this computer belongs to another site than the entered
     * token's (HTTP 409); Settings shows a note. Cleared by the next recorded check-in. */
    val registeredToAnotherSite: StateFlow<Boolean> = _registeredToAnotherSite

    /** The answer, or `null` when nothing was sent (no token) or local metadata failed. */
    suspend fun checkIn(): DeviceCheckinResult? {
        try {
            val result = withContext(dispatchers.io) {
                val siteToken = settingsRepository.read()?.siteToken?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@withContext null
                val sessions = eligibleUploadsQuery.eligibleSessions()
                api.checkIn(
                    DeviceCheckin(
                        siteToken = siteToken,
                        installationId = installationIdProvider.get(),
                        appVersion = appVersion,
                        pending = sessions.count { it.status == UploadStatusValue.NotUploaded }.coerceAtMost(MAX_COUNT),
                        failed = sessions.count { it.status == UploadStatusValue.Failed }.coerceAtMost(MAX_COUNT),
                    ),
                )
            }
            // Never the site token or the installation ID (§11); the API logs the HTTP status.
            when (result) {
                DeviceCheckinResult.Recorded -> {
                    logger.info { "device check-in: ok" }
                    _registeredToAnotherSite.value = false
                }
                DeviceCheckinResult.RegisteredToAnotherSite -> {
                    logger.warn { "device check-in: registered to another site" }
                    _registeredToAnotherSite.value = true
                }
                DeviceCheckinResult.InvalidToken -> logger.warn { "device check-in: site token rejected" }
                DeviceCheckinResult.Failed -> logger.warn { "device check-in: failed" }
                null -> logger.info { "device check-in: skipped, no site token" }
            }
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Reading local metadata failed; skipping the report is all it is worth.
            logger.warn { "device check-in: skipped, ${e::class.simpleName}" }
            return null
        }
    }

    private companion object {
        /** The server rejects counts above this with 400. */
        const val MAX_COUNT = 100_000
    }
}
