package com.anisequel.ui.components.detail.info

import android.view.View
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Where a trailer is, as far as the viewer is allowed to know.
 *
 * [Loading] and [Ready] are not a spinner and a video: the surface stays invisible until
 * [Ready], because an HTML embed that has not reported itself yet has nothing to show.
 * [Ended] is separate from a paused [Ready] so the transport can offer a replay, and
 * [Failed] carries the reason so the frame can be replaced by an explanation instead of
 * YouTube's own error card sitting inside a 16:9 hole with live controls over it.
 */
internal sealed interface NovaTrailerPhase {
    data object Loading : NovaTrailerPhase
    data object Ready : NovaTrailerPhase
    data object Ended : NovaTrailerPhase
    data class Failed(val code: Int) : NovaTrailerPhase
}

/**
 * A player that never reported ready. Not a YouTube error: the failure watchdog's code.
 *
 * This one is recoverable by waiting, which is why a late [NovaTrailerSignal.Ready] is
 * still allowed to replace it - see [NovaTrailerSession.onSignal].
 */
internal const val NOVA_TRAILER_CODE_TIMEOUT = -1

/** The IFrame API script itself did not load, so no player can ever appear. */
internal const val NOVA_TRAILER_CODE_NO_PLAYER = -2

/**
 * What to tell the reader about a refused video.
 *
 * The codes are YouTube's, so the sentences are the ones a reader can act on: a private
 * video is not a bug to retry, and "the owner has turned off playback outside YouTube"
 * is the only reading of 101 and 150 that tells them to use the escape hatch below.
 * Retrying a refusal just spends the reader's data on the same answer.
 */
internal fun novaTrailerFailureReason(code: Int): String = when (code) {
    NOVA_TRAILER_CODE_TIMEOUT -> "YouTube did not answer in time."
    NOVA_TRAILER_CODE_NO_PLAYER -> "The YouTube player could not be loaded."
    2 -> "YouTube did not recognise this video."
    5 -> "This video cannot play in the in-app player."
    100 -> "This video was removed or is private."
    101, 150 -> "The owner has turned off playback outside YouTube."
    153 -> "YouTube refused the request because it had no referrer."
    else -> "YouTube could not play this trailer here."
}

/**
 * Whether a retry could plausibly give a different answer.
 *
 * A network failure, a player that never appeared, a decoding error: yes. A video that
 * is private, removed, blocked from embeds or rejected for its referrer: no, and the
 * viewer hides the button rather than offering a tap that is guaranteed to fail - the
 * escape hatch beside it is the way out.
 */
internal fun isNovaTrailerRetryable(code: Int): Boolean = when (code) {
    2, 100, 101, 150, 153 -> false
    else -> true
}

/**
 * "1:04" and "1:02:03", with the hours dropped when there are none.
 *
 * A locale-pinned format: the reader's locale decides digit groups, and a Hindi locale
 * renders `%d` as Devanagari digits - which is correct for a date and wrong for a
 * transport clock that has to line up under a slider on every device. A negative or
 * non-finite value is "0:00": the page reports -1 before it has a player, and a clock
 * reading "-1:-1" was the old code's way of saying "not yet".
 */
internal fun formatNovaTrailerTime(seconds: Float): String {
    if (!seconds.isFinite() || seconds <= 0f) return "0:00"
    val total = seconds.toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val remainder = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, remainder)
    }
}

/**
 * Everything the viewer knows about the trailer, in one place.
 *
 * ## Why a class and not five values in the composable
 *
 * The previous viewer kept `playerReady`, `isPlaying`, `playerError`, `duration` and
 * `retryKey` as separate `mutableStateOf` values in the composable, written from a
 * callback and read, in a different order, by the close path. Close, background and
 * playback each reached into a different subset, so the three could disagree - a close
 * could pause a video whose state had already been reported as paused, and a retry could
 * leave the old error on screen because only one of the five was reset. One object with
 * one transition function makes "what the viewer draws" a function of "what the player
 * last said", and gives the retry a single place to reset.
 *
 * It is deliberately not a ViewModel: it lives exactly as long as the dialog, its source
 * of truth is a `WebView` the composition owns, and it needs no coroutine scope.
 */
internal class NovaTrailerSession {
    var phase by mutableStateOf<NovaTrailerPhase>(NovaTrailerPhase.Loading)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionSeconds by mutableFloatStateOf(0f)
        private set
    var durationSeconds by mutableFloatStateOf(0f)
        private set

    /** Whether the page has said it is ready; the viewer draws its ring until it has. */
    var revealed by mutableStateOf(false)
        private set

    /**
     * Which player the stage is showing.
     *
     * The retry increments this and the stage keys its `AndroidView` on it, so a retry
     * builds a new `WebView` rather than reloading the one that is already wedged. A
     * reload would reuse a page whose script state is exactly what failed.
     */
    var attempt by mutableIntStateOf(0)
        private set

    private var player: WebView? = null

    val controlsEnabled: Boolean
        get() = when (phase) {
            NovaTrailerPhase.Ready, NovaTrailerPhase.Ended -> true
            else -> false
        }

    val seekEnabled: Boolean
        get() = controlsEnabled && durationSeconds > 0f

    val hasEnded: Boolean
        get() = phase == NovaTrailerPhase.Ended

    fun attach(view: WebView) {
        player = view
    }

    fun detach(view: WebView) {
        if (player === view) player = null
    }

    fun onRevealed() {
        revealed = true
    }

    /**
     * One event from the page.
     *
     * A refusal is final for the attempt: YouTube reporting 101/150/100 means the video
     * cannot play here, and a later `onReady` - which the IFrame API can still emit after
     * an error for the same frame - would only hide the explanation while the player
     * showed YouTube's own error card. A timeout is not final, because it is a statement
     * about the clock rather than about the video.
     */
    fun onSignal(signal: NovaTrailerSignal) {
        when (signal) {
            is NovaTrailerSignal.Ready -> {
                val refusal = phase as? NovaTrailerPhase.Failed
                if (refusal != null && refusal.code != NOVA_TRAILER_CODE_TIMEOUT) return
                if (signal.durationSeconds > 0f) durationSeconds = signal.durationSeconds
                phase = NovaTrailerPhase.Ready
            }
            is NovaTrailerSignal.StateChanged -> onPlaybackState(signal.state)
            is NovaTrailerSignal.Failed -> {
                // The first explanation wins. A state event or a second failure would
                // otherwise replace "the owner blocked embeds" with "paused".
                if (phase is NovaTrailerPhase.Failed) return
                isPlaying = false
                phase = NovaTrailerPhase.Failed(signal.code)
            }
        }
    }

    /**
     * Applies a YouTube player state code.
     *
     * Buffering (3) keeps whatever the reader last chose: YouTube reports it both while
     * a playing video stalls and while a paused one is skipped forward, and flipping the
     * glyph on either would make the transport button flicker during a seek. Every other
     * state is a real answer about whether sound should be playing.
     */
    private fun onPlaybackState(state: Int) {
        if (phase is NovaTrailerPhase.Failed) return
        when (state) {
            0 -> {
                positionSeconds = durationSeconds
                phase = NovaTrailerPhase.Ended
            }
            else -> if (phase is NovaTrailerPhase.Ended) phase = NovaTrailerPhase.Ready
        }
        when (state) {
            1 -> isPlaying = true
            3 -> Unit
            else -> isPlaying = false
        }
    }

    /**
     * Applies one reading of the player.
     *
     * The state code is passed through [onPlaybackState] so a snapshot and an event can
     * never disagree: both driving the same transition is what makes the progress poll a
     * measurement rather than a second source of truth.
     */
    fun onSnapshot(raw: String?) {
        parseNovaTrailerSnapshot(raw)?.let(::applySnapshot)
    }

    /** The parsed half of [onSnapshot]: the poll hands its reading straight to this. */
    private fun applySnapshot(snapshot: NovaTrailerSnapshot) {
        if (snapshot.durationSeconds > 0f) durationSeconds = snapshot.durationSeconds
        if (snapshot.positionSeconds >= 0f) positionSeconds = snapshot.positionSeconds
        if (snapshot.state >= 0) onPlaybackState(snapshot.state)
    }

    /**
     * Play, pause, or start over.
     *
     * The page's own state decides the first two - it is the source of truth, and the
     * Kotlin mirror can be one event behind, so a pause that arrived while the reader was
     * tapping would otherwise make the button a no-op. An ended video is restarted from
     * the beginning instead, because a play call on an ended player may simply do nothing.
     */
    fun togglePlayback() {
        if (hasEnded) {
            seekTo(0f)
            player?.let { playNovaTrailer(it) }
            return
        }
        player?.let { toggleNovaTrailer(it) }
    }

    /**
     * Seeks to [seconds], clamped to the video it belongs to.
     *
     * The position is written first and the command second: the reader dragged a thumb
     * and should see it stay where they let go, even if the page ignores a seek it was
     * not ready for yet. The next snapshot corrects it if the player disagreed.
     */
    fun seekTo(seconds: Float) {
        if (!seconds.isFinite()) return
        val end = durationSeconds
        val target = if (end > 0f) seconds.coerceIn(0f, end) else seconds.coerceAtLeast(0f)
        positionSeconds = target
        player?.let { seekNovaTrailer(it, target) }
    }

    fun seekBy(delta: Float) = seekTo(positionSeconds + delta)

    /** Asks the page where playback is. Answers arrive through [onSnapshot]. */
    fun syncPosition() {
        player?.let { view -> readNovaTrailerSnapshot(view) { snapshot -> applySnapshot(snapshot) } }
    }

    /**
     * The close: sound first, then the surface.
     *
     * The window fades out over a couple of hundred milliseconds, and the trailer's audio
     * would keep running behind that fade if the video were left alone - the reported
     * symptom was music continuing from a viewer that looked closed. Pausing first also
     * means the platform tears down a *stopped* decoder rather than one that is mid-frame.
     * The surface goes invisible rather than transparent: this view cannot be faded
     * without compositing the video plane, which is the black-picture failure the layer
     * type in [createNovaTrailerPlayer] exists to avoid.
     */
    fun stop() {
        isPlaying = false
        player?.let {
            pauseNovaTrailer(it)
            it.visibility = View.INVISIBLE
        }
    }

    /**
     * The screen went away: pause the media and tell the view, in that order.
     *
     * `WebView.onPause()` stops the page's timers and gives up its audio focus, but it
     * does not pause the `<video>` element - the play call was made inside the iframe.
     * Without the explicit pause the trailer kept playing behind the home screen.
     */
    fun pauseForBackground() {
        isPlaying = false
        player?.let {
            pauseNovaTrailer(it)
            it.onPause()
        }
    }

    fun resumeFromBackground() {
        player?.let { it.onResume() }
    }

    /**
     * Builds a fresh player for the same video.
     *
     * Every field is reset, not just the error: the retry's whole point is that nothing
     * from the failed attempt - its duration, its position, its visibility - carries over
     * into the new one. `attempt` is what makes the stage replace the viewer's view.
     */
    fun retry() {
        attempt += 1
        phase = NovaTrailerPhase.Loading
        isPlaying = false
        revealed = false
        positionSeconds = 0f
        durationSeconds = 0f
        player = null
    }
}
