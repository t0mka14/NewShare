package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
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
}
