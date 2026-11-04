package com.anisequel.ui.components.seasonal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The weather the reader makes: gusts from scrolling, and the pulse from a tap.
 *
 * ## Why this is a process-wide object and not settings state
 *
 * Every page contains its own scrollables, and the seasonal layer sits above the
 * navigation graph rather than inside any one screen. There is no composition
 * boundary at which "the whole app's scroll" could be handed in without
 * subscribing the app root to the seasonal file or threading a parameter through
 * every destination. Wind is ambient, like the clock: one sky, one set of forces,
 * and the Settings preview should feel the same gust the page behind it does.
 *
 * The values are plain `@Volatile` fields rather than snapshot state on purpose.
 * The layer already redraws every frame from its clock, so a snapshot write here
 * would invalidate the draw twice for the same frame; and while the sky is paused
 * nothing should be waking the display to animate an effect nobody is looking at.
 * When the sky resumes, the next frame reads whatever the wind has decayed to.
 *
 * ## Why scrolling never consumes anything
 *
 * [SeasonalScrollConnection.onPostScroll] observes the delta a child has *already*
 * consumed and returns `Offset.Zero`. Returning the delta would make this
 * connection a participant in the gesture, and a nested-scroll connection that
 * eats pixels makes every list in the app scroll short. The same contract holds
 * for the tap observer: it reads the event in [PointerEventPass.Initial] and
 * never touches `consume()`, so every button, chip and list underneath receives
 * exactly the gesture it received before this file existed.
 */
object SeasonalWind {

    /** Lateral offset, -1..1, as a fraction of the layer's width at full strength. */
    @Volatile
    internal var gust: Float = 0f
        private set

    /** Up/down offset, -1..1, as a fraction of the layer's height at full strength. */
    @Volatile
    internal var lift: Float = 0f
        private set

    /** A tap's shockwave, 1 at the moment of the tap, easing to 0. */
    @Volatile
    internal var pulse: Float = 0f
        private set

    /** Where the current tap landed, as fractions of the layer. */
    @Volatile
    internal var pulseX: Float = 0.5f
        private set

    @Volatile
    internal var pulseY: Float = 0.5f
        private set

    /**
     * A slow ambient breeze, -1..1, independent of the reader's finger.
     *
     * Gesture gusts used to be the only wind. Turning Wind & touch on with a
     * still page then did nothing, which read as the switch being broken. The
     * breeze is what makes the sky lean on its own; [gust] still stacks on top
     * when the reader scrolls or taps.
     */
    @Volatile
    internal var breeze: Float = 0f
        private set

    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1

    /** Alternates the page-change gust so it does not always blow one way. */
    private var stirDirection: Float = 1f

    /**
     * The last frame this object was decayed on.
     *
     * Two layers are alive at once while the Seasonal effects page is open - the
     * preview and the overlay behind it - and each runs a clock. Without this the
     * wind would be decayed twice per frame, which is not a visible bug until the
     * reader opens Settings and the gust abruptly halves.
     */
    private var lastDecayNanos: Long = 0L

    /** True while a finger is driving the wind, so a nested-scroll fling does not double it. */
    private var fingerDriving: Boolean = false

    private var breezePhase: Float = 0f

    internal fun resize(width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
    }

    /**
     * Adds a scroll's already-consumed delta to the wind.
     *
     * Vertical travel is folded into [lift] as well as a little sideways tilt:
     * most scrolling in this app is vertical, so a wind that only listened to
     * horizontal travel would never move. The vertical share is deliberately
     * smaller than the horizontal one, because a list flick is a much larger
     * gesture than a horizontal one and would otherwise read as a storm.
     */
    internal fun scroll(consumed: Offset, fromFinger: Boolean = false) {
        if (consumed == Offset.Zero) return
        // Nested-scroll deltas and the pointer observer both see the same drag.
        // Counting both makes every list storm; counting only nested scroll
        // makes every page that does not participate (pagers, a still Settings
        // preview) look like wind is broken. The finger owns the drag; nested
        // scroll owns the fling after lift.
        if (!fromFinger && fingerDriving) return
        val horizontal = consumed.x / viewportWidth
        val vertical = consumed.y / viewportHeight
        gust = (gust + horizontal * 1.6f + vertical * 0.18f).coerceIn(-1f, 1f)
        lift = (lift + vertical * 0.9f).coerceIn(-1f, 1f)
    }

    internal fun fingerDown() {
        fingerDriving = true
    }

    internal fun fingerUp() {
        fingerDriving = false
    }

    /** Turns a tap in layer pixels into a pulse at that point. */
    internal fun tapAt(position: Offset) {
        val x = (position.x / viewportWidth).coerceIn(0f, 1f)
        val y = (position.y / viewportHeight).coerceIn(0f, 1f)
        pulseX = x
        pulseY = y
        pulse = 1f
        // A tap near an edge pushes the whole sky away from that edge, which is
        // the part that makes a tap feel like it displaced air rather than only
        // lighting a ripple.
        gust = (gust + (x - 0.5f) * 0.35f).coerceIn(-1f, 1f)
        lift = (lift + 0.18f).coerceIn(-1f, 1f)
    }

    /**
     * A small gust when the app changes page.
     *
     * The sky should notice the UI moving, not only the reader's finger. The
     * direction alternates so a walk through Settings does not look like a
     * permanent wind from one side.
     */
    internal fun stir() {
        stirDirection = -stirDirection
        gust = (gust + 0.28f * stirDirection).coerceIn(-1f, 1f)
        lift = (lift + 0.08f).coerceIn(-1f, 1f)
    }

    /**
     * Eases everything back to rest.
     *
     * Exponential rather than a spring because this is a force that was already
     * applied, not a transition toward a chosen target: a gust should lose most of
     * its energy in the first half-second and a pulse should be gone in about one,
     * with no overshoot that would make the sky shimmer after the finger stopped.
     */
    internal fun decay(stepSeconds: Float, frameNanos: Long) {
        if (frameNanos == lastDecayNanos) return
        lastDecayNanos = frameNanos
        val step = stepSeconds.coerceIn(0f, 0.1f)
        gust *= exp(-1.9f * step)
        lift *= exp(-3.4f * step)
        pulse *= exp(-3.0f * step)
        if (abs(gust) < 0.002f) gust = 0f
        if (abs(lift) < 0.002f) lift = 0f
        if (pulse < 0.002f) pulse = 0f
        breezePhase += step
        // Two slow sines, not a single period: a breeze that repeats every few
        // seconds reads as a pendulum, not as air.
        breeze = (
            sin(breezePhase * 0.37f) * 0.34f +
                sin(breezePhase * 0.13f + 1.7f) * 0.18f
            ).coerceIn(-1f, 1f)
    }

    /** Forgets every force, used when the sky is switched off. */
    fun reset() {
        gust = 0f
        lift = 0f
        pulse = 0f
        breeze = 0f
        fingerDriving = false
        lastDecayNanos = 0L
    }
}

/**
 * Installs the non-consuming observers that feed [SeasonalWind].
 *
 * Put this on the app's root container - the one Box that contains the NavHost
 * and the season layer - not on the layer itself. The layer is a sibling of the
 * graph and must stay free of pointer input: a full-screen sibling with a pointer
 * modifier is hit-tested before the page underneath and makes every control in the
 * app need two taps. A *parent* pointer observer does not compete with its
 * children, which is exactly the distinction that lets taps be noticed without
 * being taken.
 *
 * The returned modifier is remembered, so the pointer observer and the scroll
 * connection are installed once for the app's lifetime rather than on every
 * recomposition of the root.
 */
@Composable
fun rememberSeasonalAmbientModifier(): Modifier = remember {
    Modifier
        .onSizeChanged { size -> SeasonalWind.resize(size.width, size.height) }
        .nestedScroll(SeasonalScrollConnection)
        .pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            awaitPointerEventScope {
                var trackedId: PointerId? = null
                var down = Offset.Zero
                var last = Offset.Zero
                var downTime = 0L
                var travelled = 0f
                while (true) {
                    // Initial, not Main: this is the pass before children handle
                    // the event, so a tap that a button consumes a moment later is
                    // still seen here. Nothing is consumed in turn.
                    //
                    // No `requireUnconsumed` here: `awaitPointerEvent` does not
                    // take one (that parameter belongs to `awaitFirstDown`), and it
                    // would be redundant in the Initial pass anyway - nothing has
                    // been consumed yet when this reader sees the event.
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    for (change in event.changes) {
                        val tracking = trackedId
                        if (tracking == null) {
                            if (change.changedToDownIgnoreConsumed()) {
                                trackedId = change.id
                                down = change.position
                                last = change.position
                                downTime = change.uptimeMillis
                                travelled = 0f
                                SeasonalWind.fingerDown()
                            }
                        } else if (change.id == tracking) {
                            if (change.pressed) {
                                val delta = change.position - last
                                last = change.position
                                travelled = max(
                                    travelled,
                                    (change.position - down).getDistance()
                                )
                                // Past slop this is a drag, not a tap. Feed it
                                // as wind so pages whose scroll never reaches
                                // the nested-scroll connection still lean.
                                if (travelled > slop && delta != Offset.Zero) {
                                    SeasonalWind.scroll(delta, fromFinger = true)
                                }
                            }
                            val lifted = change.changedToUpIgnoreConsumed()
                            // `!pressed` also covers a cancelled pointer, which
                            // never reports an up. Leaving the id set would make
                            // one aborted gesture swallow every tap after it.
                            if (lifted || !change.pressed) {
                                val quick = change.uptimeMillis - downTime <= TAP_TIMEOUT_MILLIS
                                if (lifted && travelled <= slop && quick) {
                                    SeasonalWind.tapAt(change.position)
                                }
                                SeasonalWind.fingerUp()
                                trackedId = null
                            }
                        }
                    }
                }
            }
        }
}

/**
 * The scroll tap that opens the layer's window.
 *
 * `onPostScroll` is the only overload used: by the time it fires, the child has
 * already applied the delta, so this sees the travel that actually happened and
 * cannot double-count the overscroll a fling reports and then gives back.
 */
private object SeasonalScrollConnection : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        SeasonalWind.scroll(consumed)
        return Offset.Zero
    }
}

/** Long enough for a deliberate press, short enough that a hold is not a tap. */
private const val TAP_TIMEOUT_MILLIS = 500L
