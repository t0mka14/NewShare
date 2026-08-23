package org.example.app.domain.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * PTZ control is DirectShow over COM and works on Windows only. Keeping it unreachable
 * elsewhere is a rule about *where the type may be named*, which a reviewer can miss, so it is
 * checked here instead of trusted.
 *
 * The mechanism being guarded: `AppContainer.ptzControllerFactory` is the single site allowed
 * to name a platform implementation. Because JVM class loading is lazy and the type appears in
 * no other signature, a non-Windows host never loads the COM classes at all — they are not
 * merely never called, so JNA's native dispatch library is never even extracted.
 */
class PtzPlatformIsolationTest {

    private val mainSources: List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `sources are actually being scanned`() {
        assertTrue(mainSources.size > 50, "expected to find the main source tree, found ${mainSources.size} files")
    }

    /**
     * `DirectShowPtzController` does not exist yet (PTZ is stage 2). This test is written now
     * so the constraint is enforced from the moment it does — if it lands anywhere but the
     * factory, this fails immediately rather than at the first macOS bug report.
     */
    @Test
    fun `no Windows PTZ implementation is named outside the container factory`() {
        val offenders = mainSources.filter { file ->
            file.name != "AppContainer.kt" &&
                !file.path.contains("infrastructure/video/windows") &&
                file.readText().contains(WINDOWS_PTZ_TYPE)
        }

        assertEquals(
            emptyList<String>(),
            offenders.map { it.path },
            "$WINDOWS_PTZ_TYPE may only be named by AppContainer's OS-guarded factory, " +
                "otherwise the class is loaded (and JNA initialised) on platforms that cannot use it",
        )
    }

    /** JNA is a Windows-only concern here; it must not appear in domain, UI or navigation code. */
    @Test
    fun `JNA is not referenced outside the windows infrastructure package`() {
        val offenders = mainSources.filter { file ->
            !file.path.contains("infrastructure/video/windows") &&
                file.readText().contains("com.sun.jna")
        }

        assertEquals(emptyList<String>(), offenders.map { it.path })
    }

    @Test
    fun `the default controller reports no PTZ and ignores every action`() {
        assertFalse(NoOpPtzController.isAvailable)
        assertEquals(null, NoOpPtzController.zoom.value)

        PtzAction.entries.forEach { NoOpPtzController.move(it) }
        NoOpPtzController.close()
    }

    private companion object {
        const val WINDOWS_PTZ_TYPE = "DirectShowPtzController"
    }
}
