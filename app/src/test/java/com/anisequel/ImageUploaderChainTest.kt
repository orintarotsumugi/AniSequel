package com.anisequel

import com.anisequel.data.network.CatboxApi
import com.anisequel.data.network.ImageHost
import com.anisequel.data.network.ImageUploader
import com.anisequel.data.network.ImgbbApi
import com.anisequel.data.network.PomfApi
import com.anisequel.data.network.UploadFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUploaderChainTest {

    private class FakeHost(
        val name: String,
        var succeeds: Boolean,
        override val showsOnAniList: Boolean = true
    ) : ImageHost {
        var calls = 0
        override suspend fun upload(bytes: ByteArray, fileName: String, mimeType: String): Result<String> {
            calls++
            return if (succeeds) Result.success("https://$name/x.png") else Result.failure(UploadFailure("$name refused"))
        }
    }

    @Test
    fun `a host that refused is not sent the next file until its cooldown ends`() = runBlocking {
        // catbox paused anonymous uploads: every photo was sent to it in full,
        // refused, and only then handed to the next host. The refusal now benches it.
        var now = 0L
        val catbox = FakeHost("catbox", succeeds = false)
        val imgbb = FakeHost("imgbb", succeeds = true)
        val uploader = ImageUploader(listOf(catbox, imgbb), cooldownMillis = 1_000, clock = { now })

        assertEquals("https://imgbb/x.png", uploader.upload(byteArrayOf(1), "a.png", "image/png").getOrNull()?.url)
        assertEquals("https://imgbb/x.png", uploader.upload(byteArrayOf(1), "a.png", "image/png").getOrNull()?.url)
        assertEquals(1, catbox.calls)

        now = 1_001
        catbox.succeeds = true
        assertEquals("https://catbox/x.png", uploader.upload(byteArrayOf(1), "a.png", "image/png").getOrNull()?.url)
        assertEquals(2, catbox.calls)
    }

    @Test
    fun `when every host is benched they are all tried again rather than none`() = runBlocking {
        val first = FakeHost("first", succeeds = false)
        val second = FakeHost("second", succeeds = false)
        val uploader = ImageUploader(listOf(first, second), cooldownMillis = 60_000, clock = { 0L })

        val failed = uploader.upload(byteArrayOf(1), "a.png", "image/png")
        assertEquals("second refused", failed.exceptionOrNull()?.message)

        second.succeeds = true
        assertEquals("https://second/x.png", uploader.upload(byteArrayOf(1), "a.png", "image/png").getOrNull()?.url)
        assertEquals(2, first.calls)
    }

    /**
     * The chain asks a host whose files AniList can read before it asks pomf.
     *
     * Measured November 2026: `pomf2.lain.la` answers an HTTP/2 stream reset to any
     * GET carrying `Referer: https://anilist.co/`, while the same file with no
     * referer - which is how this app's image loader asks - returns 200. So a photo
     * that landed on pomf looked fine here and was a broken image for every reader on
     * the site the comment was posted to: the "my uploaded image doesn't work"
     * report, with the upload itself never having failed. `i.ibb.co` answers 200 to
     * the same referer, so imgbb goes second and pomf is the last resort.
     */
    @Test
    fun `the production chain puts the site-readable host ahead of the one that blocks anilist`() {
        assertEquals(
            // `::class`, not the bare names. Each host has a companion object, so
            // `CatboxApi` is a legal expression here - the companion - and comparing
            // companions against the `KClass` values the chain maps to fails on
            // every host at once rather than naming the one that moved.
            listOf(CatboxApi::class, ImgbbApi::class, PomfApi::class),
            ImageUploader.defaultHosts().map { it::class }
        )
    }

    @Test
    fun `the answer carries whether the site can show the file`() = runBlocking {
        val pomf = FakeHost("pomf", succeeds = true, showsOnAniList = false)
        val outcome = ImageUploader(listOf(pomf)).upload(byteArrayOf(1), "a.png", "image/png").getOrThrow()
        assertEquals("https://pomf/x.png", outcome.url)
        assertFalse("a host that hides files from the site must say so", outcome.showsOnAniList)

        val clean = FakeHost("imgbb", succeeds = true)
        assertTrue(ImageUploader(listOf(clean)).upload(byteArrayOf(1), "a.png", "image/png").getOrThrow().showsOnAniList)
    }

    /**
     * The rule the ordering above is built on, pinned against the shipped classes.
     *
     * A default of `true` on the interface is what keeps a new host honest: opting
     * out is the thing that needs a reason, and the reason lives in `PomfApi`.
     */
    @Test
    fun `only pomf declares itself invisible to the site`() {
        assertFalse(PomfApi().showsOnAniList)
        assertTrue(ImgbbApi().showsOnAniList)
        assertTrue(CatboxApi().showsOnAniList)
    }
}
