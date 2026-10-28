package com.anisequel

import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierFilter
import com.anisequel.domain.tier.TierListDocument
import com.anisequel.domain.tier.TierMediaType
import com.anisequel.domain.tier.decodeMediaType
import com.anisequel.domain.tier.decodeTierListDocument
import com.anisequel.domain.tier.encodeTierListDocument
import com.anisequel.domain.tier.ofMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tier list's two pools: anime and manga.
 *
 * ## Why the separation is pinned rather than trusted
 *
 * AniList numbers anime and manga independently, so media id 1 is both "Cowboy
 * Bebop" and "Monster". A board that mixed the pools would key both titles under
 * one number in the assignment map: the second placement would overwrite the
 * first, an export would draw one title's poster under the other's name, and the
 * only evidence would be a ranking that quietly lost an entry. Every test here is
 * about a way that could happen without a crash.
 *
 * ## Why the labels are here too
 *
 * "Watching" a manga and "reading" an anime are the same AniList token
 * (`CURRENT`), so the wording cannot be derived from the status. If it is not
 * pinned, a filter sheet eventually asks a manga reader whether they are still
 * watching the book.
 */
class TierMediaTypeTest {

    private fun candidate(
        id: Int,
        title: String,
        mediaType: TierMediaType
    ) = TierCandidate(mediaId = id, title = title, mediaType = mediaType)

    // ---- The document's pool ------------------------------------------------

    @Test
    fun `a manga list survives a round trip`() {
        val document = TierListDocument(
            id = "list1",
            name = "Best manga",
            mediaType = TierMediaType.MANGA,
            assignment = TierAssignment(mapOf(1 to "s"))
        )

        val decoded = decodeTierListDocument("list1", encodeTierListDocument(document))

        assertEquals(TierMediaType.MANGA, decoded?.mediaType)
        assertEquals(document, decoded)
    }

    @Test
    fun `a document written before manga existed is an anime list`() {
        // The exact older format: what this build writes, minus the line it added.
        // A missing line must read as anime rather than as a parse failure - every
        // list written before manga was rankable *is* an anime list, and treating
        // it as unknown would make somebody's ranking unopenable after an update.
        val encoded = encodeTierListDocument(
            TierListDocument(
                id = "list1",
                name = "Anime list",
                assignment = TierAssignment(mapOf(1 to "s"))
            )
        )
        val older = encoded.split('\n').dropLast(1).joinToString("\n")

        val decoded = decodeTierListDocument("list1", older)

        assertEquals(TierMediaType.ANIME, decoded?.mediaType)
        assertEquals("Anime list", decoded?.name)
        assertEquals(mapOf(1 to "s"), decoded?.assignment?.tierIdByMediaId)
    }

    @Test
    fun `an unknown pool token is read as anime rather than dropping the list`() {
        // Forward compatibility: a later build may know a third pool. This one must
        // not read that document as empty, because "successfully loaded nothing"
        // looks exactly like the ranking having been deleted.
        val encoded = encodeTierListDocument(TierListDocument(id = "list1", name = "Mine"))
        val lines = encoded.split('\n').toMutableList()
        lines[lines.lastIndex] = "LIGHT_NOVEL"

        val decoded = decodeTierListDocument("list1", lines.joinToString("\n"))

        assertEquals(TierMediaType.ANIME, decoded?.mediaType)
        assertEquals("Mine", decoded?.name)
    }

    @Test
    fun `the pool token is read case-insensitively, and blank means anime`() {
        assertEquals(TierMediaType.MANGA, decodeMediaType(" manga "))
        assertEquals(TierMediaType.MANGA, decodeMediaType("MANGA"))
        assertEquals(TierMediaType.ANIME, decodeMediaType(null))
        assertEquals(TierMediaType.ANIME, decodeMediaType(""))
        assertEquals(TierMediaType.ANIME, decodeMediaType("not a pool"))
    }

    // ---- The two pools ------------------------------------------------------

    @Test
    fun `the same media id in both pools is two different titles`() {
        // The collision this whole feature is built around. Ids are per namespace,
        // so one pool must never be used to answer a question about the other.
        val anime = candidate(1, "Cowboy Bebop", TierMediaType.ANIME)
        val manga = candidate(1, "Monster", TierMediaType.MANGA)
        val mixed = listOf(anime, manga)

        assertEquals(listOf(anime), mixed.ofMediaType(TierMediaType.ANIME))
        assertEquals(listOf(manga), mixed.ofMediaType(TierMediaType.MANGA))
    }

    @Test
    fun `a filter refuses a title from the other pool`() {
        val anime = candidate(1, "Cowboy Bebop", TierMediaType.ANIME)
        val manga = candidate(1, "Monster", TierMediaType.MANGA)

        assertTrue(TierFilter.Default.allows(anime))
        assertFalse("an anime filter must not let a manga through", TierFilter.Default.allows(manga))
        assertTrue(TierFilter.Default.forMediaType(TierMediaType.MANGA).allows(manga))
        assertFalse(
            "a manga filter must not let an anime through",
            TierFilter.Default.forMediaType(TierMediaType.MANGA).allows(anime)
        )
    }

    // ---- Studios do not exist for manga ------------------------------------

    @Test
    fun `a manga filter ignores the anime studio selection`() {
        // AniList's `studios` connection holds publishers and magazines for manga,
        // so an animation-studio selection applied to a manga board would hide
        // every title in it. The selection is kept - it belongs to the anime board
        // it was made on - but it must not narrow the manga one.
        val animeFilter = TierFilter.Default
            .withStudiosEnabled(true)
            .withStudio("MAPPA", selected = true)
        val mangaFilter = animeFilter.forMediaType(TierMediaType.MANGA)

        assertFalse(mangaFilter.studioFilterActive)
        assertTrue(
            "a kept selection that narrows nothing must not light the filter dot",
            mangaFilter.isDefault
        )
        assertTrue(mangaFilter.allows(candidate(1, "Monster", TierMediaType.MANGA)))
        assertEquals(setOf("MAPPA"), mangaFilter.studios)

        // And it is still the reader's filter when they go back to anime.
        assertTrue(animeFilter.forMediaType(TierMediaType.ANIME).studioFilterActive)
    }

    @Test
    fun `changing pool keeps the rest of the reader's filter`() {
        val filter = TierFilter.Default
            .withGenre("Action", selected = true)
            .withQuery("titan")
            .withListStatusHidden("COMPLETED", hidden = true)

        val manga = filter.forMediaType(TierMediaType.MANGA)

        assertEquals(TierMediaType.MANGA, manga.mediaType)
        assertEquals(filter.genres, manga.genres)
        assertEquals(filter.query, manga.query)
        assertEquals(filter.hiddenListStatuses, manga.hiddenListStatuses)
        assertEquals(filter.hiddenAiringStatuses, manga.hiddenAiringStatuses)
    }

    // ---- The wording --------------------------------------------------------

    @Test
    fun `a manga board asks about reading, not watching`() {
        val mangaList = TierMediaType.MANGA.listStatusOptions.associate { it.value to it.label }
        val animeList = TierMediaType.ANIME.listStatusOptions.associate { it.value to it.label }

        assertEquals("Reading", mangaList["CURRENT"])
        assertEquals("Rereading", mangaList["REPEATING"])
        assertFalse("a manga filter must not ask whether you watched a book", "Watching" in mangaList.values)
        assertEquals("Watching", animeList["CURRENT"])
        assertEquals("Rewatching", animeList["REPEATING"])
    }

    @Test
    fun `a manga is published, not aired`() {
        val manga = TierMediaType.MANGA.airingStatusOptions.associate { it.value to it.label }
        val anime = TierMediaType.ANIME.airingStatusOptions.associate { it.value to it.label }

        assertEquals("Publishing", manga["RELEASING"])
        assertEquals("Not yet published", manga["NOT_YET_RELEASED"])
        assertEquals("Publishing", TierMediaType.MANGA.releaseSectionTitle)
        assertEquals("Releasing", anime["RELEASING"])
        assertEquals("Not yet aired", anime["NOT_YET_RELEASED"])
        assertEquals("Airing", TierMediaType.ANIME.releaseSectionTitle)
    }

    @Test
    fun `both pools share AniList's status vocabulary`() {
        // Only the labels differ. A filter that invented a manga-only token would
        // match nothing, because the value is compared against the list entry's own
        // uppercase status.
        assertEquals(
            TierMediaType.ANIME.listStatusOptions.map { it.value },
            TierMediaType.MANGA.listStatusOptions.map { it.value }
        )
        assertEquals(
            TierMediaType.ANIME.airingStatusOptions.map { it.value },
            TierMediaType.MANGA.airingStatusOptions.map { it.value }
        )
    }
}
