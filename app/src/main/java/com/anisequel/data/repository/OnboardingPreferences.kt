package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
        // The launch gate waits for this flow before it draws the app, so an
        // unreadable onboarding file would otherwise be a permanent splash - the
        // same failure the theme and lock reads now survive. "Not completed" is
        // the safe default here: the reader sees the setup rather than a blank
        // screen, and a transient read failure costs one extra setup, which is
        // recoverable. It does not silently mark the setup done: the dialog stays
        // the visible surface if the final write also fails.
        .catch { error ->
            if (error is IOException) emit(false) else throw error
        }

    suspend fun setCompleted(completed: Boolean) {
        appContext.onboardingDataStore.edit { preferences ->
            preferences[Keys.SETUP_COMPLETED] = completed
        }
    }
}
