package org.example.app.ui

import androidx.compose.ui.text.AnnotatedString
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.richtext.RichTextParser
import org.example.app.ui.richtext.toAnnotatedString

/**
 * Everything a screen composable needs to resolve display text (§7, §12: no display literals
 * in UI code) without touching `AppSettingsRepository`/`ConfigurationRepository` itself —
 * `RootComponent` combines the current language selection with the active config once and
 * hands screens this bundle explicitly, consistent with "stateless UI, render component state"
 * (§5.2): screens take this as a plain parameter, not a `CompositionLocal`.
 */
data class UiLocalization(
    val provider: LocalizedStringProvider,
    val language: String,
    val config: RemoteConfig?,
) {
    fun resolve(key: String): AnnotatedString = provider.resolve(key, language, config).toAnnotatedString()

    fun resolvePlain(key: String): String = provider.resolveRaw(key, language, config)

    /** Named-placeholder substitution (§7 `{count}`/`{n}`/…) applied to the resolved raw string,
     * then re-parsed for markup — placeholders are always plain values (session IDs, counters),
     * never markup themselves. */
    fun resolve(key: String, placeholders: Map<String, String>): AnnotatedString {
        var raw = resolvePlain(key)
        placeholders.forEach { (name, value) -> raw = raw.replace("{$name}", value) }
        // Parsed after substitution so markup around a placeholder still renders; RichTextParser
        // remains the single markup implementation (no tag-stripping duplicated here).
        return RichTextParser.parse(raw).toAnnotatedString()
    }
}
