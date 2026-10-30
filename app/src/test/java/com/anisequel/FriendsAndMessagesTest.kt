package com.anisequel

import com.anisequel.data.model.ActivityFeedData
import com.anisequel.data.model.GraphQLResponse
import com.anisequel.data.model.MessageNode
import com.anisequel.data.model.ReceivedMessagesData
import com.anisequel.data.model.SentMessagesData
import com.anisequel.data.network.FlexibleStringAdapter
import com.anisequel.data.network.GraphQLQueries
import com.anisequel.ui.viewmodel.MessageFilter
import com.anisequel.ui.viewmodel.UserMessagesViewModel
import com.anisequel.ui.viewmodel.visibleError
import com.anisequel.ui.viewmodel.visibleLoadMoreError
import com.anisequel.ui.viewmodel.visibleMessages
import com.anisequel.ui.viewmodel.visibleSourceError
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The friends feed and the messages tab, pinned at the three layers that can go
 * silently wrong.
 *
 * ## Why these documents need pinning
 *
 * AniList has no single "my messages" query and no recipient filter on
 * `Page.activities`. The two halves are read from different parts of the schema -
 * sent messages from `activities(messengerId:)`, received ones only through
 * `Page.notifications(type_in: [ACTIVITY_MESSAGE])` - and both shapes are
 * unremarkable until they answer an empty page. A field renamed here reads as
 * "you have no messages" rather than as a failure, which is the same class of
 * quiet wrong answer the activity feed's `pageInfo` test exists for.
 *
 * ## Why the merge rule is tested away from the ViewModel
 *
 * The filters are top-level functions precisely so they can be exercised without
 * a coroutine harness. A merged inbox that draws an older message above a newer
 * one, or that shows the sent half under "Received", looks like a data problem
 * and is a derivation problem.
 */
class FriendsAndMessagesTest {

    private val moshi = Moshi.Builder()
        .add(String::class.java, FlexibleStringAdapter)
        .build()

    private inline fun <reified T> parse(json: String): T {
        val responseType = Types.newParameterizedType(GraphQLResponse::class.java, T::class.java)
        return moshi.adapter<GraphQLResponse<T>>(responseType).fromJson(json)!!.data!!
    }

    // ---------------------------------------------------------------------
    // Wire shapes
    // ---------------------------------------------------------------------

    @Test
    fun `the friends feed asks for everyone followed and nothing narrower`() {
        val query = GraphQLQueries.GET_FOLLOWING_ACTIVITY

        assertTrue(query.trimStart().startsWith("query"))
        // The one argument that makes this feed exist: `isFollowing` resolves
        // from the request's token, so there is no id to pass and none to get
        // wrong.
        assertTrue(query.contains("isFollowing: true"))
        assertFalse("the friends feed is not a profile feed", query.contains("userId:"))
        // Each row is a different person, so the author must travel with it.
        assertTrue(query.contains("user { id name avatar { medium large } }"))
        // The same union members the profile feed renders, and never MESSAGE.
        assertTrue(query.contains("... on ListActivity"))
        assertTrue(query.contains("... on TextActivity"))
        assertFalse(query.contains("MESSAGE"))
        assertTrue(query.contains("pageInfo { hasNextPage }"))
        assertTrue(query.contains("perPage: \$perPage"))
    }

    @Test
    fun `sent messages are the viewer's messenger activities`() {
        val query = GraphQLQueries.GET_SENT_MESSAGES

        assertTrue(query.trimStart().startsWith("query"))
        // `Page.activities` has a messenger filter and no recipient filter; this
        // is the only side of the conversation that can be listed from it.
        assertTrue(query.contains("messengerId: \$messengerId"))
        assertTrue(query.contains("type_in: [MESSAGE]"))
        assertTrue(query.contains("... on MessageActivity"))
        // Both ends are selected so a row can name its counterpart without a
        // second request.
        assertTrue(query.contains("recipient {"))
        assertTrue(query.contains("messenger {"))
        assertTrue(query.contains("recipientId"))
        assertTrue(query.contains("messengerId"))
    }

    @Test
    fun `received messages are read through notifications`() {
        val query = GraphQLQueries.GET_RECEIVED_MESSAGES

        assertTrue(query.trimStart().startsWith("query"))
        // The inbox has no activity listing of its own.
        assertTrue(query.contains("notifications(type_in: [ACTIVITY_MESSAGE])"))
        assertTrue(query.contains("... on ActivityMessageNotification"))
        // The notification embeds the whole message; that is what a row draws.
        assertTrue(query.contains("message {"))
        assertTrue(query.contains("createdAt"))
        // Reading the inbox must not mark it read on the server.
        assertFalse(query.contains("resetNotificationCount"))
        // Page.activities would answer nothing here, and asking for both in one
        // document is a validation error.
        assertFalse(query.contains("activities("))
    }

    @Test
    fun `the profile feed still refuses to show site messages`() {
        // `MESSAGE` belongs in the inbox segment and nowhere else; a public
        // profile tab drawing private messages is the failure this guards.
        assertFalse(GraphQLQueries.GET_USER_ACTIVITY.contains("MESSAGE"))
        assertFalse(GraphQLQueries.GET_FOLLOWING_ACTIVITY.contains("MESSAGE"))
    }

    // ---------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------

    @Test
    fun `a sent message parses with both ends of the conversation`() {
        val data = parse<SentMessagesData>(
            """
            {
              "data": {
                "Page": {
                  "pageInfo": { "hasNextPage": true },
                  "activities": [
                    {
                      "__typename": "MessageActivity",
                      "id": 777,
                      "message": "hey, did you finish *Frieren*?",
                      "isPrivate": true,
                      "createdAt": 1788000000,
                      "recipientId": 12,
                      "messengerId": 999,
                      "recipient": { "id": 12, "name": "JesterOW" },
                      "messenger": { "id": 999, "name": "me" }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val page = data.page!!
        val message = page.activities?.single()!!
        assertTrue(page.pageInfo?.hasNextPage == true)
        assertEquals(777, message.id)
        assertEquals("hey, did you finish *Frieren*?", message.message)
        assertEquals(true, message.isPrivate)

        // Direction is decided by the messenger, not by which list the row came
        // from - the two halves are not told apart by the payload.
        assertTrue(message.isSentBy(999))
        assertEquals("JesterOW", message.counterpartFor(999)?.name)
        assertFalse(message.isSentBy(12))
        assertEquals("me", message.counterpartFor(12)?.name)
    }

    @Test
    fun `a received message parses out of its notification wrapper`() {
        val data = parse<ReceivedMessagesData>(
            """
            {
              "data": {
                "Page": {
                  "pageInfo": { "hasNextPage": false },
                  "notifications": [
                    {
                      "id": 1,
                      "activityId": 555,
                      "createdAt": 1788000000,
                      "__typename": "ActivityMessageNotification",
                      "message": {
                        "id": 555,
                        "message": "your tier list is a war crime",
                        "isPrivate": true,
                        "createdAt": 1788000000,
                        "recipientId": 999,
                        "messengerId": 300,
                        "recipient": { "id": 999, "name": "me" },
                        "messenger": { "id": 300, "name": "GuardianLettuce" }
                      }
                    },
                    {
                      "id": 2,
                      "__typename": "FollowingNotification",
                      "message": null
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val notifications = data.page?.notifications.orEmpty()
        assertEquals(2, notifications.size)

        val messageNotification = notifications[0]
        assertFalse(messageNotification.isUnrecognised)
        val message = messageNotification.message!!
        assertEquals(555, message.id)
        assertFalse(message.isSentBy(999))
        assertEquals("GuardianLettuce", message.counterpartFor(999)?.name)

        // A notification type the filter should have kept out is skipped rather
        // than parsed into an empty message card.
        assertTrue(notifications[1].isUnrecognised)
        assertNull(notifications[1].message)
    }

    @Test
    fun `a friends-feed row carries its own author`() {
        val data = parse<ActivityFeedData>(
            """
            {
              "data": {
                "Page": {
                  "pageInfo": { "hasNextPage": true },
                  "activities": [
                    {
                      "__typename": "TextActivity",
                      "id": 9,
                      "text": "backlog-only again",
                      "createdAt": 1788000000,
                      "user": { "id": 12, "name": "JesterOW" }
                    },
                    {
                      "__typename": "ListActivity",
                      "id": 10,
                      "status": "watched episode",
                      "progress": "3",
                      "createdAt": 1788000100,
                      "user": { "id": 13, "name": "GuardianLettuce" },
                      "media": {
                        "id": 21,
                        "type": "ANIME",
                        "title": { "romaji": "Sousou no Frieren" }
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val activities = data.page?.activities.orEmpty()
        assertEquals(2, activities.size)
        assertEquals("JesterOW", activities[0].user?.name)
        assertTrue(activities[0].isTextPost)
        assertEquals("GuardianLettuce", activities[1].user?.name)
        assertFalse(activities[1].isTextPost)
        assertFalse(activities[1].isUnrecognised)
    }

    // ---------------------------------------------------------------------
    // The merge the Messages tab draws
    // ---------------------------------------------------------------------

    private fun message(
        id: Int,
        createdAt: Int,
        messengerId: Int,
        recipientId: Int,
        body: String = "message $id"
    ) = MessageNode(
        id = id,
        message = body,
        createdAt = createdAt,
        messengerId = messengerId,
        recipientId = recipientId
    )

    private fun source(vararg messages: MessageNode) =
        UserMessagesViewModel.SourceState(messages = messages.toList(), loaded = true)

    private val sentHalf = source(
        message(id = 1, createdAt = 100, messengerId = 999, recipientId = 12),
        message(id = 3, createdAt = 300, messengerId = 999, recipientId = 13)
    )
    private val receivedHalf = source(
        message(id = 2, createdAt = 200, messengerId = 12, recipientId = 999),
        message(id = 4, createdAt = 400, messengerId = 13, recipientId = 999)
    )

    @Test
    fun `all merges both halves newest first`() {
        val state = UserMessagesViewModel.UiState(
            filter = MessageFilter.ALL,
            received = receivedHalf,
            sent = sentHalf
        )

        assertEquals(listOf(4, 3, 2, 1), visibleMessages(state).map { it.id })
    }

    @Test
    fun `each filter shows only its own half`() {
        val received = UserMessagesViewModel.UiState(
            filter = MessageFilter.RECEIVED,
            received = receivedHalf,
            sent = sentHalf
        )
        val sent = received.copy(filter = MessageFilter.SENT)

        assertEquals(listOf(2, 4), visibleMessages(received).map { it.id })
        assertEquals(listOf(1, 3), visibleMessages(sent).map { it.id })
    }

    @Test
    fun `an overlapping row is drawn once`() {
        // Not expected from the two endpoints, but a repeated key in a
        // `LazyColumn` is a crash rather than a cosmetic duplicate.
        val overlap = message(id = 5, createdAt = 50, messengerId = 999, recipientId = 12)
        val state = UserMessagesViewModel.UiState(
            filter = MessageFilter.ALL,
            received = source(overlap, message(id = 2, createdAt = 200, messengerId = 12, recipientId = 999)),
            sent = source(overlap)
        )

        assertEquals(listOf(2, 5), visibleMessages(state).map { it.id })
    }

    @Test
    fun `a half failing under all is a notice, not an error page`() {
        // The half that worked is still readable; replacing it with a failure
        // screen would throw away the messages the reader can see.
        val state = UserMessagesViewModel.UiState(
            filter = MessageFilter.ALL,
            received = receivedHalf.copy(loadMoreError = "Couldn't load more messages."),
            sent = sentHalf.copy(error = "Timeout")
        )

        assertNull(visibleError(state))
        assertEquals("Some messages couldn't load.", visibleSourceError(state))
        assertEquals("Couldn't load more messages.", visibleLoadMoreError(state))
    }

    @Test
    fun `nothing loaded is the error page`() {
        val state = UserMessagesViewModel.UiState(
            filter = MessageFilter.RECEIVED,
            received = UserMessagesViewModel.SourceState(error = "Timeout")
        )

        assertEquals("Timeout", visibleError(state))
        assertNull(visibleSourceError(state))
        assertNull(visibleLoadMoreError(state))
    }

    @Test
    fun `a paging failure with no rows is not a footer`() {
        // The footer sits under rows. With none, the failure belongs to the
        // page's own error state - otherwise the tab would draw an empty list
        // with a retry floating in the middle of it.
        val state = UserMessagesViewModel.UiState(
            filter = MessageFilter.SENT,
            sent = UserMessagesViewModel.SourceState(
                loaded = true,
                loadMoreError = "Couldn't load more messages."
            )
        )

        assertNull(visibleLoadMoreError(state))
        assertNull(visibleError(state))
        assertNull(visibleSourceError(state))
    }
}
