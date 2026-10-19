package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appLockDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_app_lock_prefs"
)

/**
 * Whether the app asks for the device's own biometric or PIN before showing the
 * reader's list.
 *
 * Off by default: AniSequel is a personal app, and a lock that appears without
 * being asked for is a wall between someone and their own data. It is worth
 * offering because the app's whole value is a private watch history, and a
 * phone handed to someone else should not hand over that history with it.
 *
 * The preference is the only thing stored here. There is deliberately no
 * "unlocked" flag: a lock that survives a process restart would let a phone
 * that was unlocked once stay unlocked forever.
 */
class AppLockPreferences(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("app_lock_enabled")
    }

    val enabled: Flow<Boolean> = context.appLockDataStore.data.map { preferences ->
        preferences[Keys.ENABLED] ?: false
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.appLockDataStore.edit { preferences ->
            preferences[Keys.ENABLED] = enabled
        }
    }
}
