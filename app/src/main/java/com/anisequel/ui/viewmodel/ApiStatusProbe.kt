package com.anisequel.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.ApiStatusSnapshot
import com.anisequel.data.network.isWorthProbing
import com.anisequel.data.network.toReachability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The status strip's state, as the UI draws it.
 *
 * [isChecking] is separate from [reachability] because the two answer different
 * questions: "what do we believe right now" and "are we confirming it". A strip
 * that replaced the last known verdict with a spinner for eight seconds would
 * make the reader lose the information they opened the screen for.
 */
data class ApiStatusUi(
    val reachability: ApiReachability = ApiReachability.UNKNOWN,
    val latencyMillis: Long? = null,
    val isChecking: Boolean = false
)

/**
 * Runs the reachability probe and remembers the answer for one screen.
 *
 * ## Why the failure kind seeds the state
 *
 * Most failures already say something about reachability: a rejected token
 * means AniList answered, and a 500 means it answered badly. Starting from
 * [toReachability] means the strip is correct on the first frame, and only the
 * genuinely ambiguous failures ([isWorthProbing]) pay for a test request.
 *
 * ## Why a probe is cancelled when a newer failure arrives
 *
 * The probe of an old failure can finish after the screen has moved on to a new
 * one. Without the cancel, the strip would briefly announce the previous
 * failure's verdict under the new failure's headline - a stale status is worse
 * than a spinner, because it looks authoritative.
 */
@Stable
class ApiStatusProbeState internal constructor(
    // Not named `probe`: the public action below is `probe()`, and a property of
    // the same name would be shadowed by that member function inside this class -
    // `probe()` would resolve to the action and recurse into itself instead of
    // issuing the request.
    private val requestStatus: suspend () -> ApiStatusSnapshot,
    private val scope: CoroutineScope
) {
    private val _ui = MutableStateFlow(ApiStatusUi())
    val ui: StateFlow<ApiStatusUi> = _ui.asStateFlow()

    private var inFlight: Job? = null

    /**
     * Called once when an error state appears.
     *
     * A null [kind] - a screen that failed without a classified exception -
     * still probes, because "something went wrong" is exactly the case where
     * the reader benefits from being told whether AniList is even there.
     */
    fun onFailure(kind: AniListErrorKind?) {
        inFlight?.cancel()
        _ui.value = ApiStatusUi(reachability = kind.toReachability())
        if (kind == null || kind.isWorthProbing()) {
            probe()
        }
    }

    /** The manual "Check" action. Re-entrant calls are ignored, not queued. */
    fun probe() {
        if (inFlight?.isActive == true) return
        inFlight = scope.launch {
            _ui.update { it.copy(isChecking = true) }
            val snapshot = requestStatus()
            _ui.value = ApiStatusUi(
                reachability = snapshot.reachability,
                latencyMillis = snapshot.latencyMillis,
                isChecking = false
            )
        }
    }
}

/**
 * One probe state per screen, tied to that screen's coroutine scope.
 *
 * The probe is read through [rememberUpdatedState] rather than captured
 * directly: a method reference such as `repository::checkApiReachability`
 * allocates a new lambda on every recomposition, and remembering on it would
 * rebuild the state - and cancel the check the reader is watching - every time
 * the screen redrew.
 */
@Composable
fun rememberApiStatusProbe(probe: suspend () -> ApiStatusSnapshot): ApiStatusProbeState {
    val scope = rememberCoroutineScope()
    val currentProbe = rememberUpdatedState(probe)
    return remember(scope) {
        ApiStatusProbeState(requestStatus = { currentProbe.value() }, scope = scope)
    }
}
