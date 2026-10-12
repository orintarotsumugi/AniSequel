package com.example.domain.tier

/**
 * The geometry of a tier list, computed once and drawn three ways.
 *
 * Compose, the SVG writer and the PDF writer all read this and nothing else. A
 * separate arrangement in each renderer is how an exported PNG ends up with the
 * labels in a different order from the screen it was exported from, which is the
 * kind of bug nobody reports because the file "looks fine".
 */

/**
 * Where one item's tile sits, in output units.
 *
 * [posterHeight] is carried separately from [height] because the two are
 * different things and re-deriving one from the other is how a renderer gets it
 * wrong. [height] is the poster *plus its caption*.
 *
 * The SVG writer previously recovered the poster by multiplying [height] by 1.5,
 * which inflated every poster by 86% at the default options and put the caption
 * baseline inside the artwork. Nothing about a multiply can recover a sum, so the
 * number the layout already knew is now handed over directly.
 */
data class TierItemBox(
    val mediaId: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val posterHeight: Float
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
}

/** Where one tier's row sits, and how tall it grew. */
data class TierRowBox(
    val tierId: String,
    val label: String,
    val colorArgb: Long,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    // The same two derived edges as [TierItemBox], and for the same reason: the
    // tests assert that rows stack without overlapping, which is a question
    // about an edge rather than about y and height separately.
    val right: Float get() = x + width
    val bottom: Float get() = y + height
}

/** A whole laid-out list. */
data class TierListLayout(
    val width: Float,
    val height: Float,
    val title: String,
    val subtitle: String?,
    val rows: List<TierRowBox>,
    val items: List<TierItemBox>,
    /** media id to the box it was drawn into, for hit testing and drag previews. */
    val boxByMediaId: Map<Int, TierItemBox> = emptyMap()
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Knobs for the arrangement. Defaults are the on-screen sizes; the exporters
 * scale them rather than re-deriving a different shape.
 */
data class TierLayoutOptions(
    /** Full width available. */
    val width: Float = 1080f,
    /** Width of the label gutter down the left. */
    val labelGutter: Float = 84f,
    /** Padding inside the whole drawing. */
    val padding: Float = 24f,
    /** One item tile's width. Height follows the cover's 2:3 plus its caption. */
    val itemWidth: Float = 72f,
    val itemGap: Float = 8f,
    val rowGap: Float = 10f,
    /** Height reserved under each tile for the title. */
    val captionHeight: Float = 26f,
    /** Height of the heading, above the first row. */
    val headerHeight: Float = 76f,
    /** Shortest a row may be, when it holds nothing. Empty rows still have to read as rows. */
    val minRowHeight: Float = 56f,
    /**
     * Floor for the whole drawing, whatever the rows add up to.
     *
     * A list with no tiers, or one with a single short row, would otherwise lay
     * out to a few dozen units of height - and an exporter hands that to a
     * viewer as a sliver, or as `0x0` when there is nothing at all.
     */
    val minCanvasHeight: Float = 480f
) {
    /** AniList covers are 2:3, so the poster is one and a half times its width tall. */
    val posterHeight: Float get() = itemWidth * 1.5f
}

/**
 * Arranges tiers into rows of items, wrapping inside the available width.
 *
 * Rows grow to fit their tallest line rather than to a fixed height. That is the
 * difference between a list that looks right at 40 entries and one that either
 * wastes half its height or clips its last row.
 */
fun layoutTierList(
    tiers: List<TierDefinition>,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    title: String,
    subtitle: String? = null,
    options: TierLayoutOptions = TierLayoutOptions()
): TierListLayout {
    val o = options
    val rowWidth = o.width - o.padding * 2f
    val itemHeight = o.posterHeight + o.captionHeight
    val itemsPerRow = maxOf(
        1,
        ((rowWidth - o.labelGutter + o.itemGap) / (o.itemWidth + o.itemGap)).toInt()
    )

    val candidateById = candidates.associateBy { it.mediaId }
    val rows = mutableListOf<TierRowBox>()
    val items = mutableListOf<TierItemBox>()

    var cursorY = o.padding + o.headerHeight

    tiers.forEach { tier ->
        val members = assignment.membersOf(tier.id)
        val lines = maxOf(1, ceilDiv(members.size, itemsPerRow))
        val rowHeight = maxOf(o.minRowHeight, lines * itemHeight + (lines - 1) * o.itemGap)

        rows += TierRowBox(
            tierId = tier.id,
            label = tier.label,
            colorArgb = tier.colorArgb,
            x = o.padding,
            y = cursorY,
            width = rowWidth,
            height = rowHeight
        )

        members.forEachIndexed { index, mediaId ->
            val line = index / itemsPerRow
            val column = index % itemsPerRow
            val x = o.padding + o.labelGutter + column * (o.itemWidth + o.itemGap)
            // Each line is top-aligned inside the row, so a short last line does
            // not leave its tiles floating against the row's bottom edge.
            val y = cursorY + line * (itemHeight + o.itemGap)
            items += TierItemBox(
                mediaId = mediaId,
                x = x,
                y = y,
                width = o.itemWidth,
                height = itemHeight,
                posterHeight = o.posterHeight
            )
        }

        cursorY += rowHeight + o.rowGap
    }

    val totalHeight = if (tiers.isEmpty()) o.minCanvasHeight else cursorY - o.rowGap + o.padding
    // A list with no tiers still has to produce a page-sized drawing rather than
    // a zero-height one, or an exporter hands the file to a viewer as 0x0.
    val height = maxOf(totalHeight, o.minCanvasHeight)

    return TierListLayout(
        width = o.width,
        height = height,
        title = title,
        subtitle = subtitle,
        rows = rows,
        items = items,
        boxByMediaId = items.associateBy { it.mediaId }
    )
}

/** Integer ceiling division that does not lose a remainder of zero. */
private fun ceilDiv(value: Int, divisor: Int): Int =
    if (value <= 0) 0 else (value + divisor - 1) / divisor

/** The cover URL for a box, for renderers that draw one. */
fun TierListLayout.coverUrlFor(mediaId: Int, candidates: List<TierCandidate>): String? =
    candidates.firstOrNull { it.mediaId == mediaId }?.coverUrl