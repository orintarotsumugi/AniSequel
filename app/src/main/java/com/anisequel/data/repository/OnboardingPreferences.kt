package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_onboarding_prefs"
)

/** Whether the first-run setup has been completed on this installation. */
class OnboardingPreferences(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
    }

    val completed: Flow<Boolean> = appContext.onboardingDataStore.data.map { preferences ->
        preferences[Keys.SETUP_COMPLETED] ?: false
    }

    suspend fun setCompleted(completed: Boolean) {
        appContext.onboardingDataStore.edit { preferences ->
            preferences[Keys.SETUP_COMPLETED] = completed
        }
    }
}
