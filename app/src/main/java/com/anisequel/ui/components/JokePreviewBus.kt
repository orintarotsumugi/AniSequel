package com.anisequel.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A request counter for "show me a joke now".
 *
 * The toast is hosted once by `AppNavigation`, not by Settings: a screen cannot
 * draw a surface that outlives it, and the whole point of previewing a position
 * is to see the real host draw at that edge. A counter rather than a one-shot
 * event so a preview repeated with the same joke is still a new request, and a
 * `StateFlow` rather than a callback so the button does not need a reference to
 * the navigation graph - which is also what keeps this preview working from a
 * pushed Settings page.
 *
 * A process-wide object is enough because there is one toast and one window;
 * this is not user data and it deliberately does not survive the process.
 */
object JokePreviewBus {
    private val _requests = MutableStateFlow(0)

    /** The number of previews requested so far. Zero means "never asked". */
    val requests: StateFlow<Int> = _requests

    fun requestPreview() {
        _requests.value += 1
    }
}
