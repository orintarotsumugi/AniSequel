package com.anisequel.ui.components.detail.info

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaScoreAmount
import com.anisequel.data.model.airingCountdown
import com.anisequel.data.model.airingEpisodeNumber
import com.anisequel.data.model.compactCount
import com.anisequel.data.model.displayName
import com.anisequel.data.model.hasProgressCard
import com.anisequel.data.model.isManga
import com.anisequel.data.model.progressCountLabel
import com.anisequel.data.model.rankingList
import com.anisequel.data.model.trailerYoutubeUrl
import com.anisequel.data.model.watchFraction
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.components.cards.unseamedCover
import com.anisequel.ui.components.rememberFullHeightSheetState
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.openExternalUrlOrCopy
import java.util.Locale

/**
 * The three surfaces the Nova title page adds to the Info vocabulary.
 *
 * They live together because they are the page's *summary*: the numbers, the
 * progress and the trailer are what a reader looks at before deciding whether to
 * read the synopsis or the cast. The cards the two pages share - synopsis,
 * details, categories, the rows - stay in their own files, so this one has no
 * second copy of them to drift from.
 */

/** One number in [NovaStatsCard], with the label the reference prints under it. */
private data class NovaStat(val label: String, val value: String)

/**
 * The four-up number strip under the hero.
 *
 * The reference client's single most useful block, and the reason the Nova page
 * does not scatter these facts through the details grid: score, popularity,
 * favourites and length are the four numbers a reader compares between titles, and
 * a row that puts them side by side is a comparison rather than a paragraph.
 *
 * The values are deliberately *not* the details card's rows. That card prints what
 * production knows - source, season, country, dates - and printing "Popularity"
 * there as well would make one fact appear twice on the same scroll under two
 * headings, which is the duplication the classic page's KDoc already argues
 * against. This card owns the four numbers; the details card owns the rest.
 *
 * The countdown is the fifth fact and is not a fifth column: it is a sentence, not
 * a number, so it gets its own line across the strip. It is also the one value on
 * the page that changes while the reader is looking at it - see
 * [rememberAiringCountdown] - and a sentence that ticks is easier to read across a
 * full line than inside a quarter-width cell.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun NovaStatsCard(
    media: MediaNode,
    modifier: Modifier = Modifier,
    /**
     * Whether the detail response is still in flight.
     *
     * The list keeps this card's slot while it is loading (see `novaPageItems`),
     * so an empty strip here is *reserved*, not absent: drawing nothing would leave
     * a bare `primaryContainer` rectangle, and returning early would collapse the
     * item and make every card below jump up. The placeholders are the four labels
     * the strip is about to carry, with the numbers still shimmering.
     */
    isLoading: Boolean = false
) {
    var selectedStat by remember(media.type, media.id) { mutableStateOf<String?>(null) }
    // The shared full-height state, not a hand-built one. This sheet used to be
    // measured with `PartiallyExpanded` enabled as well, so the statistics drawer
    // could settle half-open on a tall distribution list - the top of the drawer
    // cut off mid-row with nothing to drag but the scrim. Every other sheet in the
    // app opens to one stop; this one is no longer the exception.
    val sheetState = rememberFullHeightSheetState()
    val stats = remember(media) {
        buildList {
            media.averageScore?.takeIf { it > 0 }?.let { score ->
                add(
                    NovaStat(
                        label = "Score",
                        value = String.format(Locale.US, "%.1f", score / 10f)
                    )
                )
            }
            media.popularity?.takeIf { it > 0 }?.let { popularity ->
                add(NovaStat(label = "Popularity", value = compactCount(popularity)))
            }
            media.favourites?.takeIf { it > 0 }?.let { favourites ->
                add(NovaStat(label = "Favourites", value = compactCount(favourites)))
            }
            if (media.isManga) {
                media.chapters?.takeIf { it > 0 }?.let { chapters ->
                    add(NovaStat(label = "Chapters", value = chapters.toString()))
                }
            } else {
                // One episode's length in the strip's own compact spelling: the
                // details card's "24 mins / ep" is too wide for a quarter-width
                // cell and ellipsizes under the value it is supposed to label.
                media.duration?.takeIf { it > 0 }?.let { duration ->
                    add(NovaStat(label = "Duration", value = "${duration}m"))
                }
            }
        }
    }
    // Only anime carry `nextAiringEpisode`, and only a *tracked* title has the
    // classic page's progress card to state the countdown in. An untracked title
    // gets it from the Details card instead, so drawing it here too would print
    // the same sentence twice on one scroll - the duplication the classic page's
    // KDoc already guards against.
    //
    // The gate is `stats.isEmpty() && countdown == null`, not `stats.isEmpty()`:
    // a tracked show with no score, popularity or length still has a countdown to
    // print, and returning before it would leave the page's item list holding a
    // card that draws nothing - a 14dp hole, which is the failure the section
    // gating above exists to prevent.
    val countdown = if (media.isManga || !media.hasProgressCard) {
        null
    } else {
        rememberAiringCountdown(media)
    }
    // Loading and *empty*: a summary node that already carries some numbers keeps
    // them on screen while the rest arrives, rather than replacing live data with
    // shimmer it already has.
    val placeholder = isLoading && stats.isEmpty() && countdown == null
    if (stats.isEmpty() && countdown == null && !placeholder) return
    val episode = media.airingEpisodeNumber

    Surface(
        shape = MaterialTheme.shapes.large,
        // The four numbers are the page's first summary, so they get the
        // primary container instead of the shared neutral surface. The page's
        // scheme is derived from the artwork, which means this is the cover's
        // hue at a readable tone rather than the one blue every card used to be.
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.76f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("nova_info_stats_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Crossfade(
                targetState = placeholder,
                animationSpec = ExpressiveMotion.FastEffects,
                label = "nova_stats_content"
            ) { loading ->
                if (loading) {
                    NovaStatsPlaceholder(media = media)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        stats.forEach { stat ->
                            NovaStatCell(
                                stat = stat,
                                onClick = { selectedStat = stat.label }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = countdown != null,
                enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    expandVertically(animationSpec = ExpressiveMotion.FastSpatialSize),
                exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    shrinkVertically(animationSpec = ExpressiveMotion.FastSpatialSize)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // A divider rather than a second card: the countdown belongs to the
                    // strip, and a seam inside one surface is a weaker boundary than a
                    // second card would draw around a single sentence.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f)
                            )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nova_info_countdown"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AnimatedContent(
                            targetState = if (episode != null) {
                                "Episode $episode airs $countdown"
                            } else {
                                "Next episode $countdown"
                            },
                            // The only thing that changes every minute is the
                            // countdown itself, and that is an update to one
                            // sentence, not a new state. Keyed on the sentence's
                            // *kind*, a tick rewrites the text in place; only the
                            // episode number arriving or disappearing crossfades.
                            // Before this, the line faded in and out once a minute
                            // for as long as the page was open.
                            contentKey = { text -> text.startsWith("Episode") },
                            transitionSpec = {
                                fadeIn(animationSpec = ExpressiveMotion.FastEffects) togetherWith
                                    fadeOut(animationSpec = ExpressiveMotion.FastEffects) using null
                            },
                            label = "nova_countdown_text",
                            modifier = Modifier.weight(1f, fill = false)
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    selectedStat?.let { label ->
        ModalBottomSheet(
            onDismissRequest = { selectedStat = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            NovaStatDetails(media = media, selectedLabel = label)
        }
    }
}

@Composable
private fun NovaStatDetails(media: MediaNode, selectedLabel: String) {
    // Derived once per stats payload rather than once per pass. The drawer is a
    // scrolling `Column`, so opening it, dragging the sheet or a countdown above it
    // re-running would otherwise re-filter, re-sort and re-sum four lists on every
    // recomposition - for data that cannot have changed.
    val stats = media.stats
    val scoreRows = remember(stats) {
        stats?.scoreDistribution.orEmpty()
            .filter { (it.score ?: 0) > 0 && (it.amount ?: 0) > 0 }
            .sortedBy { it.score }
    }
    val statusRows = remember(stats) {
        stats?.statusDistribution.orEmpty()
            .filter { !it.status.isNullOrBlank() && (it.amount ?: 0) > 0 }
            .sortedByDescending { it.amount }
    }
    val totalRatings = remember(scoreRows) { scoreRows.sumOf { it.amount ?: 0 } }
    val maxScoreCount = remember(scoreRows) { scoreRows.maxOfOrNull { it.amount ?: 0 } ?: 0 }
    val totalStatuses = remember(statusRows) { statusRows.sumOf { it.amount ?: 0 } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                selectedStatTitle(selectedLabel),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                media.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        when (selectedLabel) {
            "Score" -> NovaScoreDetails(
                media = media,
                scoreRows = scoreRows,
                totalRatings = totalRatings,
                maxScoreCount = maxScoreCount
            )
            "Popularity" -> NovaPopularityDetails(media)
            "Favourites" -> NovaFavouritesDetails(media)
            else -> NovaLengthDetails(media)
        }

        if (statusRows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "List status breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        compactCount(totalStatuses),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                statusRows.forEachIndexed { index, row ->
                    val amount = row.amount ?: 0
                    DistributionBarRow(
                        // Enum labels are protocol text. Folding them with the
                        // device locale turned `HIATUS` into `Hıatus` on a
                        // Turkish device, which is visible in the statistics card.
                        label = row.status.orEmpty().replace('_', ' ').lowercase(Locale.ROOT)
                            .replaceFirstChar { it.uppercase(Locale.ROOT) },
                        value = compactCount(amount),
                        fraction = if (totalStatuses > 0) amount.toFloat() / totalStatuses else 0f,
                        accent = chartAccent(index)
                    )
                }
            }
        }

        media.description?.takeIf { it.isNotBlank() }?.let { synopsis ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Synopsis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        synopsis.replace(Regex("<[^>]*>"), "").replace("&mdash;", "—").replace("&quot;", "\""),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}


@Composable
private fun NovaScoreDetails(
    media: MediaNode,
    scoreRows: List<MediaScoreAmount>,
    totalRatings: Int,
    maxScoreCount: Int
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(AppVectorIcons.Star, contentDescription = null, modifier = Modifier.size(30.dp))
            Column {
                Text(
                    media.averageScore?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f", it / 10f) } ?: "—",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("Average score · out of 10", style = MaterialTheme.typography.labelLarge)
                if (totalRatings > 0) Text("${compactCount(totalRatings)} ratings", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text("Score distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (scoreRows.isEmpty()) {
        EmptyStatMessage("AniList has not supplied a score distribution for this title.")
    } else {
        scoreRows.forEach { row ->
            val amount = row.amount ?: 0
            DistributionBarRow(
                label = String.format(Locale.US, "%.1f", (row.score ?: 0) / 10f),
                value = compactCount(amount),
                fraction = if (maxScoreCount > 0) amount.toFloat() / maxScoreCount else 0f,
                accent = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NovaPopularityDetails(media: MediaNode) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("POPULARITY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                media.popularity?.takeIf { it > 0 }?.let(::compactCount) ?: "—",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text("AniList popularity count", style = MaterialTheme.typography.bodySmall)
        }
    }
    Text("Catalogue rankings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (media.rankingList.isEmpty()) {
        EmptyStatMessage("No catalogue ranking is available for this title.")
    } else {
        media.rankingList.forEach { ranking ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        ranking.rank?.let { "#$it" } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(ranking.context ?: "Catalogue ranking", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NovaFavouritesDetails(media: MediaNode) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(AppVectorIcons.ActivityLiked, contentDescription = null, modifier = Modifier.size(30.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    media.favourites?.takeIf { it > 0 }?.let(::compactCount) ?: "—",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("AniList favourites", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    EmptyStatMessage(
        "This is the total number of AniList favourites. AniList does not provide a public list of every account that favourited this title."
    )
}

@Composable
private fun NovaLengthDetails(media: MediaNode) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (media.isManga) "CHAPTERS" else "EPISODE LENGTH", style = MaterialTheme.typography.labelMedium)
            Text(
                if (media.isManga) media.chapters?.takeIf { it > 0 }?.toString() ?: "—"
                else media.duration?.let { "$it min" } ?: "—",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (media.isManga) "Published chapters" else "Average runtime per episode",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DistributionBarRow(
    label: String,
    value: String,
    fraction: Float,
    accent: Color
) {
    // The State object, not the delegated value. The fill is drawn below, so a
    // frame of the spring invalidates this row's draw pass and nothing else. The
    // row used to read the value in composition and paint it with
    // `fillMaxWidth(fraction)`, which recomposed, re-measured and re-laid-out
    // every bar in the drawer on every frame of every spring - a statistics
    // sheet animating up to a dozen bars at once is exactly where that shows.
    val animatedFraction = animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "nova_stat_distribution_bar"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val filled = size.width * animatedFraction.value
                        if (filled > 0f) {
                            drawRect(color = accent, size = Size(filled, size.height))
                        }
                    }
            )
        }
    }
}

@Composable
private fun EmptyStatMessage(message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun chartAccent(index: Int): Color = when (index % 5) {
    0 -> Color(0xFF6D9FDE)
    1 -> Color(0xFF64A88A)
    2 -> Color(0xFFD99B59)
    3 -> Color(0xFFB48AD8)
    else -> Color(0xFFD47C86)
}

private fun selectedStatTitle(label: String): String = when (label) {
    "Duration" -> "Episode details"
    "Chapters" -> "Reading details"
    else -> label
}

/**
 * The watched share as the page's own wave.
 *
 * Same [WavyProgressBar] as the classic page, not a second drawing of one: the wave
 * is the app's progress mark, and two versions of it a layout switch apart would be
 * two different answers to "how far in am I". The card around it is the new part -
 * a capsule-width surface with the count on the same line as the label, so the
 * wave is read *with* the number rather than under a card heading.
 */
@Composable
internal fun NovaProgressCard(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val fraction = media.watchFraction() ?: return
    val count = media.progressCountLabel ?: return
    val isManga = media.isManga

    Surface(
        shape = MaterialTheme.shapes.large,
        // The second summary surface gets the secondary container, not another
        // copy of the stats card's tone. Holding the bar opens the style picker,
        // and this is also where the chosen style's own accent is read against a
        // surface chosen for it.
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.76f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("nova_info_progress_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isManga) "Reading Progress" else "Watching Progress",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f),
                    modifier = Modifier.weight(1f)
                )
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        fadeIn(animationSpec = ExpressiveMotion.FastEffects) togetherWith
                            fadeOut(animationSpec = ExpressiveMotion.FastEffects) using null
                    },
                    label = "nova_progress_count"
                ) { progressCount ->
                    Text(
                        text = progressCount,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            StyledProgressBar(
                progress = fraction,
                entranceKey = media.type to media.id,
                height = 38.dp,
                progressColor = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * The official trailer, as a card a reader can recognise before tapping.
 *
 * The trailer used to be a list row in the Extras card. That is honest - it opens
 * the video externally - but it is also the one piece of *artwork* on the page
 * that the app has a thumbnail for and never drew: AniList carries the YouTube id,
 * and YouTube publishes a deterministic thumbnail for it. The card uses that
 * picture as its surface, so the trailer reads as a destination rather than as
 * another settings row at the bottom of the page.
 *
 * The tap goes through the shared external opener, so a device with no browser
 * leaves the URL on the clipboard instead of appearing to ignore the tap.
 */
@Composable
internal fun NovaTrailerCard(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val url = media.trailerYoutubeUrl ?: return
    val videoId = media.trailer?.id?.takeIf { it.isNotBlank() } ?: return
    val thumbnail = remember(videoId) { "https://img.youtube.com/vi/$videoId/hqdefault.jpg" }
    val context = LocalContext.current
    val shape = MaterialTheme.shapes.large
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "nova_trailer_play_scale"
    )
    val playContainer by animateColorAsState(
        targetValue = if (pressed) Color.Black.copy(alpha = 0.68f) else Color.Black.copy(alpha = 0.52f),
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nova_trailer_play_container"
    )
    val scrimBottom by animateColorAsState(
        targetValue = if (pressed) Color.Black.copy(alpha = 0.82f) else Color.Black.copy(alpha = 0.76f),
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nova_trailer_scrim"
    )

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .bouncyPress(pressedScale = 0.98f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClickLabel = "Watch the official trailer"
            ) { openExternalUrlOrCopy(context, url) }
            .testTag("nova_info_trailer_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .unseamedCover()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.05f),
                            0.55f to Color.Black.copy(alpha = 0.22f),
                            1f to scrimBottom
                        )
                    )
            )
            Surface(
                shape = CircleShape,
                color = playContainer,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.42f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
                    .graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
                    }
                    .testTag("nova_info_trailer_play")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppVectorIcons.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(26.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Official trailer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = media.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** One number of the strip: its value over its label, tappable for the drawer. */
@Composable
private fun RowScope.NovaStatCell(stat: NovaStat, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.small)
            .bouncyPress(pressedScale = 0.95f, interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClickLabel = "Show ${stat.label} details",
                onClick = onClick,
                onLongClickLabel = "Show ${stat.label} details",
                onLongClick = onClick
            )
            .heightIn(min = 52.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = stat.value,
            transitionSpec = {
                fadeIn(animationSpec = ExpressiveMotion.FastEffects) togetherWith
                    fadeOut(animationSpec = ExpressiveMotion.FastEffects) using null
            },
            label = "nova_stat_value_${stat.label}"
        ) { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = stat.label,
            // `labelSmall`, not `labelMedium`: four columns on a 360dp phone
            // leave about 78dp each, and "Popularity" at labelMedium ellipsizes
            // into "Populari...".
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The four cells of [NovaStatsCard] while its numbers are still loading.
 *
 * One `rememberShimmerPhase` for the row, not one per box: the phase is the frame
 * clock, and four copies of it are four infinite transitions running behind one
 * card. The labels are real because they are already known - it is only the values
 * that are in flight - so the strip does not change shape when the numbers land.
 */
@Composable
private fun NovaStatsPlaceholder(media: MediaNode) {
    val labels = if (media.isManga) {
        listOf("Score", "Popularity", "Favourites", "Chapters")
    } else {
        listOf("Score", "Popularity", "Favourites", "Duration")
    }
    val phase = rememberShimmerPhase()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        labels.forEach { label ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .width(46.dp)
                        .height(22.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    phase = phase
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Whether [NovaStatsCard] has anything to draw. Kept beside the card so the gate
 * and the content cannot disagree. */
internal fun novaHasStats(media: MediaNode): Boolean =
    (media.averageScore ?: 0) > 0 ||
        (media.popularity ?: 0) > 0 ||
        (media.favourites ?: 0) > 0 ||
        (!media.isManga && (media.duration ?: 0) > 0) ||
        (media.isManga && (media.chapters ?: 0) > 0) ||
        // Current time, not merely a scheduled one: an episode whose air time has
        // passed has no countdown to draw, and the card would return early into an
        // empty item - the 14dp hole this predicate exists to avoid.
        (!media.isManga && media.hasProgressCard && media.airingCountdown() != null)

