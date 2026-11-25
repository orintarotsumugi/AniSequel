package com.anisequel.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.BuildConfig
import com.anisequel.data.repository.AppLockPreferences
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.JokePreferences
import com.anisequel.data.repository.NotificationPreferencesStore
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.data.repository.SeasonalPreferences
import com.anisequel.data.repository.ThemePreferences
import kotlin.math.roundToInt

/**
 * What every row on the shelf is set to right now.
 *
 * ## Why this exists as a value
 *
 * The shelf described each row but never answered it: "Appearance / Colours,
 * cover-based themes and screen backgrounds" told a reader what was behind the
 * row and not whether OLED was on. Answering that at rest is the cheapest
 * navigation improvement on the screen - it saves a page visit and a Back for the
 * four or five things a reader checks most often (theme, card style, text size,
 * refresh interval, whether Ani-chan is on).
 *
 * ## Why the shelf does not collect the preferences itself
 *
 * One collector per row would be nine subscriptions inside a composable that
 * already redraws when the search field's focus changes, and it would hand the
 * hub the ability to disagree with a page about what is stored - the failure the
 * Settings graph already avoids for the card style by passing one instance.
 * These are assembled once, next to the other values the graph passes down
 * (`viewer`, the two list counts, the preview entries), and the shelf stays a
 * pure drawing function of what it is given.
 *
 * ## Why a field can be null
 *
 * Every value here is read from a DataStore flow, and every one starts null until
 * the first emission. A row whose value is null draws *no* value rather than a
 * placeholder like "…": a guess in the value slot is indistinguishable from an
 * answer, and on a screen whose whole point is to be glanceable, a wrong glance
 * is worse than a missing one. Updates is null for the same reason and never
 * resolves - this app has no cached "last checked" answer, so there is nothing
 * honest to say until a check runs.
 */
internal data class SettingsSummaries(
    /** Theme mode, e.g. "Dark". */
    val appearance: String? = null,
    /** Card style, e.g. "Exotic". */
    val interfaceStyle: String? = null,
    /** App-wide text scale, e.g. "110%". */
    val type: String? = null,
    /** Refresh interval, e.g. "30 min". */
    val library: String? = null,
    /** "Off", or the pace she talks at. */
    val jokes: String? = null,
    /** "Off", or the effect covering the app. */
    val seasonal: String? = null,
    /** "Local", or the number of categories that are on. */
    val notifications: String? = null,
    /** Whether the app asks for the device lock. */
    val device: String? = null,
    /** The installed build, so a bug report can start here. */
    val about: String? = null
)

/**
 * Reads the summaries from the preference instances the Settings graph already
 * holds - the same instances the pages write through, so a row cannot describe a
 * value the page below it is not using.
 *
 * Six collectors for nine rows: the interface's card style and layout share one
 * flow, and the device row reads the app lock rather than the frame-rate ceiling
 * because the lock is the one of the two a reader checks on the way past.
 */
@Composable
internal fun rememberSettingsSummaries(
    themePreferences: ThemePreferences,
    interfacePreferences: InterfacePreferences,
    refreshIntervalPreferences: RefreshIntervalPreferences,
    jokePreferences: JokePreferences,
    seasonalPreferences: SeasonalPreferences,
    appLockPreferences: AppLockPreferences,
    notificationPreferencesStore: NotificationPreferencesStore
): SettingsSummaries {
    val theme by themePreferences.settings.collectAsStateWithLifecycle(initialValue = null)
    val cardStyle by interfacePreferences.cardStyle.collectAsStateWithLifecycle(initialValue = null)
    val interval by refreshIntervalPreferences.interval.collectAsStateWithLifecycle(initialValue = null)
    val jokes by jokePreferences.settings.collectAsStateWithLifecycle(initialValue = null)
    val seasonal by seasonalPreferences.settings.collectAsStateWithLifecycle(initialValue = null)
    val appLock by appLockPreferences.enabled.collectAsStateWithLifecycle(initialValue = null)
    val notifications by notificationPreferencesStore.preferences.collectAsStateWithLifecycle(
        initialValue = null
    )

    return SettingsSummaries(
        appearance = theme?.themeMode?.displayName,
        interfaceStyle = cardStyle?.displayName,
        type = theme?.fontScale?.let { scale -> "${(scale * 100).roundToInt()}%" },
        library = interval?.displayName,
        jokes = jokes?.let { settings ->
            // "Off but Chatty" is a real state - the master switch and the pace
            // are separate preferences - so the row says Off when she is off and
            // names the pace when she is not. It never reports the stored pace
            // while the switch is off, which would read as running.
            if (!settings.enabled) "Off" else settings.frequency.displayName
        },
        seasonal = seasonal?.let { settings ->
            if (!settings.enabled) "Off" else settings.effect.displayName
        },
        notifications = notifications?.let { settings ->
            if (!settings.trackingEnabled) {
                "Unread dots off"
            } else {
                val on = listOf(
                    settings.airing,
                    settings.likes,
                    settings.replies,
                    settings.mentions,
                    settings.messages,
                    settings.follows,
                    settings.mediaUpdates,
                    settings.submissions
                ).count { it }
                if (on == 8) "All categories" else "$on of 8"
            }
        },
        device = appLock?.let { if (it) "Lock on" else "Lock off" },
        // The build is a constant, so this row has an answer before DataStore does.
        about = BuildConfig.VERSION_NAME
    )
}
