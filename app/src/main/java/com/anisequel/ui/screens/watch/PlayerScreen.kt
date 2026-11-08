@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.anisequel.ui.screens.watch

import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.anisequel.R
import com.anisequel.data.extensions.PlaybackQueue
import com.anisequel.data.extensions.PlayerPreferences
import com.anisequel.data.extensions.RendererMode
import com.anisequel.data.extensions.WatchPreferences
import com.anisequel.ui.components.rememberFullHeightSheetState
import kotlinx.coroutines.delay

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * The video player.
 *
 * Media3 owns decode and the surface; Compose owns every control. The stock
 * PlayerView controller is turned off on purpose: the buttons here also write the
 * resume point, switch servers and advance the queue, and a second controller
 * would be a second set of timers and visibility rules over the same surface.
 *
 * The queue lives in [PlaybackQueue], so this screen takes no navigation argument.
 * The player is rebuilt when the episode, server or decoder changes, which makes
 * "next episode" and "another server" the same operation - replace the media item
 * - without a second player instance leaking its surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    watchPreferences: WatchPreferences,
    playerPreferences: PlayerPreferences,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by playerPreferences.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val previousOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val queueContext = remember { PlaybackQueue.context }
    var currentEpisode by remember {
        mutableStateOf(PlaybackQueue.episode())
    }
    var currentServer by remember {
        mutableStateOf(PlaybackQueue.server ?: currentEpisode?.servers?.firstOrNull())
    }
    var pendingStartMs by remember { mutableLongStateOf(PlaybackQueue.startPositionMs) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var position by remember { mutableLongStateOf(pendingStartMs) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showServers by rememberSaveable { mutableStateOf(false) }
    val serverSheetState = rememberFullHeightSheetState()

    val useTexture = settings.enhance || settings.renderer == RendererMode.TEXTURE

    // The system bars and the activity orientation are process-wide, not this
    // screen's; the previous values are restored on dispose so leaving the player
    // mid-fullscreen does not leave the rest of the app landscape.
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = previousOrientation
            if (activity != null) {
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
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
    BackHandler(enabled = fullscreen) { fullscreen = false }

    val mediaUrl = currentServer?.url
    val exoPlayer = remember(currentEpisode?.id, mediaUrl, settings.decoder, settings.renderer, settings.enhance) {
        ExoPlayer.Builder(context, buildRenderersFactory(context, settings.decoder)).build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            if (!mediaUrl.isNullOrBlank()) {
                setMediaItem(MediaItem.fromUri(mediaUrl))
                prepare()
                if (pendingStartMs > 0L) seekTo(pendingStartMs)
                playWhenReady = true
            }
        }
    }

    LaunchedEffect(currentEpisode?.id, mediaUrl) {
        ended = false
        errorMessage = null
        controlsVisible = true
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            position = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            buffering = exoPlayer.playbackState == Player.STATE_BUFFERING
            delay(500)
        }
    }

    // A five-second write is frequent enough that a crash loses at most one
    // tick, and sparse enough that playback is not doing a JSON serialisation
    // every frame. The dispose handler does the final write.
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(5_000)
            savePlayback(watchPreferences, exoPlayer)
        }
    }

    DisposableEffect(exoPlayer) {
        // The effect is keyed on the player, so this is the episode that player
        // was built for. Without capturing it, disposing after "next episode"
        // would ask the queue for the *new* episode while holding the old
        // player's position, and overwrite the new episode's resume point.
        val playerEpisodeId = currentEpisode?.id
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    savePlayback(watchPreferences, exoPlayer, atEnd = true)
                    val next = PlaybackQueue.moveNext(watchPreferences.preferredServer.value)
                    if (next != null) {
                        pendingStartMs = 0L
                        currentEpisode = next
                        currentServer = PlaybackQueue.server ?: next.servers.firstOrNull()
                    } else {
                        ended = true
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.message ?: "This file could not be played."
                buffering = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            if (PlaybackQueue.episode()?.id == playerEpisodeId) {
                savePlayback(watchPreferences, exoPlayer)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3_500)
            controlsVisible = false
        }
    }

    val hasNext = PlaybackQueue.hasNext()
    val playNext = {
        savePlayback(watchPreferences, exoPlayer, atEnd = true)
        val next = PlaybackQueue.moveNext(watchPreferences.preferredServer.value)
        if (next != null) {
            pendingStartMs = 0L
            currentEpisode = next
            currentServer = PlaybackQueue.server ?: next.servers.firstOrNull()
        } else {
            ended = true
        }
        Unit
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen")
    ) {
        if (mediaUrl.isNullOrBlank()) {
            PlayerEmpty(onBack = onBack)
        } else {
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
                        indication = null
                    ) { controlsVisible = !controlsVisible }
            )

            if (buffering && errorMessage == null) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(42.dp)
                )
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
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
                        title = queueContext?.mediaTitle ?: "Episode",
                        episode = currentEpisode,
                        server = currentServer,
                        fullscreen = fullscreen,
                        onBack = onBack,
                        onServers = { showServers = true },
                        onSettings = {
                            savePlayback(watchPreferences, exoPlayer)
                            PlaybackQueue.resumeAt(exoPlayer.currentPosition)
                            onOpenSettings()
                        },
                        onFullscreen = { fullscreen = !fullscreen },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    if (errorMessage != null) {
                        PlayerError(
                            message = errorMessage.orEmpty(),
                            onRetry = {
                                errorMessage = null
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        PlayerCenterControls(
                            isPlaying = isPlaying,
                            ended = ended,
                            onTogglePlay = {
                                if (ended) {
                                    ended = false
                                    exoPlayer.seekTo(0L)
                                    exoPlayer.play()
                                } else if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                                controlsVisible = true
                            },
                            onRewind = {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                                controlsVisible = true
                            },
                            onForward = {
                                val max = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10_000L).coerceAtMost(max))
                                controlsVisible = true
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    PlayerBottomBar(
                        position = position,
                        duration = duration,
                        scrubFraction = scrubFraction,
                        onScrub = { scrubFraction = it },
                        onScrubFinished = {
                            val target = ((scrubFraction ?: 0f) * duration).toLong()
                            exoPlayer.seekTo(target)
                            scrubFraction = null
                            controlsVisible = true
                        },
                        speed = speed,
                        hasNext = hasNext,
                        onSpeed = {
                            speed = SPEEDS[(SPEEDS.indexOf(speed) + 1) % SPEEDS.size]
                            exoPlayer.playbackParameters = PlaybackParameters(speed)
                            controlsVisible = true
                        },
                        onNext = { playNext() },
                        onServers = { showServers = true },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    if (showServers && currentEpisode != null) {
        PlayerServerSheet(
            sheetState = serverSheetState,
            episode = currentEpisode!!,
            current = currentServer,
            onDismiss = { showServers = false },
            onChoose = { server ->
                savePlayback(watchPreferences, exoPlayer)
                pendingStartMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                PlaybackQueue.switchServer(server)
                currentServer = server
                showServers = false
            }
        )
    }
}
