package com.anisequel.ui.screens.tier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierDisplay
import com.anisequel.domain.tier.TierFilter

/**
 * Which modal the board has open, if any.
 *
 * Its own object because the board opens sheets from three different places (the
 * app bar, the header, and a tap on a poster or the pool) and the sheets close
 * themselves. Keeping seven `rememberSaveable` flags in the screen made every
 * sheet's dismiss callback another branch in an already long composition root;
 * the state is saved as one map so a rotation still keeps an open sheet open.
 */
internal class TierBoardSheetState {
    var placingMediaId by mutableStateOf<Int?>(null)
    var addingTitles by mutableStateOf(false)
    var managingTiers by mutableStateOf(false)
    var filtering by mutableStateOf(false)
    var displaying by mutableStateOf(false)
    var coloringTierId by mutableStateOf<String?>(null)
    var bandTierId by mutableStateOf<String?>(null)
}

private val TierBoardSheetStateSaver: Saver<TierBoardSheetState, Any> = mapSaver(
    save = { state ->
        mapOf(
            "placing" to state.placingMediaId,
            "adding" to state.addingTitles,
            "managing" to state.managingTiers,
            "filtering" to state.filtering,
            "displaying" to state.displaying,
            "coloring" to state.coloringTierId,
            "band" to state.bandTierId
        )
    },
    restore = { saved ->
        TierBoardSheetState().apply {
            placingMediaId = saved["placing"] as Int?
            addingTitles = saved["adding"] as Boolean? ?: false
            managingTiers = saved["managing"] as Boolean? ?: false
            filtering = saved["filtering"] as Boolean? ?: false
            displaying = saved["displaying"] as Boolean? ?: false
            coloringTierId = saved["coloring"] as String?
            bandTierId = saved["band"] as String?
        }
    }
)

@Composable
internal fun rememberTierBoardSheetState(): TierBoardSheetState =
    rememberSaveable(saver = TierBoardSheetStateSaver) { TierBoardSheetState() }

/** The data every sheet reads, kept together so the host has three arguments. */
internal data class TierBoardSheetData(
    val tiers: List<TierDefinition>,
    val assignment: TierAssignment,
    val candidates: List<TierCandidate>,
    val unranked: List<TierCandidate>,
    val ranked: Int,
    val filter: TierFilter,
    val display: TierDisplay,
    val zoom: Float
)

/**
 * What a sheet can ask the board to do.
 *
 * A data class of callbacks rather than twenty parameters: the host is a switch
 * between sheets, and the sheet it opens changes on every tap, so the callback
 * set has to travel with it.
 */
internal class TierBoardSheetActions(
    val onMove: (mediaId: Int, tierId: String?, beforeMediaId: Int?) -> Unit,
    val onRenameTier: (String, String) -> Unit,
    val onAddTier: () -> Unit,
    val onRemoveTier: (String) -> Unit,
    val onTierColor: (String, Long) -> Unit,
    val onTierScoreFloor: (String, Int?) -> Unit,
    val onFilterChange: (TierFilter) -> Unit,
    val onDisplayChange: (TierDisplay) -> Unit,
    val onResetZoom: () -> Unit,
    val onClearPlacements: () -> Unit
)

/**
 * Every sheet the board can open, in one place.
 *
 * Extracted from [TierBoardScreen] because each sheet contributed its own open
 * check and dismiss lambda to that function's branch count, while none of them
 * share anything except the data they read. The screen now opens a sheet by
 * setting one field and this host decides which composable that is.
 */
@Composable
internal fun TierBoardSheetsHost(
    state: TierBoardSheetState,
    data: TierBoardSheetData,
    actions: TierBoardSheetActions
) {
    val placing = state.placingMediaId
    if (placing != null) {
        val placed = data.candidates.firstOrNull { it.mediaId == placing }
        TierPlaceSheet(
            title = placed?.title.orEmpty(),
            coverUrl = placed?.coverUrl,
            tiers = data.tiers,
            currentTierId = data.assignment.tierOf(placing),
            onMove = { tierId ->
                actions.onMove(placing, tierId, null)
                state.placingMediaId = null
            },
            onRemove = {
                actions.onMove(placing, null, null)
                state.placingMediaId = null
            },
            onDismiss = { state.placingMediaId = null }
        )
    }

    if (state.addingTitles) {
        TierAddTitlesSheet(
            unranked = data.unranked,
            tiers = data.tiers,
            currentTierOf = { mediaId -> data.assignment.tierOf(mediaId) },
            onMove = actions.onMove,
            onDismiss = { state.addingTitles = false }
        )
    }

    if (state.managingTiers) {
        TierManageSheet(
            tiers = data.tiers,
            memberCounts = data.tiers.associate { it.id to data.assignment.membersOf(it.id).size },
            rankedCount = data.ranked,
            onRename = actions.onRenameTier,
            onAdd = actions.onAddTier,
            onRemove = actions.onRemoveTier,
            onColor = { tier -> state.coloringTierId = tier.id },
            onScoreFloor = { tier -> state.bandTierId = tier.id },
            onClearPlacements = actions.onClearPlacements,
            onDismiss = { state.managingTiers = false }
        )
    }

    if (state.filtering) {
        TierFilterSheet(
            filter = data.filter,
            onFilterChange = actions.onFilterChange,
            onDismiss = { state.filtering = false }
        )
    }

    if (state.displaying) {
        TierDisplaySheet(
            // The live zoom, not the stored one: a pinch is persisted on a
            // debounce, so the stored value can be up to a gesture behind the
            // board. Toggling a title in here must not write that stale number
            // back over the zoom the person just set.
            display = data.display.copy(zoom = data.zoom),
            onDisplayChange = actions.onDisplayChange,
            // Reset goes through the same local value the stepper writes, so it
            // cannot lose a race with the debounced write of the zoom it replaced.
            onResetZoom = actions.onResetZoom,
            onDismiss = { state.displaying = false }
        )
    }

    val coloring = state.coloringTierId?.let { id -> data.tiers.firstOrNull { it.id == id } }
    if (coloring != null) {
        TierColorDialog(
            tier = coloring,
            onPick = { color ->
                actions.onTierColor(coloring.id, color)
                state.coloringTierId = null
            },
            onDismiss = { state.coloringTierId = null }
        )
    }

    val bandTier = state.bandTierId?.let { id -> data.tiers.firstOrNull { it.id == id } }
    if (bandTier != null) {
        TierScoreFloorDialog(
            tier = bandTier,
            onSet = { floor -> actions.onTierScoreFloor(bandTier.id, floor) },
            onDismiss = { state.bandTierId = null }
        )
    }
}
