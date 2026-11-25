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
import androidx.compose.ui.unit.Dp
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
 * The page's own layer: scrim, surface, info page, capsule, entry overlay and
 * appearance editor. Extracted from [SequelDetailSheet] so the sheet keeps the
 * state, effects and window while this keeps the nested layout.
 */
@Composable
internal fun SequelDetailPage(
    subject: AnimeDetailSubject,
    onDismiss: () -> Unit,
    modifier: Modifier,
    entrance: Animatable<Float, AnimationVector1D>,
    coverFlight: DetailCoverFlightState,
    immersive: Boolean,
    isDetailLoading: Boolean,
    friends: List<MediaFriend>,
    isFriendsLoading: Boolean,
    canWriteToAniList: Boolean,
    holdDurationSeconds: Int,
    swipeEnabled: Boolean,
    onAddToPlanning: (MissedSequel) -> Unit,
    onAddToWatching: (MissedSequel) -> Unit,
    quickAddStatus: QuickAddStatus,
    onAddMediaToPlanning: (MediaNode) -> Unit,
    onAddMediaToWatching: (MediaNode) -> Unit,
    onOpenMedia: (MediaNode) -> Unit,
    onOpenStudio: (Int, String?) -> Unit,
    onOpenCharacter: ((Int) -> Unit)?,
    onOpenStaff: ((Int) -> Unit)?,
    themePreferences: ThemePreferences,
    themeSettings: ThemePreferences.ThemeSettings,
    entryEditor: EntryEditor?,
    editState: EntryEditState,
    showAppearance: Boolean,
    onShowAppearanceChange: (Boolean) -> Unit,
    showEntrySheet: Boolean,
    onShowEntrySheetChange: (Boolean) -> Unit,
    onEditEntry: (() -> Unit)?,
    sheetOffset: Animatable<Float, AnimationVector1D>,
    sheetDragging: Boolean,
    onSheetDraggingChange: (Boolean) -> Unit,
    dragFraction: Float,
    onDragFractionChange: (Float) -> Unit,
    closeEntrySheet: () -> Unit,
    scope: CoroutineScope,
    infoListState: LazyListState,
    posterUrl: String?
) {
    val media = subject.media
    Box(modifier = Modifier.fillMaxSize()) {
    SheetScrim(entrance)
    Surface(
        modifier = modifier
            .fillMaxSize()
            // The page does not scale or rise while a poster flies: the copy
            // lands on the poster's measured window rectangle, and moving the
            // page under it would move the target.
            .sheetEntrance(entrance, travels = !coverFlight.travels)
            .testTag("sequel_detail_sheet"),
        color = MaterialTheme.colorScheme.surface
    ) {
    // Info stays composed underneath My Entry. The editor is a real overlay,
    // not a second destination: its draft and the Info page's scroll state both
    // remain alive while the reader moves the sheet.
    val destinations = detailDestinations(entryEditor != null)
    val selected = detailSelected(showEntrySheet, destinations.size)

    // The capsule's measured height is also the page's bottom clearance. It
    // travels to the sheet's top edge while My Entry is open, then settles back
    // into its normal dock position when the overlay closes.
    var dockHeightPx by remember { mutableStateOf(0) }
    val dockHeight = with(LocalDensity.current) { dockHeightPx.toDp() }
    val navigationInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val navClearance = detailNavClearance(
        hasDock = destinations.size > 1,
        dockHeightPx = dockHeightPx,
        dockHeight = dockHeight,
        immersive = immersive,
        navigationInset = navigationInset
    )

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
                    isFriendsLoading = isFriendsLoading,
                    canWriteToAniList = canWriteToAniList,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    quickAddStatus = quickAddStatus,
                    friends = friends,
                    onAddMediaToPlanning = onAddMediaToPlanning,
                    onAddMediaToWatching = onAddMediaToWatching,
                    onClose = onDismiss,
                    onOpenMedia = onOpenMedia,
                    onOpenStudio = onOpenStudio,
                    onOpenCharacter = onOpenCharacter,
                    onOpenStaff = onOpenStaff,
                    onEditEntry = onEditEntry,
                    onOpenAppearance = { onShowAppearanceChange(true) },
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
                    isFriendsLoading = isFriendsLoading,
                    canWriteToAniList = canWriteToAniList,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    quickAddStatus = quickAddStatus,
                    friends = friends,
                    onAddMediaToPlanning = onAddMediaToPlanning,
                    onAddMediaToWatching = onAddMediaToWatching,
                    onClose = onDismiss,
                    onOpenMedia = onOpenMedia,
                    onOpenStudio = onOpenStudio,
                    onOpenCharacter = onOpenCharacter,
                    onOpenStaff = onOpenStaff,
                    onEditEntry = onEditEntry,
                    onOpenAppearance = { onShowAppearanceChange(true) },
                    modifier = Modifier.fillMaxSize(),
                    listState = infoListState,
                    bottomContentPadding = navClearance,
                    infoBackdropSource = themeSettings.infoBackdropSource,
                    infoBackdropBlur = themeSettings.infoBackdropBlur,
                    infoBackdropTint = themeSettings.infoBackdropTint
                )
            }

            SequelDetailDock(
                destinations = destinations,
                selected = selected,
                showEntrySheet = showEntrySheet,
                sheetDragging = sheetDragging,
                dragFraction = dragFraction,
                sheetOffset = sheetOffset,
                containerHeightPx = containerHeightPx,
                dockHeightPx = dockHeightPx,
                immersive = immersive,
                onDockHeightChange = { dockHeightPx = it },
                onEditEntry = { onShowEntrySheetChange(true) },
                onCloseEntry = closeEntrySheet
            )

            SequelDetailEntryOverlay(
                media = media,
                showEntrySheet = showEntrySheet,
                entryEditor = entryEditor,
                editState = editState,
                immersive = immersive,
                containerHeightPx = containerHeightPx,
                sheetOffset = sheetOffset,
                sheetDragging = sheetDragging,
                dragFraction = dragFraction,
                onDragFractionChange = onDragFractionChange,
                onSheetDraggingChange = onSheetDraggingChange,
                closeEntrySheet = closeEntrySheet,
                onHide = { onShowEntrySheetChange(false) },
                scope = scope
            )
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
            DetailAppearanceLayer(
                media = media,
                preferences = themePreferences,
                settings = themeSettings,
                scope = scope,
                onClose = { onShowAppearanceChange(false) }
            )
        }
    }
        // Last child of the root Box, so the copy is drawn above the page
        // and its scrim and is never clipped by either.
        coverFlight.Overlay(posterUrl.orEmpty())
    }
}



/** The page's own destinations; My Entry only exists once the editor does. */
private fun detailDestinations(hasEditor: Boolean): List<NavigationDestination> = buildList {
    add(NavigationDestination("Info", AppVectorIcons.Info, "detail_tab_info"))
    if (hasEditor) {
        add(NavigationDestination("My Entry", AppVectorIcons.EditEntry, "detail_tab_edit"))
    }
}

/** The selected destination only moves to My Entry while its overlay is open. */
private fun detailSelected(showEntrySheet: Boolean, destinationCount: Int): Int =
    if (showEntrySheet && destinationCount > 1) 1 else 0

/** The page's bottom clearance: the capsule's measured height, or the nav bar. */
private fun detailNavClearance(
    hasDock: Boolean,
    dockHeightPx: Int,
    dockHeight: Dp,
    immersive: Boolean,
    navigationInset: Dp
): Dp {
    val immersiveLift = if (immersive) 12.dp else 0.dp
    return if (hasDock) {
        (if (dockHeightPx > 0) dockHeight else 112.dp) + DOCK_GAP + immersiveLift
    } else {
        navigationInset + 28.dp + immersiveLift
    }
}

/**
 * The floating capsule. It travels with My Entry and fades as the sheet covers
 * it; the page owns the measured dock height because it is also the page's
 * bottom clearance.
 */
@Composable
private fun BoxScope.SequelDetailDock(
    destinations: List<NavigationDestination>,
    selected: Int,
    showEntrySheet: Boolean,
    sheetDragging: Boolean,
    dragFraction: Float,
    sheetOffset: Animatable<Float, AnimationVector1D>,
    containerHeightPx: Float,
    dockHeightPx: Int,
    immersive: Boolean,
    onDockHeightChange: (Int) -> Unit,
    onEditEntry: () -> Unit,
    onCloseEntry: () -> Unit
) {
if (destinations.size > 1) {
    ExpressiveNavigationBar(
        destinations = destinations,
        selectedIndex = selected,
        onSelect = { index -> if (index == 1) onEditEntry() else onCloseEntry() },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .onSizeChanged { onDockHeightChange(it.height) }
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
}

/**
 * The "My Entry" overlay: a full-height sheet translated from below, with its
 * own drag handle and header. Extracted from [SequelDetailPage] so the page's
 * layout does not also carry the drag gesture and its three settle stops.
 */
@Composable
private fun BoxScope.SequelDetailEntryOverlay(
    media: MediaNode,
    showEntrySheet: Boolean,
    entryEditor: EntryEditor?,
    editState: EntryEditState,
    immersive: Boolean,
    containerHeightPx: Float,
    sheetOffset: Animatable<Float, AnimationVector1D>,
    sheetDragging: Boolean,
    dragFraction: Float,
    onDragFractionChange: (Float) -> Unit,
    onSheetDraggingChange: (Boolean) -> Unit,
    closeEntrySheet: () -> Unit,
    onHide: () -> Unit,
    scope: CoroutineScope
) {
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
                                onDragFractionChange(sheetOffset.value)
                                onSheetDraggingChange(true)
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (containerHeightPx > 0f) {
                                    onDragFractionChange(
                                        (dragFraction + dragAmount / containerHeightPx)
                                            .coerceIn(0f, 1f)
                                    )
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
                                    onSheetDraggingChange(false)
                                    if (target >= 1f) {
                                        sheetOffset.animateTo(
                                            1f,
                                            ExpressiveMotion.SettleSpatial
                                        )
                                        onHide()
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
                                    onSheetDraggingChange(false)
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
                    // The grab handle is the only affordance saying the
                    // sheet can be dragged: at 1.9:1 it was a smudge.
                    color = AniSequelTheme.emphasis.muted,
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

/**
 * The gap between the floating capsule and the last card that can scroll under it.
 *
 * Not zero: the capsule has a 6dp shadow and the page's cards have their own
 * elevation, so a card that stopped exactly at the bar's top edge looked welded to
 * it. 16dp is the same rhythm the cards use between themselves.
 */
private val DOCK_GAP = 16.dp
