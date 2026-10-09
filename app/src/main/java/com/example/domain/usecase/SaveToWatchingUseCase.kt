package com.example.domain.usecase

import com.example.data.model.SimpleMediaListEntry
import com.example.data.repository.AniListRepository

/**
 * Adds an anime entry directly to the user's Currently Watching list on AniList.
 */
class SaveToWatchingUseCase(
    private val aniListRepository: AniListRepository
) {
    suspend fun execute(mediaId: Int): Result<SimpleMediaListEntry> {
        return aniListRepository.addToWatching(mediaId)
    }
}
