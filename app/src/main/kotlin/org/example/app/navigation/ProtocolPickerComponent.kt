package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import org.example.app.domain.config.Protocol

/**
 * §3 follow-up: shown on the main menu only when the active config defines more than one
 * protocol (a single-protocol config skips straight to patient info, per `RootComponent`).
 * [protocols] is static for the lifetime of this screen — the active config cannot change
 * while a protocol is being chosen (starting a new protocol requires a config in the first
 * place, and Settings/refresh are not reachable from here).
 */
interface ProtocolPickerComponent {
    val protocols: List<Protocol>

    fun onProtocolSelected(protocol: Protocol)
    fun onBack()
}

class DefaultProtocolPickerComponent(
    componentContext: ComponentContext,
    override val protocols: List<Protocol>,
    private val onProtocolSelectedClicked: (Protocol) -> Unit,
    private val onBackClicked: () -> Unit,
) : ProtocolPickerComponent, ComponentContext by componentContext {

    override fun onProtocolSelected(protocol: Protocol) = onProtocolSelectedClicked(protocol)

    override fun onBack() = onBackClicked()
}
