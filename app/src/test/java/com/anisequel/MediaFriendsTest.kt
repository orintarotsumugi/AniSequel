package com.anisequel

import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaFriendEntry
import com.anisequel.data.model.UserAvatar
import com.anisequel.data.model.progressLabel
import com.anisequel.data.model.scoreLabel
import com.anisequel.data.model.sortedForDisplay
import com.anisequel.data.model.toMediaFriend
import com.anisequel.data.network.GraphQLQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "friends who watched this" card's query and its mapping.
 *
 * ## Why the query is asserted rather than just the mapping
 *
 * Three of the card's facts live in the GraphQL document and nowhere else, and
 * each one is silently wrong when it is missing:
 *
 *  - `isFollowing: true` is what narrows `Page.mediaList` to the viewer's
 *    follows. Without it the connection answers with *everybody's* entries for
 *    the title, which is a page of strangers' faces and one large response.
 *  - `type` is what keeps `anime/1` from answering with `manga/1`'s entries;
 *    AniList numbers the two namespaces independently.
 *  - `score(format: POINT_10_DECIMAL)` normalises every rating onto one scale.
 *    The bare field is in each user's own format, so a 85 and an 8.5 would sit
 *    in the same row as if they were comparable.
 *
 * None of the three can be caught by a screenshot, and the app has no network in
 * its tests, so the document itself is the thing to pin.
 *
 * The mapping half pins the two rules that decide whether a friend is drawn at
 * all, and the ordering the card reads top to bottom.
 */
class MediaFriendsTest {

    private fun entry(
        status: String? = "COMPLETED",
        score: Double? = null,
        progress: Int? = 0,
        progressVolumes: Int? = null,
        user: FollowUser? = FollowUser(id = 1, name = "Mika")
    ) = MediaFriendEntry(
        status = status,
        score = score,
        progress = progress,
        progressVolumes = progressVolumes,
        user = user
    )

    private fun friend(
        name: String = "Mika",
        score: Double? = null,
        progress: Int = 0,
        userId: Int = 1
    ) = MediaFriend(
        userId = userId,
        name = name,
        avatarUrl = null,
        score = score,
        progress = progress,
        progressVolumes = null
    )

    @Test
    fun `the query asks only for the viewer's follows, on one scale, for one namespace`() {
        val query = GraphQLQueries.getMediaFriendsQuery("ANIME")

        assertTrue(
            "without `isFollowing: true` the connection answers with everybody's " +
                "entries for the title, not the viewer's follows:\n$query",
            query.contains("isFollowing: true")
        )
        assertTrue(
            "the bare `score` field is in each user's own format, so the row would " +
                "compare an 85 with an 8.5:\n$query",
            query.contains("score(format: POINT_10_DECIMAL)")
        )
        assertTrue(
            "media ids are only unique inside a namespace:\n$query",
            query.contains("type: \$type")
        )
        assertTrue(
            "the type has to be declared as a variable or the document cannot take " +
                "one:\n$query",
            query.contains("\$type: MediaType")
        )
        assertTrue(
            "perPage 50 is the connection's documented maximum:\n$query",
            query.contains("perPage: 50")
        )
    }

    @Test
    fun `a title with no type does not send one it does not have`() {
        val query = GraphQLQueries.getMediaFriendsQuery(null)

        assertFalse(
            "an untyped node cannot name a namespace, so the document must not " +
                "declare the variable it cannot fill:\n$query",
            query.contains("\$type")
        )
        assertFalse(query.contains("type: \$type"))
        assertTrue(query.contains("isFollowing: true"))
    }

    @Test
    fun `planning is not the same as having watched it`() {
        assertNull(
            "a plan is the answer \"no, not yet\" with an avatar on it",
            entry(status = "PLANNING").toMediaFriend()
        )
        // The status is a raw string off the wire, and the rest of the app compares
        // AniList's enums case-insensitively; a lower-case plan must not slip past
        // the filter and draw an avatar for a title the friend has not opened.
        assertNull(entry(status = "planning").toMediaFriend())
        // Everything else means the friend has seen some of it, including the two
        // statuses that sound like they do not.
        assertNotNull(entry(status = "DROPPED").toMediaFriend())
        assertNotNull(entry(status = "PAUSED").toMediaFriend())
    }

    @Test
    fun `an entry whose owner AniList no longer returns is dropped`() {
        assertNull(entry(user = null).toMediaFriend())
    }

    @Test
    fun `an unrated entry is zero on the wire and nothing on the card`() {
        assertNull(friend(score = 0.0).scoreLabel)
        assertEquals("8", friend(score = 8.0).scoreLabel)
        assertEquals("8.5", friend(score = 8.5).scoreLabel)
    }

    @Test
    fun `the best rating leads, and unrated friends come after the rated ones`() {
        val sorted = listOf(
            friend(name = "Unrated", score = null, progress = 24, userId = 1),
            friend(name = "Good", score = 8.0, progress = 3, userId = 2),
            friend(name = "Best", score = 9.5, progress = 1, userId = 3),
            friend(name = "Also best, further", score = 9.5, progress = 12, userId = 4)
        ).sortedForDisplay()

        assertEquals(
            listOf("Also best, further", "Best", "Good", "Unrated"),
            sorted.map { it.name }
        )
    }

    @Test
    fun `progress names the total when the title has one`() {
        val friend = friend(progress = 12)

        assertEquals("12 / 24 eps", friend.progressLabel(isManga = false, total = 24))
        assertEquals("12 eps", friend.progressLabel(isManga = false, total = null))
        // Zero is AniList's "not published yet", not a total of nothing.
        assertEquals("12 eps", friend.progressLabel(isManga = false, total = 0))
        assertEquals("12 / 24 chs", friend.progressLabel(isManga = true, total = 24))
    }

    @Test
    fun `the avatar prefers the large copy and falls back to the medium one`() {
        val withBoth = entry(
            user = FollowUser(
                id = 1,
                name = "Mika",
                avatar = UserAvatar(medium = "m.jpg", large = "l.jpg")
            )
        )
        val mediumOnly = entry(
            user = FollowUser(id = 2, name = "Ren", avatar = UserAvatar(medium = "m.jpg"))
        )

        assertEquals("l.jpg", withBoth.toMediaFriend()?.avatarUrl)
        assertEquals("m.jpg", mediumOnly.toMediaFriend()?.avatarUrl)
    }
}
