package com.anisequel.ui.components.detail

import androidx.compose.animation.AnimatedVisibility
import com.anisequel.ui.components.expressive.ExpressiveMotion
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.englishName
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.isManga
import com.anisequel.data.model.pageUrl
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.model.romajiName
import com.anisequel.data.model.statusLabel
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.detail.info.COPY_CONFIRMATION_MILLIS
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.liveChartUrl
import com.anisequel.ui.components.openExternalUrlOrCopy
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
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // Measured rather than assumed: the pan limits used to be the constants 300f and
    // 400f, so a tall cover could be dragged past its own edge on a narrow phone and
    // a wide one could not be dragged far enough on a tablet.
    var coverSize by remember { mutableStateOf(IntSize.Zero) }

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
    val intrinsic = painter.intrinsicSize
    // The artwork's own ratio, so a rare non-2:3 cover - or a wide banner - draws
    // whole instead of letterboxing inside the poster's frame.
    val coverAspect = if (intrinsic.width > 0f && intrinsic.height > 0f) {
        intrinsic.width / intrinsic.height
    } else {
        source?.fallbackAspect ?: DEFAULT_COVER_ASPECT
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saveState by remember(media.id) { mutableStateOf(CoverSaveState.IDLE) }
    // Each source is a fresh view: keeping a 2.4x zoom from the poster while the
    // banner arrives would open the banner already cropped past its own edges -
    // and the pan limits were measured against the other image's frame.
    LaunchedEffect(source?.url) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
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
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            onDismiss()
                        }
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
                    // Top Bar: Format Badge, HD tag & Close Button
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
                            // The artwork, not a re-encode of what is on screen: the
                            // saver copies the CDN's own bytes, so what lands in the
                            // gallery is the highest-quality copy AniList serves - and
                            // whichever source is displayed, so a banner saves a
                            // banner rather than the poster it was switched from.
                            if (source != null) {
                                IconButton(
                                    onClick = {
                                        if (saveState == CoverSaveState.SAVING) return@IconButton
                                        saveState = CoverSaveState.SAVING
                                        scope.launch {
                                            val saved = saveCoverToGallery(
                                                context = context,
                                                coverUrl = source.url,
                                                title = if (source.label == "Banner") {
                                                    "${media.displayName} banner"
                                                } else {
                                                    media.displayName
                                                }
                                            )
                                            saveState = if (saved) {
                                                CoverSaveState.SAVED
                                            } else {
                                                CoverSaveState.FAILED
                                            }
                                        }
                                    },
                                    enabled = saveState != CoverSaveState.SAVING,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.18f))
                                        .testTag("cover_save_button")
                                ) {
                                    when (saveState) {
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

                    // Center Anime Cover Artwork with interactive Zoom & Pan
                    val coverBg = media.posterColor.toCoverColorOrNull() ?: MaterialTheme.colorScheme.surfaceContainerHighest
                    // The frame is fitted to the artwork rather than to a 340x440
                    // window: a 2:3 cover in that window letterboxed on one axis, so
                    // the "full screen" viewer drew a poster smaller than the hero
                    // it was opened from. `BoxWithConstraints` gives the space the
                    // header and footer leave, and the cover takes the largest box
                    // that fits it at its own ratio.
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
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
                                .onSizeChanged { coverSize = it }
                                .pointerInput(coverSize) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1.2f) {
                                                scale = 1f
                                                offsetX = 0f
                                                offsetY = 0f
                                            } else {
                                                scale = 2.4f
                                            }
                                        }
                                    )
                                }
                                .pointerInput(coverSize) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 4.5f)
                                        scale = newScale
                                        if (newScale > 1f) {
                                            // Half the overflow on each side: the
                                            // image scales around its centre, so this
                                            // is exactly how far it can move before
                                            // an edge shows.
                                            val maxX = coverSize.width * (newScale - 1f) / 2f
                                            val maxY = coverSize.height * (newScale - 1f) / 2f
                                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
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

                    // Bottom Details Card: Titles & Dismiss Hint
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = media.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Which AniList image is on screen. Only drawn when
                            // there is a choice: a title with no banner would
                            // otherwise get a single "Poster" pill that does
                            // nothing, which reads as a broken control.
                            if (sources.size > 1) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    sources.forEachIndexed { index, candidate ->
                                        ViewerPill(
                                            label = candidate.label,
                                            selected = index == sourceIndex,
                                            modifier = Modifier.weight(1f),
                                            onClick = { sourceIndex = index }
                                        )
                                    }
                                }
                            }

                            // Romaji & English Titles
                            val romaji = media.romajiName
                            val english = media.englishName

                            if (!romaji.isNullOrBlank() || !english.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!romaji.isNullOrBlank()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = "ROMAJI",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = romaji,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    if (!english.isNullOrBlank()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = "ENGLISH",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = english,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Where the reader can look at more of this title's
                            // artwork. AniList is the page this viewer was opened
                            // from; LiveChart is the only other site the app can
                            // honestly send them to for the schedule and its own
                            // poster, since it publishes no API to read.
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ViewerActionPill(
                                    icon = AppVectorIcons.OpenInNew,
                                    label = "AniList",
                                    modifier = Modifier.weight(1f),
                                    onClick = { openExternalUrlOrCopy(context, media.pageUrl) }
                                )
                                if (!media.isManga) {
                                    ViewerActionPill(
                                        icon = AppVectorIcons.CalendarClock,
                                        label = "LiveChart",
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            openExternalUrlOrCopy(context, media.liveChartUrl)
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (scale > 1.05f) "Double-tap to reset zoom · Tap anywhere to close" else "Pinch or double-tap to zoom · Tap anywhere to close",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One full-size image the viewer can show, with the shape to assume before it loads. */
private data class CoverSource(
    val label: String,
    val url: String,
    val fallbackAspect: Float
)

/**
 * One image source, as a selectable pill.
 *
 * A capsule in [ExpressiveShapes] rather than a Material segmented button: this
 * sits over artwork in a dialog, and the pill matches the cards behind it. The
 * selected state is the primary container, so the current image reads at a
 * glance without a second label.
 */
@Composable
private fun ViewerPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = ExpressiveShapes.pill,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
            .bouncyPress(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClickLabel = "Show the $label",
                onClick = onClick
            )
            .testTag("cover_source_${label.lowercase()}")
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** One outbound link from the viewer, wearing its destination's name. */
@Composable
private fun ViewerActionPill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = ExpressiveShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .bouncyPress(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClick = onClick
            )
            .testTag("cover_action_${label.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/** One save's lifecycle: the button's glyph is the whole acknowledgement. */
private enum class CoverSaveState { IDLE, SAVING, SAVED, FAILED }

/** AniList's cover shape, used until the image's own dimensions arrive. */
private const val DEFAULT_COVER_ASPECT = 2f / 3f

/** AniList's banner shape (about 1900x400), used until the image reports its own. */
private const val DEFAULT_BANNER_ASPECT = 1900f / 400f
