package com.anisequel

import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.repository.SnapshotStore
import com.squareup.moshi.Moshi
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The disk tier under the repository's caches.
 *
 * ## What was broken
 *
 * Every cache the repository kept lived for exactly one process. Closing the app
 * threw away the user's own list, their profile, and the synopsis/cast/studios of
 * every title they had opened - so the next launch bought all of it again from
 * AniList, none of it having changed. AniList allows about thirty requests a
 * minute, so that re-purchase is not free.
 *
 * ## What this test pins
 *
 * The store's contract with its callers, which is what makes it safe to put a
 * file between a screen and AniList:
 *
 * - a value written by one process is read by the next, carrying the moment it
 *   was **written** - reading a snapshot never renews it;
 * - the window is closed at exactly its length and a miss one millisecond past
 *   it, and an expired entry is dropped rather than refused on every open;
 * - anything unreadable - a truncated file, a payload today's models cannot
 *   parse, a file from another release - is a miss and never an exception;
 * - the cache is bounded: past its budget the oldest entry goes first, and the
 *   newest is never the one dropped.
 *
 * A hit here is a request AniList's rate limit never sees, so a wrong hit (stale
 * past its window, or another release's shape read as today's) is worse than the
 * miss it saved.
 */
class SnapshotStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val moshi = Moshi.Builder().build()

    /** A fixed, movable clock: the windows below are days long. */
    private var now = 1_700_000_000_000L

    private fun store(
        formatTag: String = TAG,
        maxBytesPerKind: Long = ONE_MEGABYTE
    ): SnapshotStore = SnapshotStore(
        directory = folder.root,
        moshi = moshi,
        formatTag = formatTag,
        nowMillis = { now },
        maxBytesPerKind = maxBytesPerKind
    )

    @Test
    fun `a value written by one process is read by the next, with its own timestamp`() = runTest {
        val node = mediaNode(id = 42, description = "A synopsis")
        store().write(KIND, KEY, MediaNode::class.java, node)
        val writtenAt = now

        now += 5 * 60 * 1000L
        val read = store().read(KIND, KEY, MediaNode::class.java, DAY)

        assertEquals(node, read?.value)
        assertEquals(
            "a read must carry the moment the value was written. Stamping it with " +
                "now is how a cache stops expiring: every lookup would renew it.",
            writtenAt,
            read?.storedAtMillis
        )
    }

    @Test
    fun `the window is closed at exactly its length and a miss past it`() = runTest {
        store().write(KIND, KEY, MediaNode::class.java, mediaNode(id = 42))
        val writtenAt = now

        now = writtenAt + DAY
        assertNotNull(
            "at exactly the window the entry is still the answer it was - the " +
                "same boundary the repository's own caches use",
            store().read(KIND, KEY, MediaNode::class.java, DAY)
        )

        now = writtenAt + DAY + 1
        assertNull(store().read(KIND, KEY, MediaNode::class.java, DAY))
        assertNull(
            "an expired entry has to be dropped, not merely refused: an unbounded " +
                "window over the same store must find nothing, or every open pays " +
                "a file parse to reach the same no",
            store().read(KIND, KEY, MediaNode::class.java, Long.MAX_VALUE)
        )
    }

    @Test
    fun `an unparseable payload is a miss, and a plain write recovers it`() = runTest {
        // A payload written for one shape and read back as another is what a
        // model change between two releases looks like from here.
        store().write(KIND, KEY, MediaListCollection::class.java, MediaListCollection())
        assertNull(store().read(KIND, KEY, MediaNode::class.java, DAY))

        val node = mediaNode(id = 42, description = "fresh")
        store().write(KIND, KEY, MediaNode::class.java, node)
        assertEquals(node, store().read(KIND, KEY, MediaNode::class.java, DAY)?.value)
    }

    @Test
    fun `a corrupt file is a miss, is not left behind, and a write recovers it`() = runTest {
        val file = File(folder.root, "$KIND.json")
        file.writeText("{\"schema\": 1, \"entries\": {")
        assertNull(store().read(KIND, KEY, MediaNode::class.java, DAY))
        assertFalse(
            "a file this build cannot read has to be removed, or every open pays " +
                "for the same failing parse",
            file.exists()
        )

        store().write(KIND, KEY, MediaNode::class.java, mediaNode(id = 42))
        assertNotNull(store().read(KIND, KEY, MediaNode::class.java, DAY))
    }

    @Test
    fun `a file from another release is a miss`() = runTest {
        store(formatTag = "release-225").write(KIND, KEY, MediaNode::class.java, mediaNode(id = 42))
        assertNull(
            "a snapshot can parse into today's models and still be missing a field " +
                "the new release's query asks for, so another release's files are " +
                "not trusted",
            store(formatTag = "release-226").read(KIND, KEY, MediaNode::class.java, DAY)
        )

        val node = mediaNode(id = 43, description = "this release")
        store(formatTag = "release-226").write(KIND, KEY, MediaNode::class.java, node)
        assertEquals(
            node,
            store(formatTag = "release-226").read(KIND, KEY, MediaNode::class.java, DAY)?.value
        )
    }

    @Test
    fun `kinds are separate files and do not answer for each other`() = runTest {
        store().write("list", KEY, MediaListCollection::class.java, MediaListCollection())
        assertNull(store().read("detail", KEY, MediaListCollection::class.java, DAY))
        assertNotNull(store().read("list", KEY, MediaListCollection::class.java, DAY))
    }

    @Test
    fun `removeKind empties one kind and leaves the others`() = runTest {
        val first = store()
        first.write("list", KEY, MediaListCollection::class.java, MediaListCollection())
        first.write("detail", KEY, MediaNode::class.java, mediaNode(id = 42))
        first.removeKind("list")

        val next = store()
        assertNull(next.read("list", KEY, MediaListCollection::class.java, DAY))
        assertNotNull(next.read("detail", KEY, MediaNode::class.java, DAY))
    }

    @Test
    fun `clear takes a signed-out person's data off the disk`() = runTest {
        val first = store()
        first.write("list", KEY, MediaListCollection::class.java, MediaListCollection())
        first.write("detail", KEY, MediaNode::class.java, mediaNode(id = 42))
        first.clear()

        assertTrue(
            "signing out has to leave nothing to be read by the next account",
            folder.root.listFiles().orEmpty().isEmpty()
        )
        val next = store()
        assertNull(next.read("list", KEY, MediaListCollection::class.java, DAY))
        assertNull(next.read("detail", KEY, MediaNode::class.java, DAY))
    }

    @Test
    fun `past its budget the oldest entry goes first, and never the newest`() = runTest {
        // Each cover below is a couple of hundred bytes, so three of them do not
        // fit in the budget and one does.
        val small = store(maxBytesPerKind = 300)
        small.write(KIND, "one", MediaCover::class.java, cover("1"))
        now += 1_000
        small.write(KIND, "two", MediaCover::class.java, cover("2"))
        now += 1_000
        small.write(KIND, "three", MediaCover::class.java, cover("3"))

        val next = store(maxBytesPerKind = 300)
        assertNull(next.read(KIND, "one", MediaCover::class.java, DAY))
        assertNull(next.read(KIND, "two", MediaCover::class.java, DAY))
        assertNotNull(next.read(KIND, "three", MediaCover::class.java, DAY))

        // A budget smaller than the entry itself still keeps it: discarding what
        // was just written would turn "too big to keep" into "never kept", which
        // is the one payload worth keeping instead of re-fetching.
        store(maxBytesPerKind = 1).write(KIND, "four", MediaCover::class.java, cover("4"))
        assertNotNull(
            "the newest entry must never be the one dropped",
            store(maxBytesPerKind = 1).read(KIND, "four", MediaCover::class.java, DAY)
        )
    }

    private companion object {
        const val TAG = "test-release"
        const val KIND = "detail"
        const val KEY = "ANIME:42"
        const val ONE_MEGABYTE = 1024L * 1024
        const val DAY = 24 * 60 * 60 * 1000L
    }
}

private fun mediaNode(id: Int, description: String? = null): MediaNode = MediaNode(
    id = id,
    title = MediaTitle(romaji = "Title $id"),
    description = description
)

private fun cover(suffix: String): MediaCover = MediaCover(
    url = "https://example.invalid/" + "x".repeat(200) + suffix,
    title = "Cover $suffix"
)
