package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The thread marks the comment being answered, not everybody who wrote it.
 *
 * The first version keyed the tint on the author's name, because that is what the
 * composer stores for the `@mention`. A person with forty comments in one thread
 * therefore had all forty lit while the editor was open against one of them - the
 * chip above the field said "Replying to YoruC", and the thread answered "which
 * YoruC comment?" by pointing at every row they had ever written.
 *
 * The fix is that the reply carries the comment's id as well as its name: the name
 * is the mention, the id is the row. This is a source scan because the claim is
 * about which value the row is compared against, and there is no device here to
 * compose a thread on.
 */
class CommentReplyTargetTest {

    @Test
    fun `the lit row is the comment id, not every comment by its author`() {
        val list = source("ThreadList.kt")
        val sheet = source("ActivityCommentsSheet.kt")
        val thread = source("CommentThread.kt")
        val row = source("CommentRows.kt")
        val bar = source("CommentActionRow.kt")

        assertTrue(
            "a reply row is compared to the recorded id:\n$list",
            list.contains("isReplyTarget = replyTargetId != null && reply.id == replyTargetId")
        )
        assertTrue(
            "and a message thread's head row can be the target too:\n$list",
            list.contains("isReplyTarget = replyTargetId != null && head.id == replyTargetId")
        )
        assertTrue(
            "the composer records the id when Reply is pressed:\n$sheet",
            sheet.contains("replyTargetId = reply.id")
        )
        assertTrue(
            "the bar hands back the comment, not only its author's name:\n$bar",
            bar.contains("onClick = { onReply(reply) }") && bar.contains("onReply: (ActivityReply) -> Unit")
        )
        assertFalse(
            "a name comparison lights every comment the same person wrote",
            list.contains("user?.name == replyTargetName") ||
                thread.contains("replyTargetName") ||
                row.contains("replyTargetName")
        )
    }

    private fun source(name: String): String = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/screens/profile/$name"
    ).readText()

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
