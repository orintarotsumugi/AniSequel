package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.AuthRepositoryImpl
import com.anisequel.data.security.TokenCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The bearer token's repository, around the two keystore counts a wrong
 * implementation wastes.
 *
 * ## What was broken
 *
 * [AuthRepositoryImpl.getAccessToken] collects the cold [AuthRepositoryImpl.accessTokenFlow]
 * with `first()`, and the cold flow ran its migration scan *and* decrypted the
 * token - and `readToken` decrypted **again** - on every collection. Every token
 * read past the first was therefore two keystore round trips, several happening
 * inside an OkHttp worker's `runBlocking` on the launch's first request. The
 * migration scan had no latch either: each new collector re-read the disk and
 * re-decrypted just to confirm the scan had nothing to do.
 *
 * Robolectric rather than a pure JVM test: DataStore needs a Context, the same
 * shape as the other preferences tests. The cipher is a counting fake, which is
 * exactly why it is a constructor argument.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositoryImplTest {

    /** A cipher that says how many times each half ran. */
    private class CountingCipher : TokenCipher {
        var encrypts = 0
        var decrypts = 0
        private val vault = mutableMapOf<String, String>()

        override fun encrypt(plaintext: String): String {
            encrypts++
            // A pseudo-random-looking ciphertext: two encryptions of one token
            // must not collide, the way a real GCM IV produces distinct bytes.
            val ciphertext = "safe[$encrypts:${plaintext.hashCode()}]"
            vault[ciphertext] = plaintext
            return ciphertext
        }

        override fun decrypt(payload: String): String? {
            decrypts++
            return vault[payload]
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearStoredToken() = runTest {
        AuthRepositoryImpl(context, CountingCipher()).clearAccessToken()
    }

    @Test
    fun `one session decrypts one ciphertext once, however many reads`() = runTest {
        val cipher = CountingCipher()
        val repository = AuthRepositoryImpl(context, cipher)
        repository.saveAccessToken("token-a")
        assertEquals("save is one encrypt", 1, cipher.encrypts)

        assertEquals("token-a", repository.getAccessToken())
        assertEquals("token-a", repository.getAccessToken())
        assertEquals("token-a", repository.accessTokenFlow.first())

        assertEquals(
            "validate-and-read used to decrypt the same ciphertext twice per " +
                "cold collection, and re-scanned the migration on every collector; " +
                "one memoised decrypt per session is all the keystore owes this",
            1,
            cipher.decrypts
        )
    }

    @Test
    fun `a new token decrypts once, not on top of the old memo`() = runTest {
        val cipher = CountingCipher()
        val repository = AuthRepositoryImpl(context, cipher)
        repository.saveAccessToken("token-a")
        assertEquals("token-a", repository.getAccessToken())

        // A re-auth writes a fresh ciphertext; the memo keyed on the ciphertext
        // must not answer for the new bytes.
        repository.saveAccessToken("token-b")
        assertEquals("token-b", repository.getAccessToken())
        assertEquals("token-b", repository.getAccessToken())

        assertEquals(
            "each distinct ciphertext is decrypted once",
            2,
            cipher.decrypts
        )
    }
}
