package com.anisequel.ui.components

import com.anisequel.domain.jokes.JokeMoment
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * "Something happened that the reader said may be worth a line."
 *
 * The timer is scheduled by the host because it belongs to the toast. The other
 * three moments belong to screens: only the dashboard knows a refresh was asked
 * for, and only the navigation graph knows a tab changed. Rather than thread a
 * callback through four screens into one composable, they announce it here and
 * the single host - which already outlives all of them - decides whether the
 * reader asked for this moment and whether a line is due.
 *
 * A `SharedFlow` with one slot rather than a counter: the moment *is* the event,
 * and an announcement that arrives while a joke is on screen should be dropped
 * rather than queued to explode the instant the pill leaves. Losing one tab
 * change is the intended behaviour, not a missed message.
 */
object JokeMomentBus {
    private val _moments = MutableSharedFlow<JokeMoment>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val moments: SharedFlow<JokeMoment> = _moments.asSharedFlow()

    fun announce(moment: JokeMoment) {
        _moments.tryEmit(moment)
    }
}
