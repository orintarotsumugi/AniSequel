package com.anisequel.ui.components.discord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.alpha
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

@Composable
fun DiscordPresenceCard(
    presence: DiscordPresence,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    live: Boolean = false,
    connecting: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = DiscordSurface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(if (compact) 12.dp else 16.dp)) {
            PresenceHeader(
                live = live,
                connecting = connecting,
                appName = presence.activityName
            )
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
                    AnimatedVisibility(
                        visible = !presence.state.isNullOrBlank(),
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(120))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = presence.state.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = DiscordMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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

@Composable
private fun PresenceHeader(live: Boolean, connecting: Boolean, appName: String) {
    // Only while connecting. The transition used to be built on every card and
    // ticked forever, so a preview sitting on the settings page animated a dot
    // nobody was looking at - the one indefinite animation in the app that was not
    // gated on whether it could be seen. Conditional composition also stops the
    // tick when the connection settles.
    val alpha = if (connecting) {
        val pulse = rememberInfiniteTransition(label = "discord_live_pulse")
        val animated by pulse.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        animated
    } else {
        1f
    }
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
            text = appName.ifBlank { "AniSequel" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = DiscordText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "· Activity",
            style = MaterialTheme.typography.labelSmall,
            color = DiscordMuted
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .alpha(if (connecting) alpha else 1f)
                    .background(
                        when {
                            live -> DiscordOnline
                            connecting -> DiscordBlurple
                            else -> DiscordMuted.copy(alpha = 0.6f)
                        }
                    )
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = when {
                    live -> "LIVE"
                    connecting -> "CONNECTING"
                    else -> "PREVIEW"
                },
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

private val DiscordSurface = Color(0xFF232428)
private val DiscordDeep = Color(0xFF1B1C20)
private val DiscordText = Color(0xFFF2F3F5)
private val DiscordMuted = Color(0xFFB5BAC1)
private val DiscordBlurple = Color(0xFF5865F2)
private val DiscordOnline = Color(0xFF23A55A)
private val DiscordButtonSurface = Color(0xFF3F4147)
