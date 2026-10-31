package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.domain.profile.ProfileLayout
import com.anisequel.domain.profile.ProfileSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileLayoutDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_profile_layout"
)

/**
 * The reader's arrangement of their own profile, persisted.
 *
 * The order is stored as one comma-separated string rather than a string set: a set
 * has no order on the DataStore side, and the whole point of this preference is the
 * order. The hidden sections are a set because *that* one has no order by nature.
 *
 * One instance per store, application-scoped through [context] like every other
 * preference class here - the profile tab reads it and the editor writes it, and two
 * stores would let the editor preview an arrangement the page does not use.
 */
class ProfileLayoutPreferences(context: Context) {

    private val appContext: Context = context.applicationContext

    private object Keys {
        val ORDER = stringPreferencesKey("profile_section_order")
        val HIDDEN = stringSetPreferencesKey("profile_hidden_sections")
        val CHARACTERS_TWO_COLUMN = booleanPreferencesKey("profile_characters_two_column")
    }

    val layout: Flow<ProfileLayout> = appContext.profileLayoutDataStore.data.map { preferences ->
        val storedOrder = preferences[Keys.ORDER]
            ?.split(',')
            ?.map { it.trim() }
            .orEmpty()
        ProfileLayout(
            // A missing order is the shipped one; `resolvedOrder` appends anything
            // a stored order predates, so a cleared file and an old file both land
            // on a complete order.
            order = ProfileSection.fromStorageList(storedOrder)
                .ifEmpty { ProfileSection.entries.toList() },
            hidden = ProfileSection.fromStorage(preferences[Keys.HIDDEN] ?: emptySet()),
            charactersTwoColumn = preferences[Keys.CHARACTERS_TWO_COLUMN] ?: false
        )
    }

    suspend fun setOrder(order: List<ProfileSection>) {
        appContext.profileLayoutDataStore.edit { preferences ->
            preferences[Keys.ORDER] = order.joinToString(separator = ",") { it.storageValue }
        }
    }

    suspend fun setHidden(hidden: Set<ProfileSection>) {
        appContext.profileLayoutDataStore.edit { preferences ->
            preferences[Keys.HIDDEN] = hidden.map { it.storageValue }.toSet()
        }
    }

    suspend fun setCharactersTwoColumn(twoColumn: Boolean) {
        appContext.profileLayoutDataStore.edit { preferences ->
            preferences[Keys.CHARACTERS_TWO_COLUMN] = twoColumn
        }
    }

    /** Back to the shipped arrangement, which is every section, in declared order. */
    suspend fun reset() {
        appContext.profileLayoutDataStore.edit { preferences ->
            preferences.remove(Keys.ORDER)
            preferences.remove(Keys.HIDDEN)
            preferences.remove(Keys.CHARACTERS_TWO_COLUMN)
        }
    }
}
