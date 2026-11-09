@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.anisequel.ui.screens.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.anisequel.data.extensions.PlayerSettings
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer

/**
 * The player's video surface, controls animation and empty state.
 *
 * This is the whole render tree moved out of `PlayerScreen`. Keeping the state
 * machine there and the tree here means the screen's complexity is the number of
 * decisions it actually makes, not one per control on screen.
 */
@Composable
internal fun PlayerStage(
    mediaUrl: String?,
    useTexture: Boolean,
    exoPlayer: ExoPlayer,
    settings: PlayerSettings,
    buffering: Boolean,
    resolving: Boolean,
    errorMessage: String?,
    controlsVisible: Boolean,
    title: String,
    episode: SourceEpisode?,
    server: SourceServer?,
    decoderLabel: String,
    locked: Boolean,
    canPip: Boolean,
    fullscreen: Boolean,
    isPlaying: Boolean,
    ended: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    position: Long,
    duration: Long,
    buffered: Long,
    scrubFraction: Float?,
    speed: Float,
    muted: Boolean,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onServers: () -> Unit,
    onSettings: () -> Unit,
    onFullscreen: () -> Unit,
    onPip: () -> Unit,
    onLock: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onSpeed: () -> Unit,
    onEpisodes: () -> Unit,
    onSkipIntro: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (mediaUrl.isNullOrBlank()) {
            if (resolving) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(42.dp)
                )
            } else {
                PlayerEmpty(onBack = onBack, message = errorMessage)
            }
            return@Box
        }
        PlayerSurface(
            useTexture = useTexture,
            exoPlayer = exoPlayer,
            settings = settings,
            buffering = buffering,
            errorMessage = errorMessage,
            onToggleControls = onToggleControls,
            onSeekBy = onSeekBy
        ) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                PlayerControlsOverlay(
                    title = title,
                    episode = episode,
                    server = server,
                    decoderLabel = decoderLabel,
                    locked = locked,
                    canPip = canPip,
                    fullscreen = fullscreen,
                    errorMessage = errorMessage,
                    isPlaying = isPlaying,
                    ended = ended,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    position = position,
                    duration = duration,
                    buffered = buffered,
                    scrubFraction = scrubFraction,
                    speed = speed,
                    muted = muted,
                    onBack = onBack,
                    onServers = onServers,
                    onSettings = onSettings,
                    onFullscreen = onFullscreen,
                    onPip = onPip,
                    onLock = onLock,
                    onTogglePlay = onTogglePlay,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onMute = onMute,
                    onScrub = onScrub,
                    onScrubFinished = onScrubFinished,
                    onSpeed = onSpeed,
                    onEpisodes = onEpisodes,
                    onSkipIntro = onSkipIntro,
                    onRetry = onRetry
                )
            }
        }
    }
}
