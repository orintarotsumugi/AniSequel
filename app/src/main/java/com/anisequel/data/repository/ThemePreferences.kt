package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Internal rather than private so a test can write the keys an older build wrote,
 * which is the only way to cover the upgrade path below. Nothing outside this file
 * should read it: every access goes through `ThemePreferences` and its
 * application-context field.
 */
internal val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
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
 * How much motion the app renders with.
 *
 * Maps to the two compiled Material MotionSchemes the app knows about - but
 * also records the user's preference for *this app's* hand-tuned tokens in
 * [com.anisequel.ui.components.expressive.ExpressiveMotion], so the fades and
 * springs written against those tokens can honour it too.
 *
 *  - **Instant** - no fade, no bounce, no morph. The user has their data; a
 *    transition there just states that.
 *  - **Smooth** - a standard fade, no overshoot. Quieter than the expressive
 *    scheme.
 *  - **Chill** - the full expressive springs. The default: it is what the rest
 *    of the app is designed around.
 */
enum class MotionStyle(val storageValue: String, val displayName: String) {
    INSTANT("instant", "Instant"),
    SMOOTH("smooth", "Smooth"),
    CHILL("chill", "Chill");

    companion object {
        val DEFAULT = CHILL

        fun fromStorage(value: String?): MotionStyle =
            entries.firstOrNull { it.storageValue == value }
                // Storage values written before the rename: "expressive" and
                // "standard" both existed in the old two-way toggle. Map them rather
                // than resetting silently - the old labels meant "a bit more bounce"
                // and "a bit more calm", which is Chill and Smooth respectively.
                ?: when (value) {
                    "expressive" -> CHILL
                    "standard" -> SMOOTH
                    else -> DEFAULT
                }
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
class ThemePreferences(context: Context) {

    /**
     * The application context, and every DataStore access through it.
     *
     * `preferencesDataStore` allows exactly one instance per file, and callers hand
     * this class whatever they happen to have: `MainActivity` passes the application
     * context, a detail sheet passes `LocalContext`, which is the Activity. Reading
     * and writing through this field is what keeps both paths on the same store
     * instead of the second one dying with "there is already one instance of
     * DataStore handles that file".
     */
    private val appContext: Context = context.applicationContext

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val MOTION_STYLE = stringPreferencesKey("motion_style")
        val TRUE_BLACK = booleanPreferencesKey("true_black")
        val CUSTOM_COLOR_HEX = stringPreferencesKey("custom_color_hex")
        val ANIME_THEME_ENABLED = booleanPreferencesKey("anime_theme_enabled")

        /**
         * Legacy. This build keeps the *applied* anime layer in memory and reads
         * the switch from [ANIME_THEME_ENABLED]; the key is only consulted when
         * the new one has never been written, and removed on the first write so
         * the value never depends on which key a later build reads.
         */
        val ANIME_THEME_ACTIVE = booleanPreferencesKey("anime_theme_active")

        /** Legacy, and dropped rather than adapted: see [ANIME_THEME_ACTIVE]. */
        val ANIME_THEME_COLOR_HEX = stringPreferencesKey("anime_theme_color_hex")

        /**
         * The cover colour this feature wrote into [CUSTOM_COLOR_HEX].
         *
         * Provenance, not a second copy of the accent: it is what lets turning
         * the feature off take back *only* the colour the anime chose, and lets
         * Settings say "keeping this show's colour" about a specific accent
         * instead of guessing from whatever hex happens to be stored.
         */
        val ANIME_THEME_RETAINED_COLOR_HEX = stringPreferencesKey("anime_theme_retained_color_hex")
        val ANIME_THEME_RETAIN_COLOR = booleanPreferencesKey("anime_theme_retain_color")
        val ANIME_THEME_REVERT_DURATION = androidx.datastore.preferences.core.intPreferencesKey("anime_theme_revert_duration")
        val APP_BACKGROUND_COLOR = stringPreferencesKey("app_background_color")
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
        val customColorHex: String? = null,
        /**
         * The switch: whether opening a title is allowed to recolour the app.
         *
         * This used to be the same flag as [animeThemeActive], and the two mean
         * different things now that the layer lives in memory. Keeping them apart
         * is what lets a reader say "yes, do this for every title" without that
         * also being a stored claim that a layer is on right now.
         */
        val animeThemeEnabled: Boolean = false,
        /**
         * True while an immersive layer is applied - a sheet is open, or a
         * temporary colour has not timed out yet. Never survives the process.
         */
        val animeThemeActive: Boolean = false,
        /** The applied layer's colour; null when [animeThemeActive] is false. */
        val animeThemeColorHex: String? = null,
        /**
         * The cover colour this feature persisted as [customColorHex], if any.
         * Null means the current accent is the reader's own pick.
         */
        val animeThemeRetainedColorHex: String? = null,
        /**
         * Whether the cover colour is *kept* as the app's accent once the sheet
         * closes. Read by the detail sheet when deciding whether to persist the
         * colour, and by [setAnimeThemeRetainColor] when the choice changes
         * mid-session. Off means the layer is on a timer instead.
         */
        val animeThemeRetainColor: Boolean = true,
        val animeThemeRevertDuration: Int = 300,
        val appBackgroundColor: String? = null,
        /** False only until the stored values have actually been read once. */
        val isLoaded: Boolean = false
    )

    /**
     * The stored preferences and the in-memory anime layer, as one stream.
     *
     * Combined rather than collected twice at the call sites: the theme wraps the
     * whole app and needs *one* value, and a screen that watched the layer
     * separately could draw a scheme from the stored half and a label from the
     * other.
     */
    val settings: Flow<ThemeSettings> = combine(
        appContext.themeDataStore.data,
        appliedColorLayer
    ) { preferences, layerHex ->
        ThemeSettings(
            themeMode = ThemeMode.fromStorage(preferences[Keys.THEME_MODE]),
            useDynamicColor = preferences[Keys.USE_DYNAMIC_COLOR] ?: false,
            paletteId = preferences[Keys.THEME_PALETTE] ?: "anisequel",
            motionStyle = MotionStyle.fromStorage(preferences[Keys.MOTION_STYLE]),
            trueBlack = preferences[Keys.TRUE_BLACK] ?: false,
            customColorHex = preferences[Keys.CUSTOM_COLOR_HEX],
            animeThemeEnabled = preferences[Keys.ANIME_THEME_ENABLED]
                // An upgrade that has never written the new key had the switch on
                // if the old flag was set, which is the honest reading: that flag
                // meant "this feature is what colours my app".
                ?: preferences[Keys.ANIME_THEME_ACTIVE]
                ?: false,
            animeThemeActive = layerHex != null,
            animeThemeColorHex = layerHex,
            animeThemeRetainedColorHex = preferences[Keys.ANIME_THEME_RETAINED_COLOR_HEX],
            animeThemeRetainColor = preferences[Keys.ANIME_THEME_RETAIN_COLOR] ?: true,
            animeThemeRevertDuration = preferences[Keys.ANIME_THEME_REVERT_DURATION] ?: 300,
            appBackgroundColor = preferences[Keys.APP_BACKGROUND_COLOR],
            isLoaded = true
        )
    }

    suspend fun setPalette(paletteId: String) {
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.THEME_PALETTE] = paletteId
        }
    }

    /**
     * Writes the app's own accent.
     *
     * Also ends the anime feature's claim on it: an explicit pick - including
     * clearing it - is the reader taking the accent back, so the retained marker
     * that lets Settings say "this colour came from that show" has to go with it,
     * or the card would keep describing an accent the reader just replaced.
     */
    suspend fun setCustomColorHex(hex: String?) {
        appContext.themeDataStore.edit { preferences ->
            val retained = preferences[Keys.ANIME_THEME_RETAINED_COLOR_HEX]
            if (retained != null && !retained.equals(hex, ignoreCase = true)) {
                preferences.remove(Keys.ANIME_THEME_RETAINED_COLOR_HEX)
            }
            if (hex != null) {
                preferences[Keys.CUSTOM_COLOR_HEX] = hex
            } else {
                preferences.remove(Keys.CUSTOM_COLOR_HEX)
            }
        }
    }

    suspend fun setMotionStyle(style: MotionStyle) {
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.MOTION_STYLE] = style.storageValue
        }
    }

    suspend fun setTrueBlack(enabled: Boolean) {
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.TRUE_BLACK] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = mode.storageValue
        }
    }

    /**
     * The switch that lets a detail sheet recolour the app.
     *
     * Turning it off takes the anime's colour away completely: the live layer
     * ends, its timer with it, and so does an accent this feature retained - and
     * only if this feature is the one that wrote it, never the reader's own hue.
     */
    suspend fun setAnimeThemeEnabled(enabled: Boolean) {
        // The layer is process state, so a reader who turns the feature off while
        // a title is on screen has to see it go immediately, whatever the stored
        // value says next.
        appliedColorLayer.value = null
        cancelPendingRevert()
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.ANIME_THEME_ENABLED] = enabled
            // One source of truth from the first write: the legacy key is only a
            // migration read, and leaving it set would make the effective value
            // depend on which key a later build happens to look at.
            preferences.remove(Keys.ANIME_THEME_ACTIVE)
            preferences.remove(Keys.ANIME_THEME_COLOR_HEX)
            if (!enabled) {
                val retained = preferences[Keys.ANIME_THEME_RETAINED_COLOR_HEX]
                preferences.remove(Keys.ANIME_THEME_RETAINED_COLOR_HEX)
                if (retained != null &&
                    retained.equals(preferences[Keys.CUSTOM_COLOR_HEX], ignoreCase = true)
                ) {
                    preferences.remove(Keys.CUSTOM_COLOR_HEX)
                }
            }
        }
    }

    /**
     * Applies the immersive layer for the title whose sheet is open.
     *
     * The colour lives in memory rather than in DataStore, and that is the fix for
     * two things the stored flag did:
     *
     *  - **A temporary colour that was not temporary.** The revert timer is a
     *    process-scoped coroutine; kill the app - or let the OS kill it - before
     *    the timer fires and the stored `active = true` outlived the job that was
     *    going to clear it. "5 minutes" became permanent until the next title
     *    overwrote it.
     *  - **A retained colour that tinted everything forever.** Retention used to
     *    leave the *immersive* layer switched on, so the show's hue stayed in the
     *    surfaces, containers and outlines on every screen and the next launch.
     *    That is the one thing [com.anisequel.ui.theme.DynamicThemeBuilder] says a
     *    standing preference must not do: surfaces the reader did not ask about
     *    move because a title was opened.
     *
     * [retain] is the difference between the two paths: the accent is persisted
     * *as well*, so what outlives the sheet is the ordinary custom-hue theme -
     * same colour, untinted surfaces - rather than the layer.
     *
     * Order matters. The layer is emitted first, so everything the reader sees
     * while the accent write lands is the immersive scheme; writing the accent
     * first would flash the untinted theme in the middle of the sheet's entrance,
     * which is the two-wave arrival the old single-transaction comment is about.
     */
    suspend fun applyAnimeColorLayer(colorHex: String, retain: Boolean) {
        cancelPendingRevert()
        appliedColorLayer.value = colorHex
        if (retain) {
            appContext.themeDataStore.edit { preferences ->
                // The same title reopened, or a recomposition that reaches this
                // effect again, must not rewrite two identical values: a write
                // emits a new `ThemeSettings` and a new `ThemeSettings` rebuilds
                // the scheme behind the whole app.
                if (preferences[Keys.CUSTOM_COLOR_HEX] != colorHex ||
                    preferences[Keys.ANIME_THEME_RETAINED_COLOR_HEX] != colorHex
                ) {
                    preferences[Keys.CUSTOM_COLOR_HEX] = colorHex
                    preferences[Keys.ANIME_THEME_RETAINED_COLOR_HEX] = colorHex
                }
            }
        }
    }

    /**
     * The sheet closed. Keep the colour for [revertAfterSeconds] or drop it now.
     *
     * A retained colour never gets here with a timer: the caller ends the layer on
     * the spot, and the accent the anime wrote is what carries the colour onward.
     */
    fun releaseAnimeColorLayer(revertAfterSeconds: Int) {
        if (revertAfterSeconds <= 0) {
            clearAnimeColorLayer()
        } else {
            revertJob?.cancel()
            revertJob = revertScope.launch {
                delay(revertAfterSeconds * 1000L)
                appliedColorLayer.value = null
            }
        }
    }

    /** Ends the immersive layer now, keeping whatever accent is stored. */
    fun clearAnimeColorLayer() {
        cancelPendingRevert()
        appliedColorLayer.value = null
    }

    /**
     * The retain switch, applied to the colour that is live right now.
     *
     * On persists it as the app's accent; off ends the retained state properly -
     * the layer, the marker, and the accent *only if the marker says the anime
     * wrote it*. Checking the marker instead of comparing hexes is what keeps a
     * reader's own `#00BFA5` from being cleared because a cover happened to be
     * that colour.
     */
    suspend fun setAnimeThemeRetainColor(retain: Boolean) {
        val liveHex = appliedColorLayer.value
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.ANIME_THEME_RETAIN_COLOR] = retain
            if (retain) {
                val color = liveHex ?: preferences[Keys.ANIME_THEME_RETAINED_COLOR_HEX]
                if (color != null) {
                    preferences[Keys.CUSTOM_COLOR_HEX] = color
                    preferences[Keys.ANIME_THEME_RETAINED_COLOR_HEX] = color
                }
            } else {
                val retained = preferences[Keys.ANIME_THEME_RETAINED_COLOR_HEX]
                preferences.remove(Keys.ANIME_THEME_RETAINED_COLOR_HEX)
                if (retained != null &&
                    retained.equals(preferences[Keys.CUSTOM_COLOR_HEX], ignoreCase = true)
                ) {
                    preferences.remove(Keys.CUSTOM_COLOR_HEX)
                }
            }
        }
        // With retention off there is nothing left to keep the colour alive, and
        // the layer outranks every other source in `Theme.kt`. Leaving it applied
        // is what made this switch look dead: the reader turned it off and the
        // app stayed the show's colour.
        if (!retain) clearAnimeColorLayer()
    }

    suspend fun setAnimeThemeRevertDuration(durationSeconds: Int) {
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.ANIME_THEME_REVERT_DURATION] = durationSeconds
        }
    }

    private fun cancelPendingRevert() {
        revertJob?.cancel()
        revertJob = null
    }

    suspend fun setAppBackgroundColor(hex: String?) {
        appContext.themeDataStore.edit { preferences ->
            if (hex != null) {
                preferences[Keys.APP_BACKGROUND_COLOR] = hex
            } else {
                preferences.remove(Keys.APP_BACKGROUND_COLOR)
            }
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
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.USE_DYNAMIC_COLOR] = enabled
        }
    }

    /** Back to shipping defaults. Used by the "Reset appearance" action. */
    suspend fun reset() {
        clearAnimeColorLayer()
        appContext.themeDataStore.edit { preferences ->
            preferences.remove(Keys.THEME_MODE)
            preferences.remove(Keys.USE_DYNAMIC_COLOR)
            preferences.remove(Keys.THEME_PALETTE)
            preferences.remove(Keys.MOTION_STYLE)
            preferences.remove(Keys.TRUE_BLACK)
            preferences.remove(Keys.CUSTOM_COLOR_HEX)
            preferences.remove(Keys.ANIME_THEME_ENABLED)
            preferences.remove(Keys.ANIME_THEME_ACTIVE)
            preferences.remove(Keys.ANIME_THEME_COLOR_HEX)
            preferences.remove(Keys.ANIME_THEME_RETAINED_COLOR_HEX)
            preferences.remove(Keys.ANIME_THEME_RETAIN_COLOR)
            preferences.remove(Keys.ANIME_THEME_REVERT_DURATION)
            preferences.remove(Keys.APP_BACKGROUND_COLOR)
        }
    }

    private companion object {
        /**
         * The immersive layer a detail sheet applied, as a hex colour, or null.
         *
         * Process state rather than a field on the instance because the sheet
         * builds its own `ThemePreferences` and the theme reads `MainActivity`'s:
         * two instances, one colour. And deliberately not DataStore - see
         * [applyAnimeColorLayer] for what storing it cost.
         */
        val appliedColorLayer = MutableStateFlow<String?>(null)

        /**
         * The temporary anime layer's revert timer. Process-scoped on purpose:
         * the sheet that started it is gone by the time it fires. There is only
         * one temporary layer at a time, so one job is the whole state, and a
         * new title (or Retain) cancels the previous one through
         * [cancelPendingRevert].
         */
        val revertScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var revertJob: Job? = null
    }
}