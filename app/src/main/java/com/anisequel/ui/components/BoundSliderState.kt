package com.anisequel.ui.components

import androidx.compose.material3.SliderState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key

/**
 * A `SliderState` for a slider whose value is owned by something outside it.
 *
 * ## The API change this papers over
 *
 * Material3 moved the value into `SliderState`, and the replacement it offers -
 * `rememberSliderState(value, steps, trackRange)` - documents that *later changes to those
 * arguments are ignored*: the state is created once and then owns the number. That is right
 * for a slider that is the source of truth, and wrong for all seven here, which are bound to
 * a draft or a preference that something else writes:
 *
 *  - the entry editor's score slider re-ticks when the account's score format changes, so a
 *    state built for `0f..10f` has to be thrown away when the entry moves to a 100-point
 *    scale, not keep drawing a ten-point track over a hundred-point value;
 *  - the Seasonal page's Reset writes every slider on the page back at once, and a state that
 *    only ever read its initial value would leave the thumbs where the reader left them while
 *    the numbers below them moved.
 *
 * ## What this does instead
 *
 * Re-creates the state when the geometry changes - `key(steps, trackRange)` around the
 * remember, which is what the deprecated `Slider(value = ...)` overload achieved with
 * `remember(steps, valueRange)` - and then pushes the incoming value into it, exactly as that
 * overload's own body did. So a drag still lands in `onValueChange` and nowhere else, and an
 * external write still moves the thumb.
 *
 * Deliberately not `value` as a plain parameter of the slider: keeping the state here is what
 * lets the seven call sites stay one line each.
 */
@Composable
internal fun rememberBoundSliderState(
    value: Float,
    steps: Int = 0,
    trackRange: ClosedFloatingPointRange<Float> = 0f..1f
): SliderState {
    val state = key(steps, trackRange) { rememberSliderState(value, steps, trackRange) }
    // SliderState is owned by Material after it is remembered, but this helper is
    // deliberately a controlled boundary: Reset buttons and preference updates can
    // change the value without a drag. Applying that write in the composition body
    // mutates snapshot state while Compose is still reading it, which can cause an
    // extra restart and made a fast Reset occasionally leave the thumb one frame
    // behind. SideEffect runs after composition commits and keeps the slider bound
    // without writing from inside composition.
    SideEffect {
        if (state.value != value) state.value = value
    }
    return state
}
