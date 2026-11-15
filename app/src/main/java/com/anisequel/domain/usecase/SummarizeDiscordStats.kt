package com.anisequel.domain.usecase

import com.anisequel.data.model.MediaListCollection
import com.anisequel.domain.discord.DiscordStats

/**
 * Folds the list the dashboard already downloaded into the three numbers the
 * Discord presence can quote.
 *
 * A use case rather than a calculation inside `DashboardViewModel` because the
 * rules are worth a test and because they are not the dashboard's: "completed"
 * means the same thing here as in the watched count, and "episodes" is progress,
 * not the episode total of the show. Both are easy to get subtly wrong, and both
 * are read by other people.
 *
 * No network, no Android, and no average score - see [DiscordStats] for why a
 * score is deliberately absent.
 */
object SummarizeDiscordStats {

    fun execute(
        collection: MediaListCollection?,
        sequelsFound: Int = 0
    ): DiscordStats {
        val entries = collection?.lists
            .orEmpty()
            .flatMap { group -> group.entries.orEmpty() }
            .distinctBy { entry -> entry.media.id }

        var animeCompleted = 0
        var episodesWatched = 0
        var mangaCompleted = 0
        var chaptersRead = 0

        entries.forEach { entry ->
            val type = entry.media.type
            val isManga = type.equals("MANGA", ignoreCase = true)
            val progress = entry.progress ?: 0
            val completed = entry.status.equals("COMPLETED", ignoreCase = true) ||
                (progress > 0 && (entry.media.episodes ?: 0) > 0 && progress >= entry.media.episodes!!)

            if (isManga) {
                chaptersRead += progress.coerceAtLeast(0)
                if (completed) mangaCompleted++
            } else {
                episodesWatched += progress.coerceAtLeast(0)
                if (completed) animeCompleted++
            }
        }

        return DiscordStats(
            animeCompleted = animeCompleted,
            episodesWatched = episodesWatched,
            mangaCompleted = mangaCompleted,
            chaptersRead = chaptersRead,
            sequelsFound = sequelsFound.coerceAtLeast(0)
        )
    }
}
