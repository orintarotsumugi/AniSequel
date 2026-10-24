package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.domain.jokes.JokeFrequency
import com.anisequel.domain.jokes.JokePosition
import com.anisequel.domain.jokes.JokeScope
import com.anisequel.domain.jokes.JokeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jokeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_joke_prefs"
)

/**
 * The joke toast's four choices.
 *
 * Application-scoped and read through a [Flow] for the same reason as
 * [ArrivingPreferences]: the host in `AppNavigation` and the card in Settings are
 * two readers of one preference, and two DataStore instances would let the
 * preview button describe a placement the toast never uses.
 *
 * The values themselves are [JokeSettings] in `domain/jokes` because they are
 * pure data with no Android import - that is what lets the timing rules be
 * pinned by a JVM test instead of by reading the host.
 */
class JokePreferences(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("jokes_enabled")
        val FREQUENCY = stringPreferencesKey("jokes_frequency")
        val POSITION = stringPreferencesKey("jokes_position")
        val SCOPE = stringPreferencesKey("jokes_scope")
    }

    val settings: Flow<JokeSettings> = context.jokeDataStore.data.map { preferences ->
        JokeSettings(
            enabled = preferences[Keys.ENABLED] ?: true,
            frequency = JokeFrequency.fromStorage(preferences[Keys.FREQUENCY]),
            position = JokePosition.fromStorage(preferences[Keys.POSITION]),
            scope = JokeScope.fromStorage(preferences[Keys.SCOPE])
        )
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

    /** Back to the shipped defaults, keeping the reader signed in. */
    suspend fun reset() {
        context.jokeDataStore.edit { preferences ->
            preferences.remove(Keys.ENABLED)
            preferences.remove(Keys.FREQUENCY)
            preferences.remove(Keys.POSITION)
            preferences.remove(Keys.SCOPE)
        }
    }
}
