package com.anisequel.ui.screens.watch

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import com.anisequel.data.extensions.PlaybackContext
import com.anisequel.data.extensions.PlaybackQueue
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
import com.anisequel.data.extensions.WatchProgress

/**
 * The three player sheets, lifted out of `PlayerScreen`.
 *
 * The screen already owns the player session state; keeping three `if (show...)`
 * blocks and the queue branches in the same function made CodeFactor report it as
 * a 31-branch method. These hosts own only the visible/hidden condition and the
 * queue mutation for their own sheet, so the screen keeps the state and the
 * reference to the player, and this file keeps the chooser logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSpeedSheetHost(
    visible: Boolean,
    sheetState: SheetState,
    speed: Float,
    presets: List<Float>,
    onSpeedChange: (Float) -> Unit,
    onAddPreset: (Float) -> Unit,
    onRemovePreset: (Float) -> Unit,
    onRestorePresets: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    PlayerSpeedSheet(
        sheetState = sheetState,
        speed = speed,
        presets = presets,
        onSpeedChange = onSpeedChange,
        onAddPreset = onAddPreset,
        onRemovePreset = onRemovePreset,
        onRestorePresets = onRestorePresets,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerEpisodesSheetHost(
    visible: Boolean,
    sheetState: SheetState,
    queueContext: PlaybackContext?,
    currentEpisodeId: String?,
    progressByMedia: Map<Int, WatchProgress>,
    showArtwork: Boolean,
    preferredServer: String?,
    preferredQuality: String?,
    onArtworkChange: (Boolean) -> Unit,
    onBeforeSelect: () -> Unit,
    onSelected: (SourceEpisode, SourceServer?) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible || currentEpisodeId == null) return
    val queue = queueContext ?: return
    PlayerEpisodesSheet(
        sheetState = sheetState,
        seriesTitle = queue.mediaTitle,
        episodes = queue.episodes,
        currentIndex = queue.episodes.indexOfFirst { it.id == currentEpisodeId },
        currentProgress = progressByMedia[queue.mediaId],
        showArtwork = showArtwork,
        onArtworkChange = onArtworkChange,
        onChoose = { index ->
            onBeforeSelect()
            PlaybackQueue.select(index, preferredServer, preferredQuality)
                ?.let { selected ->
                    onSelected(
                        selected,
                        PlaybackQueue.server ?: selected.servers.firstOrNull()
                    )
                }
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerServerSheetHost(
    visible: Boolean,
    sheetState: SheetState,
    seriesTitle: String,
    episode: SourceEpisode?,
    current: SourceServer?,
    onPreferredServer: (String?) -> Unit,
    onBeforeSelect: () -> Unit,
    onSelected: (SourceServer) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible || episode == null) return
    PlayerServerSheet(
        sheetState = sheetState,
        seriesTitle = seriesTitle,
        episode = episode,
        current = current,
        onDismiss = onDismiss,
        onChoose = { server, remember ->
            onBeforeSelect()
            PlaybackQueue.switchServer(server)
            onPreferredServer(if (remember) server.name else null)
            onSelected(server)
            onDismiss()
        }
    )
}
