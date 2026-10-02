package com.example.domain.usecase

import com.example.data.model.ViewerProfile
import com.example.data.repository.AniListRepository

class GetViewerProfileUseCase(
    private val aniListRepository: AniListRepository
) {
    suspend fun execute(): Result<ViewerProfile> {
        return aniListRepository.getViewer()
    }
}
