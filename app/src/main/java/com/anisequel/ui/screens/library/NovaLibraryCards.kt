package com.anisequel.ui.screens.library

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.anisequel.ui.components.cards.ListOwner
import com.anisequel.ui.components.cards.cardCoverAspect
import com.anisequel.ui.components.cards.cardPosterUrl
import com.anisequel.ui.components.cards.cardRatingStyle
import com.anisequel.ui.components.cards.cardTitleMotion
import com.anisequel.ui.components.cards.cardTitleStyle
import com.anisequel.ui.components.cards.libraryRating
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
                item(key = "note_$note") {
                    PendingDataNote(note)
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
                item(key = "note_$note") {
                    PendingDataNote(note)
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
                item(key = "note_$note") {
                    PendingDataNote(note)
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
                    onClick = { onOpenDetail(entry.media) }
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
        // The reader's card size, applied to the mode's own count here rather than
        // stored per mode: a bigger card is bigger in every grid, and a table of six
        // modes would have to be edited to say so.
        val columns = libraryColumns(
            mode = viewMode,
            width = maxWidth,
            size = LocalCardDesign.current.size
        )
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
                        key = "note_$note",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        PendingDataNote(note)
                    }
                }
            gridItems(
                items = visible,
                key = { it.media.namespaceKey },
                // Part of the type, not a constant: switching from a 5-column
                // shelf to big cards reuses the same slot otherwise, and the
                // recycler keeps a caption policy that belongs to the old mode
                // for the first frame.
                contentType = { "library_poster_${viewMode.storageValue}_$columns" }
            ) { entry ->
                NovaPoster(
                    entry = entry,
                    isManga = isManga,
                    compact = viewMode == LibraryViewMode.COMPACT ||
                        viewMode == LibraryViewMode.SHELF,
                    showTitle = viewMode != LibraryViewMode.SHELF &&
                        viewMode != LibraryViewMode.COVERS,
                    large = viewMode == LibraryViewMode.CARDS,
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

/**
 * The waiting and failure notes a populated list still carries above its rows.
 *
 * Returned as strings because the grid and the list place them differently - one
 * full-span grid item, one list item - and the wording is the part that must not
 * diverge.
 */
private fun notesFor(awaitingAnime: Boolean, scopedError: String?): List<String> =
    buildList {
        if (awaitingAnime) {
            add("Still fetching this list - these counts are not final yet")
        }
        if (scopedError != null && !awaitingAnime) {
            add("Couldn't load this list - $scopedError")
        }
    }

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
    modifier: Modifier = Modifier
) {
    val media = entry.media
    val interactionSource = remember { MutableInteractionSource() }
    val design = LocalCardDesign.current
    // The grid drew no rating at all: the list and table modes had a score column and
    // the poster modes had a name and a progress pill, so "what did I give this" was
    // only answerable after opening the entry. Same helper and same owner as the rows,
    // so a title scored in one mode is scored in all of them.
    val rating = libraryRating(entry, ListOwner.MINE)
    val total = if (isManga) media.chapters else media.episodes
    val completed = entry.status.equals("COMPLETED", ignoreCase = true)
    val progress = entry.progress ?: if (completed) total else null
    // Parsed once per entry rather than on every recomposition. A grid recomposes
    // its visible items whenever the list, the density or a scoped note changes,
    // and this is hex parsing and a `Color` allocation for every card each time.
    val coverTint = remember(media.coverImage?.color) {
        media.coverImage?.color.toCoverColorOrNull()
    }

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
                // A ratio rather than a fixed height: the grid is three columns
                // or four, so the same card is measured at two widths - and a
                // height literal would turn the wider one into a portrait and the
                // narrower into a strip. The ratio is then stretched by the reader's
                // card size, which is what makes a two-across card a bigger picture
                // instead of the same cover with more space beside it.
                .aspectRatio(cardCoverAspect(if (large) 0.74f else 0.68f, design))
                .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
                .background(coverTint ?: Color.Transparent)
        ) {
            AsyncImage(
                model = cardPosterUrl(media),
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
        if (showTitle) {
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
    // Asked once per caption: `cardTitleMotion` reads a composition local and the
    // branch below decides between a scrolling line and an ellipsis, so the answer
    // must not be asked twice with two different results.
    val titleMotion = cardTitleMotion(design)
    if (titleMotion.animates) {
        MarqueeText(
            text = media.displayName,
            style = titleStyle,
            color = MaterialTheme.colorScheme.onSurface,
            motion = titleMotion,
            // The card's width, not the text's: a marquee needs a boundary to
            // overflow against, and an unconstrained `Text` measures at the
            // length of the name and never scrolls.
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
    if (rating != null) {
        Spacer(modifier = Modifier.height(1.dp))
        // The same star the List and Table modes print in their score column, so the
        // number is recognisable as the reader's own verdict rather than a second
        // kind of badge.
        Text(
            text = "★ $rating",
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
                    .background(coverTint ?: Color.Transparent)
            ) {
                AsyncImage(
                    model = cardPosterUrl(media),
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
            if (score != null) {
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
