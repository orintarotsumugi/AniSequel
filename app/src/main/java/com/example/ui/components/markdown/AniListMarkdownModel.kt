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

/**
 * The media ids a raw Markdown document links to that a cover lookup could resolve.
 *
 * Parses and collects in one step, so a caller holding only the bio string does
 * not have to know that the ids live in the AST - and so the parse done for this
 * is the *same* parse the renderer will do, rather than a second one that could
 * disagree about what counts as a preview line.
 */
internal fun mediaPreviewIdsIn(markdown: String): List<Int> =
    mediaPreviewIdsIn(AniListMarkdownParser.parse(markdown))

/**
 * The media ids in a parsed document that a cover art lookup could resolve.
 *
 * ## Why this filters rather than passing everything through
 *
 * An AniList link can name six different things, and only two of them are media.
 * A character, staff member, studio or user id is a *different* id in a different
 * namespace - `anilist.co/studio/128893` is not the anime 128893 - so sending
 * those to a `Media(id:)` query would fetch an unrelated anime and put the wrong
 * poster on a studio card.
 *
 * The link's own pattern also accepts a slug as the id
 * (`anilist.co/anime/one-piece`), which is not numeric and would be asked for as
 * the integer 0 by any caller that did not check. Both checks live here rather
 * than at the call site, because every caller would otherwise have to remember
 * them, and forgetting produces a confidently wrong poster.
 *
 * Distinct and in document order, so a bio that links the same anime twice asks
 * for it once.
 */
internal fun mediaPreviewIdsIn(nodes: List<MarkdownNode>): List<Int> {
    val ids = LinkedHashSet<Int>()
    fun walk(node: MarkdownNode) {
        when (node) {
            is MarkdownNode.AniListPreviewBlock -> node.mediaIdOrNull()?.let(ids::add)
            // Only these two nest. Walking into every other node type would be
            // dead code today and a source of surprises the day another gains a
            // `children` field.
            is MarkdownNode.CenteredBlock -> node.children.forEach(::walk)
            is MarkdownNode.Header,
            is MarkdownNode.Paragraph,
            is MarkdownNode.Blockquote,
            is MarkdownNode.CodeBlock,
            is MarkdownNode.ListBlock,
            is MarkdownNode.CheckListBlock,
            is MarkdownNode.ImageBlock,
            is MarkdownNode.VideoBlock,
            is MarkdownNode.SpoilerBlock,
            is MarkdownNode.TableBlock,
            MarkdownNode.Divider -> Unit
        }
    }
    nodes.forEach(::walk)
    return ids.toList()
}

/**
 * This link's media id, or null when it does not name media.
 *
 * The single definition of "is this card a media card", shared by the batch
 * collector and the renderer that looks the cover up. They have to agree: the
 * collector decides which ids to request, and the renderer decides which cards
 * to look them up for, and two definitions would mean either fetching covers no
 * card asks for or drawing a blank on cards that have one.
 */
internal fun MarkdownNode.AniListPreviewBlock.mediaIdOrNull(): Int? {
    val type = type.lowercase()
    if (type != "anime" && type != "manga") return null
    return id.toIntOrNull()?.takeIf { it > 0 }
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
