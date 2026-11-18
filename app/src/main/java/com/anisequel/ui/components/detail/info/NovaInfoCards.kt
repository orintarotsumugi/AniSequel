package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.anisequel.data.model.airingCountdown
import com.anisequel.data.model.airingEpisodeNumber
import com.anisequel.data.model.compactCount
import com.anisequel.data.model.displayName
import com.anisequel.data.model.hasProgressCard
import com.anisequel.data.model.isManga
import com.anisequel.data.model.progressCountLabel
import com.anisequel.data.model.trailerYoutubeUrl
import com.anisequel.data.model.watchFraction
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.unseamedCover
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
internal fun NovaStatsCard(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
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
    if (stats.isEmpty() && countdown == null) return
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                stats.forEach { stat ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stat.value,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stat.label,
                            // `labelSmall`, not `labelMedium`: four columns on a
                            // 360dp phone leave about 78dp each, and "Popularity"
                            // at labelMedium ellipsizes into "Populari...".
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (countdown != null) {
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
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = if (episode != null) {
                            "Episode $episode airs $countdown"
                        } else {
                            "Next episode $countdown"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
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
            verticalArrangement = Arrangement.spacedBy(2.dp)
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
                Text(
                    text = count,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
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
            .bouncyPress(pressedScale = 0.98f)
            .clickable(
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
                            1f to Color.Black.copy(alpha = 0.76f)
                        )
                    )
            )
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.52f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.42f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
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
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
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
                    color = Color.White.copy(alpha = 0.78f),
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

