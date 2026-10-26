package com.anisequel.domain.tier

/**
 * Writes a [TierListLayout] out as a standalone SVG document.
 *
 * ## Why this is the lossless export
 *
 * The layout is already geometry - the same numbers Compose drew - so the output
 * is not a picture of the screen. Every glyph is still text, every rule is still a
 * rectangle, and the whole document is resolution-independent: printed on a
 * wallpaper or a business card it is exactly as sharp as on a phone. That is the
 * difference from a screenshot, where the text became pixels the first time it
 * was captured and blurs from there on.
 *
 * ## Posters are embedded, not linked
 *
 * An SVG may reference a poster as `href="https://..."`, which costs nothing -
 * but browsers and Inkscape will fetch it and a good many other viewers will not,
 * or will block it outright as a privacy control. A tier list whose value is its
 * posters then opens as [n] empty tiles in whatever the reader happens to be
 * using. So the bytes go in the file: self-contained, correct everywhere, at the
 * cost of roughly 40-60KB per poster.
 *
 * A poster the exporter could not fetch is drawn as its tile fill with the
 * caption still legible, rather than leaving a hole.
 */
object TierListSvg {

    /**
     * The colours an exporter draws with.
     *
     * Passed in rather than read from the Compose theme: this layer has no Android
     * dependencies on purpose, so the on-screen palette and an exported palette
     * are chosen by the caller and can differ - an export on a dark phone is
     * quite reasonably a light document.
     */
    data class Palette(
        val background: Long = 0xFF14110F,
        // No `.toLong()` on these three. A hex literal above `Int.MAX_VALUE` is
        // already a `Long`, so the call was a no-op the compiler warned about on
        // every build (`Redundant call of conversion method`). The rest are typed
        // by the `Long` parameter they are assigned to - `tileStroke` is the only
        // one small enough to be an `Int` literal, which is why it never carried
        // a conversion to begin with.
        val headingText: Long = 0xFFFFFFFF,
        val subheadingText: Long = 0xB3FFFFFF,
        val labelText: Long = 0xFFFFFFFF,
        val captionText: Long = 0xE6FFFFFF,
        val tileFill: Long = 0xFF2A2522,
        val tileStroke: Long = 0x33FFFFFF
    )

    /**
     * Renders [layout] as an SVG document.
     *
     * [embeddedPosters] maps media id to a ready-to-use `data:` URI - the
     * `"data:image/jpeg;base64,..."` form. Taking it already assembled keeps base64
     * encoding out of this layer, where it would be untestable and where the
     * caller already has the bytes in hand.
     *
     * [display] is the board's own title preference. It used to be absent, which
     * meant this renderer always drew a caption under every poster whether the
     * board showed names, put them on the artwork, or asked for the short form -
     * the one exporter that silently disagreed with the screen it mirrored.
     */
    fun write(
        layout: TierListLayout,
        tiers: List<TierDefinition>,
        candidates: List<TierCandidate>,
        embeddedPosters: Map<Int, String> = emptyMap(),
        palette: Palette = Palette(),
        display: TierDisplay = TierDisplay()
    ): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
        append("xmlns:xlink=\"http://www.w3.org/1999/xlink\" ")
        append("width=\"").append(num(layout.width)).append("\" ")
        append("height=\"").append(num(layout.height)).append("\" ")
        append("viewBox=\"0 0 ").append(num(layout.width)).append(' ').append(num(layout.height)).append("\">\n")

        append("  <rect width=\"100%\" height=\"100%\"").color("fill", palette.background).append("/>\n")

        // Heading. The title is the one text in the document whose length nobody
        // chose, so it is clipped to the page rather than allowed to widen it.
        // The comment here always claimed this; the writer then emitted the raw
        // string with no clip at all, so a long list name ran off the right edge
        // and was cut by the viewport instead of by the document.
        //
        // A clip rather than an ellipsis because this layer has no text metrics:
        // measuring a string is an Android API, and this file is deliberately
        // free of Android imports. A clip is also what a vector document should
        // do - the full title is still in the file for a search or a screen
        // reader, and a viewer that wants to reflow it can.
        append("  <defs>\n")
        append("    <clipPath id=\"heading-clip\">\n")
        append("      <rect x=\"").append(num(PADDING)).append("\" y=\"").append(num(PADDING - 12f))
            .append("\" width=\"").append(num((layout.width - PADDING * 2f).coerceAtLeast(1f)))
            .append("\" height=\"84\"/>\n")
        append("    </clipPath>\n")
        append("  </defs>\n")
        append("  <g clip-path=\"url(#heading-clip)\">\n")

        append("    <text x=\"").append(num(PADDING)).append("\" y=\"").append(num(PADDING + 34f))
            .append("\" font-family=\"system-ui,-apple-system,Segoe UI,Roboto,sans-serif\" ")
            .append("font-size=\"26\" font-weight=\"700\"").color("fill", palette.headingText)
            .append(" xml:space=\"preserve\">").append(escape(layout.title)).append("</text>\n")

        layout.subtitle?.let { subtitle ->
            append("    <text x=\"").append(num(PADDING)).append("\" y=\"").append(num(PADDING + 58f))
                .append("\" font-family=\"system-ui,-apple-system,Segoe UI,Roboto,sans-serif\" ")
                .append("font-size=\"14\"").color("fill", palette.subheadingText)
                .append(">").append(escape(subtitle)).append("</text>\n")
        }
        append("  </g>\n")

        val tierById = tiers.associateBy { it.id }
        // One map rather than `firstOrNull` per tile: a 300-title board did
        // 300 x 300 comparisons inside the builder, and the layout after it is
        // otherwise linear.
        val candidateById = candidates.associateBy { it.mediaId }

        layout.rows.forEachIndexed { index, row ->
            if (tierById[row.tierId] == null) {
                // A row whose tier has been deleted since the layout was computed.
                // Drawing it anyway would resurrect a tier the person removed a
                // second ago.
                return@forEachIndexed
            }
            append("  <g>\n")
            append("    <rect x=\"").append(num(row.x)).append("\" y=\"").append(num(row.y))
                .append("\" width=\"").append(num(row.width)).append("\" height=\"").append(num(row.height))
                .append("\" rx=\"10\"").color("fill", row.colorArgb, alpha = 0.92f).append("/>\n")

            // The number the row holds, under its label - the same pair the board
            // and the PNG exporter draw. It was missing here, so the one export
            // that is supposed to be the lossless copy of the screen was also the
            // one with no answer to "how many are in S".
            //
            // Counted geometrically, from the layout, rather than from the
            // assignment: the caller may have handed over a filtered assignment,
            // and the layout is the only thing that knows what is actually in the
            // document being written - the same rule `drawRows` uses for the PNG.
            val count = layout.items.count { it.y >= row.y && it.bottom <= row.bottom }

            // The label is clipped to its gutter for the same reason the title is
            // clipped to the page: a tier can be renamed to 24 characters, which
            // does not fit an 84-unit gutter at 20pt. The PNG exporter ellipsizes
            // against the gutter; this renderer cannot measure, so it cuts at the
            // same edge instead of letting the name spill over the posters.
            //
            // Keyed by row position, not by tier id: an id round-tripped from
            // storage is arbitrary text and need not be a legal XML id, while the
            // index is unique and always numeric-safe.
            val labelClip = "row-$index"
            append("    <clipPath id=\"").append(labelClip).append("\">\n")
            append("      <rect x=\"").append(num(row.x + LABEL_INSET))
                .append("\" y=\"").append(num(row.y))
                .append("\" width=\"").append(num(LABEL_GUTTER - LABEL_INSET * 2f))
                .append("\" height=\"").append(num(row.height)).append("\"/>\n")
            append("    </clipPath>\n")
            append("    <g clip-path=\"url(#").append(labelClip).append(")\">\n")

            // The label sits in the gutter and is vertically centred on the row,
            // which is what makes a short row and a tall row read as the same
            // control. With a count under it the pair is centred together; a
            // single baseline for both cases would leave the label high in an
            // empty row and the count half outside a full one.
            //
            // The two offsets are the PNG exporter's own: `drawRows` puts the
            // label at `centreY - 2` with a count and `centreY + 7` without, so
            // the same list exported either way does not shift its letters by a
            // couple of units when the formats are compared side by side.
            val centerY = row.y + row.height / 2f
            val labelY = centerY + if (count > 0) LABEL_WITH_COUNT_SHIFT else LABEL_WITHOUT_COUNT_SHIFT
            append("      <text x=\"").append(num(row.x + LABEL_GUTTER / 2f)).append("\" y=\"").append(num(labelY))
                .append("\" font-family=\"system-ui,-apple-system,Segoe UI,Roboto,sans-serif\" ")
                .append("font-size=\"").append(LABEL_FONT_SIZE).append("\" font-weight=\"700\" ")
                .append("text-anchor=\"middle\"").color("fill", palette.labelText)
                .append(">").append(escape(row.label)).append("</text>\n")
            if (count > 0) {
                append("      <text x=\"").append(num(row.x + LABEL_GUTTER / 2f))
                    .append("\" y=\"").append(num(labelY + LABEL_COUNT_LINE_HEIGHT))
                    .append("\" font-family=\"system-ui,-apple-system,Segoe UI,Roboto,sans-serif\" ")
                    .append("font-size=\"11\" text-anchor=\"middle\"")
                    .color("fill", palette.labelText, alpha = 0.82f)
                    .append(">").append(count).append("</text>\n")
            }
            append("    </g>\n")
            append("  </g>\n")
        }

        layout.items.forEach { box ->
            val candidate = candidateById[box.mediaId]
            // Read from the layout, never re-derived. `box.height` is poster
            // *plus caption*, so multiplying it by 1.5 inflated every poster by
            // 86% and dropped the caption baseline inside the artwork.
            val posterHeightForBox = box.posterHeight
            // The same fraction the Compose board and the PNG exporter use. A
            // literal 8 here was a fifth of a 40-unit tile and a twelfth of a
            // 96-unit one, so the SVG's covers were visibly rounder than the
            // board's at every size but the default.
            val corner = tierTileCornerRadius(box.width)
            // Defined once per tile and used by both the artwork and the
            // on-cover scrim. The id is prefixed because an XML id cannot start
            // with a digit.
            val clipId = "tile-${box.mediaId}"

            append("  <g>\n")
            append("    <clipPath id=\"").append(clipId).append("\">\n")
            append("      <rect x=\"").append(num(box.x)).append("\" y=\"").append(num(box.y))
                .append("\" width=\"").append(num(box.width)).append("\" height=\"")
                .append(num(posterHeightForBox)).append("\" rx=\"").append(num(corner)).append("\"/>\n")
            append("    </clipPath>\n")
            append("    <rect x=\"").append(num(box.x)).append("\" y=\"").append(num(box.y))
                .append("\" width=\"").append(num(box.width)).append("\" height=\"").append(num(posterHeightForBox))
                .append("\" rx=\"").append(num(corner)).append("\"")
                .color("fill", palette.tileFill).append("/>\n")

            embeddedPosters[box.mediaId]?.let { dataUri ->
                // `<image>` has no corner radius, so the artwork used to cover the
                // rounded rect underneath with square corners - the SVG was the
                // only renderer whose covers were not rounded at all. Clipping to
                // the same rect is what makes the three renderers agree.
                append("    <image x=\"").append(num(box.x)).append("\" y=\"").append(num(box.y))
                    .append("\" width=\"").append(num(box.width)).append("\" height=\"").append(num(posterHeightForBox))
                    .append("\" preserveAspectRatio=\"xMidYMid slice\" ")
                    .append("clip-path=\"url(#").append(clipId).append(")\" ")
                    .append("href=\"").append(escape(dataUri)).append("\"/>\n")
            }

            // A hairline over the artwork edge, drawn after the image exactly as
            // the PNG exporter draws it. Without it a poster that happens to be
            // the same value as the tile fill dissolves into the board, and every
            // cover loses the defined edge the screen gives it.
            append("    <rect x=\"").append(num(box.x)).append("\" y=\"").append(num(box.y))
                .append("\" width=\"").append(num(box.width)).append("\" height=\"").append(num(posterHeightForBox))
                .append("\" rx=\"").append(num(corner)).append("\" fill=\"none\" stroke-width=\"1\"")
                .color("stroke", palette.tileStroke).append("/>\n")

            if (display.showTitles && candidate != null) {
                val caption = escape(tierCaption(candidate.title, display.titleLength))
                when (display.titlePosition) {
                    TierTitlePosition.UNDER_COVER -> {
                        append("    <text x=\"").append(num(box.x + box.width / 2f))
                            .append("\" y=\"").append(num(box.y + posterHeightForBox + 18f))
                            .append("\" font-family=\"system-ui,-apple-system,Segoe UI,Roboto,sans-serif\" ")
                            .append("font-size=\"11\" text-anchor=\"middle\"")
                            .color("fill", palette.captionText)
                            .append(">").append(caption).append("</text>\n")
                    }
                    TierTitlePosition.ON_COVER -> {
                        // A scrim rather than a solid band, matching the board's
                        // gradient closely enough for a vector file: a flat
                        // strip across the top of every cover reads as a table.
                        append("    <rect x=\"").append(num(box.x)).append("\" y=\"").append(num(box.y))
                            .append("\" width=\"").append(num(box.width)).append("\" height=\"28\"")
                            .append(" fill=\"#000000\" fill-opacity=\"0.55\"")
                            .append(" clip-path=\"url(#").append(clipId).append(")\"/>\n")
                        append("    <text x=\"").append(num(box.x + box.width / 2f))
                            .append("\" y=\"").append(num(box.y + 15f))
                            .append("\" font-family=\"system-ui,-apple-system,Segoe UI,Roboto,sans-serif\" ")
                            .append("font-size=\"11\" text-anchor=\"middle\"")
                            .color("fill", palette.labelText)
                            .append(">").append(caption).append("</text>\n")
                    }
                }
            }
            append("  </g>\n")
        }

        append("</svg>\n")
    }

    private const val PADDING = 24f
    private const val LABEL_GUTTER = 84f
    private const val LABEL_FONT_SIZE = 20

    /** How far the label moves up when a count sits under it. */
    private const val LABEL_WITH_COUNT_SHIFT = -2f

    /** The label's baseline when it is alone in the gutter, matching the PNG. */
    private const val LABEL_WITHOUT_COUNT_SHIFT = 7f

    /**
     * The count's baseline, relative to the label's.
     *
     * 17 rather than the PNG's `centreY + 15` because the label above it already
     * moved up by 2; the two renderers agree on where the count lands, which is
     * the number a reader compares.
     */
    private const val LABEL_COUNT_LINE_HEIGHT = 17f

    /** Breathing room between the label text and the gutter's own edges. */
    private const val LABEL_INSET = 2f

    /**
     * Escapes text for XML character data and attribute values.
     *
     * Not optional. An AniList title containing `&` or `<` - and there are many,
     * because of `&` in titles like "Bungo Stray Dogs" and every `<something>`
     * convention in a title - produces a document that is not well-formed, and a
     * renderer will show nothing at all rather than the part that was valid.
     */
    fun escape(value: String): String {
        val out = StringBuilder(value.length + 16)
        value.forEach { ch ->
            when (ch) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&apos;")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /**
     * Formats a number for SVG output.
     *
     * `Locale.ROOT` on purpose: a locale that writes decimals with a comma puts a
     * comma inside an attribute value, and every conforming XML parser rejects
     * the document. A file that opens in the country it was made and nowhere else
     * is not an export.
     */
    private fun num(value: Float): String =
        if (value == value.toInt().toFloat()) {
            value.toInt().toString()
        } else {
            String.format(java.util.Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
        }

    /**
     * Appends `property="#RRGGBB"`, plus a matching `-opacity` when [argb] is not
     * opaque.
     *
     * ## The byte order was wrong, and it was silent
     *
     * This used to emit Android's layout - `#AARRGGBB` - for any translucent
     * colour. SVG 2 and CSS Color 4 read eight hex digits as `#RRGGBBAA`, so the
     * alpha byte landed in the red channel: `0xB3FFFFFF` (white at 70%) became
     * `#B3FFFFFF`, which every renderer reads as opaque pale cyan. The subtitle,
     * the captions and the tile stroke all came out mint instead of white, and
     * no renderer could call it malformed because the string itself was valid.
     *
     * Splitting the alpha into the property's own `-opacity` attribute avoids
     * depending on 8-digit hex support at all - older SVG 1.1 viewers accepted
     * `fill-opacity` long before they accepted `#RRGGBBAA`. [alpha] multiplies
     * the colour's own alpha, which is how a row keeps its 0.92 wash even when
     * its tier colour is itself translucent.
     */
    private fun StringBuilder.color(
        property: String,
        argb: Long,
        alpha: Float = 1f
    ): StringBuilder {
        append(' ').append(property).append("=\"")
            .append(String.format(java.util.Locale.ROOT, "#%06X", argb and 0xFFFFFF))
            .append('"')
        val opacity = ((argb ushr 24) and 0xFF).toFloat() / 255f * alpha
        if (opacity < 0.999f) {
            append(' ').append(property).append("-opacity=\"")
                .append(num(opacity.coerceIn(0f, 1f)))
                .append('"')
        }
        return this
    }

}