package com.example.data.repository

import com.example.data.model.GraphQLRequest
import com.example.data.model.MediaListCollection
import com.example.data.model.SimpleMediaListEntry
import com.example.data.model.ViewerProfile
import com.example.data.network.AniListApiService
import com.example.data.network.GraphQLQueries

class AniListRepositoryImpl(
    private val apiService: AniListApiService
) : AniListRepository {

    override suspend fun getViewer(): Result<ViewerProfile> {
        return runCatching {
            val response = apiService.getViewer(
                GraphQLRequest(query = GraphQLQueries.GET_VIEWER)
            )
            val errors = response.errors
            if (!errors.isNullOrEmpty()) {
                val errorMsg = errors.firstOrNull()?.message ?: "Failed to fetch user profile"
                if (errorMsg.contains("invalid", ignoreCase = true) || errorMsg.contains("token", ignoreCase = true)) {
                    throw Exception("Session expired. Please re-authenticate.")
                }
                throw Exception(errorMsg)
            }
            response.data?.viewer ?: throw Exception("Viewer data not found")
        }
    }

    override suspend fun getUserByName(userName: String): Result<ViewerProfile> {
        return runCatching {
            val response = apiService.getUserByName(
                GraphQLRequest(
                    query = GraphQLQueries.GET_USER_BY_NAME,
                    variables = mapOf("userName" to userName.trim())
                )
            )
            val errors = response.errors
            if (!errors.isNullOrEmpty()) {
                throw Exception(errors.firstOrNull()?.message ?: "User '$userName' not found on AniList")
            }
            response.data?.user ?: throw Exception("User '$userName' not found")
        }
    }

    override suspend fun getUserAnimeList(userId: Int): Result<MediaListCollection> {
        return runCatching {
            val response = apiService.getMediaListCollection(
                GraphQLRequest(
                    query = GraphQLQueries.GET_USER_ANIME_LIST,
                    variables = mapOf("userId" to userId)
                )
            )
            val errors = response.errors
            if (!errors.isNullOrEmpty()) {
                throw Exception(errors.firstOrNull()?.message ?: "Failed to fetch anime list")
            }
            response.data?.collection ?: throw Exception("Media list collection is empty")
        }
    }

    override suspend fun getUserAnimeListByUsername(userName: String): Result<MediaListCollection> {
        return runCatching {
            val response = apiService.getMediaListCollection(
                GraphQLRequest(
                    query = GraphQLQueries.GET_USER_ANIME_LIST,
                    variables = mapOf("userName" to userName.trim())
                )
            )
            val errors = response.errors
            if (!errors.isNullOrEmpty()) {
                throw Exception(errors.firstOrNull()?.message ?: "Failed to fetch anime list for '$userName'")
            }
            response.data?.collection ?: throw Exception("Media list collection is empty")
        }
    }

    override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> {
        return runCatching {
            val response = apiService.saveMediaListEntry(
                GraphQLRequest(
                    query = GraphQLQueries.ADD_TO_PLANNING,
                    variables = mapOf("mediaId" to mediaId)
                )
            )
            val errors = response.errors
            if (!errors.isNullOrEmpty()) {
                throw Exception(errors.firstOrNull()?.message ?: "Failed to add to planning list")
            }
            response.data?.entry ?: throw Exception("No response from planning mutation")
        }
    }

    override fun getDemoProfile(): ViewerProfile {
        return DemoDataProvider.getDemoViewer()
    }

    override fun getDemoAnimeList(): MediaListCollection {
        return DemoDataProvider.getDemoMediaList()
    }
}
