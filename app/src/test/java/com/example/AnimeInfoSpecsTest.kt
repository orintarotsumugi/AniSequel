package com.example

import com.example.data.model.MediaNode
import com.example.data.model.MediaRanking
import com.example.data.model.MediaTag
import com.example.data.model.MediaTitle
import com.example.data.model.MediaTrailer
import com.example.data.model.MissedSequel
import com.example.data.model.RelationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests verifying data integrity for the Anime Info & Specs tab.
 */
class AnimeInfoSpecsTest {

    @Test
    fun `native title is preserved for Japanese script display`() {
        val entry = MissedSequel(
            parentId = 1,
            parentTitle = "Parent Show",
            relationType = RelationKind.SEQUEL.apiValue,
            sequelMedia = MediaNode(
                id = 2,
                title = MediaTitle(
                    english = "Attack on Titan Final Season",
                    romaji = "Shingeki no Kyojin The Final Season",
                    native = "進撃の巨人 The Final Season"
                )
            )
        )

        assertEquals("進撃の巨人 The Final Season", entry.nativeTitle)
        assertEquals("Shingeki no Kyojin The Final Season", entry.romajiTitle)
        assertEquals("Attack on Titan Final Season", entry.englishTitle)
    }

    @Test
    fun `trailer youtube url is correctly constructed from trailer ID`() {
        val entry = MissedSequel(
            parentId = 1,
            parentTitle = "Parent Show",
            relationType = RelationKind.SEQUEL.apiValue,
            sequelMedia = MediaNode(
                id = 2,
                trailer = MediaTrailer(id = "dQw4w9WgXcQ", site = "youtube")
            )
        )

        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", entry.trailerUrl)
    }

    @Test
    fun `trailer returns null for non-youtube sources or empty id`() {
        val nonYt = MissedSequel(
            parentId = 1,
            parentTitle = "Parent",
            sequelMedia = MediaNode(
                id = 2,
                trailer = MediaTrailer(id = "123", site = "dailymotion")
            )
        )
        assertNull(nonYt.trailerUrl)

        val emptyId = MissedSequel(
            parentId = 1,
            parentTitle = "Parent",
            sequelMedia = MediaNode(
                id = 2,
                trailer = MediaTrailer(id = "", site = "youtube")
            )
        )
        assertNull(emptyId.trailerUrl)
    }

    @Test
    fun `rankings are surfaced with rank numbers and context`() {
        val rankings = listOf(
            MediaRanking(id = 1, rank = 1, context = "Highest Rated All Time"),
            MediaRanking(id = 2, rank = 5, context = "Popular 2024")
        )
        val entry = MissedSequel(
            parentId = 1,
            parentTitle = "Parent Show",
            relationType = RelationKind.SEQUEL.apiValue,
            sequelMedia = MediaNode(
                id = 2,
                rankings = rankings
            )
        )

        assertEquals(2, entry.rankings.size)
        assertEquals("#1 Highest Rated All Time", entry.topRanking)
    }

    @Test
    fun `thematic tags maintain rank percentages and exclude spoiler tags`() {
        val tags = listOf(
            MediaTag(id = 1, name = "Action", rank = 95, isMediaSpoiler = false),
            MediaTag(id = 2, name = "Secret Plot", rank = 90, isMediaSpoiler = true),
            MediaTag(id = 3, name = "Sci-Fi", rank = 80, isMediaSpoiler = false)
        )
        val entry = MissedSequel(
            parentId = 1,
            parentTitle = "Parent Show",
            relationType = RelationKind.SEQUEL.apiValue,
            sequelMedia = MediaNode(
                id = 2,
                tags = tags
            )
        )

        assertEquals(listOf("Action", "Sci-Fi"), entry.topTags)
    }
}
