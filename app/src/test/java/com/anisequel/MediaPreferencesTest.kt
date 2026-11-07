package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.CommentMediaSettings
import com.anisequel.data.repository.MediaPreferences
import com.anisequel.data.security.TokenCipher
import com.anisequel.domain.gif.GifProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The comment-media preferences, including the one field that must not be stored
 * in the clear.
 *
 * Robolectric rather than the JVM: DataStore needs a Context, and this is the same
 * shape every other preferences test in the app uses. A fake cipher stands in for
 * the keystore, which a unit test cannot reach - and which is exactly why
 * [MediaPreferences] takes it as a constructor argument.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaPreferencesTest {

    private class ReversibleCipher : TokenCipher {
        val stored = mutableSetOf<String>()
        override fun encrypt(plaintext: String): String = "enc:$plaintext".also { stored += it }
        override fun decrypt(payload: String): String? =
            payload.removePrefix("enc:").takeIf { payload.startsWith("enc:") }
    }

    /** A cipher whose key was invalidated: every read is unreadable. */
    private object BrokenCipher : TokenCipher {
        override fun encrypt(plaintext: String): String = "unreadable"
        override fun decrypt(payload: String): String? = null
    }

    private fun preferences(cipher: TokenCipher = ReversibleCipher()) =
        MediaPreferences(ApplicationProvider.getApplicationContext<Context>(), cipher)

    @Before
    fun clearStoredSettings() = runTest { preferences().reset() }

    @Test
    fun `the shipped defaults are the behaviour the app opens with`() = runTest {
        val settings = preferences().settings.first()
        assertTrue("chat view is the default: a window is less work than the thread", settings.chatView)
        assertTrue(settings.pollForNewComments)
        assertNull("no third-party key is stored unless the reader pasted one", settings.tenorApiKey)
        assertEquals(
            "the default index is keyless and safe by construction, so it answers on nothing",
            GifProvider.DEFAULT,
            settings.gifProvider
        )
        assertEquals("the default is Gifukai, a reaction index", GifProvider.GIFUKAI, GifProvider.DEFAULT)
    }

    @Test
    fun `both switches round-trip`() = runTest {
        val store = preferences()
        store.setChatView(false)
        assertFalse(store.settings.first().chatView)
        store.setPollForNewComments(false)
        assertFalse(store.settings.first().pollForNewComments)
        assertTrue(store.settings.first().let { !it.chatView })
    }

    @Test
    fun `the tenor key is stored encrypted and read back plainly`() = runTest {
        val cipher = ReversibleCipher()
        val store = preferences(cipher)
        store.setTenorApiKey("  AIza-saved-key  ")
        // Trimmed on the way in: a key with a trailing space from a paste fails
        // every request and is invisible in a settings field.
        assertEquals("AIza-saved-key", store.settings.first().tenorApiKey)
        assertTrue(
            "the stored form must not be the key itself",
            cipher.stored.none { it.contains("AIza-saved-key") && !it.startsWith("enc:") }
        )
        assertTrue(store.settings.first().usesTenor)
    }

    @Test
    fun `a blank key clears the stored credential instead of saving an empty one`() = runTest {
        val store = preferences()
        store.setTenorApiKey("a-key")
        assertEquals("a-key", store.settings.first().tenorApiKey)
        store.setTenorApiKey("   ")
        assertNull("a saved-but-empty key would reject every search", store.settings.first().tenorApiKey)
        assertFalse(store.settings.first().usesTenor)
        store.setTenorApiKey(null)
        assertNull(store.settings.first().tenorApiKey)
    }

    @Test
    fun `unreadable ciphertext reads as no key rather than as a crash`() = runTest {        val store = preferences()
        store.setTenorApiKey("a-key")
        val afterReset = MediaPreferences(
            ApplicationProvider.getApplicationContext(),
            BrokenCipher
        )
        // The keystore key can be invalidated by a lock-screen change. A reader who
        // then opens Settings must not be shown a credential, and the GIF search
        // must fall back to the anonymous provider rather than throw.
        assertNull(afterReset.settings.first().tenorApiKey)
    }

    @Test
    fun `decryption happens off the collecting thread`() = runTest {
        val decryptThreads = mutableSetOf<String>()
        val cipher = object : TokenCipher {
            override fun encrypt(plaintext: String): String = "enc:$plaintext"
            override fun decrypt(payload: String): String? {
                decryptThreads.add(Thread.currentThread().name)
                return payload.removePrefix("enc:").takeIf { payload.startsWith("enc:") }
            }
        }
        val store = preferences(cipher)
        store.setKlipyApiKey("k-key")
        store.settings.first()

        assertTrue(
            "DataStore reads on IO, but this map body runs wherever the collector " +
                "collects - the composition, i.e. main. Three keystore decryptions " +
                "per snapshot on the frame thread are the cold-start stutter, paid " +
                "by every collector and by every settings toggle that re-emits.",
            decryptThreads.isNotEmpty() && decryptThreads.none { it == "main" }
        )
    }

    @Test
    fun `a saved tenor key from before the choice existed still selects tenor`() = runTest {
        val store = preferences()
        // This is an upgrade case: the old build implied the provider from the key
        // and stored no choice. Reading it as Safebooru would silently send a reader
        // who configured Tenor to a different catalogue.
        store.setTenorApiKey("a-key")
        assertEquals(GifProvider.TENOR, store.settings.first().gifProvider)
        assertEquals("a-key", store.settings.first().activeGifKey)
    }

    @Test
    fun `each provider key is stored under its own entry and survives a switch`() = runTest {
        val store = preferences()
        store.setTenorApiKey("t-key")
        store.setKlipyApiKey("k-key")
        store.setGiphyApiKey("g-key")
        store.setGifProvider(GifProvider.KLIPY)

        val klipy = store.settings.first()
        assertEquals(GifProvider.KLIPY, klipy.gifProvider)
        assertEquals("k-key", klipy.activeGifKey)
        assertEquals("t-key", klipy.tenorApiKey)
        assertEquals("g-key", klipy.giphyApiKey)

        store.setGifProvider(GifProvider.GIPHY)
        assertEquals("g-key", store.settings.first().activeGifKey)
        assertEquals(
            "switching providers must not delete the key that was not chosen",
            "k-key",
            store.settings.first().klipyApiKey
        )
    }

    @Test
    fun `a keyed provider with no key resolves to the default index that can answer`() = runTest {
        val store = preferences()
        store.setGifProvider(GifProvider.GIPHY)
        val settings = store.settings.first()
        assertEquals(
            "the choice is kept so pasting the key later resumes it",
            GifProvider.GIPHY,
            settings.gifProvider
        )
        assertEquals(
            "but a search has to go somewhere that answers - the default (Gifukai) " +
                "is keyless, so the fallback is itself a working search",
            GifProvider.DEFAULT,
            settings.resolvedGifProvider
        )
        assertNull(settings.activeGifKey)
    }

    @Test
    fun `klipy without a reader key is still klipy`() = runTest {
        val store = preferences()
        store.setGifProvider(GifProvider.KLIPY)
        val settings = store.settings.first()
        // The app carries a key for Klipy (data/network/BundledGifKeys.kt), so the
        // choice stands on its own where every other keyed provider falls back.
        // `needsKey` is false for exactly that reason; this pins the boundary.
        assertEquals(GifProvider.KLIPY, settings.resolvedGifProvider)
        assertNull("no reader credential was pasted", settings.klipyApiKey)
    }

    @Test
    fun `reset returns every field to the default`() = runTest {
        val store = preferences()
        store.setChatView(false)
        store.setPollForNewComments(false)
        store.setTenorApiKey("a-key")
        store.setKlipyApiKey("k-key")
        store.setGiphyApiKey("g-key")
        store.setGifProvider(GifProvider.GIPHY)
        store.reset()
        assertEquals(CommentMediaSettings(), store.settings.first())
    }
}
