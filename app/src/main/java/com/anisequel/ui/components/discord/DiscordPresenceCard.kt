package com.anisequel.ui.components.discord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.domain.discord.DiscordPresence
import com.anisequel.ui.components.AppVectorIcons
import kotlinx.coroutines.delay

/**
 * The Discord card, drawn with Discord's own colours.
 *
 * ## Why it is not themed
 *
 * Every other surface in this app follows Material You, because it is *this* app.
 * This card is a preview of *another* app's window: a themed version would tell
 * the reader their presence will be lavender on a lavender phone, which is not
 * what their friends will see. The colours below are Discord's dark client,
 * deliberately fixed even in light mode.
 *
 * ## What it is allowed to show
 *
 * Only [DiscordPresence] - the same value handed to the relay. There is no second
 * description of the presence in the UI to drift from the one that is published.
 * If a title is hidden, the cover and the title are already absent from the
 * value; this composable does not re-check the switches.
 */
@Composable
fun DiscordPresenceCard(
    presence: DiscordPresence,
    modifier: Modifier = Modifier,
    /** Hides the buttons and shrinks the artwork for the pinned dock. */
    compact: Boolean = false,
    /**
     * Whether a relay has accepted this card.
     *
     * The header used to say LIVE unconditionally, which made a preview of an
     * unconfigured feature claim it was already visible to friends. The dock's
     * status row said "Preview only" two lines away, so the card contradicted its
     * own page. Default false because composition is not delivery.
     */
    live: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = DiscordSurface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(if (compact) 12.dp else 16.dp)) {
            PresenceHeader(live = live)
            Spacer(modifier = Modifier.height(if (compact) 10.dp else 14.dp))
            Row {
                PresenceArtwork(
                    presence = presence,
                    size = if (compact) 72.dp else 86.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = presence.details,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DiscordText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    presence.state?.let { state ->
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = state,
                            style = MaterialTheme.typography.bodySmall,
                            color = DiscordMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(7.dp))
                    PresenceTimer(presence.startTimestampSeconds)
                }
            }
            if (!compact && presence.buttons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presence.buttons.forEach { button ->
                        PresenceButton(label = button.label)
                    }
                }
            }
        }
    }
}

/**
 * The row every Discord presence shares: the app's mark, its name and the
 * "Playing" verb.
 *
 * Kept separate so the compact dock and the full card cannot disagree about what
 * the presence is.
 */
@Composable
private fun PresenceHeader(live: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DiscordBlurple),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.SequelJump,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = "AniSequel",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = DiscordText
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "· Playing",
            style = MaterialTheme.typography.labelSmall,
            color = DiscordMuted
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (live) DiscordOnline else DiscordMuted.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (live) "LIVE" else "PREVIEW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = DiscordMuted
            )
        }
    }
}

@Composable
private fun PresenceArtwork(presence: DiscordPresence, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(DiscordBlurple.copy(alpha = 0.85f), DiscordDeep)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppVectorIcons.Discord,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(size * 0.48f)
        )
        val imageUrl = presence.largeImage
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

/**
 * Discord's own count-up timer.
 *
 * A one-second clock is not an animation and is not what AGENTS section 3's
 * motion rules are about - it is the same value Discord is ticking in the other
 * window, and showing a static "just now" would make the preview look less alive
 * than the product.
 */
@Composable
private fun PresenceTimer(startTimestampSeconds: Long?) {
    if (startTimestampSeconds == null) return
    var nowSeconds by remember {
        mutableLongStateOf(System.currentTimeMillis() / 1_000L)
    }
    LaunchedEffect(startTimestampSeconds) {
        while (true) {
            nowSeconds = System.currentTimeMillis() / 1_000L
            delay(1_000L)
        }
    }
    val elapsed = (nowSeconds - startTimestampSeconds).coerceAtLeast(0L)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = AppVectorIcons.Schedule,
            contentDescription = null,
            tint = DiscordMuted,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "${formatElapsed(elapsed)} elapsed",
            style = MaterialTheme.typography.labelSmall,
            color = DiscordMuted
        )
    }
}

@Composable
private fun PresenceButton(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DiscordButtonSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = DiscordText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** `h:mm:ss` past an hour, `m:ss` below it, matching Discord's own format. */
internal fun formatElapsed(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3_600L
    val minutes = safe % 3_600L / 60L
    val seconds = safe % 60L
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

// Discord's dark client, fixed on purpose. See the composable's KDoc.
private val DiscordSurface = Color(0xFF232428)
private val DiscordDeep = Color(0xFF1B1C20)
private val DiscordText = Color(0xFFF2F3F5)
private val DiscordMuted = Color(0xFFB5BAC1)
private val DiscordBlurple = Color(0xFF5865F2)
private val DiscordOnline = Color(0xFF23A55A)
private val DiscordButtonSurface = Color(0xFF3F4147)
