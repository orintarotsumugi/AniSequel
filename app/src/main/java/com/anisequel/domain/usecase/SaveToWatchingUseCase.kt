package com.anisequel.domain.usecase

import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.repository.AniListRepository

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
