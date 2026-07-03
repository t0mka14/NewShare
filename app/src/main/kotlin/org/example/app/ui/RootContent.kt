package org.example.app.ui

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import org.example.app.navigation.RootComponent

@Composable
fun RootContent(component: RootComponent) {
    Children(
        stack = component.stack,
        animation = stackAnimation()
    ) {
        when (val child = it.instance) {
            is RootComponent.Child.Home -> HomeContent(child.component)
            is RootComponent.Child.Recorder -> RecorderContent(child.component)
        }
    }
}
