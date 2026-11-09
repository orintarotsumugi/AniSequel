@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.anisequel.ui.screens.watch

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.util.Rational
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import com.anisequel.data.extensions.DecoderMode
import com.anisequel.data.extensions.PlaybackQueue
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
import com.anisequel.data.extensions.WatchPreferences
import com.anisequel.data.extensions.WatchProgress
import com.anisequel.ui.components.AppVectorIcons
import java.util.Locale

/** The reference player's lavender, not the app's Material primary. */
internal val PlayerAccent = Color(0xFFBCC9FF)
private val PlayerYellow = Color(0xFFFFD84D)
private val PlayerTrack = Color(0xFF2C2C33)
private val PlayerPanel = Color(0xFF17171C).copy(alpha = 0.92f)
private val PlayerChip = Color(0xFF26262D).copy(alpha = 0.92f)

/**
 * The compact top band: back, the title, episode/quality chips and the three
 * utility controls. It is intentionally smaller than the old bar and uses
 * rounded squares rather than pill buttons, matching the reference.
 */
@Composable
internal fun PlayerTopBar(
    title: String,
    episode: SourceEpisode?,
    server: SourceServer?,
    decoderLabel: String,
    locked: Boolean,
    canPip: Boolean,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onPip: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            shape = RoundedCornerShape(13.dp),
            color = PlayerPanel,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    AppVectorIcons.Back,
                    contentDescription = "Close player",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PlayerAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerChipText(episodeChip(episode), PlayerChip)
                PlayerChipText(qualityChip(server), PlayerChip)
            }
        }
        Spacer(Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PlayerChipText(decoderBadge(decoderLabel), PlayerPanel)
            SmallRoundAction(
                icon = if (locked) AppVectorIcons.LockOpen else AppVectorIcons.Lock,
                description = if (locked) "Unlock controls" else "Lock controls",
                onClick = onLock
            )
            if (canPip) {
                SmallRoundAction(
                    icon = AppVectorIcons.PictureInPicture,
                    description = "Picture in picture",
                    onClick = onPip
                )
            }
            SmallRoundAction(
                icon = AppVectorIcons.Settings,
                description = "Player settings",
                onClick = onSettings
            )
        }
    }
}

/**
 * The three central transport controls.
 *
 * `prev` and `next` move the in-process episode queue. The ten-second skips are
 * double-tap gestures on the video surface, which is the gesture viewers already
 * expect and keeps two controls off the centre of a small screen.
 */
@Composable
internal fun PlayerCenterControls(
    isPlaying: Boolean,
    ended: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportButton(
            icon = AppVectorIcons.SkipPrevious,
            description = "Previous episode",
            enabled = hasPrevious,
            onClick = onPrevious
        )
        Surface(
            onClick = onTogglePlay,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    when {
                        ended -> AppVectorIcons.Refresh
                        isPlaying -> AppVectorIcons.Pause
                        else -> AppVectorIcons.Play
                    },
                    contentDescription = when {
                        ended -> "Replay"
                        isPlaying -> "Pause"
                        else -> "Play"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
        }
        TransportButton(
            icon = AppVectorIcons.SkipNext,
            description = "Next episode",
            enabled = hasNext,
            onClick = onNext
        )
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = PlayerPanel,
        modifier = Modifier.size(46.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * The bottom band: a flat scrubbable timeline with intro/outro marks, the time
 * pills, and one row of compact controls. No Material Slider, because its
 * track/thumb geometry cannot draw the three segments the reference uses.
 */
@Composable
internal fun PlayerBottomBar(
    position: Long,
    buffered: Long,
    duration: Long,
    scrubFraction: Float?,
    muted: Boolean,
    speed: Float,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onSpeed: () -> Unit,
    onServers: () -> Unit,
    onEpisodes: () -> Unit,
    onFullscreen: () -> Unit,
    onMute: () -> Unit,
    onSkipIntro: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = scrubFraction ?: if (duration > 0L) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bufferedFraction = if (duration > 0L) {
        (buffered.toFloat() / duration).coerceIn(fraction, 1f)
    } else {
        0f
    }
    val showSkipIntro = duration > 120_000L && position in 1L..90_000L
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (showSkipIntro) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    onClick = onSkipIntro,
                    shape = RoundedCornerShape(16.dp),
                    color = PlayerPanel
                ) {
                    Text(
                        "+85",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        PlayerProgress(
            fraction = fraction,
            bufferedFraction = bufferedFraction,
            duration = duration,
            onScrub = onScrub,
            onScrubFinished = onScrubFinished
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimePill(formatClock((fraction * duration).toLong()), compact = true)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallRoundAction(
                    icon = AppVectorIcons.EpisodeGrid,
                    description = "Episodes",
                    onClick = onEpisodes
                )
                SmallRoundAction(
                    icon = AppVectorIcons.Server,
                    description = "Quality and servers",
                    onClick = onServers
                )
                SmallRoundAction(
                    icon = AppVectorIcons.Fullscreen,
                    description = "Fullscreen",
                    onClick = onFullscreen
                )
                SpeedPill(speed = speed, onClick = onSpeed)
            }
            Spacer(Modifier.weight(1f))
            SmallRoundAction(
                icon = if (muted) AppVectorIcons.VolumeOff else AppVectorIcons.Volume,
                description = if (muted) "Unmute" else "Mute",
                onClick = onMute
            )
            Spacer(Modifier.width(4.dp))
            TimePill(formatClock(duration), compact = true)
        }
    }
}

@Composable
private fun PlayerProgress(
    fraction: Float,
    bufferedFraction: Float,
    duration: Long,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(duration) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.coerceAtLeast(1).toFloat()
                    fun at(x: Float): Float = (x / width).coerceIn(0f, 1f)
                    onScrub(at(down.position.x))
                    var pressed = down.pressed
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        pressed = change.pressed
                        if (pressed) {
                            onScrub(at(change.position.x))
                            change.consume()
                        }
                    }
                    onScrubFinished()
                }
            }
    ) {
        val trackHeight = 7.dp.toPx()
        val y = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f)
        drawRoundRect(
            color = PlayerTrack,
            topLeft = Offset(0f, y),
            size = Size(size.width, trackHeight),
            cornerRadius = radius
        )
        drawRoundRect(
            color = PlayerAccent.copy(alpha = 0.34f),
            topLeft = Offset(0f, y),
            size = Size(size.width * bufferedFraction.coerceIn(0f, 1f), trackHeight),
            cornerRadius = radius
        )
        drawRoundRect(
            color = PlayerAccent,
            topLeft = Offset(0f, y),
            size = Size(size.width * fraction.coerceIn(0f, 1f), trackHeight),
            cornerRadius = radius
        )
        if (duration > 120_000L) {
            val intro = (85_000f / duration).coerceIn(0f, 0.35f)
            val outro = (75_000f / duration).coerceIn(0f, 0.35f)
            drawRoundRect(
                color = PlayerYellow,
                topLeft = Offset(0f, y),
                size = Size(size.width * intro, trackHeight),
                cornerRadius = radius
            )
            drawRoundRect(
                color = PlayerYellow,
                topLeft = Offset(size.width * (1f - outro), y),
                size = Size(size.width * outro, trackHeight),
                cornerRadius = radius
            )
        }
        drawCircle(
            color = Color.White,
            radius = 6.dp.toPx(),
            center = Offset(size.width * fraction.coerceIn(0f, 1f), size.height / 2f)
        )
    }
}

@Composable
private fun SpeedPill(speed: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = PlayerChip,
        modifier = Modifier.height(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                formatSpeed(speed),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    }
}

@Composable
private fun SmallRoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = PlayerPanel,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun PlayerChipText(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(7.dp), color = color) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun TimePill(label: String, compact: Boolean = false) {
    Surface(shape = RoundedCornerShape(20.dp), color = PlayerPanel) {
        Text(
            label,
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = Color.White,
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 6.dp else 7.dp
            )
        )
    }
}

@Composable
internal fun PlayerError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.82f),
        modifier = modifier.padding(24.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Icon(AppVectorIcons.Warning, contentDescription = null, tint = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                "This file did not play",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f)
            )
            TextButton(onClick = onRetry) {
                Text("Retry", color = Color.White)
            }
        }
    }
}

@Composable
internal fun PlayerEmpty(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Nothing to play", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Open the Watch page and pick an episode.",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onBack) { Text("Back", color = Color.White) }
    }
}

internal fun buildRenderersFactory(
    context: Context,
    decoder: DecoderMode
): DefaultRenderersFactory = DefaultRenderersFactory(context).apply {
    setEnableDecoderFallback(true)
    if (decoder != DecoderMode.AUTO) {
        setMediaCodecSelector(object : MediaCodecSelector {
            override fun getDecoderInfos(
                mimeType: String,
                requiresSecureDecoder: Boolean,
                requiresTunnelingDecoder: Boolean
            ): List<MediaCodecInfo> {
                val all = MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
                val filtered = all.filter { info ->
                    if (decoder == DecoderMode.SOFTWARE) info.softwareOnly else info.hardwareAccelerated
                }
                // A phone with only software H.264 would otherwise hard-fail just
                // because the reader asked for "mediaCodec"; falling back keeps the
                // episode playable and the failure would be visible in logs rather
                // than a black screen.
                return filtered.ifEmpty { all }
            }
        })
    }
}

internal fun applyEnhancement(
    view: PlayerView,
    settings: com.anisequel.data.extensions.PlayerSettings
) {
    if (!settings.enhance) {
        view.setRenderEffect(null)
        return
    }
    val saturation = settings.shaderProfile.saturation
    val contrast = settings.shaderProfile.contrast
    val translate = (1f - contrast) * 128f
    val matrix = ColorMatrix().apply {
        setSaturation(saturation)
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
    view.setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix)))
}

/** Enters Android's picture-in-picture mode, or returns false on a device that refuses. */
internal fun enterPip(activity: Activity?): Boolean {
    val target = activity ?: return false
    return runCatching {
        target.enterPictureInPictureMode(
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
        )
    }.getOrDefault(false)
}

internal fun savePlayback(
    watchPreferences: WatchPreferences,
    player: ExoPlayer,
    atEnd: Boolean = false
) {
    val context = PlaybackQueue.context ?: return
    val episode = PlaybackQueue.episode() ?: return
    val duration = player.duration.takeIf { it > 0L } ?: return
    val position = if (atEnd) duration else player.currentPosition.coerceIn(0L, duration)
    watchPreferences.saveProgress(
        WatchProgress(
            mediaId = context.mediaId,
            sourceId = context.sourceId,
            animeId = context.animeId,
            animeTitle = context.mediaTitle,
            coverUrl = context.coverUrl,
            episodeId = episode.id,
            episodeName = episode.name,
            episodeNumber = episode.number,
            positionMs = position,
            durationMs = duration
        )
    )
}

internal fun episodeChip(episode: SourceEpisode?): String =
    episode?.let { "Episode ${it.number}" } ?: "Episode"

internal fun qualityChip(server: SourceServer?): String =
    server?.quality?.takeIf { it.isNotBlank() }
        ?: server?.name?.takeIf { it.isNotBlank() }
        ?: "Source"

internal fun decoderBadge(decoder: String): String = when {
    decoder.contains("software", ignoreCase = true) -> "SW"
    decoder.contains("auto", ignoreCase = true) -> "AUTO"
    else -> "HW+"
}

private fun formatClock(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ENGLISH, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds)
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}\u00d7" else "${speed}\u00d7"

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
