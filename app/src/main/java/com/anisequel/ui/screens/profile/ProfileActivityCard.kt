package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anisequel.data.model.ListActivity
import com.anisequel.data.model.MediaNode
import com.anisequel.ui.components.AppVectorIcons
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

@Composable
fun ActivityCard(
    activity: ListActivity,
    modifier: Modifier = Modifier,
    userName: String? = null,
    userAvatar: String? = null,
    onLike: () -> Unit = {},
    onComments: () -> Unit = {},
    onOpenLikers: () -> Unit = {},
    onOpenDetail: (MediaNode) -> Unit = {},
    likePending: Boolean = false
) {
    // Read before the early return below rather than after it: the card has nothing
    // to draw without a media node, and a `Local` read that only happens on some
    // paths is the kind of composition structure that is easy to get wrong later.
    val context = LocalContext.current
    val media = activity.media ?: return
    val coverUrl = media.coverImage?.bestUrl ?: media.coverImage?.large
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
            // The cover, blown up and blurred behind its own card.
            //
            // The blur does real work rather than decoration: a *sharp* crop of
            // the art sat its own lettering and faces directly behind the title
            // and the status pill, so the card's text fought the background no
            // matter how the scrim was weighted. Blurred into a wash, the show is
            // still recognisable by palette and every glyph on the card is
            // legible. The scrim is bottom-weighted for the same reason the
            // header's is: the top stays art, the bottom stays readable.
            if (coverUrl != null) {
                AsyncImage(
                    // Remembered rather than built inline: Coil compares requests by
                    // value, so a fresh instance on every recomposition is a cache
                    // lookup that can never hit - and this card recomposes when the
                    // feed pages, when a like lands and when its press animates.
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

            Column(modifier = Modifier.padding(14.dp)) {
                // User info header row if present
                if (userName != null || userAvatar != null || activity.createdAt != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (userAvatar != null) {
                            AsyncImage(
                                model = userAvatar,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        if (userName != null) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        if (activity.createdAt != null) {
                            Text(
                                text = formatRelativeSeconds(activity.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row {
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
                        placeholder = rememberVectorPainter(
                            if (isManga) AppVectorIcons.FavouriteManga else AppVectorIcons.FavouriteAnime
                        ),
                        error = rememberVectorPainter(
                            if (isManga) AppVectorIcons.FavouriteManga else AppVectorIcons.FavouriteAnime
                        ),
                        fallback = rememberVectorPainter(
                            if (isManga) AppVectorIcons.FavouriteManga else AppVectorIcons.FavouriteAnime
                        )
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        ActivityStatusPill(
                            text = activity.displayStatusFor(isManga),
                            accent = activity.status.equals("completed", ignoreCase = true)
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
                        Row(
                            // 8 rather than 12: the two controls below now carry a
                            // 48dp minimum each, and at 12 this row needs 182dp of a
                            // 190dp budget on a 360dp phone - so it fit only until
                            // somebody turned the font size up.
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SequelInfoChip(
                                icon = if (isManga) AppVectorIcons.FavouriteManga else AppVectorIcons.FavouriteAnime,
                                label = if (isManga) "Manga" else "Anime"
                            )
                            ActivityAction(
                                icon = if (activity.isLiked == true) {
                                    AppVectorIcons.ActivityLiked
                                } else {
                                    AppVectorIcons.ActivityNotLiked
                                },
                                count = activity.likeCount ?: 0,
                                active = activity.isLiked == true,
                                enabled = activity.isLiked != null && !likePending,
                                contentDescription = if (activity.isLiked == true) "Unlike" else "Like",
                                onClick = onLike,
                                onLongClick = onOpenLikers,
                                modifier = Modifier.testTag("activity_like_button")
                            )
                            ActivityAction(
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

/**
 * A like or comment control: an icon, a count, and whether it is active.
 *
 * The tap target is the whole row rather than the 16dp icon, because a 16dp
 * target is below the 48dp minimum and the count is part of what the user is
 * aiming at. The comment above used to claim that and not do it: the row was
 * only 16dp of icon plus 5dp of spacer plus 4dp of padding each way - about
 * 24dp tall, so a third under the minimum the comment claimed to be meeting.
 * [defaultMinSize] is applied *before* the gesture so the minimum is inside the
 * clickable's hit area, which is what actually makes the target 48dp.
 *
 * [enabled] is false for the heart when AniList has not said whether this
 * activity is liked - see the call site. The press animation used to be omitted
 * in that case, guarded by `.then(if (enabled) ...)`, because the fallback
 * pointer loop squashed anything a finger went down on regardless of whether the
 * control would do anything. Sharing the `combinedClickable`'s own
 * `InteractionSource` removes the need for the guard: a disabled control emits no
 * press, so there is nothing to suppress.
 *
 * [onLongClick] is optional; only the heart currently passes it. The long press
 * runs through `combinedClickable` so the tap path itself is unchanged.
 * `hapticFeedbackEnabled` is set false and the haptic is played by hand instead:
 * the modifier performs its own long-press haptic by default, and leaving both on
 * would buzz twice. The hand-played one is the same single
 * [HapticFeedbackType.LongPress] every other hold gesture in the app uses.
 */
@Composable
private fun ActivityAction(
    icon: ImageVector,
    count: Int,
    active: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val appearance = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                onLongClick = onLongClick?.let { open ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        open()
                    }
                },
                hapticFeedbackEnabled = false,
                onClick = onClick
            )
            .bouncyPress(pressedScale = 0.9f, interactionSource = interactionSource)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = appearance,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = appearance
        )
    }
}
