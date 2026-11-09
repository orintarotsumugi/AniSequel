package com.anisequel.ui.screens.watch

import com.anisequel.data.extensions.PlaybackContext
import com.anisequel.data.extensions.PlaybackQueue
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceMapping
import com.anisequel.data.extensions.SourceServer
import com.anisequel.data.extensions.WatchProgress
import com.anisequel.data.extensions.chooseServer
import com.anisequel.ui.navigation.WatchAnime

/**
 * Starts one episode on the process-scoped queue.
 *
 * This is a plain function rather than four branches inside `WatchScreen` for the
 * same reason the source parsing is: it is a decision with no layout, and the
 * composable should only have to call it. A null mapping means the source was
 * never matched, which is a "nothing to play" state rather than an error.
 */
internal fun launchWatchPlayback(
    mapping: SourceMapping?,
    media: WatchAnime,
    coverUrl: String?,
    episodes: List<SourceEpisode>,
    episode: SourceEpisode,
    server: SourceServer?,
    startMs: Long,
    onPlay: () -> Unit
) {
    if (mapping == null) return
    PlaybackQueue.prepare(
        context = PlaybackContext(
            mediaId = media.id,
            mediaTitle = media.title,
            coverUrl = coverUrl,
            sourceId = mapping.sourceId,
            animeId = mapping.animeId,
            episodes = episodes
        ),
        index = episodes.indexOf(episode).coerceAtLeast(0),
        server = server,
        startPositionMs = startMs
    )
    onPlay()
}

/**
 * Decides whether an episode can start, needs a server pick, or has no file.
 *
 * "Auto" means the reader has not chosen a preferred server. With one file there
 * is nothing to choose; with several, the reference UI asks, and the player's own
 * server sheet is the same chooser. The decision lives here so the composable
 * does not carry the four-way condition and its two callbacks.
 */
internal fun requestWatchEpisode(
    episode: SourceEpisode,
    startMs: Long,
    preferredServer: String?,
    preferredQuality: String?,
    onNoServers: () -> Unit,
    onNeedsServer: (SourceEpisode, Long) -> Unit,
    onPlay: (SourceServer) -> Unit
) {
    if (episode.servers.isEmpty()) {
        onNoServers()
        return
    }
    val chosen = chooseServer(episode, preferredServer, preferredQuality)
    val hasPreference = preferredServer != null ||
        preferredQuality?.equals("Auto", ignoreCase = true) == false
    if (episode.servers.size > 1 && !hasPreference) {
        onNeedsServer(episode, startMs)
    } else {
        onPlay(chosen ?: episode.servers.first())
    }
}

/**
 * Opens the saved Continue Watching point, or falls back to the title search.
 *
 * A progress record can outlive the episode list it was written from - a source
 * can drop a file, or the reader can switch to a source with a different id
 * space. The bar must not be a dead tap in that case; the one useful fallback is
 * the search that can find the title again.
 */
internal fun resumeWatchEpisode(
    progress: WatchProgress?,
    episodes: List<SourceEpisode>,
    onEpisode: (SourceEpisode, Long) -> Unit,
    onMissing: () -> Unit
) {
    val point = progress ?: return
    val episode = episodes.firstOrNull { it.id == point.episodeId }
    if (episode != null) {
        onEpisode(episode, point.positionMs)
    } else {
        onMissing()
    }
}
