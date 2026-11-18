package com.anisequel.ui.components.detail

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.anisequel.data.model.displayName
import com.anisequel.data.model.englishName
import com.anisequel.data.model.isManga
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.model.romajiName
import com.anisequel.data.network.LiveChartArtwork
import com.anisequel.data.repository.LiveChartCovers
import com.anisequel.domain.ui.PosterSource
import com.anisequel.domain.ui.SequelSeason
import com.anisequel.ui.components.cards.LocalBetterPosters
import com.anisequel.ui.components.cards.LocalPosterSource
import com.anisequel.ui.components.cards.betterPosterGallery
import com.anisequel.ui.components.cards.posterLookupTitle
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.detail.info.COPY_CONFIRMATION_MILLIS
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.liveChartUrl
import com.anisequel.ui.theme.DialogImmersiveEffect
import com.anisequel.ui.theme.LocalImmersiveMode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Full-screen immersive artwork viewer for one title.
 *
 * ## Every image the title's providers actually have
 *
 * The viewer draws the poster the reader tapped, and offers the rest of what the
 * three artwork sources hold as a pager: AniList's key visual, its banner, btttr's
 * overlay renders and LiveChart's visuals tab. Switching source rewinds the pager,
 * and the save button saves whichever image is on screen rather than a fixed URL.
 *
 * ## BetterPosters is a gallery, not a pill
 *
 * btttr renders the *same* key art at four overlay combinations (see
 * [com.anisequel.data.network.btttrVariantOverlays]). The viewer used to keep one
 * URL - the reader's configured variant - so the other three renders were
 * unreachable from inside the app that already knew their addresses. They are pages
 * of the BetterPosters source now, chosen variant first. See
 * [com.anisequel.ui.components.cards.betterPosterGallery] for why the viewer asks
 * btttr directly rather than through `cardPosterUrl`, and why that is not gated on
 * the reader's card source.
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
    // opens so it never delays the AniList sources. Empty until it arrives - or
    // forever, if the search finds no close match or the request fails.
    val liveChartArtwork = rememberLiveChartArtwork(media)

    // btttr's four renders of this title, the reader's configured overlay first.
    // Resolved here rather than inside `buildCoverSources` because that function is
    // pure: the lookup is a composable effect, and the viewer's whole source list is
    // built from its answer so the pills and the pager cannot disagree.
    val betterPosters = betterPosterGallery(media)

    // Which provider the reader's *cards* draw. It only decides what the viewer
    // opens on and in what order the source pills sit, never what is on offer.
    val readerPosterSource = LocalPosterSource.current

    val sources = remember(
        media.id,
        media.posterUrl,
        media.bannerImage,
        liveChartArtwork,
        betterPosters,
        readerPosterSource
    ) {
        buildCoverSources(
            media = media,
            betterPosters = betterPosters,
            liveChartArtwork = liveChartArtwork,
            betterPostersLead = readerPosterSource == PosterSource.BETTER_POSTERS
        )
    }

    // Which source is on screen - by label rather than by index.
    //
    // Index was a defect waiting to happen: the BetterPosters gallery and
    // LiveChart's both arrive after the dialog opens and are inserted into the
    // list, so `0` stopped meaning "the poster" the moment one landed, and the
    // artwork the reader was already looking at silently became a different
    // picture. A label survives that insertion, and the pills, the pager, the
    // counter and the save button all read this one answer.
    var selectedLabel by remember(media.id) {
        mutableStateOf(
            if (readerPosterSource == PosterSource.BETTER_POSTERS) {
                PosterSource.BETTER_POSTERS.displayName
            } else {
                ANILIST_POSTER
            }
        )
    }
    // A provider the reader is on but btttr has no art for resolves to the first
    // source there is: no pill looks selected for a moment, and the AniList cover
    // is what the card drew anyway.
    val selectedIndex = sources.indexOfFirst { it.label == selectedLabel }.takeIf { it >= 0 } ?: 0
    val source = sources.getOrNull(selectedIndex)
    val images = source?.images.orEmpty()
    // Which season btttr's render is *not*, when it is the series artwork. The
    // viewer shows those renders for every season now, so this is the one fact the
    // caption and the saved file name need to stay honest about it.
    //
    // Two sources, because neither is complete: the title carries most of them
    // ("Frieren Season 2"), and ARM carries the ones the name hides
    // ("Nisemonogatari"). ARM's answer comes from the mapping the gallery request
    // already made, so this costs no second lookup.
    val titleSeason = seriesSeasonOf(media, source)
    val posterApi = LocalBetterPosters.current
    val armSeason by produceState<Int?>(
        initialValue = null,
        posterApi,
        media.id,
        source?.label
    ) {
        value = if (source?.label == PosterSource.BETTER_POSTERS.displayName) {
            posterApi?.seasonFor(media.id)
        } else {
            null
        }
    }
    val seriesSeason = listOfNotNull(titleSeason, armSeason).maxOrNull()

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
    // the selected source, so switching from a 4-image btttr gallery to the
    // single banner cannot leave the pager pointing at a page that no longer
    // exists - the pager clamps, and the effect below rewinds to the first one.
    val pagerState = rememberPagerState(pageCount = { images.size.coerceAtLeast(1) })
    val currentPage = pagerState.currentPage.coerceIn(0, images.lastIndex.coerceAtLeast(0))
    val currentImage = images.getOrNull(currentPage)
    val currentUrl = currentImage?.url
    LaunchedEffect(source?.label) {
        // A new source starts at its first image, not wherever the previous one's
        // gallery was - the poster and the fourth btttr variant are not the same
        // position in any meaningful sense. Guarded because a source can be null
        // for a title with no artwork, and there is no pager to rewind then -
        // `scrollToPage` would wait on a layout that never arrives.
        if (source != null && pagerState.currentPage != 0) pagerState.scrollToPage(0)
        zoom.reset()
    }

    // Each image is a fresh view: keeping a 2.4x zoom from the poster while the
    // next page arrives would open it already cropped past its own edges - and
    // the pan limits were measured against the previous image's frame.
    LaunchedEffect(currentUrl) {
        zoom.reset()
        // A save that is still running owns the button's glyph. Clearing the state
        // on a swipe would replace its spinner with the idle download icon and
        // re-enable a second tap for a file that is already being written.
        if (saveState != CoverSaveState.SAVING) saveState = CoverSaveState.IDLE
    }

    // Three values, because the window has three things that must not move as one:
    //
    // - `presence` is the backdrop behind everything - the artwork at full bleed,
    //   blurred, and the wash over it.
    // - `reveal` is the artwork's journey between the poster and the pager frame,
    //   and the pager's own visibility during it.
    // - `chrome` is the header and the sheet.
    //
    // They were two (a scrim and the artwork) until the viewer went full screen,
    // and tying the chrome to the scrim is what made the entrance look wrong: the
    // header and the sheet assembled themselves over a picture still a fraction of
    // its size, so the eye had two things arriving at once. The chrome now follows
    // the artwork - and leads it on the way out. The fixed 220ms teardown this all
    // replaced was the older compromise: a dialog teardown that waits on a callback
    // used to be the risk; here the wait is inside the dialog's own composition,
    // which cannot be disposed until it calls `onDismiss`.
    val presence = remember(media.id) { Animatable(0f) }
    val reveal = remember(media.id) { Animatable(0f) }
    val chrome = remember(media.id) { Animatable(0f) }
    // The pager's measured frame, in this window's coordinates. It arrives after
    // the first layout pass, so the entrance starts from that effect rather than
    // from composition.
    var artworkFrame by remember(media.id) { mutableStateOf<Rect?>(null) }
    // The travelling copy of the artwork. It is only meaningful during the
    // entrance and the exit; once it has landed, the pager underneath is the
    // same picture at the same place, so this goes away instead of covering it.
    var overlayVisible by remember(media.id) { mutableStateOf(origin != null) }
    var closing by remember(media.id) { mutableStateOf(false) }
    // Whether the header and the sheet are on screen. The artwork is the subject
    // and the furniture is optional: a tap anywhere on the picture puts the two
    // away, and a second tap brings them back. It is deliberately not a dismissal
    // gesture - a reader inspecting the art used to lose the viewer entirely when
    // they tapped the picture, which made the one thing this screen is for the one
    // thing it could not do. Closing is still the close button, the system back
    // gesture, or the dialog's outside-tap.
    var chromeShown by remember(media.id) { mutableStateOf(true) }
    val toggleChrome: () -> Unit = { chromeShown = !chromeShown }
    val requestDismiss: () -> Unit = {
        if (!closing) closing = true
    }

    // A zoom owns the screen. The chrome would sit over the part of the picture
    // the reader pinched to inspect, so it steps aside for the duration and comes
    // back on the next tap - the same trade every photo viewer makes. Observed
    // through a snapshot flow rather than keyed on `isZoomed`: a pinch updates the
    // scale every frame, and a recomposition of the dialog per frame would re-read
    // the pager and the source list for a boolean that changes twice.
    LaunchedEffect(zoom) {
        snapshotFlow { zoom.isZoomed }.collect { zoomed ->
            if (zoomed) chromeShown = false
        }
    }

    // The reveal's choreography lives in two private composables below
    // (`CoverRevealEffects` and `CoverCloseEffects`) so the decision points for
    // entrance and exit sit next to the springs they drive, rather than inside a
    // composable that is already carrying the sources, the pager and the dialog.
    CoverRevealEffects(
        origin = origin,
        artworkFrame = artworkFrame,
        hasArtwork = currentUrl != null,
        closing = closing,
        presence = presence,
        reveal = reveal,
        chrome = chrome,
        onOverlayVisible = { overlayVisible = it }
    )
    CoverCloseEffects(
        closing = closing,
        origin = origin,
        artworkFrame = artworkFrame,
        presence = presence,
        reveal = reveal,
        chrome = chrome,
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
            // The whole point of this window is that it owns the display. Without
            // this the decor view keeps fitting the content to the system bars, so
            // the artwork stopped at the bar edges on a phone that showed them and
            // the "full-screen" viewer was a full-screen window with a letterboxed
            // picture inside it. Compose documents this flag as the pair for
            // `usePlatformDefaultWidth = false` for exactly this reason.
            decorFitsSystemWindows = false,
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (zoom.isZoomed) zoom.reset() else toggleChrome()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // The artwork behind everything: the picture on screen, cropped to the
            // display and blurred. This is what makes the screen read as one
            // full-bleed picture instead of a poster on a grey page - the letterbox
            // on either side of a 2:3 cover is the same image at full bleed, so
            // there is no empty band anywhere the reader can see.
            CoverViewerBackdrop(
                url = currentUrl,
                coverBg = coverBg,
                presence = { presence.value },
                // The backdrop drifts against the swipe. Read in the draw phase
                // like `presence`: this is a full-window image, and recomposing it
                // per scroll frame to move it a few pixels is what makes a gallery
                // stutter on the one screen that should feel like glass.
                pageOffset = { pagerState.currentPageOffsetFraction },
                modifier = Modifier.matchParentSize()
            )

            // The artwork itself, filling the window: with the decor no longer
            // fitting system bars the pager owns the whole display, so a tall cover
            // runs under the status bar and the details sheet and is as large as the
            // screen allows. `alpha = reveal.value` keeps it hidden until the
            // travelling copy has arrived - and on the way out it recedes with the
            // shrinking artwork instead of snapping in behind it.
            CoverViewerPager(
                source = source,
                pagerState = pagerState,
                currentPage = currentPage,
                zoom = zoom,
                media = media,
                onTap = {
                    if (zoom.isZoomed) zoom.reset() else chromeShown = !chromeShown
                },
                onFrameBounds = { frame -> artworkFrame = frame },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = reveal.value }
            )

            // The chrome. A Column over the artwork rather than around it: the
            // header and the sheet are glass over the picture, and the empty
            // `Spacer` between them owns no pointer input, so a tap or a swipe in
            // the middle of the screen reaches the pager underneath.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = chrome.value }
            ) {
                // The two pieces of furniture slide out on their own axes: the
                // header leaves upward and the sheet downward, so hiding them reads
                // as opening the page up rather than as the screen fading. The
                // `Spacer` between them stays and owns no pointer input, so the
                // artwork underneath keeps every gesture while they are gone.
                AnimatedVisibility(
                    visible = chromeShown,
                    enter = fadeIn(ExpressiveMotion.FastEffects) +
                        slideInVertically(ExpressiveMotion.FastSpatialOffset) { height -> -height / 3 },
                    exit = fadeOut(ExpressiveMotion.FastEffects) +
                        slideOutVertically(ExpressiveMotion.FastSpatialOffset) { height -> -height / 3 },
                    label = "cover_viewer_header"
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
                                    imageIndex = currentPage,
                                    seriesSeason = seriesSeason
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom sheet: titles, the source switch, outbound links, hint.
                AnimatedVisibility(
                    visible = chromeShown,
                    enter = fadeIn(ExpressiveMotion.FastEffects) +
                        slideInVertically(ExpressiveMotion.FastSpatialOffset) { height -> height / 3 },
                    exit = fadeOut(ExpressiveMotion.FastEffects) +
                        slideOutVertically(ExpressiveMotion.FastSpatialOffset) { height -> height / 3 },
                    label = "cover_viewer_details"
                ) {
                    ViewerDetailsCard(
                        media = media,
                        sources = sources,
                        sourceIndex = selectedIndex,
                        image = currentImage,
                        imageIndex = currentPage,
                        imageCount = images.size,
                        seriesSeason = seriesSeason,
                        onSelectSource = { index ->
                            sources.getOrNull(index)?.label?.let { selectedLabel = it }
                        },
                        zoom = zoom
                    )
                }
            }

            val start = origin
            val target = artworkFrame
            // The *on-screen* image travels, not the first URL in the list: with
            // btttr's gallery leading, those are different pictures, and growing the
            // wrong one out of the tapped poster was a visible jump at the hand-off.
            if (overlayVisible && start != null && target != null && currentUrl != null) {
                CoverRevealOverlay(
                    url = currentUrl,
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
 * pagers for one state would attach that state twice.
 */
@Composable
private fun CoverViewerPager(
    source: CoverSource?,
    pagerState: PagerState,
    currentPage: Int,
    zoom: CoverZoomState,
    media: MediaNode,
    /** A single tap on the artwork: reset a zoom, otherwise dismiss. */
    onTap: () -> Unit,
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
        // No gap between pages: the next image is a full screen away, and a visible
        // seam in the middle of a swipe is the one thing that stops a gallery
        // reading as one picture replacing another.
        pageSpacing = 0.dp,
        modifier = modifier.testTag("cover_gallery")
    ) { page ->
        val image = source.images.getOrNull(page)
        if (image == null) return@HorizontalPager
        AnimatedContent(
            targetState = image.url,
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
                onTap = onTap,
                // Both images are composed for the crossfade,
                // and the pan limits are half the scaled
                // overflow of one frame - so only the image on
                // screen may publish its size.
                reportsFrame = page == currentPage && activeUrl == image.url,
                onFrameBounds = onFrameBounds,
                modifier = Modifier.fillMaxSize()
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
 * artwork comes back, the viewer just shows its AniList and btttr sources.
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
 * The season btttr's render for [media] actually depicts, or null when the render is
 * the entry's own art.
 *
 * btttr addresses one IMDb id per series, so a Season 2 entry gets the series
 * picture - which is season one's key art with the overlays the reader chose. The
 * viewer offers it anyway (see [com.anisequel.ui.components.cards.betterPosterGallery])
 * and this is what lets it say so, rather than drawing season one's art under season
 * two's name.
 *
 * The number comes from the title first, because that is what the card, the caption
 * and the file name all already have, and a reader renaming a title in AniList should
 * move the label with it. [armSeason] covers the titles whose name says nothing:
 * "Nisemonogatari" is season two of its series and carries no number at all, which is
 * the entry that would otherwise be labelled as if the picture were its own. When the
 * two disagree the larger wins, because either naming a later season means the render
 * is not this entry's art.
 */
internal fun seriesSeasonOf(
    media: MediaNode,
    source: CoverSource?,
    armSeason: Int? = null
): Int? =
    if (source?.label == PosterSource.BETTER_POSTERS.displayName) {
        listOfNotNull(
            SequelSeason.numberIn(posterLookupTitle(media))?.takeIf { it >= 2 },
            armSeason?.takeIf { it >= 2 }
        ).maxOrNull()
    } else {
        null
    }

/**
 * The name a saved render is filed under.
 *
 * A gallery of four saved under one name is four unlabelled files in the gallery, so
 * a btttr variant is named after its overlay and an AniList pair after its index.
 *
 * The two "series" cases exist because of the season rule. A sequel's btttr render is
 * not that season's art, and the title alone cannot say so: a Save from "Frieren
 * Season 2" produced "Frieren Season 2 clean poster", which claimed something the
 * picture is not. The name now carries "- series poster" and the overlay in brackets,
 * so a season's save is recognisable as the series artwork next to the AniList cover
 * saved from the same title.
 */
internal fun coverSaveName(
    displayName: String,
    source: CoverSource?,
    image: CoverImage?,
    imageIndex: Int,
    seriesSeason: Int?
): String = buildString {
    append(displayName)
    if (source?.label == ANILIST_BANNER) append(" banner")
    if (seriesSeason != null) append(" - series poster")
    val variant = image?.variant
    when {
        variant != null && seriesSeason != null ->
            append(" (").append(variant.lowercaseFirst()).append(")")
        variant != null -> append(" ").append(variant.lowercaseFirst())
        (source?.images?.size ?: 0) > 1 -> append(" ").append(imageIndex + 1)
    }
}

/** One save, from the CDN to the gallery. */
private suspend fun saveCover(
    context: Context,
    media: MediaNode,
    source: CoverSource?,
    imageIndex: Int,
    seriesSeason: Int?
): CoverSaveState {
    val image = source?.images?.getOrNull(imageIndex) ?: return CoverSaveState.FAILED
    val title = coverSaveName(
        displayName = media.displayName,
        source = source,
        image = image,
        imageIndex = imageIndex,
        seriesSeason = seriesSeason
    )
    return if (saveCoverToGallery(context = context, coverUrl = image.url, title = title)) {
        CoverSaveState.SAVED
    } else {
        CoverSaveState.FAILED
    }
}

/** "Full poster" -> "full poster", for a file name rather than a label. */
private fun String.lowercaseFirst(): String =
    replaceFirstChar { character -> character.lowercaseChar() }


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
    /** False for a title with no artwork at all: nothing will ever land. */
    hasArtwork: Boolean,
    closing: Boolean,
    presence: Animatable<Float, *>,
    reveal: Animatable<Float, *>,
    chrome: Animatable<Float, *>,
    onOverlayVisible: (Boolean) -> Unit
) {
    LaunchedEffect(Unit) {
        // The backdrop comes up first and on its own: it is the stage the artwork
        // travels over, and fading it at the same speed as everything else made the
        // entrance read as three unrelated fades.
        launch { presence.animateTo(1f, ExpressiveMotion.FastEffects) }
        if (origin == null || !hasArtwork) {
            // Nothing to grow out of, or nothing to grow into: fade the artwork and
            // the chrome in together, the way this used to enter.
            launch { reveal.animateTo(1f, ExpressiveMotion.FastEffects) }
            chrome.animateTo(1f, ExpressiveMotion.FastEffects)
        }
    }
    LaunchedEffect(artworkFrame) {
        if (closing) return@LaunchedEffect
        val target = artworkFrame ?: return@LaunchedEffect
        if (origin == null || target.width <= 0f || target.height <= 0f) return@LaunchedEffect
        if (reveal.value < 1f) {
            // `SettleSpatial`, not the bouncier default: this travel ends on the
            // frame the picture will occupy for the rest of the session, and a
            // spring that carried it past that frame drew the artwork larger than
            // the display and then shrank it back.
            reveal.animateTo(1f, ExpressiveMotion.SettleSpatial)
            // Landed. The pager has been drawing underneath the whole time; it is
            // now exactly where the overlay is, so removing the overlay is a no-op
            // on screen and stops a second full-size image being kept around.
            onOverlayVisible(false)
        }
        // The chrome arrives with the artwork, not before it.
        chrome.animateTo(1f, ExpressiveMotion.FastEffects)
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
    chrome: Animatable<Float, *>,
    onOverlayVisible: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        coroutineScope {
            // The chrome leaves first, and finishes leaving: the exit is a return
            // to the page, and a header and a sheet still fading out in front of a
            // picture that is already travelling read as two exits that disagree.
            chrome.animateTo(0f, ExpressiveMotion.FastEffects)
            if (origin != null && artworkFrame != null) {
                onOverlayVisible(true)
            }
            // Both halves run together: the backdrop leaving while the artwork is
            // still shrinking would show the page through a moving picture.
            launch { reveal.animateTo(0f, ExpressiveMotion.SettleSpatial) }
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
