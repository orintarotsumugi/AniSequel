package com.anisequel.ui.components.seasonal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.anisequel.data.repository.SeasonalPreferences
import com.anisequel.domain.seasonal.SeasonalSettings

/**
 * The seasonal sky, floated over every page.
 *
 * ## Why it collects its own preferences
 *
 * Mounted in the app root, above the `NavHost` but below the bar and the joke
 * toast. It reads [SeasonalPreferences] itself instead of being handed a value
 * from `AppNavigation`, so a change to the density recollects this host and this
 * host alone. Passing the settings down would have subscribed the app's root -
 * and with it the `NavHost` and every destination table - to every slider tick on
 * the Settings page.
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
 * even though it is drawn on top. That is the one thing this overlay must never
 * do differently - a full-screen decorative canvas that eats a tap makes the
 * whole app feel broken.
 */
@Composable
fun BoxScope.SeasonalParticleHost(preferences: SeasonalPreferences) {
    val settings by preferences.settings.collectAsState(initial = SeasonalSettings())
    if (!settings.enabled) return

    Box(modifier = Modifier.matchParentSize()) {
        SeasonalParticleLayer(
            effect = settings.effect,
            density = settings.density,
            sizeScale = settings.sizeScale,
            speedScale = settings.speedScale
        )
    }
}
