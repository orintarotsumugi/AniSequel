package com.example.data.network

import com.example.data.model.ActivityFeedData
import com.example.data.model.FollowersData
import com.example.data.model.FollowingData
import com.example.data.model.GraphQLRequest
import com.example.data.model.GraphQLResponse
import com.example.data.model.MediaListCollectionData
import com.example.data.model.MediaDetailData
import com.example.data.model.SaveMediaListEntryData
import com.example.data.model.UserByNameData
import com.example.data.model.UserOverviewData
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

    /** Fetches the fields the list query leaves out, for one entry only. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getMediaDetail(
        @Body request: GraphQLRequest
    ): GraphQLResponse<MediaDetailData>

    /** Bio, list statistics, favourites and aggregate stats. Profile screen only. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getUserOverview(
        @Body request: GraphQLRequest
    ): GraphQLResponse<UserOverviewData>

    /** One page of the person's list activity. Opened with the Activity tab. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getUserActivity(
        @Body request: GraphQLRequest
    ): GraphQLResponse<ActivityFeedData>

    /**
     * Followers, and [getUserFollowing] separately.
     *
     * Two methods rather than one with a flag because `Page` accepts a single data
     * field - asking for `followers` and `following` in one document is a GraphQL
     * validation error ("Page query can only accept 1 child field"), not a merged
     * result. Checked, not assumed.
     */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getUserFollowers(
        @Body request: GraphQLRequest
    ): GraphQLResponse<FollowersData>

    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getUserFollowing(
        @Body request: GraphQLRequest
    ): GraphQLResponse<FollowingData>
}
