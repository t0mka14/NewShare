package org.example.shared.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** §10.1: "updater version comparison" unit tests, including malformed versions. */
class AppVersionTest {

    @Test
    fun `parses well-formed versions`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
        assertEquals(AppVersion(0, 0, 0), AppVersion.parse("0.0.0"))
        assertEquals(AppVersion(10, 20, 30), AppVersion.parse("10.20.30"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(AppVersion(1, 0, 0), AppVersion.parse("  1.0.0  "))
    }

    @Test
    fun `rejects malformed versions`() {
        val malformed = listOf(
            "",
            "1.0",
            "1.0.0.0",
            "v1.0.0",
            "1.0.0-beta",
            "1.0.0+build5",
            "a.b.c",
            "1..0",
            "1.0.",
            "-1.0.0",
            "1.0.0 x",
        )
        for (raw in malformed) {
            assertNull(AppVersion.parse(raw), "expected null for '$raw'")
        }
    }

    @Test
    fun `compares by major then minor then patch`() {
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 9, 9))
        assertTrue(AppVersion(1, 3, 0) > AppVersion(1, 2, 9))
        assertTrue(AppVersion(1, 2, 4) > AppVersion(1, 2, 3))
        assertEquals(0, AppVersion(1, 2, 3).compareTo(AppVersion(1, 2, 3)))
    }

    @Test
    fun `toString renders dotted form`() {
        assertEquals("1.2.3", AppVersion(1, 2, 3).toString())
    }
}
