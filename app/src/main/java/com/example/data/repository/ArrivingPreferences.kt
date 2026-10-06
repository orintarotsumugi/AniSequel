package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.arrivingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_arriving_prefs"
)

/**
 * What the dashboard's "Currently arriving" section shows, and how it draws.
 *
 * Two knobs because they answer different questions. Hiding the section is for
 * someone who does not want the airing-and-upcoming rows to sit above search at
 * all; compact is for someone who wants the information but not the poster
 * cards - a lighter page that loads no artwork, which is where both the data and
 * the battery spend their money.
 *
 * Read through a [Flow] like [RefreshIntervalPreferences.interval] rather than a
 * stored value, so a change made in Settings reaches the dashboard without
 * anything having to be rebuilt. The demo dashboard and the public-profile
 * dashboard construct without a preferences object and fall back to the shipped
 * defaults: they are built for one visit, not for remembering a person.
 */
class ArrivingPreferences(private val context: Context) {

    private object Keys {
        val SHOW_ARRIVING_SECTION = booleanPreferencesKey("show_arriving_section")
        val COMPACT_ARRIVING_CARDS = booleanPreferencesKey("compact_arriving_cards")
    }

    /**
     * Both preferences at once, so the dashboard can apply them in one read and
     * the composition is never split between a hidden header and a shown row.
     */
    data class ArrivingSettings(
        val showArrivingSection: Boolean = true,
        val compactArrivingCards: Boolean = false
    )

    val settings: Flow<ArrivingSettings> = context.arrivingDataStore.data.map { preferences ->
        ArrivingSettings(
            showArrivingSection = preferences[Keys.SHOW_ARRIVING_SECTION] ?: true,
            compactArrivingCards = preferences[Keys.COMPACT_ARRIVING_CARDS] ?: false
        )
    }

    suspend fun setShowArrivingSection(enabled: Boolean) {
        context.arrivingDataStore.edit { preferences ->
            preferences[Keys.SHOW_ARRIVING_SECTION] = enabled
        }
    }

    suspend fun setCompactArrivingCards(compact: Boolean) {
        context.arrivingDataStore.edit { preferences ->
            preferences[Keys.COMPACT_ARRIVING_CARDS] = compact
        }
    }

    /** Back to the shipped defaults. */
    suspend fun reset() {
        context.arrivingDataStore.edit { preferences ->
            preferences.remove(Keys.SHOW_ARRIVING_SECTION)
            preferences.remove(Keys.COMPACT_ARRIVING_CARDS)
        }
    }
}