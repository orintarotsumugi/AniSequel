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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import com.anisequel.data.model.isManga
import com.anisequel.data.model.pageUrl
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.model.trailerYoutubeUrl
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.data.repository.InfoBackdropTint
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.AnimeRankingsSection
import com.anisequel.ui.components.detail.DetailActionRow
import com.anisequel.ui.components.detail.FullScreenCoverViewer
import com.anisequel.ui.components.cards.cardPosterUrl
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.detail.toParentSummary
import com.anisequel.ui.components.openExternalUrlOrCopy
import com.anisequel.ui.theme.CoverColors
import com.anisequel.ui.theme.DynamicThemeBuilder
import com.anisequel.ui.theme.LocalAppDarkTheme
import com.anisequel.ui.theme.LocalImmersiveMode
import com.anisequel.ui.theme.immersiveDisplayInsets
import com.anisequel.ui.theme.rememberAnimatedColorScheme
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
    args: InfoPageArgs,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    bottomContentPadding: Dp = 24.dp,
    onOpenAppearance: (() -> Unit)? = null,
    infoBackdropSource: InfoBackdropSource = InfoBackdropSource.DEFAULT,
    infoBackdropBlur: InfoBackdropBlur = InfoBackdropBlur.DEFAULT,
    infoBackdropTint: InfoBackdropTint = InfoBackdropTint.DEFAULT
) {
    with(args) {
    val media = subject.media
    val context = LocalContext.current
    val immersive = LocalImmersiveMode.current

    // The whole page takes its palette from the artwork it is already showing.
    // Before this, only the hero's shadow and the backdrop wash used the cover;
    // every card and control underneath still used the app's standing accent, so
    // a blue title and a green title had the same page under different art. A
    // nested Material scheme is the smallest honest way to repaint the shared
    // cards, the docked bar and the M3 defaults together - they are not Nova-only
    // composables, so a local set of colours would have to be threaded into each
    // one.
    val darkTheme = LocalAppDarkTheme.current
    val poster = cardPosterUrl(media) ?: media.posterUrl
    val coverSeed = CoverColors.color(poster)
        ?: CoverColors.color(media.posterUrl)
        ?: media.posterColor.toCoverColorOrNull()
    // The artwork's second hue, if the quantizer found one. Passing it makes
    // tertiary - and the fixed roles built from it - the cover's own second
    // colour instead of the synthetic +60 rotation Material uses for a wallpaper
    // there is no better information about.
    val coverAccent = CoverColors.accent(poster) ?: CoverColors.accent(media.posterUrl)
    val standingScheme = MaterialTheme.colorScheme
    val derivedScheme = remember(coverSeed, coverAccent, darkTheme) {
        coverSeed?.let { seed ->
            val (light, dark) = DynamicThemeBuilder.createDynamicSchemes(
                seed = seed,
                tintSurfaces = true,
                accent = coverAccent
            )
            if (darkTheme) dark else light
        }
    } ?: standingScheme
    // The seed is read from the bitmap *after* the sheet is on screen, so the page
    // used to open on the standing scheme and cut to the cover's a frame or two
    // later - a flash on every first open of a title. `rememberAnimatedColorScheme`
    // eases each role between the two on the shared colour tokens, so the corrected
    // palette below lands as the page taking on a tint rather than as a repaint, and
    // it still changes in one frame under Instant and in one straight line under
    // Smooth, because those tokens branch on the reader's Motion style.
    val correctedScheme = remember(derivedScheme, standingScheme) {
        correctPageScheme(cover = derivedScheme, standing = standingScheme)
    }
    val pageScheme = rememberAnimatedColorScheme(correctedScheme)

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

    MaterialTheme(colorScheme = pageScheme) {
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
                    args = args,
                    sections = sections,
                    themeSongs = themeSongs,
                    context = context,
                    onOpenCover = {
                        // Not the poster, so no rectangle to expand from. Clearing the
                        // previous one matters when a relation was opened first.
                        coverOrigin = null
                        showCover = true
                    }
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
    args: InfoPageArgs,
    sections: InfoSections,
    themeSongs: List<ThemeSong>,
    context: android.content.Context,
    onOpenCover: () -> Unit
) {
    with(args) {
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

    // The strip keeps its slot while the detail is in flight. It used to be
    // gated only on the data, so a summary node without a score dropped the card
    // and every section below it sat one card higher - then the detail landed and
    // the whole page jumped down by the height of the strip. A reserved slot with
    // its numbers shimmering is both the honest waiting state and the stable one.
    cardItem(
        key = "nova_stats",
        index = 3,
        entranceKey = revealKey,
        visible = novaHasStats(media) || isDetailLoading
    ) {
        NovaStatsCard(media = media, modifier = it, isLoading = isDetailLoading)
    }

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
        visible = friends.isNotEmpty() || isFriendsLoading
    ) {
        InfoFriendsCard(
            friends = friends,
            media = media,
            modifier = it,
            isLoading = isFriendsLoading
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
            // The stats strip above states the next episode on this page, for
            // every anime with one. Letting the grid draw it too would put the
            // same ticking sentence twice on one scroll.
            showAiring = false,
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
}

/**
 * How much of the cover's neutral ramp is pulled back toward the app's own.
 *
 * Small on purpose. The page must still read as *this title's* page - the whole
 * point of a cover-derived scheme - but the tonal ladder is sampled from a single
 * seed, and a saturated cover (a hot pink poster, a neon green one) pushes every
 * neutral in it toward that hue. The cards on this page are translucent over a
 * blurred backdrop, so a fully tinted `surfaceContainer` ramp is not read against
 * one colour: it is read against the artwork showing through. Fourteen percent is
 * enough to keep the page's neutrals in the same family as the app around it and
 * its body text at the contrast the theme guarantees, without draining the cover
 * out of the page.
 */
private const val NEUTRAL_CORRECTION = 0.14f

/** One neutral, moved part of the way back to the reader's standing neutral. */
private fun Color.correctedToward(standing: Color): Color =
    lerp(this, standing, NEUTRAL_CORRECTION)

/**
 * Colour-corrects the cover scheme before the page wears it.
 *
 * Only the **neutrals** move. `primary`, `secondary` and `tertiary` and their
 * containers stay exactly what the seed produced, because they are the title's
 * identity - the entry capsule and the two summary cards are the page saying
 * "this is the colour of the thing you opened". The surfaces and outlines are
 * infrastructure: the cards, the docked bar, the sheet behind a stat drawer. A
 * surface that has taken the cover's full chroma makes every caption on it work
 * for its contrast against a background that is partly the blurred artwork, and
 * the difference between a legible page and a glowing one is a few percent of
 * saturation rather than a different palette.
 *
 * Applied before [rememberAnimatedColorScheme], so this correction is part of
 * what the colour spring animates toward rather than a second cut on top of it.
 */
private fun correctPageScheme(cover: ColorScheme, standing: ColorScheme): ColorScheme =
    cover.copy(
        background = cover.background.correctedToward(standing.background),
        surface = cover.surface.correctedToward(standing.surface),
        surfaceDim = cover.surfaceDim.correctedToward(standing.surfaceDim),
        surfaceBright = cover.surfaceBright.correctedToward(standing.surfaceBright),
        surfaceVariant = cover.surfaceVariant.correctedToward(standing.surfaceVariant),
        surfaceContainerLowest = cover.surfaceContainerLowest
            .correctedToward(standing.surfaceContainerLowest),
        surfaceContainerLow = cover.surfaceContainerLow
            .correctedToward(standing.surfaceContainerLow),
        surfaceContainer = cover.surfaceContainer
            .correctedToward(standing.surfaceContainer),
        surfaceContainerHigh = cover.surfaceContainerHigh
            .correctedToward(standing.surfaceContainerHigh),
        surfaceContainerHighest = cover.surfaceContainerHighest
            .correctedToward(standing.surfaceContainerHighest),
        outlineVariant = cover.outlineVariant.correctedToward(standing.outlineVariant)
    )
