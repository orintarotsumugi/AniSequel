package com.anisequel.ui.components.markdown

import com.anisequel.data.model.MediaCoverKey
import java.util.Locale

/**
 * The largest a markdown image may be drawn, in dp.
 *
 * AniList's own image syntax carries a pixel size (`img2147483647(url)`), and a
 * malformed or hostile one overflows Compose's constraints during layout - a
 * crash on any public bio or synopsis that contains it, not a rendering quirk.
 * The caps are generous enough that no real artwork is clipped: 3000dp is wider
 * than any phone, and 800dp is taller than a full-screen image on one. The
 * height cap matters because the width alone does not bound a very tall image
 * whose aspect ratio is unknown until it decodes.
 */
const val MAX_RICH_IMAGE_WIDTH_DP = 3000
const val MAX_RICH_IMAGE_HEIGHT_DP = 800

/**
 * The dp width an `img220(...)` / `img50%(...)` size asks for, clamped.
 *
 * A percentage is handled by [MarkdownNode.ImageBlock]'s renderer, not here;
 * this answers only the absolute form. A malformed or
 * absurd number clamps to the ceiling instead of being passed into a layout
 * constraint, and a size-less image answers null (the renderer fills width).
 */
internal fun clampedImageWidthDp(width: String?): Int? =
    width?.toIntOrNull()?.coerceIn(0, MAX_RICH_IMAGE_WIDTH_DP)

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
 * The media a raw Markdown document links to that a cover lookup could resolve.
 *
 * Parses and collects in one step, so a caller holding only the bio string does
 * not have to know that the keys live in the AST - and so the parse done for this
 * is the *same* parse the renderer will do, rather than a second one that could
 * disagree about what counts as a preview line.
 */
internal fun mediaCoverKeysIn(markdown: String): List<MediaCoverKey> =
    mediaCoverKeysIn(AniListMarkdownParser.parse(markdown))

/**
 * The media in a parsed document that a cover art lookup could resolve.
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
 * ## Why the result is a set of *keys* and not a set of ids
 *
 * Because a bare id is not unique across media types - see [MediaCoverKey]. Two
 * links in one bio sharing an id across the anime and manga namespaces are two
 * different shows, and collapsing them here is what made both cards draw the same
 * poster.
 *
 * Distinct and in document order, so a bio that links the same anime twice asks
 * for it once.
 */
internal fun mediaCoverKeysIn(nodes: List<MarkdownNode>): List<MediaCoverKey> {
    val keys = LinkedHashSet<MediaCoverKey>()
    fun walk(node: MarkdownNode) {
        when (node) {
            is MarkdownNode.AniListPreviewBlock -> node.mediaCoverKeyOrNull()?.let(keys::add)
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
    return keys.toList()
}

/**
 * Splits a paragraph's inline tree into the text that stays and the AniList
 * links that become cards.
 *
 * A media link does not have to be a top-level token to be found: the commonest
 * bio shape wraps it in a bold run (`**✨ [#1] [Title](url)**`), so the walk
 * descends into every emphasis run and link label. An emphasis run left with no
 * children is dropped rather than kept as an empty wrapper, and the returned text
 * list keeps the original order so the surrounding sentence still reads.
 *
 * Returns the text tokens first and the extracted links second, in document
 * order. The caller emits the paragraph it is given and then one card per link,
 * which is the reading order the reference clients use.
 */
internal fun splitMediaLinks(
    inlines: List<InlineToken>
): Pair<List<InlineToken>, List<InlineToken.MediaLink>> {
    val kept = mutableListOf<InlineToken>()
    val links = mutableListOf<InlineToken.MediaLink>()

    inlines.forEach { token ->
        when (token) {
            is InlineToken.MediaLink -> links += token

            is InlineToken.Bold,
            is InlineToken.Italic,
            is InlineToken.BoldItalic,
            is InlineToken.Underline,
            is InlineToken.Strikethrough,
            is InlineToken.Link -> {
                val (children, found) = splitMediaLinks(token.nestedTokens())
                links += found
                if (found.isEmpty()) {
                    // Nothing was lifted out, so the original token is kept
                    // untouched. Rebuilding it would fill in `children` that the
                    // depth cap deliberately left empty, and would allocate a new
                    // token for every emphasis run in every bio for no gain.
                    kept += token
                } else {
                    token.withNested(children)?.let(kept::add)
                }
            }

            else -> kept += token
        }
    }

    return kept to links
}

/** The inner expression of an emphasis run or link label, empty for anything else. */
private fun InlineToken.nestedTokens(): List<InlineToken> = when (this) {
    is InlineToken.Bold -> children.ifEmpty { listOf(InlineToken.Plain(text)) }
    is InlineToken.Italic -> children.ifEmpty { listOf(InlineToken.Plain(text)) }
    is InlineToken.BoldItalic -> children.ifEmpty { listOf(InlineToken.Plain(text)) }
    is InlineToken.Underline -> children.ifEmpty { listOf(InlineToken.Plain(text)) }
    is InlineToken.Strikethrough -> children.ifEmpty { listOf(InlineToken.Plain(text)) }
    is InlineToken.Link -> children
    else -> emptyList()
}

/**
 * The same wrapper carrying [children], or null when nothing is left inside it.
 *
 * Null is the point: a bold run that was *entirely* a media link has no text of
 * its own once the link is lifted out, and emitting it would draw an empty
 * emphasis wrapper - a blank line where the card already says everything.
 */
private fun InlineToken.withNested(children: List<InlineToken>): InlineToken? {
    if (children.isEmpty()) return null
    return when (this) {
        is InlineToken.Bold -> copy(children = children)
        is InlineToken.Italic -> copy(children = children)
        is InlineToken.BoldItalic -> copy(children = children)
        is InlineToken.Underline -> copy(children = children)
        is InlineToken.Strikethrough -> copy(children = children)
        is InlineToken.Link -> copy(children = children)
        else -> this
    }
}

/**
 * This link's media key, or null when it does not name media.
 *
 * The single definition of "is this card a media card", shared by the batch
 * collector and the renderer that looks the cover up. They have to agree: the
 * collector decides which media to request, and the renderer decides which cards
 * to look them up for, and two definitions would mean either fetching covers no
 * card asks for or drawing a blank on cards that have one.
 *
 * Carries the type as well as the id, because that is what [MediaCoverKey] needs
 * to keep `anilist.co/anime/1` and `anilist.co/manga/1` apart.
 */
internal fun MarkdownNode.AniListPreviewBlock.mediaCoverKeyOrNull(): MediaCoverKey? {
    val type = type.lowercase()
    if (type != "anime" && type != "manga") return null
    val mediaId = id.toIntOrNull()?.takeIf { it > 0 } ?: return null
    return MediaCoverKey(type = type.uppercase(Locale.ROOT), id = mediaId)
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

    /**
     * A link to an AniList entity, kept apart from a plain [Link] so the renderer
     * can draw a cover card for it instead of an underlined title.
     *
     * ## Why this is its own token
     *
     * The block-level preview only ever matched a line that was *nothing but* a
     * bare AniList URL. Bios almost never write one that way - the standard
     * "hall of fame" entry is a labelled link inside a bold run:
     *
     * ```
     * **✨ [#1] [Hell's Paradise](https://anilist.co/anime/128893)**
     * ```
     *
     * That fell through to the paragraph path and rendered as plain text, so the
     * preview card existed for a shape of bio nobody writes. Recognising the link
     * wherever it appears in a line is what makes the card actually show up.
     *
     * [label] is the link's own text (the title a reader recognises) rather than a
     * slug guessed from the URL, which for `anime/128893` has nothing readable in
     * it at all.
     */
    data class MediaLink(
        val label: String,
        val url: String,
        val type: String,
        val id: String
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
