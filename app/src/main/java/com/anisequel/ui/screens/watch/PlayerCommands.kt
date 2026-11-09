package com.anisequel.ui.screens.watch

import androidx.media3.exoplayer.ExoPlayer

/**
 * The branchy half of the player's command callbacks.
 *
 * `PlayerScreen` owns the session state and the callbacks' effects; these helpers
 * own the lock checks and null fallbacks. Before they existed the same `if
 * (!locked)` and `takeIf { ... } ?: Long.MAX_VALUE` branches sat inline in the
 * screen, and CodeFactor counted them toward one 31-branch method.
 */
internal fun togglePlayerControls(
    locked: Boolean,
    visible: Boolean,
    onChange: (Boolean) -> Unit
) {
    if (!locked) onChange(!visible)
}

internal fun seekPlayerBy(
    player: ExoPlayer,
    deltaMs: Long,
    locked: Boolean,
    onControls: () -> Unit
) {
    if (locked) return
    val max = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
    player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, max))
    onControls()
}

internal fun seekPlayerToScrub(player: ExoPlayer, fraction: Float?, durationMs: Long) {
    val target = ((fraction ?: 0f) * durationMs).toLong()
    player.seekTo(target)
}

internal fun applyPlayerMute(player: ExoPlayer, muted: Boolean) {
    player.volume = if (muted) 0f else 1f
}

internal fun skipPlayerIntro(player: ExoPlayer) {
    val target = (player.currentPosition + 85_000L)
        .coerceAtMost(player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE)
    player.seekTo(target)
}
