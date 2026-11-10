package org.example.app.domain.logs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.example.app.domain.settings.AppSettings
import org.example.app.domain.settings.InstallationIdProvider
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeDeviceLogApi
import org.example.app.fakes.ImmediateCoroutineDispatchers
import org.example.app.fakes.TestAppDirectories
import org.example.app.infrastructure.logging.captureLogs
import org.example.app.infrastructure.logging.fullText
import org.example.app.infrastructure.persistence.JsonDeviceLogRecordStore
import org.example.app.infrastructure.persistence.Sha256FileHashService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant

/** AC 8 (the use case) and AC 10 (log privacy) of `docs/SPEC-device-logs.md`. */
class SendDeviceLogsUseCaseTest {

    private val siteToken = "SECRETsiteTOKEN000000"
    private val installationId = "SECRET-INSTALLATION-ID-42"

    private class Harness(root: Path, siteToken: String?, installationId: String) {
        val logDir: Path = root.resolve("logs").also { Files.createDirectories(it) }
        val directories = TestAppDirectories(root.resolve("data"))
        val api = FakeDeviceLogApi()
        val sessionActive = MutableStateFlow(false)

        /** The clock the use case sees; far after any file written by a test, so files are settled. */
        var now: Instant = Instant.now().plusSeconds(3600)
        val settings = FakeAppSettingsRepository().also {
            it.write(AppSettings(siteToken = siteToken, installationId = installationId))
        }

        /** A fresh instance over the same files, as after an app restart. */
        fun newUseCase() = SendDeviceLogsUseCase(
            logDirectory = logDir,
            api = api,
            recordStore = JsonDeviceLogRecordStore(directories),
            settingsRepository = settings,
            installationIdProvider = InstallationIdProvider(settings),
            fileHashService = Sha256FileHashService(),
            sessionActive = sessionActive,
            appVersion = "1.4.0",
            dispatchers = ImmediateCoroutineDispatchers(),
            now = { now },
        )

        val useCase = newUseCase()

        fun log(name: String, content: String = name): Path =
            logDir.resolve(name).also { Files.writeString(it, content) }

        fun run(useCase: SendDeviceLogsUseCase = this.useCase) = runBlocking { useCase.run() }

        fun recordFile(): Path = directories.configDir.resolve("device_logs_sent.json")
    }

    private fun harness(dir: Path, token: String? = siteToken) = Harness(dir, token, installationId)

    @Test
    fun `app log is never sent`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.log")
        h.log("app.2026-11-09.0.log.gz")
        h.log("updater.log")
        h.log("app.2026-11-09.0.log")

        h.run()

        assertEquals(listOf("app.2026-11-09.0.log.gz"), h.api.sentFileNames)
    }

    @Test
    fun `sent files are not sent again`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-09.0.log.gz")

        h.run()
        h.run()

        assertEquals(1, h.api.calls.size)
    }

    @Test
    fun `unsendable files are not retried`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-09.0.log.gz")
        h.api.nextResult = DeviceLogResult.Unsendable

        h.run()
        h.api.nextResult = DeviceLogResult.Sent
        h.run()

        assertEquals(1, h.api.calls.size)
    }

    @Test
    fun `a RetryLater file is retried on the next run`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-09.0.log.gz")
        h.api.nextResult = DeviceLogResult.RetryLater

        h.run()
        h.api.nextResult = DeviceLogResult.Sent
        h.run()
        h.run()

        assertEquals(2, h.api.calls.size)
        assertEquals(DeviceLogRecord.State.Sent, h.newUseCaseRecord()["app.2026-11-09.0.log.gz"]?.state)
    }

    @Test
    fun `a RetryLater answer is not recorded and ends the run before the next file`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-08.0.log.gz")
        h.log("app.2026-11-09.0.log.gz")
        h.api.nextResult = DeviceLogResult.RetryLater

        h.run()

        assertEquals(listOf("app.2026-11-08.0.log.gz"), h.api.sentFileNames)
        assertTrue(h.newUseCaseRecord().isEmpty())
    }

    @Test
    fun `a file modified 10 seconds ago is not sent, and is sent once older than 60 seconds`(@TempDir dir: Path) {
        val h = harness(dir)
        val file = h.log("app.2026-11-09.0.log.gz")
        val written = Instant.now()
        Files.setLastModifiedTime(file, FileTime.from(written))
        h.now = written.plusSeconds(10)

        val logs = captureLogs { h.run() }

        assertEquals(0, h.api.calls.size)
        assertTrue(h.newUseCaseRecord().isEmpty())
        assertTrue(logs.single { it.formattedMessage.startsWith("device logs:") }.formattedMessage.contains("1 not settled"))

        h.now = written.plusSeconds(61)
        h.run()

        assertEquals(1, h.api.calls.size)
    }

    @Test
    fun `a FileFailed file does not stop later files and is retried next run`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-08.0.log.gz")
        h.log("app.2026-11-09.0.log.gz")
        h.api.respondTo("app.2026-11-08.0.log.gz", DeviceLogResult.FileFailed)

        h.run()

        assertEquals(listOf("app.2026-11-08.0.log.gz", "app.2026-11-09.0.log.gz"), h.api.sentFileNames)
        assertEquals(setOf("app.2026-11-09.0.log.gz"), h.newUseCaseRecord().keys)

        h.api.respondTo("app.2026-11-08.0.log.gz", DeviceLogResult.Sent)
        h.run()

        assertEquals(3, h.api.calls.size)
        assertEquals(2, h.newUseCaseRecord().size)
    }

    @Test
    fun `a changed hash counts as a new file`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-09.0.log.gz", "first")
        h.run()

        h.log("app.2026-11-09.0.log.gz", "second, different bytes")
        h.run()

        assertEquals(2, h.api.calls.size)
        assertFalse(h.api.calls[0].sha256 == h.api.calls[1].sha256)
    }

    @Test
    fun `with no token the api is never called`(@TempDir dir: Path) {
        val noToken = harness(dir.resolve("a"), token = null)
        noToken.log("app.2026-11-09.0.log.gz")
        noToken.run()
        val blank = harness(dir.resolve("b"), token = "   ")
        blank.log("app.2026-11-09.0.log.gz")
        blank.run()

        assertEquals(0, noToken.api.calls.size)
        assertEquals(0, blank.api.calls.size)
    }

    @Test
    fun `an active session stops the run before the next file`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-08.0.log.gz")
        h.log("app.2026-11-09.0.log.gz")
        h.log("app.2026-11-10.0.log.gz")
        h.api.onSend = { h.sessionActive.value = true }

        h.run()

        assertEquals(listOf("app.2026-11-08.0.log.gz"), h.api.sentFileNames)

        h.api.onSend = {}
        h.sessionActive.value = false
        h.run()
        assertEquals(
            listOf("app.2026-11-08.0.log.gz", "app.2026-11-09.0.log.gz", "app.2026-11-10.0.log.gz"),
            h.api.sentFileNames,
        )
    }

    @Test
    fun `a run that starts during a session sends nothing`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-09.0.log.gz")
        h.sessionActive.value = true

        h.run()

        assertEquals(0, h.api.calls.size)
    }

    @Test
    fun `the record survives a restart`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-08.0.log.gz")
        h.log("app.2026-11-09.0.log.gz")
        h.api.respondTo("app.2026-11-09.0.log.gz", DeviceLogResult.Unsendable)
        h.run()
        assertEquals(2, h.api.calls.size)

        h.run(h.newUseCase())

        assertEquals(2, h.api.calls.size)
        assertTrue(Files.exists(h.recordFile()))
        assertFalse(Files.exists(h.recordFile().resolveSibling("device_logs_sent.json.tmp")))
    }

    @Test
    fun `files go oldest first by the date and index in the name, not by mtime`(@TempDir dir: Path) {
        val h = harness(dir)
        // Written in the reverse of the expected order, with mtimes that also contradict it.
        val expected = listOf(
            "app.2026-11-08.0.log.gz",
            "app.2026-11-09.0.log.gz",
            "app.2026-11-09.1.log.gz",
            "app.2026-11-09.2.log.gz",
            "app.2026-11-09.10.log.gz",
            "app.2026-11-10.0.log.gz",
        )
        expected.reversed().forEachIndexed { i, name ->
            val file = h.log(name)
            Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L + i * 1000L))
        }

        h.run()

        assertEquals(expected, h.api.sentFileNames)
    }

    @Test
    fun `record entries for files that are gone are dropped`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-08.0.log.gz")
        h.log("app.2026-11-09.0.log.gz")
        h.run()
        Files.delete(h.logDir.resolve("app.2026-11-08.0.log.gz"))

        h.run()

        assertEquals(setOf("app.2026-11-09.0.log.gz"), h.newUseCaseRecord().keys)
    }

    @Test
    fun `the request carries the token, installation id, version and a utc offset`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-09.0.log.gz")

        h.run()

        val call = h.api.calls.single()
        assertEquals(siteToken, call.siteToken)
        assertEquals(installationId, call.installationId)
        assertEquals("1.4.0", call.appVersion)
        assertEquals(h.logDir.resolve("app.2026-11-09.0.log.gz"), call.file)
    }

    @Test
    fun `a malformed record file is treated as empty`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-09.0.log.gz")
        Files.writeString(h.recordFile(), "{not json")

        h.run()

        assertEquals(1, h.api.calls.size)
    }

    @Test
    fun `a missing log directory is not an error`(@TempDir dir: Path) {
        val h = harness(dir)
        Files.delete(h.logDir)

        h.run()

        assertEquals(0, h.api.calls.size)
    }

    @Test
    fun `one summary line with counts only, no token, installation id, file name or path`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-08.0.log.gz")
        h.log("app.2026-11-09.0.log.gz")
        h.log("app.2026-11-10.0.log.gz")
        h.api.respondTo("app.2026-11-09.0.log.gz", DeviceLogResult.Unsendable)
        h.api.respondTo("app.2026-11-10.0.log.gz", DeviceLogResult.RetryLater)

        val logs = captureLogs { h.run() }

        val lines = logs.filter { it.formattedMessage.startsWith("device logs:") }
        assertEquals(1, lines.size)
        assertEquals(
            "device logs: 1 sent, 1 unsendable, 1 retry later, 0 file failed, 0 already done, 0 unreadable, 0 not settled",
            lines.single().formattedMessage,
        )
    }

    @Test
    fun `log privacy - no line from the sender contains the token, the installation id or a path`(@TempDir dir: Path) {
        val h = harness(dir)
        h.log("app.2026-11-08.0.log.gz")
        h.log("app.2026-11-09.0.log.gz")
        h.log("app.2026-11-10.0.log.gz")
        h.api.respondTo("app.2026-11-09.0.log.gz", DeviceLogResult.Unsendable)
        h.api.respondTo("app.2026-11-10.0.log.gz", DeviceLogResult.RetryLater)
        val noToken = harness(dir.resolve("other"), token = null)
        noToken.log("app.2026-11-09.0.log.gz")

        val logs = captureLogs {
            h.run()
            h.sessionActive.value = true
            h.run()
            noToken.run()
        }

        val ours = logs.filter { it.loggerName.contains("SendDeviceLogsUseCase") || it.formattedMessage.startsWith("device logs") }
        assertTrue(ours.isNotEmpty())
        for (event in logs) {
            val text = event.fullText
            assertFalse(text.contains(siteToken), text)
            assertFalse(text.contains(installationId), text)
            assertFalse(text.contains(dir.toString()), text)
            assertFalse(text.contains(h.logDir.toString()), text)
        }
        for (event in ours) {
            val text = event.fullText
            assertFalse(text.contains(".log.gz"), text)
            assertFalse(text.contains("app.2026"), text)
            assertFalse(text.contains("/") || text.contains("\\"), text)
        }
    }

    private fun Harness.newUseCaseRecord(): Map<String, DeviceLogRecord> = JsonDeviceLogRecordStore(directories).read()
}
