package org.example.app

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.navigation.DefaultRootComponent
import org.example.app.ui.RootContent
import org.example.shared.model.AppVersion

fun main() {
    val lifecycle = LifecycleRegistry()
    val container = AppContainer()
    val root = DefaultRootComponent(
        context = DefaultComponentContext(lifecycle = lifecycle),
        container = container
    )

    application {
        val version = AppVersion(1, 0, 0)
        Window(onCloseRequest = ::exitApplication, title = "Clinical Recording App (v$version)") {
            MaterialTheme {
                RootContent(root)
            }
        }
    }
}
