package com.anisequel

import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.MarkdownNode
import com.anisequel.ui.components.markdown.imageTapUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a tap on a markdown image opens.
 *
 * On AniList every image is a link: a linked one opens its target and a plain one
 * opens the picture itself. This app only wired the first, so an embedded image
 * with no target had no `clickable` modifier at all - it looked like a picture and
 * behaved like a decoration, while the same comment on the site opened the image.
 *
 * The helper is pure so the rule is pinned without a Compose runtime: the renderer
 * is the only caller, and what it draws is Coil's business.
 */
class ImageTapUrlTest {

    @Test
    fun `an image with no link opens the picture itself`() {
        val image = MarkdownNode.ImageBlock(url = "https://example.com/screenshot.png")
        assertEquals("https://example.com/screenshot.png", imageTapUrl(image))
    }

    @Test
    fun `a linked image opens the target the author gave it`() {
        val image = MarkdownNode.ImageBlock(
            url = "https://example.com/cover.gif",
            targetUrl = "https://anilist.co/anime/1"
        )
        assertEquals("https://anilist.co/anime/1", imageTapUrl(image))
    }

    @Test
    fun `a bare image URL on its own line is tappable once the parser turns it into an embed`() {
        // The form a comment uses to post a GIF without markdown. The parser makes
        // it an ImageBlock, and from there it takes the same tap as any other image.
        val image = AniListMarkdownParser
            .parse("https://files.catbox.moe/happy.gif")
            .single() as MarkdownNode.ImageBlock

        assertEquals("https://files.catbox.moe/happy.gif", imageTapUrl(image))
    }
}
