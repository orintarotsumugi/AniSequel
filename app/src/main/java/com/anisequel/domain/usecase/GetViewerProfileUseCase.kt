package com.anisequel.domain.usecase

import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.AniListRepository

class GetViewerProfileUseCase(
    private val aniListRepository: AniListRepository
) {
    suspend fun execute(): Result<ViewerProfile> {
        return aniListRepository.getViewer()
    }
}
