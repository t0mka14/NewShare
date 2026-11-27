package org.example.app.domain.audio

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.app.domain.CoroutineDispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Session-scoped mic hot-plug watcher (§5.3, §8.5). Polls the cheap
 * [AudioInputDeviceProvider.deviceFingerprint] every [pollInterval] on `io` and only when it
 * changes performs the full [AudioInputDeviceProvider.availableDevices] scan, merging the result
 * into [devices] via [mergeDevices] so the UI list does not reshuffle.
 *
 * [initialFingerprint] is the baseline for the first poll: pass the fingerprint taken just BEFORE
 * [initialDevices] was enumerated, so any later change triggers a scan while an unchanged setup
 * skips the (format-probing) full scan. With null the first poll always scans.
 * Nothing thrown by the provider escapes the loop.
 */
class AudioDeviceWatcher(
    private val provider: AudioInputDeviceProvider,
    initialDevices: List<AudioInputDevice>,
    private val dispatchers: CoroutineDispatchers,
    private val pollInterval: Duration = 2.seconds,
    initialFingerprint: Any? = null,
) {
    private val _devices = MutableStateFlow(initialDevices)
    val devices: StateFlow<List<AudioInputDevice>> = _devices

    private val mutex = Mutex()
    private var lastFingerprint: Any? = initialFingerprint

    /** Launches the polling loop in [scope]; it ends when [scope] is cancelled or the returned job is. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            delay(pollInterval)
            refresh(force = false)
        }
    }

    /** On-demand refresh (e.g. opening a device picker): always does a full scan. */
    suspend fun refreshNow() = refresh(force = true)

    private suspend fun refresh(force: Boolean) {
        try {
            withContext(dispatchers.io) {
                mutex.withLock {
                    val fingerprint = provider.deviceFingerprint()
                    if (force || fingerprint != lastFingerprint) {
                        val fresh = provider.availableDevices()
                        lastFingerprint = fingerprint
                        _devices.value = mergeDevices(_devices.value, fresh)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Mic hot-plug refresh failed; will retry" }
        }
    }
}

/**
 * Mic hot-plug merge rule: [previous] devices keep their relative order and are replaced by
 * the fresh object with the same id (eligibility may change); devices only in [fresh] are
 * appended in provider order; devices missing from [fresh] are dropped.
 */
fun mergeDevices(previous: List<AudioInputDevice>, fresh: List<AudioInputDevice>): List<AudioInputDevice> {
    val freshById = fresh.associateBy { it.id }
    val kept = previous.mapNotNull { freshById[it.id] }
    val keptIds = kept.mapTo(HashSet()) { it.id }
    return kept + fresh.filter { it.id !in keptIds }.distinctBy { it.id }
}
