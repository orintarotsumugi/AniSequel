package com.anisequel.ui.components.detail.info

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.alternativeTitleRows
import com.anisequel.data.model.characterList
import com.anisequel.data.model.displayName
import com.anisequel.data.model.genreList
import com.anisequel.data.model.hasProgressCard
import com.anisequel.data.model.hasRankings
import com.anisequel.data.model.pageUrl
import com.anisequel.data.model.relatedMedia
import com.anisequel.data.model.staffList
import com.anisequel.data.model.weightLabel
import com.anisequel.data.model.weightedTags
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.AnimeRankingsSection
import com.anisequel.ui.components.detail.DetailActionRow
import com.anisequel.ui.components.detail.FullScreenCoverViewer
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
 * ## The scroll-driven motion
 *
 * [collapse] is one `derivedStateOf` over the list's own scroll state, read as a
 * function by the hero's `graphicsLayer` and by nothing else - the parallax is a
 * draw-phase transform, and the page never recomposes while it is scrolling. The
 * floating bar is the one thing that *is* derived in composition, and it is derived
 * through a `derivedStateOf` producing a `Boolean`: the recomposition happens when
 * the bar crosses its threshold, not on the 300 frames before it.
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
    val sequel = subject.sequel
    val context = LocalContext.current

    val collapseDistance = with(LocalDensity.current) { HERO_COLLAPSE.toPx() }
    val collapse = remember(listState, collapseDistance) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / collapseDistance).coerceIn(0f, 1f)
            }
        }
    }
    // Keyed on [collapse], and that key is the whole fix - it used to be a bare
    // `remember { }`.
    //
    // `SequelDetailSheet` hands this page a *different* `LazyListState` for every
    // title: it keys the state on `subject.mediaId` so following a relation starts
    // the new title at the top rather than inheriting the old one's offset. So when
    // a relation is opened, [collapse] above is rebuilt against the new list state -
    // and an unkeyed `remember` here would keep the *first* `derivedStateOf` it was
    // ever given, whose lambda closes over the **old** list state. That state is
    // detached from the list by then and keeps its last value, so the threshold was
    // being compared against wherever the reader was on the title they just left.
    //
    // The reader is always *past* the hero when they open a relation - the relation
    // row is a dozen sections down - so `collapse` read 1f, and the new title opened
    // with its hero at the top **and** the docked bar already on screen over it. The
    // hero and the bar each draw their own "Open on AniList" and "Close" controls, so
    // that is two of each stacked in the same corner of a page that is supposed to
    // have exactly one way out. Keying on [collapse] rebuilds the threshold with the
    // scroll state it belongs to.
    val showFloatingBar by remember(collapse) {
        derivedStateOf { collapse.value > DOCKED_BAR_AT }
    }

    // How much of the page is behind the reader, as a 0..1 share, for the docked
    // bar's title pill to fill with. Read only inside that pill's `graphicsLayer`, so
    // this recomputes during a scroll without recomposing anything - the same bargain
    // `collapse` above makes.
    //
    // Measured in *items scrolled*, plus how far into the first visible one the
    // reader is, rather than in pixels: the page's items differ in height by an order
    // of magnitude (a 340dp hero against a one-line alternative-title row), so a
    // pixel fraction would sit still across the hero and then race through the cards.
    // `visibleItemsInfo.first().size` is the height of the item being scrolled out,
    // which is the divisor that makes the offset mean "how far into this item".
    val readProgress = remember(listState) {
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
    val scope = rememberCoroutineScope()

    // Read once, before the list, because a section that decides for itself that it
    // has nothing to show cannot also be the thing that decides not to take up a
    // slot. `Arrangement.spacedBy` gives every item its trailing gap whether or not
    // that item drew anything, so a `LazyColumn` item whose content returned early
    // leaves a full 14dp hole in the page - and four of the sections here return
    // early for ordinary titles (no progress on an unstarted show, no staff, no
    // relations, no alternative titles). Hence the explicit `if` on each below.
    val altTitles = media.alternativeTitleRows

    var showCover by remember(media.id) { mutableStateOf(false) }
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
                    relationLabel = sequel?.relationLabel,
                    collapse = { collapse.value },
                    onOpenCover = { showCover = true },
                    onClose = onClose,
                    onOpenStudio = onOpenStudio
                )
            }

            // Only a franchise recommendation has a parent; an arriving or activity
            // row is a show the viewer already tracks.
            if (sequel != null) {
                item(key = "parent") {
                    InfoParentCard(sequel = sequel, modifier = Modifier.infoSection(1))
                }
            }

            item(key = "actions") {
                DetailActionRow(
                    subject = subject,
                    canWriteToAniList = canWriteToAniList,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    context = context,
                    modifier = Modifier.infoSection(2)
                )
            }

            if (media.hasProgressCard) {
                item(key = "progress") {
                    InfoProgressCard(media = media, modifier = Modifier.infoSection(3))
                }
            }

            item(key = "synopsis") {
                InfoSynopsisCard(
                    media = media,
                    isDetailLoading = isDetailLoading,
                    modifier = Modifier.infoSection(4)
                )
            }

            item(key = "specs") {
                InfoSpecsCard(
                    media = media,
                    isDetailLoading = isDetailLoading,
                    onOpenStudio = onOpenStudio,
                    modifier = Modifier.infoSection(5)
                )
            }

            val genres = media.genreList
            if (genres.isNotEmpty()) {
                item(key = "genres") {
                    InfoChipCard(
                        title = "Genres",
                        icon = AppVectorIcons.Tag,
                        labels = genres,
                        modifier = Modifier.infoSection(6)
                    )
                }
            }

            val tags = media.weightedTags.mapNotNull { it.weightLabel() }
            if (tags.isNotEmpty()) {
                item(key = "tags") {
                    InfoChipCard(
                        title = "Tags",
                        icon = AppVectorIcons.Trending,
                        labels = tags,
                        emphasis = true,
                        modifier = Modifier.infoSection(7)
                    )
                }
            }

            if (altTitles.isNotEmpty()) {
                item(key = "alt_titles") {
                    InfoAltTitlesCard(rows = altTitles, modifier = Modifier.infoSection(8))
                }
            }

            // The one section the reference has no equivalent for, and the reason it
            // is kept: an all-time AniList ranking is a fact about this title that
            // nothing else on the page can state.
            if (media.hasRankings) {
                item(key = "rankings") {
                    AnimeRankingsSection(media = media, modifier = Modifier.infoSection(9))
                }
            }

            // `sectionReveal` rather than `infoSection` on the three below, and that
            // is deliberate: they are not cards, so they draw their own heading inset
            // and their own `LazyRow` content padding, and adding the page inset on top
            // would double it. See `InfoTalent.kt`.
            if (media.characterList.isNotEmpty()) {
                item(key = "cast") {
                    InfoCharactersSection(
                        media = media,
                        onOpenCharacter = onOpenCharacter,
                        modifier = Modifier.sectionReveal(10)
                    )
                }
            }

            if (media.relatedMedia.isNotEmpty()) {
                item(key = "relations") {
                    InfoRelationsSection(
                        media = media,
                        onOpen = onOpenMedia,
                        modifier = Modifier.sectionReveal(11)
                    )
                }
            }

            if (media.staffList.isNotEmpty()) {
                item(key = "staff") {
                    InfoStaffSection(
                        media = media,
                        onOpenStaff = onOpenStaff,
                        modifier = Modifier.sectionReveal(12)
                    )
                }
            }

            item(key = "extras") {
                InfoExtrasCard(
                    media = media,
                    onOpenCover = { showCover = true },
                    modifier = Modifier.infoSection(13)
                )
            }
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
 * The page's inset and its place in the entrance stagger, in one call.
 *
 * Every card but the hero and the three artwork rows takes exactly this, and writing
 * it out thirteen times is how they drift apart.
 *
 * [PAGE_INSET] is the one in `InfoCards.kt`, not a second literal: the artwork rows
 * draw their own inset from that same value, and it used to exist twice in two files.
 */
@Composable
private fun Modifier.infoSection(index: Int): Modifier =
    padding(horizontal = PAGE_INSET).sectionReveal(index)

/**
 * How far the page scrolls before the hero counts as collapsed.
 *
 * Measured against the hero's own height rather than against the screen: the point of
 * the value is "the artwork has moved as far as it can usefully move", which is a
 * property of the hero and not of the device.
 */
private val HERO_COLLAPSE = 200.dp
