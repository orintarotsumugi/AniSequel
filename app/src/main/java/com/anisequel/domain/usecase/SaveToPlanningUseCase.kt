package com.anisequel.domain.usecase

import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.repository.AniListRepository

class SaveToPlanningUseCase(
    private val aniListRepository: AniListRepository
) {
    suspend fun execute(mediaId: Int): Result<SimpleMediaListEntry> {
        return aniListRepository.addToPlanning(mediaId)
    }
}
