package com.anisequel.ui.components.markdown

import com.anisequel.data.model.EntityPreviewKey
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
 *
 * Zero and a negative answer null too, rather than clamping to `0`: a width of
 * nothing is the one size that is not a degraded version of the request but its
 * opposite - an image laid out at 0dp is invisible, and the author finds that out
 * in the posted comment, where their own preview said it was there. `img0(...)` is
 * a mistake, and a mistake degrades to the same full width any other unreadable size
 * degrades to.
 */
internal fun clampedImageWidthDp(width: String?): Int? =
    width?.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(MAX_RICH_IMAGE_WIDTH_DP)

/**
 * The fraction of the column an `img50%(...)` asks for, or null.
 *
 * Same rule as [clampedImageWidthDp] on the other scale: a percent is a share of the
 * column, so a share of nothing, or a negative one, is not a narrow image - it is no
 * image - and answers null so the renderer falls back to the full width. A percent
 * over 100 clamps to the column rather than hanging off the side of it.
 */
internal fun clampedImageWidthPercent(width: String?): Float? {
    val percent = width?.takeIf { it.endsWith("%") }?.dropLast(1)?.toIntOrNull() ?: return null
    return percent.takeIf { it > 0 }?.coerceAtMost(100)?.div(100f)
}

/**
 * What a tap on [image] opens.
 *
 * AniList makes every image a link. A linked image - `[![cover](img)](target)` -
 * opens the target the author gave it, and a plain `img(url)` or `![alt](url)`
 * opens the picture itself. This app only wired the first form, so an image with
 * no explicit target was inert: no browser, no press feedback, no hint that it
 * could be pressed at all. The same comment on the site opened the picture, which
 * made the app look like it had dropped the link.
 *
 * The image's own URL is the fallback rather than nothing, because
 * [MarkdownNode.ImageBlock.url] is always an absolute `https` address after parsing
 * (the parser's `absoluteImageUrl` puts the scheme back on a protocol-relative
 * source) - it is drawn by Coil from that address already, so handing it to the
 * browser is the picture the reader is looking at rather than a file path or a
 * relative slug.
 */
internal fun imageTapUrl(image: MarkdownNode.ImageBlock): String =
    image.targetUrl ?: image.url

/**
 * Structural AST nodes produced by [AniListMarkdownParser].
 */
sealed interface MarkdownNode {
    data class Header(val level: Int, val inlines: List<InlineToken>) : MarkdownNode
    data class Paragraph(val inlines: List<InlineToken>, val centered: Boolean = false) : MarkdownNode

    /**
     * A `>` run.
     *
     * [inlines] is the flat form it always was, and [blocks] is the same content
     * re-parsed as blocks. The parser fills exactly one of the two: a quoted
     * sentence stays an annotated string, and a quote of a comment that carried an
     * image, an embed or a second paragraph becomes nested blocks, because an
     * annotated string has nowhere to put a bitmap and a single joined line has
     * nowhere to put a paragraph break.
     *
     * [AniListMarkdownParser] keeps the second form out of the case that does not
     * need it, so a plain quote is untouched by this field's existence.
     */
    data class Blockquote(
        val inlines: List<InlineToken>,
        val blocks: List<MarkdownNode> = emptyList()
    ) : MarkdownNode
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
    /**
     * A hidden block. [inlines] is what it always was - the text to show once the
     * lid is lifted - and [blocks] is the same content re-parsed as blocks, which
     * is only non-empty when that content holds something an annotated string
     * cannot carry: an image.
     *
     * Before this, `~!img(url)!~` and `~!![alt](x.gif)!~` opened to the literal
     * markup or the word "Image": the spoiler drew one `Text`, and a `Text` has
     * nowhere to put a bitmap, so the one thing a reader hides behind a spoiler on
     * AniList - a reveal, a colour palette, a character shot - was the one thing
     * the spoiler could not show.
     */
    data class SpoilerBlock(
        val inlines: List<InlineToken>,
        val blocks: List<MarkdownNode> = emptyList()
    ) : MarkdownNode
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
internal fun mediaCoverKeysIn(nodes: List<MarkdownNode>): List<MediaCoverKey> =
    previewKeysIn(nodes) { it.mediaCoverKeyOrNull() }

/**
 * The characters, staff and studios in a parsed document.
 *
 * The other half of the same preview cards: a media link is looked up for a
 * poster and a title, an entity link for a name and a portrait. Both come from
 * the same walk and the same batching idea, which is why the two collectors sit
 * beside each other - a host that renders one without the other used to get a
 * character card headed `Character #127222` next to correctly labelled titles.
 *
 * Distinct and in document order, so a bio that names the same character twice
 * asks once. Media, user and slug links are not here; see
 * [MarkdownNode.AniListPreviewBlock.entityPreviewKeyOrNull] for why.
 */
internal fun entityPreviewKeysIn(nodes: List<MarkdownNode>): List<EntityPreviewKey> =
    previewKeysIn(nodes) { it.entityPreviewKeyOrNull() }

/**
 * [keyOf] applied to every preview block in the document, deduplicated in order.
 *
 * One walk shared by both collectors, so "where can a preview block be" is
 * answered once. A second copy is how the two would drift the day a node type
 * gains a `children` field.
 */
private fun <K> previewKeysIn(
    nodes: List<MarkdownNode>,
    keyOf: (MarkdownNode.AniListPreviewBlock) -> K?
): List<K> {
    val keys = LinkedHashSet<K>()
    fun walk(node: MarkdownNode) {
        when (node) {
            is MarkdownNode.AniListPreviewBlock -> keyOf(node)?.let(keys::add)
            // Only these nest. Walking into every other node type would be dead
            // code today and a source of surprises the day another gains a
            // `children` field.
            is MarkdownNode.CenteredBlock -> node.children.forEach(::walk)
            // A quote or a revealed spoiler can hold a card of its own, and not
            // walking in is how the collector and the renderer disagreed about
            // which posters to fetch: the nested view draws the card, and a key
            // nobody collected is a card that reads its own title from the URL
            // with no artwork beside it.
            is MarkdownNode.Blockquote -> node.blocks.forEach(::walk)
            is MarkdownNode.SpoilerBlock -> node.blocks.forEach(::walk)
            is MarkdownNode.Header,
            is MarkdownNode.Paragraph,
            is MarkdownNode.CodeBlock,
            is MarkdownNode.ListBlock,
            is MarkdownNode.CheckListBlock,
            is MarkdownNode.ImageBlock,
            is MarkdownNode.VideoBlock,
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
internal fun InlineToken.nestedTokens(): List<InlineToken> = when (this) {
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

/**
 * This link's entity key, or null when it names something a batch cannot resolve.
 *
 * ## Why users are excluded
 *
 * A `/user/<name>` link carries a username rather than a numeric id, and there is
 * no batched name-and-avatar lookup for it that is worth a request; those cards
 * keep the label the author wrote and the profile icon. Sending a username to
 * `id_in: [Int]` would be a syntax-level failure for the whole document.
 *
 * ## Why a slug is excluded
 *
 * The link pattern accepts `anilist.co/studio/mappa` as well as
 * `anilist.co/studio/43`, but only the numeric form is an id. Asking for 0 - the
 * number a careless `toIntOrNull()` fallback would produce - is a confident wrong
 * portrait on the card, so a non-numeric id simply gets no lookup. The card still
 * draws, with the name parsed out of the slug.
 */
internal fun MarkdownNode.AniListPreviewBlock.entityPreviewKeyOrNull(): EntityPreviewKey? {
    val entityType = type.uppercase(Locale.ROOT)
    if (entityType !in EntityPreviewKey.SUPPORTED_TYPES) return null
    val entityId = id.toIntOrNull()?.takeIf { it > 0 } ?: return null
    return EntityPreviewKey(type = entityType, id = entityId)
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
     *
     * [width] is the size the author asked for, in the same spelling the block
     * images use: `220` is pixels, `50%` is a share of the column, and null is
     * "whatever the file is". It exists because AniList's own API writes inline
     * images with an HTML width - `<img width='220' src='…'>` - and the token had
     * nowhere to put it, so a bio of deliberately small strip images came out as
     * several full-column slabs. Defaulted, so every flat construction of this
     * token keeps working.
     */
    data class Image(
        val url: String,
        val alt: String? = null,
        val width: String? = null
    ) : InlineToken
}

/**
 * Whether these inline tokens carry an image a renderer could draw.
 *
 * Two callers answer this and they have to agree. The paragraph asks it to decide
 * between the flow (which embeds a real image) and the single clamped `Text` (which
 * cannot hold a bitmap at all, and so shows the image's alt text instead); the
 * profile's about card asks it to decide whether a bio may be collapsed - because a
 * collapsed bio is exactly that clamped text, and collapsing one that carries a
 * banner deleted the banner until the reader pressed "Show more".
 *
 * ## Every container is searched, not only the link
 *
 * `[![cover](x.gif)](https://…)` is one shape and it was the only one handled. The
 * site's editor also writes `**![banner](x.gif)**` - a GIF inside a bold run, in a
 * heading, underlined, struck through - and those tokens carry the image one level
 * down as well. Answering "no image" for them is what made the paragraph take its
 * clamped-text branch and print the word *banner* where the animation belongs.
 *
 * `@JvmName` because the document-level answer below has the same Kotlin name and
 * the same erased JVM signature - two `List` extensions are one method to the
 * bytecode, and the compiler calls that a platform declaration clash rather than
 * overloading them. The names stay the same for every Kotlin caller, which is the
 * point: the question is one question, asked of two different lists.
 */
@JvmName("inlineTokensContainEmbeddedImage")
internal fun List<InlineToken>.containsEmbeddedImage(): Boolean = any { token ->
    when (token) {
        is InlineToken.Image -> true
        else -> token.nestedTokens().containsEmbeddedImage()
    }
}

/**
 * Whether a parsed document holds an image anywhere in it.
 *
 * [containsEmbeddedImage] answers the question for one paragraph; this answers it
 * for a whole bio, and the difference is the bug: images that arrive as a *block* -
 * a `~~~` centred banner, a `~!…!~` spoiler's payload, a quoted comment, a centred
 * span's own children - are not in any paragraph's token list, so a caller that only
 * walked top-level paragraphs called a bio image-free and collapsed it, clipping the
 * banner the bio was written around.
 *
 * The walk mirrors the renderer's nesting exactly: [MarkdownNode.CenteredBlock],
 * [MarkdownNode.Blockquote] and [MarkdownNode.SpoilerBlock] hold children or a
 * re-parsed block list, and every other node's image lives in its inline tokens.
 * Same reason `previewKeysIn` walks these three - the collector and the renderer must
 * not disagree about where content can hide.
 */
internal fun List<MarkdownNode>.containsEmbeddedImage(): Boolean = any { node ->
    when (node) {
        is MarkdownNode.ImageBlock -> true
        is MarkdownNode.CenteredBlock -> node.children.containsEmbeddedImage()
        is MarkdownNode.Blockquote ->
            node.inlines.containsEmbeddedImage() || node.blocks.containsEmbeddedImage()
        is MarkdownNode.SpoilerBlock ->
            node.inlines.containsEmbeddedImage() || node.blocks.containsEmbeddedImage()
        is MarkdownNode.Paragraph -> node.inlines.containsEmbeddedImage()
        is MarkdownNode.Header -> node.inlines.containsEmbeddedImage()
        is MarkdownNode.ListBlock -> node.items.any { it.containsEmbeddedImage() }
        is MarkdownNode.CheckListBlock ->
            node.items.any { it.inlines.containsEmbeddedImage() }
        is MarkdownNode.TableBlock ->
            node.headers.any { it.containsEmbeddedImage() } ||
                node.rows.any { row -> row.any { it.containsEmbeddedImage() } }
        is MarkdownNode.CodeBlock,
        is MarkdownNode.VideoBlock,
        is MarkdownNode.AniListPreviewBlock,
        MarkdownNode.Divider -> false
    }
}
