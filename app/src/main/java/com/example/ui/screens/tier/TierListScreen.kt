package com.example.ui.screens.tier

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.tier.TierAssignment
import com.example.domain.tier.TierCandidate
import com.example.domain.tier.TierDefinition
import com.example.domain.tier.TierItemBox
import com.example.domain.tier.TierLayoutOptions
import com.example.domain.tier.TierListLayout
import com.example.domain.tier.TierRowBox
import com.example.domain.tier.layoutTierList
import com.example.domain.tier.unseedableCount
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveEmptyOrb
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.ExpressivePrimaryButton
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.components.expressive.ExpressiveStateChip
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.theme.AniSequelTheme
import com.example.ui.viewmodel.TierListViewModel
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Pure, testable helpers.
//
// Everything the screen decides that is not "draw a thing" lives here, in
// functions `TierScreenLogicTest` can call without a device. Each one records
// the failure it prevents.
// ---------------------------------------------------------------------------

/**
 * The tier row's fill, from the packed `0xAARRGGBB` long the domain stores.
 *
 * The conversion is written out rather than left to a constructor because
 * Compose offers several and they do not agree about what a `Long` holds:
 *
 *  - `Color(Int)` expects `0x00AARRGGBB` and shifts it into place itself, so
 *    handed `0xFFE5484D` it produces a *transparent* colour rather than an
 *    error. Six rows quietly rendering as nothing is the failure this stops.
 *  - `Color(Long)` masks to 32 bits first, which is what makes a value that has
 *    come back from DataStore as a negative `Long` (any colour whose top byte has
 *    the sign bit set) still work.
 *
 * The value class's primary constructor takes a `ULong` already in Compose's own
 * `0xAARRGGBB` layout - exactly the layout the domain uses - so all that is left
 * to do is mask off anything above the alpha byte and shift the 32-bit ARGB up to
 * where the value class keeps it. Masking rather than trusting the input is what
 * turns `0xFFFFFFFFFFFFFFFF` - which `TierListSvg.Palette` really does hold, for
 * white - into white rather than an out-of-range colour.
 */
internal fun tierRowColor(colorArgb: Long): Color =
    Color((colorArgb.toULong() and 0xFFFFFFFFUL) shl 32)

/** Below this the captions stop being readable; above it a row holds one tile. */
internal const val TIER_MIN_ZOOM = 0.75f
internal const val TIER_MAX_ZOOM = 2.0f

/**
 * Constrains a pinch to the range the board can actually render at.
 *
 * NaN is not hypothetical: a pinch's zoom factor is a ratio of the distance
 * between two pointers to the distance they started at, and two pointers that
 * momentarily coincide divide by zero. `coerceIn` is built on `<` and `>`
 * comparisons, both false for NaN, so it hands NaN straight back - and every
 * `offset()` downstream of a NaN scale produces a layout that never draws.
 * Swallowing it here means one bad frame of gesture maths cannot take the
 * screen with it.
 */
internal fun clampTierZoom(value: Float): Float =
    if (value.isNaN()) TIER_MIN_ZOOM else value.coerceIn(TIER_MIN_ZOOM, TIER_MAX_ZOOM)

/**
 * How far outside a row a drop still counts as "on" that row.
 *
 * Measured from the row's *edge*, not its centre. Rows are ~128dp tall, so a
 * centre-referenced slop would have to be enormous to bridge an 8dp gap - large
 * enough that it would also swallow the empty canvas above the first row and turn
 * a drop on the heading into a placement.
 */
internal const val TIER_DROP_SNAP_SLOP = 16f

/**
 * Which tier a drag released at ([x], [y]) belongs to, or null for "no tier".
 *
 * Null is not an error case, it is the *instruction*: [TierListViewModel.moveTo]
 * takes a nullable tier id and files nothing when given one, so a release over
 * the heading or past the bottom of the board un-files the title. That is the
 * only way to empty a row by hand - deleting the row would delete the tier.
 *
 * The two-step resolution exists because the two ways of missing a row are not
 * the same mistake. Landing in the 8dp *gap* between two rows is a near miss with
 * one obvious answer, and refusing it would make dropping feel broken, so the gap
 * snaps to whichever edge is nearer. Landing on the heading, or a hundred dp
 * below the last row, is a deliberate miss: those coordinates are nowhere near
 * any row, and quietly picking the closest one would file a title under a
 * judgement the person never made - which is the exact thing this whole feature
 * refuses to do anywhere else.
 */
internal fun tierDropTarget(
    rows: List<TierRowBox>,
    x: Float,
    y: Float,
    snapSlop: Float = TIER_DROP_SNAP_SLOP
): String? {
    if (rows.isEmpty()) return null

    rows.firstOrNull { row -> x >= row.x && x <= row.right && y >= row.y && y <= row.bottom }
        ?.let { return it.tierId }

    val nearest = rows.minByOrNull { row -> edgeDistance(y, row) } ?: return null
    val closeEnough = edgeDistance(y, nearest) <= snapSlop
    val horizontallyInside = x >= nearest.x && x <= nearest.right
    return if (closeEnough && horizontallyInside) nearest.tierId else null
}

/** How far [y] is from the nearest edge of [row]; zero when it is inside. */
private fun edgeDistance(y: Float, row: TierRowBox): Float = when {
    y < row.y -> row.y - y
    y > row.bottom -> y - row.bottom
    else -> 0f
}

/**
 * The item under a press, or null for "not a poster".
 *
 * Scans in layout order and keeps the *last* hit, so a press on the seam between
 * two rows takes the lower one - which is what the finger was over, and the order
 * the tiles are stacked in.
 */
internal fun tierItemAt(items: List<TierItemBox>, x: Float, y: Float): Int? =
    items.lastOrNull {
        x >= it.x && x <= it.right && y >= it.y && y <= it.bottom
    }?.mediaId

/**
 * The tiles the board draws, given the one currently lifted under a finger.
 *
 * The dragged tile is drawn as a ghost that follows the pointer, not in the place
 * it started, so it must not be emitted as a child of the layout. Leaving it in
 * its old slot would draw a second copy underneath the ghost - the release would
 * then read as landing on a tile that never moved.
 *
 * A named function rather than an inline `filterNot` because **both** the content
 * lambda and the slot list have to apply the exact same rule. The measure policy
 * asserts the two stayed in step, so applying the skip to one and not the other is
 * not a cosmetic glitch: it is an exception that takes the screen down. It was
 * precisely that - the skip lived only in the content lambda, so the first
 * long-press threw `TierGeometry emitted N children for N+1 slots` and killed the
 * tier list the moment a drag began.
 */
internal fun visibleTierItems(
    items: List<TierItemBox>,
    skipMediaId: Int?
): List<TierItemBox> =
    if (skipMediaId == null) items else items.filterNot { it.mediaId == skipMediaId }

/** The heading this screen and any export of the same layout carry. */
private const val TIER_LIST_TITLE = "Tier List"

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

/**
 * On-screen geometry.
 *
 * The exporter's defaults (72-unit tiles, a 24-unit frame, a 480-unit floor) are
 * sized for a 1080-unit export canvas, not for a phone: at `itemWidth = 72` a
 * 360dp screen fits three tiles and the captions are unreadable. `headerHeight`
 * is 0 because this screen draws the heading itself, above the board; leaving the
 * layout's own header band set would reserve a band of space for a heading that
 * is not inside it.
 */
private val ScreenTierOptions = TierLayoutOptions(
    width = 360f,
    labelGutter = 52f,
    padding = 10f,
    itemWidth = 64f,
    itemGap = 6f,
    rowGap = 8f,
    captionHeight = 32f,
    headerHeight = 0f,
    minRowHeight = 48f,
    minCanvasHeight = 200f
)

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

/**
 * The tier list: rows, posters, and the two ways into one.
 *
 * ## The geometry is not computed here
 *
 * Every row and every tile is placed by [layoutTierList], in the *same* units
 * the SVG exporter reads. This file holds no second opinion about where anything
 * goes: it builds a [TierLayoutOptions] whose `width` is the measured screen width
 * in dp, asks the domain for the arrangement, and places children at the boxes it
 * hands back. A screen that arranged its own rows would agree with the export
 * right up until the day somebody changed a gap in one of them - and the symptom
 * would be an exported PNG whose labels are in a different order from the screen
 * it was exported from.
 *
 * ## Zoom divides the layout width rather than scaling the canvas
 *
 * Pinching sets `options.width = screenWidthDp / zoom`, and every box is placed at
 * `x * zoom`. There is therefore still one arrangement - the same numbers an
 * export would produce at zoom 1 - measured against a narrower or wider
 * coordinate space. Scaling a finished canvas with a `graphicsLayer` instead would
 * leave `itemWidth` meaning two different things depending on whether the caller
 * had zoomed, which is precisely the disagreement the shared layout exists to
 * prevent.
 *
 * ## Two ways in
 *
 * Seeding files everything by AniList score in one tap; starting empty puts the
 * unranked library in front of the person to place by hand. Seeding asserts a
 * judgement they did not make and nothing can make it for them, so both are
 * offered from the same empty state rather than one being hidden behind the other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TierListScreen(
    tierListViewModel: TierListViewModel,
    onNavigateBack: () -> Unit,
    /**
     * The AniList account being viewed, so seeding can be remembered per user.
     *
     * Optional because the route does not have one to hand at the point it builds
     * this screen. Without it `seedFromScores` still ranks everything; it only
     * skips `markSeededFor`, which is the flag that stops the seeding offer
     * reappearing on another account. Passing it when the caller can is the
     * difference between "offered once" and "offered on every launch".
     */
    userId: Int? = null,
    modifier: Modifier = Modifier
) {
    val viewModel = tierListViewModel
    // The system back gesture has to reach the same place as the bar's arrow, or
    // the screen is reachable two ways and leaves one of them.
    BackHandler { onNavigateBack() }

    // This route is its own screen, so there is no profile screen to hand the
    // library over from - the ViewModel reads it itself, which is a cache hit
    // for anyone who has already opened their profile and costs no request.
    // Keyed on Unit rather than skipped: re-entering the screen must re-read,
    // because the whole point is to rank whatever the library holds now.
    LaunchedEffect(Unit) {
        viewModel.loadLibrary()
    }

    val stored by viewModel.stored.collectAsStateWithLifecycle()
    val assignment by viewModel.liveAssignment.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val orphaned by viewModel.orphanedCount.collectAsStateWithLifecycle()
    val isSeeding by viewModel.isSeeding.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    var placingMediaId by rememberSaveable { mutableStateOf<Int?>(null) }
    var addingTitles by rememberSaveable { mutableStateOf(false) }
    var managingTiers by rememberSaveable { mutableStateOf(false) }

    val tiers = stored.tiers
    val subtitle = tierListSubtitle(assignment.tierIdByMediaId.size, candidates.size)

    // Keyed on the collected pool as well as on the assignment. `unranked` is a
    // plain function that reads the ViewModel's own StateFlow rather than taking
    // the pool as an argument, so without the `candidates` key this memo would
    // hand the sheet the library as it was on the first composition - which is
    // empty, because the library arrives after the screen does.
    val unranked = remember(candidates, assignment) {
        viewModel.unranked(assignment.tierIdByMediaId.keys)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("tier_list_screen"),
        topBar = {
            TopAppBar(
                title = { Text(TIER_LIST_TITLE, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("tier_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The bar's own insets, not a `contentPadding` argument: this is
                // a destination with its own Scaffold, not a tab inside someone
                // else's, and taking both would double the inset.
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
        ) {
            TierBoardToolbar(subtitle = subtitle, onManageTiers = { managingTiers = true })

            notice?.let { message ->
                TierNotice(text = message, onDismiss = { viewModel.dismissNotice() })
            }

            // The ViewModel already drops orphaned entries from `liveAssignment`,
            // so saying so is what stops the missing posters reading as data loss.
            if (orphaned > 0) {
                TierNotice(
                    text = "$orphaned " + (if (orphaned == 1) "title was" else "titles were") +
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
                    onSeed = { viewModel.seedFromScores(userId) },
                    onAddTitles = { addingTitles = true }
                )
            } else {
                TierBoardHost(
                    tiers = tiers,
                    assignment = assignment,
                    candidates = candidates,
                    onMove = { mediaId, tierId -> viewModel.moveTo(mediaId, tierId) },
                    onItemTap = { mediaId -> placingMediaId = mediaId }
                )
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
                viewModel.moveTo(placing, tierId)
                placingMediaId = null
            },
            onRemove = {
                viewModel.moveTo(placing, null)
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
            onMove = viewModel::moveTo,
            onDismiss = { addingTitles = false }
        )
    }

    if (managingTiers) {
        TierManageSheet(
            tiers = tiers,
            memberCounts = tiers.associate { it.id to assignment.membersOf(it.id).size },
            onRename = viewModel::renameTier,
            onAdd = viewModel::addTier,
            onRemove = viewModel::removeTier,
            onDismiss = { managingTiers = false }
        )
    }
}

/**
 * The counts, and the one control that edits the tiers themselves.
 *
 * The title is not here: it is the app bar's, which is where a back arrow lives
 * too, and repeating it one row down would put two different answers to "where
 * am I" on one screen.
 */
@Composable
private fun TierBoardToolbar(subtitle: String, onManageTiers: () -> Unit) {
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
        // Before the click, so the 48dp minimum is inside the hit area rather
        // than padding the row after the fact.
        TextButton(
            onClick = onManageTiers,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
            Icon(
                imageVector = AppVectorIcons.Tune,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Edit tiers")
        }
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
 * The state a brand-new list is in, offering both ways out of it.
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

// ---------------------------------------------------------------------------
// The board
// ---------------------------------------------------------------------------

/**
 * Measures the available width, then hands it to [TierBoard].
 *
 * Width has to be known *before* composition emits the children, because the
 * children are positioned by the layout that the width itself determines.
 * `onSizeChanged` rather than `BoxWithConstraints`: the latter measures its
 * content against `BoxScope` constraints, and its `maxWidth`/`minWidth` accessors
 * are on their way out in favour of `constraints`, so a screen written against
 * them would be standing on a deprecated API to save one frame.
 */
@Composable
private fun TierBoardHost(
    tiers: List<TierDefinition>,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    onMove: (Int, String?) -> Unit,
    onItemTap: (Int) -> Unit
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width }
            .testTag("tier_board_host")
    ) {
        // Nothing is drawn until the first measurement lands. A layout built at
        // width 0 packs one item per row and gives every row its minimum height,
        // so rendering that frame would flash six full-width bars.
        if (widthPx > 0) {
            TierBoard(
                widthDp = with(density) { widthPx.toDp() }.value,
                tiers = tiers,
                assignment = assignment,
                candidates = candidates,
                onMove = onMove,
                onItemTap = onItemTap
            )
        }
    }
}

@Composable
private fun TierBoard(
    widthDp: Float,
    tiers: List<TierDefinition>,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    onMove: (Int, String?) -> Unit,
    onItemTap: (Int) -> Unit
) {
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    // Held as the state object rather than read with `by`, because the dragged
    // ghost's translation has to be read inside its `graphicsLayer` block. A
    // `by` here would read it during composition and rebuild the modifier chain
    // on every pointer move, which is a recomposition per frame for a drag.
    val dragTravel = remember { mutableStateOf(Offset.Zero) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val options = remember(widthDp, zoom) { ScreenTierOptions.copy(width = widthDp / zoom) }
    val arrangement = remember(options, tiers, assignment, candidates) {
        layoutTierList(
            tiers = tiers,
            assignment = assignment,
            candidates = candidates,
            title = TIER_LIST_TITLE,
            subtitle = tierListSubtitle(assignment.tierIdByMediaId.size, candidates.size),
            options = options
        )
    }
    val candidateById = remember(candidates) { candidates.associateBy { it.mediaId } }

    // Derived rather than recomputed on every pointer move. Reading `dragTravel`
    // straight into composition made the whole board recompose each frame of a
    // drag, only to redraw the same hint; a `derivedStateOf` notifies its readers
    // only when the tier *under the finger* changes, so dragging across one row is
    // a draw invalidation (the ghost's `graphicsLayer` already reads the travel in
    // the draw phase) instead of a full recomposition of every row and poster.
    val hoveredTierId = remember(draggingId, arrangement, zoom, density) {
        derivedStateOf<String?> {
            if (draggingId == null) {
                null
            } else {
                val position = dragOrigin + dragTravel.value
                tierDropTarget(
                    rows = arrangement.rows,
                    x = position.x / density.density / zoom,
                    y = position.y / density.density / zoom
                )
            }
        }
    }

    // The transform block is `suspend` and returns a Boolean, so the assignment
    // cannot be the last expression without a type error.
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        zoom = clampTierZoom(zoom * zoomChange)
        true
    }

    val dragBox = draggingId?.let { arrangement.boxByMediaId[it] }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .transformable(state = transformState)
            .pointerInput(arrangement, zoom) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val mediaId = tierItemAt(
                            items = arrangement.items,
                            x = offset.x / density.density / zoom,
                            y = offset.y / density.density / zoom
                        )
                        if (mediaId == null) return@detectDragGesturesAfterLongPress
                        draggingId = mediaId
                        dragOrigin = offset
                        dragTravel.value = Offset.Zero
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragTravel.value += amount
                    },
                    onDragEnd = {
                        val mediaId = draggingId
                        if (mediaId != null) {
                            // A null `hoveredTierId` means the release landed off
                            // every row, and a null tier id is exactly how
                            // `moveTo` un-files a title. This is the only route to
                            // an empty row that does not delete the row too.
                            onMove(mediaId, hoveredTierId.value)
                        }
                        draggingId = null
                        dragTravel.value = Offset.Zero
                    },
                    onDragCancel = {
                        draggingId = null
                        dragTravel.value = Offset.Zero
                    }
                )
            }
    ) {
        TierGeometry(
            arrangement = arrangement,
            options = options,
            zoom = zoom,
            candidateById = candidateById,
            hoveredTierId = hoveredTierId.value,
            skipMediaId = draggingId,
            onItemTap = onItemTap
        )

        if (dragBox != null) {
            TierDragGhost(
                box = dragBox,
                candidate = candidateById[dragBox.mediaId],
                zoom = zoom,
                posterHeight = options.posterHeight * zoom,
                captionHeight = options.captionHeight * zoom,
                travel = dragTravel
            )
        }

        if (draggingId != null) {
            // A pill rather than bare text. The hint sits over the board, and a
            // label drawn straight onto a row's colour or across a poster is
            // unreadable exactly when it is needed most - mid-drag, on whatever
            // tier the finger happens to be over. A surface behind it keeps it
            // legible against every tier colour and every cover.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("tier_drag_hint")
            ) {
                Text(
                    text = if (hoveredTierId != null) {
                        "Release to file under ${tierLabel(tiers, hoveredTierId.value!!)}"
                    } else {
                        "Release here to take it out of the list"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A tier's label, for text that has to name the tier a drop would land in. */
private fun tierLabel(tiers: List<TierDefinition>, tierId: String): String =
    tiers.firstOrNull { it.id == tierId }?.label ?: tierId

/**
 * Places the rows and the tiles at the boxes [layoutTierList] produced.
 *
 * A `Layout` rather than a `Canvas` so the posters are real `AsyncImage`s that
 * Coil can cancel, recycle and cross-fade. The measure policy is the only place
 * in the app that knows about positions, and it knows about nothing else.
 *
 * The content lambda and [slots] are two views of the same ordered list - rows,
 * then the tiles - and the `check` is what keeps them that way. Compose hands the
 * measure policy its children in emission order and offers no way to ask which is
 * which, so a row added to one list and not the other would otherwise place every
 * tile one row too high, and it would read as a layout bug for ever.
 *
 * Both halves read the same [visibleTierItems] list rather than filtering for
 * themselves. They used to disagree - the dragged tile was skipped from the
 * emitted children only - so starting a drag made the two counts differ by one and
 * the `check` below threw. Sharing the list is what makes that impossible rather
 * than merely fixed.
 *
 * Named `arrangement` rather than `layout` because a parameter called `layout`
 * would shadow `MeasureScope.layout` at the `layout(width, height)` call at the
 * bottom of this function.
 */
@Composable
private fun TierGeometry(
    arrangement: TierListLayout,
    options: TierLayoutOptions,
    zoom: Float,
    candidateById: Map<Int, TierCandidate>,
    hoveredTierId: String?,
    skipMediaId: Int?,
    onItemTap: (Int) -> Unit
) {
    // The one tile list the whole layout is built from, so the emitted children
    // and the positions they are measured against cannot drift apart.
    val drawnItems = remember(arrangement, skipMediaId) {
        visibleTierItems(arrangement.items, skipMediaId)
    }
    val slots = remember(arrangement, drawnItems) {
        buildList<Slot> {
            arrangement.rows.forEach { add(Slot(it.x, it.y, it.width, it.height)) }
            drawnItems.forEach { add(Slot(it.x, it.y, it.width, it.height)) }
        }
    }

    Layout(
        content = {
            arrangement.rows.forEach { row ->
                TierRowStrip(
                    row = row,
                    labelGutter = options.labelGutter,
                    highlighted = row.tierId == hoveredTierId
                )
            }
            drawnItems.forEach { box ->
                TierPosterTile(
                    candidate = candidateById[box.mediaId],
                    width = box.width,
                    posterHeight = options.posterHeight,
                    captionHeight = options.captionHeight,
                    onTap = { onItemTap(box.mediaId) }
                )
            }
        },
        measurePolicy = { measurables, _ ->
            check(measurables.size == slots.size) {
                "TierGeometry emitted ${measurables.size} children for ${slots.size} " +
                    "slots; the content lambda and the slot list must stay in step."
            }
            val placeables = measurables.mapIndexed { index, measurable ->
                val slot = slots[index]
                measurable.measure(
                    Constraints.fixed(
                        width = (slot.width * zoom).dp.toPx().roundToInt(),
                        height = (slot.height * zoom).dp.toPx().roundToInt()
                    )
                )
            }
            layout(
                width = (arrangement.width * zoom).dp.toPx().roundToInt(),
                height = (arrangement.height * zoom).dp.toPx().roundToInt()
            ) {
                placeables.forEachIndexed { index, placeable ->
                    val slot = slots[index]
                    placeable.place(
                        x = (slot.x * zoom).dp.toPx().roundToInt(),
                        y = (slot.y * zoom).dp.toPx().roundToInt()
                    )
                }
            }
        },
        modifier = Modifier.testTag("tier_geometry")
    )
}

/** One position, in the layout's own dp units, before zoom is applied. */
private data class Slot(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * A tier's coloured row, with its label in the gutter.
 *
 * The label is vertically centred for the same reason the SVG writer centres it:
 * a one-item row and a four-item row are 128dp and 512dp tall, and a top-aligned
 * label makes the short one read as a section header rather than as the same
 * control with less in it.
 *
 * The highlight animates *colour* on the border and never the row's size. Growing
 * a row while a tile is being dropped onto it would reflow every other tile in
 * that row under the finger that is holding the tile being dragged.
 */
@Composable
private fun TierRowStrip(row: TierRowBox, labelGutter: Float, highlighted: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    val border by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.Transparent
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "tier_row_highlight"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            // The same 0.92 the SVG writer uses, so a shared list does not change
            // temperature between the screen and the file exported from it.
            .background(tierRowColor(row.colorArgb).copy(alpha = 0.92f))
            .border(3.dp, border, shape),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .width(labelGutter.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/**
 * One poster, its caption, and - when there is one - its AniList score.
 *
 * The caption sits *under* the poster rather than across it. `FavouriteMediaRow`
 * records why this app already tried the other way: AniList covers carry the
 * series' own typography, frequently vertical and running to the edges, and a
 * caption over that is unreadable. The score badge stays on the cover because a
 * two-digit number in the top-start corner lands on padding that is empty on
 * almost every poster, which is the same reason the Library tab's progress badge
 * survives in the same corner.
 *
 * `defaultMinSize` is deliberately absent here. Every tile is measured with
 * `Constraints.fixed` at `itemWidth`, which is already above 48dp, so a minimum
 * would be a no-op that reads like a guarantee it is not enforcing - the real
 * floor is `ScreenTierOptions.itemWidth`, and it is the thing to change if it
 * ever needs to move.
 */
@Composable
private fun TierPosterTile(
    candidate: TierCandidate?,
    width: Float,
    posterHeight: Float,
    captionHeight: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val placeholder = rememberVectorPainter(AppVectorIcons.List)

    Column(
        modifier = modifier
            .width(width.dp)
            // Before the clickable, sharing one source: sharing is what cancels
            // the press spring the moment the gesture turns into a scroll instead
            // of leaving the tile squashed under a finger that is scrolling.
            .bouncyPress(pressedScale = 0.94f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onTap)
            .testTag("tier_tile")
    ) {
        Box(
            modifier = Modifier
                .width(width.dp)
                .height(posterHeight.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = candidate?.coverUrl,
                contentDescription = candidate?.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder
            )

            // AniList answers 0 as readily as null for "nobody has scored this",
            // and a badge reading "0" claims a title is terrible rather than
            // unrated.
            val score = candidate?.averageScore
            if (score != null && score > 0) {
                Text(
                    text = score.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(ExpressiveShapes.pill)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Text(
            text = candidate?.title.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(width.dp)
                .height(captionHeight.dp)
        )
    }
}

/**
 * The poster under the finger, lifted clear of the board.
 *
 * Both the translation and the lift are read inside `graphicsLayer`, so a drag
 * invalidates the draw pass alone. Offsetting the node during layout instead would
 * re-measure and re-place the whole board on every pointer move, which is what
 * makes a drag inside a scrolling list stutter.
 *
 * Drawn as a sibling *after* [TierGeometry] so it lands on top. A `Layout` draws
 * its children in the order it emitted them and has no z-index, so the only way to
 * put the dragged tile above the row it is hovering over is not to emit it there.
 *
 * The dimensions handed in are already multiplied by `zoom`: the ghost has to be
 * the same size on screen as the slot it came out of, or releasing it lands the
 * finger on a different part of the tile than the one they picked up.
 */
@Composable
private fun TierDragGhost(
    box: TierItemBox,
    candidate: TierCandidate?,
    zoom: Float,
    posterHeight: Float,
    captionHeight: Float,
    travel: State<Offset>
) {
    val lift by animateFloatAsState(
        targetValue = 1.12f,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "tier_drag_lift"
    )

    TierPosterTile(
        candidate = candidate,
        width = box.width * zoom,
        posterHeight = posterHeight,
        captionHeight = captionHeight,
        onTap = {},
        modifier = Modifier
            .offset(x = (box.x * zoom).dp, y = (box.y * zoom).dp)
            // `travel.value` is read *inside* the layer block, which is what keeps
            // a pointer move down to a draw invalidation instead of a rebuild of
            // this modifier chain every frame.
            .graphicsLayer {
                translationX = travel.value.x
                translationY = travel.value.y
                scaleX = lift
                scaleY = lift
            }
            .testTag("tier_drag_ghost")
    )
}

// ---------------------------------------------------------------------------
// Sheets
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TierPlaceSheet(
    title: String,
    tiers: List<TierDefinition>,
    currentTierId: String?,
    onMove: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = title.ifBlank { "Move to" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TierChoiceList(
                tiers = tiers,
                currentTierId = currentTierId,
                onPick = onMove,
                onRemove = onRemove
            )
        }
    }
}

/**
 * The "start empty" route: the library's unranked titles, then the tiers.
 *
 * Both steps live in *one* sheet, switching on internal state, because
 * `ModalBottomSheet` presents into its own window and two of them up at once -
 * even for a single frame - is the documented way to reach "only one sheet can be
 * presented". Picking a title here therefore closes nothing; it replaces the
 * contents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TierAddTitlesSheet(
    unranked: List<TierCandidate>,
    tiers: List<TierDefinition>,
    currentTierOf: (Int) -> String?,
    onMove: (Int, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var pickingId by remember { mutableStateOf<Int?>(null) }
    val picking = pickingId?.let { id -> unranked.firstOrNull { it.mediaId == id } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (picking == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .testTag("tier_add_titles_sheet"),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item(key = "header") {
                    Text(
                        text = if (unranked.isEmpty()) {
                            "Every title in your library is ranked."
                        } else {
                            "Add titles (${unranked.size} unranked)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(unranked, key = { it.mediaId }) { candidate ->
                    TierCandidateRow(
                        candidate = candidate,
                        onClick = { pickingId = candidate.mediaId }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = picking.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TierChoiceList(
                    tiers = tiers,
                    currentTierId = currentTierOf(picking.mediaId),
                    onPick = { tierId ->
                        onMove(picking.mediaId, tierId)
                        // Back to the unranked list, so placing one title flows
                        // straight into placing the next. The picker used to stay
                        // open on the title it had just filed, which read as the
                        // sheet being stuck rather than as a place to keep working.
                        pickingId = null
                    },
                    onRemove = null
                )
            }
        }
    }
}

/**
 * The tiers as a list of choices, plus optionally the way back out.
 *
 * The colour dot beside each label is drawn from the tier's own `colorArgb`, which
 * is what keeps a renamed tier recognisable as the row it came from.
 */
@Composable
private fun TierChoiceList(
    tiers: List<TierDefinition>,
    currentTierId: String?,
    onPick: (String) -> Unit,
    onRemove: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        tiers.forEach { tier ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { onPick(tier.id) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(tierRowColor(tier.colorArgb))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = tier.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (tier.id == currentTierId) {
                    Icon(
                        imageVector = AppVectorIcons.Done,
                        contentDescription = "Currently in this tier",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (onRemove != null) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            TextButton(
                onClick = onRemove,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = "Remove from this list",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun TierCandidateRow(candidate: TierCandidate, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val placeholder = rememberVectorPainter(AppVectorIcons.List)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Before the gesture, so the minimum is inside the hit area.
            .defaultMinSize(minHeight = 48.dp)
            .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = candidate.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(width = 32.dp, height = 48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = candidate.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val score = candidate.averageScore
        if (score != null && score > 0) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Rename, add and remove tiers.
 *
 * Removing a tier *drops* its entries rather than promoting them to the tier above.
 * `TierListViewModel.removeTier` documents why, and the dialog repeats it in the
 * reader's terms: promoting would file a title under a judgement the person never
 * made, and an entry nobody placed is a truer state than one placed by a deletion.
 * A dialog that only asked "Delete tier?" would leave somebody losing an
 * afternoon of ranking to one tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TierManageSheet(
    tiers: List<TierDefinition>,
    memberCounts: Map<String, Int>,
    onRename: (String, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var renamingId by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf<TierDefinition?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .testTag("tier_manage_sheet"),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Text(
                    text = "Tiers",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            items(tiers, key = { it.id }) { tier ->
                val members = memberCounts[tier.id] ?: 0
                if (renamingId == tier.id) {
                    TierRenameField(
                        initial = tier.label,
                        onCancel = { renamingId = null },
                        onConfirm = { label ->
                            onRename(tier.id, label)
                            renamingId = null
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(tierRowColor(tier.colorArgb))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${tier.label} - $members",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { renamingId = tier.id },
                            modifier = Modifier.defaultMinSize(
                                minWidth = 48.dp,
                                minHeight = 48.dp
                            )
                        ) {
                            Text("Rename")
                        }
                        TextButton(
                            onClick = { pendingRemoval = tier },
                            // `removeTier` refuses to go below one row, because
                            // with nothing left there is nowhere to drag onto.
                            // Disabled rather than hidden so the control does not
                            // appear and disappear as tiers come and go.
                            enabled = tiers.size > 1,
                            modifier = Modifier.defaultMinSize(
                                minWidth = 48.dp,
                                minHeight = 48.dp
                            )
                        ) {
                            Text(
                                text = "Remove",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item(key = "add") {
                TextButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Add a tier")
                }
            }
        }
    }

    val removal = pendingRemoval
    if (removal != null) {
        val members = memberCounts[removal.id] ?: 0
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove \"${removal.label}\"?") },
            text = {
                Text(
                    text = if (members == 0) {
                        "This tier is empty, so nothing else changes."
                    } else {
                        "The $members " + (if (members == 1) "title" else "titles") +
                            " filed under it " + (if (members == 1) "is" else "are") +
                            " removed with it, not moved into another tier. They go back" +
                            " to your library, unranked."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(removal.id)
                    pendingRemoval = null
                }) {
                    Text("Remove tier", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("Keep it")
                }
            }
        )
    }
}

@Composable
private fun TierRenameField(
    initial: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // `remember`, not `rememberSaveable`: a half-typed label is a transient edit,
    // and restoring one into a row that was re-entered minutes later would be a
    // silent overwrite of whatever the name is by then.
    var text by remember { mutableStateOf(initial) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            shape = ExpressiveShapes.pill,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("tier_rename_field")
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = { onConfirm(text) }) {
            Icon(
                imageVector = AppVectorIcons.Done,
                contentDescription = "Save tier name"
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = AppVectorIcons.Close,
                contentDescription = "Cancel rename"
            )
        }
    }
}
