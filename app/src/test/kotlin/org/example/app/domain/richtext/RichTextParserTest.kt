package org.example.app.domain.richtext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RichTextParserTest {

    @Test
    fun `plain text with no markup parses to a single text node`() {
        val result = RichTextParser.parse("Hello world")
        assertEquals(listOf(RichTextNode.Text("Hello world")), result.nodes)
    }

    @Test
    fun `parses a simple bold span`() {
        val result = RichTextParser.parse("Say <bold>Ahh</bold> now")
        assertEquals(
            listOf(
                RichTextNode.Text("Say "),
                RichTextNode.Styled(RichTextStyle.BOLD, listOf(RichTextNode.Text("Ahh"))),
                RichTextNode.Text(" now"),
            ),
            result.nodes,
        )
    }

    @Test
    fun `parses a simple italic span`() {
        val result = RichTextParser.parse("<italic>Note</italic>")
        assertEquals(
            listOf(RichTextNode.Styled(RichTextStyle.ITALIC, listOf(RichTextNode.Text("Note")))),
            result.nodes,
        )
    }

    @Test
    fun `parses nested bold within italic`() {
        val result = RichTextParser.parse("<italic>before <bold>inner</bold> after</italic>")
        val italic = result.nodes.single() as RichTextNode.Styled
        assertEquals(RichTextStyle.ITALIC, italic.style)
        assertEquals(
            listOf(
                RichTextNode.Text("before "),
                RichTextNode.Styled(RichTextStyle.BOLD, listOf(RichTextNode.Text("inner"))),
                RichTextNode.Text(" after"),
            ),
            italic.children,
        )
    }

    @Test
    fun `unterminated tag does not throw and captures remaining text`() {
        val result = RichTextParser.parse("<bold>never closed")
        val bold = result.nodes.single() as RichTextNode.Styled
        assertEquals(RichTextStyle.BOLD, bold.style)
        assertEquals(listOf(RichTextNode.Text("never closed")), bold.children)
    }

    @Test
    fun `stray closing tag is treated as literal text, not a crash`() {
        val result = RichTextParser.parse("oops </bold> text")
        assertEquals(listOf(RichTextNode.Text("oops </bold> text")), result.nodes)
    }

    @Test
    fun `original app's _b markers are not markup - they parse as literal text`() {
        // §13 decision 26: only <bold>/<italic> tags are supported; legacy conversion removed.
        val result = RichTextParser.parse("_bStart now/b please")
        assertEquals(listOf(RichTextNode.Text("_bStart now/b please")), result.nodes)
    }

    @Test
    fun `parsing nested tags produces the correct tree`() {
        val result = RichTextParser.parse("<italic>before <bold>inner</bold> after</italic>")
        val italic = result.nodes.single() as RichTextNode.Styled
        assertEquals(
            listOf(
                RichTextNode.Text("before "),
                RichTextNode.Styled(RichTextStyle.BOLD, listOf(RichTextNode.Text("inner"))),
                RichTextNode.Text(" after"),
            ),
            italic.children,
        )
    }

    @Test
    fun `substitutes named placeholders`() {
        val parsed = RichTextParser.parse("Say the vowel {vowel} for {length} seconds")
        val substituted = RichTextParser.substitutePlaceholders(parsed, mapOf("vowel" to "AAA", "length" to "10"))
        assertEquals(
            listOf(RichTextNode.Text("Say the vowel AAA for 10 seconds")),
            substituted.nodes,
        )
    }

    @Test
    fun `substitutes placeholders inside styled spans`() {
        val parsed = RichTextParser.parse("<bold>{version}</bold>")
        val substituted = RichTextParser.substitutePlaceholders(parsed, mapOf("version" to "2.0"))
        val bold = substituted.nodes.single() as RichTextNode.Styled
        assertEquals(listOf(RichTextNode.Text("2.0")), bold.children)
    }

    @Test
    fun `unresolved placeholder is left literal`() {
        val parsed = RichTextParser.parse("Hello {unknown}")
        val substituted = RichTextParser.substitutePlaceholders(parsed, emptyMap())
        assertEquals(listOf(RichTextNode.Text("Hello {unknown}")), substituted.nodes)
    }
}
