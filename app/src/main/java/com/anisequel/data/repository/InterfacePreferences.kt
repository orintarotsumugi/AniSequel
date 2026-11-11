package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.ui.AppLayout
import com.anisequel.domain.ui.HomeSequelStyle
import com.anisequel.domain.ui.LibraryViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.interfaceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_interface_prefs"
)

/**
 * How the app draws, as distinct from what colour it is drawn in.
 *
 * Its own store rather than a key on [LibraryPreferences] or [ThemePreferences],
 * because the card style is read by two surfaces that share neither of those
 * owners: the Library grid belongs to the reader's own list, and the dashboard's
 * sequel rows belong to the recommendations. A style stored under the Library would
 * mean the dashboard has to reach into somebody else's preferences to find out how
 * to draw itself - and the Library store is deliberately about *which* entries are
 * shown, not about the shape they are shown in.
 *
 * ## The layout choice lives here too
 *
 * [settings] is the one read every screen depends on: which presentation the app
 * draws, how dense the new Library is, and whether Home opens on the Continue
 * Watching rail. They are one `Flow` because they are one answer to "how should
 * this app look" - and because the app root collecting three separate flows would
 * rebuild the navigation graph three times for one tap.
 *
 * Every value is the enum's `storageValue`, never its ordinal: a reordering of the
 * entries would otherwise silently repaint every card in the app, and a style a
 * newer build removed reads back as the plain one rather than as a crash.
 */
class InterfacePreferences(private val context: Context) {

    private object Keys {
        val CARD_STYLE = stringPreferencesKey("media_card_style")
        val APP_LAYOUT = stringPreferencesKey("app_layout")
        val LIBRARY_VIEW = stringPreferencesKey("library_view_mode")
        val SHOW_CONTINUE_RAIL = booleanPreferencesKey("show_continue_rail")
        val HOME_SEQUEL_STYLE = stringPreferencesKey("home_sequel_style")
    }

    /** Everything about the interface that is not the colour palette. */
    data class InterfaceSettings(
        val layout: AppLayout = AppLayout.DEFAULT,
        val libraryView: LibraryViewMode = LibraryViewMode.DEFAULT,
        val showContinueRail: Boolean = true,
        val homeSequelStyle: HomeSequelStyle = HomeSequelStyle.DEFAULT
    )

    /**
     * The chosen card style, defaulting to the plain layout.
     *
     * A [Flow] like every other preference here, so a tap on a style chip repaints
     * the grid behind the Settings page the moment the page is left - the reader sees
     * the choice land, which is the only proof a preview was worth drawing.
     */
    val cardStyle: Flow<MediaCardStyle> =
        context.interfaceDataStore.data.map { preferences ->
            MediaCardStyle.fromStorage(preferences[Keys.CARD_STYLE])
        }

    /**
     * The layout, density and Home-rail choice, in one emission.
     *
     * Read once at the app root and provided as [com.anisequel.ui.theme.LocalAppLayout],
     * [com.anisequel.ui.theme.LocalLibraryViewMode] and
     * [com.anisequel.ui.theme.LocalShowContinueRail]. The defaults are the shipped
     * ones, so an install that has never touched Settings looks exactly as it did.
     */
    val settings: Flow<InterfaceSettings> =
        context.interfaceDataStore.data.map { preferences ->
            InterfaceSettings(
                layout = AppLayout.fromStorage(preferences[Keys.APP_LAYOUT]),
                libraryView = LibraryViewMode.fromStorage(preferences[Keys.LIBRARY_VIEW]),
                showContinueRail = preferences[Keys.SHOW_CONTINUE_RAIL] ?: true,
                homeSequelStyle = HomeSequelStyle.fromStorage(
                    preferences[Keys.HOME_SEQUEL_STYLE]
                )
            )
        }

    suspend fun setCardStyle(style: MediaCardStyle) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.CARD_STYLE] = style.storageValue
        }
    }

    suspend fun setLayout(layout: AppLayout) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.APP_LAYOUT] = layout.storageValue
        }
    }

    suspend fun setLibraryView(view: LibraryViewMode) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.LIBRARY_VIEW] = view.storageValue
        }
    }

    suspend fun setShowContinueRail(show: Boolean) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.SHOW_CONTINUE_RAIL] = show
        }
    }

    /**
     * How the new Home draws its recommendation list.
     *
     * A setting rather than a second screen: both layouts read the same state and
     * call the same callbacks, so the reader switches the shape and keeps their
     * place - see [HomeSequelStyle].
     */
    suspend fun setHomeSequelStyle(style: HomeSequelStyle) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.HOME_SEQUEL_STYLE] = style.storageValue
        }
    }

    /** Back to the shipped layout, density, rail and card style. */
    suspend fun reset() {
        context.interfaceDataStore.edit { preferences ->
            preferences.remove(Keys.CARD_STYLE)
            preferences.remove(Keys.APP_LAYOUT)
            preferences.remove(Keys.LIBRARY_VIEW)
            preferences.remove(Keys.SHOW_CONTINUE_RAIL)
            preferences.remove(Keys.HOME_SEQUEL_STYLE)
        }
    }
}
