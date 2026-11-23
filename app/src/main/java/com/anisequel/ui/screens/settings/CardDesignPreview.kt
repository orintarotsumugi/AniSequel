package com.anisequel.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.displayName
import com.anisequel.data.model.posterColor
import com.anisequel.domain.card.CardDesign
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.card.metaCornerFor
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.ListOwner
import com.anisequel.ui.components.cards.LocalCardDesign
import com.anisequel.ui.components.cards.MediaPosterCell
import com.anisequel.ui.components.cards.POSTER_ASPECT
import com.anisequel.ui.components.cards.cardPosterUrl
import com.anisequel.ui.components.cards.cardRatingStyle
import com.anisequel.ui.components.cards.libraryRating
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.cards.toAlignment
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.navigation.MainTab
import com.anisequel.ui.navigation.enabledMainTabs
import com.anisequel.ui.navigation.mainTabDestinations
import com.anisequel.ui.screens.library.LibraryGroupHeading
import com.anisequel.ui.screens.library.LibraryListRow
import com.anisequel.ui.screens.library.MOSAIC_HERO_ASPECT
import com.anisequel.ui.screens.library.LibraryTableHeader
import com.anisequel.ui.screens.profile.libraryProgressLabel

/**
 * A whole Library page, drawn small, from the cards the reader is designing.
 *
 * ## Why this is a page and not three covers in a row
 *
 * The card-style preview used to be three cards on a blank card background, and the
 * question it could not answer was the one the reader actually had: *does this look
 * like my library?* A cover alone says nothing about whether four across is cramped,
 * whether a two-line name collides with the next row, or where the rating sits
 * against the header above it. So the preview is the page those cards live on - a top
 * bar, the status filters, the layout, the bottom bar - and every one of those is
 * drawn at the real proportions the app uses.
 *
 * ## Why the shape follows the Library, not a fixed three across
 *
 * This preview used to be three across whatever the reader's Library was set to, so
 * "Big cards" and "Cover wall" both previewed as a three-column poster grid - the
 * preview described a page the app does not draw. [viewMode] is the mode the Library
 * page is actually in: the grid takes that mode's own column count, a cover wall
 * draws no captions, and List, Grouped and Table draw the Library's real rows rather
 * than a poster grid they never produce.
 *
 * ## Why the covers are only the reader's own
 *
 * The frame used to top itself up with six invented titles and AniList cover URLs
 * whenever the reader's list was short, and its header printed a made-up "128 titles
 * · 12 airing". Both have gone. Choosing a card means judging it against posters the
 * reader actually owns, and a preview that lies about their library is worse than one
 * that says it has nothing to show: an empty list now says so. There is no request
 * here either - the entries are the ones the dashboard already downloaded, and a
 * title btttr has art for resolves through the same [cardPosterUrl] the grid uses.
 *
 * ## Why the frame's bars are the reader's own
 *
 * The mock-up used to draw a fixed bar - Home, Library, Search, Profile - and a
 * magnifier in the app bar, whether or not the reader had those pages on their bar at
 * all. A preview whose bar is not the bar is a preview of a different app, and the
 * reader who had just turned the Search tab off met it again here, one page away from
 * the switch they turned it off with. [barTabs] is the list `enabledMainTabs` produced
 * for the real bar, and the glyphs come from `mainTabDestinations`, so the two cannot
 * describe different bars.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardDesignPreview(
    style: MediaCardStyle,
    design: CardDesign,
    entries: List<MediaListEntryItem>,
    modifier: Modifier = Modifier,
    /**
     * The Library page's current shape.
     *
     * The card design does not change this - the Library owns it - but the preview
     * must show the page those cards will actually land on. A reader on Big cards
     * who opens Card design expects to see two across, not a generic three-column
     * grid.
     */
    viewMode: LibraryViewMode = LibraryViewMode.DEFAULT,
    /**
     * The bar this preview should draw, exactly as the app draws it.
     *
     * Defaulted to the shipped bar (no Search, no Tiers) rather than left empty: a
     * caller that has no preferences in reach still gets a bar the app could produce,
     * and the default is the one a fresh install has.
     */
    barTabs: List<MainTab> = enabledMainTabs(tierListTab = false)
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (entries.isEmpty()) {
                "Preview \u00b7 no covers on your list yet"
            } else {
                "Preview \u00b7 your library \u00b7 ${viewMode.displayName}"
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("card_design_preview")
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                PreviewTopBar(
                    showSearch = MainTab.SEARCH in barTabs,
                    hasCovers = entries.isNotEmpty()
                )
                Spacer(modifier = Modifier.height(10.dp))
                PreviewFilters()
                Spacer(modifier = Modifier.height(12.dp))

                // The draft design, handed to the cells only. Everything outside the
                // frame - the card, its labels, the chips under it - keeps the app's
                // own look, so the reader can tell the preview from the control.
                CompositionLocalProvider(LocalCardDesign provides design) {
                    if (entries.isEmpty()) {
                        PreviewEmptyState()
                    } else {
                        // Every arm is a fact on the mode, not a view name spelled
                        // again here: Mosaic and Grouped covers used to fall into
                        // the plain-grid arm, so two of the ten views previewed as
                        // their neighbour and the reader could not see the shape
                        // they were choosing. See `LibraryViewMode`.
                        when {
                            viewMode.drawsRows -> PreviewListShape(
                                mode = viewMode,
                                entries = entries
                            )

                            viewMode.hasHeroCells -> PreviewMosaicShape(
                                mode = viewMode,
                                style = style,
                                entries = entries
                            )

                            viewMode.hasStatusHeadings -> PreviewGroupedCoversShape(
                                mode = viewMode,
                                style = style,
                                entries = entries
                            )

                            else -> PreviewGridShape(
                                mode = viewMode,
                                style = style,
                                entries = entries
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                PreviewBottomBar(tabs = barTabs)
            }
        }
    }
}

/**
 * The grid every card-shaped mode produces: the mode's own column count, two full
 * rows, and the reader's real covers repeated when their list is shorter.
 *
 * Two rows rather than one: a one-row preview cannot show how a caption collides
 * with the next row, which is the thing the card design most often gets wrong. The
 * caption itself is the mode's own answer - Cover wall and Shelf draw none, exactly
 * as the Library draws none in those modes.
 */
@Composable
private fun PreviewGridShape(
    mode: LibraryViewMode,
    style: MediaCardStyle,
    entries: List<MediaListEntryItem>
) {
    val columns = mode.columns.coerceAtLeast(1)
    // Exactly two full rows: a half row would read as a layout the grid cannot
    // produce, and the point of the frame is that it can.
    val cells = List(columns * 2) { index -> entries[index % entries.size] }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(columns).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { entry ->
                    PreviewPosterCell(
                        entry = entry,
                        style = style,
                        // The mode decides whether a caption is printed, exactly as
                        // it does in the Library. This said "everything but Cover
                        // wall", so Shelf - which never prints a name - drew one
                        // here, under the covers it was supposed to show without.
                        showTitle = mode.showsCaption,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Mosaic's rhythm: a full-width anchor over the pair it breaks.
 *
 * The Library draws every third cover as a landscape banner, and this preview used
 * to draw a flat two-up grid under the Mosaic name - the one difference between it
 * and Big cards, invisible in the page that exists to show it.
 */
@Composable
private fun PreviewMosaicShape(
    mode: LibraryViewMode,
    style: MediaCardStyle,
    entries: List<MediaListEntryItem>
) {
    val columns = mode.columns.coerceAtLeast(1)
    // `NovaPoster` draws the hero at `MOSAIC_HERO_ASPECT` regardless of the cover
    // proportion the reader picked, while `MediaPosterCell` multiplies whatever it
    // is given by that bias - so the bias is divided out and the banner keeps the
    // shape the Library actually gives it.
    val heroAspect = MOSAIC_HERO_ASPECT / LocalCardDesign.current.size.coverBias
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PreviewPosterCell(
            entry = entries.first(),
            style = style,
            showTitle = mode.showsCaption,
            aspect = heroAspect,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(columns) { index ->
                PreviewPosterCell(
                    entry = entries[(index + 1) % entries.size],
                    style = style,
                    showTitle = mode.showsCaption,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Grouped covers: the status heading, then the poster grid it sorts.
 *
 * "Your titles" rather than a status, for the reason the row preview gives: the
 * entries handed to this frame are the reader's most recently touched, not one
 * status group, and a heading that named one would be the invented data this page
 * has already removed once.
 */
@Composable
private fun PreviewGroupedCoversShape(
    mode: LibraryViewMode,
    style: MediaCardStyle,
    entries: List<MediaListEntryItem>
) {
    val columns = mode.columns.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LibraryGroupHeading(label = "Your titles", count = entries.size)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(columns) { index ->
                PreviewPosterCell(
                    entry = entries[index % entries.size],
                    style = style,
                    showTitle = mode.showsCaption,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * One poster, drawn by the cell the Library draws.
 *
 * Extracted when the Mosaic and Grouped-covers arms arrived: three copies of the
 * same cell would have let the progress pill sit in a different corner depending on
 * which mode the reader was previewing.
 *
 * The pill takes the corner the *meta* occupies, which is the corner the style left
 * free: a style that puts the rating top-start moves this pill. Two capsules stacked
 * in one corner is the collision this preview exists to catch before the reader
 * meets it in the grid.
 */
@Composable
private fun PreviewPosterCell(
    entry: MediaListEntryItem,
    style: MediaCardStyle,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
    aspect: Float = POSTER_ASPECT
) {
    val media = entry.media
    MediaPosterCell(
        coverUrl = cardPosterUrl(media),
        coverColor = media.posterColor.toCoverColorOrNull(),
        title = media.displayName,
        score = libraryRating(entry, ListOwner.MINE),
        style = style,
        aspect = aspect,
        titleVisible = showTitle,
        modifier = modifier,
        overlay = {
            libraryProgressLabel(entry)?.let { progress ->
                PreviewProgressPill(
                    label = progress,
                    modifier = Modifier.align(metaCornerFor(style).toAlignment())
                )
            }
        }
    )
}

/**
 * The three list-shaped modes, drawn with the Library's own rows.
 *
 * A poster grid here would be a drawing of a page the mode never produces. Grouped
 * adds the status heading it groups by, Table adds the aligned column header, and
 * List is the plain rows between them - the same components the real Library calls.
 */
@Composable
private fun PreviewListShape(
    mode: LibraryViewMode,
    entries: List<MediaListEntryItem>
) {
    val shown = entries.take(3)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (mode == LibraryViewMode.GROUPED) {
            // "Your titles" rather than a status: the three entries handed to this
            // preview are the reader's most recently touched, not a status group,
            // and a heading that named one would be the same invented data the
            // counts used to be.
            LibraryGroupHeading(label = "Your titles", count = shown.size)
        }
        if (mode == LibraryViewMode.TABLE) {
            LibraryTableHeader()
        }
        shown.forEach { entry ->
            LibraryListRow(
                entry = entry,
                detailed = mode == LibraryViewMode.TABLE,
                onClick = {}
            )
        }
        if (shown.size < entries.size) {
            Text(
                text = "Showing the first ${shown.size} of ${entries.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The app bar the grid sits under: a title with the count of covers this preview was
 * given, and the search action when the reader actually has a Search page to open.
 *
 * No invented library totals: this screen is handed a handful of the reader's own
 * entries, so a line about "128 titles" was a number nothing here could know. The
 * count is the one the frame can honestly report - how many of their covers it is
 * showing.
 *
 * Without the Search tab there is no magnifier, because the action opens a page that
 * has no bar entry and - unlike the tier list - no other door at all. The switch that
 * removes it is on the Library page one level up, so a magnifier drawn here anyway
 * was the preview contradicting the reader's answer immediately after they gave it.
 */
@Composable
private fun PreviewTopBar(showSearch: Boolean, hasCovers: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppVectorIcons.ViewGrid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "My library",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (hasCovers) {
                    "Your covers, drawn with this card design"
                } else {
                    "No titles on your list yet"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showSearch) {
            Icon(
                imageVector = AppVectorIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * The status filters, because their height is part of what makes a four-across grid
 * feel cramped or a two-across one feel empty.
 *
 * The counts are gone: they used to read "Watching 42" against a library the preview
 * had never seen. The labels are the page's shape, and the shape is what this frame
 * is for.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewFilters() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        listOf("Watching" to true, "Completed" to false, "Planning" to false)
            .forEach { (label, selected) ->
                Surface(
                    shape = ExpressiveShapes.pill,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
    }
}

/**
 * What an empty library sees: an honest empty state in the frame, not six invented
 * covers.
 *
 * The reader can still design a card here - the preview simply says it has nothing
 * of theirs to draw it on. Filling the frame with somebody else's artwork is how a
 * preview stops being a preview of *their* library.
 */
@Composable
private fun PreviewEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp)
            .testTag("card_design_preview_empty"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = AppVectorIcons.ViewGrid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your covers will preview here",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Add a few titles to your list and open this page again.",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The bottom bar, so the frame reads as a page rather than a floating grid.
 *
 * The glyphs are the bar's own: `mainTabDestinations` maps the same [MainTab] list the
 * real bar is built from, and the highlighted entry is the Library because the frame is
 * a Library page. A tab the reader turned off has no destination and so draws nothing.
 */
@Composable
private fun PreviewBottomBar(tabs: List<MainTab>) {
    val destinations = mainTabDestinations(tabs, profileAvatarUrl = null)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        destinations.forEachIndexed { index, destination ->
            val selected = tabs[index] == MainTab.LIBRARY
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * The progress capsule the Library draws on a cover, at the card's own meta size.
 *
 * Scaled with the rating rather than with the title: it is a number, and a reader who
 * enlarged the small print on their cards meant this one too. The capsule itself
 * grows with the text because it is padded, which is the point of drawing it here
 * instead of measuring a fixed height.
 */
@Composable
private fun PreviewProgressPill(
    label: String,
    modifier: Modifier = Modifier
) {
    val design = LocalCardDesign.current
    Row(
        modifier = modifier
            .padding(5.dp)
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppVectorIcons.Play,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(9.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = cardRatingStyle(design),
            color = Color.White,
            maxLines = 1
        )
    }
}
