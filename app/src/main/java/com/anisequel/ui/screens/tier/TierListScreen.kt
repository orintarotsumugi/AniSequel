package com.anisequel.ui.screens.tier

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.domain.tier.TierMediaType
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.viewmodel.TierListViewModel

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
 * ## The library has two shelves
 *
 * Anime and manga are separate pools with separate lists. The section lives here,
 * in the screen, because it is a way of looking at the library rather than a
 * property of any list: a list already knows its own type, and opening one switches
 * the shelf to match it. The manga half of the library is fetched the first time
 * that shelf is opened - AniList numbers the two namespaces independently and the
 * ids collide, so neither pool can be used to answer for the other.
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

    // Which shelf of the library is showing. Saved across rotation because a
    // person who was looking at their manga lists should not come back to anime
    // after turning the phone; not stored because it is not a fact about the
    // lists, and a new visit may reasonably start on the first shelf.
    var section by rememberSaveable { mutableStateOf(TierMediaType.ANIME) }

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
    val animeCandidates by viewModel.animeCandidates.collectAsStateWithLifecycle()
    val mangaCandidates by viewModel.mangaCandidates.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val isSeeding by viewModel.isSeeding.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val undoLabel by viewModel.undoLabel.collectAsStateWithLifecycle()
    val redoLabel by viewModel.redoLabel.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val isLoadingManga by viewModel.isLoadingManga.collectAsStateWithLifecycle()
    val mangaLoadFailed by viewModel.mangaLoadFailed.collectAsStateWithLifecycle()

    val document = opened

    // The manga list is fetched the first time its shelf is opened, and again if a
    // manga board is restored directly without the shelf being touched. Guarded in
    // the ViewModel, so this is safe to call on every recomposition of the effect.
    LaunchedEffect(section, document?.mediaType) {
        if (section == TierMediaType.MANGA || document?.mediaType == TierMediaType.MANGA) {
            viewModel.loadMangaLibrary()
        }
    }

    // The system back gesture has to reach the same place as the bar's arrow, or
    // the feature is reachable two ways and leaves one of them. Inside a list that
    // is the library; on the library it is wherever the trophy was opened from.
    BackHandler {
        if (document == null) onNavigateBack() else viewModel.closeList()
    }

    // The switch between the shelf of lists and an open board is a state change
    // inside one route, so the route transition never sees it: without this it
    // was a hard cut in the middle of the destination. `contentKey` is the list
    // id, not the document, because the document is replaced on every edit - a
    // drag, a rename, an undo - and animating those would re-open the screen
    // under the finger.
    AnimatedContent(
        targetState = document,
        modifier = modifier.fillMaxSize(),
        contentKey = { it?.id },
        transitionSpec = {
            val opening = targetState != null
            // Opening moves towards the left, closing towards the right: the same
            // direction language as a route push and pop, so the gesture reads
            // the same wherever the person is.
            val direction = if (opening) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            }
            (slideIntoContainer(
                direction,
                animationSpec = ExpressiveMotion.DefaultSpatialOffset,
                // A short travel rather than a whole page: the list expands out
                // of the shelf it was tapped on, and the shelf recedes under it.
                initialOffset = { fullWidth -> fullWidth / 6 }
            ) + fadeIn(ExpressiveMotion.DefaultEffects) + scaleIn(
                initialScale = 0.96f,
                animationSpec = ExpressiveMotion.BouncySpatial
            )) togetherWith (slideOutOfContainer(
                direction,
                animationSpec = ExpressiveMotion.DefaultSpatialOffset,
                targetOffset = { fullWidth -> fullWidth / 12 }
            ) + fadeOut(ExpressiveMotion.DefaultEffects) + scaleOut(
                targetScale = 0.98f,
                animationSpec = ExpressiveMotion.FastSpatial
            ))
        },
        label = "tier_library_board"
    ) { openDocument ->
        if (openDocument == null) {
            val sectionCandidates = when (section) {
                TierMediaType.ANIME -> animeCandidates
                TierMediaType.MANGA -> mangaCandidates
            }
            TierLibraryScreen(
                documents = library.documents.filter { it.mediaType == section },
                recent = recent?.takeIf { it.mediaType == section },
                section = TierLibrarySection(
                    mediaType = section,
                    librarySize = sectionCandidates.orEmpty().size,
                    isLoaded = sectionCandidates != null,
                    isLoading = section == TierMediaType.MANGA && isLoadingManga,
                    isUnavailable = section == TierMediaType.MANGA && !viewModel.canLoadManga,
                    loadFailed = section == TierMediaType.MANGA && mangaLoadFailed
                ),
                onSelectMediaType = { section = it },
                onRetryLoad = viewModel::loadMangaLibrary,
                onOpen = { listId ->
                    library.documents.firstOrNull { it.id == listId }?.let {
                        section = it.mediaType
                    }
                    viewModel.openList(listId)
                },
                onCreate = { mediaType ->
                    section = mediaType
                    viewModel.createList(mediaType)
                },
                onRename = viewModel::renameList,
                onDelete = viewModel::deleteList,
                onNavigateBack = onNavigateBack,
                modifier = Modifier
            )
        } else {
            TierBoardScreen(
                document = openDocument,
                assignment = assignment,
                candidates = candidates,
                filter = filter,
                orphaned = orphaned,
                isSeeding = isSeeding,
                notice = notice,
                undoLabel = undoLabel,
                redoLabel = redoLabel,
                onSeed = viewModel::seedFromScores,
                onMove = viewModel::moveTo,
                onClearPlacements = viewModel::clearPlacements,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onRenameTier = viewModel::renameTier,
                onAddTier = viewModel::addTier,
                onRemoveTier = viewModel::removeTier,
                onTierColor = viewModel::setTierColor,
                onTierScoreFloor = viewModel::setTierScoreFloor,
                onFilterChange = viewModel::setFilter,
                onDisplayChange = viewModel::setDisplay,
                onZoomChange = viewModel::setZoom,
                onDismissNotice = viewModel::dismissNotice,
                onLeave = viewModel::closeList,
                modifier = Modifier
            )
        }
    }
}
