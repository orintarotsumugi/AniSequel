package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hiddenSequelsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_hidden_sequels"
)

/**
 * The anime the user has chosen to stop being reminded about.
 *
 * A deliberate omission from [FilterCriteria]'s usual lifecycle: the other
 * filters are session state that the app is free to forget, but "don't show me
 * this again" is a decision the user made on purpose and would be rightly
 * annoyed to have to repeat on next launch.
 *
 * Stored as one comma-separated string rather than a set of preferences keys
 * because the set is unbounded - a user with a long list could hide dozens -
 * and DataStore has no set type. Parsing tolerates the junk that produces: a
 * blank value, stray separators, and non-numeric entries all resolve to an
 * empty set rather than throwing on a value the app cannot control.
 */
class HiddenSequelsPreferences(private val context: Context) {

    private val KEY_HIDDEN_IDS = stringPreferencesKey("hidden_media_ids")

    /** AniList media ids the user has hidden. Empty until something is read. */
    val hiddenIds: Flow<Set<Int>> = context.hiddenSequelsDataStore.data.map { preferences ->
        parseIds(preferences[KEY_HIDDEN_IDS])
    }

    /** Hides [mediaId], or unhides it if it is already hidden. */
    suspend fun toggle(mediaId: Int) {
        context.hiddenSequelsDataStore.edit { preferences ->
            val current = parseIds(preferences[KEY_HIDDEN_IDS])
            val updated = if (mediaId in current) current - mediaId else current + mediaId
            preferences[KEY_HIDDEN_IDS] = encodeIds(updated)
        }
    }

    /** Unhides everything, for a "show all again" affordance. */
    suspend fun clear() {
        context.hiddenSequelsDataStore.edit { preferences ->
            preferences.remove(KEY_HIDDEN_IDS)
        }
    }

    private fun parseIds(raw: String?): Set<Int> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .toSet()
    }

    private fun encodeIds(ids: Set<Int>): String =
        ids.sorted().joinToString(separator = ",")
}