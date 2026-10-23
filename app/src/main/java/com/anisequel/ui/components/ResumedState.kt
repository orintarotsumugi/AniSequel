package com.anisequel.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Whether the Activity is in the `RESUMED` state right now.
 *
 * ## Why this exists as one helper
 *
 * Two animation clocks in the app have to stop when the app is not on screen: the
 * joke toast's wait ([JokeToastHost]) and the empty-state orb's morph
 * ([com.anisequel.ui.components.expressive.ExpressiveEmptyOrb]). Both had grown
 * their own copy of "set a flag in a `LifecycleResumeEffect` and remember it", and
 * the copies had already drifted - the toast's was keyed on its `enabled` argument,
 * so switching pages restarted it, while the orb had no gate at all and went on
 * morphing behind a stopped Activity.
 *
 * `LifecycleResumeEffect(Unit)` rather than `LocalLifecycleOwner` plus a manual
 * observer: this is the documented API for "do something on resume and undo it on
 * pause or dispose", and it is what the dashboard's freshness check already uses.
 * The `Unit` key is deliberate - the answer is a property of the process, not of
 * the caller, so a caller-specific key would only make the flag flicker false and
 * true for no reason.
 *
 * Returns `false` for the first frame, before the lifecycle has reported in. That
 * is the honest answer: nothing has confirmed the screen is up yet, so a caller
 * that starts a clock should wait one frame rather than start one it may have to
 * cancel. In practice the first `ON_RESUME` lands in the same frame as the first
 * composition, so nothing visible is delayed.
 */
@Composable
fun rememberIsResumed(): Boolean {
    var resumed by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }

    return resumed
}
