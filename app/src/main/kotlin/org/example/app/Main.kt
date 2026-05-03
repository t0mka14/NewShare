package org.example.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.example.shared.model.AppVersion

fun main() = application {
    val version = AppVersion(1, 0, 0)
    Window(onCloseRequest = ::exitApplication, title = "Clinical Recording App (v$version)") {
        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Welcome to the Clinical Recording App")
            }
        }
    }
}
