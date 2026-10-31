package com.anisequel.ui.screens.tier

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierItemBox
import com.anisequel.domain.tier.TierRowBox

/**
 * The parts of an exported board that are not posters: its heading and its tier
 * rows.
 *
 * Split out of [com.anisequel.ui.screens.tier.TierListImageExporter] when the
 * exporter crossed the 700-line ceiling in AGENTS §1. The split is by
 * responsibility rather than by count: this file draws the document around the
 * posters, the exporter draws the posters themselves, and the caption helpers the
 * two share live here because the heading is what established them.
 */

/** White for a tier's letters and the score badges drawn over its posters. */
internal const val EXPORT_LABEL = 0xFFFFFFFF.toInt()

private const val EXPORT_HEADING = 0xFFFFFFFF.toInt()
private const val EXPORT_SUBHEADING = 0xB3FFFFFF.toInt()

/** The heading sizes the export offers, as a multiplier on the drawn text. */
enum class TierExportTitleSize(val scale: Float, val displayName: String) {
    SMALL(0.8f, "S"),
    MEDIUM(1f, "M"),
    LARGE(1.3f, "L")
}

/** The heading faces the export offers. Kept as generic families so the image uses
 * the platform's own faces rather than a font the app cannot embed in a PNG. */
enum class TierExportTitleFont(val displayName: String, val family: String) {
    SANS("Sans", "sans-serif"),
    SERIF("Serif", "serif"),
    MONO("Mono", "monospace")
}

/**
 * How the list's name appears at the top of the image.
 *
 * A choice for this export rather than a stored preference: the board's own title
 * settings are about the *screen* (captions under covers), and the export's
 * heading is a different surface with a different job - a 4K PNG needs its name
 * legible when it is scaled into a chat window, which is not the size that reads
 * well on a phone. Hiding it gives the posters the whole page.
 */
data class TierExportTitleOptions(
    val showTitle: Boolean = true,
    val size: TierExportTitleSize = TierExportTitleSize.MEDIUM,
    val font: TierExportTitleFont = TierExportTitleFont.SANS
) {
    /** The heading band the layout has to reserve, in layout units. */
    val headerHeight: Float
        get() = if (showTitle) 22f + TitleLineHeight * size.scale * 2f else 24f
}

private const val TitleLineHeight = 26f

/**
 * The list's name and its counted subtitle.
 *
 * Both are clipped to the page rather than allowed to widen it, because the title
 * is the one text in the document whose length nobody chose: a list called
 * `My very long tier list of everything I watched in 2024` would otherwise run
 * past the right edge of every export. Size and face come from the options, and a
 * hidden heading is skipped outright - the layout has already collapsed its band.
 */
internal fun drawHeading(
    canvas: Canvas,
    title: String,
    subtitle: String?,
    width: Float,
    options: TierExportTitleOptions
) {
    if (!options.showTitle) return

    val scale = options.size.scale
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EXPORT_HEADING
        textSize = TitleLineHeight * scale
        typeface = Typeface.create(options.font.family, Typeface.BOLD)
    }
    val titleY = 22f + TitleLineHeight * scale
    canvas.drawText(ellipsize(title, paint, width - 48f), 24f, titleY, paint)

    if (subtitle != null) {
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_SUBHEADING
            textSize = 14f * scale
            typeface = Typeface.create(options.font.family, Typeface.NORMAL)
        }
        canvas.drawText(
            ellipsize(subtitle, subtitlePaint, width - 48f),
            24f,
            titleY + 22f * scale,
            subtitlePaint
        )
    }
}

/**
 * Every tier's fill, label and member count, in the order the layout stacked
 * them.
 *
 * The count is the number of tiles geometrically inside the row rather than
 * `membersOf` from the assignment: the exporter was handed a filtered assignment
 * and a layout built from it, so the layout is the only thing that knows what is
 * actually in the image.
 */
internal fun drawRows(
    canvas: Canvas,
    rows: List<TierRowBox>,
    items: List<TierItemBox>,
    tiers: List<TierDefinition>
) {
    val knownTiers = tiers.associateBy { it.id }
    val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EXPORT_LABEL
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EXPORT_LABEL
        alpha = 210
        textSize = 11f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    rows.forEach { row ->
        if (knownTiers[row.tierId] == null) return@forEach
        rowPaint.color = row.colorArgb.toInt()
        rowPaint.alpha = 235
        val rect = RectF(row.x, row.y, row.x + row.width, row.y + row.height)
        canvas.drawRoundRect(rect, 10f, 10f, rowPaint)

        val count = items.count { it.y >= row.y && it.bottom <= row.bottom }
        val centerY = row.y + row.height / 2f
        val label = ellipsize(row.label, labelPaint, 76f)
        if (count > 0) {
            canvas.drawText(label, row.x + 42f, centerY - 2f, labelPaint)
            canvas.drawText(count.toString(), row.x + 42f, centerY + 15f, countPaint)
        } else {
            canvas.drawText(label, row.x + 42f, centerY + 7f, labelPaint)
        }
    }
}

/**
 * One line of text cut to [maxWidth] with an ellipsis.
 *
 * Shared with the exporter's captions: a caption is bounded by the same rule as
 * the heading, and the two having separate copies is how one of them ends up
 * measuring against a different width.
 */
internal fun ellipsize(text: String, paint: Paint, maxWidth: Float): String =
    TextUtils.ellipsize(
        text,
        TextPaint(paint),
        maxWidth.coerceAtLeast(1f),
        TextUtils.TruncateAt.END
    ).toString()
