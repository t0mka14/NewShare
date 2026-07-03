package org.example.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.navigation.RootComponent

@Composable
fun RootContent(component: RootComponent) {
    val localization by component.localization.subscribeAsState()

    Children(stack = component.stack, animation = stackAnimation()) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Blocking -> BlockingConfigurationRequiredContent(
                localization = localization,
                onOpenSettings = component::onOpenSettingsFromBlocking,
            )

            is RootComponent.Child.MainMenu -> MainMenuContent(instance.component, localization)

            is RootComponent.Child.Settings -> SettingsContent(
                component = instance.component,
                localization = localization,
                onBack = component::onSettingsBack,
            )

            is RootComponent.Child.PatientInfo -> PatientInfoContent(
                component = instance.component,
                localization = localization,
                onBack = component::onPatientInfoBack,
            )

            is RootComponent.Child.Session -> SessionContent(
                component = instance.component,
                navigableTasks = instance.navigableTasks,
                availableDevices = instance.availableDevices,
                initialDevice = instance.initialDevice,
                localization = localization,
                onBackToMenu = component::onSessionFailedBackToMenu,
            )

            is RootComponent.Child.SessionSummary -> SessionSummaryContent(
                localization = localization,
                onDone = component::onSessionSummaryDone,
            )

            is RootComponent.Child.Upload,
            is RootComponent.Child.SessionBrowser,
            -> PlaceholderContent(localization = localization, onBack = component::onPlaceholderBack)
        }
    }
}
