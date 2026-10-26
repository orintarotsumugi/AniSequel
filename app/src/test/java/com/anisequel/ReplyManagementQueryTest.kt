package com.anisequel

import com.anisequel.data.network.GraphQLQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A comment can be rewritten and removed, and this app could do neither.
 *
 * ## What was missing
 *
 * The thread could be read, a comment could be written, and a comment could be
 * liked - and that was the whole surface. Once posted, a typo was permanent and a
 * comment the author no longer stood behind stayed in somebody else's thread. The
 * schema has had `SaveActivityReply(id:)` and `DeleteActivityReply(id:)` all
 * along; the app simply never asked.
 *
 * ## Verified against the live schema before it was written
 *
 * `Mutation.SaveActivityReply` is documented "create or update an activity
 * reply", with `id` "required for updating", and `text` Min 2 / Max 8000.
 * `Mutation.DeleteActivityReply` returns the shared `Deleted` object. Both
 * documents were then sent to the live API: each was answered by the
 * authentication wall (401 unauthenticated), which is what a *valid* document
 * gets, where a malformed one is a 400 naming the bad argument. The two are
 * distinguishable, and only the former means the document is right.
 *
 * ## What this file pins
 *
 * The documents' argument names and selections, the four-hop chain from document
 * to network, the cache rule that splits the two mutations - a delete moves the
 * feed's reply count and drops the cached pages, an edit cannot and must not -
 * and the two UI rules that make the pair reachable: the menu is drawn only on
 * the viewer's own comments, and the sheet scrolls only to a comment this
 * session posted.
 */
class ReplyManagementQueryTest {

    @Test
    fun `the edit is the same mutation with an id, not a second document`() {
        val mutation = GraphQLQueries.EDIT_ACTIVITY_REPLY

        assertTrue("it is a mutation:\n$mutation", mutation.trimStart().startsWith("mutation"))
        assertTrue(
            "`SaveActivityReply` is documented as create-or-update and `id` as " +
                "the update:\n$mutation",
            mutation.contains("SaveActivityReply(") && mutation.contains("id: \$id")
        )
        assertTrue(
            "the reply's actual parent travels with the edit. It is a declared " +
                "argument of the field, so it cannot be a validation error, and a " +
                "resolver that re-checks the thread is exactly what it is for:\n$mutation",
            mutation.contains("activityId: \$activityId")
        )
        assertTrue(mutation.contains("text: \$text"))
    }

    @Test
    fun `every declared edit variable is used exactly once`() {
        val mutation = GraphQLQueries.EDIT_ACTIVITY_REPLY

        listOf("id", "activityId", "text").forEach { name ->
            // Two occurrences: the declaration (`$id: Int`) and the one use
            // (`id: $id`). A document that declares a variable it never uses is a
            // 400, and one that uses a variable it never declared is the same
            // failure.
            assertEquals(
                "`$name` should be declared once and used once:\n$mutation",
                2,
                mutation.split("$" + name).size - 1
            )
        }
    }

    /**
     * The response replaces the row, so it has to carry everything the row draws.
     *
     * A selection that only returned `id` and `text` would produce an edit that
     * silently loses the comment's heart - its count and its state - because the
     * replacement is what gets stored in the thread's state.
     */
    @Test
    fun `the edit selection carries the whole row back`() {
        val mutation = GraphQLQueries.EDIT_ACTIVITY_REPLY

        listOf("id", "text", "likeCount", "isLiked", "createdAt", "user").forEach { field ->
            assertTrue("`$field` is part of the row that gets replaced:\n$mutation", mutation.contains(field))
        }
    }

    @Test
    fun `the delete is its own mutation with the reply's id`() {
        val mutation = GraphQLQueries.DELETE_ACTIVITY_REPLY

        assertTrue("it is a mutation:\n$mutation", mutation.trimStart().startsWith("mutation"))
        assertTrue(
            "`DeleteActivity` deletes the post a thread hangs off, not a reply in " +
                "it, and the two take ids from different namespaces:\n$mutation",
            mutation.contains("DeleteActivityReply(id: \$id)")
        )
        assertTrue(
            "the shared `Deleted` object is the only thing that comes back - the " +
                "reply is not echoed, so the row is dropped by the id that was sent:\n$mutation",
            mutation.contains("deleted")
        )
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
            "Retrofit has one method per document; the edit reuses the save method " +
                "because it is the same field:",
            api.contains("suspend fun saveActivityReply(") &&
                api.contains("suspend fun deleteActivityReply(")
        )
        assertTrue(
            "the interface declares both, or the impl overrides nothing",
            iface.contains("suspend fun editActivityReply(replyId: Int, activityId: Int, text: String)") &&
                iface.contains("suspend fun deleteActivityReply(replyId: Int)")
        )
        assertTrue(impl.contains("override suspend fun editActivityReply("))
        assertTrue(impl.contains("override suspend fun deleteActivityReply("))
        assertTrue(
            "each reaches the document the schema accepts",
            impl.contains("GraphQLQueries.EDIT_ACTIVITY_REPLY") &&
                impl.contains("GraphQLQueries.DELETE_ACTIVITY_REPLY")
        )
        assertTrue(
            "and goes through the service, not the network, for both",
            impl.contains("apiService::saveActivityReply") &&
                impl.contains("apiService::deleteActivityReply")
        )
    }

    /**
     * The cache rule the two mutations must not share.
     *
     * A cached activity page carries the thread's *reply count*, never its text.
     * A delete moves that count, so the pages are dropped rather than left
     * claiming a comment that no longer exists - the mirror of what a post does.
     * An edit cannot change the count, and dropping the pages for it would spend
     * a re-fetch on every rewrite for nothing.
     */
    @Test
    fun `a delete drops the cached pages and an edit leaves them alone`() {
        val impl = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/data/repository/AniListRepositoryImpl.kt"
        ).readText()

        val editStart = impl.indexOf("override suspend fun editActivityReply(")
        val deleteStart = impl.indexOf("override suspend fun deleteActivityReply(")
        assertTrue("both functions exist", editStart >= 0 && deleteStart >= 0)

        val editBlock = impl.substring(editStart, deleteStart)
        val nextOverride = impl.indexOf("\n    override suspend fun", deleteStart + 1)
        val deleteBlock = impl.substring(
            deleteStart,
            if (nextOverride >= 0) nextOverride else impl.length
        )

        assertTrue(
            "the delete has to drop the pages whose count it just moved:\n$deleteBlock",
            deleteBlock.contains("activityCache.clear()")
        )
        assertFalse(
            "the edit must not - the count did not move:\n$editBlock",
            editBlock.contains("activityCache.clear()")
        )
    }

    /**
     * The menu exists only on the viewer's own comments.
     *
     * Checked twice on purpose: the sheet needs it to decide whether to draw the
     * control at all, and the store needs it because it is the half a test can
     * reach - "the server would refuse anyway" is a sentence about a round trip,
     * not about a UI.
     */
    @Test
    fun `the manage control is offered only on the viewer's own comments`() {
        // The row lives in `CommentThread.kt` since the sheet was split by
        // responsibility; read it together with the sheet so the assertion is
        // about the behaviour rather than about which file currently draws it.
        val sheet = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/screens/profile/ActivityCommentsSheet.kt"
        ).readText() + File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/screens/profile/CommentThread.kt"
        ).readText()
        val store = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/viewmodel/CommentThreadStore.kt"
        ).readText()
        val icons = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/components/AppVectorIcons.kt"
        ).readText()

        assertTrue(
            "the sheet compares the author against the signed-in viewer, and a null " +
                "viewer draws no control:\n$sheet",
            sheet.contains("canManage = viewerId != null && reply.user?.id == viewerId")
        )
        assertTrue(
            "the store repeats the check rather than trusting the caller:\n$store",
            store.contains("if (reply.user?.id == null || reply.user.id != viewerId) return")
        )
        assertTrue("the control has a vector, no emoji:", icons.contains("val ActivityManage ="))
        assertTrue(sheet.contains("AppVectorIcons.ActivityManage"))
    }

    /**
     * The sheet scrolls to a comment this session posted, and to nothing else.
     *
     * The list also grows when the thread *loads* - jumping to the bottom of a
     * conversation the reader has not seen yet would hide its first comments
     * behind an effect nobody asked for. That is why the trigger is the id the
     * store records on a successful post rather than "the list got longer".
     */
    @Test
    fun `the sheet scrolls to a posted comment, not to a loaded thread`() {
        val sheet = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/screens/profile/ActivityCommentsSheet.kt"
        ).readText()
        val store = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/viewmodel/CommentThreadStore.kt"
        ).readText()

        assertTrue(
            "the scroll effect is keyed on the recorded id:\n$sheet",
            sheet.contains("LaunchedEffect(state.postedReplyId, composing)")
        )
        assertTrue(
            "and waits for the editor to close before it moves:\n$sheet",
            sheet.contains("if (composing) return@LaunchedEffect")
        )
        assertTrue(
            "it looks the posted comment up by id, never by list position:\n$sheet",
            sheet.contains("replies.indexOfFirst { it.id == postedReplyId }")
        )
        assertTrue(
            "the row it scrolls to accounts for the retry banner a failed refresh " +
                "puts above the replies, or the scroll stops one row short:\n$sheet",
            sheet.contains("postedReplyRow(") && sheet.contains("hasRefreshBanner")
        )
        assertTrue(
            "the store is what records it, on the post that succeeded:\n$store",
            store.contains("postedReplyId = reply.id")
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
