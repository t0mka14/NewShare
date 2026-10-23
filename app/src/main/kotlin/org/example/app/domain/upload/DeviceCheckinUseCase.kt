package org.example.app.domain.upload

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.settings.AppSettingsRepository
import org.example.app.domain.settings.InstallationIdProvider

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

    suspend fun checkIn() {
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
            when (result) {
                DeviceCheckinResult.Recorded -> _registeredToAnotherSite.value = false
                DeviceCheckinResult.RegisteredToAnotherSite -> _registeredToAnotherSite.value = true
                // A rejected token is already reported by the config refresh itself.
                DeviceCheckinResult.InvalidToken, DeviceCheckinResult.Failed, null -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Reading local metadata failed; the API logs its own failures and this one is not
            // worth more than skipping the report.
        }
    }

    private companion object {
        /** The server rejects counts above this with 400. */
        const val MAX_COUNT = 100_000
    }
}
