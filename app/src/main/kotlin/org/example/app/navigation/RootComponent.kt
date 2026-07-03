package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable
import org.example.app.AppContainer

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    sealed class Child {
        class Home(val component: HomeComponent) : Child()
        class Recorder(val component: RecorderComponent) : Child()
    }
}

class DefaultRootComponent(
    context: ComponentContext,
    private val container: AppContainer
) : RootComponent, ComponentContext by context {

    private val navigation = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.Home,
            handleBackButton = true,
            childFactory = ::createChild
        )

    @OptIn(DelicateDecomposeApi::class)
    private fun createChild(config: Config, context: ComponentContext): RootComponent.Child =
        when (config) {
            Config.Home -> RootComponent.Child.Home(
                DefaultHomeComponent(
                    context = context,
                    onNavigateToRecorder = { navigation.push(Config.Recorder) }
                )
            )
            Config.Recorder -> RootComponent.Child.Recorder(
                DefaultRecorderComponent(
                    context = context,
                    audioRecorder = container.audioRecorder,
                    sessionsDir = container.sessionsDir,
                    onFinished = { navigation.pop() }
                )
            )
        }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Home : Config
        @Serializable
        data object Recorder : Config
    }
}
