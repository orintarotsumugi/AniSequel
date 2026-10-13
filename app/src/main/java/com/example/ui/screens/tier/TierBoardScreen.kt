package com.example.ui.screens.tier

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.tier.TierAssignment
import com.example.domain.tier.TierCandidate
import com.example.domain.tier.TierListDocument
import com.example.domain.tier.unseedableCount
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveEmptyOrb
import com.example.ui.components.expressive.ExpressivePrimaryButton
import com.example.ui.components.expressive.ExpressiveStateChip
import com.example.ui.theme.AniSequelTheme

/**
 * The open list: its rows, its posters, and the edits to both.
 *
 * Stateless in the sense that matters - it is handed a document and it hands
 * decisions back - so the one thing it owns is what a *visit* owns: which sheet is
 * open, the scroll position, and what the finger is currently over. Everything
 * that outlives the screen is already in the store by the time it is drawn, which
 * is why leaving this screen cannot lose anything.
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
    orphaned: Int,
    isSeeding: Boolean,
    notice: String?,
    undoLabel: String?,
    onSeed: () -> Unit,
    onMove: (mediaId: Int, tierId: String?, beforeMediaId: Int?) -> Unit,
    onClearPlacements: () -> Unit,
    onUndo: () -> Unit,
    onRenameTier: (String, String) -> Unit,
    onAddTier: () -> Unit,
    onRemoveTier: (String) -> Unit,
    onDismissNotice: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tiers = document.tiers
    val scrollState = rememberScrollState()
    val viewport = remember { TierBoardViewport() }

    var placingMediaId by rememberSaveable { mutableStateOf<Int?>(null) }
    var addingTitles by rememberSaveable { mutableStateOf(false) }
    var managingTiers by rememberSaveable { mutableStateOf(false) }
    var dragHint by remember { mutableStateOf<String?>(null) }

    // The pool's drag, hoisted above both the tray that writes it and the overlay
    // below that draws its ghost. The ghost has to be a sibling of the board rather
    // than a child of the tray, because the tray's own surface clips its content -
    // and a ghost clipped to the tray is invisible for the whole gesture.
    val trayDrag = remember { TierTrayDrag() }

    val ranked = assignment.tierIdByMediaId.size
    val subtitle = tierListSubtitle(ranked, candidates.size)
    // Read from the two values the screen already has rather than asked of the
    // ViewModel, which would mean a third collection of the same data and a
    // recomposition of this screen every time the pool or the assignment moved.
    val unranked = remember(candidates, assignment) {
        candidates.filter { it.mediaId !in assignment.tierIdByMediaId }
    }

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
                    TextButton(
                        onClick = { managingTiers = true },
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
                    // Exactly the tray's height, so the last tier row can always be
                    // scrolled clear of it. Without this the tray covers whichever
                    // row happens to be at the bottom, and a drop onto a row the
                    // finger is not over looks like a dropped gesture.
                    .padding(bottom = if (unranked.isEmpty()) 0.dp else TIER_TRAY_HEIGHT)
            ) {
                TierBoardCounts(subtitle = subtitle)

                notice?.let { message ->
                    TierNotice(text = message, onDismiss = onDismissNotice)
                }

                undoLabel?.let { label ->
                    TierUndoRow(label = label, onUndo = onUndo)
                }

                // The ViewModel already drops orphaned entries from the displayed
                // assignment, so saying so is what stops the missing posters
                // reading as data loss.
                if (orphaned > 0) {
                    TierNotice(
                        text = "$orphaned " +
                            (if (orphaned == 1) "title was" else "titles were") +
                            " filed under a tier that no longer exists. They are not shown" +
                            " until you place them again.",
                        onDismiss = null
                    )
                }

                if (assignment.isEmpty) {
                    TierEmptyState(
                        candidates = candidates,
                        unrankedCount = unranked.size,
                        isSeeding = isSeeding,
                        onSeed = onSeed,
                        onAddTitles = { addingTitles = true }
                    )
                } else {
                    TierBoardHost(
                        listName = document.name,
                        tiers = tiers,
                        assignment = assignment,
                        candidates = candidates,
                        viewport = viewport,
                        scrollState = scrollState,
                        onMove = onMove,
                        onItemTap = { mediaId -> placingMediaId = mediaId },
                        onDragHintChanged = { hint -> dragHint = hint }
                    )
                    TierBoardLegend()
                }
            }

            dragHint?.let { hint ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("tier_drag_hint")
                ) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // The pool, pinned over the bottom of the scroll, and the ghost it
            // drags. Both are siblings of the scrolling column on purpose: the tray
            // has to stay put while the board scrolls under it, and the ghost has to
            // be able to leave it.
            if (!unranked.isEmpty()) {
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
                    onItemTap = { mediaId -> placingMediaId = mediaId },
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

    val placing = placingMediaId
    if (placing != null) {
        TierPlaceSheet(
            title = candidates.firstOrNull { it.mediaId == placing }?.title.orEmpty(),
            tiers = tiers,
            currentTierId = assignment.tierOf(placing),
            onMove = { tierId ->
                onMove(placing, tierId, null)
                placingMediaId = null
            },
            onRemove = {
                onMove(placing, null, null)
                placingMediaId = null
            },
            onDismiss = { placingMediaId = null }
        )
    }

    if (addingTitles) {
        TierAddTitlesSheet(
            unranked = unranked,
            tiers = tiers,
            currentTierOf = { mediaId -> assignment.tierOf(mediaId) },
            onMove = onMove,
            onDismiss = { addingTitles = false }
        )
    }

    if (managingTiers) {
        TierManageSheet(
            tiers = tiers,
            memberCounts = tiers.associate { it.id to assignment.membersOf(it.id).size },
            rankedCount = ranked,
            onRename = onRenameTier,
            onAdd = onAddTier,
            onRemove = onRemoveTier,
            onClearPlacements = onClearPlacements,
            onDismiss = { managingTiers = false }
        )
    }
}

/**
 * The counts, on their own row.
 *
 * The title is not here: it is the app bar's, which is where a back arrow lives
 * too, and repeating it one row down would put two different answers to "where
 * am I" on one screen.
 */
@Composable
private fun TierBoardCounts(subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * One line of plain reporting, with a close only when there is something to
 * close.
 *
 * The orphan notice has no dismiss because it is not a message that was sent -
 * it is the current state of the data, and it goes away by itself when the
 * person places those titles.
 */
@Composable
private fun TierNotice(text: String, onDismiss: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AniSequelTheme.statusColors.infoContainer)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppVectorIcons.Info,
            contentDescription = null,
            tint = AniSequelTheme.statusColors.onInfoContainer,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = AniSequelTheme.statusColors.onInfoContainer,
            modifier = Modifier.weight(1f)
        )
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = AppVectorIcons.Close,
                    contentDescription = "Dismiss",
                    tint = AniSequelTheme.statusColors.onInfoContainer
                )
            }
        }
    }
}

/**
 * "Filed under A - Undo" for the edit that has just happened.
 *
 * A drop is one gesture and no confirmation, and the row it lands in is decided by
 * a finger that is covering part of the board. Undo is what makes that safe to do
 * quickly - and it is named for what it would take back, because a bare "Undo" asks
 * somebody to remember an action they were not paying attention to.
 *
 * It survives until the next edit, rather than timing out: a toast that expires
 * while a person is looking at where a poster went is a toast that has already
 * failed them.
 */
@Composable
private fun TierUndoRow(label: String, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onUndo,
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("tier_undo_button")
        ) {
            Text("Undo")
        }
    }
}

/**
 * The state a list with nothing in it is in, offering both ways out of it.
 *
 * The unscored count is stated *before* the button, not after the press. Seeding
 * skips those titles - correctly, since filing an unrated title under F asserts a
 * judgement nobody made - and a person who presses the button expecting 271
 * posters and gets 259 has been told nothing about where the other twelve went.
 */
@Composable
private fun TierEmptyState(
    candidates: List<TierCandidate>,
    unrankedCount: Int,
    isSeeding: Boolean,
    onSeed: () -> Unit,
    onAddTitles: () -> Unit
) {
    val unscored = unseedableCount(candidates)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(
            icon = AppVectorIcons.Trophy,
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Nothing ranked yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (candidates.isEmpty()) {
                "Your library has not loaded, so there is nothing to rank yet."
            } else {
                "Rank ${candidates.size} titles by their AniList score, or place them yourself."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (unscored > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$unscored of ${candidates.size} " +
                    (if (unscored == 1) "title has" else "titles have") +
                    " no AniList score and will not be ranked. " +
                    (if (unscored == 1) "It stays" else "They stay") +
                    " for you to place.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        ExpressivePrimaryButton(
            text = "Rank from AniList scores",
            onClick = onSeed,
            icon = AppVectorIcons.Trophy,
            loading = isSeeding,
            enabled = candidates.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        ExpressiveStateChip(
            label = if (unrankedCount == 0) {
                "No titles left to place"
            } else {
                "Start empty - add titles ($unrankedCount)"
            },
            selected = false,
            onClick = onAddTitles,
            minTouchTargetHeight = 48.dp
        )
    }
}

/**
 * What the gestures are, under the board.
 *
 * A long press and a pinch are both invisible: nothing on a board full of posters
 * says either exists, and the drag is the whole feature. It is the last row of the
 * screen rather than a hint card, because it is a legend for the thing above it -
 * and it says the one non-obvious thing, which is that releasing off every row
 * takes a title out of the list.
 */
@Composable
private fun TierBoardLegend() {
    Text(
        text = "Hold a cover to move it, and drop it between two others to place " +
            "it exactly. Pinch to zoom. Release off the rows to take it out.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    )
}

/**
 * The line under the title, stated as counts rather than as a claim.
 *
 * "Your library has not loaded yet" is its own case because the alternative -
 * "0 of 0 titles ranked" - is a true statement about an empty set and a false
 * one about the person's account.
 */
internal fun tierListSubtitle(ranked: Int, librarySize: Int): String = when {
    librarySize == 0 -> "Your library has not loaded yet"
    ranked == 0 -> "Nothing ranked yet"
    else -> "$ranked of $librarySize titles ranked"
}
