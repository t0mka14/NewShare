package org.example.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import org.example.app.domain.config.ConfigError
import org.example.app.domain.config.IndicatorType
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.upload.UploadStatusValue
import org.example.app.navigation.SettingsComponent
import org.example.app.navigation.UploadComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Fix round 4, increment D1: F-064, F-088, F-089, F-105, F-046. */
@OptIn(ExperimentalTestApi::class)
class FixRound4DUiTest {

    private val en = UiLocalization(LocalizedStringProvider(), "en", null)
    private val cs = UiLocalization(LocalizedStringProvider(), "cs", null)

    // --- F-064 ---

    @Test
    fun `F-064 one ready session reads singular in English`() {
        assertEquals("1 session ready to upload", en.resolve(uploadReadyCountKey(1), mapOf("count" to "1")).text)
        assertEquals("3 sessions ready to upload", en.resolve(uploadReadyCountKey(3), mapOf("count" to "3")).text)
        assertEquals("0 sessions ready to upload", en.resolve(uploadReadyCountKey(0), mapOf("count" to "0")).text)
    }

    @Test
    fun `F-064 ready count is natural Czech for one, few and many`() {
        assertEquals("Sezení připravené k odeslání: 1", cs.resolve(uploadReadyCountKey(1), mapOf("count" to "1")).text)
        assertEquals("Sezení připravená k odeslání: 3", cs.resolve(uploadReadyCountKey(3), mapOf("count" to "3")).text)
        assertEquals("Sezení připravená k odeslání: 7", cs.resolve(uploadReadyCountKey(7), mapOf("count" to "7")).text)
    }

    // --- F-088 ---

    private class StubSettings : SettingsComponent {
        private val _state = MutableValue(
            SettingsComponent.State(availableLanguages = listOf("cs", "en"), selectedLanguage = "en"),
        )
        override val state: Value<SettingsComponent.State> = _state
        override fun onDeviceSelected(deviceId: String) = Unit
        override fun onSiteTokenChanged(value: String) = Unit
        override fun onLanguageSelected(language: String) = Unit
        override fun onRefreshClicked() = Unit
        override fun onCameraSelected(deviceId: String?) = Unit
        override fun onRefreshDevicesClicked() = Unit
        override fun onIndicatorTypeSelected(type: IndicatorType) = Unit
        override fun onIndicatorTypeReset() = Unit
        override fun onMicGainChanged(value: Int) = Unit
        override fun onMicGainChangeFinished() = Unit
        override fun onMicGainReset() = Unit
    }

    @Test
    fun `F-088 settings language options show names not raw codes`() = runDesktopComposeUiTest(1000, 1400) {
        setContent { SettingsContent(StubSettings(), en) {} }
        waitForIdle()
        onNodeWithText("Čeština").assertExists()
        onNodeWithText("English").assertExists()
        assertEquals("Čeština", languageDisplayName("cs"))
        assertEquals("xx", languageDisplayName("xx"))
    }

    // --- F-089 ---

    @Test
    fun `F-089 no-token message is centre aligned`() = runDesktopComposeUiTest(1000, 820) {
        setContent { BlockingConfigurationRequiredContent(en, ConfigError.SiteTokenMissing) {} }
        waitForIdle()
        val node = onNodeWithTag(TestTags.Blocking.CONFIGURATION_REQUIRED_MESSAGE).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        assertEquals(TextAlign.Center, results.single().layoutInput.style.textAlign)
        assertTrue(SemanticsProperties.Text in node.config)
    }

    // --- F-105 ---

    @Test
    fun `F-105 editor times are rounded to the shown tenth`() {
        val rate = 1000
        assertEquals("1:02.9", formatSampleTime(62_887, rate))
        assertEquals("9.0 s", formatSampleDuration(8_987, rate))
        assertEquals("0:07.4", formatSampleTime(7_440, rate))
        assertEquals("7.4 s", formatSampleDuration(7_449, rate))
    }

    @Test
    fun `F-105 rounding carries over into the next unit`() {
        val rate = 1000
        assertEquals("1:00.0", formatSampleTime(59_960, rate))
        assertEquals("1:00 min", formatSampleDuration(59_960, rate))
        assertEquals("0:59.9", formatSampleTime(59_940, rate))
        assertEquals("59.9 s", formatSampleDuration(59_940, rate))
    }

    // --- F-046 ---

    private fun row(status: UploadStatusValue) = UploadComponent.Row("f", "s", "p", status, null, "01. 01. 2026", emptyMap())

    @Test
    fun `F-046 progress bar turns to the error colour when any session failed`() {
        assertTrue(uploadHasFailure(listOf(row(UploadStatusValue.NotUploaded), row(UploadStatusValue.Failed))))
        assertFalse(uploadHasFailure(listOf(row(UploadStatusValue.NotUploaded))))
        assertFalse(uploadHasFailure(emptyList()))
    }
}
