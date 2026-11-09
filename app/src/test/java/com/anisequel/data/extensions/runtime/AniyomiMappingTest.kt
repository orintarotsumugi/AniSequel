package com.anisequel.data.extensions.runtime

import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import eu.kanade.tachiyomi.animesource.model.SEpisodeImpl
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Aniyomi-to-host mapping. The video case is the regression: a hoster
 * page is not a playable file, and a file served without its resolved headers is
 * a 403, so the mapping must prefer `videoUrl` and carry the headers across.
 */
class AniyomiMappingTest {

    @Test
    fun `anime maps url to id and thumbnail to cover`() {
        val anime = SAnimeImpl(url = "/anime/1", title = "Title", thumbnail_url = "cover", description = "d")
        val mapped = AniyomiMapping.anime(anime)
        assertEquals("/anime/1", mapped.id)
        assertEquals("Title", mapped.title)
        assertEquals("cover", mapped.coverUrl)
        assertEquals("d", mapped.description)
    }

    @Test
    fun `episode number floors the source float`() {
        val episode = SEpisodeImpl(url = "/watch/1", name = "Ep", episode_number = 3.9f)
        val mapped = AniyomiMapping.episode(episode)
        assertEquals("/watch/1", mapped.id)
        assertEquals(3, mapped.number)
        assertTrue(mapped.servers.isEmpty())
    }

    @Test
    fun `video prefers the playable file over the hoster page`() {
        val video = Video(url = "https://host/page", quality = "1080p", videoUrl = "https://cdn/file.mp4")
        val server = AniyomiMapping.server(video)
        assertEquals("https://cdn/file.mp4", server.url)
        assertEquals("1080p", server.quality)
        assertEquals("1080p", server.name)
    }

    @Test
    fun `video falls back to the page when no file is resolved`() {
        assertEquals("page", AniyomiMapping.server(Video("page", "720p", null)).url)
        assertEquals("page", AniyomiMapping.server(Video("page", "720p", "null")).url)
        assertEquals("page", AniyomiMapping.server(Video("page", "720p", "  ")).url)
    }

    @Test
    fun `video headers travel with the server`() {
        val headers = Headers.Builder().add("Referer", "https://host/").build()
        val video = Video("page", "1080p", "file.mp4", headers)
        assertEquals(mapOf("Referer" to "https://host/"), AniyomiMapping.server(video).headers)
    }

    @Test
    fun `a headerless video maps to empty headers`() {
        assertTrue(AniyomiMapping.server(Video("page", "q", "file")).headers.isEmpty())
    }

    @Test
    fun `toSAnime reuses a cached instance and refreshes identity`() {
        val cached = SAnimeImpl(url = "/old", title = "Old", description = "kept")
        val dto = com.anisequel.data.extensions.SourceAnime(id = "/new", title = "New", coverUrl = null)
        val result = AniyomiMapping.toSAnime(dto, cached)
        assertEquals("/new", result.url)
        assertEquals("New", result.title)
        assertEquals("kept", result.description)
    }

    @Test
    fun `toSAnime without a cache builds a fresh anime`() {
        val dto = com.anisequel.data.extensions.SourceAnime(id = "/a", title = "T", coverUrl = "c")
        val result = AniyomiMapping.toSAnime(dto, null)
        assertEquals("/a", result.url)
        assertEquals("T", result.title)
        assertEquals("c", result.thumbnail_url)
        assertNull(result.description)
    }
}