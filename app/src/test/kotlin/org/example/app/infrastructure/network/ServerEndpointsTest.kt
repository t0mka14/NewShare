package org.example.app.infrastructure.network

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Guards the wiring from the `serverBaseUrl` Gradle property to the generated `server.json`. */
class ServerEndpointsTest {

    @Test
    fun `the generated server resource is on the classpath`() {
        assertNotNull(
            ServerJson::class.java.getResource("/org/example/app/server.json"),
            "server.json resource missing - generateServerJson is no longer wired into the main resources",
        )
    }

    @Test
    fun `the base url is the one the build was invoked with`() {
        val expected = System.getProperty("share.expectedServerBaseUrl")
            ?: error("share.expectedServerBaseUrl not set; see tasks.test in app/build.gradle.kts")
        assertEquals(expected, SERVER_BASE_URL)
        assertEquals("$expected/api", WEB_SERVER_BASE_URL)
    }
}
