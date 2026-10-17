package com.anisequel.ui.components.detail.info

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.StudioCredit
import com.anisequel.data.model.airingCountdown
import com.anisequel.data.model.airingEpisodeNumber
import com.anisequel.data.model.descriptionBody
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.isManga
import com.anisequel.data.model.progressCountLabel
import com.anisequel.data.model.releaseLabel
import com.anisequel.data.model.seasonLabel
import com.anisequel.data.model.sourceLabel
import com.anisequel.data.model.statusLabel
import com.anisequel.data.model.studioCredits
import com.anisequel.data.model.synopsis
import com.anisequel.data.model.watchFraction
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.AniListMarkdownView
import com.anisequel.ui.components.markdown.MARKDOWN_COLLAPSED_BLOCK_LIMIT
import com.anisequel.ui.theme.AniSequelTheme
import java.util.Locale

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
 *
 * The page asks [MediaNode.hasProgressCard] before adding this as a list item,
 * because an item that draws nothing still costs a gap - see the note on
 * [com.anisequel.data.model.hasRankings].
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
    // `descriptionBody`, not `description`: the markdown renderer reads a bare
    // `(Source: Wikipedia)` line as a paragraph and prints it as part of the story.
    // Stripping it here is what stops the credit line being the last thing on the
    // card - see `MediaNode.descriptionBody`.
    val raw = media.descriptionBody
    val nodes = remember(raw) {
        raw?.takeIf { it.isNotBlank() }?.let { AniListMarkdownParser.parse(it) } ?: emptyList()
    }
    val plain = media.synopsis

    // `rememberSaveable`, not `remember`, and that is the whole point: this card is a
    // `LazyColumn` item, so scrolling past it disposes it. A plain `remember` meant
    // that expanding a long synopsis, reading on and coming back up reopened it
    // collapsed - the card silently undid the one control it has. The `LazyColumn`
    // keeps a disposed item's saved state under its key, so the choice survives the
    // round trip the same way the entrance animation's "already played" flag does.
    var isExpanded by rememberSaveable(media.id) { mutableStateOf(false) }

    InfoCard(
        modifier = modifier
            // The synopsis is the one card on this page whose *height* changes in
            // front of the reader, and it used to change in one frame: the text
            // jumped, the page jumped with it, and the button the reader just pressed
            // moved out from under their finger. Springing the size instead keeps the
            // control under the finger and reads as the card opening. A size-animating
            // modifier takes an `IntSize` spec, so this is the `FastSpatialSize` token
            // - the same physics `AnimatedVisibility`'s expand/shrink uses.
            .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
            .testTag("info_synopsis_card"),
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

                // The toggle is gated on the same thing the view clamps, and the view
                // clamps two different ways: `AniListMarkdownView` drops whole blocks
                // past MARKDOWN_COLLAPSED_BLOCK_LIMIT, *and* clamps each remaining
                // paragraph to `maxLines`. Gating on character count alone - which is
                // all this used to check - missed the block case: a description of six
                // short paragraphs totalling 200 characters was cut to four blocks and
                // offered no way to read the other two, because 200 is under the
                // threshold. Text the reader cannot reach is worse than a card that
                // grew a line.
                val isClamped = nodes.size > MARKDOWN_COLLAPSED_BLOCK_LIMIT ||
                    (plain?.length ?: 0) > SYNOPSIS_EXPAND_THRESHOLD

                if (isClamped) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.testTag("info_synopsis_toggle")
                    ) {
                        Text(
                            text = if (isExpanded) "Show less" else "Read more",
                            // Drawn in the accent, and at the same weight as the
                            // card's own title rather than as a button label. This
                            // is the one control on the card, so it reads as chrome
                            // at rest: a full `TextButton` slot of tonal padding in
                            // the middle of a paragraph looked like a button the
                            // synopsis needed.
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "Source: AniList",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.testTag("info_synopsis_source")
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
 *
 * The `FlowRow` for the studio credits needs the same opt-in as the chip cards
 * above, and it is annotated here rather than file-wide so that a function which
 * stops using an experimental layout API stops carrying the promise that it does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoSpecsCard(
    media: MediaNode,
    isDetailLoading: Boolean,
    /**
     * Opens the page for one of the credited studios.
     *
     * Every credit is drawn, not just the animation studio: AniList answers
     * `studios` with the publishers and licensors alongside the studio that made
     * the thing, and the page used to ask for the main ones only and print them as
     * a comma-joined string - which hid the rest of the credits *and* made the ones
     * it did show un-openable.
     */
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val studios = media.studioCredits

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
        // The studio is the one fact the list query never carries, so it is the one
        // that can be known-absent rather than not-yet-arrived - and showing
        // "Loading production details…" *underneath* a grid of real facts read as
        // though the whole card was still filling in.
        if (studios.isEmpty() && isDetailLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveLoadingIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Loading production details…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (studios.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Studio",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("info_studio_row")
                ) {
                    studios.forEach { credit ->
                        StudioCreditChip(credit = credit, onOpenStudio = onOpenStudio)
                    }
                }
            }
        }

        // `Arrangement.spacedBy` on the grid, not a `Row` per pair: each pair was its
        // own row with no space above it, so a two-column grid of facts rendered as
        // four columns of text with the pairs visually indistinguishable from each
        // other - and a single label ("Episodes") sitting above a single value
        // ("24") looked like a broken label/value pair rather than one of four.
        //
        // The pairs are also `Arrangement.Top`, so a fact whose value wraps to two
        // lines does not drag its neighbour's value up to meet it.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            facts.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    pair.forEach { (label, value) ->
                        InfoFact(label = label, value = value, modifier = Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * A label with its value under it - the unit both detail grids are built from.
 *
 * The label is **uppercased and tracked out**. This is the one place on the page
 * where a reader is scanning for a specific number across a two-column grid, and
 * every label was set in the same weight and size as the values above and below
 * them - so a grid of "Episodes / 24 / Duration / 24m / Season / Winter 2012"
 * read as one undifferentiated block. A label that is visibly a *label* is what
 * makes a number findable: the eye goes to the small tracked-out word, and the
 * answer is the line under it.
 *
 * `onSurfaceVariant` at a reduced alpha rather than at full strength: the values
 * are the content and the labels are the scaffolding, and at equal weight the
 * scaffolding was competing.
 */
@Composable
private fun InfoFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            // Letter spacing is what makes a run of small capitals legible as a
            // unit; at this size with the default spacing they run together.
            letterSpacing = 0.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A label on the left, its value on the right - the alternative-titles rows.
 *
 * Same treatment as [InfoFact] and for the same reason: these rows are read by
 * looking for one name, and the label is what says which column it is in.
 */
@Composable
private fun InfoFactRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            letterSpacing = 0.6.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
 * A row of chips - genres, or AniList's thematic tags with their weights.
 *
 * [emphasis] is what tells the two apart: a genre is a fact, while a tag is a share
 * of the audience - "Urban Fantasy 96%" - and tinting those with the accent colour
 * is what stops the block reading as one undifferentiated heap of chips.
 *
 * [labels] empty renders nothing: the page asks for the list before it adds this as
 * a list item, because an item that draws nothing still costs a gap.
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

    // Keyed by title as well as index: both this card's callers pass the same tag for
    // index zero, so "the first genre chip" and "the first tag chip" were the same
    // testTag on the same screen, and `onNodeWithTag` could only ever reach one of
    // them - whichever happened to be laid out last.
    val tagPrefix = "info_chip_${title.lowercase(Locale.ROOT)}"

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
                    modifier = Modifier.testTag("${tagPrefix}_$index")
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

/**
 * Every name this title goes by that the hero is not already showing, synonyms last.
 *
 * The rows come from [com.anisequel.data.model.alternativeTitleRows], which drops
 * any name identical to the one the hero displays in full. This card used to open
 * with a "Main title" row and then repeat that exact string under its own name -
 * see the note on that derivation for why that was most titles rather than some.
 *
 * A title whose only name is the one the hero shows now renders nothing at all,
 * which is why the page asks for the rows before it adds this as a list item.
 */
@Composable
fun InfoAltTitlesCard(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
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

/**
 * One credited studio, as a capsule that opens its page.
 *
 * Two weights rather than two rows: the studios AniList flags `isMain` are the ones
 * that made the title and are drawn filled, and the publishers, licensors and
 * producers in the same connection are drawn quieter. Splitting them into sections
 * would need names for the halves - AniList does not say which is which - and the
 * page would be inventing a taxonomy to avoid a colour.
 *
 * A credit with no id is drawn as a plain capsule rather than removed: the name is
 * still part of the answer to "who made this", it just cannot be followed.
 */
@Composable
private fun StudioCreditChip(
    credit: StudioCredit,
    onOpenStudio: (Int, String?) -> Unit
) {
    val openable = credit.id != null
    val container = if (credit.isMain) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (credit.isMain) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = ExpressiveShapes.pill,
        color = container,
        modifier = Modifier
            .then(if (openable) Modifier.bouncyPress(pressedScale = 0.94f) else Modifier)
            .clickable(enabled = openable) {
                credit.id?.let { id -> onOpenStudio(id, credit.name) }
            }
            .testTag("info_studio_${credit.name}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.Studio,
                contentDescription = null,
                tint = content.copy(alpha = 0.8f),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = credit.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (credit.isMain) FontWeight.Bold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** How many lines of synopsis show before "Read more". */
private const val COLLAPSED_SYNOPSIS_LINES = 5

/** Below this, the whole synopsis is already on screen and the toggle is noise. */
private const val SYNOPSIS_EXPAND_THRESHOLD = 260

/**
 * The page's left and right inset, shared by the cards and by the three sections
 * that bleed to the edge.
 *
 * It lives here, beside the card shell, rather than in the page: the three artwork
 * rows draw their own heading inset and their own `LazyRow` padding because they
 * are *not* cards, and that was a second literal in each file. The two drifted
 * without anything noticing, because the only way to see the drift is to hold the
 * two numbers side by side.
 */
internal val PAGE_INSET = 20.dp
