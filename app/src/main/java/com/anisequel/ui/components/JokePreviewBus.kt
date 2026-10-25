package com.anisequel.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One "show me a joke" request from Settings.
 *
 * [tick] is the request counter and [line] is an optional exact line. A saved
 * favourite is previewed as itself rather than as another random draw: the
 * reader tapped that line because they want to see that line, and "your
 * favourite, but not" is a strange thing for a preview to do.
 */
data class JokePreviewRequest(val tick: Int, val line: String? = null)

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
    private val _requests = MutableStateFlow(JokePreviewRequest(0))

    /** The requests so far. Tick zero means "never asked". */
    val requests: StateFlow<JokePreviewRequest> = _requests

    fun requestPreview(line: String? = null) {
        _requests.value = JokePreviewRequest(_requests.value.tick + 1, line)
    }
}
