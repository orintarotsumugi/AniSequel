package com.anisequel.ui.components.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.displayName
import com.anisequel.data.model.isManga
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.repository.CoverColorRepository
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.domain.color.ArgbHex
import com.anisequel.domain.list.ListStatuses
import com.anisequel.domain.ui.InfoPageStyle
import com.anisequel.domain.ui.PosterSource
import com.anisequel.domain.ui.SequelSeason
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.LocalBetterPosters
import com.anisequel.ui.components.cards.LocalPosterOverlays
import com.anisequel.ui.components.cards.LocalPosterSource
import com.anisequel.ui.components.cards.LocalPosterUniqueSeasons
import com.anisequel.ui.components.cards.cardPosterUrl
import com.anisequel.ui.components.cards.posterLookupTitle
import com.anisequel.ui.components.detail.info.AnimeInfoPage
import com.anisequel.ui.components.detail.info.DetailAppearanceLayer
import com.anisequel.ui.components.detail.info.NovaInfoPage
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveNavigationBar
import com.anisequel.ui.components.expressive.NavigationDestination
import com.anisequel.ui.components.rememberIsResumed
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.theme.CoverColors
import com.anisequel.ui.theme.DialogImmersiveEffect
import com.anisequel.ui.theme.LocalImmersiveMode
import com.anisequel.ui.theme.LocalInfoPageStyle
import com.anisequel.ui.theme.immersiveDisplayInsets
import com.anisequel.ui.viewmodel.QuickAddStatus
import com.anisequel.ui.viewmodel.quickAddKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One title's page, full screen.
 *
 * ## Why this is a dialog rather than a bottom sheet
 *
 * It used to be a `ModalBottomSheet`, and the drag handle was a promise the page
 * could not keep: a reader halfway down a long synopsis could pull the whole thing
 * away by accident, and the page never owned the top of the screen - it opened
 * under the status bar with the activity's bar behind it. A title is a destination,
 * not a peek: it has its own hero, its own artwork and its own way out, and the
 * reference client opens it as a page. The dialog draws the same content edge to
 * edge, with the hero under the status bar, and the only ways out are the explicit
 * close control and Back.
 *
 * It stays a dialog rather than a navigation route because six hosts own the
 * subject they open and none of them has a route to hand it to; a route would mean
 * moving that state, and the detail store, into navigation for a cosmetic win.
 * The dialog is still its own window, which is what lets a person profile or the
 * appearance editor stack above it without tearing it down.
 *
 * ## Two destinations, switched from a floating capsule
 *
 * The page, and the viewer's own entry when the page has write access to it. The
 * page used to be three tabs - story, specs, entry - which split one title's
 * information across two of them and left the reader to hold the halves in their
 * head; it is now one scroll (see [AnimeInfoPage]) and the capsule switches between
 * *reading* and *editing*, which is the only distinction left that means anything.
 */
@Composable
fun SequelDetailSheet(
    subject: AnimeDetailSubject?,
    onDismiss: () -> Unit,
    onAddToPlanning: (MissedSequel) -> Unit,
    /**
     * Fetches the heavy fields for the opened title.
     *
     * Takes the media's AniList type as well as its id because the id alone does
     * not identify a title - `anime/1` and `manga/1` are different entries sharing a
     * number - and the sheet is reachable from a manga list as well as an anime one.
     */
    onLoadDetail: (Int, String?) -> Unit,
    /**
     * Fetches the followed users' entries for the opened title.
     *
     * Separate from [onLoadDetail] because it is a separate request with its own
     * failure mode: a signed-out session or a spent request budget must not stop
     * the detail page from drawing. See
     * [com.anisequel.ui.viewmodel.MediaDetailStore.loadFriends].
     */
    onLoadFriends: (Int, String?) -> Unit = { _, _ -> },
    isDetailLoading: Boolean,
    /**
     * The followed users who have this title on their list: active entries first,
     * then best rating - every status, including planning and dropped.
     *
     * Already mapped and sorted by the repository - see
     * [com.anisequel.data.model.MediaFriend]. Empty with the detail still loading
     * draws the card's checking row rather than a hole; empty once it has loaded
     * draws no card at all. Empty is also the ordinary answer: a viewer who follows
     * nobody, or a title nobody they follow has opened.
     */
    friends: List<MediaFriend> = emptyList(),
    /** Whether the independent followed-users request is still in flight. */
    isFriendsLoading: Boolean = false,
    canWriteToAniList: Boolean,
    modifier: Modifier = Modifier,
    onAddToWatching: (MissedSequel) -> Unit = {},
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true,
    /**
     * Opens a related title in place of the current one.
     *
     * The host owns the subject, so "go to the next title in this franchise" is a
     * state change on its side rather than a second sheet. See
     * [com.anisequel.ui.components.detail.info.InfoRelationsSection] for why this
     * replaces rather than stacks.
     */
    onOpenMedia: (MediaNode) -> Unit = {},
    /**
     * Opens a studio's page from the credits on this title.
     *
     * Hosted by the caller rather than by this sheet, because a studio is a
     * *destination* and the sheet is a window over whatever opened it: the host
     * closes the sheet and navigates, so that returning lands on the title the
     * reader came from rather than on a sheet floating over a screen it does not
     * belong to. A `ModalBottomSheet` lives in its own window and would otherwise
     * stay drawn on top of the studio page.
     */
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
    /**
     * Opens a character's profile sheet from the cast row.
     *
     * A callback for the same reason [onOpenStudio] is: this sheet holds no
     * `NavController` and no store of its own. Whoever drew the sheet owns the
     * [com.anisequel.ui.viewmodel.PersonProfileStore], so the cast card's tap becomes
     * a fetch there rather than a second sheet stacked on this one.
     */
    onOpenCharacter: ((Int) -> Unit)? = null,
    /** The same, for the crew row. */
    onOpenStaff: ((Int) -> Unit)? = null,
    /**
     * The quick-add state for every title this sheet can show, keyed by media id.
     *
     * A map rather than one value because the sheet walks relations in place: the
     * reader can follow a prequel from the title they opened, and each title keeps
     * its own in-flight flags and confirmation. Supplied by the screen's
     * [com.anisequel.ui.viewmodel.PlanningAddHost]; empty on a host that has no
     * repository to write with (the demo), where the row then offers the sign-in
     * line instead.
     */
    quickAddStatuses: Map<MediaCoverKey, QuickAddStatus> = emptyMap(),
    /**
     * Adds the opened title to the viewer's Planning list.
     *
     * Only reached for a title that is not a dashboard recommendation - a sequel
     * keeps the dashboard's own write, which also recomputes the card behind the
     * sheet. See [DetailActionRow].
     */
    onAddMediaToPlanning: (MediaNode) -> Unit = {},
    onAddMediaToWatching: (MediaNode) -> Unit = {},
    /**
     * Read/write access to the viewer's own entry, or null when the sheet is
     * read-only.
     *
     * Null removes the editing destination entirely rather than drawing it disabled:
     * an editor that cannot save looks identical to one that can until the moment it
     * silently does nothing.
     *
     * Every host with a session passes one, and they are all the same one
     * ([com.anisequel.ui.components.detail.RepositoryEntryEditor] for the Library,
     * the studio page and the profile; `DashboardViewModel` for the dashboard,
     * which reloads its own counts after a write). That sameness is the point: the
     * tab holds the *viewer's* entry for this media on every screen, so opening a
     * title from your library, from a studio's filmography or from a stranger's
     * favourites grid is the same sheet with the same capabilities, and only a
     * demo or signed-out session gets it read-only. It used to be dashboard-only,
     * which made the same title editable from one list and not from another.
     */
    entryEditor: EntryEditor? = null
) {
    if (subject == null) return
    val media = subject.media

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themePreferences = remember(context) { ThemePreferences(context) }
    val themeSettings by themePreferences.settings.collectAsStateWithLifecycle(
        initialValue = ThemePreferences.ThemeSettings(
            themeMode = ThemeMode.DEFAULT,
            useDynamicColor = true
        )
    )
    val immersive = LocalImmersiveMode.current
    // Read at composition, used by the extraction below: the colour has to come from
    // the picture the reader is actually shown. With BetterPosters on that is btttr's
    // rendered art, not the AniList cover, and the two palettes can be unrelated.
    val posterSource = LocalPosterSource.current
    val posterOverlays = LocalPosterOverlays.current
    val betterPosters = LocalBetterPosters.current
    val uniqueSeasons = LocalPosterUniqueSeasons.current

    // The page's scroll position is hoisted out of `AnimatedContent`, and so is the
    // in-progress edit below, for the same reason: `AnimatedContent` disposes the
    // destination it animates away from. Held here, coming back from the editor lands
    // on the card the reader left, and the draft in the editor survives the round
    // trip - it used to be re-fetched from AniList on every switch, which is a network
    // request per tap on the capsule.
    // Keyed on the media id, which is what makes walking a franchise work: tapping a
    // relation swaps the subject, and without the key the new title would inherit the
    // old one's scroll offset - so following a prequel dropped the reader into the
    // middle of a page they had never seen. `key` rather than putting the id in a
    // `remember` key, because `rememberLazyListState` takes no keys and the state has
    // to be *discarded*, not re-read.
    //
    // The **type** is part of every key here, not just the id. AniList numbers
    // `anime/` and `manga/` independently, so a relation from one namespace to the
    // other can arrive carrying the same number: keyed on the id alone, the new
    // title inherited the old one's scroll, its half-typed draft and its saved
    // destination - and the entry tab then saved the *old* title's progress under
    // the new title's id.
    val infoListState = key(subject.mediaId, subject.media.type) { rememberLazyListState() }

    val editState = remember(subject.mediaId, subject.media.type) { EntryEditState() }
    LaunchedEffect(subject.mediaId, subject.media.type, entryEditor) {
        if (entryEditor != null) editState.load(entryEditor, subject.mediaId)
    }

    // The ticker stops with the window. A sheet left open behind another app is
    // still composed, and this loop was keeping a request every ten minutes for a
    // page nobody could see - the one live-refresh loop in the app that no
    // visibility gate covered. The first load is deliberately not gated: the answer
    // is what the page needs the moment it comes back, and asking once costs less
    // than repainting an empty hero.
    val detailRefreshResumed = rememberIsResumed()
    LaunchedEffect(subject.mediaId, subject.media.type, detailRefreshResumed) {
        onLoadDetail(subject.mediaId, subject.media.type)
        onLoadFriends(subject.mediaId, subject.media.type)
        if (!detailRefreshResumed) return@LaunchedEffect

        // Keep the open title current. The store republishes its cache immediately
        // and performs a network refresh only after its ten-minute freshness window expires.
        while (true) {
            delay(com.anisequel.ui.viewmodel.MediaDetailStore.DETAIL_REFRESH_INTERVAL_MILLIS)
            onLoadDetail(subject.mediaId, subject.media.type)
        }
    }

    // The colour comes from the pixels, and the effect waits for the stored
    // preferences before it writes any.
    //
    // It used to read `themeSettings` during the sheet's first composition and
    // apply the theme in the same `LaunchedEffect` that loaded the detail. The
    // flow starts at its initial value - `isLoaded = false`, feature off - so a
    // sheet opened while DataStore was still reading saw the feature as off and
    // never re-ran the effect when the real settings arrived. The result was the
    // reported "sometimes it works": whether the app recoloured depended on
    // whether the preferences happened to beat the tap. Keying on `isLoaded` is
    // the fix: the effect is inert until the real values are there, then runs
    // exactly once for this title.
    //
    // Extraction runs even when the anime layer is off, because the hero uses
    // the same seed for its backdrop and shadow. It is one 96px decode from
    // Coil's cache - the hero is already loading the full image - and it means
    // the artwork's colour is right whether or not the global recolour is on.
    LaunchedEffect(
        subject.mediaId,
        subject.media.type,
        media.posterUrl,
        posterSource,
        posterOverlays,
        uniqueSeasons,
        themeSettings.isLoaded,
        themeSettings.animeThemeEnabled,
        themeSettings.animeThemeRetainColor
    ) {
        if (!themeSettings.isLoaded) return@LaunchedEffect

        // The picture on screen, resolved the same way `cardPosterUrl` resolves it:
        // the same source, the same season rule, the same cache. A sequel season
        // keeps its own cover, so the sheet must not read a palette from a btttr
        // poster the grid is not drawing - keeping the two in step is why
        // [posterLookupTitle] exists. The lookup is a cache hit in practice, because
        // the card that opened this sheet already asked for the same id and variant;
        // a miss falls back to AniList's cover rather than delaying the colour.
        val useBetterPoster = posterSource == PosterSource.BETTER_POSTERS &&
            !(uniqueSeasons && SequelSeason.isSequel(posterLookupTitle(media)))
        val shownPoster = if (useBetterPoster) {
            betterPosters?.posterUrl(
                subject.mediaId,
                posterOverlays,
                skipSequels = uniqueSeasons
            ) ?: media.posterUrl
        } else {
            media.posterUrl
        }
        val palette = CoverColorRepository.extractPalette(context, shownPoster)
        val seed = palette?.seed
        if (palette != null) {
            // Both keys, because the surfaces read different ones: the sheet knows
            // the resolved URL, and the hero still falls back to the AniList cover.
            CoverColors.put(shownPoster, palette.seed)
            CoverColors.put(media.posterUrl, palette.seed)
            palette.accent?.let { accent ->
                CoverColors.putAccent(shownPoster, accent)
                CoverColors.putAccent(media.posterUrl, accent)
            }
        }

        if (!themeSettings.animeThemeEnabled) return@LaunchedEffect

        // Fallback order, most specific first: the cover's own pixels, then
        // AniList's dominant colour (which is still the cover, just computed
        // elsewhere), then the brand. A grey cover legitimately produces no
        // seed, and the app should still recolour to *something* the reader
        // recognises rather than doing nothing.
        val colorHex = seed?.let { ArgbHex.format(it) }
            ?: media.posterColor
            ?: "#0057B7"

        themeWrite {
            themePreferences.applyAnimeColorLayer(
                colorHex = colorHex,
                retain = themeSettings.animeThemeRetainColor
            )
        }
    }

    // The revert timer starts when the sheet is *closed*, not while it is open.
    // It used to be a `LaunchedEffect` here, which the dismiss disposed - so the
    // "5 Minutes" and "10 Minutes" choices never fired at all, and a temporary
    // colour outlived every title it was borrowed for. The delay now belongs to a
    // process-scoped job in `ThemePreferences`, and it ends the in-memory layer
    // rather than a stored flag, so a colour on a timer cannot survive a restart
    // the way the stored one did.

    // One progress value drives both directions, so Back and the close control
    // settle the page out instead of removing the dialog in the middle of a frame.
    // The entrance and the close are the sheet's, not the title's: following a
    // relation replaces what the page shows without replacing the page, and
    // keying either animation by the subject made the new title replay the open
    // over a transparent window (or cancel an exit already in flight).
    //
    // Created below, after the flight: the page waits for the copy's picture (see
    // `entranceGate`), so the two can start on the same frame.

    val finishDismiss: () -> Unit = {
        if (themeSettings.animeThemeRetainColor) {
            themePreferences.clearAnimeColorLayer()
        } else {
            themePreferences.releaseAnimeColorLayer(themeSettings.animeThemeRevertDuration)
        }
        onDismiss()
    }
    // The copy that flies between the card and the page.
    //
    // The rectangle is taken once, on the sheet's first composition: the press
    // that opened it recorded it in the list's window a frame earlier, and by now
    // it is the only thing that remembers where the reader's finger was. Taken,
    // not peeked at, so a following title cannot reuse it.
    //
    // `cardPosterUrl` here rather than the effect's `shownPoster`: this is the URL
    // the hero draws, and the copy has to land as the same picture. It may still
    // be the AniList cover for a frame or two while btttr answers, which is why
    // `DetailCoverFlight.take` also accepts a rectangle recorded under the
    // AniList cover - see its fallback.
    val posterUrl = cardPosterUrl(media) ?: media.posterUrl
    // The copy is on stage only while it travels: at rest the page's own poster is
    // authoritative, because it carries the border and shadow the copy does not
    // and it scrolls with the page.
    val coverFlight = rememberDetailCoverFlight(listOf(posterUrl, media.posterUrl))
    // The page and the copy leave on the same frame.
    //
    // The copy is decoded before the flight starts, and the page's entrance is
    // held until then: without the gate the page faded in over a rectangle that
    // was still downloading, and the poster popped into it halfway through the
    // open. A page with no flight open - no press behind it, or a cover that will
    // not decode - reads `true` immediately and keeps its own entrance.
    val entrance = rememberSheetEntrance(released = coverFlight.entranceGate)
    val handleDismiss = rememberSheetClose(
        entrance = entrance,
        onDismissed = finishDismiss,
        travel = coverFlight.travel,
        onTravelStart = coverFlight::begin
    )

    // The palette's destination, hoisted above the dialog so the dialog's own
    // dismiss request can see it. It used to live inside the dialog content,
    // where `onDismissRequest` could not read it, so Back in the appearance
    // editor dismissed the *whole title page* and dropped the reader back on
    // the list they opened it from. Back now closes the editor first, exactly
    // like its own Back control; only the next Back leaves the title.
    var showAppearance by rememberSaveable(subject.mediaId, subject.media.type) {
        mutableStateOf(false)
    }
    // Back unwinds the topmost detail layer first: appearance editor, My Entry,
    // then the title page. Keep this state outside Dialog content so its window
    // dismissal callback can make the same decision as the visible controls.
    var showEntrySheet by rememberSaveable(subject.mediaId, subject.media.type) {
        mutableStateOf(false)
    }
    // How much of the entry overlay sits below the viewport: 0 is fully open, 1 is
    // off-screen. The height itself is now fixed - see the sheet's `graphicsLayer`
    // below - so this value is a draw-phase translation and nothing here re-measures
    // the form while it moves.
    val sheetOffset = remember(subject.mediaId, subject.media.type) { Animatable(1f) }
    // A drag writes these synchronously; the Animatable only owns the settle. The
    // handle used to `launch { snapTo(...) }` once per pointer event, so one flick
    // queued a dozen coroutines that each cancelled the previous one and the sheet
    // visibly lagged the finger.
    var sheetDragging by remember(subject.mediaId, subject.media.type) {
        mutableStateOf(false)
    }
    var dragFraction by remember(subject.mediaId, subject.media.type) {
        mutableFloatStateOf(1f)
    }
    val closeEntrySheet: () -> Unit = {
        scope.launch {
            sheetOffset.animateTo(1f, ExpressiveMotion.SettleSpatial)
            showEntrySheet = false
        }
    }
    LaunchedEffect(showEntrySheet) {
        if (showEntrySheet) {
            // Opened at full height, not stopped at a peek. The form is status,
            // progress, score, two dates, the private switch, the note and its
            // pinned action bar - a sheet that stopped at 60% drew three rows and
            // an action bar and looked like a finished, short form, with every
            // field below Score hidden behind a scroll nothing suggested. The
            // handle still collapses it to a 60% peek for a reader who wants the
            // page behind it.
            sheetDragging = false
            sheetOffset.snapTo(1f)
            sheetOffset.animateTo(0f, ExpressiveMotion.DefaultSpatial)
        } else {
            sheetDragging = false
            sheetOffset.snapTo(1f)
        }
    }

    // Opening My Entry is also the moment to retry an entry load that never landed.
    // The entry is fetched when the title opens, so a request that failed while the
    // reader was still reading left the editor on its error card - and the only
    // retry was inside that card. `load` is a no-op once a draft exists, so this
    // cannot spend a second request on a healthy form.
    LaunchedEffect(showEntrySheet, subject.mediaId, subject.media.type, entryEditor) {
        if (showEntrySheet && entryEditor != null && editState.loadError != null) {
            editState.load(entryEditor, subject.mediaId)
        }
    }

    Dialog(
        onDismissRequest = {
            when {
                showAppearance -> showAppearance = false
                showEntrySheet -> closeEntrySheet()
                else -> handleDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            // A tap beside the page is not a way out of it: the page is the whole
            // screen, so there is no "beside", and a stray tap on the dim edge used
            // to close the sheet the reader had just scrolled.
            dismissOnClickOutside = false,
            // The artwork is meant to reach the status bar. Everything that must
            // stay clear of the bars applies its own inset; the shell does not.
            decorFitsSystemWindows = false
        )
    ) {
        // A dialog is its own window, so the activity's immersive setting has to be
        // applied here too - see `DialogImmersiveEffect`.
        DialogImmersiveEffect()
        // The entrance belongs to the Surface, not to the content inside it. The
        // Surface is what paints the page's background, so fading and scaling only
        // its children left an opaque flat rectangle of app colour on screen for
        // the first and last frames of every open and close - the page appeared to
        // flash a blank sheet before it arrived, and again after it left. Fading
        // the whole window is what makes it read as the page settling in over the
        // list.
        //
        // The scrim sits in the same Box so it is drawn behind the page and can
        // follow the same spring; see `SheetScrim`.
        CompositionLocalProvider(
            LocalDetailCoverReveal provides coverFlight.reveal,
            LocalDetailCoverTarget provides coverFlight.pageTarget
        ) {
        SequelDetailPage(
            subject = subject,
            onDismiss = handleDismiss,
            modifier = modifier,
            entrance = entrance,
            coverFlight = coverFlight,
            immersive = immersive,
            isDetailLoading = isDetailLoading,
            friends = friends,
            isFriendsLoading = isFriendsLoading,
            canWriteToAniList = canWriteToAniList,
            holdDurationSeconds = holdDurationSeconds,
            swipeEnabled = swipeEnabled,
            onAddToPlanning = onAddToPlanning,
            onAddToWatching = onAddToWatching,
            quickAddStatus = quickAddStatuses[quickAddKey(subject.media)] ?: QuickAddStatus(),
            onAddMediaToPlanning = onAddMediaToPlanning,
            onAddMediaToWatching = onAddMediaToWatching,
            onOpenMedia = onOpenMedia,
            onOpenStudio = onOpenStudio,
            onOpenCharacter = onOpenCharacter,
            onOpenStaff = onOpenStaff,
            themePreferences = themePreferences,
            themeSettings = themeSettings,
            entryEditor = entryEditor,
            editState = editState,
            showAppearance = showAppearance,
            onShowAppearanceChange = { showAppearance = it },
            showEntrySheet = showEntrySheet,
            onShowEntrySheetChange = { showEntrySheet = it },
            sheetOffset = sheetOffset,
            sheetDragging = sheetDragging,
            onSheetDraggingChange = { sheetDragging = it },
            dragFraction = dragFraction,
            onDragFractionChange = { dragFraction = it },
            closeEntrySheet = closeEntrySheet,
            scope = scope,
            infoListState = infoListState,
            posterUrl = posterUrl
        )
        }
        }
    }
