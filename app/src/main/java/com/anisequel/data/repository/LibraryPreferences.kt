package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_library_prefs"
)

/**
 * The Library tab's own view preferences.
 *
 * ## Why the type choice is remembered
 *
 * "All" concatenates anime and manga into one grid, which is the wrong default
 * for somebody who reads both: their manga sits among their anime, split across
 * a page boundary rather than gathered. Once they have said which they want,
 * the answer is not a question any more, and asking again every visit is the
 * app failing to remember a one-tap decision.
 *
 * Only the *choice* is remembered, never the resulting data. Caching a library
 * here would duplicate the repository's, which is already keyed and is already
 * correct about when to refetch - two caches answering "what does this person
 * track" is how one of them starts lying.
 */
class LibraryPreferences(private val context: Context) {

    private object Keys {
        val TYPE = stringPreferencesKey("library_type")
    }

    /** The remembered media type, as its enum name, or null when never chosen. */
    val lastType: Flow<String?> = context.libraryPrefsDataStore.data.map { it[Keys.TYPE] }

    suspend fun setType(value: String) {
        context.libraryPrefsDataStore.edit { it[Keys.TYPE] = value }
    }

    /**
     * Clears the remembered choice, so the next visit asks again.
     *
     * Reached from the tab's own reset, and the reason it exists rather than
     * being folded into "clear filters": forgetting the type is not the same act
     * as forgetting a search, and a person who is done with manga for the season
     * wants that gone without losing a query they typed.
     */
    suspend fun clearType() {
        context.libraryPrefsDataStore.edit { it.remove(Keys.TYPE) }
    }
}