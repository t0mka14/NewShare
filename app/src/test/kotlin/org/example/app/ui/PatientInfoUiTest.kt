package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.config.PatientField
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.participant.ValidateParticipantInfoUseCase
import org.example.app.navigation.DefaultPatientInfoComponent
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PatientInfoUiTest {

    private val manyFields = listOf("current_date", "sex", "education", "patient_code") +
        (1..10).map { "custom_$it" }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a long form scrolls so Continue stays reachable, and empty dropdowns say Choose`() = runComposeUiTest {
        val component = DefaultPatientInfoComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            fields = manyFields.map { PatientField(name = it, labelKey = it) },
            examinationDate = LocalDate.of(2026, 9, 29),
            validateParticipantInfoUseCase = ValidateParticipantInfoUseCase(),
            onValidated = {},
        )
        setContent { PatientInfoContent(component, UiLocalization(LocalizedStringProvider(), "en", null), onBack = {}) }
        waitForIdle()

        onNodeWithTag(TestTags.PatientInfo.field("sex")).assertTextEquals("Choose…")
        onNodeWithTag(TestTags.PatientInfo.CONTINUE_BUTTON).performScrollTo().assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a bad format shows only once the field loses focus`() = runComposeUiTest {
        val component = DefaultPatientInfoComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            fields = listOf(
                PatientField(name = "patient_code", labelKey = "patient_code", regex = "[A-Z]{2}\\d{3}"),
                PatientField(name = "custom_1", labelKey = "custom_1"),
            ),
            examinationDate = LocalDate.of(2026, 9, 29),
            validateParticipantInfoUseCase = ValidateParticipantInfoUseCase(),
            onValidated = {},
        )
        setContent { PatientInfoContent(component, UiLocalization(LocalizedStringProvider(), "en", null), onBack = {}) }

        onNodeWithTag(TestTags.PatientInfo.field("patient_code")).performClick()
        onNodeWithTag(TestTags.PatientInfo.field("patient_code")).performTextInput("HC")
        waitForIdle()
        onNodeWithTag(TestTags.PatientInfo.fieldError("patient_code")).assertDoesNotExist()

        onNodeWithTag(TestTags.PatientInfo.field("custom_1")).performClick() // focus moves on
        waitForIdle()
        onNodeWithTag(TestTags.PatientInfo.fieldError("patient_code")).assertIsDisplayed()
    }
}
