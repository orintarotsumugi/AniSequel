package com.anisequel

import com.anisequel.data.network.BUNDLED_KLIPY_KEY
import com.anisequel.data.network.KlipyGifs
import com.anisequel.data.network.gifSearchApiFor
import com.anisequel.domain.gif.GifProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
/**
 * A keyed GIF index without a key has to say so. Klipy is not in that set any more:
 * the app carries a key for it ([BUNDLED_KLIPY_KEY]), so the factory has no
 * keyless-Klipy branch and none of Klipy's own sentences can become the grid's
 * failure. What is pinned here is the other half: the bundled key actually flows
 * through, a saved reader key replaces it, and the fallbacks that still exist
 * (GIPHY, Tenor) name themselves.
 *
 * The substitution itself is deliberate - a missing credential must not leave the
 * picker with no search - but it used to happen silently, which meant the reader
 * chose a provider, got a booru's catalogue, and had nothing to connect the two. The
 * note moved to where the substitution happens.
 */
class GifKeyFallbackTest {

    @Test
    fun `a keyed provider with no key names the index that is answering`() {
        listOf(GifProvider.GIPHY, GifProvider.TENOR).forEach { provider ->
            val api = gifSearchApiFor(provider, tenorKey = null, klipyKey = null, giphyKey = null)
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
    fun `klipy answers on the bundled key when the reader has none`() {
        val api = gifSearchApiFor(GifProvider.KLIPY, null, null, null)
        assertEquals("the default index must not ask the reader for a credential first", "Klipy", api.providerName)
        assertEquals(
            "the bundled key is what the search would send",
            BUNDLED_KLIPY_KEY,
            (api as KlipyGifs).keyInUse
        )
        assertTrue(BUNDLED_KLIPY_KEY.isNotBlank())
        assertNull("the reader was never refused anything, so there is nothing to explain", api.substitutionNote)
    }

    @Test
    fun `a saved klipy key replaces the bundled one`() {
        val api = gifSearchApiFor(GifProvider.KLIPY, null, "reader-key", null)
        assertEquals("reader-key", (api as KlipyGifs).keyInUse)
        // And clearing the field hands the bundled key back: the default keeps
        // answering rather than becoming a refusal again.
        val cleared = gifSearchApiFor(GifProvider.KLIPY, null, "   ", null)
        assertEquals(BUNDLED_KLIPY_KEY, (cleared as KlipyGifs).keyInUse)
    }

    @Test
    fun `a plain provider answers for itself with no note`() {
        assertNull(
            gifSearchApiFor(GifProvider.SAFEBOORU, null, null, null).substitutionNote
        )
        assertNull(
            gifSearchApiFor(GifProvider.NEKOS_BEST, null, null, null).substitutionNote
        )
        // The key is there, so the chosen index answers and says nothing about it.
        assertNull(
            gifSearchApiFor(GifProvider.GIPHY, null, null, "secret").substitutionNote
        )
    }
}
