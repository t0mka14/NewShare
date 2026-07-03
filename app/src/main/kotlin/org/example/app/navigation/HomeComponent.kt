package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext

interface HomeComponent {
    fun onRecorderClicked()
}

class DefaultHomeComponent(
    context: ComponentContext,
    private val onNavigateToRecorder: () -> Unit
) : HomeComponent, ComponentContext by context {
    override fun onRecorderClicked() {
        onNavigateToRecorder()
    }
}
