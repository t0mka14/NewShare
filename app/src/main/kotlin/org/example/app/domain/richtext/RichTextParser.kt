package org.example.app.domain.richtext

/**
 * Parses `<bold>`/`<italic>` markup (nesting allowed) into a [RichText] tree (§7, §6.2).
 * These tags are the only supported markup; the original app's `_b`/`/b` markers are not
 * recognized and pass through as literal text (§13 decision 26).
 */
object RichTextParser {
    private val tagRegex = Regex("</?(bold|italic)>")
    private val placeholderRegex = Regex("\\{(\\w+)\\}")

    fun parse(raw: String): RichText {
        val (nodes, _) = parseUntil(raw, 0, null)
        return RichText(nodes)
    }

    /**
     * Substitutes named placeholders (`{vowel}`, `{length}`, `{version}`, §7) using [values].
     * A placeholder with no matching key is left as-is (e.g. `{unknown}` stays literal) rather
     * than silently dropped, so a missing binding is visible instead of producing blank text.
     */
    fun substitutePlaceholders(richText: RichText, values: Map<String, String>): RichText =
        RichText(richText.nodes.map { substitutePlaceholders(it, values) })

    private fun substitutePlaceholders(node: RichTextNode, values: Map<String, String>): RichTextNode =
        when (node) {
            is RichTextNode.Text -> RichTextNode.Text(
                placeholderRegex.replace(node.text) { match -> values[match.groupValues[1]] ?: match.value }
            )

            is RichTextNode.Styled -> RichTextNode.Styled(
                node.style,
                node.children.map { substitutePlaceholders(it, values) },
            )
        }

    /**
     * Recursive-descent parse of [source] starting at [startIndex] until either the string
     * ends or a closing tag matching [closingStyle] is found (top-level call passes `null`,
     * which never matches, so stray/unbalanced closing tags at any depth are treated as
     * literal text rather than throwing — malformed markup degrades gracefully instead of
     * crashing the config load).
     */
    private fun parseUntil(source: String, startIndex: Int, closingStyle: RichTextStyle?): Pair<List<RichTextNode>, Int> {
        val nodes = mutableListOf<RichTextNode>()
        val textBuilder = StringBuilder()
        var index = startIndex

        fun flushText() {
            if (textBuilder.isNotEmpty()) {
                nodes += RichTextNode.Text(textBuilder.toString())
                textBuilder.clear()
            }
        }

        while (index < source.length) {
            val match = tagRegex.find(source, index)
            if (match == null) {
                textBuilder.append(source, index, source.length)
                index = source.length
                break
            }

            textBuilder.append(source, index, match.range.first)

            val isClosing = match.value[1] == '/'
            val style = RichTextStyle.valueOf(match.groupValues[1].uppercase())
            val afterTag = match.range.last + 1

            if (isClosing) {
                if (style == closingStyle) {
                    flushText()
                    return nodes to afterTag
                }
                // Unmatched closing tag in this scope: keep as literal text.
                textBuilder.append(match.value)
                index = afterTag
            } else {
                flushText()
                val (children, nextIndex) = parseUntil(source, afterTag, style)
                nodes += RichTextNode.Styled(style, children)
                index = nextIndex
            }
        }

        flushText()
        return nodes to index
    }
}
