package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.domain.jokes.JokeDwell
import com.anisequel.domain.jokes.JokeFrequency
import com.anisequel.domain.jokes.JokeMoment
import com.anisequel.domain.jokes.JokePosition
import com.anisequel.domain.jokes.JokeScope
import com.anisequel.domain.jokes.JokeSettings
import com.anisequel.domain.jokes.JokeTone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jokeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_joke_prefs"
)

/**
 * Everything the joke toast needs to remember.
 *
 * Application-scoped and read through a [Flow] for the same reason as
 * [ArrivingPreferences]: the host in `AppNavigation` and the cards in Settings are
 * two readers of one preference, and two DataStore instances would let the
 * preview button describe a placement the toast never uses.
 *
 * The values themselves are [JokeSettings] in `domain/jokes` because they are
 * pure data with no Android import - that is what lets the timing and filtering
 * rules be pinned by a JVM test instead of by reading the host.
 *
 * Saved lines are the one thing here that the reader *created* rather than
 * chose from a list. They are stored as a separate set and deliberately survive
 * [reset]: "reset joke settings" means the switches, not the collection.
 */
class JokePreferences(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("jokes_enabled")
        val FREQUENCY = stringPreferencesKey("jokes_frequency")
        val POSITION = stringPreferencesKey("jokes_position")
        val SCOPE = stringPreferencesKey("jokes_scope")
        val DWELL = stringPreferencesKey("jokes_dwell")
        val HAPTICS = booleanPreferencesKey("jokes_haptics")
        val TONES = stringSetPreferencesKey("jokes_tones")
        val MOMENTS = stringSetPreferencesKey("jokes_moments")
        val FAVOURITES = stringSetPreferencesKey("jokes_favourites")
    }

    /** The tone storage values that mean "not chosen yet" is "all of them". */
    private val allToneValues: Set<String> = JokeTone.entries.map { it.storageValue }.toSet()

    val settings: Flow<JokeSettings> = context.jokeDataStore.data.map { preferences ->
        JokeSettings(
            enabled = preferences[Keys.ENABLED] ?: true,
            frequency = JokeFrequency.fromStorage(preferences[Keys.FREQUENCY]),
            position = JokePosition.fromStorage(preferences[Keys.POSITION]),
            scope = JokeScope.fromStorage(preferences[Keys.SCOPE]),
            dwell = JokeDwell.fromStorage(preferences[Keys.DWELL]),
            haptics = preferences[Keys.HAPTICS] ?: true,
            // An absent key is the shipped default, which is every tone. Reading
            // it as an empty set would make the first launch draw from nothing.
            tones = JokeTone.fromStorage(preferences[Keys.TONES] ?: allToneValues),
            moments = JokeMoment.fromStorage(preferences[Keys.MOMENTS] ?: emptySet())
        )
    }

    /**
     * The saved lines, as stored. A set rather than a list because order is not
     * part of the promise: the list in Settings renders them in pool order.
     */
    val favourites: Flow<Set<String>> = context.jokeDataStore.data.map { preferences ->
        preferences[Keys.FAVOURITES] ?: emptySet()
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.jokeDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setFrequency(frequency: JokeFrequency) {
        context.jokeDataStore.edit { it[Keys.FREQUENCY] = frequency.storageValue }
    }

    suspend fun setPosition(position: JokePosition) {
        context.jokeDataStore.edit { it[Keys.POSITION] = position.storageValue }
    }

    suspend fun setScope(scope: JokeScope) {
        context.jokeDataStore.edit { it[Keys.SCOPE] = scope.storageValue }
    }

    suspend fun setDwell(dwell: JokeDwell) {
        context.jokeDataStore.edit { it[Keys.DWELL] = dwell.storageValue }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.jokeDataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    /**
     * Switches one tone on or off, refusing to switch the last one off.
     *
     * Read-modify-write lives in the repository rather than in the card because
     * two fast taps would otherwise both read the same "before" value and one of
     * them would be lost. The refusal is here as well as in the UI's `enabled`
     * flag: the UI can be a build behind, and a toast with no pool is not a state
     * the reader can recover from without guessing.
     */
    suspend fun toggleTone(tone: JokeTone) {
        context.jokeDataStore.edit { preferences ->
            val current = preferences[Keys.TONES] ?: allToneValues
            val next = if (tone.storageValue in current) {
                current - tone.storageValue
            } else {
                current + tone.storageValue
            }
            if (next.isNotEmpty()) preferences[Keys.TONES] = next
        }
    }

    suspend fun toggleMoment(moment: JokeMoment) {
        context.jokeDataStore.edit { preferences ->
            val current = preferences[Keys.MOMENTS] ?: emptySet()
            preferences[Keys.MOMENTS] = if (moment.storageValue in current) {
                current - moment.storageValue
            } else {
                current + moment.storageValue
            }
        }
    }

    suspend fun toggleFavourite(line: String) {
        if (line.isBlank()) return
        context.jokeDataStore.edit { preferences ->
            val current = preferences[Keys.FAVOURITES] ?: emptySet()
            preferences[Keys.FAVOURITES] = if (line in current) {
                current - line
            } else {
                current + line
            }
        }
    }

    suspend fun clearFavourites() {
        context.jokeDataStore.edit { it.remove(Keys.FAVOURITES) }
    }

    /**
     * Back to the shipped defaults, keeping the reader signed in and keeping
     * [favourites]. The saved lines are not a setting; deleting them would be the
     * one reset nobody expects.
     */
    suspend fun reset() {
        context.jokeDataStore.edit { preferences ->
            preferences.remove(Keys.ENABLED)
            preferences.remove(Keys.FREQUENCY)
            preferences.remove(Keys.POSITION)
            preferences.remove(Keys.SCOPE)
            preferences.remove(Keys.DWELL)
            preferences.remove(Keys.HAPTICS)
            preferences.remove(Keys.TONES)
            preferences.remove(Keys.MOMENTS)
        }
    }
}
