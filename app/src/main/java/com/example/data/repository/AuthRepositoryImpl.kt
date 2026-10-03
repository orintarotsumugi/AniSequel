package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anisequel_auth_prefs")

class AuthRepositoryImpl(private val context: Context) : AuthRepository {

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("anilist_access_token")
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
     */
    override val accessTokenFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[KEY_ACCESS_TOKEN]?.takeIf { it.isNotBlank() } }
        .onEach { token -> cachedToken = token }

    override val clientIdFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CLIENT_ID]?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID
    }

    override fun cachedAccessToken(): String? = cachedToken

    override suspend fun saveAccessToken(token: String) {
        val trimmed = token.trim()
        cachedToken = trimmed
        context.dataStore.edit { preferences ->
            preferences[KEY_ACCESS_TOKEN] = trimmed
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
        }
    }

    override suspend fun getAccessToken(): String? {
        return accessTokenFlow.first().also { cachedToken = it }
    }

    override suspend fun getClientId(): String {
        return clientIdFlow.first()
    }
}
