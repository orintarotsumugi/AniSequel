package com.anisequel.ui.components.detail

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.model.statusLabel
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.detail.info.COPY_CONFIRMATION_MILLIS
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen immersive artwork viewer for one title.
 *
 * ## Both AniList images, not just the poster
 *
 * The viewer used to draw `posterUrl` and nothing else, so the banner - the one
 * wide piece of art AniList serves, and the hero behind it - was unreachable at
 * full size. It now offers every image the detail answer actually carries as a
 * source: the key visual and, when there is one, the banner. Switching resets
 * the zoom, and the save button saves whichever source is on screen rather than
 * a fixed URL.
 *
 * ## Why LiveChart has a link and not an image source
 *
 * LiveChart serves its own posters, but publishes no API - its FAQ points at RSS
 * feeds and nothing else - so reading those images would mean scraping a site
 * that has asked not to be consumed that way. The viewer therefore links out to
 * LiveChart's own search for this title, where the reader sees the schedule and
 * the artwork on the site that hosts it. That is the honest version of "show me
 * the LiveChart cover": the tap goes to LiveChart.
 *
 * Pinch-to-zoom, double-tap and pan are unchanged; see the frame below.
 */
@Composable
fun FullScreenCoverViewer(
    media: MediaNode,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    // Scale, pan and the measured frame live in one holder: the double-tap, the
    // pinch and the background tap all move the same three values, and keeping
    // them together is what lets each gesture be a method rather than another
    // branch inside this composable.
    //
    // The pan limits are measured from the frame rather than the constants 300f
    // and 400f this used to carry: a tall cover could be dragged past its own
    // edge on a narrow phone, and a wide one could not be dragged far enough on
    // a tablet.
    val zoom = remember(media.id) { CoverZoomState() }

    val sources = remember(media.id, media.posterUrl, media.bannerImage) {
        buildList {
            media.posterUrl?.let {
                add(CoverSource(label = "Poster", url = it, fallbackAspect = DEFAULT_COVER_ASPECT))
            }
            // AniList banners are about 1900x400. The fallback is only ever read
            // for the frame before the image reports its real size.
            media.bannerImage?.let {
                add(CoverSource(label = "Banner", url = it, fallbackAspect = DEFAULT_BANNER_ASPECT))
            }
        }
    }
    var sourceIndex by remember(media.id) { mutableIntStateOf(0) }
    val source = sources.getOrNull(sourceIndex)
    val painter = rememberAsyncImagePainter(model = source?.url)
    // The artwork's own ratio, so a rare non-2:3 cover - or a wide banner - draws
    // whole instead of letterboxing inside the poster's frame.
    val coverAspect = coverAspectOf(painter.intrinsicSize, source)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saveState by remember(media.id) { mutableStateOf(CoverSaveState.IDLE) }
    // Each source is a fresh view: keeping a 2.4x zoom from the poster while the
    // banner arrives would open the banner already cropped past its own edges -
    // and the pan limits were measured against the other image's frame.
    LaunchedEffect(source?.url) {
        zoom.reset()
        saveState = CoverSaveState.IDLE
    }
    // Saved and failed are acknowledgements, not modes: they return to the idle
    // glyph after the same window the Extras card's "Link copied" uses.
    LaunchedEffect(saveState) {
        if (saveState != CoverSaveState.SAVED && saveState != CoverSaveState.FAILED) {
            return@LaunchedEffect
        }
        delay(COPY_CONFIRMATION_MILLIS)
        saveState = CoverSaveState.IDLE
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (zoom.isZoomed) zoom.reset() else onDismiss()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(ExpressiveMotion.DefaultEffects) +
                    scaleIn(
                        initialScale = 0.82f,
                        animationSpec = ExpressiveMotion.DefaultSpatial
                    ),
                exit = fadeOut(ExpressiveMotion.FastEffects) +
                    scaleOut(
                        targetScale = 0.85f,
                        animationSpec = ExpressiveMotion.DefaultSpatial
                    ),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    CoverViewerTopBar(
                        media = media,
                        canSave = source != null,
                        saveState = saveState,
                        onSave = {
                            val target = source
                            if (target != null && saveState != CoverSaveState.SAVING) {
                                saveState = CoverSaveState.SAVING
                                scope.launch { saveState = saveCover(context, media, target) }
                            }
                        },
                        onDismiss = onDismiss
                    )

                    // Center Anime Cover Artwork with interactive Zoom & Pan
                    val coverBg = media.posterColor.toCoverColorOrNull() ?: MaterialTheme.colorScheme.surfaceContainerHighest
                    // The frame is fitted to the artwork rather than to a 340x440
                    // window: a 2:3 cover in that window letterboxed on one axis, so
                    // the "full screen" viewer drew a poster smaller than the hero
                    // it was opened from. [CoverArtwork] takes the space the header
                    // and footer leave, and the cover takes the largest box that
                    // fits it at its own ratio.
                    CoverArtwork(
                        media = media,
                        painter = painter,
                        coverAspect = coverAspect,
                        coverBg = coverBg,
                        zoom = zoom,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )

                    // Bottom Details Card: Titles & Dismiss Hint
                    ViewerDetailsCard(
                        media = media,
                        sources = sources,
                        sourceIndex = sourceIndex,
                        onSelectSource = { sourceIndex = it },
                        zoom = zoom
                    )
                }
            }
        }
    }
}

/** A pinch that scales past this counts as zoomed for the background tap. */
private const val ZOOMED_THRESHOLD = 1.05f

/** Past this, a double-tap puts the artwork back instead of opening it further. */
private const val DOUBLE_TAP_RESET_THRESHOLD = 1.2f

private const val DOUBLE_TAP_SCALE = 2.4f
private const val MAX_ZOOM = 4.5f

/**
 * The viewer's zoom and pan.
 *
 * Held outside the composable so the three gestures that move it - pinch,
 * double-tap and the background tap - are methods with names rather than more
 * branches inside the viewer's body. [frameSize] is measured by the artwork,
 * because the pan limits are half the scaled overflow on each axis; the
 * constants this replaced (300f by 400f) were wrong on both a narrow phone and
 * a tablet.
 *
 * `internal` rather than file-private because the details card reads
 * [isZoomed] to word its gesture hint, and recomposing that hint on a pinch is
 * the card's job rather than the whole overlay's.
 */
internal class CoverZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set
    var frameSize by mutableStateOf(IntSize.Zero)

    val isZoomed: Boolean get() = scale > ZOOMED_THRESHOLD

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    /** The double-tap: open the artwork, or put a zoomed one back. */
    fun toggleDoubleTap() {
        if (scale > DOUBLE_TAP_RESET_THRESHOLD) reset() else scale = DOUBLE_TAP_SCALE
    }

    /**
     * One pinch frame.
     *
     * Half the overflow on each side: the image scales around its centre, so
     * that is exactly how far it can move before an edge shows.
     */
    fun applyGesture(pan: Offset, zoomChange: Float) {
        val newScale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        scale = newScale
        if (newScale > 1f) {
            val maxX = frameSize.width * (newScale - 1f) / 2f
            val maxY = frameSize.height * (newScale - 1f) / 2f
            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }
}

/** The artwork's own ratio, falling back to what the source is expected to be. */
private fun coverAspectOf(intrinsic: Size, source: CoverSource?): Float =
    if (intrinsic.width > 0f && intrinsic.height > 0f) {
        intrinsic.width / intrinsic.height
    } else {
        source?.fallbackAspect ?: DEFAULT_COVER_ASPECT
    }

/**
 * The viewer's header: what the artwork is, and the two ways out of it.
 *
 * The save button copies the CDN's own bytes rather than re-encoding what is on
 * screen, so what lands in the gallery is the highest-quality copy AniList
 * serves - and whichever source is displayed, so a banner saves a banner rather
 * than the poster it was switched from.
 */
@Composable
private fun CoverViewerTopBar(
    media: MediaNode,
    canSave: Boolean,
    saveState: CoverSaveState,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = "${media.formatLabel} · ${media.statusLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "HD",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canSave) {
                IconButton(
                    onClick = onSave,
                    enabled = saveState != CoverSaveState.SAVING,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .testTag("cover_save_button")
                ) {
                    SaveButtonIcon(saveState)
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = AppVectorIcons.Close,
                    contentDescription = "Close full screen",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** The save button's glyph: the whole acknowledgement for one save. */
@Composable
private fun SaveButtonIcon(state: CoverSaveState) {
    when (state) {
        CoverSaveState.SAVING -> ExpressiveLoadingIndicator(
            modifier = Modifier.size(18.dp),
            color = Color.White
        )
        CoverSaveState.SAVED -> Icon(
            imageVector = AppVectorIcons.CheckCircle,
            contentDescription = "Cover saved to your gallery",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        CoverSaveState.FAILED -> Icon(
            imageVector = AppVectorIcons.Warning,
            contentDescription = "Couldn't save the cover",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        CoverSaveState.IDLE -> Icon(
            imageVector = AppVectorIcons.Download,
            contentDescription = "Save cover to your gallery",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * The artwork between the header and the details card.
 *
 * [BoxWithConstraints] gives it the space the header and footer leave, and the
 * cover takes the largest box that fits it at its own ratio - the 340x440 frame
 * this replaced letterboxed a 2:3 cover on one axis, so the "full screen" viewer
 * drew a poster smaller than the hero it was opened from.
 */
@Composable
private fun CoverArtwork(
    media: MediaNode,
    painter: Painter,
    coverAspect: Float,
    coverBg: Color,
    zoom: CoverZoomState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val frameWidth = minOf(maxWidth, maxHeight * coverAspect)
        val frameHeight = frameWidth / coverAspect
        Box(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
                .shadow(
                    elevation = 24.dp,
                    shape = MaterialTheme.shapes.large,
                    ambientColor = coverBg,
                    spotColor = Color.Black
                )
                .clip(MaterialTheme.shapes.large)
                .background(coverBg)
                .onSizeChanged { zoom.frameSize = it }
                .pointerInput(zoom) {
                    detectTapGestures(onDoubleTap = { zoom.toggleDoubleTap() })
                }
                .pointerInput(zoom) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        zoom.applyGesture(pan, zoomChange)
                    }
                }
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offsetX
                    translationY = zoom.offsetY
                }
        ) {
            Image(
                painter = painter,
                contentDescription = media.displayName,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/** One save, from the CDN to the gallery. */
private suspend fun saveCover(context: Context, media: MediaNode, source: CoverSource): CoverSaveState {
    val title = if (source.label == "Banner") {
        "${media.displayName} banner"
    } else {
        media.displayName
    }
    return if (saveCoverToGallery(context = context, coverUrl = source.url, title = title)) {
        CoverSaveState.SAVED
    } else {
        CoverSaveState.FAILED
    }
}

/**
 * One full-size image the viewer can show, with the shape to assume before it loads.
 *
 * `internal` because the details card's source switch takes the same list the
 * viewer shows; the type travels with the image, not with the composable that
 * happens to declare it.
 */
internal data class CoverSource(
    val label: String,
    val url: String,
    val fallbackAspect: Float
)

/** One save's lifecycle: the button's glyph is the whole acknowledgement. */
private enum class CoverSaveState { IDLE, SAVING, SAVED, FAILED }

/** AniList's cover shape, used until the image's own dimensions arrive. */
private const val DEFAULT_COVER_ASPECT = 2f / 3f

/** AniList's banner shape (about 1900x400), used until the image reports its own. */
private const val DEFAULT_BANNER_ASPECT = 1900f / 400f
