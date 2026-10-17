package com.anisequel

import com.anisequel.ui.components.openExternalUrlOrCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A link in somebody else's bio that the device cannot open used to do nothing at all.
 *
 * ## What was broken
 *
 * `openExternalUrl` started an `ACTION_VIEW` intent inside a `runCatching` and returned
 * `Unit`. Every one of its eighteen call sites discarded whatever it produced, so the
 * failure was structurally invisible: a `Uri.parse` of a malformed link, a scheme no
 * app handles, or a device with nothing registered for `https` all threw an
 * `ActivityNotFoundException` into a `catch` that threw it away.
 *
 * The reader's experience was a tap with no browser, no error and no hint — and no way
 * to tell a link the app failed to open from one that is broken on AniList. For a bio
 * that is the *common* case, not an edge case: a profile routinely links `steam://`,
 * `spotify:` and bare `anilist.co/anime/one-piece` slugs, and a device without those
 * apps installed is an ordinary phone.
 *
 * ## Why this is a test and not a note
 *
 * The edit that reintroduces it is adding a second `runCatching` somewhere, or
 * swapping the copy fallback back for a plain `Unit`. Nothing about that diff looks
 * like it could remove the reader's only feedback.
 */
class ExternalLinkTest {

    private fun markdownSource(name: String) = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/components/markdown/$name.kt"
    ).readText()

    private fun linksSource() = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/components/OpenExternalUrl.kt"
    ).readText()

    /**
     * The outcome has to be reportable.
     *
     * `Unit` is the specific thing that made the failure invisible: with no value to
     * return there is nothing for a caller to check, so "did it work" was not a
     * question the code could even phrase.
     */
    @Test
    fun `opening a link reports whether it worked`() {
        val source = linksSource()

        assertTrue(
            "`openExternalUrl` must return a Boolean, or a caller has no way to ask " +
                "whether the link opened:\n$source",
            Regex("""fun openExternalUrl\(context: Context, url: String\): Boolean""").containsMatchIn(source)
        )
        assertTrue(
            "and it must answer from the call rather than from a bare Unit:\n$source",
            source.contains(".isSuccess")
        )
    }

    /**
     * Every link a reader can tap has to have somewhere to go.
     *
     * A dead link still leaves the reader with the address — to paste into a browser
     * or to send to themselves — which is strictly more useful than the tap doing
     * nothing, and costs one clipboard write.
     */
    @Test
    fun `a link nothing can open still reaches the reader`() {
        val source = linksSource()

        assertTrue("the copy fallback exists:\n$source", source.contains("fun openExternalUrlOrCopy("))
        assertTrue(
            "and it writes to the clipboard rather than only complaining",
            source.contains("setPrimaryClip")
        )
        assertTrue(
            "and it says what happened, so the paste is not a mystery",
            source.contains("Toast.makeText")
        )
    }

    /**
     * The whole markdown renderer is what "a reader's bio" means here.
     *
     * Every renderer that draws a link has to use the fallback, and the check is that
     * the *plain* opener is gone from these files entirely — a single file left on it
     * is a link that silently dies, and it is invisible because the code still
     * compiles and still looks correct.
     */
    @Test
    fun `every link in a rendered bio uses the fallback`() {
        listOf("AniListMarkdownBlocks", "AniListMarkdownParagraph", "AniListMarkdownTable").forEach { name ->
            val source = markdownSource(name)

            assertFalse(
                "$name still calls the plain `openExternalUrl`, so at least one link " +
                    "in a bio does nothing at all when it fails:\n$source",
                Regex("""openExternalUrl\(context""").containsMatchIn(source)
            )
            assertTrue(
                "$name should be using `openExternalUrlOrCopy`:\n$source",
                source.contains("openExternalUrlOrCopy(")
            )
            assertTrue(
                "$name must import the name it uses:\n$source",
                source.contains("import com.anisequel.ui.components.openExternalUrlOrCopy")
            )
        }
    }

    /**
     * A title page's character link is not a bio link.
     *
     * It goes to a page AniList itself answered for, so there is nothing to copy and
     * a clipboard write on the failure path would be a surprise. This is the boundary
     * the two functions exist to draw, so it is worth pinning rather than leaving to
     * whoever edits next.
     */
    @Test
    fun `a link the app vouched for does not fall back to the clipboard`() {
        val infoTalent = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/components/detail/info/InfoTalent.kt"
        ).readText()

        assertTrue(
            "the character card opens AniList's own page, which always has a scheme " +
                "an app handles",
            infoTalent.contains("openExternalUrl(context, url)")
        )
        assertFalse(
            "so it must not be the copy fallback",
            infoTalent.contains("openExternalUrlOrCopy(")
        )
    }

    /**
     * Source compatibility is the other half of this change.
     *
     * Eighteen call sites kept `openExternalUrl(...)` as a bare statement. That works
     * because Kotlin allows a value-returning function to be called as a statement and
     * coerces a lambda's last expression to `Unit` — but it is not obvious, and the
     * day it stops being true this is a file-per-error across five files.
     */
    @Test
    fun `callers still compile as bare statements`() {
        val callers = listOf(
            "ui/components/detail/info/InfoTalent.kt",
            "ui/screens/profile/ProfileHomeTab.kt",
            "ui/screens/profile/UserProfileScreen.kt",
            "ui/screens/settings/InfoSettingsTab.kt",
            "ui/screens/studio/StudioScreen.kt"
        ).map { path ->
            File(findProjectRoot(), "app/src/main/java/com/anisequel/$path").readText()
        }

        val asExpression = callers.filter { Regex("""=\s*openExternalUrl\(""").containsMatchIn(it) }
        assertTrue(
            "no caller should be assigning the result today; if one has started to " +
                "that is fine, but this assertion is what tells you the signature " +
                "change rippled somewhere unintended",
            asExpression.isEmpty()
        )

        // And none of them should be wrapping it in something that needs Unit back.
        callers.forEach { source ->
            assertFalse(
                "a caller must not force the old Unit signature",
                source.contains(": (Context, String) -> Unit")
            )
        }
    }

    @Test
    fun `the opener still guards its own failure`() {
        val source = linksSource()

        assertTrue(
            "no app for the scheme is the common case, not an exceptional one, so " +
                "the intent is still started defensively:\n$source",
            source.contains("runCatching")
        )
        assertEquals(
            "one public opener and one fallback, and no third copy of this logic",
            2,
            Regex("""fun openExternalUrl[A-Za-z]*\(""").findAll(source).count()
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