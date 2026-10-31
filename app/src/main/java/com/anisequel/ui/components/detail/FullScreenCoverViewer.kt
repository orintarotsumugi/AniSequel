package com.anisequel.ui.components.detail

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.anisequel.data.model.MediaNode
import com.anisequel.data.network.LiveChartArtwork
import com.anisequel.data.model.displayName
import com.anisequel.data.model.englishName
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.isManga
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.model.romajiName
import com.anisequel.data.model.statusLabel
import com.anisequel.data.repository.LiveChartCovers
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.liveChartUrl
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.detail.info.COPY_CONFIRMATION_MILLIS
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
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
 * ## LiveChart as a third source - the owner reversed the no-scrape rule
 *
 * This viewer used to carry only AniList's two images and deliberately no
 * LiveChart art, on the reasoning that LiveChart publishes no API and so reading
 * its posters would be scraping a site that has asked not to be consumed that way
 * (the full history is in [com.anisequel.ui.components.LiveChart] and NOTES.md).
 * The owner has since decided that LiveChart's posters - more numerous and higher
 * quality than AniList's for many titles - should be shown here. That is a
 * deliberate reversal, so it is done the narrow way: the app reads exactly one
 * thing off the search page, the best title match's `large.jpg` poster
 * ([com.anisequel.data.network.LiveChartPosterApi]), added lazily as a source only
 * once it arrives. AniList's poster and banner still paint immediately, the
 * outbound "LiveChart" link still goes to the same search page, and a failure to
 * fetch simply means there is no third pill.
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

    // LiveChart's poster and visuals, fetched off the UI thread after the dialog
    // opens so it never delays the AniList sources. Null until it arrives - or
    // forever, if the search finds no close match or the request fails.
    val liveChartArtwork = rememberLiveChartArtwork(media)

    val sources = remember(media.id, media.posterUrl, media.bannerImage, liveChartArtwork) {
        buildCoverSources(media, liveChartArtwork)
    }
    var sourceIndex by remember(media.id) { mutableIntStateOf(0) }
    // The LiveChart source is appended *after* the dialog opens, and it can also
    // come back as nothing on a later open. An index that outlived the list it was
    // pointing into left the viewer with a null source: a black rectangle where the
    // artwork should be, and no pill looking selected. Clamping on every size change
    // (and writing the clamp back) keeps the pills and the artwork in agreement.
    LaunchedEffect(sources.size) {
        if (sourceIndex > sources.lastIndex) sourceIndex = sources.lastIndex.coerceAtLeast(0)
    }
    val selectedIndex = sourceIndex.coerceIn(0, sources.lastIndex.coerceAtLeast(0))
    val source = sources.getOrNull(selectedIndex)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saveState by remember(media.id) { mutableStateOf(CoverSaveState.IDLE) }

    // The gallery's page state lives above the pager because the counter is in
    // the header and the save button saves what is on screen. pageCount follows
    // the selected source, so switching from a 4-image LiveChart gallery to the
    // single banner cannot leave the pager pointing at a page that no longer
    // exists - the pager clamps, and the effect below rewinds to the first one.
    val pagerState = rememberPagerState(
        pageCount = { source?.urls?.size ?: 1 }
    )
    val currentPage = pagerState.currentPage.coerceIn(0, (source?.urls?.lastIndex ?: 0).coerceAtLeast(0))
    val currentUrl = source?.urls?.getOrNull(currentPage)
    LaunchedEffect(source?.label) {
        // A new source starts at its first image, not wherever the previous one's
        // gallery was - the poster and the fourth LiveChart visual are not the
        // same position in any meaningful sense. Guarded because a source can be
        // null for a title with no artwork, and there is no pager to rewind
        // then - `scrollToPage` would wait on a layout that never arrives.
        if (source != null && pagerState.currentPage != 0) pagerState.scrollToPage(0)
        zoom.reset()
        saveState = CoverSaveState.IDLE
    }

    // Each image is a fresh view: keeping a 2.4x zoom from the poster while the
    // next page arrives would open it already cropped past its own edges - and
    // the pan limits were measured against the previous image's frame.
    LaunchedEffect(currentUrl) {
        zoom.reset()
        saveState = CoverSaveState.IDLE
    }

    // The way out, and the only one: fade and shrink first, then tell the host.
    //
    // `AnimatedVisibility`'s exit branch was dead code before this: every control
    // called the host's `onDismiss` directly, which removed the `Dialog` in the
    // same frame, so the artwork that fades in never faded out. The delay is the
    // exit spring's settle time; it is a fixed wait rather than a measured one
    // because a dialog dismissal that waits on an animation callback can outlive
    // the host's state and never fire.
    var closing by remember { mutableStateOf(false) }
    val requestDismiss: () -> Unit = {
        if (!closing) {
            closing = true
            isVisible = false
        }
    }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        delay(COVER_VIEWER_EXIT_MILLIS)
        onDismiss()
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
        onDismissRequest = requestDismiss,
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
                        if (zoom.isZoomed) zoom.reset() else requestDismiss()
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
                        // `FastSpatial`, not the slow `DefaultSpatial` the entrance
                        // uses: the dialog is torn down after a fixed
                        // [COVER_VIEWER_EXIT_MILLIS], and the first overshoot of the
                        // slower spring lands about there - so the artwork was being
                        // deleted mid-bounce. The exit has to settle inside its own
                        // window; the entrance does not.
                        animationSpec = ExpressiveMotion.FastSpatial
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
                    CoverViewerHeader(
                        media = media,
                        saveState = saveState,
                        source = source,
                        currentPage = currentPage,
                        onDismiss = requestDismiss,
                        startSave = {
                            saveState = CoverSaveState.SAVING
                            scope.launch {
                                saveState = saveCover(
                                    context = context,
                                    media = media,
                                    source = source,
                                    imageIndex = currentPage
                                )
                            }
                        }
                    )

                    // Center Anime Cover Artwork with interactive Zoom & Pan.
                    CoverViewerPager(
                        source = source,
                        pagerState = pagerState,
                        currentPage = currentPage,
                        zoom = zoom,
                        media = media,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    // Bottom Details Card: Titles & Dismiss Hint
                    ViewerDetailsCard(
                        media = media,
                        sources = sources,
                        sourceIndex = selectedIndex,
                        imageIndex = currentPage,
                        imageCount = source?.urls?.size ?: 0,
                        onSelectSource = { sourceIndex = it },
                        zoom = zoom
                    )
                }
            }
        }
    }
}

/**
 * The viewer's header: what the artwork is, and the two ways out of it.
 *
 * The counter and the save guard belong to the header rather than to the viewer
 * because both are answers to "what is on screen", and that is one question with
 * one answer: a label like `2 / 4` and a save button that refuses to run against
 * a page that does not exist are the same fact read twice. Keeping them here
 * also keeps the guard where the button it guards is.
 *
 * The save button copies the CDN's own bytes rather than re-encoding what is on
 * screen, so what lands in the gallery is the highest-quality copy AniList
 * serves - and whichever source is displayed, so a banner saves a banner rather
 * than the poster it was switched from.
 */
@Composable
private fun CoverViewerHeader(
    media: MediaNode,
    saveState: CoverSaveState,
    source: CoverSource?,
    currentPage: Int,
    onDismiss: () -> Unit,
    startSave: () -> Unit
) {
    val imageCount = source?.urls?.size ?: 0
    val onScreen = source?.urls?.getOrNull(currentPage) != null
    CoverViewerTopBar(
        media = media,
        canSave = onScreen,
        saveState = saveState,
        pageLabel = if (imageCount > 1) {
            "${currentPage + 1} / $imageCount"
        } else {
            null
        },
        onSave = {
            if (onScreen && saveState != CoverSaveState.SAVING) startSave()
        },
        onDismiss = onDismiss
    )
}

/**
 * The artwork: one pager page per image of the selected source.
 *
 * Extracted from [FullScreenCoverViewer] because the viewer is the dialog, the
 * header and the save state, while this is the gallery - a null source, a page
 * with no image, and the crossfade between two images of one source all belong
 * together. It also keeps the viewer itself readable: CodeFactor's complexity
 * ceiling was crossed when every one of those cases lived in the one function.
 *
 * The pager is the only thing attached to [pagerState], and the crossfade lives
 * *inside* a page on the image rather than around the pager - composing two
 * pagers for one state would attach that state twice. [modifier] carries the
 * caller's `weight(1f)`, because only the Column the viewer lays out in can
 * give it the space the header and details card leave.
 */
@Composable
private fun CoverViewerPager(
    source: CoverSource?,
    pagerState: PagerState,
    currentPage: Int,
    zoom: CoverZoomState,
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val coverBg = media.posterColor.toCoverColorOrNull()
        ?: MaterialTheme.colorScheme.surfaceContainerHighest
    if (source == null) {
        Box(modifier = modifier)
        return
    }
    HorizontalPager(
        state = pagerState,
        // A zoomed image owns the finger: letting the pager
        // steal a pan would make a 2.4x cover impossible to
        // inspect past its own edge.
        userScrollEnabled = !zoom.isZoomed,
        pageSpacing = 12.dp,
        modifier = modifier.testTag("cover_gallery")
    ) { page ->
        val url = source.urls.getOrNull(page)
        if (url == null) return@HorizontalPager
        AnimatedContent(
            targetState = url,
            transitionSpec = {
                fadeIn(ExpressiveMotion.FastEffects)
                    .togetherWith(fadeOut(ExpressiveMotion.FastEffects)) using null
            },
            contentKey = { it },
            label = "cover_viewer_image",
            modifier = Modifier.fillMaxSize()
        ) { activeUrl ->
            val painter = rememberAsyncImagePainter(model = activeUrl)
            CoverArtwork(
                media = media,
                painter = painter,
                // The artwork's own ratio, so a rare
                // non-2:3 cover - or a wide banner - draws
                // whole instead of letterboxing inside the
                // poster's frame.
                coverAspect = coverAspectOf(painter.intrinsicSize, source),
                coverBg = coverBg,
                zoom = zoom,
                // Both images are composed for the crossfade,
                // and the pan limits are half the scaled
                // overflow of one frame - so only the image on
                // screen may publish its size.
                reportsFrame = page == currentPage && activeUrl == url,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp)
            )
        }
    }
}

/**
 * The viewer's sources, in paint order, for one title.
 *
 * AniList's poster and banner first, because they are already cached and paint
 * before any request this function makes; LiveChart's gallery last, because it
 * arrives late and can be nothing at all. The owner reversed the earlier
 * no-scrape rule, so the gallery is appended only when the fetch returns one -
 * a null (failure, no match, manga) means no third pill, and a title with no
 * visuals falls back to its LiveChart poster rather than losing the source.
 *
 * Pure and separate from the composable so the ordering and the fallback are one
 * readable list rather than three branches inside the dialog's body.
 */
private fun buildCoverSources(
    media: MediaNode,
    liveChartArtwork: LiveChartArtwork?
): List<CoverSource> = buildList {
    media.posterUrl?.let {
        add(
            CoverSource(
                label = "Poster",
                urls = listOf(it),
                fallbackAspect = DEFAULT_COVER_ASPECT
            )
        )
    }
    // AniList banners are about 1900x400. The fallback is only ever read
    // for the frame before the image reports its real size.
    media.bannerImage?.let {
        add(
            CoverSource(
                label = "Banner",
                urls = listOf(it),
                fallbackAspect = DEFAULT_BANNER_ASPECT
            )
        )
    }
    liveChartArtwork?.let { artwork ->
        val urls = artwork.visualUrls.ifEmpty {
            listOfNotNull(artwork.posterUrl)
        }
        if (urls.isNotEmpty()) {
            add(
                CoverSource(
                    label = "LiveChart",
                    urls = urls,
                    fallbackAspect = DEFAULT_COVER_ASPECT
                )
            )
        }
    }
}

/**
 * The viewer's extra LiveChart gallery, arrived at late and off the UI thread.
 *
 * Mirrors how [com.anisequel.ui.components.detail.info] loads theme songs: the
 * scrape runs in `LiveChartPosterApi`'s own `withContext(Dispatchers.IO)`,
 * cached for the session by `LiveChartCovers`, and `produceState` is keyed on the
 * search URL so opening a different title cancels the previous fetch instead of
 * letting its artwork land here. A manga has no LiveChart entry to find, so it is
 * skipped outright and no request is spent.
 *
 * `null` is a real answer rather than a loading state to render: until (and unless)
 * artwork comes back, the viewer just shows its AniList sources.
 */
@Composable
private fun rememberLiveChartArtwork(media: MediaNode): LiveChartArtwork? {
    if (media.isManga) return null
    val searchUrl = media.liveChartUrl
    val titles = listOfNotNull(media.romajiName, media.englishName, media.displayName)
    return produceState(initialValue = null as LiveChartArtwork?, searchUrl) {
        value = LiveChartCovers.repository().artwork(searchUrl, titles)
    }.value
}

/**
 * The header's own row: the format chip, the counter, save and close.
 *
 * Presentation only - [CoverViewerHeader] above it decides *what* the counter says
 * and *whether* a save may run, so this file's rule holds: the row draws what it
 * is handed. The save button copies the CDN's own bytes rather than re-encoding
 * what is on screen, so a banner saves a banner rather than the poster it was
 * switched from.
 */
@Composable
private fun CoverViewerTopBar(
    media: MediaNode,
    canSave: Boolean,
    saveState: CoverSaveState,
    /** "2 / 4" while a multi-image gallery is selected, null for a single image. */
    pageLabel: String?,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    // One centred row with three anchored groups rather than a `SpaceBetween`
    // row: the counter is the only thing in the header whose position carries
    // meaning, and in a two-child row it drifted with the width of the format
    // chip - which is exactly the off-centre pill that was reported.
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = ExpressiveShapes.pill,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = "${media.formatLabel} · ${media.statusLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
            Surface(
                shape = ExpressiveShapes.pill,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "HD",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }

        if (pageLabel != null) {
            Surface(
                shape = ExpressiveShapes.pill,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("cover_page_counter")
            ) {
                Text(
                    text = pageLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
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

/** One save, from the CDN to the gallery. */
private suspend fun saveCover(
    context: Context,
    media: MediaNode,
    source: CoverSource?,
    imageIndex: Int
): CoverSaveState {
    val url = source?.urls?.getOrNull(imageIndex) ?: return CoverSaveState.FAILED
    val title = buildString {
        append(media.displayName)
        if (source.label == "Banner") append(" banner")
        // A gallery of four saved under one name is four unlabelled files in the
        // gallery; the index is what makes them a set rather than duplicates.
        if (source.urls.size > 1) append(" ").append(imageIndex + 1)
    }
    return if (saveCoverToGallery(context = context, coverUrl = url, title = title)) {
        CoverSaveState.SAVED
    } else {
        CoverSaveState.FAILED
    }
}

/**
 * One source the viewer can show, with the shape to assume before it loads.
 *
 * [urls] is a gallery rather than a single image because LiveChart answers with
 * many: its `/visuals` tab is the reason this source exists. Poster and Banner
 * still hold one URL each, and the pager simply has no second page to offer.
 *
 * `internal` because the details card's source switch takes the same list the
 * viewer shows; the type travels with the image, not with the composable that
 * happens to declare it.
 */
internal data class CoverSource(
    val label: String,
    val urls: List<String>,
    val fallbackAspect: Float
)

/** One save's lifecycle: the button's glyph is the whole acknowledgement. */
private enum class CoverSaveState { IDLE, SAVING, SAVED, FAILED }

/**
 * How long the close animation is given before the dialog is torn down.
 *
 * The fade and scale are springs, so this is a settle time rather than a duration;
 * 220ms is long enough for the exit to read and short enough that the back gesture
 * still feels immediate.
 */
private const val COVER_VIEWER_EXIT_MILLIS = 220L

/** AniList's banner shape (about 1900x400), used until the image reports its own. */
private const val DEFAULT_BANNER_ASPECT = 1900f / 400f
