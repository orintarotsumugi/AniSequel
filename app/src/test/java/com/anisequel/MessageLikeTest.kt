package com.anisequel

import com.anisequel.data.network.GraphQLQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A direct message is likeable, and this app was not liking messages.
 *
 * ## What was broken
 *
 * `MessageNode` has carried `isLiked` and `likeCount` since the inbox was
 * written, and every inbox query selects both. The cards drew a direction chip, a
 * privacy chip, a comment count, a link out and a dismiss button - and no heart.
 * The API paid for the state on every page and the app read none of it.
 *
 * The mutation was worse than missing. `TOGGLE_ACTIVITY_LIKE` fragments
 * `ListActivity` and `TextActivity`, so sending a message id through it selects
 * **no member of `LikeableUnion`**: the server answers the union with an object
 * that has no fields, and the decoder reads `isLiked` as false and `likeCount` as
 * zero. A like that worked would come back looking like an unlike with no likes.
 *
 * `LikeableUnion`'s third activity member is `MessageActivity` - verified against
 * the schema in `ReplyLikeQueryTest`, which is also where the reply document and
 * its separate `ACTIVITY_REPLY` namespace are pinned. This test is about the
 * activity document and the inbox that calls it.
 */
class MessageLikeTest {

    @Test
    fun `the activity like fragments the third activity member too`() {
        val mutation = GraphQLQueries.TOGGLE_ACTIVITY_LIKE

        listOf("ListActivity", "TextActivity", "MessageActivity").forEach { member ->
            assertTrue(
                "`$member` is a member of `LikeableUnion`; unfragmented it comes " +
                    "back with no fields and the heart reads as unliked at zero:\n$mutation",
                mutation.contains("... on $member")
            )
        }
        assertTrue(
            "the union-shaped fields all three need are the ones this control " +
                "draws:\n$mutation",
            listOf("isLiked", "likeCount").all { mutation.contains(it) }
        )
    }

    @Test
    fun `the mutation is balanced`() {
        val mutation = GraphQLQueries.TOGGLE_ACTIVITY_LIKE
        assertEquals("braces", mutation.count { it == '{' }, mutation.count { it == '}' })
        assertEquals("parens", mutation.count { it == '(' }, mutation.count { it == ')' })
    }

    /**
     * The whole chain: the ViewModel sends the message id, the tab passes the row
     * it drew, and the card draws a heart.
     *
     * Every link in this chain is a place the feature can be silently absent - a
     * heart declared with no callback, a callback never wired, a ViewModel method
     * with no caller - and none of those fail to compile.
     */
    @Test
    fun `the heart, the tab and the view model are wired to each other`() {
        fun source(relative: String) = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/$relative"
        ).readText()

        val card = source("ui/screens/profile/ProfileMessageCard.kt")
        val tab = source("ui/screens/profile/ProfileMessagesTab.kt")
        val page = source("ui/screens/profile/ProfileActivityPage.kt")
        val viewModel = source("ui/viewmodel/UserMessagesViewModel.kt")

        assertTrue(
            "the card has to take the callback, not invent one",
            card.contains("onToggleLike: () -> Unit")
        )
        assertTrue(
            "and draw the shared like control with it",
            card.contains("EngagementAction(") && card.contains("onClick = onToggleLike")
        )
        assertTrue(
            "a null `isLiked` means AniList did not answer for a viewer; the " +
                "heart must be disabled rather than drawn unliked. The demo has " +
                "no session either, so it is disabled there too",
            card.contains("likeEnabled = canLike && message.isLiked != null && !likePending")
        )
        assertTrue(
            "the tab passes the callback down",
            tab.contains("onToggleLike = { onToggleLike(message) }") &&
                tab.contains("likePending = message.id in state.pendingLikes") &&
                tab.contains("canLike = state.canLike")
        )
        assertTrue(
            "the page aims it at the ViewModel, by the message's own id",
            page.contains("messagesViewModel.toggleMessageLike(it.id)")
        )
        assertTrue(
            "the ViewModel owns the optimistic write",
            viewModel.contains("fun toggleMessageLike(messageId: Int)")
        )
        assertTrue(
            "and sends it through the activity like, which is the type a " +
                "`MessageActivity` belongs to",
            viewModel.contains("aniListRepository.toggleActivityLike(messageId)")
        )
        assertTrue(
            "one request per row at a time, or a double tap toggles twice",
            viewModel.contains("if (messageId in current.pendingLikes) return")
        )
    }

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }
}
