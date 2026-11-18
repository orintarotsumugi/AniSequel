package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.ThemeSong
import com.anisequel.data.model.displayName
import com.anisequel.data.model.hasProgressCard
import com.anisequel.data.model.pageUrl
import com.anisequel.data.model.trailerYoutubeUrl
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.data.repository.InfoBackdropTint
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.AnimeRankingsSection
import com.anisequel.ui.components.detail.DetailActionRow
import com.anisequel.ui.components.detail.FullScreenCoverViewer
import com.anisequel.ui.components.detail.toParentSummary
import com.anisequel.ui.components.openExternalUrlOrCopy
import com.anisequel.ui.theme.LocalImmersiveMode
import com.anisequel.ui.theme.immersiveDisplayInsets
import com.anisequel.ui.viewmodel.QuickAddStatus
import kotlinx.coroutines.launch

/**
 * The redesigned title page: the same facts as [AnimeInfoPage], read in the order
 * the reference clients use.
 *
 * ## Why this is a second page rather than a restyle of the first
 *
 * The classic page is a scroll of cards under a hero that collapses away. That is
 * a good shape for reading a synopsis and a cast list, and it is the shape every
 * existing install already knows. The redesign is not that shape with rounder
 * corners: the artwork is the page's ground, the identity is a poster lockup, and
 * the first thing under it is the four numbers a reader compares between titles.
 * Trying to express both from one composable would mean a branch on every section -
 * which is the kind of file this project's rules split rather than grow.
 *
 * So the two pages share the *sections*, not the shell. Synopses, details,
 * categories, the cast, the relations, the extras and the backdrop are the same
 * composables on both; only the hero, the section order and the three summary
 * surfaces in `NovaInfoCards.kt` are new. A fix to a section therefore reaches
 * both pages, and a difference between them is a decision someone wrote down
 * rather than a copy that drifted.
 *
 * ## The section order
 *
 * Reference order, with this app's constraints:
 *
 * 1. hero - art, poster, title, status capsule
 * 2. the planning row, only for a title that is not on a list yet
 * 3. the four stats and the airing countdown
 * 4. the wavy progress card
 * 5. friends watching it
 * 6. synopsis
 * 7. details
 * 8. categories (genres and weighted tags)
 * 9. trailer
 * 10. where to watch, external links, themes
 * 11. names/producers, rankings
 * 12. cast, relations, staff
 * 13. extras
 *
 * The classic page puts the action row first and the synopsis before the numbers.
 * That is a reading page; this one is a decision page - "what is this, how is it
 * rated, where am I in it" comes before the prose, which is what the screenshots
 * the redesign was drawn from do.
 *
 * ## What it does not do
 *
 * It does not add an Episodes or Comments destination. The reference clients have
 * both because they play video or own a comment store; AniSequel has neither, and
 * a capsule destination that leads nowhere is the dead control this codebase's
 * rules keep out. The existing Info/My Entry capsule stays exactly as it is.
 */
@Composable
fun NovaInfoPage(
    subject: AnimeDetailSubject,
    isDetailLoading: Boolean,
    canWriteToAniList: Boolean,
    holdDurationSeconds: Int,
    swipeEnabled: Boolean,
    onAddToPlanning: (MissedSequel) -> Unit,
    onAddToWatching: (MissedSequel) -> Unit,
    quickAddStatus: QuickAddStatus = QuickAddStatus(),
    onAddMediaToPlanning: (MediaNode) -> Unit = {},
    onAddMediaToWatching: (MediaNode) -> Unit = {},
    friends: List<MediaFriend> = emptyList(),
    onClose: () -> Unit,
    onOpenMedia: (MediaNode) -> Unit = {},
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
    onOpenCharacter: ((Int) -> Unit)? = null,
    onOpenStaff: ((Int) -> Unit)? = null,
    onEditEntry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    bottomContentPadding: Dp = 24.dp,
    onOpenAppearance: (() -> Unit)? = null,
    infoBackdropSource: InfoBackdropSource = InfoBackdropSource.DEFAULT,
    infoBackdropBlur: InfoBackdropBlur = InfoBackdropBlur.DEFAULT,
    infoBackdropTint: InfoBackdropTint = InfoBackdropTint.DEFAULT
) {
    val media = subject.media
    val context = LocalContext.current
    val immersive = LocalImmersiveMode.current

    val themeSongs by rememberThemeSongs(media)
    // The same scroll-derived values as the classic page, from the same helpers:
    // the backdrop hand-off and the docked bar's threshold are properties of the
    // *page*, not of its hero, and two copies of either would drift the first time
    // one of them moved.
    val collapse = rememberInfoCollapse(listState)
    val heroBottom = rememberHeroBottom(listState)
    val showFloatingBar by rememberShowFloatingBar(collapse)
    val readProgress = rememberReadProgress(listState)
    val scope = rememberCoroutineScope()
    val sections = remember(media) { infoSectionsFor(media) }

    val openOnAniList: () -> Unit = remember(context, media.pageUrl) {
        { openExternalUrlOrCopy(context, media.pageUrl) }
    }

    var showCover by remember(media.id, media.type) { mutableStateOf(false) }
    // The poster's window bounds at the moment it was tapped. The viewer grows out
    // of that rectangle; null keeps the plain fade for the Extras card, which has no
    // poster to grow from.
    var coverOrigin by remember(media.id, media.type) { mutableStateOf<Rect?>(null) }
    if (showCover) {
        FullScreenCoverViewer(
            media = media,
            origin = coverOrigin,
            onDismiss = { showCover = false }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        InfoPageBackdrop(
            media = media,
            source = infoBackdropSource,
            blur = infoBackdropBlur,
            tint = infoBackdropTint,
            collapse = { collapse.value },
            heroBottom = { heroBottom.value }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("nova_info_page"),
            contentPadding = PaddingValues(bottom = bottomContentPadding),
            // Tighter than the classic page's 16dp: the stats strip and the
            // progress card are one block of summary, and a full card gap between
            // them would read them as two unrelated sections.
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "nova_hero", contentType = "nova_hero") {
                NovaInfoHero(
                    media = media,
                    relationLabel = subject.sequel?.relationLabel,
                    collapse = { collapse.value },
                    onOpenCover = { origin ->
                        coverOrigin = origin
                        showCover = true
                    },
                    onClose = onClose,
                    onOpenAppearance = onOpenAppearance,
                    onEditEntry = onEditEntry,
                    canWriteToAniList = canWriteToAniList,
                    onOpenStudio = onOpenStudio,
                    backdropSource = infoBackdropSource
                )
            }

            novaPageItems(
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
                onOpenCover = {
                    // Not the poster, so no rectangle to expand from. Clearing the
                    // previous one matters when a relation was opened first.
                    coverOrigin = null
                    showCover = true
                },
                onEditEntry = onEditEntry
            )
        }

        InfoFloatingBar(
            visible = showFloatingBar,
            title = media.displayName,
            progress = { readProgress.value },
            onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } },
            onOpenAniList = openOnAniList,
            onOpenAppearance = onOpenAppearance,
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .immersiveDisplayInsets(immersive)
        )
    }
}

/**
 * The redesigned page's section list.
 *
 * The gating is the same contract [infoPageItems] documents: an item that would
 * draw nothing is not added, because `Arrangement.spacedBy` charges every item its
 * gap whether or not its content produced a pixel. The list uses the same
 * [cardItem] / [rowItem] helpers as the classic page so the inset and the entrance
 * stagger cannot be written twice, and the keys are prefixed `nova_` so a title
 * walked from one page style to the other never reuses the other page's saved
 * state under the same key.
 */
private fun LazyListScope.novaPageItems(
    subject: AnimeDetailSubject,
    sections: InfoSections,
    themeSongs: List<ThemeSong>,
    isDetailLoading: Boolean,
    canWriteToAniList: Boolean,
    holdDurationSeconds: Int,
    swipeEnabled: Boolean,
    context: android.content.Context,
    quickAddStatus: QuickAddStatus,
    friends: List<MediaFriend>,
    onAddMediaToPlanning: (MediaNode) -> Unit,
    onAddMediaToWatching: (MediaNode) -> Unit,
    onAddToPlanning: (MissedSequel) -> Unit,
    onAddToWatching: (MissedSequel) -> Unit,
    onOpenMedia: (MediaNode) -> Unit,
    onOpenStudio: (Int, String?) -> Unit,
    onOpenCharacter: ((Int) -> Unit)?,
    onOpenStaff: ((Int) -> Unit)?,
    onOpenCover: () -> Unit,
    onEditEntry: (() -> Unit)?
) {
    val media = subject.media
    val revealKey = subject.mediaId to media.type
    val tracked = media.mediaListEntry?.status != null

    subject.sequel?.let { sequel ->
        cardItem(key = "nova_parent", index = 1, entranceKey = revealKey) {
            InfoParentCard(
                sequel = sequel,
                onOpenParent = { onOpenMedia(sequel.toParentSummary()) },
                modifier = it
            )
        }
    }

    // Only for a title that is not on a list: a tracked one already has the status
    // capsule in the hero, and the planning row would be a second control for the
    // same question. The row also carries the quick-add error, so an untracked
    // title keeps the one place that can report a failed write.
    if (!tracked) {
        cardItem(key = "nova_actions", index = 2, entranceKey = revealKey) {
            InfoCard(modifier = it) {
                DetailActionRow(
                    subject = subject,
                    canWriteToAniList = canWriteToAniList,
                    quickAddStatus = quickAddStatus,
                    onAddMediaToPlanning = onAddMediaToPlanning,
                    onAddMediaToWatching = onAddMediaToWatching,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    isDetailLoading = isDetailLoading,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    onEditEntry = onEditEntry,
                    context = context
                )
            }
        }
    }

    cardItem(
        key = "nova_stats",
        index = 3,
        entranceKey = revealKey,
        visible = novaHasStats(media)
    ) { NovaStatsCard(media = media, modifier = it) }

    cardItem(
        key = "nova_progress",
        index = 4,
        entranceKey = revealKey,
        visible = media.hasProgressCard
    ) { NovaProgressCard(media = media, modifier = it) }

    // Friends before the prose: "has anyone I follow seen this" is the same
    // question the progress card answers about the reader, and it is worth more
    // than a synopsis they can get anywhere.
    cardItem(
        key = "nova_friends",
        index = 5,
        entranceKey = revealKey,
        visible = friends.isNotEmpty() || isDetailLoading
    ) {
        InfoFriendsCard(
            friends = friends,
            media = media,
            modifier = it,
            isLoading = isDetailLoading
        )
    }

    cardItem(key = "nova_synopsis", index = 6, entranceKey = revealKey) {
        InfoSynopsisCard(media = media, isDetailLoading = isDetailLoading, modifier = it)
    }

    cardItem(key = "nova_specs", index = 7, entranceKey = revealKey) {
        InfoSpecsCard(
            media = media,
            isDetailLoading = isDetailLoading,
            onOpenStudio = onOpenStudio,
            modifier = it
        )
    }

    cardItem(
        key = "nova_categories",
        index = 8,
        entranceKey = revealKey,
        visible = sections.hasCategories
    ) { InfoCategoriesSection(media = media, modifier = it) }

    // The trailer is its own card rather than a row in Extras: it is artwork, and
    // the one picture on the page AniList hands the app a thumbnail for.
    cardItem(
        key = "nova_trailer",
        index = 9,
        entranceKey = revealKey,
        visible = media.trailerYoutubeUrl != null
    ) { NovaTrailerCard(media = media, modifier = it) }

    cardItem(
        key = "nova_streaming",
        index = 10,
        entranceKey = revealKey,
        visible = sections.streamingLinks.isNotEmpty()
    ) {
        InfoWhereToWatchCard(
            links = sections.streamingLinks,
            modifier = it,
            isManga = media.isManga
        )
    }

    cardItem(
        key = "nova_external_links",
        index = 11,
        entranceKey = revealKey,
        visible = sections.hasExternalLinks
    ) { InfoExternalLinksSection(media = media, modifier = it) }

    rowItem(
        key = "nova_theme_songs",
        index = 12,
        entranceKey = revealKey,
        visible = themeSongs.isNotEmpty()
    ) { InfoThemeSongsSection(themes = themeSongs, modifier = it) }

    cardItem(
        key = "nova_meta_rows",
        index = 13,
        entranceKey = revealKey,
        visible = sections.hasMetaRows
    ) { InfoMetaRowsSection(media = media, modifier = it) }

    cardItem(
        key = "nova_rankings",
        index = 14,
        entranceKey = revealKey,
        visible = sections.hasRankings
    ) { AnimeRankingsSection(media = media, modifier = it) }

    rowItem(
        key = "nova_cast",
        index = 15,
        entranceKey = revealKey,
        visible = sections.hasCast
    ) { InfoCharactersSection(media = media, onOpenCharacter = onOpenCharacter, modifier = it) }

    rowItem(
        key = "nova_relations",
        index = 16,
        entranceKey = revealKey,
        visible = sections.hasRelations
    ) { InfoRelationsSection(media = media, onOpen = onOpenMedia, modifier = it) }

    rowItem(
        key = "nova_staff",
        index = 17,
        entranceKey = revealKey,
        visible = sections.hasStaff
    ) { InfoStaffSection(media = media, onOpenStaff = onOpenStaff, modifier = it) }

    cardItem(key = "nova_extras", index = 18, entranceKey = revealKey) {
        InfoExtrasCard(media = media, onOpenCover = onOpenCover, modifier = it)
    }
}
