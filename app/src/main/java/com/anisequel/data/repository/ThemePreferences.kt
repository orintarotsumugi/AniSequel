package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
 * How far a retained cover colour reaches into the background.
 *
 * Retention used to be all-or-nothing: the show's hue either repainted every
 * surface or stopped at the accent. Both are honest answers to different readers -
 * a night-sky show at full tint is a very dark app, while its accent alone is a
 * navy app with one blue button - so the choice is a setting rather than a guess.
 *
 * The strength only applies after the sheet closes. While a title is open the
 * immersive layer is the point, and dialling it down would make the preview
 * disagree with the page the reader is looking at.
 */
enum class RetainedTint(
    val storageValue: String,
    val displayName: String,
    /** 0 = the palette's own surfaces, 1 = the cover's tinted scheme. */
    val strength: Float
) {
    SUBTLE("subtle", "Subtle", 0.35f),
    BALANCED("balanced", "Balanced", 0.7f),
    FULL("full", "Full", 1f);

    companion object {
        val DEFAULT = FULL

        fun fromStorage(value: String?): RetainedTint =
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
/**
 * How the app animates, as one of five characters.
 *
 * [SNAPPY] and [FLOATY] were added beside the original three so the choice spans a
 * real range rather than "off / plain / expressive": Snappy is for readers who want
 * the app to feel instant without losing its transitions, Floaty for the opposite -
 * slow, soft movement on everything. The token mappings in `ExpressiveMotion` give
 * each one its own physics; the Appearance tab previews them on the spot, because a
 * name is not a feel.
 *
 * Order is the order the picker draws, so a style added at the end stays where the
 * reader expects it rather than reshuffling the row.
 */
enum class MotionStyle(val storageValue: String, val displayName: String) {
    INSTANT("instant", "Instant"),
    SMOOTH("smooth", "Smooth"),
    SNAPPY("snappy", "Snappy"),
    CHILL("chill", "Chill"),
    FLOATY("floaty", "Floaty");

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
        val ANIME_THEME_RETAINED_TINT = stringPreferencesKey("anime_theme_retained_tint")
        val ANIME_THEME_REVERT_DURATION = androidx.datastore.preferences.core.intPreferencesKey("anime_theme_revert_duration")
        val APP_BACKGROUND_COLOR = stringPreferencesKey("app_background_color")

        /**
         * Full screen: the status and navigation bars hidden, revealed by an edge
         * swipe.
         *
         * **Off by default.** Hiding the bars also zeroes every screen's
         * `statusBarsPadding()`, so a top app bar sits flush against the screen
         * edge - which reads as cramped on a device with no cutout, and as a bug on
         * one that has. It is a choice the reader makes in Appearance, not the
         * shape a fresh install lands in.
         */
        val IMMERSIVE_MODE = booleanPreferencesKey("immersive_mode")

        /** The reading scale applied to every text style, 1.0 = as designed. */
        val FONT_SCALE = floatPreferencesKey("font_scale")

        /**
         * A font the reader imported, as an absolute path inside the app's files
         * directory - copied there rather than referenced by URI so the choice
         * survives the granting app being uninstalled.
         */
        val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
        val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")

        /** How fast a title too long for its row scrolls, in dp per second. */
        val TITLE_SCROLL_VELOCITY = floatPreferencesKey("title_scroll_velocity_dp")
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
        /**
         * How much of the retained colour's tinted scheme the background keeps.
         * Full by default, which is the colour the reader was looking at in the
         * sheet; Subtle keeps the accent and leaves the surfaces to the palette.
         */
        val retainedTint: RetainedTint = RetainedTint.DEFAULT,
        val animeThemeRevertDuration: Int = 300,
        val appBackgroundColor: String? = null,
        /**
         * Hide the system bars, off by default.
         *
         * See [Keys.IMMERSIVE_MODE]: an absent key means the bars stay, and only an
         * explicit `true` - written by the switch - hides them.
         */
        val immersive: Boolean = false,
        /** Reading scale for every text style; see [setFontScale]. */
        val fontScale: Float = 1f,
        /** The imported font's file path, or null for the system face. */
        val customFontPath: String? = null,
        /** The imported font's original file name, for Settings to name it by. */
        val customFontName: String? = null,
        /** Scroll speed for titles that outgrow their row, in dp per second. */
        val titleScrollDpPerSecond: Float = DEFAULT_TITLE_SCROLL_DP_PER_SECOND,
        /** False only until the stored values have actually been read once. */
        val isLoaded: Boolean = false
    ) {
        /**
         * The retained cover colour, if it is allowed to drive the scheme now.
         *
         * The marker on its own is provenance: it records who wrote the accent,
         * and it outlives the switch being flipped so that turning the feature back
         * on does not need a title opened to find the colour again. Only these two
         * switches decide whether it may tint, which is why the resolution lives
         * here rather than at each of the four call sites (the theme, the preview
         * card, the palette/hue selection state and the reset button) - four copies
         * is how one of them ends up naming a colour the app is not drawing.
         */
        val retainedAnimeColorHex: String?
            get() = animeThemeRetainedColorHex?.takeIf {
                animeThemeEnabled && animeThemeRetainColor
            }
    }

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
            retainedTint = RetainedTint.fromStorage(preferences[Keys.ANIME_THEME_RETAINED_TINT]),
            animeThemeRevertDuration = preferences[Keys.ANIME_THEME_REVERT_DURATION] ?: 300,
            appBackgroundColor = preferences[Keys.APP_BACKGROUND_COLOR],
            // `?: false`: the bars are the default, and only an explicit true -
            // written by the switch - hides them.
            immersive = preferences[Keys.IMMERSIVE_MODE] ?: false,
            fontScale = preferences[Keys.FONT_SCALE] ?: 1f,
            customFontPath = preferences[Keys.CUSTOM_FONT_PATH],
            customFontName = preferences[Keys.CUSTOM_FONT_NAME],
            titleScrollDpPerSecond = preferences[Keys.TITLE_SCROLL_VELOCITY]
                ?: DEFAULT_TITLE_SCROLL_DP_PER_SECOND,
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

    /**
     * Full-screen mode.
     *
     * Only records the choice: the window applies it, and `MainActivity` watches
     * the same flow - a DataStore write from this screen and a window flag set
     * somewhere else would be two answers to one question.
     */
    suspend fun setImmersive(enabled: Boolean) {
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.IMMERSIVE_MODE] = enabled
        }
    }

    /** The reading scale, clamped to the range Settings offers. */
    suspend fun setFontScale(scale: Float) {
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.FONT_SCALE] = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        }
    }

    /**
     * Records the imported font.
     *
     * The caller copies the file first and passes the copy's path: a picked
     * document URI stops working when the app that granted it is uninstalled, and
     * a font preference that silently reverts to the default months later is
     * indistinguishable from a bug.
     */
    suspend fun setCustomFont(path: String?, name: String?) {
        appContext.themeDataStore.edit { preferences ->
            if (path != null) {
                preferences[Keys.CUSTOM_FONT_PATH] = path
                preferences[Keys.CUSTOM_FONT_NAME] = name.orEmpty()
            } else {
                preferences.remove(Keys.CUSTOM_FONT_PATH)
                preferences.remove(Keys.CUSTOM_FONT_NAME)
            }
        }
    }

    /** How fast overflowing titles scroll. */
    suspend fun setTitleScrollSpeed(dpPerSecond: Float) {
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.TITLE_SCROLL_VELOCITY] =
                dpPerSecond.coerceIn(MIN_TITLE_SCROLL_DP_PER_SECOND, MAX_TITLE_SCROLL_DP_PER_SECOND)
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
                preferences.removeAnimeClaim()
            }
        }
    }

    /**
     * Hands the accent back to the reader, ending the anime's claim on it.
     *
     * Called when a control that *should* own the scheme would otherwise be
     * silently overruled by a retained cover colour. The wallpaper switch is the
     * one that needs it: a palette or a custom hue already clears the claim through
     * [setCustomColorHex], and the anime section's own switches have their paths.
     * Only the accent this feature wrote is removed - the marker is the whole
     * reason a reader's identical-looking hex is safe.
     *
     * The live layer is not touched: that is the sheet's, and this is about what the
     * app is drawn with once no sheet is open.
     */
    suspend fun releaseAnimeRetainedColor() {
        appContext.themeDataStore.edit { preferences ->
            preferences.removeAnimeClaim()
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
     *  - **A retained colour and the layer getting confused.** Retention and the
     *    live layer are two different lifetimes, and the bug was storing the
     *    layer's flag on disk. The retained *marker* is stored; the layer is not.
     *    The theme now builds the tinted scheme from the marker while no sheet is
     *    open (see `MainActivity` and `AniSequelTheme`), so a kept colour keeps the
     *    background it showed in the sheet - which is what the reader asked for -
     *    without a process-scoped flag pretending a sheet is still open.
     *
     * [retain] is the difference between the two paths: the accent *and* the
     * provenance marker are persisted, so the retained scheme has a colour to be
     * built from after the layer ends. With retention off nothing is stored and
     * the sheet is a preview on its own timer.
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
                preferences.removeAnimeClaim()
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

    suspend fun setRetainedTint(tint: RetainedTint) {
        appContext.themeDataStore.edit { preferences ->
            preferences[Keys.ANIME_THEME_RETAINED_TINT] = tint.storageValue
        }
    }

    private fun cancelPendingRevert() {
        revertJob?.cancel()
        revertJob = null
    }

    /**
     * Removes the retained marker, and the accent too when the marker says this
     * feature wrote it.
     *
     * One function rather than three copies of the check: the three exits (feature
     * off, Retain off, another control taking over) have to make the same decision,
     * and a fourth caller is how one of them would drift.
     */
    private fun MutablePreferences.removeAnimeClaim() {
        val retained = this[Keys.ANIME_THEME_RETAINED_COLOR_HEX] ?: return
        remove(Keys.ANIME_THEME_RETAINED_COLOR_HEX)
        if (retained.equals(this[Keys.CUSTOM_COLOR_HEX], ignoreCase = true)) {
            remove(Keys.CUSTOM_COLOR_HEX)
        }
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
            preferences.remove(Keys.ANIME_THEME_RETAINED_TINT)
            preferences.remove(Keys.ANIME_THEME_REVERT_DURATION)
            preferences.remove(Keys.APP_BACKGROUND_COLOR)
            // Back to the bars, which is the default this app ships with.
            // The reading font, size and title scroll are their own page's choices
            // and are reset there, not by "Reset appearance".
            preferences.remove(Keys.IMMERSIVE_MODE)
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
/**
 * The reading-scale range Settings > Type & reading offers.
 *
 * Bounds live here rather than in the slider so the preference and the control
 * cannot disagree about what "as large as it goes" is - the value that is written
 * and the value the screen claims are one.
 */
const val MIN_FONT_SCALE = 0.80f
const val MAX_FONT_SCALE = 1.40f

/**
 * The title-scroll range, in dp per second.
 *
 * A title scrolls only when it does not fit its row, and this is the one number
 * that makes the effect readable: too slow reads as stuck, too fast is unreadable.
 * The span covers "very leisurely" to "brisk" without either end being useless.
 */
const val MIN_TITLE_SCROLL_DP_PER_SECOND = 24f
const val MAX_TITLE_SCROLL_DP_PER_SECOND = 140f
const val DEFAULT_TITLE_SCROLL_DP_PER_SECOND = 60f
