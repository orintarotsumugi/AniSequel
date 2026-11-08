@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.anisequel.ui.screens.watch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
internal fun PlayerTopBar(
    title: String,
    episode: SourceEpisode?,
    server: SourceServer?,
    fullscreen: Boolean,
    onBack: () -> Unit,
    onServers: () -> Unit,
    onSettings: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fullscreen) Modifier else Modifier.statusBarsPadding())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(AppVectorIcons.Back, contentDescription = "Close player", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                playerSubtitle(episode),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            onClick = onServers,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.16f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    AppVectorIcons.CloudRepo,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    server?.name ?: "Server",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
        IconButton(onClick = onSettings) {
            Icon(AppVectorIcons.Settings, contentDescription = "Player settings", tint = Color.White)
        }
        IconButton(onClick = onFullscreen) {
            Icon(
                if (fullscreen) AppVectorIcons.FullscreenExit else AppVectorIcons.Fullscreen,
                contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen",
                tint = Color.White
            )
        }
    }
}

@Composable
internal fun PlayerCenterControls(
    isPlaying: Boolean,
    ended: Boolean,
    onTogglePlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onRewind,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.42f),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    AppVectorIcons.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Surface(
            onClick = onTogglePlay,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (ended) AppVectorIcons.Refresh else if (isPlaying) AppVectorIcons.Pause else AppVectorIcons.Play,
                    contentDescription = if (ended) "Replay" else if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Surface(
            onClick = onForward,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.42f),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    AppVectorIcons.Forward10,
                    contentDescription = "Forward 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
internal fun PlayerBottomBar(
    position: Long,
    duration: Long,
    scrubFraction: Float?,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    speed: Float,
    hasNext: Boolean,
    onSpeed: () -> Unit,
    onNext: () -> Unit,
    onServers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = scrubFraction ?: if (duration > 0L) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Slider(
            value = fraction,
            onValueChange = onScrub,
            onValueChangeFinished = onScrubFinished
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${formatClock((fraction * duration).toLong())} / ${formatClock(duration)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
            ControlTextButton(label = "${formatSpeed(speed)}\u00d7", onClick = onSpeed)
            ControlTextButton(label = "Servers", onClick = onServers)
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(
                    AppVectorIcons.SkipNext,
                    contentDescription = "Next episode",
                    tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
private fun ControlTextButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
        color = Color.Black.copy(alpha = 0.78f),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerServerSheet(
    sheetState: SheetState,
    episode: SourceEpisode,
    current: SourceServer?,
    onDismiss: () -> Unit,
    onChoose: (SourceServer) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                "Select Server",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Episode ${episode.number} \u00b7 ${episode.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(episode.servers, key = { it.id }) { server ->
                    val playing = server.id == current?.id
                    Surface(
                        onClick = { onChoose(server) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (playing) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                AppVectorIcons.Play,
                                contentDescription = null,
                                tint = if (playing) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                server.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (playing) {
                                Text(
                                    "Playing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
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

private fun formatClock(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ENGLISH, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds)
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

private fun playerSubtitle(episode: SourceEpisode?): String {
    if (episode == null) return ""
    val label = "Episode ${episode.number}"
    return if (episode.name.isNotBlank() && episode.name != label) {
        "$label \u00b7 ${episode.name}"
    } else {
        label
    }
}

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
