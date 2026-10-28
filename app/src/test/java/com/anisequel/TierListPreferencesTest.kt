package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.StoredTierLibrary
import com.anisequel.data.repository.TierListPreferences
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierMediaType
import com.anisequel.domain.tier.withPlacement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the tier list *library*: several documents, written one at a time.
 *
 * This is the layer that holds the only state in the app a person cannot recreate.
 * A ranking built over an afternoon of dragging is not derivable from AniList, and
 * the failure modes here are all silent - a list that is written but not indexed, an
 * index entry whose document was removed, an undo that restores the wrong snapshot -
 * so each of them is exercised through the real DataStore rather than argued about
 * in a comment.
 *
 * ## Why every test makes its own list
 *
 * Robolectric hands every test in a class the same application context, and this
 * preferences file lives on disk, so a test that leaves a list behind leaves it
 * there for the next one. JUnit's method order is not guaranteed, which is how a
 * shared file produces failures that depend on which test ran first. Every test here
 * therefore creates the list it is about and asserts about that list, and none of
 * them assumes it is the first to run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TierListPreferencesTest {

    private fun preferences(): TierListPreferences =
        TierListPreferences(ApplicationProvider.getApplicationContext<Context>())

    private suspend fun TierListPreferences.snapshot(): StoredTierLibrary = library.first()

    @Test
    fun `a new list is empty, named, and the one the app will carry on with`() = runTest {
        val preferences = preferences()

        val id = preferences.createEmpty("A list this test owns")

        val library = preferences.snapshot()
        val document = library.document(id)
        assertEquals("A list this test owns", document?.name)
        assertTrue("a new list must start empty", document!!.isEmpty)
        assertEquals(
            "making a list is the strongest signal that it is the one to carry on with",
            id,
            library.lastOpenedId
        )
        assertEquals(id, library.recent?.id)
    }

    @Test
    fun `a list is given the tiers it was created with`() = runTest {
        val tiers = listOf(TierDefinition("fav", "Favourites", 0xFFE5484D))

        val id = preferences().createEmpty("Custom tiers", tiers)

        assertEquals(tiers, preferences().snapshot().document(id)?.tiers)
    }

    @Test
    fun `a list remembers which pool it ranks`() = runTest {
        // The pool is a property of the document, not of the screen that opened it:
        // the board is rebuilt from this value on the next launch, and an anime
        // board relabelled as manga would keep placements for ids that mean
        // different titles in the other namespace.
        val id = preferences().createEmpty("Manga ranking", mediaType = TierMediaType.MANGA)

        assertEquals(
            TierMediaType.MANGA,
            preferences().snapshot().document(id)?.mediaType
        )
        assertEquals(
            "a list made without a pool is an anime list, as every older one was",
            TierMediaType.ANIME,
            preferences().snapshot().document(
                preferences().createEmpty("Anime ranking")
            )?.mediaType
        )
    }

    @Test
    fun `an edit is written and handed back as a change`() = runTest {
        val preferences = preferences()
        val id = preferences.createEmpty("Edited list")
        val before = preferences.snapshot().document(id)!!

        val mutation = preferences.mutate(id) { document ->
            document.copy(assignment = document.assignment.withPlacement(21, "s"))
        }

        val after = preferences.snapshot().document(id)!!
        assertEquals(mapOf(21 to "s"), after.assignment.tierIdByMediaId)
        assertEquals("the caller is handed the state the edit replaced", before, mutation?.before)
        assertEquals("and the state it produced", after, mutation?.after)
        assertTrue(
            "the store stamps the write, because a caller that forgot to would sort " +
                "the list it just edited to the bottom of the library",
            after.updatedAt > 0L
        )
    }

    @Test
    fun `an edit that changes nothing is not written`() = runTest {
        // A drag that puts a title back where it already was. Writing anyway would
        // bump `updatedAt` and reorder the library by "recently edited" for an edit
        // that did not happen, and it would put a no-op on the undo stack - so the
        // next press of Undo would take back the edit before it.
        val preferences = preferences()
        val id = preferences.createEmpty("Unchanged list")
        val before = preferences.snapshot().document(id)!!

        val mutation = preferences.mutate(id) { it }

        assertEquals(mutation?.before, mutation?.after)
        assertEquals(before.updatedAt, preferences.snapshot().document(id)!!.updatedAt)
    }

    @Test
    fun `an edit to a list that does not exist is refused rather than created`() = runTest {
        // The id is the key, so writing here would invent a list nothing indexes -
        // on disk, invisible, and in the way of the next list that takes that id.
        val preferences = preferences()

        assertNull(preferences.mutate("list9999") { it.copy(name = "ghost") })
        assertNull(preferences.snapshot().document("list9999"))
    }

    @Test
    fun `deleting a list removes it and its claim to be the last opened`() = runTest {
        val preferences = preferences()
        val id = preferences.createEmpty("Doomed list")

        preferences.delete(id)

        val library = preferences.snapshot()
        assertNull(library.document(id))
        assertNotEquals(id, library.lastOpenedId)
    }

    @Test
    fun `a deleted id is reused, and the new list does not inherit the old one`() = runTest {
        // Ids are sequential, so the next list created can take a removed one's id.
        // If the document key were left behind, that list would come back from the
        // dead holding the deleted list's contents - which reads as the app
        // restoring work rather than resurrecting it.
        val preferences = preferences()
        val first = preferences.createEmpty("First life")
        preferences.mutate(first) { document ->
            document.copy(assignment = document.assignment.withPlacement(7, "s"))
        }
        preferences.delete(first)

        val second = preferences.createEmpty("Second life")

        val revived = preferences.snapshot().document(second)
        assertEquals("Second life", revived?.name)
        assertTrue("a reused id must not carry the old list's placements", revived!!.isEmpty)
        assertEquals("the id really was reused, so this test is about that case", first, second)
    }

    @Test
    fun `restoring a snapshot is the undo, rows and entries together`() = runTest {
        // The shape a tier removal actually has: the row *and* the entries filed
        // under it, in one document. This is why undo stores a whole document
        // rather than a placement: a placement-only undo could not bring the
        // dropped entries back.
        val preferences = preferences()
        val id = preferences.createEmpty("Undo target")
        preferences.mutate(id) { document ->
            document.copy(assignment = document.assignment.withPlacement(1, "s"))
        }
        assertEquals(1, preferences.snapshot().document(id)!!.rankedCount)

        val removal = preferences.mutate(id) { document ->
            document.copy(
                tiers = document.tiers.filterNot { it.id == "s" },
                assignment = TierAssignment(
                    document.assignment.tierIdByMediaId.filterValues { it != "s" }
                )
            )
        }
        val afterRemoval = preferences.snapshot().document(id)!!
        assertEquals(0, afterRemoval.rankedCount)
        assertTrue("the row really was removed", afterRemoval.tiers.none { it.id == "s" })

        preferences.restore(removal!!.before)

        val restored = preferences.snapshot().document(id)!!
        assertEquals(mapOf(1 to "s"), restored.assignment.tierIdByMediaId)
        assertTrue("the rows come back with the entries", restored.tiers.any { it.id == "s" })
    }

    @Test
    fun `the last opened list is remembered separately from the edits`() = runTest {
        val preferences = preferences()
        val first = preferences.createEmpty("Opened first")
        val second = preferences.createEmpty("Opened second")

        preferences.setLastOpened(first)

        val library = preferences.snapshot()
        assertEquals(
            "carrying on with the list you opened is not the same as the one you last edited",
            first,
            library.recent?.id
        )
        assertEquals(first, library.lastOpenedId)
        // The other list is still there, and is not the recent one.
        assertTrue(library.document(second) != null)
        assertNotEquals(second, library.recent?.id)
    }

    @Test
    fun `an install that never made a list is not given one by the migration`() = runTest {
        // The legacy keys are absent on a fresh install. Migrating anyway would
        // create one empty list called "Tier list" that the person did not make,
        // and the library screen would open on it instead of on its empty state.
        val preferences = preferences()
        val before = preferences.snapshot()

        preferences.migrateLegacyList()

        val after = preferences.snapshot()
        assertEquals(before.documents.map { it.id }, after.documents.map { it.id })
        assertEquals(before.lastOpenedId, after.lastOpenedId)
    }

    @Test
    fun `the migration is idempotent`() = runTest {
        // It runs on the way into the screen, every time. The index is the guard:
        // once it exists there is nothing to move, so a second run must not add a
        // second copy of anything.
        val preferences = preferences()
        val id = preferences.createEmpty("Already on the new format")
        val before = preferences.snapshot().documents.map { it.id }

        preferences.migrateLegacyList()
        preferences.migrateLegacyList()

        val after = preferences.snapshot().documents.map { it.id }
        assertEquals("the migration must not add a list once the index exists", before, after)
        assertEquals("this test's own list must be in there exactly once", 1, after.count { it == id })
    }
}
