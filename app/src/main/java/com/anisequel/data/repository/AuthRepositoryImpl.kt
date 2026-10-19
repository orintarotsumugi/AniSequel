package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.data.security.KeystoreTokenCipher
import com.anisequel.data.security.TokenCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anisequel_auth_prefs")

class AuthRepositoryImpl(
    private val context: Context,
    /**
     * The at-rest cipher for the bearer token, injectable so the migration and
     * the invalidation path can be tested without a real keystore.
     *
     * The default is the hardware-backed one. This interface exists because a
     * JVM test has no `AndroidKeyStore`: without it the only way to test the
     * token store would be an instrumented test on a device.
     */
    private val tokenCipher: TokenCipher = KeystoreTokenCipher()
) : AuthRepository {

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("anilist_access_token")
        private val KEY_ENCRYPTED_ACCESS_TOKEN = stringPreferencesKey("anilist_access_token_encrypted")
        private val KEY_CLIENT_ID = stringPreferencesKey("anilist_client_id")
        
        /**
         * The AniList OAuth client AniSequel authorizes against.
         *
         * This is *our own* AniList developer client, not a borrowed one. It
         * matters that it is this app's client: AniList sends the token to
         * whatever Redirect URL is registered against the client the authorize
         * request names, so authorizing against anyone else's id delivers the
         * token to their app and sign-in can never complete - the app just
         * waits for a callback that goes elsewhere.
         *
         * Fork owners can override this in Settings, which is persisted
         * separately and always wins over the value here.
         */
        const val DEFAULT_CLIENT_ID = "52542"
    }

    /**
     * Mirror of the stored token so [AuthInterceptor] can read it without
     * touching the disk. Written on every read and on every write, so it can
     * never hand out a token that a sign-out has already revoked.
     */
    @Volatile
    private var cachedToken: String? = null

    /**
     * The stored token, mirrored into [cachedToken] as it is read.
     *
     * The `onEach` is what keeps the in-memory copy warm without a blocking
     * disk read: `AuthViewModel` collects this once at startup, and the moment
     * it does, [AuthInterceptor] can attach a token from memory instead of
     * falling back to `runBlocking { getAccessToken() }` on an OkHttp thread for
     * the first request of the session - which is the very request that draws
     * the dashboard.
     *
     * `onEach` rather than a side effect inside `map`: it must fire for every
     * collector, and it has to run even for a null value so a cleared token
     * clears the cache too. Sign-out clears it explicitly as well; this is the
     * second line of defence against handing out a revoked token.
     *
     * The `flow { }` wrapper is the migration. It runs before the preferences
     * are observed, encrypts a token left by an older plaintext build, and
     * removes an unreadable one whose keystore key was invalidated. Both are
     * writes, and DataStore cannot be written from inside its own `map`.
     */
    override val accessTokenFlow: Flow<String?> = flow {
        prepareStoredToken()
        emitAll(
            context.dataStore.data.map { preferences -> readToken(preferences) }
        )
    }.onEach { token -> cachedToken = token }

    override val clientIdFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CLIENT_ID]?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID
    }

    override fun cachedAccessToken(): String? = cachedToken

    override suspend fun saveAccessToken(token: String) {
        val trimmed = token.trim()
        // Encrypt before touching either cache. A failed encryption must leave
        // the previous session exactly as it was rather than half-written, and
        // it must never fall back to storing the bearer token in plaintext.
        val encrypted = tokenCipher.encrypt(trimmed)
        cachedToken = trimmed
        context.dataStore.edit { preferences ->
            preferences[KEY_ENCRYPTED_ACCESS_TOKEN] = encrypted
            preferences.remove(KEY_ACCESS_TOKEN)
        }
    }

    override suspend fun saveClientId(clientId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CLIENT_ID] = clientId.trim()
        }
    }

    override suspend fun clearAccessToken() {
        cachedToken = null
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_ACCESS_TOKEN)
            preferences.remove(KEY_ENCRYPTED_ACCESS_TOKEN)
        }
    }

    override suspend fun getAccessToken(): String? {
        return accessTokenFlow.first().also { cachedToken = it }
    }

    override suspend fun getClientId(): String {
        return clientIdFlow.first()
    }

    /**
     * Migrates the plaintext token once, and drops an unreadable ciphertext.
     *
     * ## The migration
     *
     * Builds before encryption stored the token under [KEY_ACCESS_TOKEN]. That
     * is read once, encrypted, and removed in one transaction. If encryption
     * itself fails (no keystore on a broken device), the legacy value is left
     * alone and still served by [readToken] - losing a working session is worse
     * than the plaintext that was already there, and the next launch retries.
     *
     * ## The invalidation recovery
     *
     * Removing the device's secure lock can invalidate the keystore key. The
     * ciphertext then cannot be decrypted, and retrying it on every read would
     * leave the app permanently signed out *and* permanently writing failed
     * decryptions. The unreadable value is removed, which is the honest state:
     * there is no session on this device any more, and sign-in is the way back.
     * The equality check inside the transaction is what stops a concurrent
     * sign-in's fresh token from being deleted by a stale read.
     */
    private suspend fun prepareStoredToken() {
        val preferences = context.dataStore.data.first()

        val legacy = preferences[KEY_ACCESS_TOKEN]?.takeIf { it.isNotBlank() }
        if (legacy != null) {
            val encrypted = runCatching { tokenCipher.encrypt(legacy.trim()) }.getOrNull()
            if (encrypted != null) {
                context.dataStore.edit {
                    it[KEY_ENCRYPTED_ACCESS_TOKEN] = encrypted
                    it.remove(KEY_ACCESS_TOKEN)
                }
            }
            return
        }

        val encrypted = preferences[KEY_ENCRYPTED_ACCESS_TOKEN] ?: return
        if (tokenCipher.decrypt(encrypted) != null) return

        context.dataStore.edit {
            if (it[KEY_ENCRYPTED_ACCESS_TOKEN] == encrypted) {
                it.remove(KEY_ENCRYPTED_ACCESS_TOKEN)
            }
        }
    }

    /**
     * The token a preferences snapshot holds.
     *
     * Encrypted first, legacy plaintext second. The fallback is what keeps a
     * session alive on the one launch where the migration could not encrypt;
     * it is not a second storage format the app ever writes.
     */
    private fun readToken(preferences: Preferences): String? {
        preferences[KEY_ENCRYPTED_ACCESS_TOKEN]
            ?.let { tokenCipher.decrypt(it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return preferences[KEY_ACCESS_TOKEN]?.takeIf { it.isNotBlank() }
    }
}
