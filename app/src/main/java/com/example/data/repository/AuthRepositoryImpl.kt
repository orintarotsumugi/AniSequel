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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anisequel_auth_prefs")

class AuthRepositoryImpl(private val context: Context) : AuthRepository {

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("anilist_access_token")
        private val KEY_CLIENT_ID = stringPreferencesKey("anilist_client_id")
        
        // Default AniList OAuth Client ID (or placeholder ready for user)
        const val DEFAULT_CLIENT_ID = "23668"
    }

    override val accessTokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_ACCESS_TOKEN]?.takeIf { it.isNotBlank() }
    }

    override val clientIdFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CLIENT_ID]?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID
    }

    override suspend fun saveAccessToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ACCESS_TOKEN] = token.trim()
        }
    }

    override suspend fun saveClientId(clientId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CLIENT_ID] = clientId.trim()
        }
    }

    override suspend fun clearAccessToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_ACCESS_TOKEN)
        }
    }

    override suspend fun getAccessToken(): String? {
        return accessTokenFlow.first()
    }

    override suspend fun getClientId(): String {
        return clientIdFlow.first()
    }
}
