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
     */
    fun write(
        layout: TierListLayout,
        tiers: List<TierDefinition>,
        candidates: List<TierCandidate>,
        embeddedPosters: Map<Int, String> = emptyMap(),
        palette: Palette = Palette()
    ): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
        append("xmlns:xlink=\"http://www.w3.org/1999/xlink\" ")
        append("width=\"").append(num(layout.width)).append("\" ")
        append("height=\"").append(num(layout.height)).append("\" ")
        append("viewBox=\"0 0 ").append(num(layout.width)).append(' ').append(num(layout.height)).append("\">\n")

        append("  <rect width=\"100%\" height=\"100%\" fill=\"").append(hex(palette.background)).append("\"/>\n")

        // Heading. The title is the one text in the document that can be long
        // enough to run past the right edge, so it is clipped rather than allowed
        // to widen the page.
        append("  <text x=\"").append(num(PADDING)).append("\" y=\"").append(num(PADDING + 34f))
            .append("\" font-family=\"system-ui,-apple-system,Segoe UI,Roboto,sans-serif\" ")
            .append("font-size=\"26\" font-weight=\"700\" fill=\"").append(hex(palette.headingText))
            .append("\" xml:space=\"preserve\">").append(escape(layout.title)).append("</text>\n")

        layout.subtitle?.let { subtitle ->
            append("  <text x=\"").append(num(PADDING)).append("\" y=\"").append(num(PADDING + 58f))
                .append("\" font-family=\"system-ui,-apple-system,Segoe UI,Roboto,sans-serif\" ")
                .append("font-size=\"14\" fill=\"").append(hex(palette.subheadingText))
                .append("\">").append(escape(subtitle)).append("</text>\n")
        }

        val tierById = tiers.associateBy { it.id }

        layout.rows.forEach { row ->
            if (tierById[row.tierId] == null) {
                // A row whose tier has been deleted since the layout was computed.
                // Drawing it anyway would resurrect a tier the person removed a
                // second ago.
                return@forEach
            }
            append("  <g>\n")
            append("    <rect x=\"").append(num(row.x)).append("\" y=\"").append(num(row.y))
                .append("\" width=\"").append(num(row.width)).append("\" height=\"").append(num(row.height))
                .append("\" rx=\"10\" fill=\"").append(hex(row.colorArgb)).append("\" fill-opacity=\"0.92\"/>\n")

            // The label sits in the gutter and is vertically centred on the row,
            // which is what makes a short row and a tall row read as the same
            // control.
            val labelY = row.y + row.height / 2f + LABEL_FONT_SIZE * 0.36f
            append("    <text x=\"").append(num(row.x + LABEL_GUTTER / 2f)).append("\" y=\"").append(num(labelY))
                .append("\" font-family=\"system-ui,-apple-system,Segoe UI,Roboto,sans-serif\" ")
                .append("font-size=\"").append(LABEL_FONT_SIZE).append("\" font-weight=\"700\" ")
                .append("text-anchor=\"middle\" fill=\"").append(hex(palette.labelText))
                .append("\">").append(escape(row.label)).append("</text>\n")
            append("  </g>\n")
        }

        layout.items.forEach { box ->
            val candidate = candidates.firstOrNull { it.mediaId == box.mediaId }
            // Read from the layout, never re-derived. `box.height` is poster
            // *plus caption*, so multiplying it by 1.5 inflated every poster by
            // 86% and dropped the caption baseline inside the artwork.
            val posterHeightForBox = box.posterHeight

            append("  <g>\n")
            append("    <rect x=\"").append(num(box.x)).append("\" y=\"").append(num(box.y))
                .append("\" width=\"").append(num(box.width)).append("\" height=\"").append(num(posterHeightForBox))
                .append("\" rx=\"8\" fill=\"").append(hex(palette.tileFill)).append("\"/>\n")

            embeddedPosters[box.mediaId]?.let { dataUri ->
                append("    <image x=\"").append(num(box.x)).append("\" y=\"").append(num(box.y))
                    .append("\" width=\"").append(num(box.width)).append("\" height=\"").append(num(posterHeightForBox))
                    .append("\" preserveAspectRatio=\"xMidYMid slice\" ")
                    .append("href=\"").append(escape(dataUri)).append("\"/>\n")
            }

            candidate?.title?.let { title ->
                append("    <text x=\"").append(num(box.x + box.width / 2f))
                    .append("\" y=\"").append(num(box.y + posterHeightForBox + 18f))
                    .append("\" font-family=\"system-ui,-apple-system,Segoe UI,Roboto,sans-serif\" ")
                    .append("font-size=\"11\" fill=\"").append(hex(palette.captionText))
                    .append("\" text-anchor=\"middle\">").append(escape(truncate(title))).append("</text>\n")
            }
            append("  </g>\n")
        }

        append("</svg>\n")
    }

    private const val PADDING = 24f
    private const val LABEL_GUTTER = 84f
    private const val LABEL_FONT_SIZE = 20

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

    /** `#RRGGBB`, or `#AARRGGBB` when the colour is not opaque. */
    private fun hex(argb: Long): String {
        val a = (argb ushr 24) and 0xFF
        val rgb = argb and 0xFFFFFF
        return if (a == 0xFFL) {
            String.format(java.util.Locale.ROOT, "#%06X", rgb)
        } else {
            String.format(java.util.Locale.ROOT, "#%02X%06X", a, rgb)
        }
    }

    /** Shortens a caption to something that fits under a tile. */
    private fun truncate(title: String, max: Int = 22): String =
        if (title.length <= max) title else title.take(max - 1).trimEnd() + "…"
}