package com.example.data.repository

import com.example.data.model.MediaListCollection
import com.example.data.model.MediaNode
import com.example.data.model.SimpleMediaListEntry
import com.example.data.model.ViewerProfile

interface AniListRepository {
    suspend fun getViewer(): Result<ViewerProfile>
    suspend fun getUserByName(userName: String): Result<ViewerProfile>
    suspend fun getUserAnimeList(userId: Int): Result<MediaListCollection>
    suspend fun getUserAnimeListByUsername(userName: String): Result<MediaListCollection>

    /**
     * Description, banner and studio for one entry, fetched on demand because
     * the list query omits them (they are only rendered for the entry the user
     * opens, and asking for them up front makes AniList fail on large lists).
     */
    suspend fun getMediaDetail(mediaId: Int): Result<MediaNode>
    suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry>
    fun getDemoProfile(): ViewerProfile
    fun getDemoAnimeList(): MediaListCollection
}
