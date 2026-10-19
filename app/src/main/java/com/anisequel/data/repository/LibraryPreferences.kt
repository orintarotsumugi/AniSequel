package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_library_prefs"
)

/**
 * The Library page's own view preferences.
 *
 * ## Why the choices are remembered
 *
 * "All" concatenates anime and manga into one grid, which is the wrong default
 * for somebody who reads both: their manga sits among their anime, split across
 * a page boundary rather than gathered. Once they have said which they want,
 * the answer is not a question any more, and asking again every visit is the
 * app failing to remember a one-tap decision.
 *
 * The same is true of the status filter and the sort. They used to be plain
 * `remember`-style state inside the tab, which dies with it: the profile screen
 * switches tabs through an `AnimatedContent`, and that disposes the tab it
 * animates away from. So every trip to Activity and back reset a carefully
 * narrowed "Completed, newest first" view back to "All, list order" - a decision
 * the reader had already made, asked again because the state lived somewhere
 * that could not outlive a transition. DataStore is where the type already
 * lived, and the rest now lives beside it for the same reason.
 *
 * Only the *choice* is remembered, never the resulting data. Caching a library
 * here would duplicate the repository's, which is already keyed and is already
 * correct about when to refetch - two caches answering "what does this person
 * track" is how one of them starts lying.
 */
class LibraryPreferences(private val context: Context) {

    private object Keys {
        val TYPE = stringPreferencesKey("library_type")
        val FILTER = stringPreferencesKey("library_filter")
        val SORT = stringPreferencesKey("library_sort")
        val SORT_ASCENDING = booleanPreferencesKey("library_sort_ascending")
    }

    /** The remembered media type, as its enum name, or null when never chosen. */
    val lastType: Flow<String?> = context.libraryPrefsDataStore.data.map { it[Keys.TYPE] }

    /** The remembered status filter, as its enum name, or null when never chosen. */
    val lastFilter: Flow<String?> = context.libraryPrefsDataStore.data.map { it[Keys.FILTER] }

    /** The remembered sort, as its enum name, or null when never chosen. */
    val lastSort: Flow<String?> = context.libraryPrefsDataStore.data.map { it[Keys.SORT] }

    /**
     * The remembered sort direction, or null when never chosen.
     *
     * Null is a real state rather than a missing default: [lastSort] without a
     * direction cannot happen from this app (the pair is written in one edit),
     * and treating "never chosen" as ascending would silently re-answer the
     * direction the next time it is read alongside a sort.
     */
    val lastSortAscending: Flow<Boolean?> =
        context.libraryPrefsDataStore.data.map { it[Keys.SORT_ASCENDING] }

    suspend fun setType(value: String) {
        context.libraryPrefsDataStore.edit { it[Keys.TYPE] = value }
    }

    suspend fun setFilter(value: String) {
        context.libraryPrefsDataStore.edit { it[Keys.FILTER] = value }
    }

    /** Sort and direction in one edit: they are one choice, and a torn pair would
     *  restore a sort the reader never picked the direction for. */
    suspend fun setSort(name: String, ascending: Boolean) {
        context.libraryPrefsDataStore.edit {
            it[Keys.SORT] = name
            it[Keys.SORT_ASCENDING] = ascending
        }
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

    /**
     * Forgets everything, in one edit.
     *
     * The test seam - Robolectric hands every test the same application context
     * and this file lives on disk, so a stored choice would leak between them -
     * and the obvious body of a future "reset library view" action: all four
     * values are one view of the tab, and half a reset would restore a filter the
     * reader no longer wants against a type they do.
     */
    suspend fun reset() {
        context.libraryPrefsDataStore.edit {
            it.remove(Keys.TYPE)
            it.remove(Keys.FILTER)
            it.remove(Keys.SORT)
            it.remove(Keys.SORT_ASCENDING)
        }
    }
}