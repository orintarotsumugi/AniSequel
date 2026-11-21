package com.anisequel

import com.anisequel.ui.components.novamarkdown.NovaBlock
import com.anisequel.ui.components.novamarkdown.NovaMarkdown
import com.anisequel.ui.components.novamarkdown.novaImageTapUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a tap on a NovaMarkdown image opens.
 *
 * On AniList every image is a link: a linked one opens its target and a plain one
 * opens the picture itself. The previous renderer only wired the first, so an
 * embedded image with no target had no `clickable` modifier at all - it looked
 * like a picture and behaved like a decoration, while the same comment on the
 * site opened the image.
 *
 * The helper is pure so the rule is pinned without a Compose runtime: the
 * renderer is the only caller, and what it draws is Coil's business.
 */
class ImageTapUrlTest {

    @Test
    fun `an image with no link opens the picture itself`() {
        val image = NovaBlock.Image(url = "https://example.com/screenshot.png")

        assertEquals("https://example.com/screenshot.png", novaImageTapUrl(image))
    }

    @Test
    fun `a linked image opens the target the author gave it`() {
        val image = NovaBlock.Image(
            url = "https://example.com/cover.gif",
            targetUrl = "https://anilist.co/anime/1"
        )

        assertEquals("https://anilist.co/anime/1", novaImageTapUrl(image))
    }

    @Test
    fun `a bare image URL on its own line is tappable once the parser turns it into an embed`() {
        // The form a comment uses to post a GIF without markdown. The parser makes
        // it a block image, and from there it takes the same tap as any other.
        val image = NovaMarkdown
            .parse("https://files.catbox.moe/happy.gif")
            .single() as NovaBlock.Image

        assertEquals("https://files.catbox.moe/happy.gif", novaImageTapUrl(image))
    }
}
