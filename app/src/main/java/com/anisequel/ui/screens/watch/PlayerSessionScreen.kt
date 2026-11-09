@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.anisequel.ui.screens.watch

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.anisequel.data.extensions.PlaybackQueue
import com.anisequel.data.extensions.chooseServer
import com.anisequel.data.extensions.PlayerPreferences
import com.anisequel.data.extensions.RendererMode
import com.anisequel.data.extensions.WatchPreferences
import com.anisequel.ui.components.rememberFullHeightSheetState
import kotlinx.coroutines.delay

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
 *
 * Composition is in `PlayerStage.kt`, listener/timers in `PlayerBindings.kt` and
 * the server chooser in `PlayerServerSheet.kt`. This function is the session
 * state and the commands over it.
 */
@Composable
fun PlayerScreen(
    watchPreferences: WatchPreferences,
    playerPreferences: PlayerPreferences,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by playerPreferences.settings.collectAsStateWithLifecycle()
    val speedPresets by playerPreferences.speedPresets.collectAsStateWithLifecycle()
    val sourceSettings by watchPreferences.sourceSettings.collectAsStateWithLifecycle()
    val showArtwork by watchPreferences.showArtwork.collectAsStateWithLifecycle()
    val progressMap by watchPreferences.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val previousOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val queueContext = remember { PlaybackQueue.context }
    var currentEpisode by remember { mutableStateOf(PlaybackQueue.episode()) }
    var currentServer by remember {
        mutableStateOf(
            PlaybackQueue.server
                ?: currentEpisode?.let {
                    chooseServer(
                        it,
                        watchPreferences.preferredServer.value,
                        sourceSettings.preferredQuality
                    )
                }
        )
    }
    var pendingStartMs by remember { mutableLongStateOf(PlaybackQueue.startPositionMs) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var position by remember { mutableLongStateOf(pendingStartMs) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableFloatStateOf(playerPreferences.playbackSpeed.value) }
    var muted by remember { mutableStateOf(false) }
    var showServers by rememberSaveable { mutableStateOf(false) }
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    var showEpisodes by rememberSaveable { mutableStateOf(false) }
    val serverSheetState = rememberFullHeightSheetState()
    val speedSheetState = rememberFullHeightSheetState()
    val episodesSheetState = rememberFullHeightSheetState()

    val useTexture = settings.enhance || settings.renderer == RendererMode.TEXTURE
    val canPip = activity?.packageManager
        ?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    val mediaUrl = currentServer?.url

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
    PlayerFullscreenEffect(activity = activity, fullscreen = fullscreen)
    BackHandler(enabled = fullscreen || locked) {
        if (locked) locked = false else fullscreen = false
    }

    val loadControl = remember(settings.cacheMinutes) {
        val readAhead = settings.cacheMinutes * 60_000
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                readAhead.coerceAtLeast(30_000),
                2_500,
                5_000
            )
            .build()
    }

    val exoPlayer = remember(
        currentEpisode?.id,
        mediaUrl,
        settings.decoder,
        settings.renderer,
        settings.enhance,
        loadControl
    ) {
        ExoPlayer.Builder(context, buildRenderersFactory(context, settings.decoder))
            .setLoadControl(loadControl)
            .build()
            .apply {
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
        buffering = true
        controlsVisible = true
    }

    LaunchedEffect(speed, settings.audioPitchCorrection, exoPlayer) {
        exoPlayer.playbackParameters = PlaybackParameters(
            speed,
            // Pitch correction keeps the voice at 1.0. Turning it off lets the
            // pitch rise with the speed, which is what the reference row means.
            if (settings.audioPitchCorrection) 1f else speed
        )
    }

    PlayerBindings(
        player = exoPlayer,
        watchPreferences = watchPreferences,
        episodeId = currentEpisode?.id,
        onSnapshot = { snapshot ->
            position = snapshot.position
            duration = snapshot.duration
            buffered = snapshot.buffered
            isPlaying = snapshot.isPlaying
            buffering = snapshot.buffering
        },
        onEnded = {
            val next = PlaybackQueue.moveNext(
                watchPreferences.preferredServer.value,
                sourceSettings.preferredQuality
            )
            if (next != null) {
                pendingStartMs = 0L
                currentEpisode = next
                currentServer = PlaybackQueue.server ?: next.servers.firstOrNull()
            } else {
                ended = true
            }
        },
        onError = { message ->
            errorMessage = message
            buffering = false
            controlsVisible = true
        }
    )

    LaunchedEffect(controlsVisible, isPlaying, locked) {
        if (controlsVisible && isPlaying && !locked) {
            delay(3_500)
            controlsVisible = false
        }
    }

    val hasNext = PlaybackQueue.hasNext()
    val hasPrevious = PlaybackQueue.hasPrevious()

    val playNext = {
        savePlayback(watchPreferences, exoPlayer, atEnd = true)
        val next = PlaybackQueue.moveNext(
            watchPreferences.preferredServer.value,
            sourceSettings.preferredQuality
        )
        if (next != null) {
            pendingStartMs = 0L
            currentEpisode = next
            currentServer = PlaybackQueue.server ?: next.servers.firstOrNull()
        } else {
            ended = true
        }
        Unit
    }

    val playPrevious = {
        savePlayback(watchPreferences, exoPlayer)
        val previous = PlaybackQueue.movePrevious(
            watchPreferences.preferredServer.value,
            sourceSettings.preferredQuality
        )
        if (previous != null) {
            pendingStartMs = 0L
            currentEpisode = previous
            currentServer = PlaybackQueue.server ?: previous.servers.firstOrNull()
        }
        Unit
    }

    PlayerStage(
        mediaUrl = mediaUrl,
        useTexture = useTexture,
        exoPlayer = exoPlayer,
        settings = settings,
        buffering = buffering,
        errorMessage = errorMessage,
        controlsVisible = controlsVisible,
        title = queueContext?.mediaTitle ?: "Episode",
        episode = currentEpisode,
        server = currentServer,
        decoderLabel = settings.decoder.label,
        locked = locked,
        canPip = canPip,
        fullscreen = fullscreen,
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
        onBack = {
            savePlayback(watchPreferences, exoPlayer)
            onBack()
        },
        // Locked means a pocket tap must not hide the only way back to Unlock,
        // and must not seek either. Before this, tapping a locked player hid the
        // top bar and left Back as the only escape.
        onToggleControls = {
            togglePlayerControls(locked, controlsVisible) { controlsVisible = it }
        },
        onSeekBy = { delta ->
            seekPlayerBy(exoPlayer, delta, locked) { controlsVisible = true }
        },
        onServers = { showServers = true },
        onSettings = {
            savePlayback(watchPreferences, exoPlayer)
            PlaybackQueue.resumeAt(exoPlayer.currentPosition)
            onOpenSettings()
        },
        onFullscreen = { fullscreen = !fullscreen },
        onPip = { enterPip(activity) },
        onLock = {
            locked = !locked
            controlsVisible = true
        },
        onTogglePlay = {
            togglePlayerPlayback(exoPlayer, isPlaying, ended) { ended = false }
            controlsVisible = true
        },
        onPrevious = {
            playPrevious()
            controlsVisible = true
        },
        onNext = {
            playNext()
            controlsVisible = true
        },
        onMute = {
            muted = !muted
            applyPlayerMute(exoPlayer, muted)
            controlsVisible = true
        },
        onScrub = { scrubFraction = it },
        onScrubFinished = {
            seekPlayerToScrub(exoPlayer, scrubFraction, duration)
            scrubFraction = null
            controlsVisible = true
        },
        onSpeed = {
            showSpeed = true
            controlsVisible = true
        },
        onEpisodes = {
            showEpisodes = true
            controlsVisible = true
        },
        onSkipIntro = {
            skipPlayerIntro(exoPlayer)
            controlsVisible = true
        },
        onRetry = {
            errorMessage = null
            exoPlayer.prepare()
            exoPlayer.play()
        },
        modifier = modifier.testTag("player_screen")
    )

    PlayerSpeedSheetHost(
        visible = showSpeed,
        sheetState = speedSheetState,
        speed = speed,
        presets = speedPresets,
        onSpeedChange = { value ->
            speed = value
            playerPreferences.setPlaybackSpeed(value)
        },
        onAddPreset = { preset ->
            playerPreferences.setSpeedPresets(speedPresets + preset)
        },
        onRemovePreset = { preset ->
            playerPreferences.setSpeedPresets(speedPresets - preset)
        },
        onRestorePresets = playerPreferences::resetSpeedPresets,
        onDismiss = { showSpeed = false }
    )

    PlayerEpisodesSheetHost(
        visible = showEpisodes,
        sheetState = episodesSheetState,
        queueContext = queueContext,
        currentEpisodeId = currentEpisode?.id,
        progressByMedia = progressMap,
        showArtwork = showArtwork,
        preferredServer = watchPreferences.preferredServer.value,
        preferredQuality = sourceSettings.preferredQuality,
        onArtworkChange = watchPreferences::setShowArtwork,
        onBeforeSelect = {
            savePlayback(watchPreferences, exoPlayer)
            pendingStartMs = 0L
        },
        onSelected = { selected, selectedServer ->
            currentEpisode = selected
            currentServer = selectedServer
        },
        onDismiss = { showEpisodes = false }
    )

    PlayerServerSheetHost(
        visible = showServers,
        sheetState = serverSheetState,
        seriesTitle = queueContext?.mediaTitle ?: "Episode",
        episode = currentEpisode,
        current = currentServer,
        onPreferredServer = watchPreferences::setPreferredServer,
        onBeforeSelect = {
            savePlayback(watchPreferences, exoPlayer)
            pendingStartMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        },
        onSelected = { server -> currentServer = server },
        onDismiss = { showServers = false }
    )
}
