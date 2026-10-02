package com.example.data.repository

import com.example.data.model.MediaListCollection
import com.example.data.model.SimpleMediaListEntry
import com.example.data.model.ViewerProfile

interface AniListRepository {
    suspend fun getViewer(): Result<ViewerProfile>
    suspend fun getUserByName(userName: String): Result<ViewerProfile>
    suspend fun getUserAnimeList(userId: Int): Result<MediaListCollection>
    suspend fun getUserAnimeListByUsername(userName: String): Result<MediaListCollection>
    suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry>
    fun getDemoProfile(): ViewerProfile
    fun getDemoAnimeList(): MediaListCollection
}
