package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.domain.card.MediaCardStyle
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
 * The value is the enum's `storageValue`, never its ordinal: a reordering of the
 * entries would otherwise silently repaint every card in the app, and a style a
 * newer build removed reads back as the plain one rather than as a crash.
 */
class InterfacePreferences(private val context: Context) {

    private object Keys {
        val CARD_STYLE = stringPreferencesKey("media_card_style")
    }

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

    suspend fun setCardStyle(style: MediaCardStyle) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.CARD_STYLE] = style.storageValue
        }
    }

    /** Back to the shipped layout. */
    suspend fun reset() {
        context.interfaceDataStore.edit { preferences ->
            preferences.remove(Keys.CARD_STYLE)
        }
    }
}
