package org.example.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.navigation.MainMenuComponent

/**
 * §3 main menu, in the original's three-band layout (§4 "keep... look and layout",
 * `shareapp/src/main/kotlin/screens/MainScreen.kt`): a title bar carrying the language flag at
 * the top right, the centered button column, and the SAMI logo bottom right.
 *
 * Two deliberate departures from a literal copy. The original centered its title with a
 * `Spacer(100.dp)` opposite the flag row and `Arrangement.SpaceBetween`, so the title was only
 * *accidentally* centered (the flag row happens to be about as wide as the spacer); equal
 * weighted slots on both sides center it for real. And its buttons were a flat
 * `fillMaxWidth(0.3f)`, which collapses to unusable stubs on a small window — here they fill a
 * 60%-wide column up to a 560dp cap, so a 1920px screen looks like the original and a
 * tablet-sized one still reads.
 */
@Composable
fun MainMenuContent(component: MainMenuComponent, localization: UiLocalization) {
    val state by component.state.subscribeAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TitleBar(component, localization)

        // Centered while there is room; scrolls instead of clipping once the window is shorter
        // than the button column needs (the original clipped).
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            MenuButtons(component, state, localization)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Image(
                painter = painterResource("drawable/sami_trans.png"),
                contentDescription = "SAMI",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(140.dp),
            )
        }
    }
}

@Composable
private fun TitleBar(component: MainMenuComponent, localization: UiLocalization) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Equal slots either side of the title keep it centered whether or not the language
        // selector is showing.
        Spacer(modifier = Modifier.weight(1f))
        Text(
            localization.resolve("mainMenu.title"),
            style = screenTitleTextStyle(),
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            LanguageSelector(component, localization)
        }
    }
}

@Composable
private fun MenuButtons(
    component: MainMenuComponent,
    state: MainMenuComponent.State,
    localization: UiLocalization,
) {
    val buttonWidth = Modifier.contentWidth(560.dp)
    val buttonText = actionButtonTextStyle()

    Column(
        modifier = Modifier.fillMaxWidth(0.6f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Button(
            onClick = component::onStartProtocol,
            enabled = state.startEnabled,
            modifier = buttonWidth.testTag(TestTags.MainMenu.START_PROTOCOL_BUTTON),
        ) {
            Text(localization.resolve("mainMenu.startButton"), style = buttonText)
        }

        Button(
            onClick = component::onSettings,
            modifier = buttonWidth.testTag(TestTags.MainMenu.SETTINGS_BUTTON),
        ) {
            Text(localization.resolve("mainMenu.settingsButton"), style = buttonText)
        }

        ProtocolPdfButton(component, state, localization, buttonWidth, buttonText)

        Button(
            onClick = component::onUpload,
            modifier = buttonWidth.testTag(TestTags.MainMenu.UPLOAD_BUTTON),
        ) {
            Text(localization.resolve("mainMenu.uploadButton"), style = buttonText)
        }

        Button(
            onClick = component::onSessionBrowser,
            modifier = buttonWidth.testTag(TestTags.MainMenu.SESSION_BROWSER_BUTTON),
        ) {
            Text(localization.resolve("mainMenu.sessionBrowserButton"), style = buttonText)
        }
    }
}

/**
 * The original's "Get protocol PDF" button. Disabled when no protocol declares a
 * `protocolInstructionsPdfUrl`; with exactly one it opens straight away, and with several it
 * drops down a picker of protocol names rather than guessing.
 */
@Composable
private fun ProtocolPdfButton(
    component: MainMenuComponent,
    state: MainMenuComponent.State,
    localization: UiLocalization,
    modifier: Modifier,
    textStyle: TextStyle,
) {
    val pdfs = state.protocolPdfs
    // Menu expansion is UI state, not component state (§5.2) — same rule as DropdownSelector.
    var pickerExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = {
                if (pdfs.size == 1) component.onOpenProtocolPdf(pdfs.first().url) else pickerExpanded = true
            },
            enabled = pdfs.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag(TestTags.MainMenu.PROTOCOL_PDF_BUTTON),
        ) {
            Text(localization.resolve("mainMenu.protocolPdfButton"), style = textStyle)
        }
        DropdownMenu(expanded = pickerExpanded, onDismissRequest = { pickerExpanded = false }) {
            pdfs.forEach { pdf ->
                DropdownMenuItem(
                    text = { Text(pdf.protocolName) },
                    onClick = {
                        pickerExpanded = false
                        component.onOpenProtocolPdf(pdf.url)
                    },
                    modifier = Modifier.testTag(TestTags.MainMenu.protocolPdfOption(pdf.protocolName)),
                )
            }
        }
    }
}

/**
 * The original's top-right language flag (`MainScreen.languageDropdownMenu`): the current
 * language's flag and code, dropping down the same row per configured language. Hidden when
 * the config offers nothing to switch between; Settings keeps its own language dropdown.
 */
@Composable
private fun LanguageSelector(component: MainMenuComponent, localization: UiLocalization) {
    val languages = localization.config?.languages.orEmpty()
    if (languages.size < 2) return
    var expanded by remember { mutableStateOf(false) }

    Box {
        LanguageRow(
            language = localization.language,
            modifier = Modifier
                .clickable { expanded = true }
                .testTag(TestTags.MainMenu.LANGUAGE_SELECTOR),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { language ->
                LanguageRow(
                    language = language,
                    modifier = Modifier
                        .clickable {
                            expanded = false
                            component.onLanguageSelected(language)
                        }
                        .testTag(TestTags.MainMenu.languageOption(language)),
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(language: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A language with no bundled flag still renders — just as its code (§6: the config
        // decides which languages exist, this file only knows about the flags it ships).
        FlagResources[language]?.let { flag ->
            Image(
                painter = painterResource(flag),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(language, style = MaterialTheme.typography.labelLarge)
    }
}

private val FlagResources = mapOf(
    "cs" to "drawable/flags/czech.png",
    "en" to "drawable/flags/english.png",
)
