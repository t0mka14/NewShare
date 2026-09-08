package org.example.app.fakes

import org.example.app.domain.audio.AudioInputGainControl
import org.example.app.domain.audio.GainApplyResult

/**
 * Records level writes and serves reads from [levels] (device name → 0..100). A set
 * [result] is returned verbatim instead of the default `Applied` (e.g. to simulate a device
 * with no volume control); [throwOnSet] simulates a failing sound subsystem.
 */
class FakeAudioInputGainControl(
    val levels: MutableMap<String, Int> = mutableMapOf(),
    var result: GainApplyResult? = null,
    var throwOnSet: Boolean = false,
) : AudioInputGainControl {
    val setCalls = mutableListOf<Pair<String, Int>>()
    val readCalls = mutableListOf<String>()

    override fun setInputGain(deviceName: String, percent: Int): GainApplyResult {
        if (throwOnSet) throw IllegalStateException("sound subsystem unavailable")
        setCalls += deviceName to percent
        result?.let { return it }
        levels[deviceName] = percent
        return GainApplyResult.Applied("Port $deviceName", 1)
    }

    override fun readInputGain(deviceName: String): Int? {
        readCalls += deviceName
        return levels[deviceName]
    }
}
