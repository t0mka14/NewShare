package org.example.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.domain.ErrorReporter
import org.example.app.navigation.RootComponent

@Composable
fun RootContent(
    component: RootComponent,
    errorReporter: ErrorReporter? = null,
    onExitApp: () -> Unit = {},
) {
    val localization by component.localization.subscribeAsState()

    if (errorReporter != null) {
        UnexpectedErrorDialog(errorReporter, localization, onExitApp)
    }

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

            is RootComponent.Child.ProtocolPicker -> ProtocolPickerContent(instance.component, localization)

            is RootComponent.Child.PatientInfo -> PatientInfoContent(
                component = instance.component,
                localization = localization,
                onBack = component::onPatientInfoBack,
            )

            is RootComponent.Child.Session -> SessionContent(
                component = instance.component,
                localization = localization,
                onBackToMenu = component::onSessionFailedBackToMenu,
            )

            is RootComponent.Child.Editor -> EditorContent(instance.component, localization)

            is RootComponent.Child.Processing -> ProcessingContent(instance.component, localization)

            is RootComponent.Child.SessionSummary -> SessionSummaryContent(
                localization = localization,
                onDone = component::onSessionSummaryDone,
            )

            is RootComponent.Child.Upload -> UploadContent(instance.component, localization)

            is RootComponent.Child.SessionBrowser -> SessionBrowserContent(instance.component, localization)
        }
    }
}
