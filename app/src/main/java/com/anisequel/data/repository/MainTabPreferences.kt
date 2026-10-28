package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mainTabsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_main_tabs"
)

/**
 * Which pages the bottom bar offers.
 *
 * The tier list was a page pushed from the Library and the Profile, which meant
 * two doors into it and no door back that did not depend on which one the reader
 * used. It is one of the bar's pages now, so all three ways in agree about where
 * Back goes - but it is also the one page in the app that owns the whole screen:
 * a board is dragged, and the tray pinned over its bottom edge is the page's own
 * control. Hence a switch rather than a decision. A reader who ranks nothing
 * never meets a tab for it, and a reader who ranks everything gets one without a
 * trip through the Library.
 *
 * Off by default. The tab was on by default for one release, on the argument that
 * a feature nobody can find is a feature that does not exist. That argument holds
 * for the feature and not for the tab: the trophy in the Library and the row in the
 * profile are two visible doors into the same page, and the bar is the app's most
 * contended strip of screen. A reader who ranks nothing gets four tabs instead of
 * five and can turn the fifth on in Settings > Library > Bottom bar.
 *
 * Read through a [Flow] rather than a stored value so a flip in Settings reaches
 * the bar in the same session, without the app's root being rebuilt - the bar is
 * drawn by the host, and the host is what collects this.
 */
class MainTabPreferences(private val context: Context) {

    private object Keys {
        val TIER_LIST_TAB = booleanPreferencesKey("tier_list_tab")
    }

    /**
     * Whether the tier list is one of the bar's pages.
     *
     * The name says *tab* rather than "tier list" because the tier list itself is
     * never hidden: the trophy in the Library and the row in the profile still
     * open it, and turning this off only takes it off the bar. A switch that
     * looked like it disabled the feature would be lying about what it does.
     */
    val tierListTab: Flow<Boolean> = context.mainTabsDataStore.data.map { preferences ->
        preferences[Keys.TIER_LIST_TAB] ?: DEFAULT_TIER_LIST_TAB
    }

    suspend fun setTierListTab(enabled: Boolean) {
        context.mainTabsDataStore.edit { preferences ->
            preferences[Keys.TIER_LIST_TAB] = enabled
        }
    }

    /** Back to the shipped default, which is "hidden". */
    suspend fun reset() {
        context.mainTabsDataStore.edit { preferences ->
            preferences.remove(Keys.TIER_LIST_TAB)
        }
    }

    companion object {
        const val DEFAULT_TIER_LIST_TAB = false
    }
}
