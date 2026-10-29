package com.anisequel.ui.screens.tier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextPaint
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierDisplay
import com.anisequel.domain.tier.TierItemBox
import com.anisequel.domain.tier.TierLayoutOptions
import com.anisequel.domain.tier.TierListLayout
import com.anisequel.domain.tier.TierTitleLength
import com.anisequel.domain.tier.TierTitlePosition
import com.anisequel.domain.tier.layoutTierList
import com.anisequel.domain.tier.onlyFor
import com.anisequel.domain.tier.tierCaption
import com.anisequel.domain.tier.tierTileCornerRadius
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
 * chrome around them. This lays the whole visible assignment out at the exporter's
 * own 1080-unit width, which fits about eleven posters across and puts a
 * 300-title list into a single file. It is the same arrangement, so a row cannot
 * appear in a different order here than it does on the board.
 *
 * ## The output is scaled to the requested width, up to 4K
 *
 * The layout is authored in 1080 units; [targetWidth] asks for a bitmap
 * 1080/2160/3840 pixels across and every unit - artwork, text, stroke - is scaled
 * by the same factor. Two things cap it, and both are memory rather than taste:
 * the bitmap has to fit in the heap ([exportPixelBudget]), and a list tall enough
 * to make a 4K-wide image billions of pixels is scaled down until it does. A
 * device that cannot allocate even the first attempt steps the budget down and
 * tries again, because a slightly smaller export is better than no export.
 *
 * ## Posters are fetched at their drawn size and released in batches
 *
 * A poster is requested at exactly the pixels it occupies in the final bitmap, so
 * it is never upscaled and never decoded twice as large as it is drawn. The
 * requests opt out of Coil's memory cache - each cover appears once, so the cache
 * would only hold a second copy of the whole board - and each batch is recycled
 * after it is drawn. Retaining all covers until the end is what would turn a 4K
 * export of a 300-title list into an out-of-memory crash: at 4K each tile is
 * about 256x384, roughly 400 KB, and the sum is over a hundred megabytes.
 *
 * ## Progress is reported per poster batch
 *
 * [onProgress] is called with a 0..1 fraction: once when the layout is ready, then
 * once per [POSTER_BATCH] of tiles as their covers arrive and are drawn, and once
 * at 1 when the bitmap is complete. Fetching is the slow part and it happens in
 * those batches, so the batches are the only place where a fraction means
 * anything - a per-pixel report would cost more than the drawing it measures.
 *
 * The callback is `suspend` on purpose. It runs inside this function's
 * `Dispatchers.Default` block, and the sheet's state is Compose state that is read
 * on the main thread; letting the caller hop threads itself keeps this file from
 * having to know where the bar that reads it lives.
 */
internal suspend fun renderTierListImage(
    context: Context,
    listName: String,
    tiers: List<TierDefinition>,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    display: TierDisplay,
    targetWidth: Int = TIER_EXPORT_WIDTH_ULTRA,
    onProgress: suspend (Float) -> Unit = {}
): Bitmap? = withContext(Dispatchers.Default) {
    onProgress(0f)
    val plan = planTierExport(
        listName = listName,
        tiers = tiers,
        assignment = assignment,
        candidates = candidates,
        display = display
    ) ?: return@withContext null
    val layout = plan.layout
    onProgress(EXPORT_PROGRESS_LAYOUT)

    // First choice is the requested resolution. A failed allocation halves the
    // budget and retries, so a low-memory device gets a smaller complete image
    // rather than the sheet's generic "could not be rendered".
    var budget = exportPixelBudget()
    var scale = 1f
    var allocated: Bitmap? = null
    while (allocated == null && budget >= MIN_EXPORT_PIXELS) {
        scale = exportScale(
            width = layout.width,
            height = layout.height,
            targetWidth = targetWidth,
            maxPixels = budget
        )
        allocated = allocateBitmap(layout.width * scale, layout.height * scale)
        if (allocated == null) budget /= 2.0
    }
    val bitmap = allocated ?: return@withContext null

    // Every path out of the drawing block has to free the output. This used to
    // return the bitmap on success and let the caller's catch turn a failure into
    // a null *without* recycling it, so an OutOfMemoryError midway through
    // drawing a 4K board left the largest allocation in the app to the collector
    // while the sheet was still open.
    try {
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
            showUnderCover = plan.showUnderCover,
            showOnCover = plan.showOnCover,
            titleLength = plan.titleLength,
            captionBandHeight = plan.captionBandHeight,
            onProgress = { fraction ->
                onProgress(
                    EXPORT_PROGRESS_LAYOUT + fraction * (1f - EXPORT_PROGRESS_LAYOUT)
                )
            }
        )
        onProgress(1f)
        bitmap
    } catch (failure: Throwable) {
        bitmap.recycle()
        throw failure
    }
}

/**
 * The output widths the export sheet offers.
 *
 * 1080 is the layout's own unit width, 2160 is 2x, and 3840 is 4K UHD - the
 * width an image has to reach to be called 4K without a footnote. The taller
 * dimension is whatever the board's aspect ratio produces; a 4K-wide board that
 * happens to be 2000 units tall exports at 3840x7111, which is still a 4K-class
 * file and is capped by [exportPixelBudget] on smaller devices.
 */
internal const val TIER_EXPORT_WIDTH_STANDARD = 1080
internal const val TIER_EXPORT_WIDTH_HIGH = 2160
internal const val TIER_EXPORT_WIDTH_ULTRA = 3840

/** The size a given quality would actually produce, for the sheet's caption. */
internal data class TierExportSize(val width: Int, val height: Int)

/**
 * What [renderTierListImage] would produce, without allocating the bitmap.
 *
 * The sheet shows "3840 x 2214 px" before the person presses anything, which is
 * the difference between a quality switch and a promise. It runs the same plan
 * and the same scale arithmetic as the renderer, so the number cannot drift from
 * the file. The layout step allocates no bitmaps and is a single pass over the
 * assignment.
 */
internal fun estimateTierExportSize(
    listName: String,
    tiers: List<TierDefinition>,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    display: TierDisplay,
    targetWidth: Int
): TierExportSize? {
    val plan = planTierExport(
        listName = listName,
        tiers = tiers,
        assignment = assignment,
        candidates = candidates,
        display = display
    ) ?: return null
    val scale = exportScale(
        width = plan.layout.width,
        height = plan.layout.height,
        targetWidth = targetWidth,
        maxPixels = exportPixelBudget()
    )
    return TierExportSize(
        width = (plan.layout.width * scale).roundToInt().coerceAtLeast(1),
        height = (plan.layout.height * scale).roundToInt().coerceAtLeast(1)
    )
}

/** The arrangement, and the display decisions both the render and the size share. */
private data class TierExportPlan(
    val layout: TierListLayout,
    val showUnderCover: Boolean,
    val showOnCover: Boolean,
    /** The preference the captions are cut with, shared with the board. */
    val titleLength: TierTitleLength,
    /** The strip under each poster the layout reserved, 0 when there is none. */
    val captionBandHeight: Float
)

/**
 * Works out what the image will contain.
 *
 * The board keeps a placement whose title is no longer in the library, so a
 * filtered library that is still loading cannot make a placement look deleted. An
 * *export* has no such job: there is no poster to fetch and no name to caption, so
 * keeping the slot would draw a blank tile and push the counted titles into a
 * lie. Those placements are dropped from the geometry here rather than drawn
 * around.
 */
private fun planTierExport(
    listName: String,
    tiers: List<TierDefinition>,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    display: TierDisplay
): TierExportPlan? {
    if (assignment.isEmpty || tiers.isEmpty()) return null

    val drawableIds = candidates.mapTo(HashSet()) { it.mediaId }
    val drawableAssignment = assignment.onlyFor(drawableIds)
    if (drawableAssignment.isEmpty) return null

    val showUnderCover = display.showTitles &&
        display.titlePosition == TierTitlePosition.UNDER_COVER
    val showOnCover = display.showTitles &&
        display.titlePosition == TierTitlePosition.ON_COVER
    // Read from the same property the board's layout options read, rather than
    // from `showUnderCover` alone: the two are the same answer today, and the
    // next way a caption band can exist should not have to change two rules.
    val captionBandHeight = if (display.hasCaptionBand) EXPORT_CAPTION_BAND_HEIGHT else 0f
    val options = TierLayoutOptions(
        width = 1080f,
        labelGutter = 84f,
        padding = 24f,
        itemWidth = 72f,
        itemGap = 8f,
        rowGap = 10f,
        captionHeight = captionBandHeight,
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
    return TierExportPlan(
        layout = layout,
        showUnderCover = showUnderCover,
        showOnCover = showOnCover,
        titleLength = display.titleLength,
        captionBandHeight = captionBandHeight
    )
}

/**
 * Picks a scale that reaches [targetWidth] without exceeding [maxPixels].
 *
 * The result is a *width* promise first and a memory promise second: a short board
 * exports at exactly the chosen quality, and only a board too tall to fit the heap
 * gives some of it back. The floor is not fixed at 1x - a 300-title list must
 * still export something - but it is low enough that the complete image survives.
 */
private fun exportScale(
    width: Float,
    height: Float,
    targetWidth: Int,
    maxPixels: Double
): Float {
    val basePixels = width.toDouble() * height.toDouble()
    if (basePixels <= 0.0) return 1f
    val byWidth = targetWidth.coerceAtLeast(1).toDouble() / width.toDouble()
    val byBudget = sqrt(maxPixels / basePixels)
    return min(byWidth, byBudget).toFloat().coerceIn(MIN_EXPORT_SCALE, MAX_EXPORT_SCALE)
}

/**
 * How many pixels this process can reasonably hold for one bitmap.
 *
 * The output is ARGB_8888, four bytes per pixel. A third of the heap leaves room
 * for the app behind the sheet, the canvas's own allocation, and the poster batch
 * being drawn into it. The floor is what makes an export possible on a small
 * heap; the ceiling keeps a 4K-wide board from asking for a 256 MB bitmap that
 * the PNG encoder would then need time and memory to walk.
 */
private fun exportPixelBudget(): Double {
    val maxHeap = Runtime.getRuntime().maxMemory().toDouble()
    return (maxHeap * 0.33 / 4.0).coerceIn(MIN_EXPORT_PIXELS, MAX_EXPORT_PIXELS)
}

/** Allocates the output, returning null instead of throwing on a tight heap. */
private fun allocateBitmap(width: Float, height: Float): Bitmap? {
    val pxWidth = width.roundToInt().coerceIn(1, MAX_EXPORT_DIMENSION)
    val pxHeight = height.roundToInt().coerceIn(1, MAX_EXPORT_DIMENSION)
    return try {
        Bitmap.createBitmap(pxWidth, pxHeight, Bitmap.Config.ARGB_8888)
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** Four bytes an output pixel, with room for a poster batch and the encoder. */
private const val MIN_EXPORT_PIXELS = 4_000_000.0

/** 32 MP: a 4K-width board up to about 8000 units tall. */
private const val MAX_EXPORT_PIXELS = 33_554_432.0

private const val MIN_EXPORT_SCALE = 0.25f
private const val MAX_EXPORT_SCALE = 8f

/** Bitmap's practical dimension ceiling; anything larger throws at allocation. */
private const val MAX_EXPORT_DIMENSION = 32_000

private const val EXPORT_BACKGROUND = 0xFF14110F.toInt()
private const val EXPORT_CAPTION = 0xE6FFFFFF.toInt()
private const val EXPORT_TILE_FILL = 0xFF2A2522.toInt()
private const val EXPORT_TILE_STROKE = 0x33FFFFFF.toInt()
private const val EXPORT_SCORE_SCRIM = 0x99000000.toInt()
private const val EXPORT_TITLE_SCRIM = 0xC4000000.toInt()
private const val POSTER_BATCH = 8

/**
 * Where the progress bar starts once the layout is known.
 *
 * The plan is a single pass over the assignment and the drawing is the rest, so a
 * bar that jumped to 50% for "the layout is ready" would be lying about the work
 * left. Five percent is the honest share, and it keeps the bar moving from the
 * first frame rather than sitting at zero until the first batch of covers lands.
 */
private const val EXPORT_PROGRESS_LAYOUT = 0.05f

/** The strip under a poster a caption sits in, in the layout's own units. */
private const val EXPORT_CAPTION_BAND_HEIGHT = 26f

/**
 * Draws every tile, in batches, releasing each batch's covers as it goes.
 *
 * The tiles are laid out with gaps and never overlap, so drawing them in chunks
 * cannot change the picture. The alternative - load every poster, then draw every
 * poster - is what made the 4K export the largest allocation in the app.
 */
private suspend fun drawItems(
    context: Context,
    canvas: Canvas,
    layout: List<TierItemBox>,
    candidates: List<TierCandidate>,
    scale: Float,
    showUnderCover: Boolean,
    showOnCover: Boolean,
    titleLength: TierTitleLength,
    captionBandHeight: Float,
    onProgress: suspend (Float) -> Unit
) {
    val candidateById = candidates.associateBy { it.mediaId }

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
    val scorePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EXPORT_LABEL
        textSize = 10f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    val scoreBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EXPORT_SCORE_SCRIM
    }
    val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = EXPORT_TITLE_SCRIM
    }

    // The total is the number of tiles, not of batches: a board of ten tiles is one
    // batch of ten and one of two, and reporting per batch would jump 5/6 -> 1.
    val total = layout.size.coerceAtLeast(1)
    var drawn = 0

    layout.chunked(POSTER_BATCH).forEach { batch ->
        val posters = loadPosterBatch(
            context = context,
            items = batch,
            candidateById = candidateById,
            scale = scale
        )
        // `finally`: a draw that throws - a recycled bitmap, an OOM on the
        // canvas - used to skip the recycle below and leave the whole batch
        // alive while the failure was reported.
        try {
            batch.forEach { box ->
                drawTile(
                    canvas = canvas,
                    box = box,
                    bitmap = posters[box.mediaId],
                    candidate = candidateById[box.mediaId],
                    tilePaint = tilePaint,
                    imagePaint = imagePaint,
                    strokePaint = strokePaint,
                    captionPaint = captionPaint,
                    scorePaint = scorePaint,
                    scoreBackground = scoreBackground,
                    scrimPaint = scrimPaint,
                    showUnderCover = showUnderCover,
                    showOnCover = showOnCover,
                    titleLength = titleLength,
                    captionBandHeight = captionBandHeight
                )
            }
        } finally {
            // The requests opt out of the memory cache, so nothing else
            // references these pixels: freeing them here is what bounds the
            // export's peak memory.
            posters.values.forEach { it.recycle() }
        }
        drawn += batch.size
        onProgress(drawn.toFloat() / total)
    }
}

private fun drawTile(
    canvas: Canvas,
    box: TierItemBox,
    bitmap: Bitmap?,
    candidate: TierCandidate?,
    tilePaint: Paint,
    imagePaint: Paint,
    strokePaint: Paint,
    captionPaint: TextPaint,
    scorePaint: TextPaint,
    scoreBackground: Paint,
    scrimPaint: Paint,
    showUnderCover: Boolean,
    showOnCover: Boolean,
    titleLength: TierTitleLength,
    captionBandHeight: Float
) {
    // The same corner fraction the board draws, so an exported tile is not rounder
    // than the tile it came from - the difference was most visible on a zoomed-out
    // screen, where a constant radius made the covers look peeled.
    val corner = tierTileCornerRadius(box.width)
    val poster = RectF(box.x, box.y, box.x + box.width, box.y + box.posterHeight)
    val path = Path().apply {
        addRoundRect(poster, corner, corner, Path.Direction.CW)
    }

    canvas.drawRoundRect(poster, corner, corner, tilePaint)
    if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
        // A bitmap shader fills the rounded path, rather than `clipPath` plus
        // `drawBitmap`. `Canvas.clipPath` is documented as *not* anti-aliased, so
        // the artwork's corners were stair-stepped at every export scale and most
        // visible at 4K - where the corner is about 30px and the jagged edge sits
        // immediately inside a smooth, anti-aliased stroke. A path fill is
        // anti-aliased, so the cover's own corners are now as clean as its
        // outline.
        imagePaint.shader = BitmapShader(
            bitmap,
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP
        ).apply {
            setLocalMatrix(centerCropMatrix(bitmap, poster))
        }
        canvas.drawPath(path, imagePaint)
        // The shader holds the batch's bitmap. Clearing it before the batch is
        // recycled keeps the paint from outliving the pixels it samples.
        imagePaint.shader = null
    }

    // The board moves the badge to the opposite corner when the name is across
    // the top; the export used to keep it at the top, so an exported ON_COVER
    // board drew the score through the first word of every title.
    drawScoreBadge(
        canvas = canvas,
        box = box,
        candidate = candidate,
        scorePaint = scorePaint,
        scoreBackground = scoreBackground,
        alignBottom = showOnCover
    )

    canvas.drawRoundRect(poster, corner, corner, strokePaint)

    if (candidate == null) return
    val captionText = tierCaption(candidate.title, titleLength)
    if (showUnderCover) {
        val caption = ellipsize(captionText, captionPaint, box.width)
        // Centred in the band the layout reserved, from the paint's own metrics.
        // The baseline used to be `poster.bottom + 16`, a number that happened to
        // sit inside a 26-unit band at 11pt and went wrong the moment the band or
        // the type size changed - a descender cut at the bottom of the image.
        val bandCenter = box.y + box.posterHeight + captionBandHeight / 2f
        val baseline = bandCenter - (captionPaint.ascent() + captionPaint.descent()) / 2f
        canvas.drawText(caption, box.x + box.width / 2f, baseline, captionPaint)
    } else if (showOnCover) {
        // A gradient rather than a solid strip, matching the board's scrim: a band
        // of flat paint across the top of every cover turns a board into a table.
        val scrimBottom = box.y + 28f
        scrimPaint.shader = LinearGradient(
            0f,
            box.y,
            0f,
            scrimBottom,
            intArrayOf(EXPORT_TITLE_SCRIM, 0x00000000),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(box.x, box.y, box.x + box.width, scrimBottom, scrimPaint)
        canvas.restore()
        val caption = ellipsize(captionText, captionPaint, box.width - 6f)
        // Measured the same way as the caption under the cover: the ascent is
        // negative, so this is "one text line down from the top edge", not a
        // guessed constant that has to change with the type size.
        val baseline = box.y + 4f - captionPaint.ascent()
        canvas.drawText(caption, box.x + box.width / 2f, baseline, captionPaint)
    }
}

/**
 * The AniList score, in the same corner the board puts it.
 *
 * The board has drawn this badge since the redesign; the export did not, so the
 * "same arrangement" promise held for the rows and not for the numbers on them.
 * A badge is small and cheap to draw, and without it an exported board gives a
 * reader no way to tell a 92 from an 82 when the covers are unfamiliar.
 */
private fun drawScoreBadge(
    canvas: Canvas,
    box: TierItemBox,
    candidate: TierCandidate?,
    scorePaint: TextPaint,
    scoreBackground: Paint,
    alignBottom: Boolean
) {
    val score = candidate?.averageScore ?: return
    if (score <= 0) return

    val text = score.toString()
    val paddingX = 4f
    val paddingY = 1.5f
    val textWidth = scorePaint.measureText(text)
    val badgeHeight = scorePaint.textSize + paddingY * 2f
    val left = box.x + 4f
    // The board puts it at the bottom when the title is on the cover, so the two
    // never overlap. Bottom-aligned here to match, not merely moved far enough
    // down to miss this font size.
    val top = if (alignBottom) {
        box.y + box.posterHeight - badgeHeight - 4f
    } else {
        box.y + 4f
    }
    val badge = RectF(left, top, left + textWidth + paddingX * 2f, top + badgeHeight)
    val radius = badge.height() / 2f

    canvas.drawRoundRect(badge, radius, radius, scoreBackground)
    val baseline = badge.centerY() - (scorePaint.ascent() + scorePaint.descent()) / 2f
    canvas.drawText(text, badge.centerX(), baseline, scorePaint)
}

/**
 * The transform that center-crops a poster into a tile.
 *
 * `ContentScale.Crop` on the board cuts the longer axis rather than squeezing the
 * cover, and an export that stretched every 2:3 cover into a slightly different
 * tile would show artwork the board never showed.
 *
 * A `Matrix` rather than a source `Rect` because the caller fills a rounded path
 * with a [BitmapShader] instead of clipping and blitting a rectangle: the shader
 * needs the same crop expressed as a bitmap-to-canvas transform. The translation
 * centres the overflowing axis, so the visible middle of the cover is the middle
 * the board showed.
 */
private fun centerCropMatrix(bitmap: Bitmap, destination: RectF): Matrix {
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()
    val scale = max(destination.width() / bitmapWidth, destination.height() / bitmapHeight)
    return Matrix().apply {
        setScale(scale, scale)
        postTranslate(
            destination.left - (bitmapWidth * scale - destination.width()) / 2f,
            destination.top - (bitmapHeight * scale - destination.height()) / 2f
        )
    }
}

/**
 * Fetches one batch of posters and returns only the ones that decoded.
 *
 * A missing poster is a tile with its fill colour and a readable caption, which is
 * what the SVG exporter does too. The batch cap is what keeps a 300-title board
 * from opening 300 connections against a CDN at once, and the catch is per
 * poster: one 404 must not fail the whole image.
 */
private suspend fun loadPosterBatch(
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
    val loaded = requests.map { (mediaId, url, size) ->
        async(Dispatchers.IO) {
            mediaId to loadPoster(context, url, size.first, size.second)
        }
    }.awaitAll()
    loaded.mapNotNull { (mediaId, bitmap) ->
        if (bitmap == null) null else mediaId to bitmap
    }.toMap()
}

/**
 * One poster at its drawn size.
 *
 * `memoryCachePolicy(DISABLED)` is what makes the batch's `recycle()` safe. Every
 * cover in a board appears once, so the memory cache would never serve a hit
 * during an export; it would only keep a second copy of each bitmap alive after
 * the batch had been drawn. With the cache off, the result belongs to this
 * request alone and can be freed as soon as the pixels are on the canvas.
 */
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
        .memoryCachePolicy(CachePolicy.DISABLED)
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
