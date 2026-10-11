package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.navigation.DefaultProtocolPickerComponent
import org.junit.jupiter.api.Test

/** A protocol's `descriptionKey` (§13 decision 55) renders its config string under the button; no key, no line. */
class ProtocolDescriptionUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the description line shows the config string and is absent without a key`() = runComposeUiTest {
        val described = Protocol(name = "Standard", descriptionKey = "standard_description", recordingsFileName = "a")
        val plain = Protocol(name = "Short", recordingsFileName = "b")
        val config = RemoteConfig(
            schemaVersion = 1, configVersion = "v", defaultLanguage = "en",
            protocols = listOf(described, plain),
            strings = mapOf("en" to mapOf("standard_description" to "No. tasks: 7. Requires camera.")),
        )
        val component = DefaultProtocolPickerComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            protocols = config.protocols,
            onProtocolSelectedClicked = {},
            onBackClicked = {},
        )

        setContent { ProtocolPickerContent(component, UiLocalization(LocalizedStringProvider(), "en", config)) }

        onNodeWithTag(TestTags.ProtocolPicker.protocolDescription("Standard"))
            .assertIsDisplayed()
            .assertTextEquals("No. tasks: 7. Requires camera.")
        onNodeWithTag(TestTags.ProtocolPicker.protocolDescription("Short")).assertDoesNotExist()
    }
}
