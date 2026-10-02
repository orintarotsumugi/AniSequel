package com.example.domain.usecase

import com.example.data.model.SimpleMediaListEntry
import com.example.data.repository.AniListRepository

class SaveToPlanningUseCase(
    private val aniListRepository: AniListRepository
) {
    suspend fun execute(mediaId: Int): Result<SimpleMediaListEntry> {
        return aniListRepository.addToPlanning(mediaId)
    }
}
