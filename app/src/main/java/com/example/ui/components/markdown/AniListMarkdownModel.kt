package com.example.ui.components.markdown

/**
 * Structural AST nodes produced by [AniListMarkdownParser].
 */
sealed interface MarkdownNode {
    data class Header(val level: Int, val inlines: List<InlineToken>) : MarkdownNode
    data class Paragraph(val inlines: List<InlineToken>, val centered: Boolean = false) : MarkdownNode
    data class Blockquote(val inlines: List<InlineToken>) : MarkdownNode
    data class CodeBlock(val language: String?, val code: String) : MarkdownNode
    data class ListBlock(val ordered: Boolean, val items: List<List<InlineToken>>) : MarkdownNode
    data class CheckListBlock(val items: List<CheckListItem>) : MarkdownNode
    data class ImageBlock(
        val url: String,
        val alt: String? = null,
        val targetUrl: String? = null,
        val width: String? = null,
        val centered: Boolean = false
    ) : MarkdownNode
    data class VideoBlock(val url: String, val isYoutube: Boolean) : MarkdownNode
    data class SpoilerBlock(val inlines: List<InlineToken>) : MarkdownNode
    data class TableBlock(
        val headers: List<List<InlineToken>>,
        val rows: List<List<List<InlineToken>>>
    ) : MarkdownNode
    data class CenteredBlock(val children: List<MarkdownNode>) : MarkdownNode
    object Divider : MarkdownNode
}

data class CheckListItem(
    val checked: Boolean,
    val inlines: List<InlineToken>
)

/**
 * Inline style segments for rich text rendering.
 */
sealed interface InlineToken {
    data class Plain(val text: String) : InlineToken
    data class Bold(val text: String) : InlineToken
    data class Italic(val text: String) : InlineToken
    data class BoldItalic(val text: String) : InlineToken
    data class Underline(val text: String) : InlineToken
    data class Strikethrough(val text: String) : InlineToken
    data class Code(val code: String) : InlineToken
    data class Link(val label: String, val url: String) : InlineToken
    data class Mention(val username: String) : InlineToken
    data class Spoiler(val text: String) : InlineToken
}
