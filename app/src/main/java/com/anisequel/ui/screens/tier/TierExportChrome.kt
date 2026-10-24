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

/**
 * The list's name and its counted subtitle.
 *
 * Both are clipped to the page rather than allowed to widen it, because the title
 * is the one text in the document whose length nobody chose: a list called
 * `My very long tier list of everything I watched in 2024` would otherwise run
 * past the right edge of every export.
 */
internal fun drawHeading(
    canvas: Canvas,
    title: String,
    subtitle: String?,
    width: Float
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EXPORT_HEADING
        textSize = 26f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    canvas.drawText(ellipsize(title, paint, width - 48f), 24f, 54f, paint)

    if (subtitle != null) {
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EXPORT_SUBHEADING
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawText(ellipsize(subtitle, subtitlePaint, width - 48f), 24f, 78f, subtitlePaint)
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
