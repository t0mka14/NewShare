package org.example.app.domain.localization

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuiltinStringsTest {

    @Test
    fun `Czech defines exactly the English keys`() {
        assertEquals(BuiltinStrings.en.keys, BuiltinStrings.cs.keys)
    }

    @Test
    fun `Czech keeps every named placeholder of the English text`() {
        val placeholder = Regex("""\{[a-zA-Z]+}""")
        BuiltinStrings.en.forEach { (key, english) ->
            val expected = placeholder.findAll(english).map { it.value }.toSet()
            val actual = placeholder.findAll(BuiltinStrings.cs.getValue(key)).map { it.value }.toSet()
            assertEquals(expected, actual, key)
        }
    }

    @Test
    fun `byLanguage exposes both languages`() {
        assertTrue(BuiltinStrings.byLanguage.keys.containsAll(listOf("en", "cs")))
    }
}
