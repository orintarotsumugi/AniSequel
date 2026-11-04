package com.anisequel

import com.anisequel.data.network.gifSearchApiFor
import com.anisequel.domain.gif.GifProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
/**
 * A keyed GIF index without a key has to say so.
 *
 * The substitution itself is deliberate - a missing credential must not leave the
 * picker with no search, and Safebooru answers anime tags for nothing - but it used
 * to happen silently, which meant the reader chose Klipy, got a booru's catalogue,
 * and had nothing to connect the two. The keyed clients each carry a "no key is
 * saved" sentence in their own `search`; that branch was unreachable, because this
 * factory never builds one without a key. So the note moved to where the substitution
 * happens.
 */
class GifKeyFallbackTest {

    @Test
    fun `a keyed provider with no key names the index that is answering`() {
        listOf(GifProvider.KLIPY, GifProvider.GIPHY, GifProvider.TENOR).forEach { provider ->
            val api = gifSearchApiFor(provider, tenorKey = null, klipyKey = " ", giphyKey = "")
            assertEquals("Safebooru", api.providerName)
            val note = api.substitutionNote
            assertTrue("$provider substituted without a note", note?.isNotBlank() == true)
            assertTrue(
                "the note does not name the index the reader chose: $note",
                note!!.contains(provider.displayName)
            )
            assertTrue("the note gives no way to fix it: $note", note.contains("Settings"))
        }
    }

    @Test
    fun `a provider that answers for itself carries no note`() {
        assertNull(
            gifSearchApiFor(GifProvider.SAFEBOORU, null, null, null).substitutionNote
        )
        assertNull(
            gifSearchApiFor(GifProvider.NEKOS_BEST, null, null, null).substitutionNote
        )
        // The key is there, so the chosen index answers and says nothing about it.
        assertNull(
            gifSearchApiFor(GifProvider.KLIPY, null, "secret", null).substitutionNote
        )
    }
}
