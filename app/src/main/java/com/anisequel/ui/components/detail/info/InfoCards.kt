package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.airingCountdown
import com.anisequel.data.model.airingEpisodeNumber
import com.anisequel.data.model.displayName
import com.anisequel.data.model.englishName
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.isManga
import com.anisequel.data.model.nativeName
import com.anisequel.data.model.progressCountLabel
import com.anisequel.data.model.releaseLabel
import com.anisequel.data.model.romajiName
import com.anisequel.data.model.seasonLabel
import com.anisequel.data.model.sourceLabel
import com.anisequel.data.model.statusLabel
import com.anisequel.data.model.synonymList
import com.anisequel.data.model.synopsis
import com.anisequel.data.model.watchFraction
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.AniListMarkdownView
import com.anisequel.ui.theme.AniSequelTheme

/**
 * The container every section of the Info page sits in.
 *
 * One shell rather than a shape per card: a page of a dozen cards is only readable
 * if they share a corner radius, an inset and a surface tone, and the alternative -
 * each section choosing its own - is how the old Specs tab ended up with a trailer
 * banner, two bordered boxes and a bare grid stacked on top of each other.
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/**
 * The viewer's progress through this title, as a wave.
 *
 * Renders nothing at all when there is no progress to show - not on the list, or a
 * total AniList does not know - rather than a 0% bar. An empty bar is a claim that
 * the viewer is at zero, and for a title they are *not* tracking that is a claim
 * about a thing that does not exist.
 */
@Composable
fun InfoProgressCard(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val fraction = media.watchFraction() ?: return
    val count = media.progressCountLabel ?: return
    val isManga = media.isManga
    val nextEpisode = media.airingEpisodeNumber
    val countdown = media.airingCountdown()

    InfoCard(
        modifier = modifier.testTag("info_progress_card"),
        title = if (isManga) "Reading Progress" else "Watching Progress",
        icon = AppVectorIcons.Trending,
        trailing = {
            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    ) {
        WavyProgressBar(progress = fraction)

        if (nextEpisode != null && countdown != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppVectorIcons.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Episode $nextEpisode airs in $countdown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The synopsis, rendered through the app's AniList markdown pipeline.
 *
 * AniList's `description` is markdown-with-HTML-glue: `(Source: Aniplex)` lines,
 * `<br>` runs, `<i>` notes and occasionally a linked image. Stripping it to plain
 * text - which is what this card replaced - throws away the paragraph structure the
 * author wrote and prints the raw `(Source: ...)` line as body copy.
 */
@Composable
fun InfoSynopsisCard(
    media: MediaNode,
    isDetailLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val raw = media.description
    val nodes = remember(raw) {
        raw?.takeIf { it.isNotBlank() }?.let { AniListMarkdownParser.parse(it) } ?: emptyList()
    }
    val plain = media.synopsis
    var isExpanded by remember(media.id) { mutableStateOf(false) }

    InfoCard(
        modifier = modifier.testTag("info_synopsis_card"),
        title = "Synopsis",
        icon = AppVectorIcons.SourceBook
    ) {
        when {
            nodes.isEmpty() && isDetailLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveLoadingIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Loading synopsis from AniList…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            nodes.isEmpty() -> Text(
                text = "AniList has no synopsis for this title.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> {
                AniListMarkdownView(
                    nodes = nodes,
                    maxLines = if (isExpanded) Int.MAX_VALUE else COLLAPSED_SYNOPSIS_LINES
                )

                if ((plain?.length ?: 0) > SYNOPSIS_EXPAND_THRESHOLD) {
                    TextButton(onClick = { isExpanded = !isExpanded }) {
                        Text(if (isExpanded) "Show less" else "Read more")
                    }
                }

                Text(
                    text = "Source: AniList",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/**
 * The production facts: who made it, and the numbers a reader came for.
 *
 * A two-column grid rather than a row of four. AniList's own data does not fit four
 * columns on a 360dp phone - "WINTER 2012" and "187472" both need the full quarter
 * width and neither fits it - and the cramped grid was the reason the previous Specs
 * tab wrapped half its values onto a second line at random.
 */
@Composable
fun InfoSpecsCard(
    media: MediaNode,
    isDetailLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val studioNames = media.studios?.nodes?.mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }
        ?: emptyList()

    val facts = buildList {
        if (media.isManga) {
            add("Chapters" to (media.chapters?.toString() ?: "TBA"))
            media.volumes?.let { add("Volumes" to it.toString()) }
        } else {
            add("Episodes" to (media.episodes?.toString() ?: "TBA"))
        }
        media.duration?.let { add("Duration" to "${it}m") }
        media.seasonLabel?.let { add("Season" to it.uppercase()) }
        add("Status" to media.statusLabel.uppercase())
        add("Format" to media.formatLabel)
        media.sourceLabel?.let { add("Source" to it) }
        media.averageScore?.let { add("Score" to "$it%") }
        media.meanScore?.let { add("Mean score" to "$it%") }
        media.popularity?.let { add("Popularity" to it.toString()) }
        media.releaseLabel.takeIf { it != "TBA" }?.let { add("Started" to it.uppercase()) }
    }

    InfoCard(
        modifier = modifier.testTag("info_specs_card"),
        title = "Details",
        icon = AppVectorIcons.AnimeSparkle
    ) {
        if (studioNames.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Studio",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = studioNames.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else if (isDetailLoading) {
            Text(
                text = "Loading production details…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        facts.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth()) {
                pair.forEach { (label, value) ->
                    InfoFact(label = label, value = value, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A label with its value under it - the unit both detail grids are built from. */
@Composable
private fun InfoFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A label on the left, its value on the right - the alternative-titles rows. */
@Composable
private fun InfoFactRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Genres, and AniList's thematic tags with their weights.
 *
 * [emphasis] is what tells the two apart: a genre is a fact, while a tag is a share
 * of the audience - "Urban Fantasy 96%" - and tinting those with the accent colour
 * is what stops the block reading as one undifferentiated heap of chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoChipCard(
    title: String,
    icon: ImageVector,
    labels: List<String>,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false
) {
    if (labels.isEmpty()) return

    InfoCard(modifier = modifier, title = title, icon = icon) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            labels.forEachIndexed { index, label ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    border = BorderStroke(
                        1.dp,
                        if (emphasis) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        }
                    ),
                    modifier = Modifier.testTag("info_chip_$index")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (emphasis) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/** Every name this title goes by, with AniList's synonyms last. */
@Composable
fun InfoAltTitlesCard(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val synonyms = media.synonymList
    val rows = buildList {
        add("Main title" to media.displayName)
        media.romajiName?.let { add("Romaji" to it) }
        media.englishName?.let { add("English" to it) }
        media.nativeName?.let { add("Native" to it) }
        if (synonyms.isNotEmpty()) add("Synonyms" to synonyms.joinToString(", "))
    }

    InfoCard(
        modifier = modifier.testTag("info_alt_titles_card"),
        title = "Alternative Titles",
        icon = AppVectorIcons.List
    ) {
        rows.forEach { (label, value) ->
            InfoFactRow(label = label, value = value)
        }
    }
}

/**
 * The watched entry a recommendation hangs off.
 *
 * Only a franchise recommendation has a parent - an arriving or activity row is a
 * show the viewer already tracks - and this is the card that explains why the sheet
 * is open at all, so it keeps the parent's own artwork rather than a bare title.
 */
@Composable
fun InfoParentCard(
    sequel: MissedSequel,
    modifier: Modifier = Modifier
) {
    val statusColors = AniSequelTheme.statusColors

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.CheckCircle,
                contentDescription = null,
                tint = statusColors.success,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))

            if (sequel.parentCoverUrl != null) {
                AsyncImage(
                    model = sequel.parentCoverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            sequel.parentCoverColor.toCoverColorOrNull()
                                ?: MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${sequel.relationLabel} to watched entry",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sequel.parentTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** How many lines of synopsis show before "Read more". */
private const val COLLAPSED_SYNOPSIS_LINES = 5

/** Below this, the whole synopsis is already on screen and the toggle is noise. */
private const val SYNOPSIS_EXPAND_THRESHOLD = 260
