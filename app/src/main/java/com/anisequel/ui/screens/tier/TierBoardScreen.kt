package com.anisequel.ui.screens.tier

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDisplay
import com.anisequel.domain.tier.TierFilter
import com.anisequel.domain.tier.TierListDocument
import com.anisequel.domain.tier.onlyFor
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import kotlinx.coroutines.delay

/**
 * The open list: its rows, its posters, and the edits to both.
 *
 * Stateless in the sense that matters - it is handed a document and it hands
 * decisions back - so the one thing it owns is what a *visit* owns: which sheet is
 * open, the scroll position, the zoom, and what the finger is currently over.
 * Everything that outlives the screen is already in the store by the time it is
 * drawn, which is why leaving this screen cannot lose anything.
 *
 * ## Filters change what is drawn, not what is stored
 *
 * The assignment handed in is the whole list. This screen derives a *visible*
 * assignment and a *visible* pool from it, and the board is built from those: a
 * hidden title that still held a slot would leave a poster-shaped hole in its row,
 * and a tray that still offered it would invite the person to rank something the
 * filter says they are not looking at. The counts in the header stay totals, so
 * hiding never looks like losing.
 *
 * ## The drag hint is pinned to the screen, not to the board
 *
 * It used to be a pill at the board's top edge, which was fine while the board
 * never moved. It moves now: a drag near the edge scrolls the screen underneath the
 * finger, so a hint drawn inside the scrolling content scrolls away exactly when
 * auto-scrolling has made the drop hard to aim - the one moment it is being read.
 * It is a sibling of the scrolling column instead, so it stays where it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierBoardScreen(
    document: TierListDocument,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    filter: TierFilter,
    orphaned: Int,
    isSeeding: Boolean,
    notice: String?,
    undoLabel: String?,
    redoLabel: String?,
    onSeed: () -> Unit,
    onMove: (mediaId: Int, tierId: String?, beforeMediaId: Int?) -> Unit,
    onClearPlacements: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRenameTier: (String, String) -> Unit,
    onAddTier: () -> Unit,
    onRemoveTier: (String) -> Unit,
    onTierColor: (String, Long) -> Unit,
    onTierScoreFloor: (String, Int?) -> Unit,
    onFilterChange: (TierFilter) -> Unit,
    onDisplayChange: (TierDisplay) -> Unit,
    onZoomChange: (Float) -> Unit,
    onDismissNotice: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tiers = document.tiers
    val display = document.display
    val scrollState = rememberScrollState()
    val viewport = remember { TierBoardViewport() }

    val sheets = rememberTierBoardSheetState()
    var dragHint by remember { mutableStateOf<String?>(null) }

    // The board's zoom is a *local* value between gestures so a pinch does not
    // write to DataStore on every frame; the stored value is the starting point
    // and the debounce below is the only writer. `rememberSaveable` keyed on the
    // list id means switching lists starts from that list's own zoom rather than
    // carrying the previous one across.
    var zoom by rememberSaveable(document.id) {
        mutableFloatStateOf(clampTierZoom(display.zoom))
    }

    // A change made elsewhere - "Reset zoom" in the display sheet - has to reach
    // the board. Comparing first stops this from fighting the local value on the
    // composition where the debounced write lands.
    LaunchedEffect(display.zoom) {
        val stored = clampTierZoom(display.zoom)
        if (stored != zoom) zoom = stored
    }

    // Debounced rather than written on every pinch frame. A `delay` inside a
    // `LaunchedEffect` keyed on the zoom is the whole debounce: a new value
    // cancels the pending write, so the store sees the value the gesture settled
    // on and nothing in between.
    LaunchedEffect(zoom) {
        if (zoom != clampTierZoom(display.zoom)) {
            delay(ZOOM_WRITE_DELAY_MS)
            onZoomChange(zoom)
        }
    }

    // The pool's drag, hoisted above both the tray that writes it and the overlay
    // below that draws its ghost. The ghost has to be a sibling of the board rather
    // than a child of the tray, because the tray's own surface clips its content -
    // and a ghost clipped to the tray is invisible for the whole gesture.
    val trayDrag = remember { TierTrayDrag() }

    val ranked = assignment.tierIdByMediaId.size
    val visibleCandidates = remember(candidates, filter) {
        candidates.filter(filter::allows)
    }
    val visibleIds = remember(visibleCandidates) {
        visibleCandidates.mapTo(HashSet()) { it.mediaId }
    }
    // Placements the library has no entry for: a title removed from the viewer's
    // list, or a library that has not loaded yet. The filter has no status to judge
    // those by, so they are kept rather than hidden - and they are not counted as
    // "hidden by filters" either, which is why the board does not show a filter
    // notice over a list that is merely still loading.
    val libraryIds = remember(candidates) { candidates.mapTo(HashSet()) { it.mediaId } }
    val unplaceableIds = remember(assignment, libraryIds) {
        assignment.tierIdByMediaId.keys.filterNotTo(HashSet()) { it in libraryIds }
    }
    val visibleAssignment = remember(assignment, visibleIds, unplaceableIds) {
        assignment.onlyFor(visibleIds, unplaceableIds)
    }
    val hiddenRanked = ranked - visibleAssignment.tierIdByMediaId.size
    val hiddenTotal = candidates.size - visibleCandidates.size
    // Read from the two values the screen already has rather than asked of the
    // ViewModel, which would mean a third collection of the same data and a
    // recomposition of this screen every time the pool or the assignment moved.
    val unranked = remember(visibleCandidates, assignment) {
        visibleCandidates.filter { it.mediaId !in assignment.tierIdByMediaId }
    }

    // The floating toolbar sits above the tray, or above the screen edge when the
    // tray is empty. The board reserves that whole strip as bottom padding: the
    // legend is the last thing in the scrolling column, and chrome floating over
    // the one row that explains the gestures is worse than a little empty space.
    val controlsBottom = if (unranked.isEmpty()) {
        16.dp
    } else {
        TIER_TRAY_HEIGHT + TIER_CONTROLS_GAP
    }
    val boardBottomInset = controlsBottom + TIER_CONTROLS_HEIGHT + 8.dp

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("tier_list_screen"),
        topBar = {
            TopAppBar(
                // The list's own name, not the feature's. Which list you are in is
                // the one thing a bar over a board full of posters cannot tell you
                // by looking at it.
                title = {
                    Text(
                        text = document.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onLeave,
                        modifier = Modifier.testTag("tier_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Your tier lists"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { sheets.filtering = true },
                            modifier = Modifier.testTag("tier_filter_button")
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.Filter,
                                contentDescription = "Filters"
                            )
                        }
                        // A dot rather than a count: the number of hidden statuses
                        // is not a number anybody acts on, but "a filter is on" is
                        // the thing that explains a short board.
                        if (!filter.isDefault) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 10.dp, end = 10.dp)
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                    IconButton(
                        onClick = { sheets.displaying = true },
                        modifier = Modifier.testTag("tier_display_button")
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.SectionAppearance,
                            contentDescription = "Display options"
                        )
                    }
                    TextButton(
                        onClick = { sheets.managingTiers = true },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tiers")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                // Measured on the *parent* of the scrolling column rather than on
                // the column itself. This box is the viewport: it fills the area
                // below the app bar and the column inside it is the same size. The
                // column's own node is a scrolling one, so asking it for its size
                // answers with the content - which is exactly the wrong number here,
                // because the edge test is about the screen. A pointer's position on
                // screen is this box's top plus the drag's own offset.
                // `localToWindow(Offset.Zero)` is the current spelling of
                // `positionInWindow()`, which Compose UI has removed.
                .onGloballyPositioned { coordinates ->
                    val window = coordinates.localToWindow(Offset.Zero)
                    viewport.viewportTop = window.y
                    viewport.viewportLeft = window.x
                    viewport.viewportHeight = coordinates.size.height.toFloat()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    // The tray's height plus the floating toolbar, so the last
                    // tier row and the legend can always be scrolled clear of
                    // both. Without this the tray covers whichever row happens to
                    // be at the bottom, and a drop onto a row the finger is not
                    // over looks like a dropped gesture.
                    .padding(bottom = boardBottomInset)
            ) {
                TierBoardHeader(
                    ranked = ranked,
                    total = candidates.size,
                    unranked = unranked.size,
                    undoLabel = undoLabel,
                    onAddTitles = { sheets.addingTitles = true }
                )

                TierBoardNotices(
                    hiddenTotal = hiddenTotal,
                    assignmentEmpty = assignment.isEmpty,
                    visibleAssignmentEmpty = visibleAssignment.isEmpty,
                    onOpenFilters = { sheets.filtering = true },
                    notice = notice,
                    onDismissNotice = onDismissNotice,
                    orphaned = orphaned
                )

                when {
                    // Nothing ranked at all, and there is something to rank: the
                    // first-run state with both routes into the feature.
                    assignment.isEmpty && (visibleCandidates.isNotEmpty() || candidates.isEmpty()) -> {
                        TierEmptyState(
                            candidates = visibleCandidates,
                            unrankedCount = unranked.size,
                            isSeeding = isSeeding,
                            onSeed = onSeed,
                            onAddTitles = { sheets.addingTitles = true }
                        )
                    }

                    // Ranked titles exist but the filters hide every one of them.
                    // Not the empty state: seeding here would offer to overwrite a
                    // list the person already built.
                    visibleAssignment.isEmpty -> {
                        TierFilteredEmptyState(
                            hiddenCount = if (assignment.isEmpty) hiddenTotal else hiddenRanked,
                            onOpenFilters = { sheets.filtering = true },
                            onShowAll = {
                                onFilterChange(TierFilter(emptySet(), emptySet()))
                            }
                        )
                    }

                    else -> {
                        TierBoardHost(
                            listName = document.name,
                            tiers = tiers,
                            assignment = visibleAssignment,
                            candidates = visibleCandidates,
                            display = display,
                            zoom = zoom,
                            onZoomChange = { value -> zoom = clampTierZoom(value) },
                            viewport = viewport,
                            scrollState = scrollState,
                            onMove = onMove,
                            onItemTap = { mediaId -> sheets.placingMediaId = mediaId },
                            onDragHintChanged = { hint -> dragHint = hint }
                        )
                        TierBoardLegend()
                    }
                }
            }

            AnimatedContent(
                targetState = dragHint,
                transitionSpec = {
                    // Infix `togetherWith` binds looser than `+`, so the two
                    // enter/exit pairs group without parentheses around each side.
                    fadeIn(ExpressiveMotion.FastEffects) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = ExpressiveMotion.FastSpatial
                    ) togetherWith
                        fadeOut(ExpressiveMotion.FastEffects) + scaleOut(
                            targetScale = 0.92f,
                            animationSpec = ExpressiveMotion.FastSpatial
                        )
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                label = "tier_drag_hint"
            ) { hint ->
                if (hint != null) {
                    Box(
                        modifier = Modifier
                            .clip(ExpressiveShapes.pill)
                            .background(MaterialTheme.colorScheme.inverseSurface)
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                            .testTag("tier_drag_hint")
                    ) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // The board's own controls, floating over the bottom-end of the board
            // and above the tray. One row rather than a column so it covers the
            // smallest possible strip of the last tier.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = controlsBottom
                    )
                    .testTag("tier_board_controls"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TierHistoryControls(
                    canUndo = undoLabel != null,
                    canRedo = redoLabel != null,
                    onUndo = onUndo,
                    onRedo = onRedo
                )
                TierZoomControls(
                    zoom = zoom,
                    onZoomOut = { zoom = clampTierZoom(zoom - TIER_ZOOM_STEP) },
                    onZoomIn = { zoom = clampTierZoom(zoom + TIER_ZOOM_STEP) },
                    onResetZoom = { zoom = 1f }
                )
            }

            // The pool, pinned over the bottom of the scroll, and the ghost it
            // drags. Both are siblings of the scrolling column on purpose: the tray
            // has to stay put while the board scrolls under it, and the ghost has to
            // be able to leave it.
            if (unranked.isNotEmpty()) {
                TierTray(
                    candidates = unranked,
                    drag = trayDrag,
                    resolveTier = { windowX, windowY ->
                        tierRowAt(
                            bands = viewport.rowBands,
                            windowX = windowX,
                            windowY = windowY,
                            leftEdge = viewport.boardLeft,
                            rightEdge = viewport.boardLeft + viewport.boardWidth
                        )
                    },
                    // A null tier id is the instruction to un-file, and dropping a
                    // poster back into the pool is exactly that - so a mis-drop is
                    // undone by dropping it where it started.
                    onDrop = { mediaId, tierId -> onMove(mediaId, tierId, null) },
                    onItemTap = { mediaId -> sheets.placingMediaId = mediaId },
                    onBrowse = { sheets.addingTitles = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                val draggingId = trayDrag.mediaId
                val ghostCandidate = unranked.firstOrNull { it.mediaId == draggingId }
                if (ghostCandidate != null) {
                    TierTrayGhost(
                        candidate = ghostCandidate,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                // The drag tracks the finger in *window* pixels and
                                // this box is laid out from the viewport's own top-left,
                                // so the difference between the two is the whole
                                // translation. Read in the draw phase, so moving the
                                // ghost costs a repaint rather than a recomposition.
                                translationX = trayDrag.posterX - viewport.viewportLeft
                                translationY = trayDrag.liftedY - viewport.viewportTop
                            }
                    )
                }
            }
        }
    }

    TierBoardSheetsHost(
        state = sheets,
        data = TierBoardSheetData(
            tiers = tiers,
            assignment = assignment,
            candidates = candidates,
            unranked = unranked,
            ranked = ranked,
            filter = filter,
            display = display,
            zoom = zoom
        ),
        actions = TierBoardSheetActions(
            onMove = onMove,
            onRenameTier = onRenameTier,
            onAddTier = onAddTier,
            onRemoveTier = onRemoveTier,
            onTierColor = onTierColor,
            onTierScoreFloor = onTierScoreFloor,
            onFilterChange = onFilterChange,
            onDisplayChange = onDisplayChange,
            // Through the local value the stepper writes, so reset cannot lose a
            // race with the debounced write of the zoom it replaced.
            onResetZoom = { zoom = 1f },
            onClearPlacements = onClearPlacements
        )
    )
}

/** How long a pinch has to be still before its zoom is written to the store. */
private const val ZOOM_WRITE_DELAY_MS = 600L
