package com.anisequel.ui.screens.notifications

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.domain.notifications.NotificationActor
import com.anisequel.domain.notifications.NotificationGroup
import com.anisequel.domain.notifications.NotificationKind
import com.anisequel.domain.notifications.NotificationTarget
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.expressive.ExpressiveShapes
import java.time.Instant
import java.util.Locale

/** One inbox row: a media row, a social row, or the grouped-like variant. */
@Composable
internal fun NotificationCard(
    group: NotificationGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now()
) {
    // The fold key is the saveable identity, not the representative id: a like
    // group can gain members while the card is expanded, and its representative
    // then changes without the reader having done anything.
    var expanded by rememberSaveable(group.key) { mutableStateOf(false) }
    val mediaFocused = group.representative.isMediaFocused()
    val border = if (group.isUnread) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
    // A media deletion can arrive without the title it removed, and an unknown
    // union member has no destination this build can name. Those rows still draw
    // and still expand, but a press must not promise an open that goes nowhere.
    val hasTarget = group.representative.target != NotificationTarget.None
    val contextText = group.contextLine()
    // Text overflow is a layout fact, not a character count. The child measures
    // the collapsed preview and reports back, so a wide tablet does not get a
    // Show more action for a sentence it already fits on three lines.
    var contextCanExpand by remember(group.key, contextText) { mutableStateOf(false) }
    val canExpand = contextText != null && contextCanExpand
    val relative = group.representative.relativeTime(now)
    // A screen reader should hear the gist, not a copied ten-line message. The
    // row itself still shows and expands the full text.
    val contextSummary = contextText
        ?.replace('\n', ' ')
        ?.let { if (it.length > 240) it.take(240).trimEnd() + "..." else it }
    val headline = group.headline()
    val description = notificationDescription(group, headline, contextSummary, relative)
    val press = if (hasTarget) Modifier.bouncyPress(pressedScale = 0.985f) else Modifier
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
            .clip(ExpressiveShapes.extraLarge)
            .then(press)
            .then(
                if (hasTarget) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "Open notification",
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = description
                // A clickable card merges its descendants, so the nested Show
                // more button is not a separate stop. Expose the same action on
                // the card there; a card with no destination is not clickable,
                // so its real button stays reachable and must not be duplicated.
                if (hasTarget && canExpand) {
                    customActions = listOf(
                        CustomAccessibilityAction(if (expanded) "Show less" else "Show more") {
                            expanded = !expanded
                            true
                        }
                    )
                }
            },
        shape = ExpressiveShapes.extraLarge,
        color = if (group.isUnread) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = border
    ) {
        NotificationBody(
            group = group,
            mediaFocused = mediaFocused,
            expanded = expanded,
            now = now,
            onToggleExpanded = { expanded = !expanded },
            onContextOverflow = { contextCanExpand = it }
        )
    }
}


/** The spoken summary: category, headline, title, actors, preview, time, unread. */
private fun notificationDescription(
    group: NotificationGroup,
    headline: String,
    contextSummary: String?,
    relative: String
): String = buildString {
    append(group.representative.kind.categoryLabel())
    append(". ")
    append(headline)
    // An airing headline already names the title; repeating it made the
    // row read the same show twice. A media-only activity card has no title
    // in its headline, so it still gets one here.
    group.mediaTitle()
        ?.takeIf { it.isNotBlank() && !headline.contains(it, ignoreCase = true) }
        ?.let { title ->
            append(". ")
            append(title)
        }
    if (group.actorCount > 1) {
        append(". ")
        append(group.actorCount)
        append(" people")
    }
    if (!contextSummary.isNullOrBlank()) {
        append(". ")
        append(contextSummary)
    }
    if (relative.isNotBlank()) {
        append(". ")
        append(relative)
    }
    if (group.isUnread) append(". Unread")
}

/** Media rows and social rows differ only in the body they draw. */
@Composable
private fun NotificationBody(
    group: NotificationGroup,
    mediaFocused: Boolean,
    expanded: Boolean,
    now: Instant,
    onToggleExpanded: () -> Unit,
    onContextOverflow: (Boolean) -> Unit
) {
    if (mediaFocused) {
        MediaNotificationBody(
            group = group,
            expanded = expanded,
            now = now,
            onToggleExpanded = onToggleExpanded,
            onContextOverflow = onContextOverflow
        )
    } else {
        SocialNotificationBody(
            group = group,
            expanded = expanded,
            now = now,
            onToggleExpanded = onToggleExpanded,
            onContextOverflow = onContextOverflow
        )
    }
}

@Composable
private fun MediaNotificationBody(
    group: NotificationGroup,
    expanded: Boolean,
    now: Instant,
    onToggleExpanded: () -> Unit,
    onContextOverflow: (Boolean) -> Unit
) {
    val media = when (val n = group.representative) {
        is com.anisequel.domain.notifications.AiringNotification -> n.media
        is com.anisequel.domain.notifications.MediaUpdateNotification -> n.media
        is com.anisequel.domain.notifications.SubmissionNotification -> n.media
        is com.anisequel.domain.notifications.ActivityNotification -> n.media
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        MediaPoster(media?.coverUrl, media?.type)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeGlyph(group.representative.kind, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = group.representative.kind.categoryLabel().uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                UnreadDot(group.isUnread)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = group.headline(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
            val detail = group.mediaDetail()
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val context = group.contextLine()
            if (!context.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                ExpandableText(
                    text = context,
                    expanded = expanded,
                    onToggle = onToggleExpanded,
                    onOverflowChange = onContextOverflow
                )
            }
            Spacer(Modifier.height(8.dp))
            Timestamp(group.representative.relativeTime(now))
        }
    }
}

@Composable
private fun SocialNotificationBody(
    group: NotificationGroup,
    expanded: Boolean,
    now: Instant,
    onToggleExpanded: () -> Unit,
    onContextOverflow: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActorCluster(group.actors, group.isUnread)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.representative.kind.categoryLabel().uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (group.actorCount > 1) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "${group.actorCount} people",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = group.headline(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            UnreadDot(group.isUnread)
        }
        val mediaTitle = group.mediaTitle()
        if (!mediaTitle.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            MediaContextStrip(group, mediaTitle)
        }
        val context = group.contextLine()
        if (!context.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            ExpandableText(
                text = context,
                expanded = expanded,
                onToggle = onToggleExpanded,
                preview = true,
                onOverflowChange = onContextOverflow
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Timestamp(group.representative.relativeTime(now))
            Spacer(Modifier.weight(1f))
            TypeGlyph(
                kind = group.representative.kind,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExpandableText(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    preview: Boolean = false,
    onOverflowChange: (Boolean) -> Unit = {}
) {
    var overflowing by remember(text) { mutableStateOf(false) }
    val report = rememberUpdatedState(onOverflowChange)
    // Report the measured fact instead of a guess based on character count. The
    // expanded state also counts as overflow so the card keeps its Show less
    // accessibility action after a configuration change.
    LaunchedEffect(expanded, overflowing) { report.value(expanded || overflowing) }
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                if (!expanded) {
                    val measured = layout.hasVisualOverflow
                    if (measured != overflowing) overflowing = measured
                }
            },
            modifier = if (preview && !expanded) {
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            } else {
                Modifier.fillMaxWidth()
            }
        )
        if (expanded || overflowing) {
            TextButton(
                onClick = onToggle,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)
            ) {
                Text(
                    text = if (expanded) "Show less" else "Show more",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MediaContextStrip(group: NotificationGroup, title: String) {
    val media = (group.representative as? com.anisequel.domain.notifications.ActivityNotification)?.media
    val cover = media?.coverUrl
    var failed by remember(cover) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 46.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (!cover.isNullOrBlank() && !failed) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (media?.type?.equals("MANGA", ignoreCase = true) == true) {
                        AppVectorIcons.FavouriteManga
                    } else {
                        AppVectorIcons.Movie
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val type = media?.type?.lowercase(Locale.ROOT)?.replaceFirstChar { it.uppercase() }
            if (!type.isNullOrBlank()) {
                Text(
                    text = type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MediaPoster(coverUrl: String?, type: String?) {
    var failed by remember(coverUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(66.dp)
            .height(94.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrBlank() && !failed) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (type?.equals("MANGA", ignoreCase = true) == true) {
                    AppVectorIcons.FavouriteManga
                } else {
                    AppVectorIcons.Movie
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** Two or three faces, overlapped; one face gets a plain circle. */
@Composable
private fun ActorCluster(actors: List<NotificationActor>, unread: Boolean) {
    val shown = actors.take(3)
    if (shown.size <= 1) {
        val actor = shown.firstOrNull()
        ActorAvatar(
            url = actor?.avatarUrl,
            name = actor?.name,
            size = 46.dp,
            ring = unread
        )
        return
    }
    val size = 42.dp
    val overlap = 13.dp
    Box(modifier = Modifier.width(size + overlap * (shown.size - 1)).height(size)) {
        shown.forEachIndexed { index, actor ->
            ActorAvatar(
                url = actor.avatarUrl,
                name = actor.name,
                size = size,
                ring = unread,
                modifier = Modifier.offset(x = overlap * index)
            )
        }
    }
}

@Composable
internal fun ActorAvatar(
    url: String?,
    name: String?,
    size: androidx.compose.ui.unit.Dp,
    ring: Boolean,
    modifier: Modifier = Modifier
) {
    val ringColor = MaterialTheme.colorScheme.surfaceContainerLow
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (ring) MaterialTheme.colorScheme.primary else ringColor)
            .padding(if (ring) 2.dp else 0.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank() && !failed) {
            AsyncImage(
                model = url,
                // The card's summary already names the people; a second avatar
                // announcement made every grouped row read its actors twice.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = name?.trim()?.take(1)?.uppercase(Locale.ROOT).orEmpty().ifBlank { "?" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnreadDot(unread: Boolean) {
    if (!unread) return
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}

@Composable
private fun Timestamp(text: String) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TypeGlyph(kind: NotificationKind, tint: Color, size: androidx.compose.ui.unit.Dp = 15.dp) {
    Icon(
        imageVector = kindIcon(kind),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

internal fun kindIcon(kind: NotificationKind): ImageVector = when (kind) {
    NotificationKind.AIRING -> AppVectorIcons.Tv
    NotificationKind.ACTIVITY_LIKE,
    NotificationKind.ACTIVITY_REPLY_LIKE,
    NotificationKind.THREAD_LIKE,
    NotificationKind.THREAD_COMMENT_LIKE -> AppVectorIcons.ActivityLiked
    NotificationKind.ACTIVITY_REPLY,
    NotificationKind.ACTIVITY_REPLY_SUBSCRIBED,
    NotificationKind.THREAD_COMMENT_REPLY,
    NotificationKind.THREAD_COMMENT_SUBSCRIBED -> AppVectorIcons.ActivityReplies
    NotificationKind.ACTIVITY_MENTION,
    NotificationKind.THREAD_COMMENT_MENTION -> AppVectorIcons.ProfilePerson
    NotificationKind.ACTIVITY_MESSAGE -> AppVectorIcons.ProfileMessages
    NotificationKind.FOLLOWING -> AppVectorIcons.ProfileSocial
    NotificationKind.RELATED_MEDIA_ADDITION,
    NotificationKind.MEDIA_DATA_CHANGE,
    NotificationKind.MEDIA_MERGE,
    NotificationKind.MEDIA_DELETION -> AppVectorIcons.Movie
    NotificationKind.MEDIA_SUBMISSION_UPDATE -> AppVectorIcons.EditEntry
    NotificationKind.UNKNOWN -> AppVectorIcons.Info
}

/** A row width cap, so cards do not stretch on tablets. */
internal val NotificationCardMaxWidth = 680.dp
