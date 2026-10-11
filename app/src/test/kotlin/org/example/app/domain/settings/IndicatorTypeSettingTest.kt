package org.example.app.domain.settings

import org.example.app.domain.config.IndicatorType
import org.example.app.domain.config.RemoteConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IndicatorTypeSettingTest {

    private val waveformConfig = RemoteConfig(
        schemaVersion = 1, configVersion = "v", defaultLanguage = "en", indicatorType = IndicatorType.WAVEFORM,
    )

    @Test
    fun `no settings or no override follows the config`() {
        assertEquals(IndicatorType.WAVEFORM, (null as AppSettings?).indicatorType(waveformConfig))
        assertEquals(IndicatorType.WAVEFORM, AppSettings().indicatorType(waveformConfig))
    }

    @Test
    fun `a Settings override wins over the config`() {
        assertEquals(IndicatorType.CIRCLE, AppSettings(indicatorType = IndicatorType.CIRCLE).indicatorType(waveformConfig))
    }
}
