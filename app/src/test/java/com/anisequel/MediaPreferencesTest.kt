package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.CommentMediaSettings
import com.anisequel.data.repository.MediaPreferences
import com.anisequel.data.security.TokenCipher
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
    fun `unreadable ciphertext reads as no key rather than as a crash`() = runTest {
        val store = preferences()
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
    fun `reset returns every field to the default`() = runTest {
        val store = preferences()
        store.setChatView(false)
        store.setPollForNewComments(false)
        store.setTenorApiKey("a-key")
        store.reset()
        assertEquals(CommentMediaSettings(), store.settings.first())
    }
}
