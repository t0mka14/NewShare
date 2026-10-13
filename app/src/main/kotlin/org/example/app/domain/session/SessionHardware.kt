package org.example.app.domain.session

import kotlinx.serialization.Serializable
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.video.VideoCaptureFormat

/**
 * `examination.json.hardware`: which microphone and camera a session really used, for
 * diagnosing "why does this recording sound/look off" after the fact.
 *
 * `configured*` is what Settings said at session start (null = nothing saved, a default was
 * picked); [micUses]/[cameraUses] are what was actually opened, one entry per open — session
 * start, a calibration switch, a device-loss resume (mic); each time the camera is re-opened for a
 * filming screen (camera). More than one entry therefore *is* the change log; [events] adds the
 * losses in between. Additive with defaults, so no `version` bump.
 */
@Serializable
data class SessionHardware(
    val configuredMicDeviceId: String? = null,
    val configuredCameraDeviceId: String? = null,
    val micUses: List<MicUse> = emptyList(),
    val cameraUses: List<CameraUse> = emptyList(),
    val events: List<HardwareEvent> = emptyList(),
)

@Serializable
data class MicUse(
    val openedAt: String,
    val deviceId: String,
    val deviceName: String,
    val description: String = "",
    /**
     * OS input level (0–100, the Windows sound-panel scale) read back right after the line opened —
     * the level actually in force, not the one requested. Null when the port mixer exposes none.
     * A boost stage (`Mic Boost`) is not included; Java Sound cannot read it reliably.
     */
    val inputGainPercent: Int? = null,
    val captureFormat: CaptureFormat? = null,
)

@Serializable
data class CameraUse(
    val openedAt: String,
    val deviceId: String,
    val deviceName: String,
    val nameOrdinal: Int,
    val platformIndex: Int,
    val captureFormat: VideoCaptureFormat? = null,
)

/** [device] is `microphone` or `camera`; [event] e.g. `interrupted`, `capture_failed`, `gain_changed`. */
@Serializable
data class HardwareEvent(
    val at: String,
    val device: String,
    val event: String,
    val detail: String? = null,
)