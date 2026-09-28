package org.example.app.domain.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoudnessRangeTest {

    @Test
    fun `no settings or no override uses the default band`() {
        assertEquals(DEFAULT_OPTIMAL_LOUDNESS, (null as AppSettings?).loudnessRange())
        assertEquals(DEFAULT_OPTIMAL_LOUDNESS, AppSettings().loudnessRange())
    }

    @Test
    fun `a well-formed override wins`() {
        assertEquals(0.1..0.7, AppSettings(optimalLoudness = listOf(0.1, 0.7)).loudnessRange())
    }

    @Test
    fun `malformed overrides fall back to the default`() {
        listOf(
            listOf(0.3),
            listOf(0.1, 0.2, 0.3),
            listOf(0.6, 0.4),
            listOf(-0.1, 0.5),
            listOf(0.2, 1.5),
        ).forEach { band ->
            assertEquals(DEFAULT_OPTIMAL_LOUDNESS, AppSettings(optimalLoudness = band).loudnessRange(), "$band")
        }
    }
}
