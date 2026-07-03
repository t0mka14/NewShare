package org.example.app.domain.richtext

/**
 * UI-free rich text model (§7). Produced by [RichTextParser] from a localized string; the UI
 * layer walks the tree to build an `AnnotatedString` (nested [RichTextNode.Styled] spans
 * simply nest style spans in the renderer).
 */
enum class RichTextStyle { BOLD, ITALIC }

sealed interface RichTextNode {
    data class Text(val text: String) : RichTextNode
    data class Styled(val style: RichTextStyle, val children: List<RichTextNode>) : RichTextNode
}

data class RichText(val nodes: List<RichTextNode>) {
    companion object {
        val EMPTY = RichText(emptyList())
    }
}
