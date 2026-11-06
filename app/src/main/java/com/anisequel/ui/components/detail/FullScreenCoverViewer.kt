package com.anisequel.ui.components.detail

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.rememberAsyncImagePainter
import com.anisequel.data.model.MediaNode
import com.anisequel.data.network.LiveChartArtwork
import com.anisequel.data.model.displayName
import com.anisequel.data.model.englishName
import com.anisequel.data.model.isManga
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.model.romajiName
import com.anisequel.data.repository.LiveChartCovers
import com.anisequel.ui.components.liveChartUrl
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.detail.info.COPY_CONFIRMATION_MILLIS
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.theme.DialogImmersiveEffect
import com.anisequel.ui.theme.LocalImmersiveMode
import com.anisequel.ui.theme.immersiveDisplayInsets
import kotlinx.coroutines.coroutineScope
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
    /**
     * The tapped poster's window bounds, or null when the viewer was opened from
     * something that is not a poster.
     *
     * The viewer is a separate dialog window, so Compose's shared-element APIs -
     * which work within one `Composition` - cannot move the artwork between them.
     * This rectangle is the hand-off instead: the first frame of the dialog draws
     * the artwork at exactly the poster's position and size, then springs it to
     * the frame the pager measured. Dismissing runs the same spring backwards.
     * Null keeps the old fade, because there is nothing to grow out of.
     */
    origin: Rect? = null,
    onDismiss: () -> Unit
) {
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
    // A dialog is its own window, so the Activity hiding its bars does not reach
    // this one: in full screen the viewer used to come back with the status and
    // navigation bars visible, which is the one place a "full-screen viewer"
    // cannot have them. See `DialogImmersiveEffect`.
    val immersive = LocalImmersiveMode.current
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

    // The two values the window is drawn from, both `Animatable` so the closing
    // coroutine can *await the spring* rather than guess its settle time.
    //
    // `presence` is the scrim and the chrome; `reveal` is the artwork's journey
    // between the poster and the pager frame. They are separate because the scrim
    // is a fade and the artwork is a spring, and the fixed 220ms teardown this
    // replaced was the compromise that made one of them cut off early. A dialog
    // teardown that waits on a callback used to be the risk; here the wait is
    // inside the dialog's own composition, which cannot be disposed until it
    // calls `onDismiss`.
    val presence = remember(media.id) { Animatable(0f) }
    val reveal = remember(media.id) { Animatable(0f) }
    // The pager's measured frame, in this window's coordinates. It arrives after
    // the first layout pass, so the entrance starts from that effect rather than
    // from composition.
    var artworkFrame by remember(media.id) { mutableStateOf<Rect?>(null) }
    // The travelling copy of the artwork. It is only meaningful during the
    // entrance and the exit; once it has landed, the pager underneath is the
    // same picture at the same place, so this goes away instead of covering it.
    var overlayVisible by remember(media.id) { mutableStateOf(origin != null) }
    var closing by remember(media.id) { mutableStateOf(false) }
    val requestDismiss: () -> Unit = {
        if (!closing) closing = true
    }

    // The reveal's choreography lives in two private composables below
    // (`CoverRevealEffects` and `CoverCloseEffects`) so the decision points for
    // entrance and exit sit next to the springs they drive, rather than inside a
    // composable that is already carrying the sources, the pager and the dialog.
    CoverRevealEffects(
        origin = origin,
        artworkFrame = artworkFrame,
        closing = closing,
        presence = presence,
        reveal = reveal,
        onOverlayVisible = { overlayVisible = it }
    )
    CoverCloseEffects(
        closing = closing,
        origin = origin,
        artworkFrame = artworkFrame,
        presence = presence,
        reveal = reveal,
        onOverlayVisible = { overlayVisible = it },
        onDismiss = onDismiss
    )
    // Saved and failed are acknowledgements, not modes: they return to the idle
    // glyph after the same window the Extras card's "Link copied" uses.
    CoverSaveAcknowledgement(saveState = saveState) {
        saveState = CoverSaveState.IDLE
    }

    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        val dialogView = LocalView.current
        LaunchedEffect(Unit) {
            // The platform draws a hard, undimmable scrim behind a dialog, which
            // used to cut to 60% black on the frame the viewer opened while the
            // app's own scrim faded in behind it. Zeroing the window's dim lets
            // the animated scrim below be the only one, and lets the exit fade
            // back to the page instead of being unmasked in one frame.
            (dialogView.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        DialogImmersiveEffect(immersive)

        val coverBg = media.posterColor.toCoverColorOrNull()
            ?: MaterialTheme.colorScheme.surfaceContainerHighest
        // The picture that grows out of the poster: the first source at its first
        // image, which is exactly what the pager opens on.
        val overlayUrl = sources.firstOrNull()?.urls?.firstOrNull()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (zoom.isZoomed) zoom.reset() else requestDismiss()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // The scrim is its own layer, read in the draw phase: `presence` is an
            // `Animatable`, and reading it in composition would recompose this
            // whole dialog on every fade frame for one alpha value.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = presence.value }
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // With the bars hidden this window's own status-bar
                    // padding is zero, so the header would sit on the glass
                    // and under a cutout - the shared helper restores both,
                    // exactly as it does for the Activity's root.
                    .then(Modifier.immersiveDisplayInsets(immersive))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .graphicsLayer { alpha = presence.value },
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
                    onFrameBounds = { frame -> artworkFrame = frame },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Hidden until the travelling copy arrives. On the way
                        // out this reverses, so the pager recedes with the
                        // shrinking artwork instead of snapping in behind it.
                        .graphicsLayer { alpha = reveal.value }
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

            val start = origin
            val target = artworkFrame
            if (overlayVisible && start != null && target != null && overlayUrl != null) {
                CoverRevealOverlay(
                    url = overlayUrl,
                    coverBg = coverBg,
                    origin = start,
                    target = target,
                    progress = { reveal.value }
                )
            }
        }
    }
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
    /** The measured frame of the image on screen, for the dialog's reveal. */
    onFrameBounds: (Rect) -> Unit,
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
                onFrameBounds = onFrameBounds,
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

/** AniList's banner shape (about 1900x400), used until the image reports its own. */
private const val DEFAULT_BANNER_ASPECT = 1900f / 400f

/**
 * The reveal's entrance, and the first half of its landing.
 *
 * Separate from the close in [CoverCloseEffects] because the two run at different
 * times and are driven by different state - the entrance waits for the artwork
 * frame to exist, the exit waits for the reader to ask - so keeping them together
 * only made one composable with two sets of launch conditions.
 */
@Composable
private fun CoverRevealEffects(
    origin: Rect?,
    artworkFrame: Rect?,
    closing: Boolean,
    presence: Animatable<Float, *>,
    reveal: Animatable<Float, *>,
    onOverlayVisible: (Boolean) -> Unit
) {
    LaunchedEffect(Unit) {
        if (origin == null) {
            // Nothing to grow out of: fade the artwork in over the scrim, the
            // way this used to enter.
            launch { reveal.animateTo(1f, ExpressiveMotion.DefaultSpatial) }
        }
        presence.animateTo(1f, ExpressiveMotion.FastEffects)
    }
    LaunchedEffect(artworkFrame) {
        if (closing) return@LaunchedEffect
        val target = artworkFrame ?: return@LaunchedEffect
        if (origin == null || target.width <= 0f || target.height <= 0f) return@LaunchedEffect
        if (reveal.value < 1f) {
            reveal.animateTo(1f, ExpressiveMotion.DefaultSpatial)
            // Landed. The pager has been drawing underneath the whole time; it is
            // now exactly where the overlay is, so removing the overlay is a no-op
            // on screen and stops a second full-size image being kept around.
            onOverlayVisible(false)
        }
    }
}

/**
 * The reveal's exit, and the dialog's teardown.
 *
 * The scrim and the artwork run together: the exit has to read as a return, not
 * as a second performance. Tearing the dialog down happens only once both have
 * settled, which is why this is a `suspend`-driven coroutine and not a timer.
 */
@Composable
private fun CoverCloseEffects(
    closing: Boolean,
    origin: Rect?,
    artworkFrame: Rect?,
    presence: Animatable<Float, *>,
    reveal: Animatable<Float, *>,
    onOverlayVisible: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        coroutineScope {
            if (origin != null && artworkFrame != null) {
                onOverlayVisible(true)
            }
            // Both halves run together: the scrim leaving while the artwork is
            // still shrinking would show the page through a moving picture.
            launch { reveal.animateTo(0f, ExpressiveMotion.FastSpatial) }
            presence.animateTo(0f, ExpressiveMotion.FastEffects)
        }
        onDismiss()
    }
}

/**
 * Clears a saved/failed acknowledgement back to the idle glyph.
 *
 * The same window the Extras card's "Link copied" uses - long enough to be read,
 * short enough that the next tap on the download glyph is not ambiguous.
 */
@Composable
private fun CoverSaveAcknowledgement(
    saveState: CoverSaveState,
    onReset: () -> Unit
) {
    LaunchedEffect(saveState) {
        if (saveState != CoverSaveState.SAVED && saveState != CoverSaveState.FAILED) {
            return@LaunchedEffect
        }
        delay(COPY_CONFIRMATION_MILLIS)
        onReset()
    }
}
