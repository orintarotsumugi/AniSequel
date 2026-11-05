package com.anisequel.ui.screens.settings

import android.app.KeyguardManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.AppLockPreferences
import com.anisequel.data.repository.DevicePreferences
import com.anisequel.data.repository.QuickAddPreferences
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
    quickAddPreferences: QuickAddPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by seasonalPreferences.settings.collectAsState(initial = SeasonalSettings())
    val themeSettings by themePreferences.settings.collectAsState(
        initial = ThemePreferences.ThemeSettings(
            themeMode = ThemeMode.DEFAULT,
            useDynamicColor = false
        )
    )
    val ceiling by devicePreferences.ceiling.collectAsState()

    val detected = remember { detectSeasonalQualityTier(context) }
    val resolvedQuality = remember(settings.quality, detected) {
        settings.quality.resolve(detected)
    }

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
        DeviceCard(
            rate = settings.frameRate,
            offered = availableSeasonalFrameRates(screen.currentHz, screen.supportedHz),
            resolvedHz = seasonalFrameHz(
                rate = settings.frameRate,
                tierHz = resolvedQuality.frameHz,
                deviceHz = screen.currentHz
            ),
            screen = screen,
            tierName = SeasonalQuality.of(resolvedQuality).displayName.lowercase(),
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
                context.clearCoverCache()
                usedBytes = context.coverCacheBytes()
            }
        )

        DisplaySection(
            themePreferences = themePreferences,
            settings = themeSettings,
            scope = scope
        )

        AppLockCard(appLockPreferences)

        QuickAddSectionCard(quickAddPreferences)

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
    val enabled by appLockPreferences.enabled.collectAsState(initial = false)
    val deviceHasSecureLock = remember {
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

    SectionCard(
        title = "App lock",
        icon = AppVectorIcons.Privacy,
        subtitle = "Who can open AniSequel"
    ) {
        SwitchRow(
            title = "Lock AniSequel",
            subtitle = if (deviceHasSecureLock) {
                "Ask for your fingerprint, face or screen lock when the app opens or returns from the background."
            } else {
                "Set a screen lock on this device first - there is nothing to ask for yet."
            },
            checked = enabled,
            enabled = deviceHasSecureLock,
            onCheckedChange = { locked ->
                scope.launch { appLockPreferences.setEnabled(locked) }
            },
            modifier = Modifier.testTag("app_lock_switch")
        )
    }
}
