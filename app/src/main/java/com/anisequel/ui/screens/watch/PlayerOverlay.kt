@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.anisequel.ui.screens.watch

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.anisequel.R
import com.anisequel.data.extensions.PlayerSettings
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer

/** The video surface and everything drawn over it. */
@Composable
internal fun PlayerSurface(
    useTexture: Boolean,
    exoPlayer: ExoPlayer,
    settings: PlayerSettings,
    buffering: Boolean,
    errorMessage: String?,
    onToggleControls: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit
) {
    AndroidView(
        factory = { ctx ->
            val layout = if (useTexture) {
                R.layout.player_view_texture
            } else {
                R.layout.player_view_surface
            }
            (LayoutInflater.from(ctx).inflate(layout, null) as PlayerView).apply {
                player = exoPlayer
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                applyEnhancement(this, settings)
            }
        },
        update = { view ->
            view.player = exoPlayer
            applyEnhancement(view, settings)
        },
        modifier = Modifier.fillMaxSize()
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleControls
            )
    )

    if (buffering && errorMessage == null) {
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .size(42.dp)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        overlay()
    }
}

/**
 * The controls, extracted from the player state.
 *
 * The player's main composable owns players, listeners and timers; this owns only
 * the three bands of controls. Splitting them is what keeps the state machine
 * readable without moving any of the callbacks away from the player that owns
 * them.
 */
@Composable
internal fun PlayerControlsOverlay(
    title: String,
    episode: SourceEpisode?,
    server: SourceServer?,
    fullscreen: Boolean,
    errorMessage: String?,
    isPlaying: Boolean,
    ended: Boolean,
    position: Long,
    duration: Long,
    scrubFraction: Float?,
    speed: Float,
    hasNext: Boolean,
    onBack: () -> Unit,
    onServers: () -> Unit,
    onSettings: () -> Unit,
    onFullscreen: () -> Unit,
    onTogglePlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onSpeed: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.72f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.8f)
                    )
                )
            )
    ) {
        PlayerTopBar(
            title = title,
            episode = episode,
            server = server,
            fullscreen = fullscreen,
            onBack = onBack,
            onServers = onServers,
            onSettings = onSettings,
            onFullscreen = onFullscreen,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (errorMessage != null) {
            PlayerError(
                message = errorMessage,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            PlayerCenterControls(
                isPlaying = isPlaying,
                ended = ended,
                onTogglePlay = onTogglePlay,
                onRewind = onRewind,
                onForward = onForward,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        PlayerBottomBar(
            position = position,
            duration = duration,
            scrubFraction = scrubFraction,
            onScrub = onScrub,
            onScrubFinished = onScrubFinished,
            speed = speed,
            hasNext = hasNext,
            onSpeed = onSpeed,
            onNext = onNext,
            onServers = onServers,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Play, pause, or replay from zero after a finished file. */
internal fun togglePlayerPlayback(
    player: ExoPlayer,
    isPlaying: Boolean,
    ended: Boolean,
    onEnded: () -> Unit
) {
    if (ended) {
        onEnded()
        player.seekTo(0L)
        player.play()
    } else if (isPlaying) {
        player.pause()
    } else {
        player.play()
    }
}

/** Hides and shows the system bars with the player's fullscreen state. */
@Composable
internal fun PlayerFullscreenEffect(activity: Activity?, fullscreen: Boolean) {
    LaunchedEffect(fullscreen) {
        if (activity == null) return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
