package org.example.app.ui.richtext

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.example.app.domain.richtext.RichText
import org.example.app.domain.richtext.RichTextNode
import org.example.app.domain.richtext.RichTextStyle

/**
 * Maps the UI-free [RichText] tree (§7 `<bold>`/`<italic>`, nesting allowed) produced by
 * `RichTextParser` into an [AnnotatedString] Compose can render. This is the "small
 * RichText→AnnotatedString mapper in ui/" — no parsing logic lives here, only tree-walking.
 */
fun RichText.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    nodes.forEach { appendNode(it) }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendNode(node: RichTextNode) {
    when (node) {
        is RichTextNode.Text -> append(node.text)
        is RichTextNode.Styled -> {
            val spanStyle = when (node.style) {
                RichTextStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                RichTextStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            }
            withStyle(spanStyle) {
                node.children.forEach { appendNode(it) }
            }
        }
    }
}
