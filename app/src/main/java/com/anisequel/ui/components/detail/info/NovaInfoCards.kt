package com.anisequel.ui.components.detail.info

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.airingCountdown
import com.anisequel.data.model.airingEpisodeNumber
import com.anisequel.data.model.compactCount
import com.anisequel.data.model.hasProgressCard
import com.anisequel.data.model.isManga
import com.anisequel.data.model.progressCountLabel
import com.anisequel.data.model.watchFraction
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.components.rememberFullHeightSheetState
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
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
private data class NovaStat(
    val label: String,
    val value: String,
    /**
     * The share of a ring this number is, when it is one.
     *
     * Only the score has a whole to be a share of - 76 of 100 - which is what makes
     * it the one number on the strip that earns a ring rather than a plain figure.
     * Null for the rest, and the null is load-bearing: a ring drawn around
     * "58K popularity" at some made-up fraction would be a picture of a measurement
     * nobody took.
     */
    val fraction: Float? = null
)

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
                        value = String.format(Locale.US, "%.1f", score / 10f),
                        fraction = (score / 100f).coerceIn(0f, 1f)
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
                                entranceKey = media.type to media.id,
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

/** How tall the value slot is in every cell, ringed or not. */
private val STAT_VALUE_SLOT = 46.dp

/**
 * One number of the strip: its value over its label, tappable for the drawer.
 *
 * ## Why the value slot has a fixed height
 *
 * The score is drawn inside [NovaWavyRing] and the other three are plain figures, so
 * the cells have different natural heights. Left to themselves the labels would sit
 * at four different heights on the one row a reader scans across, which reads as
 * four unrelated numbers rather than as one strip. The slot is therefore reserved for
 * every cell - the ring fits it exactly, and a plain figure is centred in it - so the
 * four labels share a baseline whether or not their number carries a ring.
 */
@Composable
private fun RowScope.NovaStatCell(
    stat: NovaStat,
    entranceKey: Any?,
    onClick: () -> Unit
) {
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
        Box(
            modifier = Modifier.height(STAT_VALUE_SLOT),
            contentAlignment = Alignment.Center
        ) {
            val fraction = stat.fraction
            if (fraction != null) {
                NovaWavyRing(
                    progress = fraction,
                    entranceKey = entranceKey,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(STAT_VALUE_SLOT)
                ) {
                    // `titleSmall` inside the ring rather than `titleLarge` beside
                    // it: three characters in a 46dp ring at titleLarge touch the
                    // stroke on both sides.
                    NovaStatValue(stat = stat, style = MaterialTheme.typography.titleSmall)
                }
            } else {
                NovaStatValue(stat = stat, style = MaterialTheme.typography.titleLarge)
            }
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
 * The number itself, rolling to a new value rather than fading.
 *
 * A fade was the whole transition, and on this strip it read as a flicker: two
 * numbers of different widths cross-faded in place, so the row twitched sideways
 * while it changed. The slide is the one [com.anisequel.ui.components.expressive.AnimatedCounterText]
 * uses for the same job - up and out, in from below, on the shared offset token - so
 * the strip and the counters elsewhere in the app move the same way, and under
 * `INSTANT` they both simply become the new number.
 */
@Composable
private fun NovaStatValue(
    stat: NovaStat,
    style: androidx.compose.ui.text.TextStyle
) {
    AnimatedContent(
        targetState = stat.value,
        transitionSpec = {
            (slideInVertically(animationSpec = ExpressiveMotion.FastSpatialOffset) { it / 3 } +
                fadeIn(animationSpec = ExpressiveMotion.FastEffects)) togetherWith
                (slideOutVertically(animationSpec = ExpressiveMotion.FastSpatialOffset) { -it / 3 } +
                    fadeOut(animationSpec = ExpressiveMotion.FastEffects)) using null
        },
        label = "nova_stat_value_${stat.label}"
    ) { value ->
        Text(
            text = value,
            style = style,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                Box(
                    modifier = Modifier.height(STAT_VALUE_SLOT),
                    contentAlignment = Alignment.Center
                ) {
                    // The score's cell is a *measurement* that has not been taken
                    // yet, so it waits as the page's loading mark - the wavy ring,
                    // turning - rather than as a shimmer bar standing in for a
                    // number. The other three have no ring to fill, so they keep the
                    // shimmer, which is the strip saying "these are coming" instead
                    // of three spinners turning beside one.
                    if (label == "Score") {
                        NovaWavyRing(
                            progress = null,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                alpha = 0.22f
                            ),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(STAT_VALUE_SLOT)
                        )
                    } else {
                        ShimmerBox(
                            modifier = Modifier
                                .width(46.dp)
                                .height(22.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                            phase = phase
                        )
                    }
                }
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

