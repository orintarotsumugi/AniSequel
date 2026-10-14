package com.anisequel

import com.anisequel.data.model.MediaCoverKey
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.MarkdownNode
import com.anisequel.ui.components.markdown.mediaCoverKeysIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * How long a single bio parse may take before the watchdog declares it looping.
 *
 * Generous on purpose: this is a bound on a *non-terminating* loop, not a
 * performance target. The longest legitimate parse in this suite is a
 * dozen-block bio and takes microseconds, so anything reaching this has already
 * proved it is not going to finish.
 */
private const val PARSE_WATCHDOG_SECONDS = 10L

/**
 * Guards which AniList links are treated as media, and under which key.
 *
 * The failure this replaces is not "no cover appears" - it is *the wrong cover
 * appears*. An AniList URL names six different things in six different id
 * namespaces, so `anilist.co/studio/128893` resolved through a `Media(id:)`
 * query returns the anime 128893 and puts an unrelated poster on a studio card.
 * Nothing about that looks broken; it looks correct.
 *
 * The pattern also accepts a slug where an id goes (`anilist.co/anime/one-piece`),
 * which is not numeric. Anything that reads it as an integer asks for 0.
 */
class MarkdownMediaPreviewTest {

    @Test
    fun `an anime link yields its media key`() {
        assertEquals(
            listOf(MediaCoverKey("ANIME", 128893)),
            mediaCoverKeysIn("https://anilist.co/anime/128893")
        )
    }

    @Test
    fun `a manga link yields its media key`() {
        assertEquals(
            listOf(MediaCoverKey("MANGA", 30013)),
            mediaCoverKeysIn("https://anilist.co/manga/30013")
        )
    }

    @Test
    fun `a non-media link is never resolved as media`() {
        // Same numeric id, different namespace. Resolving these would put the
        // anime 128893's poster on a character, a studio and a user card.
        listOf(
            "https://anilist.co/character/128893",
            "https://anilist.co/staff/128893",
            "https://anilist.co/studio/128893",
            "https://anilist.co/user/128893"
        ).forEach { url ->
            assertTrue("$url must not be resolved as media", mediaCoverKeysIn(url).isEmpty())
        }
    }

    @Test
    fun `a slug is not asked for as a number`() {
        assertTrue(
            "a slug id is not numeric and would be requested as 0",
            mediaCoverKeysIn("https://anilist.co/anime/one-piece").isEmpty()
        )
    }

    @Test
    fun `a bio's links are all collected, in order, without duplicates`() {
        val bio = """
            **Favourites**

            https://anilist.co/anime/128893
            https://anilist.co/manga/30013
            https://anilist.co/anime/128893
            https://anilist.co/studio/1
            https://anilist.co/anime/1535
        """.trimIndent()

        // "Twice" must not become two ids to fetch; the studio must not appear.
        assertEquals(
            listOf(
                MediaCoverKey("ANIME", 128893),
                MediaCoverKey("MANGA", 30013),
                MediaCoverKey("ANIME", 1535)
            ),
            mediaCoverKeysIn(bio)
        )
    }

    @Test
    fun `a bio with no anilist links asks for nothing`() {
        assertTrue(
            mediaCoverKeysIn("Just a person who watches a lot of anime and writes about it.")
                .isEmpty()
        )
    }

    @Test
    fun `zero is not a valid media id`() {
        assertTrue(mediaCoverKeysIn("https://anilist.co/anime/0").isEmpty())
    }

    /**
     * The half that makes a card show a *cover*.
     *
     * This collector drives the single batched request, so a link it cannot see is
     * a card with a title and a permanently empty poster frame. The shape that
     * matters is the labelled link inside a bold run every "hall of fame" bio
     * uses - not the bare-URL line this already handled.
     */
    @Test
    fun `a labelled link inside a bold run still yields its key`() {
        val bio = "**✨ [#1] [Hell's Paradise](https://anilist.co/anime/128893)**\n\n*Loyal to it.*"

        assertEquals(listOf(MediaCoverKey("ANIME", 128893)), mediaCoverKeysIn(bio))
    }

    /**
     * AniList media ids are unique only *within* a type.
     *
     * `anilist.co/anime/1` and `anilist.co/manga/1` are different shows, and the
     * collector used to hand both to one `LinkedHashSet<Int>` - so only one was
     * requested, and the single answer was then drawn on *both* cards. Two
     * correct-looking posters, one of which is wrong, which is the failure mode
     * the rest of this class exists to prevent.
     *
     * The keys must therefore differ even though the numbers do not.
     */
    @Test
    fun `an anime and a manga sharing an id are two different keys`() {
        val bio = """
            https://anilist.co/anime/1
            https://anilist.co/manga/1
        """.trimIndent()

        assertEquals(
            listOf(MediaCoverKey("ANIME", 1), MediaCoverKey("MANGA", 1)),
            mediaCoverKeysIn(bio)
        )
    }

    /**
     * A query string on an AniList URL, which is what sharing a bio produces.
     *
     * The URL pattern used to be anchored straight after the path, so this matched
     * nothing: it rendered as a plain underlined link where every other entry in
     * the same hall-of-fame list drew a card, and its poster was never requested
     * at all. The `?utm_source=` form - a link pasted out of a browser address bar
     * - is the single most common way a person copies one of these.
     */
    @Test
    fun `a share query string does not stop a link being a media card`() {
        assertEquals(
            listOf(MediaCoverKey("ANIME", 128893)),
            mediaCoverKeysIn("[Hell's Paradise](https://anilist.co/anime/128893?utm_source=discord)")
        )
    }

    @Test
    fun `a slug with a trailing slash and a query still resolves`() {
        assertEquals(
            listOf(MediaCoverKey("ANIME", 16498)),
            mediaCoverKeysIn("https://anilist.co/anime/16498/Death-Note?ref=share")
        )
    }
}

/**
 * The parser's own termination, and what it does with a line it cannot classify.
 *
 * ## The failure
 *
 * `parse` is called from `AboutCard` inside `remember(about)` - on the **Compose
 * main thread** - and again from the ViewModel. Every block handler above the
 * paragraph path is reached only when it matches the line at `i`, so each is
 * guaranteed to advance. `tryParseTable` was the exception: it declines a `|`
 * line with no `|---|` separator under it, one whose separator carries anything
 * else, or one that is simply the bio's last line. The paragraph loop refuses to
 * consume a `|` line so a paragraph stops and lets the table handler have its
 * turn - so on exactly that input the loop body never ran, nothing was appended,
 * and `i` never moved.
 *
 * That is an ANR, not a slow parse, and it is reachable by writing two lines of
 * a table without the separator between them. Every test here runs the real
 * parser, so a regression is an actual hang rather than a failed assertion.
 */
class MarkdownPipeLineTest {

    @Test
    fun `a pipe line with no table separator does not hang the parser`() {
        // The table handler declines this: the line under it has no `|`.
        val bio = "Hello there\n| Completed | 100 |\n| Watching | 20 |"

        val nodes = withTimeout { AniListMarkdownParser.parse(bio) }

        // The author's text is kept rather than dropped, so the reader still sees
        // the line they wrote rather than a silent gap.
        assertTrue("the pipe lines must survive as paragraphs", nodes.isNotEmpty())
    }

    @Test
    fun `a pipe line as the bio's last line does not hang the parser`() {
        // No line under it at all, so `tryParseTable` returns null on the length
        // check alone.
        val nodes = withTimeout { AniListMarkdownParser.parse("Hello\n| a | b |") }

        assertTrue(nodes.isNotEmpty())
    }

    @Test
    fun `a lone pipe does not hang the parser`() {
        val nodes = withTimeout { AniListMarkdownParser.parse("I like:\n|") }

        assertTrue(nodes.isNotEmpty())
    }

    @Test
    fun `a pipe line whose separator carries text does not hang the parser`() {
        // `separatorClean` is non-empty here, so the table handler declines and
        // the paragraph loop still refuses the line.
        val nodes = withTimeout {
            AniListMarkdownParser.parse("Stats\n| Completed | 100 |\n|---|\n| Watching | 20 |")
        }

        assertTrue(nodes.isNotEmpty())
    }

    @Test
    fun `a real table is still parsed as a table`() {
        // The control for the four above: the fix must not turn every table into
        // a paragraph.
        val nodes = withTimeout {
            AniListMarkdownParser.parse("Rank | Title\n--|--\n1 | Cowboy Bebop")
        }

        assertEquals(1, nodes.size)
        assertTrue(
            "a well-formed table must still be a table",
            nodes[0] is MarkdownNode.TableBlock
        )
    }

    @Test
    fun `a box-drawing separator does not hang the parser`() {
        val nodes = withTimeout { AniListMarkdownParser.parse("Hello!\n|----------------------|") }

        assertTrue(nodes.isNotEmpty())
    }

    /**
     * Runs [block] under a watchdog so a reintroduced infinite loop fails this
     * test rather than hanging CI until the job times out.
     *
     * A plain call would not do: the regression this guards is a `while` loop
     * that never terminates, so without the watchdog the suite would sit there
     * looking like an infrastructure problem instead of a named test failing
     * with a message naming the actual cause.
     *
     * The parse runs on a separate thread because it cannot be interrupted - a
     * spinning loop holds the thread's own `while` and no flag will release it.
     * `cancel(true)` therefore cannot free it, so the only way to bound the wait
     * is to give up on the thread and fail the assertion. The thread is a daemon
     * so a genuinely stuck parse cannot keep the JVM alive after the failure is
     * reported.
     */
    private fun <T> withTimeout(block: () -> T): T {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "markdown-parse-watchdog").apply { isDaemon = true }
        }
        try {
            return executor.submit(Callable { block() }).get(PARSE_WATCHDOG_SECONDS, TimeUnit.SECONDS)
        } catch (timeout: TimeoutException) {
            throw AssertionError(
                "AniListMarkdownParser.parse did not terminate within " +
                    "$PARSE_WATCHDOG_SECONDS seconds - the parser is looping on this input."
            )
        } finally {
            executor.shutdownNow()
        }
    }
}