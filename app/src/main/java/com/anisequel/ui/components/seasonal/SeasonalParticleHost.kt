package com.anisequel.ui.components.seasonal

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.anisequel.data.repository.SeasonalPreferences
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_FRAME_HZ
import com.anisequel.domain.seasonal.SeasonalSettings
import com.anisequel.domain.seasonal.seasonalFrameHz

/**
 * The seasonal sky, floated over every page.
 *
 * ## Why it collects its own preferences
 *
 * Mounted in the app root, above the `NavHost` but below the joke toast. It reads
 * [SeasonalPreferences] itself instead of being handed a value from
 * `AppNavigation`, so a change to the density recollects this host and this host
 * alone. Passing the settings down would have subscribed the app's root - and
 * with it the `NavHost` and every destination table - to every slider tick on the
 * Settings page.
 *
 * ## Why it is a [BoxScope] extension
 *
 * `matchParentSize` only exists in a `BoxScope`, and it is the right modifier
 * here: the layer is an overlay, so it must take the parent's measured size
 * without contributing to it. `fillMaxSize` would work in this particular Box
 * today and would quietly start sizing the root the moment the Box gained a
 * wrapping parent.
 *
 * The layer has no pointer input, so taps pass through it to the page underneath
 * even though it is drawn on top. Gestures are observed by the app root instead
 * and arrive here as wind; see [rememberSeasonalAmbientModifier].
 *
 * ## Why the wind is reset on every toggle
 *
 * The observers at the root are always installed - they have to be, or the first
 * gust after switching the sky on would need a page reload to register. That
 * means a reader who scrolls with the effect off keeps pushing a wind nobody is
 * drawing, and the first frame after switching it back on would arrive mid-gust.
 * Clearing on every toggle makes every enable start from a still sky - including
 * the gusts the chrome sends on page changes while the effect is off.
 */
@Composable
fun BoxScope.SeasonalParticleHost(preferences: SeasonalPreferences) {
    val settings by preferences.settings.collectAsState(initial = SeasonalSettings())
    val context = LocalContext.current
    val detected = remember { detectSeasonalQualityTier(context) }
    // Re-read every time the sky's settings change rather than once for the
    // process: the active rate is a system setting, and a reader who switches the
    // panel from 60 to 120 while AniSequel is open should get the frames they just
    // unlocked without a restart.
    val screen = detectSeasonalScreen(context)
    val quality = remember(settings.quality, detected) { settings.quality.resolve(detected) }
    val frameHz = seasonalFrameHz(
        rate = settings.frameRate,
        tierHz = quality.frameHz,
        deviceHz = screen.currentHz
    )

    LaunchedEffect(settings.enabled) {
        SeasonalWind.reset()
    }

    // Asking the frame loop for 120 is only half of it. The system buckets an app's
    // window into a refresh-rate category and leaves ordinary content in the default
    // one, which on many panels means 60 no matter what the app's clock asks for.
    // Above sixty the overlay therefore asks for the high category on the view that
    // is actually being composited, and puts it back the moment the sky is off or
    // capped - the battery cost of a high-rate window is the app's to accept only
    // while the reader has a sky on screen that needs it.
    //
    // The category API is Android 14. On Android 12 and 13 this is a no-op rather
    // than a wrong answer: the sky still steps at the rate the reader chose, it just
    // cannot lift the window out of the bucket the platform left it in. Calling it
    // without the guard is a `NoSuchMethodError` on those releases, and `lintRelease`
    // does not fail the build, so the check is the only thing standing between this
    // line and a crash on the two releases before the current one.
    val root = LocalView.current
    SideEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            root.setRequestedFrameRate(
                if (settings.enabled && frameHz > DEFAULT_SEASONAL_FRAME_HZ) {
                    View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
                } else {
                    View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
                }
            )
        }
    }

    if (!settings.enabled) return

    Box(modifier = Modifier.matchParentSize()) {
        SeasonalParticleLayer(
            effect = settings.effect,
            density = settings.density,
            sizeScale = settings.sizeScale,
            speedScale = settings.speedScale,
            settle = settings.settle,
            pileDepth = settings.pileDepth,
            reactToWind = settings.reactToWind,
            windStrength = settings.windStrength,
            quality = quality,
            frameHz = frameHz
        )
    }
}
