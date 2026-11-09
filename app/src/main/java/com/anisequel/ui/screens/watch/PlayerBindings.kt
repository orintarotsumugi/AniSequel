@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.anisequel.ui.screens.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.anisequel.data.extensions.PlaybackQueue
import com.anisequel.data.extensions.WatchPreferences
import kotlinx.coroutines.delay

/** One tick of player state, so the screen does not define five callbacks. */
internal data class PlayerSnapshot(
    val position: Long,
    val duration: Long,
    val buffered: Long,
    val isPlaying: Boolean,
    val buffering: Boolean
)

/**
 * Binds an [ExoPlayer] to the Compose screen: polls the clock, writes resume
 * points and translates Media3's listener into three callbacks.
 *
 * Extracted from `PlayerScreen` because the screen's own body is state and
 * decisions; a listener object plus two polling loops beneath it made the
 * composable large enough for CodeFactor to flag it, while every branch here is
 * about the player and not about layout.
 */
@Composable
internal fun PlayerBindings(
    player: ExoPlayer,
    watchPreferences: WatchPreferences,
    episodeId: String?,
    onSnapshot: (PlayerSnapshot) -> Unit,
    onEnded: () -> Unit,
    onError: (String) -> Unit
) {
    LaunchedEffect(player) {
        while (true) {
            onSnapshot(
                PlayerSnapshot(
                    position = player.currentPosition.coerceAtLeast(0L),
                    duration = player.duration.coerceAtLeast(0L),
                    buffered = player.bufferedPosition.coerceAtLeast(0L),
                    isPlaying = player.isPlaying,
                    buffering = player.playbackState == Player.STATE_BUFFERING
                )
            )
            delay(500)
        }
    }

    // A five-second write is frequent enough that a crash loses at most one
    // tick, and sparse enough that playback is not doing a JSON serialisation
    // every frame. Disposal does the final write.
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            savePlayback(watchPreferences, player)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    savePlayback(watchPreferences, player, atEnd = true)
                    onEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onError(error.message ?: "This file could not be played.")
            }
        }
        player.addListener(listener)
        onDispose {
            // Only the episode this player was built for may be written here.
            // After "next episode" the queue already points at the new one, and
            // the old player's position would overwrite the new episode's point.
            if (PlaybackQueue.episode()?.id == episodeId) {
                savePlayback(watchPreferences, player)
            }
            player.removeListener(listener)
            player.release()
        }
    }
}
