package com.anisequel.data.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository shapes this app reads, pinned without the network.
 *
 * The CloudStream manifest case is the regression: a `repo.json` is a pointer to
 * one or more `plugins.json` lists, and reading it as an empty catalogue made a
 * valid repository look broken. The `.cs3` file name matters too - it used to be
 * handed to Android's APK installer, which fails every time.
 */
class ExtensionIndexTest {

    @Test
    fun `a CloudStream manifest names its plugin lists`() {
        val manifest = """
            {
              "name": "CloudX",
              "manifestVersion": 1,
              "pluginLists": [
                "https://example.test/builds/plugins.json",
                "https://example.test/builds/extra.json"
              ]
            }
        """.trimIndent()

        assertEquals(
            listOf(
                "https://example.test/builds/plugins.json",
                "https://example.test/builds/extra.json"
            ),
            ExtensionIndex.pluginListUrls(manifest)
        )
        assertEquals(ExtensionKind.CLOUDSTREAM, ExtensionIndex.kindFor(manifest, "https://example.test/repo.json"))
    }

    @Test
    fun `a CloudStream plugin keeps its cs3 file name`() {
        val body = """
            [{
              "name": "Kawanfilm",
              "internalName": "com.example.kawanfilm",
              "url": "https://example.test/builds/Kawanfilm.cs3",
              "version": 1,
              "tvTypes": ["Movie", "TvSeries"]
            }]
        """.trimIndent()

        val entries = ExtensionIndex.parse(
            body = body,
            repositoryId = "repo",
            repositoryUrl = "https://example.test/builds/plugins.json",
            kind = ExtensionKind.CLOUDSTREAM,
            content = ExtensionContent.ANIME
        )

        assertEquals(1, entries.size)
        assertEquals("Kawanfilm", entries.single().name)
        assertEquals("Kawanfilm.cs3", entries.single().fileName)
        assertEquals(ExtensionKind.CLOUDSTREAM, entries.single().kind)
    }

    @Test
    fun `an Aniyomi row carries its source names and strips the store prefix`() {
        val body = """
            [{
              "name": "Aniyomi: Gotaku",
              "pkg": "eu.kanade.tachiyomi.animeextension.en.gotaku",
              "apk": "aniyomi-en.gotaku-v14.3.apk",
              "lang": "en",
              "version": "14.3",
              "nsfw": 1,
              "sources": [{"name": "Gotaku"}]
            }]
        """.trimIndent()

        val entry = ExtensionIndex.parse(
            body = body,
            repositoryId = "repo",
            repositoryUrl = "https://example.test/repo/index.min.json",
            kind = ExtensionKind.ANIYOMI,
            content = ExtensionContent.ANIME
        ).single()

        assertEquals("Gotaku", entry.name)
        assertTrue(entry.nsfw)
        assertEquals("aniyomi-en.gotaku-v14.3.apk", entry.fileName)
        assertEquals(listOf("Gotaku"), entry.sourceNames)
    }

    @Test
    fun `a GitHub root also tries the builds branch CloudStream uses`() {
        val candidates = ExtensionIndex.candidateUrls("https://github.com/example/cloudstream-repo")

        assertTrue(candidates.any { "/builds/" in it })
        assertFalse(candidates.any { it.contains(" ") })
    }
}
