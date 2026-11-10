package org.example.app.domain.logs

import org.example.app.domain.session.SessionActivity
import org.example.app.infrastructure.logging.LogDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class SessionActivityTest {

    @Test
    fun `active from begin until its end function runs, and ending twice does nothing`() {
        val activity = SessionActivity()
        assertFalse(activity.active.value)

        val end = activity.begin()
        assertTrue(activity.active.value)
        end()
        assertFalse(activity.active.value)

        val first = activity.begin()
        val second = activity.begin()
        first()
        first()
        assertTrue(activity.active.value)
        second()
        assertFalse(activity.active.value)
    }

    @Test
    fun `log directory resolves like logback - property, then env, then user dir`() {
        val none: (String) -> String? = { null }
        assertEquals(
            Path.of("/prop"),
            LogDirectory.resolve({ "/prop" }, { "/env" }, "/work"),
        )
        assertEquals(Path.of("/env"), LogDirectory.resolve(none, { "/env" }, "/work"))
        assertEquals(Path.of("/work", "data", "logs"), LogDirectory.resolve(none, none, "/work"))
        assertEquals(Path.of("/work", "data", "logs"), LogDirectory.resolve({ " " }, none, "/work"))
    }
}
