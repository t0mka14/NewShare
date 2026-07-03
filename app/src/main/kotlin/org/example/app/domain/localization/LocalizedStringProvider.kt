package org.example.app.domain.localization

import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.richtext.RichText
import org.example.app.domain.richtext.RichTextParser
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Resolves `key -> RichText` for a language, layering the active config's `strings` over the
 * bundled built-in English fallback set (§7). Precedence (exact-match lookup only, no
 * `contains()`-style matching — fixes the original app's XPath bug):
 *
 * 1. `config.strings[language][key]`
 * 2. `config.strings[config.defaultLanguage][key]` (skipped if `language == defaultLanguage`,
 *    already covered by step 1)
 * 3. [BuiltinStrings.en]
 * 4. the key itself (logged at most once per key, per §7 — never a raw `"ERROR"`)
 *
 * Deliberately stateless with respect to *which* language/config is active: it takes both as
 * explicit parameters to [resolve] rather than observing
 * `AppSettingsRepository`/`ConfigurationRepository` itself (§12: no singletons holding state).
 * A UI-owned component combines its own language selection with
 * `ConfigurationRepository.activeConfig` (e.g. via `combine`) and calls [resolve] again on
 * either change — this class only needs to be constructed once and is safe to share, since it
 * holds no per-call state beyond the missing-key dedupe set.
 */
class LocalizedStringProvider(
    private val builtins: Map<String, String> = BuiltinStrings.en,
) {
    private val loggedMissingKeys = ConcurrentHashMap.newKeySet<String>()

    /** Resolves and parses [key] into a [RichText] tree (`<bold>`/`<italic>`, §7). Placeholder
     * substitution, if needed, is a separate step via [RichTextParser.substitutePlaceholders]. */
    fun resolve(key: String, language: String, config: RemoteConfig?): RichText =
        RichTextParser.parse(resolveRaw(key, language, config))

    /** Same precedence as [resolve] but returns the raw (unparsed) string. */
    fun resolveRaw(key: String, language: String, config: RemoteConfig?): String {
        config?.strings?.get(language)?.get(key)?.let { return it }

        val defaultLanguage = config?.defaultLanguage
        if (defaultLanguage != null && defaultLanguage != language) {
            config.strings[defaultLanguage]?.get(key)?.let { return it }
        }

        builtins[key]?.let { return it }

        if (loggedMissingKeys.add(key)) {
            logger.warn { "localization: no string for key '$key' (language=$language); falling back to the key itself" }
        }
        return key
    }
}
