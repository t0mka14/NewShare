package org.example.app.domain.logs

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** What the server answered for one log file, by the hash that was sent. */
@Serializable
data class DeviceLogRecord(
    val sha256: String,
    val state: State,
) {
    @Serializable
    enum class State {
        @SerialName("sent") Sent,
        @SerialName("unsendable") Unsendable,
    }
}

/** `config/device_logs_sent.json`: file name to [DeviceLogRecord]. */
interface DeviceLogRecordStore {
    /** The record, empty when there is none or it cannot be read. */
    fun read(): Map<String, DeviceLogRecord>

    /** Replaces the record atomically. */
    fun write(record: Map<String, DeviceLogRecord>)
}
