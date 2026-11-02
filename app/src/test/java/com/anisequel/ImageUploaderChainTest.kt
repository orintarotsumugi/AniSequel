package com.anisequel

import com.anisequel.data.network.ImageHost
import com.anisequel.data.network.ImageUploader
import com.anisequel.data.network.UploadFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUploaderChainTest {

    private class FakeHost(val name: String, var succeeds: Boolean) : ImageHost {
        var calls = 0
        override suspend fun upload(bytes: ByteArray, fileName: String, mimeType: String): Result<String> {
            calls++
            return if (succeeds) Result.success("https://$name/x.png") else Result.failure(UploadFailure("$name refused"))
        }
    }

    @Test
    fun `a host that refused is not sent the next file until its cooldown ends`() = runBlocking {
        // catbox paused anonymous uploads: every photo was sent to it in full,
        // refused, and only then handed to pomf. The refusal now benches it.
        var now = 0L
        val catbox = FakeHost("catbox", succeeds = false)
        val pomf = FakeHost("pomf", succeeds = true)
        val uploader = ImageUploader(listOf(catbox, pomf), cooldownMillis = 1_000, clock = { now })

        assertEquals("https://pomf/x.png", uploader.upload(byteArrayOf(1), "a.png", "image/png").getOrNull())
        assertEquals("https://pomf/x.png", uploader.upload(byteArrayOf(1), "a.png", "image/png").getOrNull())
        assertEquals(1, catbox.calls)

        now = 1_001
        catbox.succeeds = true
        assertEquals("https://catbox/x.png", uploader.upload(byteArrayOf(1), "a.png", "image/png").getOrNull())
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
        assertEquals("https://second/x.png", uploader.upload(byteArrayOf(1), "a.png", "image/png").getOrNull())
        assertEquals(2, first.calls)
    }
}
