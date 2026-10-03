package com.example.data.repository

import com.example.data.model.MediaListCollection
import com.example.data.model.MediaNode
import com.example.data.model.SimpleMediaListEntry
import com.example.data.model.ViewerProfile

interface AniListRepository {
    suspend fun getViewer(): Result<ViewerProfile>
    suspend fun getUserByName(userName: String): Result<ViewerProfile>
    /**
     * The user's full anime list.
     *
     * [forceRefresh] skips the short-lived response cache and re-asks AniList.
     * It exists because a cache that every caller can bypass is not a cache, it
     * is a stale-data generator: pull-to-refresh went through the cached copy
     * and silently did nothing, which is the exact opposite of what the gesture
     * promises. Default values live here and not on the overrides, which is
     * where Kotlin requires them for an interface method.
     */
    suspend fun getUserAnimeList(
        userId: Int,
        forceRefresh: Boolean = false
    ): Result<MediaListCollection>

    /** As [getUserAnimeList], for a profile looked up by name. */
    suspend fun getUserAnimeListByUsername(
        userName: String,
        forceRefresh: Boolean = false
    ): Result<MediaListCollection>

    /**
     * Description, banner and studio for one entry, fetched on demand because
     * the list query omits them (they are only rendered for the entry the user
     * opens, and asking for them up front makes AniList fail on large lists).
     */
    suspend fun getMediaDetail(mediaId: Int): Result<MediaNode>
    suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry>
    fun getDemoProfile(): ViewerProfile
    fun getDemoAnimeList(): MediaListCollection
    fun clearDetailCache() {}
}
