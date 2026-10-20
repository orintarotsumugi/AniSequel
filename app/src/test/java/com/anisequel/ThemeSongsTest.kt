package com.anisequel

import com.anisequel.data.model.themeSongsSummary
import com.anisequel.data.network.AnimeThemeDto
import com.anisequel.data.network.AnimeThemeEntryDto
import com.anisequel.data.network.AnimeThemeSongDto
import com.anisequel.data.network.AnimeThemeVideoDto
import com.anisequel.data.network.toThemeSongs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The anime title page's opening/ending row, mapped from AnimeThemes' JSON.
 *
 * The section is the only part of the page fed by a service other than AniList,
 * and the response has three shapes that bite in review rather than on a device:
 * a theme whose `sequence` is null while its slug is `OP1`, a theme that carries
 * several broadcast versions under one slug, and a theme whose entries have no
 * video at all. All three are ordinary in the fetched examples, so they are pinned
 * here where a network is not needed.
 */
class ThemeSongsTest {

    private fun theme(
        type: String?,
        slug: String? = null,
        sequence: Int? = null,
        title: String? = null,
        videos: List<String?> = listOf("https://v.animethemes.moe/x.webm")
    ) = AnimeThemeDto(
        type = type,
        slug = slug,
        sequence = sequence,
        song = title?.let { AnimeThemeSongDto(it) },
        animethemeentries = videos.map { AnimeThemeEntryDto(listOf(AnimeThemeVideoDto(it))) }
    )

    @Test
    fun `openings sort before endings and keep their own labels`() {
        val songs = listOf(
            theme("ED", slug = "ED1"),
            theme("OP", slug = "OP2"),
            theme("OP", slug = "OP1")
        ).toThemeSongs(fallbackArt = "cover")

        assertEquals(listOf("OP1", "OP2", "ED1"), songs.map { it.label })
        assertEquals(listOf(true, true, false), songs.map { it.isOpening })
    }

    @Test
    fun `a null sequence falls back to the slug, then to the type`() {
        // Documented as nullable, and the fetched examples include an ED whose
        // sequence is null while its slug is ED1.
        assertEquals(
            "ED1",
            listOf(theme("ED", slug = "ED1", sequence = null))
                .toThemeSongs(null).single().label
        )
        assertEquals(
            "OP2",
            listOf(theme("OP", sequence = 2))
                .toThemeSongs(null).single().label
        )
        assertEquals(
            "OP",
            listOf(theme("OP"))
                .toThemeSongs(null).single().label
        )
    }

    @Test
    fun `several versions of one theme are one card`() {
        val songs = listOf(
            theme("OP", slug = "OP1", videos = listOf("a.webm", "b.webm")),
            theme("OP", slug = "OP1", videos = listOf("c.webm"))
        ).toThemeSongs(null)

        assertEquals(1, songs.size)
        // The first playable video wins; the card has room for one.
        assertEquals("a.webm", songs.single().videoUrl)
    }

    @Test
    fun `a theme without a video is still listed`() {
        val song = theme("OP", slug = "OP1", title = "Guren no Yumiya", videos = listOf(null))
            .let { listOf(it).toThemeSongs(null).single() }

        assertNull(song.videoUrl)
        assertEquals("Guren no Yumiya", song.title)
    }

    @Test
    fun `anything that is not an opening or ending is dropped`() {
        assertEquals(
            emptyList<Any>(),
            listOf(theme("IN", slug = "IN1"), theme(null, slug = "OP9"))
                .toThemeSongs(null)
        )
    }

    @Test
    fun `the card art is the title's own, not the service's cover`() {
        val songs = listOf(theme("OP", slug = "OP1")).toThemeSongs("https://anilist/banner.jpg")

        assertEquals("https://anilist/banner.jpg", songs.single().artworkUrl)
    }

    @Test
    fun `the header counts openings and endings separately`() {
        val songs = listOf(
            theme("OP", slug = "OP1"),
            theme("OP", slug = "OP2"),
            theme("ED", slug = "ED1"),
            theme("ED", slug = "ED2")
        ).toThemeSongs(null)

        assertEquals("2 openings · 2 endings", songs.themeSongsSummary())
        assertEquals("1 opening", songs.take(1).themeSongsSummary())
        assertEquals("1 ending", songs.drop(2).take(1).themeSongsSummary())
    }
}
