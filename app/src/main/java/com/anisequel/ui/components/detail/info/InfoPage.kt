package com.anisequel.ui.components.detail.info

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.ThemeSong
import com.anisequel.data.model.displayName
import com.anisequel.data.model.pageUrl
import com.anisequel.data.repository.AniThemes
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.FullScreenCoverViewer
import com.anisequel.ui.viewmodel.QuickAddStatus
import kotlinx.coroutines.launch

/**
 * The anime Info page: one scroll of cards under a hero that gets out of the way.
 *
 * ## Why one page instead of the three tabs it replaces
 *
 * The sheet used to split a single title across "Story", "Specs" and "My Entry",
 * which meant that the answer to "what is this show" was spread over two of them and
 * neither was complete: the story tab had the synopsis, the specs tab had the
 * numbers, and the reader had to hold both in their head. A page that scrolls -
 * artwork, progress, synopsis, the numbers, the cast, the crew, the related titles -
 * is the shape the reference app uses, and it is the shape the data wants: it is one
 * title, and there is no reason any of it should be behind a tab.
 *
 * The editor stays behind a tab, because editing is a mode rather than a section: it
 * is the only part of the sheet that can *write*, and it is reached from the floating
 * capsule rather than scrolled to by accident.
 *
 * ## What is in this file, and what is not
 *
 * This function owns the page's *behaviour*: the state it derives, the one request
 * it makes that is not AniList's, and the shell - a `LazyColumn` under a floating
 * bar. What it *lists*, in order, is `infoPageItems`, and the decisions about which
 * sections exist are [infoSectionsFor]. Those lived here too, and the body reached
 * four hundred lines with fifteen `item { }` declarations in it - long enough to be
 * reported as a Complex Method (CodeFactor failed the pull request that added the
 * reference sections on exactly that). The split is by reason to change rather than
 * by size: adding a section is a change to the list; retiming the hero is a change
 * to this file.
 *
 * ## The scroll-driven motion
 *
 * [rememberInfoCollapse] is one `derivedStateOf` over the list's own scroll state,
 * read as a function by the hero's `graphicsLayer` and by nothing else - the
 * parallax is a draw-phase transform, and the page never recomposes while it is
 * scrolling. The floating bar is the one thing that *is* derived in composition,
 * and it is derived through a `derivedStateOf` producing a `Boolean`: the
 * recomposition happens when the bar crosses its threshold, not on the 300 frames
 * before it.
 *
 * That threshold is not a number this file owns: the hero fades its lockup out
 * across the same stretch of scroll, so the two live together as
 * [DOCKED_BAR_AT] and [LOCKUP_FADE_FROM] in `InfoMotion.kt`. See the note there.
 */
@Composable
fun AnimeInfoPage(
    subject: AnimeDetailSubject,
    isDetailLoading: Boolean,
    canWriteToAniList: Boolean,
    holdDurationSeconds: Int,
    swipeEnabled: Boolean,
    onAddToPlanning: (MissedSequel) -> Unit,
    onAddToWatching: (MissedSequel) -> Unit,
    /**
     * This title's quick-add state for the action row.
     *
     * Only used for a title that is not a dashboard recommendation; see
     * [DetailActionRow]. Defaulted so the page still draws for a host that has
     * no [com.anisequel.ui.viewmodel.PlanningAddHost].
     */
    quickAddStatus: QuickAddStatus = QuickAddStatus(),
    onAddMediaToPlanning: (MediaNode) -> Unit = {},
    onAddMediaToWatching: (MediaNode) -> Unit = {},
    /**
     * The followed users who have this title on their list, best rating first.
     *
     * Defaulted so the page still draws for a host with no friends request; see
     * `SequelDetailSheet`. Empty means the card is not added at all.
     */
    friends: List<MediaFriend> = emptyList(),
    onClose: () -> Unit,
    /**
     * Opens a related title in place of this one.
     *
     * Replaces rather than stacks: the page is already inside a sheet, and a sheet
     * opened from a sheet has no back control the reader can see. The host swaps its
     * own subject and the same page renders the new title.
     */
    onOpenMedia: (MediaNode) -> Unit = {},
    /**
     * Opens the page for one of the studios credited on this title.
     *
     * Takes the studio's AniList **id** as well as its name: the page it opens is
     * fetched by id, and the name is only there so the destination's bar has a
     * title on its first frame instead of an empty space. A credit AniList gave no
     * id for is drawn as plain text rather than as a button that cannot work.
     */
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
    /**
     * Opens the profile sheet for a character, by AniList id.
     *
     * The cast used to hand its tap to the browser, which was the one thing on this
     * page that did - a studio credit opens a studio page inside the app, so a
     * character credit now opens a character page inside the app.
     *
     * Defaulted to a no-op so a host that has not wired the store still renders the
     * page; the card gates its own `clickable` on this being meaningful, so a host that
     * wires nothing draws captions rather than dead buttons.
     */
    onOpenCharacter: ((Int) -> Unit)? = null,
    /** The same, for the crew row. See [onOpenCharacter]. */
    onOpenStaff: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * Hoisted by the sheet, because the capsule's other destination disposes this
     * page: a remembered state here would send the reader back to the top of the
     * page every time they came back from the entry editor.
     */
    listState: LazyListState = rememberLazyListState(),
    bottomContentPadding: Dp = 24.dp
) {
    val media = subject.media
    val context = LocalContext.current

    val themeSongs by rememberThemeSongs(media)
    val collapse = rememberInfoCollapse(listState)
    val showFloatingBar by rememberShowFloatingBar(collapse)
    // How much of the page is behind the reader, as a 0..1 share, for the docked
    // bar's title pill to fill with. Read only inside that pill's `graphicsLayer`.
    val readProgress = rememberReadProgress(listState)
    val scope = rememberCoroutineScope()

    // Read once per title, before the list: every one of these is a derived walk
    // over the node, and the page's body recomposes several times inside one open -
    // the first frame, the `isDetailLoading` flip, the detail arriving, and the
    // floating bar's visibility change. Rebuilding them on each of those passes is
    // work the sheet was doing while it was still animating.
    val sections = remember(media) { infoSectionsFor(media) }

    var showCover by remember(media.id, media.type) { mutableStateOf(false) }
    if (showCover) {
        FullScreenCoverViewer(media = media, onDismiss = { showCover = false })
    }

    val openOnAniList: () -> Unit = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(media.pageUrl)))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("info_page"),
            contentPadding = PaddingValues(bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "hero") {
                InfoHero(
                    media = media,
                    relationLabel = subject.sequel?.relationLabel,
                    collapse = { collapse.value },
                    onOpenCover = { showCover = true },
                    onClose = onClose,
                    onOpenStudio = onOpenStudio
                )
            }

            infoPageItems(
                subject = subject,
                sections = sections,
                themeSongs = themeSongs,
                isDetailLoading = isDetailLoading,
                canWriteToAniList = canWriteToAniList,
                holdDurationSeconds = holdDurationSeconds,
                swipeEnabled = swipeEnabled,
                context = context,
                quickAddStatus = quickAddStatus,
                onAddMediaToPlanning = onAddMediaToPlanning,
                onAddMediaToWatching = onAddMediaToWatching,
                friends = friends,
                onAddToPlanning = onAddToPlanning,
                onAddToWatching = onAddToWatching,
                onOpenMedia = onOpenMedia,
                onOpenStudio = onOpenStudio,
                onOpenCharacter = onOpenCharacter,
                onOpenStaff = onOpenStaff,
                onOpenCover = { showCover = true }
            )
        }

        InfoFloatingBar(
            visible = showFloatingBar,
            title = media.displayName,
            progress = { readProgress.value },
            // `animateScrollToItem` rather than `scrollToItem`: the whole point of the
            // control is that the reader is deep in the page and does not want to
            // fling their way back, so the return should be one motion they can
            // follow rather than a jump cut.
            onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } },
            onOpenAniList = openOnAniList,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * The opening/ending row's data, the one thing on this page fetched from a second
 * service.
 *
 * The request is per title and cached for the session
 * ([com.anisequel.data.repository.ThemeSongsRepository]); `produceState` is scoped
 * to this page, so walking to a relation cancels the old title's fetch instead of
 * letting it land on the new page.
 *
 * The artwork key is the title's own banner (falling back to its cover): AnimeThemes
 * has no per-sequence thumbnail, so the row is drawn on this title's key visual.
 * It is a *key*, which is deliberate - if the detail fetch lands and supplies art
 * the summary did not have, the row is re-derived rather than left with whatever
 * the first frame could guess, and the second call is a cache hit.
 */
@Composable
private fun rememberThemeSongs(media: MediaNode): State<List<ThemeSong>> {
    val artwork = media.bannerImage?.takeIf { it.isNotBlank() }
        ?: media.coverImage?.bestUrl
    return produceState(
        initialValue = emptyList(),
        media.id,
        artwork
    ) {
        value = AniThemes.repository().themes(media.id, artwork)
    }
}

/**
 * How far the page has scrolled, as the hero's own 0..1 collapse.
 *
 * Measured against the hero's own height rather than against the screen: the point
 * of the value is "the artwork has moved as far as it can usefully move", which is
 * a property of the hero and not of the device.
 *
 * ## Keyed on [listState], and that key is the whole fix
 *
 * `SequelDetailSheet` hands this page a *different* `LazyListState` for every
 * title: it keys the state on `subject.mediaId` so following a relation starts the
 * new title at the top rather than inheriting the old one's offset. So when a
 * relation is opened, this helper is called again against the new list state - and
 * an unkeyed `remember` inside it would keep the *first* `derivedStateOf` it was
 * ever given, whose lambda closes over the **old** list state. That state is
 * detached from the list by then and keeps its last value, so the threshold was
 * being compared against wherever the reader was on the title they just left.
 *
 * The reader is always *past* the hero when they open a relation - the relation row
 * is a dozen sections down - so the collapse read 1f, and the new title opened with
 * its hero at the top **and** the docked bar already on screen over it. The hero and
 * the bar each draw their own "Open on AniList" and "Close" controls, so that is two
 * of each stacked in the same corner of a page that is supposed to have exactly one
 * way out. Keying on [listState] rebuilds the threshold with the scroll state it
 * belongs to.
 */
@Composable
private fun rememberInfoCollapse(listState: LazyListState): State<Float> {
    val distance = with(LocalDensity.current) { HERO_COLLAPSE.toPx() }
    return remember(listState, distance) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / distance).coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * Whether the docked bar has crossed its threshold, as its own `Boolean`.
 *
 * The bar is the one thing on this page derived *in composition* rather than in the
 * draw phase, and it is derived through a `derivedStateOf` producing a `Boolean`:
 * the recomposition happens when the bar crosses [DOCKED_BAR_AT], not on the 300
 * frames before it.
 *
 * Keyed on [collapse] for the same reason [rememberInfoCollapse] is keyed on its
 * list state: a new collapse must produce a threshold over the state it was built
 * from, not keep the one from the title the reader just left.
 */
@Composable
private fun rememberShowFloatingBar(collapse: State<Float>): State<Boolean> =
    remember(collapse) {
        derivedStateOf { collapse.value > DOCKED_BAR_AT }
    }

/**
 * How much of the page is behind the reader, as a 0..1 share, for the docked bar's
 * title pill to fill with. Read only inside that pill's `graphicsLayer`, so this
 * recomputes during a scroll without recomposing anything - the same bargain the
 * collapse makes.
 *
 * Measured in *items scrolled*, plus how far into the first visible one the reader
 * is, rather than in pixels: the page's items differ in height by an order of
 * magnitude (a 340dp hero against a one-line meta row), so a pixel fraction would
 * sit still across the hero and then race through the cards.
 * `visibleItemsInfo.first().size` is the height of the item being scrolled out,
 * which is the divisor that makes the offset mean "how far into this item".
 */
@Composable
private fun rememberReadProgress(listState: LazyListState): State<Float> =
    remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 1) {
                1f
            } else {
                val itemHeight = (info.visibleItemsInfo.firstOrNull()?.size ?: 1).coerceAtLeast(1)
                val scrolled = listState.firstVisibleItemIndex +
                    (listState.firstVisibleItemScrollOffset.toFloat() / itemHeight)
                (scrolled / (total - 1)).coerceIn(0f, 1f)
            }
        }
    }

/**
 * How far the page scrolls before the hero counts as collapsed.
 *
 * Measured against the hero's own height rather than against the screen: the point of
 * the value is "the artwork has moved as far as it can usefully move", which is a
 * property of the hero and not of the device.
 */
private val HERO_COLLAPSE = 200.dp
