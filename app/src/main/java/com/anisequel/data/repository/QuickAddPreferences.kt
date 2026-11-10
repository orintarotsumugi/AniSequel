package com.anisequel.data.repository

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
 *  - Whether a Planning add is confirmed with a tick or cancelled with a cross
 *
 * The confirmation is **on by default**. A one-tap Planning add writes to the
 * reader's real AniList before the sheet has even opened, and the button sits on
 * rows the thumb passes while scrolling - so the shipped behaviour asks first,
 * and the switch is the way back to the old speed for somebody who wants it.
 */
class QuickAddPreferences(private val context: Context) {

    private object Keys {
        val HOLD_DURATION_SECONDS = intPreferencesKey("hold_duration_seconds")
        val SWIPE_ENABLED = booleanPreferencesKey("swipe_enabled")
        val CONFIRM_ADDS = booleanPreferencesKey("confirm_adds")
    }

    data class QuickAddSettings(
        val holdDurationSeconds: Int = DEFAULT_HOLD_DURATION,
        val swipeEnabled: Boolean = true,
        val confirmAdds: Boolean = DEFAULT_CONFIRM_ADDS
    ) {
        val holdDurationMillis: Long get() = holdDurationSeconds * 1000L
    }

    val settings: Flow<QuickAddSettings> = context.quickAddDataStore.data.map { preferences ->
        val duration = preferences[Keys.HOLD_DURATION_SECONDS] ?: DEFAULT_HOLD_DURATION
        val validDuration = if (duration == 5) 5 else 3
        QuickAddSettings(
            holdDurationSeconds = validDuration,
            swipeEnabled = preferences[Keys.SWIPE_ENABLED] ?: true,
            confirmAdds = preferences[Keys.CONFIRM_ADDS] ?: DEFAULT_CONFIRM_ADDS
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

    /**
     * Whether a Planning add waits for the tick.
     *
     * Off is the old one-tap behaviour, and the card in Settings says so rather
     * than leaving the reader to discover it on their list.
     */
    suspend fun setConfirmAdds(enabled: Boolean) {
        context.quickAddDataStore.edit { preferences ->
            preferences[Keys.CONFIRM_ADDS] = enabled
        }
    }

    suspend fun reset() {
        context.quickAddDataStore.edit { preferences ->
            preferences.remove(Keys.HOLD_DURATION_SECONDS)
            preferences.remove(Keys.SWIPE_ENABLED)
            preferences.remove(Keys.CONFIRM_ADDS)
        }
    }

    companion object {
        const val DEFAULT_HOLD_DURATION = 3

        /**
         * The shipped answer: confirm first.
         *
         * Named rather than written as `true` at the read and the default, so the
         * preference, the Settings copy and any test share one fact.
         */
        const val DEFAULT_CONFIRM_ADDS = true
    }
}
