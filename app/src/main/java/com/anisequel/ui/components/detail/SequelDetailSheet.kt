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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.posterColor
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.detail.info.AnimeInfoPage
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveNavigationBar
import com.anisequel.ui.components.expressive.NavigationDestination
import kotlinx.coroutines.launch

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
    isDetailLoading: Boolean,
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
     * Read/write access to the viewer's own entry, or null when the sheet is
     * read-only.
     *
     * Null removes the editing destination entirely rather than drawing it disabled:
     * an editor that cannot save looks identical to one that can until the moment it
     * silently does nothing. The dashboard passes a real editor for the signed-in
     * viewer; a scanned profile passes nothing, because the entry on screen is
     * somebody else's.
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
    val infoListState = key(subject.mediaId) { rememberLazyListState() }

    val editState = remember(subject.mediaId) { EntryEditState() }
    LaunchedEffect(subject.mediaId, entryEditor) {
        if (entryEditor != null) editState.load(entryEditor, subject.mediaId)
    }

    LaunchedEffect(subject.mediaId, subject.media.type) {
        onLoadDetail(subject.mediaId, subject.media.type)
        if (themeSettings.animeThemeActive) {
            val colorHex = media.posterColor ?: "#0057B7"
            themePreferences.setAnimeThemeActive(true, colorHex)
            if (themeSettings.animeThemeRetainColor) {
                // "Retain" means the anime's colour becomes the app's accent, not
                // just a glow around this sheet: persist it as a custom hue so the
                // whole app - and the next launch - keeps it. There is an exit hatch:
                // picking a palette or a hue in the Theme tab clears it, as does
                // Reset appearance.
                themePreferences.setCustomColorHex(colorHex)
            }
        }
    }

    // The revert timer belongs to the *temporary* layer only. While Retain is on the
    // colour was persisted as a custom hue, so deactivating the layer after a delay
    // would flick the app between schemes for no gain - the retained accent is already
    // what the user asked to keep.
    LaunchedEffect(
        themeSettings.animeThemeActive,
        themeSettings.animeThemeRevertDuration,
        themeSettings.animeThemeRetainColor
    ) {
        if (themeSettings.animeThemeActive &&
            themeSettings.animeThemeRevertDuration > 0 &&
            !themeSettings.animeThemeRetainColor
        ) {
            val delayMillis = themeSettings.animeThemeRevertDuration * 1000L
            kotlinx.coroutines.delay(delayMillis)
            themePreferences.setAnimeThemeActive(false, null)
        }
    }

    val handleDismiss: () -> Unit = {
        scope.launch {
            if (themeSettings.animeThemeRevertDuration == 0) {
                themePreferences.setAnimeThemeActive(false, null)
            }
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
        var destination by rememberSaveable(subject.mediaId) { mutableIntStateOf(0) }
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
                        )
                },
                label = "detail_destination"
            ) { index ->
                if (index == 1 && entryEditor != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = navClearance)
                    ) {
                        DetailEditTab(media = media, editor = entryEditor, state = editState)
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
                        onClose = handleDismiss,
                        onOpenMedia = onOpenMedia,
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
