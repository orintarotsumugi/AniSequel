package com.anisequel

import com.anisequel.domain.tier.StandardTiers
import com.anisequel.domain.tier.TIER_LIST_BASE_NAME
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierListDocument
import com.anisequel.domain.tier.TierMediaType
import com.anisequel.domain.tier.decodeAssignment
import com.anisequel.domain.tier.decodeDocumentIds
import com.anisequel.domain.tier.decodeTierListDocument
import com.anisequel.domain.tier.decodeTiers
import com.anisequel.domain.tier.encodeAssignment
import com.anisequel.domain.tier.encodeDocumentIds
import com.anisequel.domain.tier.encodeTierListDocument
import com.anisequel.domain.tier.encodeTiers
import com.anisequel.domain.tier.legacyTierListDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the tier list's on-disk format.
 *
 * This file holds somebody's afternoon. A parse that throws on one bad value
 * does not fail loudly - it fails by loading an *empty* list, and the person
 * finds out when the tier list they built is gone and there is nothing in the
 * UI that says why. So the behaviour that matters is: skip the bad part, keep
 * the rest, and never silently return "nothing".
 *
 * The format is now several documents rather than one list, which is why the
 * cases below cover a document and the index as well as the two fields they are
 * built from. The escaping cases are not hypothetical: a tier renamed to
 * `S, best of` used to produce a field that split in two, and the row came back
 * renamed with the list shifted around it.
 */
class TierListSerialisationTest {

    @Test
    fun `an unwritten list loads the standard tiers`() {
        assertEquals(StandardTiers, decodeTiers(null))
        assertEquals(StandardTiers, decodeTiers(""))
        assertEquals(StandardTiers, decodeTiers("   "))
    }

    @Test
    fun `tiers survive a round trip`() {
        val custom = listOf(
            TierDefinition("s", "Peak", 0xFFE5484D),
            TierDefinition("fav", "Favourites", 0xFF30A46C)
        )

        assertEquals(custom, decodeTiers(encodeTiers(custom)))
    }

    @Test
    fun `a rename keeps the id, so nothing filed under it is orphaned`() {
        // This is the whole reason the id is stored separately from the label:
        // renaming "A" to "Excellent" must not detach every entry filed under A.
        val renamed = StandardTiers.map { if (it.id == "a") it.copy(label = "Excellent") else it }

        val decoded = decodeTiers(encodeTiers(renamed))
        assertEquals("Excellent", decoded.first { it.id == "a" }.label)
    }

    @Test
    fun `a colour survives as the packed value it was stored from`() {
        val decoded = decodeTiers(encodeTiers(listOf(TierDefinition("x", "X", 0xFF0091FF))))

        assertEquals(0xFF0091FFL, decoded.single().colorArgb)
    }

    @Test
    fun `one unreadable field does not lose the others`() {
        // A single corrupt entry must not cost the whole list - that is the
        // difference between a bad row and a deleted afternoon.
        val good = "Peak|4294906125|s"
        val bad = "this line has no separators at all"

        val decoded = decodeTiers("$good${','}$bad")

        assertEquals(1, decoded.size)
        assertEquals("Peak", decoded.single().label)
    }

    @Test
    fun `a file with no readable tiers falls back to the standard ones`() {
        // Falling back to nothing would look exactly like "your list was
        // deleted". Showing the template is the recoverable outcome.
        assertEquals(StandardTiers, decodeTiers("garbage|||,,,|||"))
    }

    @Test
    fun `an empty label or id is dropped rather than drawn blank`() {
        // "Good" first, so a reader of the failure sees that only the bad rows
        // were lost - and the assertion is "the good one survived", which fails
        // if the whole file is rejected, rather than the weaker "nothing is
        // blank", which passes on an empty list.
        val decoded = decodeTiers("Peak|123|s,|123|x,,y|123|")

        assertEquals(listOf("Peak"), decoded.map { it.label })
    }

    @Test
    fun `an assignment survives a round trip`() {
        val assignment = TierAssignment(mapOf(21 to "s", 30013 to "fav", 7 to "s"))

        assertEquals(assignment, decodeAssignment(encodeAssignment(assignment)))
    }

    @Test
    fun `an empty assignment is an empty assignment`() {
        assertEquals(TierAssignment(), decodeAssignment(null))
        assertEquals(TierAssignment(), decodeAssignment(""))
        assertEquals(TierAssignment(), decodeAssignment("   "))
    }

    @Test
    fun `a truncated entry is skipped, not guessed at`() {
        // "21:s" then a dangling "30013" with no tier. Guessing the tier would
        // file a title somewhere the person never put it.
        val decoded = decodeAssignment("21:s;30013")

        assertEquals(mapOf(21 to "s"), decoded.tierIdByMediaId)
    }

    @Test
    fun `a non-numeric media id is skipped`() {
        assertEquals(TierAssignment(), decodeAssignment("abc:s"))
    }

    // ---- Escaping ----------------------------------------------------------

    @Test
    fun `a tier named with the format's own separators keeps its name`() {
        // The failure this pins: renaming a tier to "S, best of" split the field
        // in two, both halves failed to parse, and the row came back under a
        // mangled name with the rest of the list shifted around it.
        val awkward = listOf(
            TierDefinition("s", "S, best of", 0xFFE5484D),
            TierDefinition("a", "A|B;C", 0xFFF76B15)
        )

        assertEquals(awkward, decodeTiers(encodeTiers(awkward)))
    }

    @Test
    fun `a backslash in a label survives as one backslash`() {
        // Escaping the escape character is the step that is easy to do second and
        // get wrong: escape the comma first and this label gains one backslash
        // per round trip.
        val label = "C:\\anime"
        val tier = TierDefinition("s", label, 0xFFE5484D)

        val once = decodeTiers(encodeTiers(listOf(tier)))
        val twice = decodeTiers(encodeTiers(once))

        assertEquals(label, once.single().label)
        assertEquals(label, twice.single().label)
    }

    @Test
    fun `a label with the separators does not swallow the tiers beside it`() {
        // Two tiers, so the assertion is about the *count* as well as the label:
        // the old format dropped the second tier's entry entirely.
        val tiers = listOf(
            TierDefinition("s", "Peak, honestly", 0xFFE5484D),
            TierDefinition("a", "Fine", 0xFFF76B15)
        )

        val decoded = decodeTiers(encodeTiers(tiers))

        assertEquals(2, decoded.size)
        assertEquals("Peak, honestly", decoded[0].label)
        assertEquals("Fine", decoded[1].label)
    }

    // ---- Documents ---------------------------------------------------------

    @Test
    fun `a document survives a round trip`() {
        val document = TierListDocument(
            id = "list1",
            name = "Best of 2024",
            tiers = listOf(TierDefinition("s", "Peak", 0xFFE5484D)),
            assignment = TierAssignment(mapOf(1 to "s", 2 to "s")),
            updatedAt = 1_700_000_000_000L
        )

        assertEquals(document, decodeTierListDocument("list1", encodeTierListDocument(document)))
    }

    @Test
    fun `a document's name may contain a newline without breaking the format`() {
        // Documents are line-based, so a name that could contain a real newline
        // would turn one document into two. It is escaped like every other
        // separator, which is what makes that impossible rather than unlikely.
        val document = TierListDocument(id = "list1", name = "First\nSecond")

        val decoded = decodeTierListDocument("list1", encodeTierListDocument(document))

        assertEquals("First\nSecond", decoded?.name)
    }

    @Test
    fun `a document whose name line is blank keeps its ranking`() {
        // The name is a label the person can set again in one tap; the placements
        // are the work. Dropping the whole document to fix the label would be
        // throwing away the afternoon to tidy up a title.
        val raw = listOf(
            "   ",
            "0",
            "Peak|${0xFFE5484D}|s",
            "21:s"
        ).joinToString("\n")

        val decoded = decodeTierListDocument("list1", raw)

        assertEquals(TIER_LIST_BASE_NAME, decoded?.name)
        assertEquals(mapOf(21 to "s"), decoded?.assignment?.tierIdByMediaId)
    }

    @Test
    fun `a document with no content is no document`() {
        // Null is reserved for "this key holds nothing". A list that genuinely
        // does not exist is the only case the library may drop silently.
        assertNull(decodeTierListDocument("list1", null))
        assertNull(decodeTierListDocument("list1", "   "))
        assertNull(decodeTierListDocument("", "anything"))
    }

    @Test
    fun `a document written by an older build keeps what it has`() {
        // Three lines: there is no count of when it was written. It reads as
        // never-updated rather than as a parse failure, which sorts it to the
        // bottom of the library instead of making it unopenable.
        val raw = listOf("Old list", "Peak|${0xFFE5484D}|s").joinToString("\n")

        val decoded = decodeTierListDocument("list1", raw)

        assertEquals("Old list", decoded?.name)
        assertEquals(mapOf<Int, String>(), decoded?.assignment?.tierIdByMediaId)
        assertEquals(0L, decoded?.updatedAt)
        // Manga rankings did not exist when five lines were the whole format, so a
        // document without the media-type line is an anime list by definition. An
        // unknown token is treated the same way: a build that learns a third type
        // must not read every older document as empty or unopenable.
        assertEquals(TierMediaType.ANIME, decoded?.mediaType)
    }

    @Test
    fun `the id comes from the key rather than the value`() {
        // A document whose value named a different list would be one that cannot
        // be found again, which is why the id is not written into it at all.
        val document = TierListDocument(id = "list1", name = "Mine")

        val decoded = decodeTierListDocument("list7", encodeTierListDocument(document))

        assertEquals("list7", decoded?.id)
        assertNotEquals(document, decoded)
    }

    // ---- Migrating the pre-library install ---------------------------------

    @Test
    fun `an install with no list migrates nothing`() {
        // Byte-identical to the empty state of a fresh install. Returning an empty
        // document here would greet a new user with one list called "Tier list"
        // that they did not make, and the library would open on it.
        assertNull(legacyTierListDocument(tiers = null, assignment = null))
    }

    @Test
    fun `a list made before lists were documents migrates with its placements`() {
        val migrated = legacyTierListDocument(
            tiers = "Peak|4294906125|s,Excellent|4294927893|a",
            assignment = "21:s;30013:a"
        )

        assertEquals(listOf("Peak", "Excellent"), migrated?.tiers?.map { it.label })
        assertEquals(mapOf(21 to "s", 30013 to "a"), migrated?.assignment?.tierIdByMediaId)
        // Named and identified like any other list, because from the library's
        // point of view it is one - there is no "migrated" flag to explain later.
        assertEquals(TIER_LIST_BASE_NAME, migrated?.name)
        assertEquals("list1", migrated?.id)
    }

    @Test
    fun `only the tiers being customised is still a list worth keeping`() {
        // Somebody who renamed their rows and placed nothing yet.
        val migrated = legacyTierListDocument(tiers = "Peak|4294906125|s", assignment = null)

        assertEquals(listOf("Peak"), migrated?.tiers?.map { it.label })
        assertEquals(0, migrated?.rankedCount)
    }

    @Test
    fun `unreadable tiers do not cost the placements`() {
        // The placements are the work; the rows are one tap to rename. Dropping
        // the document because its first line is unreadable would throw away the
        // afternoon to tidy up the labels.
        val migrated = legacyTierListDocument(tiers = "garbage|||", assignment = "21:s")

        assertEquals(StandardTiers, migrated?.tiers)
        assertEquals(mapOf(21 to "s"), migrated?.assignment?.tierIdByMediaId)
    }

    // ---- The index ---------------------------------------------------------

    @Test
    fun `the index survives a round trip in order`() {
        val ids = listOf("list1", "list2", "list3")

        assertEquals(ids, decodeDocumentIds(encodeDocumentIds(ids)))
    }

    @Test
    fun `an index with no entries is empty`() {
        assertEquals(emptyList<String>(), decodeDocumentIds(null))
        assertEquals(emptyList<String>(), decodeDocumentIds(""))
        assertEquals(emptyList<String>(), decodeDocumentIds(" , , "))
    }

    @Test
    fun `the index drops duplicates and blank entries`() {
        // A duplicated id would show the same list twice and delete it halfway.
        assertEquals(listOf("list1", "list2"), decodeDocumentIds("list1,list2,list1, ,list2"))
    }
}
