package com.anisequel.ui.components.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.displayName
import com.anisequel.data.model.isManga
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.repository.CoverColorRepository
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.data.repository.InfoBackdropTint
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
import com.anisequel.ui.components.cards.posterLookupTitle
import com.anisequel.ui.components.detail.info.AnimeInfoPage
import com.anisequel.ui.components.detail.info.InfoAppearanceEditor
import com.anisequel.ui.components.detail.info.NovaInfoPage
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveNavigationBar
import com.anisequel.ui.components.expressive.NavigationDestination
import com.anisequel.ui.theme.CoverColors
import com.anisequel.ui.theme.DialogImmersiveEffect
import com.anisequel.ui.theme.LocalImmersiveMode
import com.anisequel.ui.theme.LocalInfoPageStyle
import com.anisequel.ui.theme.immersiveDisplayInsets
import com.anisequel.ui.viewmodel.QuickAddStatus
import com.anisequel.ui.viewmodel.quickAddKey
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
    val themeSettings by themePreferences.settings.collectAsState(
        initial = ThemePreferences.ThemeSettings(
            themeMode = ThemeMode.DEFAULT,
            useDynamicColor = false
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

    LaunchedEffect(subject.mediaId, subject.media.type) {
        onLoadDetail(subject.mediaId, subject.media.type)
        onLoadFriends(subject.mediaId, subject.media.type)

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
    val entrance = remember(subject.mediaId, subject.media.type) { Animatable(0f) }
    var isClosing by remember(subject.mediaId, subject.media.type) { mutableStateOf(false) }
    LaunchedEffect(subject.mediaId, subject.media.type) {
        entrance.animateTo(1f, ExpressiveMotion.SettleSpatial)
    }

    val finishDismiss: () -> Unit = {
        if (themeSettings.animeThemeRetainColor) {
            themePreferences.clearAnimeColorLayer()
        } else {
            themePreferences.releaseAnimeColorLayer(themeSettings.animeThemeRevertDuration)
        }
        onDismiss()
    }
    val handleDismiss: () -> Unit = {
        if (!isClosing) isClosing = true
    }

    // The exit belongs to this media identity, not to the composition-wide scope.
    // If a relation replaces the subject while the page is animating out, this
    // effect is cancelled with the old id/type and cannot call the old host's
    // onDismiss against the newly opened title.
    LaunchedEffect(subject.mediaId, subject.media.type, isClosing) {
        if (!isClosing) return@LaunchedEffect
        entrance.animateTo(0f, ExpressiveMotion.SettleSpatial)
        finishDismiss()
    }

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
        Surface(
            modifier = modifier
                .fillMaxSize()
                // The entrance belongs to the Surface, not to the content inside
                // it. The Surface is what paints the page's background, so fading
                // and scaling only its children left an opaque flat rectangle of
                // app colour on screen for the first and last frames of every open
                // and close - the page appeared to flash a blank sheet before it
                // arrived, and again after it left. Fading the whole window is what
                // makes it read as the page settling in over the list.
                .graphicsLayer {
                    alpha = entrance.value
                    val scale = 0.985f + (0.015f * entrance.value)
                    scaleX = scale
                    scaleY = scale
                    translationY = (1f - entrance.value) * 22.dp.toPx()
                }
                .testTag("sequel_detail_sheet"),
            color = MaterialTheme.colorScheme.surface
        ) {
        // Info stays composed underneath My Entry. The editor is a real overlay,
        // not a second destination: its draft and the Info page's scroll state both
        // remain alive while the reader moves the sheet.
        val destinations = buildList {
            add(NavigationDestination("Info", AppVectorIcons.Info, "detail_tab_info"))
            if (entryEditor != null) {
                add(NavigationDestination("My Entry", AppVectorIcons.EditEntry, "detail_tab_edit"))
            }
        }
        val selected = if (showEntrySheet && destinations.size > 1) 1 else 0

        // The capsule's measured height is also the page's bottom clearance. It
        // travels to the sheet's top edge while My Entry is open, then settles back
        // into its normal dock position when the overlay closes.
        var dockHeightPx by remember { mutableStateOf(0) }
        val dockHeight = with(LocalDensity.current) { dockHeightPx.toDp() }
        val navigationInset = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        val navClearance = if (destinations.size > 1) {
            (if (dockHeightPx > 0) dockHeight else 112.dp) + DOCK_GAP +
                if (immersive) 12.dp else 0.dp
        } else {
            navigationInset + 28.dp + if (immersive) 12.dp else 0.dp
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            val density = LocalDensity.current
            val containerHeightPx = with(density) { maxHeight.toPx() }

            Box(modifier = Modifier.fillMaxSize()) {
                if (LocalInfoPageStyle.current == InfoPageStyle.NOVA) {
                    NovaInfoPage(
                        subject = subject,
                        isDetailLoading = isDetailLoading,
                        canWriteToAniList = canWriteToAniList,
                        holdDurationSeconds = holdDurationSeconds,
                        swipeEnabled = swipeEnabled,
                        onAddToPlanning = onAddToPlanning,
                        onAddToWatching = onAddToWatching,
                        quickAddStatus = quickAddStatuses[quickAddKey(subject.media)]
                            ?: QuickAddStatus(),
                        friends = friends,
                        onAddMediaToPlanning = onAddMediaToPlanning,
                        onAddMediaToWatching = onAddMediaToWatching,
                        onClose = handleDismiss,
                        onOpenMedia = onOpenMedia,
                        onOpenStudio = onOpenStudio,
                        onOpenCharacter = onOpenCharacter,
                        onOpenStaff = onOpenStaff,
                        onEditEntry = { if (entryEditor != null) showEntrySheet = true },
                        onOpenAppearance = { showAppearance = true },
                        modifier = Modifier.fillMaxSize(),
                        listState = infoListState,
                        bottomContentPadding = navClearance,
                        infoBackdropSource = themeSettings.infoBackdropSource,
                        infoBackdropBlur = themeSettings.infoBackdropBlur,
                        infoBackdropTint = themeSettings.infoBackdropTint
                    )
                } else {
                    AnimeInfoPage(
                        subject = subject,
                        isDetailLoading = isDetailLoading,
                        canWriteToAniList = canWriteToAniList,
                        holdDurationSeconds = holdDurationSeconds,
                        swipeEnabled = swipeEnabled,
                        onAddToPlanning = onAddToPlanning,
                        onAddToWatching = onAddToWatching,
                        quickAddStatus = quickAddStatuses[quickAddKey(subject.media)]
                            ?: QuickAddStatus(),
                        friends = friends,
                        onAddMediaToPlanning = onAddMediaToPlanning,
                        onAddMediaToWatching = onAddMediaToWatching,
                        onClose = handleDismiss,
                        onOpenMedia = onOpenMedia,
                        onOpenStudio = onOpenStudio,
                        onOpenCharacter = onOpenCharacter,
                        onOpenStaff = onOpenStaff,
                        onEditEntry = { if (entryEditor != null) showEntrySheet = true },
                        onOpenAppearance = { showAppearance = true },
                        modifier = Modifier.fillMaxSize(),
                        listState = infoListState,
                        bottomContentPadding = navClearance,
                        infoBackdropSource = themeSettings.infoBackdropSource,
                        infoBackdropBlur = themeSettings.infoBackdropBlur,
                        infoBackdropTint = themeSettings.infoBackdropTint
                    )
                }

                if (destinations.size > 1) {
                    ExpressiveNavigationBar(
                        destinations = destinations,
                        selectedIndex = selected,
                        onSelect = { index ->
                            if (index == 1) showEntrySheet = true else closeEntrySheet()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { dockHeightPx = it.height }
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = if (immersive) 12.dp else 0.dp)
                            .graphicsLayer {
                                if (showEntrySheet) {
                                    // Read the live offset here so the capsule follows
                                    // the finger during a drag instead of only
                                    // catching up when the sheet settles.
                                    val hidden = if (sheetDragging) {
                                        dragFraction
                                    } else {
                                        sheetOffset.value
                                    }
                                    translationY = -(
                                        (1f - hidden) * containerHeightPx +
                                            dockHeightPx + 12.dp.toPx()
                                        )
                                    alpha = (hidden / 0.4f).coerceIn(0f, 1f)
                                }
                            }
                    )
                }

                if (showEntrySheet && entryEditor != null) {
                    // The sheet opens at the full height of the window and is
                    // translated down from there. Its three stops are the open
                    // position, the 60% peek its handle drags to, and closed;
                    // it used to *open* on the peek, which is why the form looked
                    // cut down to its first three rows.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // The full height is laid out once and the sheet is moved
                            // in the draw phase. Sizing it to `1f - offset` made every
                            // frame of the open animation re-measure and re-flow the
                            // form: the rows squashed while the sheet rose, and the
                            // pinned Save bar slid under the reader's thumb.
                            .fillMaxHeight()
                            .align(Alignment.BottomCenter)
                            .graphicsLayer {
                                val hidden = if (sheetDragging) {
                                    dragFraction
                                } else {
                                    sheetOffset.value
                                }
                                translationY = hidden * containerHeightPx
                            }
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            )
                            // Claim the overlay's own area. A Column has no pointer
                            // handler of its own, so a tap on the sheet's padding was
                            // hit-tested straight through to the Info page composed
                            // underneath it - opening a relation card or scrolling the
                            // synopsis behind the editor. One handler here is enough
                            // to make the sheet the hit target for every point it
                            // covers; it consumes nothing, so the fields inside it
                            // still receive their own events.
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent()
                                    }
                                }
                            }
                            .statusBarsPadding()
                            .immersiveDisplayInsets(immersive)
                            .imePadding()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = 24.dp)
                            .testTag("detail_my_entry_sheet")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .pointerInput(containerHeightPx, showEntrySheet) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            // Start from wherever the sheet actually
                                            // is, so grabbing it mid-spring does not
                                            // jump it back to its resting offset.
                                            dragFraction = sheetOffset.value
                                            sheetDragging = true
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            if (containerHeightPx > 0f) {
                                                dragFraction = (
                                                    dragFraction + dragAmount / containerHeightPx
                                                    ).coerceIn(0f, 1f)
                                            }
                                        },
                                        onDragEnd = {
                                            // Three stops: open, the 60% peek, and
                                            // closed. The peek is the resting height
                                            // the 2.4 sheet documented; the sheet now
                                            // *opens* past it, so reaching it is a
                                            // choice rather than the first thing the
                                            // reader has to undo.
                                            val target = when {
                                                dragFraction < 0.18f -> 0f
                                                dragFraction < 0.62f -> 0.4f
                                                else -> 1f
                                            }
                                            scope.launch {
                                                // Hand the drag's last position to the
                                                // animatable before the gesture state
                                                // stops drawing from it, or the sheet
                                                // snaps back for one frame.
                                                sheetOffset.snapTo(dragFraction)
                                                sheetDragging = false
                                                if (target >= 1f) {
                                                    sheetOffset.animateTo(
                                                        1f,
                                                        ExpressiveMotion.SettleSpatial
                                                    )
                                                    showEntrySheet = false
                                                } else {
                                                    sheetOffset.animateTo(
                                                        target,
                                                        ExpressiveMotion.DefaultSpatial
                                                    )
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch {
                                                sheetOffset.snapTo(dragFraction)
                                                sheetDragging = false
                                                sheetOffset.animateTo(
                                                    0f,
                                                    ExpressiveMotion.SettleSpatial
                                                )
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(50)
                            ) {}
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "My Entry",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = listOfNotNull(
                                        media.displayName,
                                        editState.draft?.status?.let {
                                            ListStatuses.label(it, media.isManga)
                                        }
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("detail_edit_subtitle")
                                )
                            }
                            IconButton(
                                onClick = closeEntrySheet,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                                    .testTag("detail_edit_close")
                            ) {
                                Icon(
                                    imageVector = AppVectorIcons.Close,
                                    contentDescription = "Close My Entry"
                                )
                            }
                        }
                        // `weight(1f)`, not the default `fillMaxSize()`. A
                        // Column measures non-weighted children against an
                        // unbounded height, so the tab's own `weight(1f)` scroll
                        // area and its pinned action bar were being measured
                        // against infinity: the fields drew wherever they landed
                        // and the bar sat directly under the third row, which is
                        // exactly the cut-down form in the report. Weighting it
                        // hands the tab the space between the header and the
                        // bottom of the sheet, which is what makes the full form
                        // - dates, private switch, notes and the action bar -
                        // scroll inside a bounded box.
                        DetailEditTab(
                            media = media,
                            editor = entryEditor,
                            state = editState,
                            scope = scope,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

            // Last, so it covers the capsule as well as the page: while the
            // appearance editor is open there is one way back, and a bar that
            // still switched pages underneath it would be a second.
            //
            // The editor writes the same preferences the page reads, so moving a
            // chip changes the entire page behind this overlay and the miniature
            // in front of it at the same time. That is the live preview: it is
            // the page, not a copy of it.
            if (showAppearance) {
                InfoAppearanceEditor(
                    media = media,
                    source = themeSettings.infoBackdropSource,
                    blur = themeSettings.infoBackdropBlur,
                    tint = themeSettings.infoBackdropTint,
                    onSelectSource = { chosen ->
                        scope.launch { themePreferences.setInfoBackdropSource(chosen) }
                    },
                    onSelectBlur = { chosen ->
                        scope.launch { themePreferences.setInfoBackdropBlur(chosen) }
                    },
                    onSelectTint = { chosen ->
                        scope.launch { themePreferences.setInfoBackdropTint(chosen) }
                    },
                    onReset = {
                        scope.launch {
                            themePreferences.setInfoBackdropSource(InfoBackdropSource.DEFAULT)
                            themePreferences.setInfoBackdropBlur(InfoBackdropBlur.DEFAULT)
                            themePreferences.setInfoBackdropTint(InfoBackdropTint.DEFAULT)
                        }
                    },
                    onClose = { showAppearance = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        }
    }

/**
 * A theme write, where a failure is cosmetic rather than a screen.
 *
 * Opening a title writes the cover's colour into the theme preferences, and
 * closing it schedules the revert. Both are `DataStore` round trips inside a
 * coroutine the sheet owns - and a `DataStore` write answers a full disk or a
 * corrupted file by *throwing*. An uncaught throw there does not fail the colour;
 * it kills the coroutine, reaches the thread's handler and closes the app, which
 * was the reported force-stop on opening a title. The accent is not worth that:
 * a colour that could not be saved leaves the app looking like it did before the
 * sheet opened, which is a quiet, survivable outcome.
 *
 * Cancellation is rethrown: it means the sheet is gone, not that the write
 * failed.
 */
private suspend fun themeWrite(block: suspend () -> Unit) {
    try {
        block()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Deliberately swallowed: see above.
    }
}

/**
 * The gap between the floating capsule and the last card that can scroll under it.
 *
 * Not zero: the capsule has a 6dp shadow and the page's cards have their own
 * elevation, so a card that stopped exactly at the bar's top edge looked welded to
 * it. 16dp is the same rhythm the cards use between themselves.
 */
private val DOCK_GAP = 16.dp
