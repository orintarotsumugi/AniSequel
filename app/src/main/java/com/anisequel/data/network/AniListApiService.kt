package com.anisequel.data.network

import com.anisequel.data.model.ActivityDetailData
import com.anisequel.data.model.ActivityFeedData
import com.anisequel.data.model.ActivityLikesData
import com.anisequel.data.model.DeleteActivityReplyData
import com.anisequel.data.model.DeleteMediaListEntryData
import com.anisequel.data.model.FollowersData
import com.anisequel.data.model.FollowingData
import com.anisequel.data.model.GraphQLRequest
import com.anisequel.data.model.GraphQLResponse
import com.anisequel.data.model.MediaCoverPage
import com.anisequel.data.model.MediaListCollectionData
import com.anisequel.data.model.MediaListEntryData
import com.anisequel.data.model.MediaDetailData
import com.anisequel.data.model.CharacterProfileData
import com.anisequel.data.model.SaveActivityReplyData
import com.anisequel.data.model.SaveMediaListEntryData
import com.anisequel.data.model.StudioData
import com.anisequel.data.model.StaffProfileData
import com.anisequel.data.model.ToggleLikeData
import com.anisequel.data.model.UserByNameData
import com.anisequel.data.model.UserOverviewData
import com.anisequel.data.model.ViewerData
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

    /** The viewer's own list entry for one media. Read-only; the editor tab opens on it. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getMediaListEntry(
        @Body request: GraphQLRequest
    ): GraphQLResponse<MediaListEntryData>

    /** Removes one entry from the viewer's list. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun deleteMediaListEntry(
        @Body request: GraphQLRequest
    ): GraphQLResponse<DeleteMediaListEntryData>

    /** Fetches the fields the list query leaves out, for one entry only. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getMediaDetail(
        @Body request: GraphQLRequest
    ): GraphQLResponse<MediaDetailData>

    /**
     * One studio and one page of its work.
     *
     * The same `POST /` as everything else, with a different document - AniList is
     * a single GraphQL endpoint, so the queries are told apart by what they ask
     * for rather than by their path.
     */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getStudio(
        @Body request: GraphQLRequest
    ): GraphQLResponse<StudioData>

    /**
     * Cover art for many media in one round trip.
     *
     * `Map<String, ...>` rather than a generated class because the response keys
     * are aliases the query builds at runtime (`m0`, `m1`, ...), so the shape of
     * the answer is not known until the request is written.
     *
     * The value is a `Page` because the same request written against the `Media`
     * root field answers **404** - taking every cover in the bio with it - as soon
     * as one id in the batch has no media. See [MediaCoverPage].
     */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getMediaCovers(
        @Body request: GraphQLRequest
    ): GraphQLResponse<Map<String, MediaCoverPage>>

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

    /** One activity and its replies. Opened with the comments sheet. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getActivityReplies(
        @Body request: GraphQLRequest
    ): GraphQLResponse<ActivityDetailData>

    /** The people who liked one activity. Opened with the likers sheet. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getActivityLikes(
        @Body request: GraphQLRequest
    ): GraphQLResponse<ActivityLikesData>

    /** Likes or un-likes an activity. Requires a session. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun toggleActivityLike(
        @Body request: GraphQLRequest
    ): GraphQLResponse<ToggleLikeData>

    /** Likes or un-likes a comment on an activity. Requires a session. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun toggleActivityReplyLike(
        @Body request: GraphQLRequest
    ): GraphQLResponse<ToggleLikeData>

    /** Posts a comment on an activity. Requires a session. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun saveActivityReply(
        @Body request: GraphQLRequest
    ): GraphQLResponse<SaveActivityReplyData>

    /**
     * Deletes one of the viewer's own comments. Requires a session.
     *
     * Editing goes through [saveActivityReply] - one mutation type on the
     * server, a create and an update - so only the delete has a method to itself.
     */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun deleteActivityReply(
        @Body request: GraphQLRequest
    ): GraphQLResponse<DeleteActivityReplyData>

    /** One character and the titles they appear in. Opened by the cast row. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getCharacterProfile(
        @Body request: GraphQLRequest
    ): GraphQLResponse<CharacterProfileData>

    /** One staff member and the titles they worked on. Opened by the crew row. */
    @POST("/")
    @Headers("Content-Type: application/json", "Accept: application/json")
    suspend fun getStaffProfile(
        @Body request: GraphQLRequest
    ): GraphQLResponse<StaffProfileData>
}
