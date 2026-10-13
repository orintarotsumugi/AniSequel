package com.example.ui.screens.tier

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.TierListViewModel

/**
 * The tier list destination: the lists, and whichever one is open.
 *
 * ## One route, two screens, and one place that decides
 *
 * The trophy still opens one destination, and this is it. Which of the two screens
 * is showing is `opened == null` - the library - and nothing else: no second route,
 * no argument in the navigation graph, and therefore no way for a deep link, a
 * restored back stack and the app's own back gesture to disagree about where they
 * are. Back inside a list returns to the lists because that is one state change
 * ([TierListViewModel.closeList]) rather than a pop of something that was pushed.
 *
 * ## This file was 1,587 lines and is now a host
 *
 * The board, its geometry, its gestures and its sheets were parts of it that change
 * for different reasons: the drag maths is arithmetic over boxes and is unit-tested
 * without a device, the sheets hand one decision back to the caller, and this is
 * wiring. They now live in `TierBoardLogic`, `TierBoard`, `TierBoardScreen` and
 * `TierPlacementSheets`, all in this package, which is the split the repository's
 * conventions ask for - by responsibility, not by line count.
 *
 * ## Seeding takes the account from the ViewModel, not from the caller
 *
 * It used to take a `userId` parameter that the one call site never passed, so the
 * "already offered" flag was never written and the offer came back on every launch.
 * The ViewModel is constructed with the signed-in account, so it already knows, and
 * a parameter that can only be wrong is not worth threading through a screen.
 */
@Composable
fun TierListScreen(
    tierListViewModel: TierListViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = tierListViewModel

    // This route is its own screen, so there is no profile screen to hand the
    // library over from - the ViewModel reads it itself, which is a cache hit
    // for anyone who has already opened their profile and costs no request.
    // Keyed on Unit rather than skipped: re-entering the screen must re-read,
    // because the whole point is to rank whatever the library holds now.
    LaunchedEffect(Unit) {
        viewModel.loadLibrary()
    }

    val library by viewModel.library.collectAsStateWithLifecycle()
    val opened by viewModel.opened.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val assignment by viewModel.liveAssignment.collectAsStateWithLifecycle()
    val orphaned by viewModel.orphanedCount.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val isSeeding by viewModel.isSeeding.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val undoLabel by viewModel.undoLabel.collectAsStateWithLifecycle()

    val document = opened

    // The system back gesture has to reach the same place as the bar's arrow, or
    // the feature is reachable two ways and leaves one of them. Inside a list that
    // is the library; on the library it is wherever the trophy was opened from.
    BackHandler {
        if (document == null) onNavigateBack() else viewModel.closeList()
    }

    if (document == null) {
        TierLibraryScreen(
            documents = library.documents,
            recent = recent,
            librarySize = candidates.size,
            onOpen = viewModel::openList,
            onCreate = viewModel::createList,
            onRename = viewModel::renameList,
            onDelete = viewModel::deleteList,
            onNavigateBack = onNavigateBack,
            modifier = modifier
        )
    } else {
        TierBoardScreen(
            document = document,
            assignment = assignment,
            candidates = candidates,
            orphaned = orphaned,
            isSeeding = isSeeding,
            notice = notice,
            undoLabel = undoLabel,
            onSeed = viewModel::seedFromScores,
            onMove = viewModel::moveTo,
            onClearPlacements = viewModel::clearPlacements,
            onUndo = viewModel::undo,
            onRenameTier = viewModel::renameTier,
            onAddTier = viewModel::addTier,
            onRemoveTier = viewModel::removeTier,
            onDismissNotice = viewModel::dismissNotice,
            onLeave = viewModel::closeList,
            modifier = modifier
        )
    }
}
