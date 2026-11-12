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
        val SEARCH_TAB = booleanPreferencesKey("search_tab")
        val SEARCH_ANIME = booleanPreferencesKey("search_anime")
        val SEARCH_MANGA = booleanPreferencesKey("search_manga")
        val SEARCH_TOP_BAR = booleanPreferencesKey("search_top_bar")
        val TIER_TOP_BAR = booleanPreferencesKey("tier_top_bar")
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
            if (!enabled) preferences[Keys.TIER_TOP_BAR] = false
        }
    }

    /**
     * Whether the catalogue search is one of the bar's pages.
     *
     * Off by default for the same reason the tier tab is: the bar is the app's
     * most contended strip of screen, and a reader who never searches the
     * catalogue should not pay a tab for it. The difference is where the feature
     * lives without the tab - there is no alternative door into Search, on
     * purpose, because a search field on the dashboard is the thing this page
     * exists to replace. So the switch is how the page exists for a reader at
     * all, and its subtitle says so.
     */
    val searchTab: Flow<Boolean> = context.mainTabsDataStore.data.map { preferences ->
        preferences[Keys.SEARCH_TAB] ?: DEFAULT_SEARCH_TAB
    }

    suspend fun setSearchTab(enabled: Boolean) {
        context.mainTabsDataStore.edit { preferences ->
            preferences[Keys.SEARCH_TAB] = enabled
            if (!enabled) preferences[Keys.SEARCH_TOP_BAR] = false
        }
    }

    /**
     * Whether the catalogue search may answer with anime.
     *
     * Two switches rather than one because the namespaces are independent: a
     * reader who tracks manga but no anime should not have to see an anime half
     * of every search, and a reader who wants both should not lose the anime
     * results because they hid the manga page. Both default on, so a fresh
     * install keeps the combined search it had; turning both off leaves no
     * catalogue half to ask, and [com.anisequel.ui.navigation.enabledMainTabs]
     * then hides the Search tab rather than opening a page with nothing in it.
     */
    val searchAnimeEnabled: Flow<Boolean> = context.mainTabsDataStore.data.map { preferences ->
        preferences[Keys.SEARCH_ANIME] ?: DEFAULT_SEARCH_ANIME
    }

    suspend fun setSearchAnimeEnabled(enabled: Boolean) {
        context.mainTabsDataStore.edit { preferences ->
            preferences[Keys.SEARCH_ANIME] = enabled
        }
    }

    /** The manga half of [searchAnimeEnabled]; same rules and default. */
    val searchMangaEnabled: Flow<Boolean> = context.mainTabsDataStore.data.map { preferences ->
        preferences[Keys.SEARCH_MANGA] ?: DEFAULT_SEARCH_MANGA
    }

    suspend fun setSearchMangaEnabled(enabled: Boolean) {
        context.mainTabsDataStore.edit { preferences ->
            preferences[Keys.SEARCH_MANGA] = enabled
        }
    }

    /** Whether the catalogue has a direct shortcut beside the top-bar profile/settings. */
    val searchTopBar: Flow<Boolean> = context.mainTabsDataStore.data.map { preferences ->
        preferences[Keys.SEARCH_TOP_BAR] ?: DEFAULT_SEARCH_TOP_BAR
    }

    suspend fun setSearchTopBar(enabled: Boolean) {
        context.mainTabsDataStore.edit { preferences ->
            preferences[Keys.SEARCH_TOP_BAR] = enabled
            if (enabled) preferences[Keys.SEARCH_TAB] = true
        }
    }

    /** Whether tier lists have a direct top-bar shortcut. */
    val tierTopBar: Flow<Boolean> = context.mainTabsDataStore.data.map { preferences ->
        preferences[Keys.TIER_TOP_BAR] ?: DEFAULT_TIER_TOP_BAR
    }

    suspend fun setTierTopBar(enabled: Boolean) {
        context.mainTabsDataStore.edit { preferences ->
            preferences[Keys.TIER_TOP_BAR] = enabled
            if (enabled) preferences[Keys.TIER_LIST_TAB] = true
        }
    }

    /** Back to the shipped default, which is "hidden". */
    suspend fun reset() {
        context.mainTabsDataStore.edit { preferences ->
            preferences.remove(Keys.TIER_LIST_TAB)
            preferences.remove(Keys.SEARCH_TAB)
            preferences.remove(Keys.SEARCH_ANIME)
            preferences.remove(Keys.SEARCH_MANGA)
            preferences.remove(Keys.SEARCH_TOP_BAR)
            preferences.remove(Keys.TIER_TOP_BAR)
        }
    }

    companion object {
        const val DEFAULT_TIER_LIST_TAB = false
        const val DEFAULT_SEARCH_TAB = false

        /** Both catalogue halves are on; the *tab* is still off by default. */
        const val DEFAULT_SEARCH_ANIME = true
        const val DEFAULT_SEARCH_MANGA = true
        const val DEFAULT_SEARCH_TOP_BAR = false
        const val DEFAULT_TIER_TOP_BAR = false
    }
}
