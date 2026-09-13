package org.example.app

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Guards the wiring between the `appVersion` Gradle property, the generated `version.json`
 * resource and what the running app reports. A silent break here is invisible in dev but makes
 * the updater re-apply the same package on every run (§13 decision 38), so it gets a test.
 */
class BuildInfoTest {

    @Test
    fun `the generated version resource is on the classpath`() {
        assertNotEquals(
            BuildInfo.UNKNOWN,
            BuildInfo.appVersion,
            "version.json resource missing — generateAppVersionJson is no longer wired into the main resources",
        )
    }

    @Test
    fun `the reported version is the one the build was invoked with`() {
        val expected = System.getProperty("share.expectedVersion")
            ?: error("share.expectedVersion not set; see tasks.test in app/build.gradle.kts")
        assertEquals(expected, BuildInfo.appVersion.toString())
    }
}
