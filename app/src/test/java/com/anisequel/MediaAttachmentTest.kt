package com.anisequel

import com.anisequel.domain.usecase.MediaAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What may be attached to a comment, and what it turns into.
 *
 * The upload is a round trip to a third party, so a wrong decision here costs the
 * reader a spinner and then a sentence they cannot act on. The limits are the
 * host's published ones, and the type rules exist because a comment is read by
 * other people on other devices.
 */
class MediaAttachmentTest {

    @Test
    fun `the image types every reader can open are accepted`() {
        listOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/avif", "image/bmp")
            .forEach { type ->
                assertEquals(
                    "an $type image at a normal size must be accepted",
                    MediaAttachment.Verdict.Accept,
                    MediaAttachment.verdictFor(type, 1_000_000L)
                )
            }
    }

    @Test
    fun `a video, a document and an unknown type are refused`() {
        listOf("video/mp4", "application/pdf", "application/octet-stream", "", null)
            .forEach { type ->
                assertEquals(
                    MediaAttachment.Verdict.UnsupportedType,
                    MediaAttachment.verdictFor(type, 1_000L)
                )
            }
    }

    @Test
    fun `the gif ceiling is lower than the file ceiling`() {
        // The host's own rule, quoted in the client's documentation: an animation
        // over 20 MB is refused there, so refusing it here turns a rejection into a
        // sentence about the reader's file before ten seconds of upload.
        assertEquals(
            MediaAttachment.Verdict.Accept,
            MediaAttachment.verdictFor("image/gif", MediaAttachment.MAX_GIF_BYTES)
        )
        assertEquals(
            MediaAttachment.Verdict.TooLarge,
            MediaAttachment.verdictFor("image/gif", MediaAttachment.MAX_GIF_BYTES + 1)
        )
        assertEquals(
            MediaAttachment.Verdict.Accept,
            MediaAttachment.verdictFor("image/png", MediaAttachment.MAX_FILE_BYTES)
        )
        assertTrue(MediaAttachment.MAX_GIF_BYTES < MediaAttachment.MAX_FILE_BYTES)
    }

    @Test
    fun `an empty file is its own answer`() {
        assertEquals(MediaAttachment.Verdict.Empty, MediaAttachment.verdictFor("image/png", 0L))
        assertEquals(MediaAttachment.Verdict.Empty, MediaAttachment.verdictFor("image/png", -1L))
    }

    @Test
    fun `the reason lines are only written for refusals`() {
        assertEquals("", MediaAttachment.reasonFor(MediaAttachment.Verdict.Accept))
        assertNotNull(MediaAttachment.reasonFor(MediaAttachment.Verdict.TooLarge))
        assertTrue(MediaAttachment.reasonFor(MediaAttachment.Verdict.UnsupportedType).isNotEmpty())
    }

    @Test
    fun `magic numbers beat whatever the provider claimed the type was`() {
        // A cloud document reports `application/octet-stream` for a JPEG; a gallery
        // labels a PNG `.jpg`. The bytes are the only witness that matters, because
        // the URL that goes into the comment is served by extension.
        assertTrue(MediaAttachment.sniffMimeType(jpeg()) == "image/jpeg")
        assertTrue(MediaAttachment.sniffMimeType(png()) == "image/png")
        assertTrue(MediaAttachment.sniffMimeType(gif()) == "image/gif")
        assertTrue(MediaAttachment.sniffMimeType(webp()) == "image/webp")
        assertTrue(MediaAttachment.sniffMimeType(avif()) == "image/avif")
        assertTrue(MediaAttachment.sniffMimeType(heic()) == "image/heif")
        assertNull("a text file is not an image", MediaAttachment.sniffMimeType("hello, world".toByteArray()))
    }

    @Test
    fun `only the phone formats need re-encoding`() {
        assertTrue(MediaAttachment.needsRecode("image/heic"))
        assertTrue(MediaAttachment.needsRecode("image/heif"))
        assertFalse("a GIF must never be re-encoded - it would lose its animation", MediaAttachment.needsRecode("image/gif"))
        assertFalse(MediaAttachment.needsRecode("image/jpeg"))
        assertFalse(MediaAttachment.needsRecode(null))
    }

    @Test
    fun `an embed carries the width the reader chose, and nothing else`() {
        assertEquals("img(https://x/a.gif)", MediaAttachment.embedMarkdown("https://x/a.gif"))
        assertEquals("img220(https://x/a.gif)", MediaAttachment.embedMarkdown("https://x/a.gif", "220"))
        assertEquals("img100%(https://x/a.gif)", MediaAttachment.embedMarkdown("https://x/a.gif", "100%"))
        // A width the parser would not read back is dropped rather than written:
        // `imgwibble(url)` is not an image, it is a line of text in somebody's
        // comment, and the author sees it only after posting.
        assertEquals("img(https://x/a.gif)", MediaAttachment.embedMarkdown("https://x/a.gif", "wibble"))
        assertEquals("img(https://x/a.gif)", MediaAttachment.embedMarkdown("https://x/a.gif", "-20"))
    }

    @Test
    fun `the stored name keeps an extension that matches the bytes`() {
        assertEquals("Screenshot.png", MediaAttachment.storageFileName("image/png", "Screenshot"))
        // The picker's name lies; the mime type the sniffer agreed with does not.
        assertEquals("photo.jpg", MediaAttachment.storageFileName("image/jpeg", "photo.png"))
        assertEquals("attachment.gif", MediaAttachment.storageFileName("image/gif", null))
        // A name is sent as a multipart part name: path separators and quotes would
        // change what the host stores, and the app has no reason to let a filename
        // decide anything.
        assertEquals(
            "etc-pass-wd.jpg",
            MediaAttachment.storageFileName("image/jpeg", "../../etc/pass wd.mp4")
        )
    }

    @Test
    fun `an embed is the site tag on one line`() {
        assertEquals(
            "img(https://files.catbox.moe/9k2p1f.gif)",
            MediaAttachment.embedMarkdown("https://files.catbox.moe/9k2p1f.gif")
        )
    }

    private fun jpeg(): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(9)
    private fun png(): ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(4)
    private fun gif(): ByteArray = "GIF89a".toByteArray() + ByteArray(6)
    private fun webp(): ByteArray = "RIFF".toByteArray() + ByteArray(4) + "WEBPVP8 ".toByteArray()
    private fun avif(): ByteArray = ByteArray(4) + "ftypavif".toByteArray() + ByteArray(4)
    private fun heic(): ByteArray = ByteArray(4) + "ftypheic".toByteArray() + ByteArray(4)
}
