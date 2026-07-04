package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.config.Protocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** §3 follow-up: protocol picker component tests (fakes only, no Compose). */
class ProtocolPickerComponentTest {

    private val share = Protocol(name = "Share", recordingsFileName = "\${taskIndex}.wav")
    private val questionnaireOnly = Protocol(name = "QuestionnaireOnly", recordingsFileName = "\${taskIndex}.wav")

    private class Harness(protocols: List<Protocol>) {
        var selected: Protocol? = null
        var backCalled = 0

        val component: ProtocolPickerComponent = DefaultProtocolPickerComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            protocols = protocols,
            onProtocolSelectedClicked = { protocol -> selected = protocol },
            onBackClicked = { backCalled++ },
        )
    }

    @Test
    fun `exposes the protocols it was given, in order`() {
        val h = Harness(listOf(share, questionnaireOnly))
        assertEquals(listOf(share, questionnaireOnly), h.component.protocols)
    }

    @Test
    fun `selecting a protocol forwards exactly that protocol to the caller`() {
        val h = Harness(listOf(share, questionnaireOnly))
        h.component.onProtocolSelected(questionnaireOnly)
        assertEquals(questionnaireOnly, h.selected)
    }

    @Test
    fun `back forwards to the caller`() {
        val h = Harness(listOf(share))
        h.component.onBack()
        assertEquals(1, h.backCalled)
    }
}
