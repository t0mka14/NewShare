package org.example.app.domain.logs

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.settings.AppSettingsRepository
import org.example.app.domain.settings.InstallationIdProvider
import org.example.app.domain.upload.FileHashService
import org.example.app.infrastructure.logging.LogPolicy
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger {}

/**
 * Sends the rotated log files (`app.<yyyy-MM-dd>.<n>.log.gz`) to the server: web repo
 * `docs/device_logs/01_spec.md`, app summary in `03_desktop_brief.md`.
 *
 * One run at a time (a [Mutex]). A run does nothing without a site token; otherwise it lists the
 * rotated files (never `app.log`, which Logback holds open), drops record entries whose file is
 * gone, and sends the files it has no record for, oldest first by the date and index in the
 * file name (not by modification time). A file whose hash differs from its recorded hash is a
 * new file. Before each file it stops if a session is running ([sessionActive]). The record is
 * written atomically after every result.
 *
 * A file modified in the last 60 s is skipped, not hashed or recorded ("not settled"): Logback may
 * still be compressing it. A [DeviceLogResult.FileFailed] file is skipped and retried next run. A
 * [DeviceLogResult.RetryLater] answer ends the run: the causes (no clinic yet, server down,
 * rate limit) hit every following file the same way, and each would wait for a timeout.
 *
 * It logs one summary line with counts only; the token, installation ID, file names and paths
 * never appear in a log line (§11).
 */
class SendDeviceLogsUseCase(
    private val logDirectory: Path,
    private val api: DeviceLogApi,
    private val recordStore: DeviceLogRecordStore,
    private val settingsRepository: AppSettingsRepository,
    private val installationIdProvider: InstallationIdProvider,
    private val fileHashService: FileHashService,
    private val sessionActive: StateFlow<Boolean>,
    /** `BuildInfo.appVersion` as text. */
    private val appVersion: String,
    private val dispatchers: CoroutineDispatchers,
    private val utcOffset: () -> ZoneOffset = { ZonedDateTime.now().offset },
    private val now: () -> Instant = Instant::now,
) {
    private val mutex = Mutex()

    private class Counts {
        var sent = 0
        var unsendable = 0
        var retryLater = 0
        var alreadyDone = 0
        var unreadable = 0
        var fileFailed = 0
        var notSettled = 0
        var stoppedForSession = false
    }

    /** Never throws (except for cancellation). */
    suspend fun run() {
        mutex.withLock {
            try {
                withContext(dispatchers.io) { runLocked() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn { "device logs: aborted, ${LogPolicy.safeDescribe(e)}" }
            }
        }
    }

    private suspend fun runLocked() {
        val siteToken = settingsRepository.read()?.siteToken?.trim()?.takeIf { it.isNotEmpty() }
        if (siteToken == null) {
            logger.info { "device logs: skipped, no site token" }
            return
        }

        val files = rotatedFiles()
        val names = files.mapTo(HashSet()) { it.name }
        var record = recordStore.read()
        val pruned = record.filterKeys { it in names }
        if (pruned.size != record.size) {
            record = pruned
            recordStore.write(record)
        }

        val counts = Counts()
        var installationId: String? = null
        for (file in files) {
            if (sessionActive.value) {
                counts.stoppedForSession = true
                break
            }
            // Logback's compressor writes straight into the final name; a young file may be partial.
            if (Duration.between(file.modified, now()) < SETTLE_TIME) {
                counts.notSettled++
                continue
            }
            val sha256 = try {
                fileHashService.sha256(file.path)
            } catch (_: IOException) {
                counts.unreadable++
                continue
            }
            if (record[file.name]?.sha256 == sha256) {
                counts.alreadyDone++
                continue
            }
            val id = installationId ?: installationIdProvider.get().also { installationId = it }
            val state = when (api.send(file.path, file.name, sha256, siteToken, id, appVersion, utcOffset())) {
                DeviceLogResult.Sent -> DeviceLogRecord.State.Sent.also { counts.sent++ }
                DeviceLogResult.Unsendable -> DeviceLogRecord.State.Unsendable.also { counts.unsendable++ }
                DeviceLogResult.RetryLater -> {
                    counts.retryLater++
                    break
                }
                DeviceLogResult.FileFailed -> {
                    counts.fileFailed++
                    continue
                }
            }
            record = record + (file.name to DeviceLogRecord(sha256, state))
            recordStore.write(record)
        }

        logger.info {
            "device logs: ${counts.sent} sent, ${counts.unsendable} unsendable, ${counts.retryLater} retry later, " +
                "${counts.fileFailed} file failed, ${counts.alreadyDone} already done, ${counts.unreadable} unreadable, " +
                "${counts.notSettled} not settled" +
                if (counts.stoppedForSession) ", stopped: session running" else ""
        }
    }

    private class RotatedLog(val name: String, val path: Path, val date: String, val index: Int, val modified: Instant)

    /** The rotated files, oldest first: by the date, then the index, in the file name. */
    private fun rotatedFiles(): List<RotatedLog> {
        if (!Files.isDirectory(logDirectory)) return emptyList()
        val found = Files.list(logDirectory).use { stream ->
            stream.toList().mapNotNull { path ->
                val name = path.fileName.toString()
                val match = FILE_NAME.matchEntire(name) ?: return@mapNotNull null
                if (!Files.isRegularFile(path)) return@mapNotNull null
                RotatedLog(
                    name, path, match.groupValues[1], match.groupValues[2].toInt(),
                    Files.getLastModifiedTime(path).toInstant(),
                )
            }
        }
        return found.sortedWith(compareBy<RotatedLog> { it.date }.thenBy { it.index })
    }

    private companion object {
        /** A rotated file modified more recently than this may still be being compressed. */
        val SETTLE_TIME: Duration = Duration.ofSeconds(60)
        val FILE_NAME = Regex("""app\.(\d{4}-\d{2}-\d{2})\.(\d{1,3})\.log\.gz""")
    }
}

/** Runs [useCase] on [scope] without waiting; the scope is the app's, so it outlives any screen. */
class LaunchingDeviceLogTrigger(
    private val scope: CoroutineScope,
    private val useCase: SendDeviceLogsUseCase,
) : DeviceLogTrigger {
    override fun trigger() {
        scope.launch { useCase.run() }
    }
}
