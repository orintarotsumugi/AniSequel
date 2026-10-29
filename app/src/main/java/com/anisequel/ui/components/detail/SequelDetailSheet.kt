package com.anisequel.ui.components.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.repository.CoverColorRepository
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.domain.color.ArgbHex
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.detail.info.AnimeInfoPage
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveNavigationBar
import com.anisequel.ui.components.expressive.NavigationDestination
import com.anisequel.ui.theme.CoverColors
import com.anisequel.ui.viewmodel.QuickAddStatus

/**
 * The sheet that shows one title's Info page.
 *
 * Two destinations, switched from a floating capsule: the page, and the viewer's own
 * entry when the sheet has write access to it. The sheet used to be three tabs -
 * story, specs, entry - which split one title's information across two of them and
 * left the reader to hold the halves in their head; the page is now one scroll (see
 * [AnimeInfoPage]) and the capsule switches between *reading* and *editing*, which is
 * the only distinction left that means anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequelDetailSheet(
    subject: AnimeDetailSubject?,
    sheetState: SheetState,
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
     * The followed users who have this title on their list, best rating first.
     *
     * Already filtered and sorted by the repository - see
     * [com.anisequel.data.model.MediaFriend]. Empty draws no card at all rather
     * than an empty one, and it is empty for the ordinary reason as well: a
     * viewer who follows nobody, or a title nobody they follow has opened.
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
    quickAddStatuses: Map<Int, QuickAddStatus> = emptyMap(),
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
    // the same seed for its backdrop and shadow. It is one 128px decode from
    // Coil's cache - the hero is already loading the full image - and it means
    // the artwork's colour is right whether or not the global recolour is on.
    LaunchedEffect(
        subject.mediaId,
        subject.media.type,
        media.posterUrl,
        themeSettings.isLoaded,
        themeSettings.animeThemeEnabled,
        themeSettings.animeThemeRetainColor
    ) {
        if (!themeSettings.isLoaded) return@LaunchedEffect

        val seed = CoverColorRepository.extract(context, media.posterUrl)
        if (seed != null) {
            CoverColors.put(media.posterUrl, seed)
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
    val handleDismiss: () -> Unit = {
        if (themeSettings.animeThemeRetainColor) {
            // Retain owns the colour, and it owns it through the accent it
            // persisted - so the immersive layer can end here. Keeping the layer
            // alive instead left the show's hue in every surface on every screen
            // and on the next launch, which is the thing a standing preference is
            // not supposed to do.
            themePreferences.clearAnimeColorLayer()
        } else {
            themePreferences.releaseAnimeColorLayer(themeSettings.animeThemeRevertDuration)
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = handleDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("sequel_detail_sheet"),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        // The page is destination zero and stays at index zero, so a saved tab index
        // cannot reopen the sheet on a destination that is not there any more.
        var destination by rememberSaveable(subject.mediaId, subject.media.type) {
            mutableIntStateOf(0)
        }
        val destinations = buildList {
            add(NavigationDestination("Info", AppVectorIcons.Info, "detail_tab_info"))
            if (entryEditor != null) {
                add(NavigationDestination("My Entry", AppVectorIcons.EditEntry, "detail_tab_edit"))
            }
        }
        val selected = destination.coerceIn(0, destinations.lastIndex)
        val navClearance = if (destinations.size > 1) 112.dp else 28.dp

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    val forward = targetState > initialState
                    // The trailing `using null` drops the default
                    // `SizeTransform`, which springs the container's height
                    // between the page and the editor - re-measuring the
                    // `LazyColumn` underneath on every frame of the switch,
                    // under a reader who may be scrolling it at the same time.
                    // That is the motion the crash was reported from: opening
                    // this sheet and scrolling it in one go. The slide and the
                    // fade are draw-phase and stay; only the height snaps.
                    (
                        slideInHorizontally(
                            // `DefaultSpatialOffset` is the `IntOffset` token, which is
                            // the type `slideInHorizontally` takes - the `Int` and
                            // `IntSize` tokens cannot be passed to it at all.
                            animationSpec = ExpressiveMotion.DefaultSpatialOffset
                        ) { width -> if (forward) width / 5 else -width / 5 } +
                            fadeIn(animationSpec = ExpressiveMotion.FastEffects)
                        ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = ExpressiveMotion.DefaultSpatialOffset
                        ) { width -> if (forward) -width / 5 else width / 5 } +
                            fadeOut(animationSpec = ExpressiveMotion.FastEffects)
                        ) using null
                },
                label = "detail_destination"
            ) { index ->
                if (index == 1 && entryEditor != null) {
                    // No `verticalScroll` here any more. The edit tab scrolls its own
                    // fields so that its action row can stay pinned above the
                    // navigation bar; scrolling at both levels would let the pinned row
                    // scroll away, which is the thing the tab was restructured to stop.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = navClearance)
                    ) {
                        DetailEditTab(
                            media = media,
                            editor = entryEditor,
                            state = editState,
                            // The sheet's scope, which outlives the tab switch -
                            // see the parameter's doc.
                            scope = scope
                        )
                    }
                } else {
                    AnimeInfoPage(
                        subject = subject,
                        isDetailLoading = isDetailLoading,
                        canWriteToAniList = canWriteToAniList,
                        holdDurationSeconds = holdDurationSeconds,
                        swipeEnabled = swipeEnabled,
                        onAddToPlanning = onAddToPlanning,
                        onAddToWatching = onAddToWatching,
                        quickAddStatus = quickAddStatuses[subject.mediaId]
                            ?: QuickAddStatus(),
                        friends = friends,
                        onAddMediaToPlanning = onAddMediaToPlanning,
                        onAddMediaToWatching = onAddMediaToWatching,
                        onClose = handleDismiss,
                        onOpenMedia = onOpenMedia,
                        onOpenStudio = onOpenStudio,
                        onOpenCharacter = onOpenCharacter,
                        onOpenStaff = onOpenStaff,
                        modifier = Modifier.fillMaxSize(),
                        listState = infoListState,
                        bottomContentPadding = navClearance
                    )
                }
            }

            if (destinations.size > 1) {
                ExpressiveNavigationBar(
                    destinations = destinations,
                    selectedIndex = selected,
                    onSelect = { destination = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
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
