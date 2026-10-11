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
    data class AniListPreviewBlock(
        val url: String,
        val type: String,
        val id: String,
        val title: String
    ) : MarkdownNode
    object Divider : MarkdownNode
}

data class CheckListItem(
    val checked: Boolean,
    val inlines: List<InlineToken>
)

/**
 * Inline style segments for rich text rendering.
 *
 * ## Why the emphasis and link tokens carry [children]
 *
 * An emphasis run is not a string, it is a *sub-expression*. AniList bios nest
 * markup constantly - a bold line whose text is a link, an italic quote that
 * contains a bold word, a link whose label is itself bold:
 *
 * ```
 * **✨ [#1] [Hell's Paradise](https://anilist.co/anime/128893)**
 * ```
 *
 * Modelling an emphasis as `text: String` flattens that inner link into its own
 * source, so the renderer printed the raw `[Hell's Paradise](https://...)`
 * characters inside the bold run - which is exactly the bug the reference
 * screenshot shows. [children] holds the parsed inner expression instead, and the
 * renderer walks it under the same style. [text] is kept as the raw source for
 * the styles that need it (a spoiler's reveal, an image's fallback label) and as
 * the graceful degradation when the nesting depth cap is reached.
 *
 * Additive and defaulted, so every existing flat construction keeps working.
 */
sealed interface InlineToken {
    data class Plain(val text: String) : InlineToken
    data class Bold(val text: String, val children: List<InlineToken> = emptyList()) : InlineToken
    data class Italic(val text: String, val children: List<InlineToken> = emptyList()) : InlineToken
    data class BoldItalic(val text: String, val children: List<InlineToken> = emptyList()) : InlineToken
    data class Underline(val text: String, val children: List<InlineToken> = emptyList()) : InlineToken
    data class Strikethrough(val text: String, val children: List<InlineToken> = emptyList()) : InlineToken
    data class Code(val code: String) : InlineToken
    data class Link(
        val label: String,
        val url: String,
        val children: List<InlineToken> = emptyList()
    ) : InlineToken
    data class Mention(val username: String) : InlineToken
    data class Spoiler(val text: String) : InlineToken

    /**
     * An image or GIF embedded *inside* a paragraph, not on a line of its own.
     *
     * AniList bios are full of these: `Text ![alt](cat.gif) more text` and bare
     * `https://.../cat.gif` links between words. [RenderParagraph] puts a real
     * [androidx.compose.foundation.Image] into the flow for this token so a GIF
     * animates inline; everywhere a paragraph is not rendered as a flow (a
     * header, a list item, a truncated bio) it degrades to a plain link to [url],
     * which is what [buildInlineAnnotatedString] does with it.
     */
    data class Image(val url: String, val alt: String? = null) : InlineToken
}
