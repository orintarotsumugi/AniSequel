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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.displayName
import com.anisequel.data.model.genreList
import com.anisequel.data.model.pageUrl
import com.anisequel.data.model.rankingList
import com.anisequel.data.model.weightLabel
import com.anisequel.data.model.weightedTags
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.AnimeRankingsSection
import com.anisequel.ui.components.detail.DetailActionRow
import com.anisequel.ui.components.detail.FullScreenCoverViewer

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
    val showFloatingBar by remember {
        derivedStateOf { collapse.value > DOCKED_BAR_AT }
    }

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
                    onClose = onClose
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

            item(key = "progress") {
                InfoProgressCard(media = media, modifier = Modifier.infoSection(3))
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

            item(key = "alt_titles") {
                InfoAltTitlesCard(media = media, modifier = Modifier.infoSection(8))
            }

            // The one section the reference has no equivalent for, and the reason it
            // is kept: an all-time AniList ranking is a fact about this title that
            // nothing else on the page can state.
            if (media.rankingList.isNotEmpty()) {
                item(key = "rankings") {
                    Box(modifier = Modifier.infoSection(9)) {
                        AnimeRankingsSection(media = media)
                    }
                }
            }

            item(key = "cast") {
                InfoCharactersSection(media = media, modifier = Modifier.sectionReveal(10))
            }

            item(key = "relations") {
                InfoRelationsSection(media = media, modifier = Modifier.sectionReveal(11))
            }

            item(key = "staff") {
                InfoStaffSection(media = media, modifier = Modifier.sectionReveal(12))
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
 */
@Composable
private fun Modifier.infoSection(index: Int): Modifier =
    padding(horizontal = PAGE_INSET).sectionReveal(index)

/** The page's left and right inset. The hero and the artwork rows deliberately ignore it. */
private val PAGE_INSET = 20.dp

/**
 * How far the page scrolls before the hero counts as collapsed.
 *
 * Measured against the hero's own height rather than against the screen: the point of
 * the value is "the artwork has moved as far as it can usefully move", which is a
 * property of the hero and not of the device.
 */
private val HERO_COLLAPSE = 200.dp
