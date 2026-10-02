package com.example.data.network

import com.example.data.model.GraphQLRequest
import com.example.data.model.GraphQLResponse
import com.example.data.model.MediaListCollectionData
import com.example.data.model.SaveMediaListEntryData
import com.example.data.model.UserByNameData
import com.example.data.model.ViewerData
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface AniListApiService {

    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getViewer(
        @Body request: GraphQLRequest
    ): GraphQLResponse<ViewerData>

    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getUserByName(
        @Body request: GraphQLRequest
    ): GraphQLResponse<UserByNameData>

    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getMediaListCollection(
        @Body request: GraphQLRequest
    ): GraphQLResponse<MediaListCollectionData>

    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun saveMediaListEntry(
        @Body request: GraphQLRequest
    ): GraphQLResponse<SaveMediaListEntryData>
}
