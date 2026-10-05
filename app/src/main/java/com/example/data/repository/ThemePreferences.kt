package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_theme_prefs"
)

/**
 * How the app picks between light and dark.
 *
 * [SYSTEM] follows the device's own setting, which is also what happens when
 * nothing has been chosen. [LIGHT] and [DARK] pin it, for the people who want
 * one regardless of what their phone is set to.
 */
enum class ThemeMode(val storageValue: String, val displayName: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        val DEFAULT = SYSTEM

        /**
         * Reads a stored value, falling back to [DEFAULT].
         *
         * Falls back rather than throwing: a preference file written by a newer
         * build (or hand-edited) can hold a value this build has never heard of,
         * and a theme preference is not worth refusing to start over.
         */
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * Which of the two compiled Material motion schemes the app renders with.
 * The expressive scheme is the default; standard boys prefer less bounce.
 */
enum class MotionStyle(val storageValue: String, val displayName: String) {
    EXPRESSIVE("expressive", "Expressive"),
    STANDARD("standard", "Standard");

    companion object {
        val DEFAULT = EXPRESSIVE

        fun fromStorage(value: String?): MotionStyle =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * The user's appearance choices, persisted.
 *
 * Read once at startup as a plain value rather than a Flow at every call site:
 * the theme wraps the entire app, so it needs a value up front, and a Flow here
 * would only mean every screen collecting the same preference to draw one
 * switch.
 *
 * Material You (dynamic colour) defaults to **off**. That is a deliberate
 * default rather than an oversight - see [setUseDynamicColor]. It was also
 * previously unreachable entirely: the flag existed on `AniSequelTheme` but
 * nothing ever set it, so the "themes from my wallpaper" behaviour could not be
 * turned on even by a developer.
 */
class ThemePreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val MOTION_STYLE = stringPreferencesKey("motion_style")
        val TRUE_BLACK = booleanPreferencesKey("true_black")
    }

    /**
     * Both preferences at once, so the theme can apply them in a single read.
     *
     * [isLoaded] is what makes the difference between "the user chose the light
     * theme" and "we have not read their choice yet" - and DataStore reports
     * both as the same value. Without it, every cold start for someone who
     * picked a non-default theme painted the whole app in the default one and
     * then repainted it, which is the flash that reads as a bug.
     */
    data class ThemeSettings(
        val themeMode: ThemeMode,
        val useDynamicColor: Boolean,
        val paletteId: String = "anisequel",
        val motionStyle: MotionStyle = MotionStyle.DEFAULT,
        val trueBlack: Boolean = false,
        /** False only until the stored values have actually been read once. */
        val isLoaded: Boolean = false
    )

    val settings: Flow<ThemeSettings> = context.themeDataStore.data.map { preferences ->
        ThemeSettings(
            themeMode = ThemeMode.fromStorage(preferences[Keys.THEME_MODE]),
            useDynamicColor = preferences[Keys.USE_DYNAMIC_COLOR] ?: false,
            paletteId = preferences[Keys.THEME_PALETTE] ?: "anisequel",
            motionStyle = MotionStyle.fromStorage(preferences[Keys.MOTION_STYLE]),
            trueBlack = preferences[Keys.TRUE_BLACK] ?: false,
            isLoaded = true
        )
    }

    suspend fun setPalette(paletteId: String) {
        context.themeDataStore.edit { preferences ->
            preferences[Keys.THEME_PALETTE] = paletteId
        }
    }

    suspend fun setMotionStyle(style: MotionStyle) {
        context.themeDataStore.edit { preferences ->
            preferences[Keys.MOTION_STYLE] = style.storageValue
        }
    }

    suspend fun setTrueBlack(enabled: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[Keys.TRUE_BLACK] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = mode.storageValue
        }
    }

    /**
     * Turns Material You on or off.
     *
     * Unconditional. This used to be documented as ignored below Android 12,
     * where the UI disabled the switch rather than storing a value that could
     * do nothing - a guard against exactly the "written but never observed"
     * failure the refresh interval had. With `minSdk = 31` there is no device
     * it applies to, so the switch is always live and the caveat is gone.
     */
    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[Keys.USE_DYNAMIC_COLOR] = enabled
        }
    }

    /** Back to shipping defaults. Used by the "Reset appearance" action. */
    suspend fun reset() {
        context.themeDataStore.edit { preferences ->
            preferences.remove(Keys.THEME_MODE)
            preferences.remove(Keys.USE_DYNAMIC_COLOR)
            preferences.remove(Keys.THEME_PALETTE)
            preferences.remove(Keys.MOTION_STYLE)
            preferences.remove(Keys.TRUE_BLACK)
        }
    }
}