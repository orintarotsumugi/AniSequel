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
import com.anisequel.domain.card.columnsFor
import com.anisequel.domain.card.metaCornerFor
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.LocalCardDesign
import com.anisequel.ui.components.cards.MediaPosterCell
import com.anisequel.ui.components.cards.cardRatingStyle
import com.anisequel.ui.components.cards.toAlignment
import com.anisequel.ui.components.expressive.ExpressiveShapes
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardDesignPreview(
    style: MediaCardStyle,
    design: CardDesign,
    entries: List<MediaListEntryItem>,
    modifier: Modifier = Modifier
) {
    // The columns the reader's card size produces at the shipped three-across, which
    // is what makes the sliders and chips show their effect on *this* page rather than
    // only after a trip to the Library.
    val columns = columnsFor(3, design.size)
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
                PreviewTopBar()
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
                                        coverUrl = sample.coverUrl,
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
                PreviewBottomBar()
            }
        }
    }
}

/** The app bar the grid sits under: a title with its count, and the search action. */
@Composable
private fun PreviewTopBar() {
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
        Icon(
            imageVector = AppVectorIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
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

/** The bottom bar, so the frame reads as a page rather than a floating grid. */
@Composable
private fun PreviewBottomBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            AppVectorIcons.HomeTab to false,
            AppVectorIcons.Library to true,
            AppVectorIcons.Search to false,
            AppVectorIcons.ProfileHome to false
        ).forEach { (icon, selected) ->
            Icon(
                imageVector = icon,
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
    val progress: String
)

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
            progress = progressLabels[index % progressLabels.size]
        )
    }
    val placeholders = listOf(
        Triple("Dandadan", 84, "12/24"),
        Triple("Frieren: Beyond Journey's End Season 2", 91, "3/12"),
        Triple("Kaiju No. 8", 76, "8/8"),
        // Long on purpose: the one sample that shows what a name does when it does
        // not fit - scroll, or clip - at the size the reader has picked.
        Triple("That Time I Got Reincarnated as a Slime Season 3 Part 2", 82, "24/24"),
        Triple("Solo Leveling", 79, "6/13"),
        Triple("Delicious in Dungeon", 88, "2/10")
    )
    val fills = placeholders
        .drop(fromLibrary.size)
        .mapIndexed { index, (title, score, progress) ->
            PreviewSample(
                title = title,
                coverUrl = null,
                // Scheme containers rather than a colour picked off a poster: the
                // fallback has to read on a light theme and a dark one.
                coverColor = when (index % 3) {
                    0 -> Color(0xFF3B4A6B)
                    1 -> Color(0xFF5A3B4A)
                    else -> Color(0xFF2F5148)
                },
                score = score,
                progress = progress
            )
        }
    return fromLibrary + fills
}
