package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.AppContainer
import org.example.app.fakes.ConfigFixtures
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeIdGenerator
import org.example.app.fakes.TestAppDirectories
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * §6.1 routing: no-config → blocking screen; cached/fetched config → main menu. `AppContainer`
 * is the composition root (§5.2) — only `directories`/`clock`/`idGenerator`/`dispatchers` are
 * swappable, so this exercises the real `JsonConfigurationRepository`/`JsonAppSettingsRepository`
 * etc. against a temp directory rather than fakes (chunk-1's per-component tests already cover
 * `MainMenuComponent`/`SettingsComponent`/`PatientInfoComponent` in isolation with fakes; this
 * suite is scoped to the navigation changes RootComponent itself owns).
 */
class RootComponentTest {

    private fun buildContainer(tempDir: Path, dispatchers: TestCoroutineDispatchers = TestCoroutineDispatchers()): AppContainer =
        AppContainer(
            directories = TestAppDirectories(tempDir),
            clock = FakeClock(),
            idGenerator = FakeIdGenerator(),
            dispatchers = dispatchers,
        )

    private fun buildRoot(container: AppContainer): RootComponent =
        DefaultRootComponent(DefaultComponentContext(LifecycleRegistry()), container)

    @Test
    fun `no cached config routes to the blocking screen`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.configurationRepository.loadCached()

        val root = buildRoot(container)

        assertTrue(root.stack.value.active.instance is RootComponent.Child.Blocking)
    }

    @Test
    fun `a valid cached config routes straight to the main menu`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.rawConfigCache.write(ConfigFixtures.questionnaireOnly)
        container.configurationRepository.loadCached()

        val root = buildRoot(container)

        assertTrue(root.stack.value.active.instance is RootComponent.Child.MainMenu)
    }

    @Test
    fun `applying a fetched config while blocked navigates to the main menu`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val container = buildContainer(tempDir, dispatchers)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Blocking)

        container.configurationRepository.applyFetched(ConfigFixtures.questionnaireOnly)
        dispatchers.scheduler.advanceUntilIdle()

        assertTrue(root.stack.value.active.instance is RootComponent.Child.MainMenu)
    }

    @Test
    fun `settings button then back returns to the main menu`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.rawConfigCache.write(ConfigFixtures.questionnaireOnly)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)

        val menu = (root.stack.value.active.instance as RootComponent.Child.MainMenu).component
        menu.onSettings()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Settings)

        root.onSettingsBack()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.MainMenu)
    }

    @Test
    fun `start protocol opens patient info and back returns to the main menu`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.rawConfigCache.write(ConfigFixtures.questionnaireOnly)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)

        val menu = (root.stack.value.active.instance as RootComponent.Child.MainMenu).component
        menu.onStartProtocol()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.PatientInfo)

        root.onPatientInfoBack()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.MainMenu)
    }

    @Test
    fun `blocking screen open-settings button navigates to settings`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Blocking)

        root.onOpenSettingsFromBlocking()

        assertTrue(root.stack.value.active.instance is RootComponent.Child.Settings)
    }
}
