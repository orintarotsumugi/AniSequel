package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.quickAddDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_quick_add_prefs"
)

/**
 * User preferences for the quick add action button interactions:
 *  - Hold duration to switch from adding to Planning to Currently Watching (3s or 5s)
 *  - Enabling/disabling horizontal swipe to watch gesture
 */
class QuickAddPreferences(private val context: Context) {

    private object Keys {
        val HOLD_DURATION_SECONDS = intPreferencesKey("hold_duration_seconds")
        val SWIPE_ENABLED = booleanPreferencesKey("swipe_enabled")
    }

    data class QuickAddSettings(
        val holdDurationSeconds: Int = DEFAULT_HOLD_DURATION,
        val swipeEnabled: Boolean = true
    ) {
        val holdDurationMillis: Long get() = holdDurationSeconds * 1000L
    }

    val settings: Flow<QuickAddSettings> = context.quickAddDataStore.data.map { preferences ->
        val duration = preferences[Keys.HOLD_DURATION_SECONDS] ?: DEFAULT_HOLD_DURATION
        val validDuration = if (duration == 5) 5 else 3
        QuickAddSettings(
            holdDurationSeconds = validDuration,
            swipeEnabled = preferences[Keys.SWIPE_ENABLED] ?: true
        )
    }

    suspend fun setHoldDuration(seconds: Int) {
        val validDuration = if (seconds == 5) 5 else 3
        context.quickAddDataStore.edit { preferences ->
            preferences[Keys.HOLD_DURATION_SECONDS] = validDuration
        }
    }

    suspend fun setSwipeEnabled(enabled: Boolean) {
        context.quickAddDataStore.edit { preferences ->
            preferences[Keys.SWIPE_ENABLED] = enabled
        }
    }

    suspend fun reset() {
        context.quickAddDataStore.edit { preferences ->
            preferences.remove(Keys.HOLD_DURATION_SECONDS)
            preferences.remove(Keys.SWIPE_ENABLED)
        }
    }

    companion object {
        const val DEFAULT_HOLD_DURATION = 3
    }
}
