package com.anisequel.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.namespaceKey
import com.anisequel.domain.card.CardDesign
import com.anisequel.domain.list.ListStatuses
import com.anisequel.ui.components.MarqueeText
import com.anisequel.ui.components.cards.LocalCardDesign
import com.anisequel.ui.components.cards.PosterPlaceholder
import com.anisequel.ui.components.cards.ListOwner
import com.anisequel.ui.components.cards.cardCoverAspect
import com.anisequel.ui.components.cards.cardPosterUrl
import com.anisequel.ui.components.cards.cardRatingStyle
import com.anisequel.ui.components.cards.cardTitleMotion
import com.anisequel.ui.components.cards.cardTitleStyle
import com.anisequel.ui.components.cards.libraryRating
import com.anisequel.ui.components.detail.detailCoverPressOrigin
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.ui.components.nova.NovaProgressPill
import com.anisequel.ui.components.nova.NovaSurface
import com.anisequel.ui.navigation.mainBarClearance
import com.anisequel.ui.screens.profile.PendingDataNote
import com.anisequel.ui.screens.profile.libraryProgressLabel

/**
 * The surfaces the new Library scrolls: a list, a grid, and the two rows they
 * draw. Split from [NovaLibraryScreen] so that file is the page's state and
 * layout rather than 300 lines of item drawing.
 */
@Composable
internal fun NovaLibraryList(
    visible: List<MediaListEntryItem>,
    isManga: Boolean,
    awaitingAnime: Boolean,
    scopedError: String?,
    onOpenDetail: (MediaNode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = mainBarClearance() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        notesFor(awaitingAnime = awaitingAnime, scopedError = scopedError)
            .forEach { note ->
                item(key = "note_${note.text}") {
                    PendingDataNote(note.text, busy = note.busy)
                }
            }
        items(
            items = visible,
            key = { it.media.namespaceKey },
            contentType = { "library_row" }
        ) { entry ->
            NovaLibraryRow(
                entry = entry,
                isManga = isManga,
                onClick = { onOpenDetail(entry.media) },
                modifier = Modifier.animateItem(
                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                )
            )
        }
    }
}

/**
 * The Table mode: the same rows as List with the score and format columns added.
 *
 * A table is a triage surface, so it keeps the cover small, the title on one or
 * two lines and every number in a column that lines up down the page. The header
 * is a single row rather than a sticky bar: the list already scrolls under a fixed
 * library header, and a second pinned row on a phone costs more than it explains.
 */
@Composable
internal fun NovaLibraryTable(
    visible: List<MediaListEntryItem>,
    awaitingAnime: Boolean,
    scopedError: String?,
    onOpenDetail: (MediaNode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = mainBarClearance() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        notesFor(awaitingAnime = awaitingAnime, scopedError = scopedError)
            .forEach { note ->
                item(key = "note_${note.text}") {
                    PendingDataNote(note.text, busy = note.busy)
                }
            }
        item(key = "table_header") {
            LibraryTableHeader()
        }
        items(
            items = visible,
            key = { it.media.namespaceKey },
            contentType = { "library_table_row" }
        ) { entry ->
            LibraryListRow(
                entry = entry,
                detailed = true,
                // The table is the reader's own list: this is the only caller of the
                // shared row that is not a profile, and the column has to say whose
                // rating it is carrying.
                owner = ListOwner.MINE,
                onClick = { onOpenDetail(entry.media) },
                modifier = Modifier.animateItem(
                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                )
            )
        }
    }
}

/**
 * The same entries, grouped under the six status headings.
 *
 * The headings come from [ListStatuses.options] rather than from the entries:
 * walking the list and inventing a heading for each token a payload happens to
 * carry would reorder the page between refreshes, and the API's order is the one
 * people already know. A token this build cannot classify - an old v1 name, a
 * status AniList added - gets one trailing "Other" section instead of vanishing.
 */
@Composable
internal fun NovaLibraryGrouped(
    visible: List<MediaListEntryItem>,
    isManga: Boolean,
    awaitingAnime: Boolean,
    scopedError: String?,
    onOpenDetail: (MediaNode) -> Unit
) {
    val groups = remember(visible, isManga) {
        ListStatuses.options.mapNotNull { option ->
            val entries = visible.filter { entry ->
                ListStatuses.normalize(entry.status) == option.apiValue
            }
            if (entries.isEmpty()) null else option to entries
        }
    }
    val unclassified = remember(visible) {
        visible.filter { entry -> ListStatuses.normalize(entry.status) == null }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("nova_library_grouped"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = mainBarClearance() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        notesFor(awaitingAnime = awaitingAnime, scopedError = scopedError)
            .forEach { note ->
                item(key = "note_${note.text}") {
                    PendingDataNote(note.text, busy = note.busy)
                }
            }

        groups.forEach { (option, entries) ->
            item(key = "group_${option.apiValue}") {
                LibraryGroupHeading(
                    label = option.labelFor(isManga),
                    count = entries.size
                )
            }
            items(
                items = entries,
                key = { it.media.namespaceKey },
                contentType = { "library_grouped_row" }
            ) { entry ->
                NovaLibraryRow(
                    entry = entry,
                    isManga = isManga,
                    onClick = { onOpenDetail(entry.media) },
                    modifier = Modifier.animateItem(
                        placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                        fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                        fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                    )
                )
            }
        }

        if (unclassified.isNotEmpty()) {
            item(key = "group_unclassified") {
                LibraryGroupHeading(label = "Other", count = unclassified.size)
            }
            items(
                items = unclassified,
                key = { it.media.namespaceKey },
                contentType = { "library_grouped_row" }
            ) { entry ->
                NovaLibraryRow(
                    entry = entry,
                    isManga = isManga,
                    onClick = { onOpenDetail(entry.media) },
                    modifier = Modifier.animateItem(
                        placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                        fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                        fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                    )
                )
            }
        }
    }
}

@Composable
internal fun NovaLibraryGrid(
    visible: List<MediaListEntryItem>,
    isManga: Boolean,
    viewMode: LibraryViewMode,
    awaitingAnime: Boolean,
    scopedError: String?,
    gridState: LazyGridState,
    onOpenDetail: (MediaNode) -> Unit
) {
    // The shelf and compact modes name five and four covers across because that
    // is how many fit a tablet or a wide phone. On a 360dp phone the same five
    // are 57dp each - a strip of thumbnails, not a cover grid - so the count is
    // reduced until every cover can still show a face. The Settings preview keeps
    // the named count: it is a diagram of the mode, not a measurement of the
    // device it happens to be drawn on.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The mode's own count, never the card size: "Big cards" stays two across
        // and "Cover wall" three however the cards' covers are shaped. The card
        // design used to bias this by a column, which is how choosing Compact under
        // Big cards produced a three-up grid neither setting had asked for.
        val columns = libraryColumns(mode = viewMode, width = maxWidth)
        // Only the Grouped covers arm reads these, but they are computed here rather
        // than in the body so the heading and the posters under it come from one walk
        // of the list, in the API's own status order.
        val groupedByStatus = remember(visible, isManga) {
            ListStatuses.options.mapNotNull { option ->
                val entries = visible.filter { entry ->
                    ListStatuses.normalize(entry.status) == option.apiValue
                }
                if (entries.isEmpty()) null else option to entries
            }
        }
        val unclassified = remember(visible) {
            visible.filter { entry -> ListStatuses.normalize(entry.status) == null }
        }
        // Which cells are the wide ones in Mosaic. A key set rather than an index
        // lookup: the span lambda and the item content both need the answer, and
        // `indexOf` in the content walked the whole list once per visible cell.
        val heroKeys = remember(visible, viewMode) {
            if (viewMode != LibraryViewMode.MOSAIC) {
                emptySet<String>()
            } else {
                visible.filterIndexed { index, _ -> index % MOSAIC_HERO_EVERY == 0 }
                    .map { it.media.namespaceKey }
                    .toSet()
            }
        }
        // A *new* lazy layout per media type, rather than one layout handed a
        // different list.
        //
        // That is the whole fix for the covers that overlapped after switching
        // between the anime and manga halves. The lazy layout keeps per-key
        // animation and measurement state - the fade a removed key is playing, and
        // the offsets placement springs are travelling between - and a type switch
        // removes every key the old state was about. Asking one layout to animate
        // one list out while measuring another in produced exactly what two quick
        // switches used to show: the old posters drawn over the new cells, rows
        // sitting at offsets that belonged to the other list, and ghosts that
        // outlived the switch. A fresh layout has none of that state to inherit,
        // which is why
        // leaving the page and coming back - the one thing that did work - looked
        // correct: it also started from nothing.
        //
        // `isManga` and not the whole filter: a filter, a sort or a refresh keeps
        // most keys, and their animating is the point of `animateItem` below.
        key(isManga) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("nova_library_grid"),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = mainBarClearance() + 20.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                notesFor(awaitingAnime = awaitingAnime, scopedError = scopedError)
                    .forEach { note ->
                        item(
                            key = "note_${note.text}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            PendingDataNote(note.text, busy = note.busy)
                        }
                    }
                if (viewMode == LibraryViewMode.GROUPED_COVERS) {
                    groupedByStatus.forEach { (option, entries) ->
                        item(
                            key = "group_${option.apiValue}",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = { "library_group_heading" }
                        ) {
                            LibraryGroupHeading(
                                label = option.labelFor(isManga),
                                count = entries.size
                            )
                        }
                        gridItems(
                            items = entries,
                            key = { it.media.namespaceKey },
                            contentType = { "library_grouped_poster_$columns" }
                        ) { entry ->
                            LibraryGridPoster(
                                entry = entry,
                                isManga = isManga,
                                viewMode = viewMode,
                                onClick = { onOpenDetail(entry.media) },
                                modifier = Modifier.animateItem(
                                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                                )
                            )
                        }
                    }
                    if (unclassified.isNotEmpty()) {
                        item(
                            key = "group_unclassified",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = { "library_group_heading" }
                        ) {
                            LibraryGroupHeading(label = "Other", count = unclassified.size)
                        }
                        gridItems(
                            items = unclassified,
                            key = { it.media.namespaceKey },
                            contentType = { "library_grouped_poster_$columns" }
                        ) { entry ->
                            LibraryGridPoster(
                                entry = entry,
                                isManga = isManga,
                                viewMode = viewMode,
                                onClick = { onOpenDetail(entry.media) },
                                modifier = Modifier.animateItem(
                                    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                                    fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                                    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                                )
                            )
                        }
                    }
                } else {
                    gridItems(
                        items = visible,
                        key = { it.media.namespaceKey },
                        // Mosaic is a span mode, not a second cell: every third cover
                        // takes the whole row. Without this the mode drew the same flat
                        // two-up grid as Big cards and the choice meant nothing.
                        span = { entry ->
                            if (entry.media.namespaceKey in heroKeys) {
                                GridItemSpan(maxLineSpan)
                            } else {
                                GridItemSpan(1)
                            }
                        },
                        // Part of the type, not a constant: switching from a 5-column
                        // shelf to big cards reuses the same slot otherwise, and the
                        // recycler keeps a caption policy that belongs to the old mode
                        // for the first frame.
                        contentType = { "library_poster_${viewMode.storageValue}_$columns" }
                    ) { entry ->
                        LibraryGridPoster(
                            entry = entry,
                            isManga = isManga,
                            viewMode = viewMode,
                            hero = entry.media.namespaceKey in heroKeys,
                            onClick = { onOpenDetail(entry.media) },
                            modifier = Modifier.animateItem(
                                placementSpec = ExpressiveMotion.DefaultSpatialOffset,
                                fadeInSpec = ExpressiveMotion.ListItemFadeIn,
                                fadeOutSpec = ExpressiveMotion.ListItemFadeOut
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * The waiting and failure notes a populated list still carries above its rows.
 *
 * Returned as strings because the grid and the list place them differently - one
 * full-span grid item, one list item - and the wording is the part that must not
 * diverge.
 */
private data class LibraryNote(val text: String, val busy: Boolean)

private fun notesFor(awaitingAnime: Boolean, scopedError: String?): List<LibraryNote> =
    buildList {
        if (awaitingAnime) {
            add(
                LibraryNote(
                    text = "Still fetching this list - these counts are not final yet",
                    busy = true
                )
            )
        }
        if (scopedError != null && !awaitingAnime) {
            // `busy = false`, and it is the whole reason this list is typed: the note
            // used to be a bare string drawn with PendingDataNote's default spinner,
            // so "Couldn't load this list" arrived with a loader turning beside it -
            // a promise of an answer that had already arrived and *was* the failure.
            // ProfileStatsTab has passed `busy = false` for exactly this since it
            // shipped; the Library's three surfaces were still spinning.
            add(LibraryNote("Couldn't load this list - $scopedError", busy = false))
        }
    }

/** How often a Mosaic row breaks into a full-width cover. */
internal const val MOSAIC_HERO_EVERY = 3

/** Width over height of a Mosaic hero cell: wide enough to read as a banner. */
internal const val MOSAIC_HERO_ASPECT = 1.55f

/**
 * Width over height of a Spotlight cover.
 *
 * The mode's whole claim is "one title, seen properly", so the cover is the shape a
 * still is rather than the 2:3 shape of a poster. It is part of the mode's
 * arrangement, like [MOSAIC_HERO_ASPECT], and both the grid and the skeleton read
 * this constant so the page and the wait before it agree.
 */
internal const val SPOTLIGHT_ASPECT = 1.55f

/**
 * One poster cell, with the caption and shape the view mode asks for.
 *
 * Extracted because the grouped-covers arm and the flat grid draw the same cell, and
 * "which modes print a caption" was already duplicated once (here and in
 * `CardDesignPreview`) before a third copy arrived with the new modes.
 *
 * [hero] is Mosaic's wide cell. It uses a landscape ratio instead of the card
 * design's own: the wide tile's *job* is to break the wall, and a full-width 2:3
 * poster is not a break, it is a poster at double size running off the bottom of
 * the screen. The mode owns its arrangement - it already owns the columns and the
 * caption policy - so the hero's proportion is part of the layout rather than a
 * second card design.
 */
@Composable
private fun LibraryGridPoster(
    entry: MediaListEntryItem,
    isManga: Boolean,
    viewMode: LibraryViewMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hero: Boolean = false
) {
    NovaPoster(
        entry = entry,
        isManga = isManga,
        compact = viewMode == LibraryViewMode.COMPACT ||
            viewMode == LibraryViewMode.SHELF,
        // The mode answers this, not the surface: the two Settings previews read
        // the same property, so a preview cannot print a caption the grid drops.
        showTitle = viewMode.showsCaption,
        large = viewMode == LibraryViewMode.CARDS || hero || viewMode.usesWideStill,
        hero = hero,
        wide = viewMode.usesWideStill,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * Which picture the page's cover flight may start from, if any.
 *
 * A mosaic hero and a spotlight still are one landscape crop of the artwork, while
 * the page opens on the portrait cover - a flight from either would start on a
 * picture the reader never sees at that size, so those two keep the page's own
 * fade. Named rather than inlined because [NovaPoster] is already the densest
 * function on this screen, and a branch in it is a branch to hold while reading
 * geometry.
 */
private fun tileCoverOrigin(hero: Boolean, wide: Boolean, posterUrl: String?): String? =
    if (hero || wide) null else posterUrl

/**
 * One cover in the new grid.
 *
 * The progress capsule is on the cover, top-left, exactly as the reference draws
 * it. The title is under the artwork, not over it: a second caption on a cover
 * that already carries its own typography is the failure `ProfileLibraryTab`
 * documents. Semibold, two lines - a bold label that clips at the first line
 * makes two seasons look like the same title.
 */
@Composable
private fun NovaPoster(
    entry: MediaListEntryItem,
    isManga: Boolean,
    compact: Boolean,
    showTitle: Boolean,
    large: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Mosaic's full-width cell: a landscape crop rather than a poster. */
    hero: Boolean = false,
    /** Spotlight's full-width cell: one landscape still per row. */
    wide: Boolean = false
) {
    val media = entry.media
    val interactionSource = remember { MutableInteractionSource() }
    val design = LocalCardDesign.current
    // The grid drew no rating at all: the list and table modes had a score column and
    // the poster modes had a name and a progress pill, so "what did I give this" was
    // only answerable after opening the entry. Same helper and same owner as the rows,
    // so a title scored in one mode is scored in all of them.
    val rating = libraryRating(entry, ListOwner.MINE)
    // `takeIf { it > 0 }`: a zero count is AniList's "not announced", and the
    // progress pill would otherwise caption the cover "3 | 0".
    val total = (if (isManga) media.chapters else media.episodes)?.takeIf { it > 0 }
    val completed = entry.status.equals("COMPLETED", ignoreCase = true)
    val progress = entry.progress ?: if (completed) total else null
    // Parsed once per entry rather than on every recomposition. A grid recomposes
    // its visible items whenever the list, the density or a scoped note changes,
    // and this is hex parsing and a `Color` allocation for every card each time.
    val coverTint = remember(media.coverImage?.color) {
        media.coverImage?.color.toCoverColorOrNull()
    }
    // Resolved once: the page's flight asks for the rectangle under this URL, and
    // a second `cardPosterUrl(media)` call here would start a second lookup for
    // the same title.
    val posterUrl = cardPosterUrl(media)

    Column(
        modifier = modifier
            .bouncyPress(pressedScale = 0.95f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("nova_library_poster")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(
                    when {
                        hero -> MOSAIC_HERO_ASPECT
                        wide -> SPOTLIGHT_ASPECT
                        else -> cardCoverAspect(if (large) 0.74f else 0.68f, design)
                    }
                )
                .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
                .detailCoverPressOrigin(tileCoverOrigin(hero, wide, posterUrl))
        ) {
            // The grid is what a cold start opens on, and `Color.Transparent` meant a
            // poster with no dominant colour in its data had nothing at all behind it
            // until its cover decoded. The title's initials give every cell content on
            // the first frame; see [PosterPlaceholder].
            PosterPlaceholder(
                title = media.displayName,
                tint = coverTint,
                modifier = Modifier.matchParentSize()
            )
            AsyncImage(
                model = posterUrl,
                contentDescription = media.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (progress != null) {
                NovaProgressPill(
                    progress = progress,
                    total = total,
                    isManga = isManga,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                )
            }
        }
        val captionVisible = showTitle && (design.showTitles || design.showScores)
        if (captionVisible) {
            Spacer(modifier = Modifier.height(5.dp))
            NovaPosterCaption(
                entry = entry,
                isManga = isManga,
                large = large,
                rating = rating,
                design = design
            )
        }
    }
}

/**
 * The lines a poster prints under its cover: the name, the score, and the status.
 *
 * Split out of [NovaPoster] rather than kept inside it, because this is where every
 * decision on a card lives - scroll or clip, two lines or three, whether there is a
 * score to print at all - and next to the poster's own layout it pushed that function
 * past what a reader can scan. The poster is now geometry; the caption is the text.
 *
 * The two drawings of one caption are chosen by whether anything may move. With
 * motion, a name that does not fit scrolls on one line: the reader can read the whole
 * of it, and at three across the part an ellipsis was cutting - the season number, the
 * subtitle - is the part that tells two entries apart. Without motion it stays the two
 * or three ellipsized lines it always was, because a still row of text is what a reader
 * who chose Instant asked to see.
 */
@Composable
private fun NovaPosterCaption(
    entry: MediaListEntryItem,
    isManga: Boolean,
    large: Boolean,
    rating: Int?,
    design: CardDesign
) {
    val media = entry.media
    val titleStyle = cardTitleStyle(design)
    val titleMotion = cardTitleMotion(design)
    val shownRating = rating?.takeIf { design.showScores }
    if (design.showTitles) {
        if (titleMotion.animates) {
            MarqueeText(
                text = media.displayName,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                motion = titleMotion,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = media.displayName,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (large) 3 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    if (shownRating != null) {
        if (design.showTitles) Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = "★ $shownRating",
            style = cardRatingStyle(design),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
    if (large) {
        Text(
            text = listOfNotNull(
                ListStatuses.label(entry.status, isManga),
                libraryProgressLabel(entry)
            ).joinToString(" · "),
            // The same step the card style's rating line uses, so a reader who
            // enlarged the small print on their cards enlarged this too.
            style = cardRatingStyle(design),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The same entry as a row, for the List density.
 *
 * This is the mode that makes a long library readable rather than browsable:
 * progress, status and score are all in one line, and the cover is a thumbnail
 * rather than the page.
 */
@Composable
private fun NovaLibraryRow(
    entry: MediaListEntryItem,
    isManga: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = entry.media
    val interactionSource = remember { MutableInteractionSource() }
    // The reader's own rating when AniList returned one and the switch is on, the
    // community average otherwise - the same rule the borrowed list uses, through the
    // same helper, so the two pages cannot print different numbers for one entry.
    val score = libraryRating(entry, ListOwner.MINE)
    val progressLabel = libraryProgressLabel(entry)
    val coverTint = remember(media.coverImage?.color) {
        media.coverImage?.color.toCoverColorOrNull()
    }
    val posterUrl = cardPosterUrl(media)

    NovaSurface(
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("nova_library_row"),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .detailCoverPressOrigin(posterUrl)
            ) {
                PosterPlaceholder(
                    title = media.displayName,
                    tint = coverTint,
                    modifier = Modifier.matchParentSize()
                )
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = ExpressiveShapes.pill,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = ListStatuses.label(entry.status, isManga),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                    if (progressLabel != null) {
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            val design = LocalCardDesign.current
            if (score != null && design.showScores) {
                Text(
                    text = "★ $score",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
