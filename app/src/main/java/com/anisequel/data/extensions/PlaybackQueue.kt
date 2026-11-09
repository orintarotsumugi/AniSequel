package com.anisequel.data.extensions

/**
 * The episodes the player is walking through, held for the one process.
 *
 * The player route takes no arguments on purpose. A stream URL is long, signed
 * and sometimes carries a query string; putting it in a navigation route means
 * percent-encoding it twice and still losing the queue the "next episode" control
 * needs. The Watch page puts the queue here and navigates, exactly as a media
 * session would, and the Player reads it in the same process. A process death
 * while the player is in the foreground has nothing to restore - the screen
 * returns to the Watch page, which re-resolves the source.
 */
data class PlaybackContext(
    val mediaId: Int,
    val mediaTitle: String,
    val coverUrl: String?,
    val sourceId: String,
    val animeId: String,
    val episodes: List<SourceEpisode>
)

object PlaybackQueue {

    @Volatile
    var context: PlaybackContext? = null
        private set

    @Volatile
    var index: Int = 0
        private set

    @Volatile
    var server: SourceServer? = null
        private set

    /** Where playback resumes, in milliseconds. Zero for a fresh episode. */
    @Volatile
    var startPositionMs: Long = 0L
        private set

    fun prepare(
        context: PlaybackContext,
        index: Int,
        server: SourceServer?,
        startPositionMs: Long = 0L
    ) {
        this.context = context
        this.index = index.coerceIn(0, (context.episodes.size - 1).coerceAtLeast(0))
        this.server = server
        this.startPositionMs = startPositionMs.coerceAtLeast(0L)
    }

    fun episode(): SourceEpisode? = context?.episodes?.getOrNull(index)

    fun hasNext(): Boolean = context?.episodes?.let { index + 1 < it.size } == true

    fun hasPrevious(): Boolean = context?.episodes?.let { index - 1 >= 0 } == true

    fun movePrevious(
        preferredServer: String?,
        preferredQuality: String? = null
    ): SourceEpisode? {
        val context = context ?: return null
        val previous = index - 1
        if (previous !in context.episodes.indices) return null
        index = previous
        startPositionMs = 0L
        val episode = context.episodes[previous]
        server = chooseServer(episode, preferredServer, preferredQuality)
        return episode
    }

    fun moveNext(
        preferredServer: String?,
        preferredQuality: String? = null
    ): SourceEpisode? {
        val context = context ?: return null
        val next = index + 1
        if (next !in context.episodes.indices) return null
        index = next
        startPositionMs = 0L
        val episode = context.episodes[next]
        server = chooseServer(episode, preferredServer, preferredQuality)
        return episode
    }

    /** Jumps to an episode from the player's episode sheet. */
    fun select(
        targetIndex: Int,
        preferredServer: String?,
        preferredQuality: String? = null
    ): SourceEpisode? {
        val context = context ?: return null
        if (targetIndex !in context.episodes.indices) return null
        index = targetIndex
        startPositionMs = 0L
        val episode = context.episodes[targetIndex]
        server = chooseServer(episode, preferredServer, preferredQuality)
        return episode
    }

    fun switchServer(next: SourceServer) {
        server = next
    }

    /**
     * Moves the resume point without changing the episode.
     *
     * The player is disposed while Player Settings is open, and it is rebuilt
     * from this value on the way back. Without this the return trip started the
     * episode over at whatever position the player was first opened with.
     */
    fun resumeAt(positionMs: Long) {
        startPositionMs = positionMs.coerceAtLeast(0L)
    }

    fun clear() {
        context = null
        index = 0
        server = null
        startPositionMs = 0L
    }
}

/**
 * Picks a server by remembered name first, then by preferred quality.
 *
 * A source can rename its files between episodes while keeping the quality, so
 * the quality match is what makes "prefer 1080p" survive episode two. The first
 * file stays the fallback: refusing to play because no 1080p exists would turn a
 * preference into a dead end. Quality strings are compared by their pixels
 * ("1080p", "1920x1080", "FHD" is not attempted) rather than as labels.
 */
internal fun chooseServer(
    episode: SourceEpisode,
    preferredServer: String?,
    preferredQuality: String?
): SourceServer? {
    val servers = episode.servers
    if (servers.isEmpty()) return null
    preferredServer
        ?.takeIf { it.isNotBlank() }
        ?.let { name ->
            servers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        }
    val wanted = qualityPixels(preferredQuality)
    if (wanted != null) {
        servers.firstOrNull { qualityPixels(it.quality) == wanted }?.let { return it }
    }
    return servers.first()
}

private fun qualityPixels(value: String?): Int? {
    val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (raw.equals("auto", ignoreCase = true)) return null
    return Regex("(\\d{3,4})")
        .find(raw)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
}
