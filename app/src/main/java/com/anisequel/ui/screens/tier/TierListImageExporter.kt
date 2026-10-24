package com.anisequel.ui.screens.tier

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierDisplay
import com.anisequel.domain.tier.TierLayoutOptions
import com.anisequel.domain.tier.TierItemBox
import com.anisequel.domain.tier.TierRowBox
import com.anisequel.domain.tier.TierTitlePosition
import com.anisequel.domain.tier.layoutTierList
import com.anisequel.domain.tier.onlyFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Renders a tier board as one PNG, from the same [layoutTierList] geometry the
 * screen and the SVG writer use.
 *
 * ## Why a third renderer rather than a screenshot
 *
 * The board is inside a scrolling column, so the screen only ever holds the
 * viewport. A screenshot of it is a picture of *the phone*: whatever zoom the
 * person left the board at, whatever rows happened to be on screen, and the app's
 * chrome around them. This lays the whole visible assignment out at the
 * exporter's own 1080-unit width, which fits about eleven posters across and
 * puts a 300-title list into a single file. It is the same arrangement, so a row
 * cannot appear in a different order here than it does on the board.
 *
 * ## The bitmap is capped by pixels, not by title count
 *
 * A 35-200% zoom range and a list that can be thousands of rows tall make "one
 * image" a memory question. The output scale is chosen so the result stays under
 * [MAX_EXPORT_PIXELS]; a very tall list is therefore rendered at slightly under
 * 1 px per layout unit rather than failing with an OOM. Text is drawn at the
 * scaled size, so the cap costs sharpness on the largest lists and never
 * correctness.
 */
internal suspend fun renderTierListImage(
    context: Context,
    listName: String,
    tiers: List<TierDefinition>,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    display: TierDisplay
): Bitmap? = withContext(Dispatchers.Default) {
    if (assignment.isEmpty || tiers.isEmpty()) return@withContext null

    // The board keeps a placement whose title is no longer in the library, so a
    // filtered library that is still loading cannot make a placement look
    // deleted. An *export* has no such job: there is no poster to fetch and no
    // name to caption, so keeping the slot would draw a blank tile and push the
    // counted titles into a lie. Drop those placements from the geometry instead
    // of drawing around them.
    val drawableIds = candidates.mapTo(HashSet()) { it.mediaId }
    val drawableAssignment = assignment.onlyFor(drawableIds)
    if (drawableAssignment.isEmpty) return@withContext null

    val showUnderCover = display.showTitles &&
        display.titlePosition == TierTitlePosition.UNDER_COVER
    val showOnCover = display.showTitles &&
        display.titlePosition == TierTitlePosition.ON_COVER
    val options = TierLayoutOptions(
        width = 1080f,
        labelGutter = 84f,
        padding = 24f,
        itemWidth = 72f,
        itemGap = 8f,
        rowGap = 10f,
        captionHeight = if (showUnderCover) 26f else 0f,
        headerHeight = 76f,
        minRowHeight = 56f,
        minCanvasHeight = 480f
    )
    // Every placement left has a candidate, so this is the number of titles the
    // image actually contains - the count the subtitle promises.
    val titleCount = drawableAssignment.tierIdByMediaId.size
    val layout = layoutTierList(
        tiers = tiers,
        assignment = drawableAssignment,
        candidates = candidates,
        title = listName,
        subtitle = "$titleCount ${if (titleCount == 1) "title" else "titles"} · AniSequel",
        options = options
    )

    val scale = exportScale(layout.width, layout.height)
    val widthPx = (layout.width * scale).roundToInt().coerceAtLeast(1)
    val heightPx = (layout.height * scale).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(EXPORT_BACKGROUND)
    canvas.scale(scale, scale)

    drawHeading(canvas, layout.title, layout.subtitle, layout.width)
    drawRows(canvas, layout.rows, layout.items, tiers)
    drawItems(
        context = context,
        canvas = canvas,
        layout = layout.items,
        candidates = candidates,
        scale = scale,
        showUnderCover = showUnderCover,
        showOnCover = showOnCover
    )
    bitmap
}

/**
 * Picks a scale that keeps the bitmap under [MAX_EXPORT_PIXELS].
 *
 * 2x is the ceiling because the layout is already 1080 units wide - an export
 * that is 2160px across is crisp on any phone and not wastefully large for a
 * short board. The floor is not fixed: a list tall enough to need less than 1x
 * still gets exported, because a slightly soft complete image beats a failure.
 */
private fun exportScale(width: Float, height: Float): Float {
    val basePixels = width.toDouble() * height.toDouble()
    if (basePixels <= 0.0) return 1f
    val fit = sqrt(MAX_EXPORT_PIXELS / basePixels)
    return min(2.0, fit).toFloat().coerceAtLeast(0.25f)
}

private const val MAX_EXPORT_PIXELS = 16_000_000.0
private const val EXPORT_BACKGROUND = 0xFF14110F.toInt()
private const val EXPORT_HEADING = 0xFFFFFFFF.toInt()
private const val EXPORT_SUBHEADING = 0xB3FFFFFF.toInt()
private const val EXPORT_LABEL = 0xFFFFFFFF.toInt()
private const val EXPORT_CAPTION = 0xE6FFFFFF.toInt()
private const val EXPORT_TILE_FILL = 0xFF2A2522.toInt()
private const val EXPORT_TILE_STROKE = 0x33FFFFFF.toInt()
private const val POSTER_BATCH = 8

private fun drawHeading(
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

private fun drawRows(
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

private suspend fun drawItems(
    context: Context,
    canvas: Canvas,
    layout: List<TierItemBox>,
    candidates: List<TierCandidate>,
    scale: Float,
    showUnderCover: Boolean,
    showOnCover: Boolean
) {
    val candidateById = candidates.associateBy { it.mediaId }
    val posters = loadPosters(context, layout, candidateById, scale)

    val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = EXPORT_TILE_FILL }
    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EXPORT_TILE_STROKE
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EXPORT_CAPTION
        textSize = 11f
        textAlign = Paint.Align.CENTER
    }
    val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }

    layout.forEach { box ->
        val poster = RectF(box.x, box.y, box.x + box.width, box.y + box.posterHeight)
        val path = Path().apply {
            addRoundRect(poster, 8f, 8f, Path.Direction.CW)
        }

        canvas.drawRoundRect(poster, 8f, 8f, tilePaint)
        val bitmap = posters[box.mediaId]
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            canvas.save()
            canvas.clipPath(path)
            val destination = Rect(
                poster.left.roundToInt(),
                poster.top.roundToInt(),
                poster.right.roundToInt(),
                poster.bottom.roundToInt()
            )
            canvas.drawBitmap(
                bitmap,
                centerCropSource(bitmap, poster),
                destination,
                imagePaint
            )
            canvas.restore()
        }
        canvas.drawRoundRect(poster, 8f, 8f, strokePaint)

        val candidate = candidateById[box.mediaId] ?: return@forEach
        if (showUnderCover) {
            val caption = ellipsize(candidate.title, captionPaint, box.width)
            canvas.drawText(
                caption,
                box.x + box.width / 2f,
                box.y + box.posterHeight + 16f,
                captionPaint
            )
        } else if (showOnCover) {
            canvas.save()
            canvas.clipPath(path)
            canvas.drawRect(box.x, box.y, box.x + box.width, box.y + 24f, scrimPaint)
            val caption = ellipsize(candidate.title, captionPaint, box.width - 6f)
            canvas.drawText(
                caption,
                box.x + box.width / 2f,
                box.y + 15f,
                captionPaint
            )
            canvas.restore()
        }
    }
}

/**
 * The source rectangle for a center-cropped poster.
 *
 * `ContentScale.Crop` on the board cuts the longer axis rather than squeezing
 * the cover, and an export that stretched every 2:3 cover into a slightly
 * different tile would show artwork the board never showed.
 */
private fun centerCropSource(bitmap: Bitmap, destination: RectF): Rect {
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()
    val scale = max(destination.width() / bitmapWidth, destination.height() / bitmapHeight)
    val sourceWidth = destination.width() / scale
    val sourceHeight = destination.height() / scale
    val left = (bitmapWidth - sourceWidth) / 2f
    val top = (bitmapHeight - sourceHeight) / 2f
    return Rect(
        left.roundToInt(),
        top.roundToInt(),
        (left + sourceWidth).roundToInt(),
        (top + sourceHeight).roundToInt()
    )
}

/**
 * Fetches the posters in small batches and returns only the ones that decoded.
 *
 * A missing poster is a tile with its fill colour and a readable caption, which
 * is what the SVG exporter does too. The batch cap is what keeps a 300-title
 * board from opening 300 connections against a CDN at once, and the catch is
 * per poster: one 404 must not fail the whole image.
 */
private suspend fun loadPosters(
    context: Context,
    items: List<TierItemBox>,
    candidateById: Map<Int, TierCandidate>,
    scale: Float
): Map<Int, Bitmap> = coroutineScope {
    val requests = items.mapNotNull { box ->
        val url = candidateById[box.mediaId]?.coverUrl ?: return@mapNotNull null
        Triple(
            box.mediaId,
            url,
            (box.width * scale).roundToInt().coerceAtLeast(1) to
                (box.posterHeight * scale).roundToInt().coerceAtLeast(1)
        )
    }
    val loaded = mutableMapOf<Int, Bitmap>()
    requests.chunked(POSTER_BATCH).forEach { batch ->
        batch.map { (mediaId, url, size) ->
            async(Dispatchers.IO) {
                mediaId to loadPoster(context, url, size.first, size.second)
            }
        }.awaitAll().forEach { (mediaId, bitmap) ->
            if (bitmap != null) loaded[mediaId] = bitmap
        }
    }
    loaded
}

private suspend fun loadPoster(
    context: Context,
    url: String,
    widthPx: Int,
    heightPx: Int
): Bitmap? = try {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(widthPx, heightPx)
        .allowHardware(false)
        .build()
    when (val result = context.imageLoader.execute(request)) {
        is SuccessResult -> result.drawable.toExportBitmap()
        else -> null
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    null
}

private fun Drawable.toExportBitmap(): Bitmap? {
    val source = if (this is BitmapDrawable) {
        bitmap
    } else {
        try {
            toBitmap()
        } catch (_: Exception) {
            null
        }
    } ?: return null
    return if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        source
    }
}

/**
 * Writes a PNG into the cache and hands its URI to whatever can share images.
 *
 * The cache, not the files directory: this file exists for one chooser round
 * trip and the system may evict it afterwards. Only the `exports/` directory is
 * published by the app's `FileProvider`, so a file written anywhere else here
 * would fail `getUriForFile` at the last step - the same two-strings-must-agree
 * trap the updater's paths file documents.
 */
internal suspend fun shareTierListImage(
    context: Context,
    bitmap: Bitmap,
    listName: String
): Boolean {
    val file = withContext(Dispatchers.IO) { writeExportFile(context, bitmap, listName) }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    withContext(Dispatchers.Main) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, listName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share tier list"))
    }
    return true
}

/** Writes a PNG to `Pictures/AniSequel` through MediaStore. */
internal suspend fun saveTierListImage(
    context: Context,
    bitmap: Bitmap,
    listName: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${safeName(listName)}-$now.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/AniSequel"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri: Uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return@withContext false

        val written = resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: false
        if (!written) {
            resolver.delete(uri, null, null)
            return@withContext false
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }
}

private fun writeExportFile(context: Context, bitmap: Bitmap, listName: String): File {
    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(
        directory,
        "${safeName(listName)}-${System.currentTimeMillis()}.png"
    )
    file.outputStream().use { output ->
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            throw IOException("PNG encoding failed")
        }
    }
    return file
}

/**
 * A file name that survives every filesystem without becoming a different name.
 *
 * The list name is user text: it can contain `/`, a colon, or a leading dot, and
 * a `File` built from it would write outside the cache or vanish as a hidden
 * file. Everything outside the conservative set becomes a dash, and the result
 * is bounded so the MediaStore display name stays usable.
 */
private fun safeName(listName: String): String =
    listName.trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-', '.')
        .take(60)
        .ifBlank { "tier-list" }

private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String =
    TextUtils.ellipsize(
        text,
        TextPaint(paint),
        maxWidth.coerceAtLeast(1f),
        TextUtils.TruncateAt.END
    ).toString()
