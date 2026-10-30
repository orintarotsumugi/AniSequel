package com.anisequel.ui.screens.profile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anisequel.data.model.ProfileActivity
import com.anisequel.data.model.MediaNode
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.EngagementAction
import com.anisequel.ui.components.cards.SequelInfoChip
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import java.util.Locale

/** AniList covers are 2:3. */
private const val POSTER_ASPECT = 2f / 3f

/**
 * How present the cover is behind its own card.
 *
 * Raised from 0.22 now that the backdrop is blurred: a sharp crop had to stay
 * faint to keep the card's text readable, whereas a blurred one can be half
 * present and still leave every glyph legible - so the show's palette actually
 * reads instead of being a hint.
 */
private const val COVER_BACKGROUND_ALPHA = 0.50f

/**
 * How hard the ambient cover is blurred.
 *
 * Enough that the art behind a card reads as a colour wash and its own lettering
 * and faces cannot compete with the card's text, which is what a sharp crop at
 * even low alpha did. `Modifier.blur` needs the API 31 render effect, which is
 * this app's whole supported range.
 */
private val COVER_BLUR_RADIUS = 28.dp

/**
 * How large the blurred backdrop is decoded, on its longest side.
 *
 * A quarter of what the card actually draws on a 3x phone, and deliberately so: the
 * bitmap is blurred by [COVER_BLUR_RADIUS] and then drawn at 50% alpha as a colour
 * wash, so nothing finer than a colour survives it. Asking Coil for the card's own
 * size made every row that scrolled into view pay for a full-resolution decode of
 * art it was about to smear into background - which is the busiest thing the
 * Activity feed does while scrolling, on the one tab that is read by scrolling for
 * a long time.
 *
 * 220 rather than a dp value because this is a decode budget, not a layout: the
 * blur radius is 84px at 3x, so anything above a couple of hundred pixels is
 * thrown away by the effect regardless of the screen.
 */
private const val BACKDROP_DECODE_PX = 220

/**
 * One activity in the feed, whichever member of `ActivityUnion` it is.
 *
 * Two shapes share this entry point so the feed does not have to branch: a
 * list update with a poster ([ActivityListCard]) and a text post
 * ([ActivityTextCard]). Anything else is a union member the feed does not
 * render and draws nothing - the repository filters those out before the feed
 * sees them, this is only a second line of defence for the preview.
 */
@Composable
fun ActivityCard(
    activity: ProfileActivity,
    modifier: Modifier = Modifier,
    userName: String? = null,
    userAvatar: String? = null,
    onLike: () -> Unit = {},
    onComments: () -> Unit = {},
    onOpenLikers: () -> Unit = {},
    onOpenDetail: (MediaNode) -> Unit = {},
    likePending: Boolean = false,
    nowEpochSecond: Long? = null,
    /** Opens the author's profile; null on the profile's own feed. */
    onOpenAuthor: (() -> Unit)? = null
) {
    when {
        activity.media != null -> ActivityListCard(
            activity = activity,
            modifier = modifier,
            userName = userName,
            userAvatar = userAvatar,
            onLike = onLike,
            onComments = onComments,
            onOpenLikers = onOpenLikers,
            onOpenDetail = onOpenDetail,
            likePending = likePending,
            nowEpochSecond = nowEpochSecond,
            onOpenAuthor = onOpenAuthor
        )
        activity.isTextPost -> ActivityTextCard(
            activity = activity,
            modifier = modifier,
            userName = userName,
            userAvatar = userAvatar,
            onLike = onLike,
            onComments = onComments,
            onOpenLikers = onOpenLikers,
            likePending = likePending,
            nowEpochSecond = nowEpochSecond,
            onOpenAuthor = onOpenAuthor
        )
    }
}

@Composable
private fun ActivityListCard(
    activity: ProfileActivity,
    modifier: Modifier = Modifier,
    userName: String? = null,
    userAvatar: String? = null,
    onLike: () -> Unit = {},
    onComments: () -> Unit = {},
    onOpenLikers: () -> Unit = {},
    onOpenDetail: (MediaNode) -> Unit = {},
    likePending: Boolean = false,
    /**
     * The clock the relative time is read against, hoisted by the feed so all the
     * cards share one tick and the label can actually go stale correctly.
     *
     * Null is "read the clock at composition", which is what a preview or a
     * caller with one card wants.
     */
    nowEpochSecond: Long? = null,
    /** Opens the author's profile; null on the profile's own feed. */
    onOpenAuthor: (() -> Unit)? = null
) {
    // Read before the early return below rather than after it: the card has nothing
    // to draw without a media node, and a `Local` read that only happens on some
    // paths is the kind of composition structure that is easy to get wrong later.
    val context = LocalContext.current
    val media = activity.media ?: return
    // `large` before `bestUrl`, deliberately: this row draws a 96dp poster and a
    // backdrop decoded at [BACKDROP_DECODE_PX] pixels, so `extraLarge` (AniList
    // serves roughly 460x690 there) is more than twice the pixels anything on the
    // card can show - downloaded and decoded for every row that scrolls in. The
    // fallback keeps the card working if a response omits the smaller size.
    val coverUrl = media.coverImage?.large ?: media.coverImage?.extraLarge
    // `MANGA` is the value AniList sends, but the model documents `type` as the
    // discriminator the whole feed depends on, so a case difference must not be
    // what decides whether a manga row reads "Episode" or "Chapter".
    val isManga = media.type.equals("MANGA", ignoreCase = true)

    // Shared with [bouncyPress] rather than kept to itself: the `clickable`
    // cancels its press as soon as the gesture becomes a scroll, so dragging the
    // feed no longer leaves the card under the finger squashed, and the press
    // animation costs one pointer-input node instead of two on every row.
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                onClick = { onOpenDetail(media) }
            )
            .testTag("activity_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Box {
            ActivityCoverBackdrop(coverUrl = coverUrl, context = context)

            Column(modifier = Modifier.padding(14.dp)) {
                ActivityAuthorRow(
                    userName = userName,
                    userAvatar = userAvatar,
                    createdAt = activity.createdAt,
                    nowEpochSecond = nowEpochSecond,
                    avatarSize = 28.dp,
                    onOpenAuthor = onOpenAuthor
                )

                Row {
                    ActivityPoster(coverUrl = coverUrl, isManga = isManga)

                    Spacer(modifier = Modifier.width(14.dp))

                    ActivityListDetails(
                        activity = activity,
                        media = media,
                        isManga = isManga,
                        likePending = likePending,
                        onLike = onLike,
                        onComments = onComments,
                        onOpenLikers = onOpenLikers,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * The cover, blown up and blurred behind its own card.
 *
 * The blur does real work rather than decoration: a *sharp* crop of the art sat
 * its own lettering and faces directly behind the title and the status pill, so
 * the card's text fought the background no matter how the scrim was weighted.
 * Blurred into a wash, the show is still recognisable by palette and every glyph
 * on the card is legible. The scrim is bottom-weighted for the same reason the
 * header's is: the top stays art, the bottom stays readable.
 *
 * A [BoxScope] extension because `matchParentSize` is: the wash has to take the
 * card's measured size without influencing it, which is exactly what that modifier
 * exists for and `fillMaxSize` does not do.
 */
@Composable
private fun BoxScope.ActivityCoverBackdrop(coverUrl: String?, context: Context) {
    if (coverUrl == null) return
    AsyncImage(
        // Remembered rather than built inline: Coil compares requests by value,
        // so a fresh instance on every recomposition is a cache lookup that can
        // never hit - and this card recomposes when the feed pages, when a like
        // lands and when its press animates.
        model = remember(coverUrl, context) {
            ImageRequest.Builder(context)
                .data(coverUrl)
                .size(BACKDROP_DECODE_PX)
                .build()
        },
        contentDescription = null,
        modifier = Modifier
            .matchParentSize()
            .blur(COVER_BLUR_RADIUS),
        contentScale = ContentScale.Crop,
        alpha = COVER_BACKGROUND_ALPHA
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.40f),
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.80f),
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f)
                    )
                )
            )
    )
}

/** The fixed-width poster, falling back to the type's own glyph. */
@Composable
private fun ActivityPoster(coverUrl: String?, isManga: Boolean) {
    // One painter for all three slots rather than three separate ones: the URL
    // did not load, and the same title glyph is the honest answer for a
    // placeholder, an error and a null cover alike.
    val fallback = rememberVectorPainter(
        if (isManga) AppVectorIcons.FavouriteManga else AppVectorIcons.FavouriteAnime
    )
    AsyncImage(
        model = coverUrl,
        contentDescription = null,
        modifier = Modifier
            .width(96.dp)
            .aspectRatio(POSTER_ASPECT)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentScale = ContentScale.Crop,
        placeholder = fallback,
        error = fallback,
        fallback = fallback
    )
}

/** The status pill, progress line, title and engagement controls beside the poster. */
@Composable
private fun ActivityListDetails(
    activity: ProfileActivity,
    media: MediaNode,
    isManga: Boolean,
    likePending: Boolean,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onOpenLikers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ActivityStatusPill(
            text = activity.displayStatusFor(isManga),
            status = activity.status
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = activityDetail(activity),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = media.title?.displayTitle.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        ActivityControls(
            activity = activity,
            isManga = isManga,
            likePending = likePending,
            onLike = onLike,
            onComments = onComments,
            onOpenLikers = onOpenLikers
        )
    }
}

/** The type chip, the like control and the replies control, in one row. */
@Composable
private fun ActivityControls(
    activity: ProfileActivity,
    isManga: Boolean,
    likePending: Boolean,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onOpenLikers: () -> Unit
) {
    // Read once: `isLiked` is nullable in the payload, and five separate
    // `== true` comparisons made the null case look like a different answer
    // from every place it was consulted.
    val liked = activity.isLiked == true
    Row(
        // 8 rather than 12: the two controls below now carry a 48dp minimum
        // each, and at 12 this row needs 182dp of a 190dp budget on a 360dp
        // phone - so it fit only until somebody turned the font size up.
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SequelInfoChip(
            icon = if (isManga) AppVectorIcons.FavouriteManga else AppVectorIcons.FavouriteAnime,
            label = if (isManga) "Manga" else "Anime"
        )
        EngagementAction(
            icon = if (liked) {
                AppVectorIcons.ActivityLiked
            } else {
                AppVectorIcons.ActivityNotLiked
            },
            count = activity.likeCount ?: 0,
            active = liked,
            enabled = activity.isLiked != null && !likePending,
            contentDescription = if (liked) "Unlike" else "Like",
            onClick = onLike,
            onLongClick = onOpenLikers,
            modifier = Modifier.testTag("activity_like_button")
        )
        EngagementAction(
            icon = AppVectorIcons.ActivityReplies,
            count = activity.replyCount ?: 0,
            active = false,
            enabled = true,
            contentDescription = "Comments",
            onClick = onComments,
            modifier = Modifier.testTag("activity_comments_button")
        )
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
private fun activityDetail(activity: ProfileActivity): String {
    val media = activity.media ?: return ""
    val isManga = media.type.equals("MANGA", ignoreCase = true)
    val progress = activity.progress?.takeIf { it.isNotBlank() }
    if (progress == null) {
        val label = formatLabel(media.format)
        return if (label.isNotBlank()) label else if (isManga) "Manga" else "TV"
    }
    val cleanProgress = if (isManga) {
        progress.replace("Episode", "Chapter", ignoreCase = true)
            .replace("ep", "ch", ignoreCase = true)
    } else {
        progress
    }
    if (isManga) {
        val chapters = media.chapters
        val displayNum = if (cleanProgress.startsWith("Chapter", ignoreCase = true)) cleanProgress else "Chapter $cleanProgress"
        return if (chapters != null && !displayNum.contains("of", ignoreCase = true)) "$displayNum of $chapters" else displayNum
    }
    val episodes = media.episodes
    val displayNum = if (cleanProgress.startsWith("Episode", ignoreCase = true)) cleanProgress else "Episode $cleanProgress"
    return if (episodes != null && !displayNum.contains("of", ignoreCase = true)) "$displayNum of $episodes" else displayNum
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

/**
 * The action the row reports ("Plans to watch", "Completed"), as a tinted pill.
 *
 * ## Why the status chooses the colour
 *
 * It used to be two cases - completed was tertiary, everything else primary - so
 * a dropped title and a finished one were the same blue and a feed could only be
 * skimmed by reading every pill. Six tones let the eye separate finished, in
 * progress, planned, paused and dropped without reading them, which is the one
 * thing a scrolling history is for.
 *
 * Each pair is a Material container with its matching `on` colour rather than a
 * role colour drawn *on* a container. The old pill drew `primary` on
 * `primaryContainer`, which happens to be legible with this theme's palette and
 * is not guaranteed to be with a wallpaper-derived one or in the other theme - a
 * relationship Material's `on*Container` roles exist to state.
 */
@Composable
private fun ActivityStatusPill(text: String, status: String?) {
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    when (status?.lowercase(Locale.ROOT)) {
        "completed" -> {
            container = scheme.tertiaryContainer
            content = scheme.onTertiaryContainer
        }
        "current" -> {
            container = scheme.primaryContainer
            content = scheme.onPrimaryContainer
        }
        "repeating", "planning" -> {
            container = scheme.secondaryContainer
            content = scheme.onSecondaryContainer
        }
        "paused" -> {
            container = scheme.surfaceContainerHighest
            content = scheme.onSurfaceVariant
        }
        "dropped" -> {
            container = scheme.errorContainer
            content = scheme.onErrorContainer
        }
        else -> {
            container = scheme.primaryContainer
            content = scheme.onPrimaryContainer
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("activity_status_pill")
    )
}

