package org.example.app.domain.settings

import org.example.app.fakes.FakeAppSettingsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class InstallationIdProviderTest {

    @Test
    fun `generates once, persists it and returns the same id afterwards`() {
        val repository = FakeAppSettingsRepository()
        var generated = 0
        val provider = InstallationIdProvider(repository) { "pc-${++generated}" }

        assertEquals("pc-1", provider.get())
        assertEquals("pc-1", provider.get())
        assertEquals(1, generated)
        assertEquals("pc-1", repository.read()?.installationId)
    }

    @Test
    fun `an id saved earlier is kept`() {
        val repository = FakeAppSettingsRepository().apply { write(AppSettings(installationId = "DEMO-001")) }

        assertEquals("DEMO-001", InstallationIdProvider(repository) { error("must not generate") }.get())
    }

    @Test
    fun `a blank saved id is replaced`() {
        val repository = FakeAppSettingsRepository().apply { write(AppSettings(installationId = " ")) }

        assertEquals("fresh", InstallationIdProvider(repository) { "fresh" }.get())
    }

    @Test
    fun `generating keeps every other setting`() {
        val saved = AppSettings(micDeviceId = "mic-1", cameraDeviceId = "cam-1", siteToken = "token", language = "cs", micGain = 40)
        val repository = FakeAppSettingsRepository().apply { write(saved) }

        InstallationIdProvider(repository) { "pc-1" }.get()

        assertEquals(saved.copy(installationId = "pc-1"), repository.read())
    }

    @Test
    fun `the default generator produces a UUID`() {
        val id = InstallationIdProvider(FakeAppSettingsRepository()).get()

        assertEquals(id, UUID.fromString(id).toString())
    }
}
