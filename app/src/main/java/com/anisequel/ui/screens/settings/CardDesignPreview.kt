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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.displayName
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.domain.card.CardDesign
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.card.metaCornerFor
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.LocalCardDesign
import com.anisequel.ui.components.cards.MediaPosterCell
import com.anisequel.ui.components.cards.cardPosterUrl
import com.anisequel.ui.components.cards.cardRatingStyle
import com.anisequel.ui.components.cards.toAlignment
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.navigation.MainTab
import com.anisequel.ui.navigation.enabledMainTabs
import com.anisequel.ui.navigation.mainTabDestinations
import com.anisequel.ui.theme.DynamicThemeBuilder

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
 * bar, the status filters, the grid, the bottom bar - and every one of those is drawn
 * at the real proportions the app uses.
 *
 * ## Why the cards are the real cell
 *
 * [MediaPosterCell] draws them, with the reader's style and the draft design handed
 * down through [LocalCardDesign]. Nothing here re-implements a caption or a badge, so
 * a style that looks wrong in the preview is wrong in the grid for the same reason -
 * and a slider that moves the title here moves the title there, before it is even
 * committed.
 *
 * ## Why the covers are the reader's own
 *
 * Same argument as the style preview: choosing a card means judging it against
 * posters. Entries come from the list the dashboard already downloaded, so the frame
 * costs no request, and the sample titles fill in only what the library cannot (a
 * fresh account, demo mode, or Settings opened before Home has answered). One of the
 * samples is deliberately long, because a name that scrolls is a design decision and
 * an unbroken run of short names would hide the switch that turns it off.
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
     * The bar this preview should draw, exactly as the app draws it.
     *
     * Defaulted to the shipped bar (no Search, no Tiers) rather than left empty: a
     * caller that has no preferences in reach still gets a bar the app could produce,
     * and the default is the one a fresh install has.
     */
    barTabs: List<MainTab> = enabledMainTabs(tierListTab = false)
) {
    // Three across, deliberately fixed. The frame is a diagram of the card, not a
    // claim about the Library's grid: the Library view owns its own column count
    // (Big cards is two, Cover wall three), and a card size that moved this preview
    // while the real grid stayed put would be the preview describing a page the app
    // does not draw. The sliders and switches still show their effect here because
    // they change the card's own type and artwork.
    val columns = 3
    val samples = remember(entries) { previewSamples(entries) }
    val cards = remember(samples, columns) {
        // Exactly two full rows: a half row would read as a layout the grid cannot
        // produce, and the point of the frame is that it can.
        List(columns * 2) { index -> samples[index % samples.size] }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (entries.isNotEmpty()) {
                "Preview \u00b7 your library, $columns across"
            } else {
                "Preview \u00b7 $columns across"
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
                PreviewTopBar(showSearch = MainTab.SEARCH in barTabs)
                Spacer(modifier = Modifier.height(10.dp))
                PreviewFilters()
                Spacer(modifier = Modifier.height(12.dp))

                // The draft design, handed to the cells only. Everything outside the
                // frame - the card, its labels, the chips under it - keeps the app's
                // own look, so the reader can tell the preview from the control.
                CompositionLocalProvider(LocalCardDesign provides design) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        cards.chunked(columns).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { sample ->
                                    MediaPosterCell(
                                        coverUrl = sampleCoverUrl(sample),
                                        coverColor = sample.coverColor,
                                        title = sample.title,
                                        score = sample.score,
                                        style = style,
                                        modifier = Modifier.weight(1f),
                                        overlay = {
                                            // The corner the *meta* occupies, which is
                                            // the corner the style left free: a style
                                            // that puts the rating top-start moves this
                                            // pill. Two capsules stacked in one corner
                                            // is the collision this preview exists to
                                            // catch before the reader meets it in the
                                            // grid.
                                            PreviewProgressPill(
                                                label = sample.progress,
                                                modifier = Modifier.align(
                                                    metaCornerFor(style).toAlignment()
                                                )
                                            )
                                        }
                                    )
                                }
                            }
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
 * The app bar the grid sits under: a title with its count, and the search action when
 * the reader actually has a Search page to open.
 *
 * Without the tab there is no magnifier, because the action opens a page that has no
 * bar entry and - unlike the tier list - no other door at all. The switch that removes
 * it is on the Library page one level up, so a magnifier drawn here anyway was the
 * preview contradicting the reader's answer immediately after they gave it.
 */
@Composable
private fun PreviewTopBar(showSearch: Boolean) {
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
                text = "128 titles \u00b7 12 airing this season",
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewFilters() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        listOf("Watching 42" to true, "Completed 61" to false, "Planning 25" to false)
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

/** One card the preview can draw, already flattened out of a list entry. */
private data class PreviewSample(
    val title: String,
    val coverUrl: String?,
    val coverColor: Color?,
    val score: Int?,
    val progress: String,
    /**
     * The AniList id, for the btttr lookup, or null for a placeholder card.
     *
     * The samples have no id because they are not titles: asking btttr about an
     * invented show would be a request whose only possible answer is "no".
     */
    val mediaId: Int? = null
)

/**
 * The cover the cell should draw for a sample.
 *
 * A placeholder resolves to its own null and never asks; the reader's entries go
 * through the same [cardPosterUrl] the grid uses, so the preview shows the source they
 * chose or falls back to the cover while btttr answers.
 */
@Composable
private fun sampleCoverUrl(sample: PreviewSample): String? {
    val id = sample.mediaId ?: return sample.coverUrl
    // The title is part of the lookup, not decoration: btttr resolves by IMDb
    // series, and without it a sequel sample would draw season one's poster under
    // the sequel's name - the exact mismatch the schedule row already avoids.
    return cardPosterUrl(mediaId = id, fallback = sample.coverUrl, title = sample.title)
}

/**
 * The reader's own covers first, then the samples the page has always used.
 *
 * The long name is in the samples rather than taken from the list on purpose: whether
 * a title scrolls or clips is a property of the design, and leaving it to whichever
 * names happen to be in somebody's list would make the preview show that switch to
 * some readers and not others.
 */
private fun previewSamples(entries: List<MediaListEntryItem>): List<PreviewSample> {
    val progressLabels = listOf("12/24", "3/12", "8/8", "24/24", "6/13", "2/10", "19/25", "1/12")
    val fromLibrary = entries.take(6).mapIndexed { index, entry ->
        PreviewSample(
            title = entry.media.displayName,
            coverUrl = entry.media.posterUrl,
            coverColor = DynamicThemeBuilder.parseHexColor(entry.media.posterColor),
            score = entry.media.averageScore?.takeIf { it > 0 },
            progress = progressLabels[index % progressLabels.size],
            mediaId = entry.media.id
        )
    }
    // Real AniList ids and their covers. The placeholders used to be null covers
    // with a colour rectangle, which meant a reader with an empty library designed
    // cards against three flat swatches and never saw a poster. AniList's cover is
    // the fallback the grid uses too, and btttr can replace it through the same
    // lookup the real cards use.
    val placeholders = listOf(
        PreviewSample(
            title = "Dandadan",
            coverUrl = "https://img.anili.st/media/171018",
            coverColor = Color(0xFF3B4A6B),
            score = 84,
            progress = "12/24",
            mediaId = 171018
        ),
        PreviewSample(
            title = "Frieren: Beyond Journey's End",
            coverUrl = "https://img.anili.st/media/154587",
            coverColor = Color(0xFF5A3B4A),
            score = 91,
            progress = "3/12",
            mediaId = 154587
        ),
        PreviewSample(
            title = "Kaiju No. 8",
            coverUrl = "https://img.anili.st/media/164212",
            coverColor = Color(0xFF2F5148),
            score = 76,
            progress = "8/8",
            mediaId = 164212
        ),
        // Long on purpose: the one sample that shows what a name does when it does
        // not fit - scroll, or clip - at the size the reader has picked.
        PreviewSample(
            title = "That Time I Got Reincarnated as a Slime Season 3 Part 2",
            coverUrl = "https://img.anili.st/media/157295",
            coverColor = Color(0xFF3B4A6B),
            score = 82,
            progress = "24/24",
            mediaId = 157295
        ),
        PreviewSample(
            title = "Solo Leveling",
            coverUrl = "https://img.anili.st/media/151807",
            coverColor = Color(0xFF5A3B4A),
            score = 79,
            progress = "6/13",
            mediaId = 151807
        ),
        PreviewSample(
            title = "Delicious in Dungeon",
            coverUrl = "https://img.anili.st/media/153518",
            coverColor = Color(0xFF2F5148),
            score = 88,
            progress = "2/10",
            mediaId = 153518
        )
    )
    val fills = placeholders.drop(fromLibrary.size)
    return fromLibrary + fills
}
