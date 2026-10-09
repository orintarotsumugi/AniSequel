package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ListActivity
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.cards.SequelInfoChip
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.components.openExternalUrl
import java.util.Locale

/** AniList covers are 2:3. */
private const val POSTER_ASPECT = 2f / 3f

/**
 * How present the cover is behind its own card.
 *
 * Low on purpose: this is a background, and a card whose background competes with
 * the status pill is a card nobody can read the pill on.
 */
private const val COVER_BACKGROUND_ALPHA = 0.18f

@Composable
fun ActivityCard(
    activity: ListActivity,
    modifier: Modifier = Modifier
) {
    val media = activity.media ?: return
    val coverUrl = media.coverImage?.bestUrl ?: media.coverImage?.large
    // `MANGA` is the value AniList sends, but the model documents `type` as the
    // discriminator the whole feed depends on, so a case difference must not be
    // what decides whether a manga row reads "Episode" or "Chapter".
    val isManga = media.type.equals("MANGA", ignoreCase = true)
    val context = LocalContext.current
    val siteUrl = media.siteUrl

    Card(
        modifier = modifier
            .bouncyPress(pressedScale = 0.97f)
            .clickable(enabled = siteUrl != null) {
                siteUrl?.let { openExternalUrl(context, it) }
            }
            .testTag("activity_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Box {
            // The tinted cover, behind everything. `fillMaxSize` inside a Box whose
            // size comes from the content below, so it is measured last and drawn first.
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop,
                    alpha = COVER_BACKGROUND_ALPHA
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
                                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f)
                                )
                            )
                        )
                )
            }

            Row(modifier = Modifier.padding(14.dp)) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(92.dp)
                        .aspectRatio(POSTER_ASPECT)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(AppVectorIcons.FavouriteAnime),
                    error = rememberVectorPainter(AppVectorIcons.FavouriteAnime),
                    fallback = rememberVectorPainter(AppVectorIcons.FavouriteAnime)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActivityStatusPill(
                            text = activity.displayStatusFor(isManga),
                            // `equals(ignoreCase = true)` rather than
                            // `lowercase() ==`: the status is already lowercase in
                            // AniList's payload, and lowercasing it again asks the
                            // JVM's default locale to do a job that is not a
                            // locale question at all (`I` does not lowercase to
                            // `i` in Turkish).
                            accent = activity.status.equals("completed", ignoreCase = true)
                        )
                        if (activity.createdAt != null) {
                            Text(
                                text = formatRelativeSeconds(activity.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activityDetail(activity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = media.title?.displayTitle.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Anime and manga rows were previously identical cards with
                        // different cover art - and the two share covers often
                        // enough (manga and its adaptation) that the feed could
                        // not be read without opening something. The type is the
                        // one field that always answers it.
                        SequelInfoChip(
                            icon = if (isManga) AppVectorIcons.FavouriteManga else AppVectorIcons.FavouriteAnime,
                            label = if (isManga) "Manga" else "Anime"
                        )
                        ActivityCount(
                            icon = AppVectorIcons.Star,
                            count = activity.likeCount ?: 0
                        )
                        ActivityCount(
                            icon = AppVectorIcons.ActivityReplies,
                            count = activity.replyCount ?: 0
                        )
                    }
                }
            }
        }
    }
}

/**
 * The sentence under the status pill.
 *
 * "Episode 7 of 24" while watching, the title alone when there is nothing to add.
 * The progress is only rendered when AniList gave a progress *and* the show has an
 * episode count to compare it against - "Episode 7 of 0" is worse than no second
 * line at all.
 *
 * Manga is worded as **chapters**, not episodes, and never as "of N": this query
 * does not ask for a manga's total chapter count (the model has no field for it),
 * and "Chapter 42 of null" is worse than the number on its own. The demo fixture
 * carries one manga row so this branch is exercised without a real account.
 */
private fun activityDetail(activity: ListActivity): String {
    val media = activity.media ?: return ""
    val isManga = media.type.equals("MANGA", ignoreCase = true)
    val progress = activity.progress?.takeIf { it.isNotBlank() }
        ?: return formatLabel(media.format)
    if (isManga) return "Chapter $progress"
    val episodes = media.episodes ?: return "Episode $progress"
    return "Episode $progress of $episodes"
}

/**
 * AniList's `MediaFormat` enum, spelled the way a person writes it.
 *
 * `format.lowercase()` produced "Tv", because the field is an enum name rather
 * than a word - so every entry in the feed with no progress on it was labelled
 * "Tv" or "Manga" next to the title. This is the same mapping the favourites row
 * uses; there is no shared home for it yet because the two disagree on the
 * separator ("TV • 84%" there, the format alone here), and a helper parameterised
 * on that would be more code than the table.
 */
private fun formatLabel(format: String?): String {
    if (format == null) return ""
    return when (format.uppercase(Locale.ROOT)) {
        "TV" -> "TV"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MANGA" -> "Manga"
        "NOVEL" -> "Novel"
        "ONE_SHOT" -> "One Shot"
        else -> format.replace('_', ' ').lowercase(Locale.ROOT)
            .replaceFirstChar { it.uppercase(Locale.ROOT) }
    }
}

@Composable
private fun ActivityStatusPill(text: String, accent: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (accent) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        },
        maxLines = 1,
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(
                if (accent) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("activity_status_pill")
    )
}

@Composable
private fun ActivityCount(icon: ImageVector, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
