package com.anisequel.ui.components.novamarkdown

import com.anisequel.data.model.EntityPreviewKey
import com.anisequel.data.model.MediaCoverKey
import java.util.Locale
import kotlin.jvm.JvmName

/**
 * The largest an inline or block image may be drawn, in dp.
 *
 * AniList's sizing syntax accepts any integer - `img2147483647(url)` is valid
 * markup - and a malformed value overflows Compose's constraints during layout.
 * These caps are deliberately far above any phone: 3000dp is wider than a
 * tablet and 800dp is taller than a full-screen banner on one, so no real
 * artwork clips.
 */
const val NOVA_MAX_IMAGE_WIDTH_DP = 3000
const val NOVA_MAX_IMAGE_HEIGHT_DP = 800

/**
 * How many top-level blocks a collapsed document keeps.
 *
 * Public because the profile's About card has to decide whether to offer a
 * "Show more" control, and it can only answer from the same number the renderer
 * clips on.
 */
const val NOVA_MARKDOWN_COLLAPSED_BLOCK_LIMIT = 4

/**
 * Horizontal alignment, parsed from AniList's `~~~`, `<center>`, `align=""` and
 * `text-align:` forms.
 *
 * Alignment is a *property of the block*, not a wrapper node. Dartotsu's
 * renderer taught this the hard way: a centered container that only sets
 * `TextAlign` centers the text but leaves images and cards flush left, because
 * a `Text` cannot center the widget drawn beside it. Carrying alignment on
 * every block lets the image renderer, the card renderer and the paragraph
 * renderer each honour it, which is exactly the thing Dantotsu's
 * `<align center>` (not a real HTML element) never did.
 */
enum class NovaAlign { Start, Center, End, Justify }

/**
 * One structural block of an AniList Markdown document.
 *
 * The AST is deliberately renderer-agnostic: the parser, the cover-key
 * collector and the tests all read it, and Compose only appears in the files
 * that draw it.
 */
sealed interface NovaBlock {
    val align: NovaAlign get() = NovaAlign.Start

    data class Heading(
        val level: Int,
        val inlines: List<NovaInline>,
        override val align: NovaAlign = NovaAlign.Start
    ) : NovaBlock

    data class Paragraph(
        val inlines: List<NovaInline>,
        override val align: NovaAlign = NovaAlign.Start
    ) : NovaBlock

    /** A `>` run or an HTML `<blockquote>`, kept as blocks so nesting survives. */
    data class Quote(
        val blocks: List<NovaBlock>,
        override val align: NovaAlign = NovaAlign.Start
    ) : NovaBlock

    data class Code(
        val language: String?,
        val code: String,
        override val align: NovaAlign = NovaAlign.Start
    ) : NovaBlock

    /**
     * A bullet or numbered list. An item is itself a block list, which is what
     * lets a nested list, a paragraph and an image share one item without a
     * second dialect. [checked] is non-null for a `- [x]` task item.
     */
    data class BulletList(
        val ordered: Boolean,
        val start: Int,
        val items: List<NovaListItem>,
        override val align: NovaAlign = NovaAlign.Start
    ) : NovaBlock

    data class Table(
        val headers: List<NovaTableCell>,
        val rows: List<List<NovaTableCell>>,
        override val align: NovaAlign = NovaAlign.Start
    ) : NovaBlock

    data class Image(
        val url: String,
        val alt: String? = null,
        val targetUrl: String? = null,
        /** `220` is pixels, `50%` is a share of the column, null is the image's own size. */
        val width: String? = null,
        /** `60` is pixels; null keeps the file's own aspect ratio. */
        val height: String? = null,
        override val align: NovaAlign = NovaAlign.Start
    ) : NovaBlock

    data class Video(
        val url: String,
        val youtube: Boolean,
        override val align: NovaAlign = NovaAlign.Start
    ) : NovaBlock

    /**
     * A hidden block. The payload is parsed as a full document, so a heading, a
     * list or an image behind the lid reads like the rest of the bio.
     */
    data class Spoiler(
        val label: String?,
        val blocks: List<NovaBlock>,
        override val align: NovaAlign = NovaAlign.Start
    ) : NovaBlock

    data class Preview(
        val url: String,
        val type: String,
        val id: String,
        val title: String,
        override val align: NovaAlign = NovaAlign.Start
    ) : NovaBlock

    data object Rule : NovaBlock
}

data class NovaListItem(
    val blocks: List<NovaBlock>,
    val checked: Boolean? = null
)

data class NovaTableCell(
    val inlines: List<NovaInline>,
    val align: NovaAlign = NovaAlign.Start
)

/** The inline expression inside a heading, paragraph, list item or table cell. */
sealed interface NovaInline {
    data class Text(val text: String) : NovaInline
    data class Bold(val children: List<NovaInline>) : NovaInline
    data class Italic(val children: List<NovaInline>) : NovaInline
    data class BoldItalic(val children: List<NovaInline>) : NovaInline
    data class Underline(val children: List<NovaInline>) : NovaInline
    data class Strike(val children: List<NovaInline>) : NovaInline

    /** A backtick code span: its payload is literal, never parsed. */
    data class Code(val code: String) : NovaInline

    /** An HTML `<code>` run, where AniList renders markdown inside monospace. */
    data class Mono(val children: List<NovaInline>) : NovaInline

    data class Link(
        val url: String,
        val children: List<NovaInline>
    ) : NovaInline

    /**
     * A link to an AniList entity, kept apart so the renderer can draw a card
     * instead of an underlined title. [label] is already flattened to text.
     */
    data class MediaLink(
        val label: String,
        val url: String,
        val type: String,
        val id: String
    ) : NovaInline

    data class Mention(val username: String) : NovaInline

    data class Spoiler(
        /** Flat form, used for the obscured run's width and by simple readers. */
        val text: String,
        val children: List<NovaInline>,
        val label: String? = null
    ) : NovaInline

    data class Image(
        val url: String,
        val alt: String? = null,
        val width: String? = null,
        val height: String? = null
    ) : NovaInline

    /**
     * AniList's coloured-text trick: `<a>hello</a>` with no `href` is not a
     * link, it is text in the profile's accent colour.
     */
    data class Accent(val children: List<NovaInline>) : NovaInline
}

/** The dp width an `img220(...)` asks for, clamped; null for no absolute size. */
internal fun novaImageWidthDp(width: String?): Int? =
    width?.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(NOVA_MAX_IMAGE_WIDTH_DP)

/** The dp height an HTML `height="60"` asks for, clamped; null for no size. */
internal fun novaImageHeightDp(height: String?): Int? =
    height?.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(NOVA_MAX_IMAGE_HEIGHT_DP)

/** The fraction an `img50%(...)` asks for; null for no percentage size. */
internal fun novaImageWidthPercent(width: String?): Float? {
    val percent = width?.takeIf { it.endsWith("%") }?.dropLast(1)?.toIntOrNull()
        ?: return null
    return percent.takeIf { it > 0 }?.coerceAtMost(100)?.div(100f)
}

/** What a tap on [image] opens: the author's target, or the picture itself. */
internal fun novaImageTapUrl(image: NovaBlock.Image): String =
    image.targetUrl ?: image.url

/** `//host/file.svg` is protocol-relative; Coil and the WebView need a scheme. */
internal fun novaAbsoluteUrl(url: String): String =
    if (url.startsWith("//")) "https:$url" else url

/** An entity address may be written bare (`anilist.co/anime/1`); a tap needs https. */
internal fun novaEntityAbsoluteUrl(url: String): String = when {
    url.startsWith("http", ignoreCase = true) -> url
    url.startsWith("//") -> "https:$url"
    else -> "https://$url"
}

/**
 * The tree flattened to its characters.
 *
 * Used for link labels that become card headings and for a spoiler's obscured
 * run, which needs a length rather than a rendering.
 */
internal fun List<NovaInline>.novaPlainText(): String = joinToString("") { token ->
    when (token) {
        is NovaInline.Text -> token.text
        is NovaInline.Bold -> token.children.novaPlainText()
        is NovaInline.Italic -> token.children.novaPlainText()
        is NovaInline.BoldItalic -> token.children.novaPlainText()
        is NovaInline.Underline -> token.children.novaPlainText()
        is NovaInline.Strike -> token.children.novaPlainText()
        is NovaInline.Code -> token.code
        is NovaInline.Mono -> token.children.novaPlainText()
        is NovaInline.Link -> token.children.novaPlainText()
        is NovaInline.MediaLink -> token.label
        is NovaInline.Mention -> "@${token.username}"
        is NovaInline.Spoiler -> token.text
        is NovaInline.Image -> token.alt.orEmpty()
        is NovaInline.Accent -> token.children.novaPlainText()
    }
}

/**
 * The wrapper a container keeps after its children are rewritten.
 *
 * Null means "nothing is left inside" - a bold run that was only a media link
 * has no text of its own once the card is lifted out, and drawing the empty
 * wrapper leaves a blank line above the card.
 */
internal fun NovaInline.withChildren(children: List<NovaInline>): NovaInline? {
    if (children.isEmpty()) return null
    return when (this) {
        is NovaInline.Bold -> copy(children = children)
        is NovaInline.Italic -> copy(children = children)
        is NovaInline.BoldItalic -> copy(children = children)
        is NovaInline.Underline -> copy(children = children)
        is NovaInline.Strike -> copy(children = children)
        is NovaInline.Mono -> copy(children = children)
        is NovaInline.Link -> copy(children = children)
        is NovaInline.Accent -> copy(children = children)
        else -> this
    }
}

/** The children to descend into, empty for a leaf. */
internal fun NovaInline.nestedInlines(): List<NovaInline> = when (this) {
    is NovaInline.Bold -> children
    is NovaInline.Italic -> children
    is NovaInline.BoldItalic -> children
    is NovaInline.Underline -> children
    is NovaInline.Strike -> children
    is NovaInline.Mono -> children
    is NovaInline.Link -> children
    is NovaInline.Accent -> children
    is NovaInline.Spoiler -> children
    else -> emptyList()
}

/**
 * Splits a paragraph into the prose that stays and the AniList links that
 * become cards.
 *
 * Bios almost never write a bare AniList URL on its own line: the standard
 * shape is a labelled link inside a bold run,
 * `**#1 [Title](https://anilist.co/anime/1)**`. Lifting a card only at block
 * level therefore missed every card a reader actually sees, so the walk
 * descends into emphasis and link labels and then rebuilds the wrappers around
 * whatever text remains.
 *
 * Returns text tokens first, then the links in document order.
 */
internal fun splitNovaMediaLinks(
    inlines: List<NovaInline>
): Pair<List<NovaInline>, List<NovaInline.MediaLink>> {
    val kept = mutableListOf<NovaInline>()
    val links = mutableListOf<NovaInline.MediaLink>()

    inlines.forEach { token ->
        if (token is NovaInline.MediaLink) {
            links += token
            return@forEach
        }
        // A link behind a spoiler stays behind it. Lifting the card here would
        // draw it outside the lid and reveal the one thing the author hid.
        if (token is NovaInline.Spoiler) {
            kept += token
            return@forEach
        }
        val children = token.nestedInlines()
        if (children.isEmpty()) {
            kept += token
            return@forEach
        }
        val (remaining, found) = splitNovaMediaLinks(children)
        links += found
        if (found.isEmpty()) {
            kept += token
        } else {
            token.withChildren(remaining)?.let(kept::add)
        }
    }

    return kept to links
}

/** Whether these tokens carry an image a paragraph can draw. */
@JvmName("containsNovaImageInlines")
internal fun List<NovaInline>.containsNovaImage(): Boolean = any { token ->
    token is NovaInline.Image || token.nestedInlines().containsNovaImage()
}

/** Whether a document carries an image anywhere, including behind a lid. */
internal fun List<NovaBlock>.containsNovaImage(): Boolean = any { block ->
    when (block) {
        is NovaBlock.Image -> true
        is NovaBlock.Paragraph -> block.inlines.containsNovaImage()
        is NovaBlock.Heading -> block.inlines.containsNovaImage()
        is NovaBlock.Quote -> block.blocks.containsNovaImage()
        is NovaBlock.Spoiler -> block.blocks.containsNovaImage()
        is NovaBlock.BulletList -> block.items.any { it.blocks.containsNovaImage() }
        is NovaBlock.Table ->
            block.headers.any { it.inlines.containsNovaImage() } ||
                block.rows.any { row -> row.any { it.inlines.containsNovaImage() } }
        is NovaBlock.Code,
        is NovaBlock.Video,
        is NovaBlock.Preview,
        NovaBlock.Rule -> false
    }
}

/** The media keys a document links to, distinct and in reading order. */
internal fun novaMediaCoverKeysIn(blocks: List<NovaBlock>): List<MediaCoverKey> =
    novaPreviewKeysIn(blocks) { it.mediaCoverKeyOrNull() }

/** The character/staff/studio keys a document links to. */
internal fun novaEntityPreviewKeysIn(blocks: List<NovaBlock>): List<EntityPreviewKey> =
    novaPreviewKeysIn(blocks) { it.entityPreviewKeyOrNull() }

private fun <K> novaPreviewKeysIn(
    blocks: List<NovaBlock>,
    keyOf: (NovaBlock.Preview) -> K?
): List<K> {
    val keys = LinkedHashSet<K>()
    fun walkInline(tokens: List<NovaInline>) {
        tokens.forEach { token ->
            if (token is NovaInline.MediaLink) {
                keyOf(
                    NovaBlock.Preview(
                        url = token.url,
                        type = token.type,
                        id = token.id,
                        title = token.label
                    )
                )?.let(keys::add)
            } else {
                walkInline(token.nestedInlines())
            }
        }
    }
    fun walkBlocks(nodes: List<NovaBlock>) {
        nodes.forEach { block ->
            when (block) {
                is NovaBlock.Preview -> keyOf(block)?.let(keys::add)
                is NovaBlock.Quote -> walkBlocks(block.blocks)
                is NovaBlock.Spoiler -> walkBlocks(block.blocks)
                is NovaBlock.BulletList -> block.items.forEach { walkBlocks(it.blocks) }
                is NovaBlock.Paragraph -> walkInline(block.inlines)
                is NovaBlock.Heading -> walkInline(block.inlines)
                is NovaBlock.Table -> {
                    block.headers.forEach { walkInline(it.inlines) }
                    block.rows.forEach { row -> row.forEach { walkInline(it.inlines) } }
                }
                is NovaBlock.Image,
                is NovaBlock.Code,
                is NovaBlock.Video,
                NovaBlock.Rule -> Unit
            }
        }
    }
    walkBlocks(blocks)
    return keys.toList()
}

internal fun NovaBlock.Preview.mediaCoverKeyOrNull(): MediaCoverKey? {
    val mediaType = type.lowercase(Locale.ROOT)
    if (mediaType != "anime" && mediaType != "manga") return null
    val mediaId = id.toIntOrNull()?.takeIf { it > 0 } ?: return null
    return MediaCoverKey(type = mediaType.uppercase(Locale.ROOT), id = mediaId)
}

internal fun NovaBlock.Preview.entityPreviewKeyOrNull(): EntityPreviewKey? {
    val entityType = type.uppercase(Locale.ROOT)
    if (entityType !in EntityPreviewKey.SUPPORTED_TYPES) return null
    val entityId = id.toIntOrNull()?.takeIf { it > 0 } ?: return null
    return EntityPreviewKey(type = entityType, id = entityId)
}

/** The kind of SVG that needs a real browser rather than AndroidSVG. */
private val NOVA_SVG_URL = Regex(
    """(?i)\.svg(?:[?#].*)?$|[?&]format=svg(?:&.*)?$""",
)

internal fun isNovaSvgUrl(url: String): Boolean =
    NOVA_SVG_URL.containsMatchIn(url)
