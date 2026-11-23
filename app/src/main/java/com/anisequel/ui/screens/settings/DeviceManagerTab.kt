package com.anisequel.ui.screens.settings

import android.app.KeyguardManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.repository.AppLockPreferences
import com.anisequel.data.repository.DevicePreferences
import com.anisequel.data.repository.SeasonalPreferences
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.domain.seasonal.SeasonalQuality
import com.anisequel.domain.seasonal.SeasonalSettings
import com.anisequel.domain.seasonal.availableSeasonalFrameRates
import com.anisequel.domain.seasonal.seasonalFrameHz
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.rememberIsResumed
import com.anisequel.ui.components.seasonal.detectSeasonalQualityTier
import com.anisequel.ui.components.seasonal.detectSeasonalScreen
import kotlinx.coroutines.launch

/**
 * What this phone reports, and how hard the app is allowed to work on it.
 *
 * ## Why a page of its own
 *
 * These four cards were scattered by the file each one was written in rather than
 * by what they are about: the frame-rate ceiling and the cover cache sat at the
 * bottom of the Seasonal effects page because the sky was the first heavy thing to
 * need them; full-screen mode sat on Appearance because it is stored in the theme
 * preferences; the app lock sat on the account page because it gates the session;
 * quick-add gestures sat on Library because that page owns the cards they act on.
 * A reader asking "what is this costing my device, and what does this device let the
 * app do" had to visit four pages to answer it.
 *
 * ## Why the seasonal sliders stayed behind
 *
 * "Density & size" reads as device-shaped and is not separable: its draft values are
 * what the Seasonal page's live sky preview follows, and moving the sliders would
 * leave one page with a preview that no longer answers to anything and another with
 * sliders that have nothing to preview.
 */
@Composable
fun DeviceManagerTab(
    seasonalPreferences: SeasonalPreferences,
    devicePreferences: DevicePreferences,
    themePreferences: ThemePreferences,
    appLockPreferences: AppLockPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Defaults rather than null, which is what every other settings page does -
    // including `SeasonalSettingsTab`, the page this card came from. The null used
    // to drive a pair of "Reading your saved … settings" placeholder cards, which
    // meant a cold open drew two skeletons, then two real cards: a layout jump, and
    // a screen whose own copy said it had not read anything yet. A default is a
    // number the reader may see for one frame; a placeholder is a screen telling
    // them it has nothing to show.
    //
    // The app-lock switch below keeps its own reading line, deliberately: it is one
    // line *inside* an existing card, so nothing moves, and drawing the switch off
    // for a frame before a stored "on" arrives would be a security claim the app has
    // not read yet.
    val seasonalSettings by seasonalPreferences.settings.collectAsStateWithLifecycle(
        initialValue = SeasonalSettings()
    )
    val themeSettings by themePreferences.settings.collectAsStateWithLifecycle(
        initialValue = ThemePreferences.ThemeSettings(
            themeMode = ThemeMode.DEFAULT,
            useDynamicColor = true
        )
    )
    val ceiling by devicePreferences.ceiling.collectAsStateWithLifecycle()

    // Re-read on resume, the same way the card did on the Seasonal page: a reader
    // who unlocks a higher refresh rate in the system settings should find the pill
    // waiting on the way back in, and the cache figure only moves with downloads.
    val resumed = rememberIsResumed()
    var screen by remember { mutableStateOf(detectSeasonalScreen(context)) }
    var usedBytes by remember { mutableStateOf(context.coverCacheBytes()) }
    LaunchedEffect(resumed) {
        screen = detectSeasonalScreen(context)
        usedBytes = context.coverCacheBytes()
    }

    SettingsScrollColumn(modifier) {
        val settings = seasonalSettings
        val detected = remember { detectSeasonalQualityTier(context) }
        val resolvedQuality = remember(settings.quality, detected) {
            settings.quality.resolve(detected)
        }
        DeviceCard(
            rate = settings.frameRate,
            offered = availableSeasonalFrameRates(screen.currentHz, screen.supportedHz),
            resolvedHz = seasonalFrameHz(
                rate = settings.frameRate,
                tierHz = resolvedQuality.frameHz,
                deviceHz = screen.currentHz
            ),
            screen = screen,
            tierName = SeasonalQuality.of(resolvedQuality)
                .displayName
                .lowercase(java.util.Locale.ROOT),
            ceiling = ceiling,
            usedBytes = usedBytes,
            onRateChange = { rate ->
                scope.launch { seasonalPreferences.setFrameRate(rate) }
            },
            onCeilingChange = { option ->
                // Synchronous by design: one string in a preferences file, whose value
                // the image loader reads at the next process start.
                devicePreferences.setCeiling(option)
            },
            onClearCache = {
                // Coil's clear walks and deletes the disk cache's journal and
                // files. On the click frame that is the whole page freezing for
                // the duration of a file delete, so both the clear and the new
                // size go to IO and the figure updates when they return.
                scope.launch {
                    usedBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.clearCoverCache()
                        context.coverCacheBytes()
                    }
                }
            }
        )

        DisplaySection(
            themePreferences = themePreferences,
            settings = themeSettings,
            scope = scope
        )

        AppLockCard(appLockPreferences)

        // The hold/swipe/confirm gestures moved to the Interface page, which is
        // where the rest of "how does this app behave" lives - and where their
        // preview can be tapped without a card to hold it.

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The optional app lock.
 *
 * The switch is disabled, with the reason in its subtitle, when the device has
 * no secure lock. That is not a limitation invented here - the platform's
 * credential prompt has nothing to ask for without one - and offering a switch
 * that would silently fail open is the worse version of the same truth.
 *
 * Moved here from the account page because the question it answers is about the
 * device, not the session: "who may open this app on this phone" is answered by
 * this phone's lock screen, and the account page has no way to know it.
 */
@Composable
private fun AppLockCard(appLockPreferences: AppLockPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabledState by appLockPreferences.enabled.collectAsStateWithLifecycle(initialValue = null)
    // Re-read when the app comes back, not once for the life of the card.
    //
    // This used to be a bare `remember`, and the screen it depends on is one the
    // reader has to leave the app to change: add a PIN in the system Settings and
    // come back, and the switch was still disabled and still saying "Set a screen
    // lock on this device first" about a screen lock that was already set - until
    // the process restarted. Every other platform fact on these pages is re-read on
    // resume for the same reason (`screen` and `usedBytes` below, and `MotionSection`
    // re-reads the animation scale), because a value read once behind a screen the
    // reader can edit goes stale on the way back.
    val resumed = rememberIsResumed()
    var deviceHasSecureLock by remember {
        mutableStateOf(
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        )
    }
    LaunchedEffect(resumed) {
        deviceHasSecureLock =
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

    SectionCard(
        title = "App lock",
        icon = AppVectorIcons.Privacy,
        subtitle = "Who can open AniSequel"
    ) {
        if (enabledState == null) {
            Text(
                text = "Reading your app lock setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SwitchRow(
                title = "Lock AniSequel",
                subtitle = when {
                    deviceHasSecureLock ->
                        "Ask for your fingerprint, face or screen lock when the app opens or returns from the background."
                    enabledState == true ->
                        "The screen lock this used was removed from the device. Turn the lock off here, or set a screen lock again."
                    else ->
                        "Set a screen lock on this device first - there is nothing to ask for yet."
                },
                checked = enabledState == true,
                // A stored "on" has to stay removable. If the device lock this
                // switch relied on is gone, the switch drew itself checked and
                // disabled, so the preference could never be cleared - and the
                // app would silently lock again the moment a screen lock was set.
                // Off is always allowed; on still needs something to ask for.
                enabled = deviceHasSecureLock || enabledState == true,
                onCheckedChange = { locked ->
                    scope.launch { appLockPreferences.setEnabled(locked) }
                },
                modifier = Modifier.testTag("app_lock_switch")
            )
        }
    }
}
