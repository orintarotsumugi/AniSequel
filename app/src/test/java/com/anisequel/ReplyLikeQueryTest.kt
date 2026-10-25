package com.anisequel

import com.anisequel.data.network.GraphQLQueries
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.AniListRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A comment is likeable, and this app was not liking comments.
 *
 * ## What was broken
 *
 * `ActivityReply` has carried `isLiked` and `likeCount` since the comment sheet was
 * written, `GET_ACTIVITY_REPLIES` selects both, and **nothing read either**. A reply
 * came back from AniList carrying its own like state, the app paid for it in every
 * thread it opened, and drew no heart at all — so a reader could read a comment, agree
 * with it, and have no way to say so.
 *
 * It read as a deliberate scope cut rather than a gap, which is why it survived: the
 * toggle mutation's KDoc said a reply like was "not sent yet" and would "add an
 * `ActivityReply` fragment here", while the payload type's KDoc claimed the document
 * already "fragments the two members this app can toggle (an activity and an activity
 * reply)". One of those two was wrong and nothing in the repository could tell which.
 *
 * ## Verified against the live schema before it was written
 *
 * `LikeableUnion`'s members are `ListActivity`, `TextActivity`, `MessageActivity`,
 * `ActivityReply`, `Thread` and `ThreadComment`. `LikeableType` is `THREAD`,
 * `THREAD_COMMENT`, `ACTIVITY`, `ACTIVITY_REPLY`. So a reply is likeable, and
 * `ACTIVITY_REPLY` is the value that says so. The mutation was then sent to the live
 * API and answered **401 Unauthorized** — the authentication wall, which is what a
 * valid document gets. The same endpoint answers **400** with
 * `Unknown argument "perPage"` for a malformed one, so the two are distinguishable and
 * only the former means the document is right.
 *
 * ## Why the type argument is not optional
 *
 * Reply ids and activity ids are independent namespaces, so an untyped `ToggleLikeV2`
 * is ambiguous in the same way `Media(id:)` was — see `MediaDetailQueryTest`. That is
 * the argument this pins.
 */
class ReplyLikeQueryTest {

    @Test
    fun `a reply is liked through the reply type, not the activity type`() {
        val mutation = GraphQLQueries.TOGGLE_REPLY_LIKE

        assertTrue("it is a mutation:\n$mutation", mutation.trimStart().startsWith("mutation"))
        assertTrue("`ToggleLikeV2` is the documented name:\n$mutation", mutation.contains("ToggleLikeV2"))
        assertTrue(
            "`ACTIVITY_REPLY` is the `LikeableType` that says " +
                "`ActivityReply`:\n$mutation",
            mutation.contains("type: ACTIVITY_REPLY")
        )
        assertTrue(
            "and the selection has to fragment `ActivityReply`, or it matches no " +
                "member of the union and every field comes back null:\n$mutation",
            mutation.contains("... on ActivityReply")
        )
    }

    /**
     * The two id namespaces do not overlap, so this is the same trap as an untyped
     * `Media(id:)`: the server would resolve an id against whichever it preferred.
     */
    @Test
    fun `the id is declared and used under the name the request sends`() {
        val mutation = GraphQLQueries.TOGGLE_REPLY_LIKE

        assertTrue(
            "the repository sends it as `replyId`, so the document has to declare " +
                "that name:\n$mutation",
            mutation.contains("replyId: Int")
        )
        assertEquals(
            "a document that declares a variable it never uses is a 400, and one that " +
                "uses one it never declared is the same failure:\n$mutation",
            1,
            mutation.split("replyId: Int").size - 1
        )
        assertTrue(mutation.contains("ToggleLikeV2(id: \$replyId"))
    }

    @Test
    fun `the three fields the control draws are all selected`() {
        val mutation = GraphQLQueries.TOGGLE_REPLY_LIKE

        listOf("id", "isLiked", "likeCount").forEach { field ->
            assertTrue(
                "the heart swaps its glyph on `isLiked` and prints `likeCount`; " +
                    "`$field` missing is a heart that cannot do either:\n$mutation",
                mutation.contains(field)
            )
        }
    }

    @Test
    fun `it is balanced`() {
        val mutation = GraphQLQueries.TOGGLE_REPLY_LIKE
        assertEquals("braces", mutation.count { it == '{' }, mutation.count { it == '}' })
        assertEquals("parens", mutation.count { it == '(' }, mutation.count { it == ')' })
    }

    // ---- the rest of the chain, which is where a gap like this actually hides ----

    @Test
    fun `the service, the interface and the implementation all agree`() {
        fun source(relative: String) = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/$relative"
        ).readText()

        val api = source("data/network/AniListApiService.kt")
        val iface = source("data/repository/AniListRepository.kt")
        val impl = source("data/repository/AniListRepositoryImpl.kt")

        assertTrue(
            "the Retrofit method is the hop the repository actually calls",
            api.contains("suspend fun toggleActivityReplyLike(")
        )
        assertTrue(
            "the interface has to declare it, or the impl does not override " +
                "anything",
            iface.contains("suspend fun toggleActivityReplyLike(replyId: Int)")
        )
        assertTrue(impl.contains("override suspend fun toggleActivityReplyLike("))
        assertTrue(
            "the implementation has to send the document the schema accepts",
            impl.contains("GraphQLQueries.TOGGLE_REPLY_LIKE")
        )
        assertTrue(
            "and reach the service through it, not by calling the network directly",
            impl.contains("apiService::toggleActivityReplyLike")
        )
    }

    /**
     * The liker-control is shared rather than declared twice.
     *
     * It was `private` to `ProfileActivityCard`, which meant a second caller had to
     * re-declare the icon swap, the 48dp target, the press spring and the disabled
     * state. Nothing fails when that drifts - it just produces two controls that are
     * meant to look identical and do not - so it is worth pinning.
     */
    @Test
    fun `the like control is one shared composable, not two declarations`() {
        val shared = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/components/EngagementAction.kt"
        ).readText()

        val card = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileActivityCard.kt"
        ).readText()
        val comments = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/screens/profile/CommentThread.kt"
        ).readText()

        assertTrue("the shared control is public:", shared.contains("fun EngagementAction("))
        assertTrue(
            "the card uses the shared one",
            card.contains("EngagementAction(") && card.contains("import com.anisequel.ui.components.EngagementAction")
        )
        assertFalse(
            "and does not keep a private copy of it, which is the whole point",
            card.contains("private fun ActivityAction")
        )
        assertTrue(
            "the comments sheet uses the same control",
            comments.contains("EngagementAction(") &&
                comments.contains("import com.anisequel.ui.components.EngagementAction")
        )
    }

    /**
     * A heart that renders and does nothing is the one state this control must never
     * be in, and a signed-out reader is exactly how that happens: AniList leaves
     * `isLiked` null rather than false when it has no viewer.
     */
    @Test
    fun `the reply heart is disabled when AniList did not say whether it is liked`() {
        val comments = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/screens/profile/CommentThread.kt"
        ).readText()

        assertTrue(
            "gating on `== true` would draw a live-looking heart for a signed-out " +
                "reader that then does nothing:\n$comments",
            comments.contains("enabled = reply.isLiked != null && !likePending")
        )
    }

    /**
     * Both sheets are drawn as siblings of the tab content.
     *
     * `AnimatedContent` composes the incoming tab alongside the outgoing one and then
     * *disposes* the outgoing one, and a `ModalBottomSheet` is a window of its own. One
     * drawn from inside a tab is therefore torn down with the tab: open a thread, tap
     * another tab, and the sheet goes mid-sentence with the draft still in it. The
     * likers sheet had been hoisted out for that reason and carried a note saying so;
     * the comments sheet was left behind, leaving the two with opposite lifetimes and
     * nothing to explain why.
     */
    @Test
    fun `neither sheet is drawn from inside the tab AnimatedContent disposes`() {
        val screen = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/screens/profile/UserProfileScreen.kt"
        ).readText()
        val tab = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileActivityTab.kt"
        ).readText()

        assertFalse(
            "the comments sheet must not be drawn by the tab",
            tab.contains("ActivityCommentsSheet(")
        )
        assertTrue(
            "the screen draws it as a sibling instead",
            screen.contains("ActivityCommentsSheet(")
        )
        assertTrue(
            "and so does the likers sheet, which is the arrangement this fixes",
            screen.contains("ActivityLikersSheet(")
        )
        assertTrue(
            "the thread still opens from the tab, which is what the tap is on",
            tab.contains("onOpenComments(activity.id)")
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