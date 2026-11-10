package org.example.app.infrastructure.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.example.app.domain.AppDirectories
import org.example.app.domain.logs.DeviceLogRecord
import org.example.app.domain.logs.DeviceLogRecordStore
import org.example.app.infrastructure.logging.LogPolicy

private val logger = KotlinLogging.logger {}

/** [DeviceLogRecordStore] at `config/device_logs_sent.json`, written with [AtomicFileWriter]. */
class JsonDeviceLogRecordStore(private val directories: AppDirectories) : DeviceLogRecordStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), DeviceLogRecord.serializer())
    private val path get() = directories.configDir.resolve("device_logs_sent.json")

    override fun read(): Map<String, DeviceLogRecord> {
        val text = try {
            AtomicFileWriter.readStringOrNull(path)
        } catch (e: java.io.IOException) {
            logger.warn { "device logs: record unreadable, ${LogPolicy.safeDescribe(e)}" }
            null
        } ?: return emptyMap()
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: kotlinx.serialization.SerializationException) {
            // A damaged record only means files are offered to the server again (it dedupes).
            logger.warn { "device logs: record malformed, starting empty" }
            emptyMap()
        }
    }

    override fun write(record: Map<String, DeviceLogRecord>) {
        AtomicFileWriter.writeString(path, json.encodeToString(serializer, record))
    }
}
