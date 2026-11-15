package com.anisequel.ui.components.discord

import com.anisequel.domain.discord.DiscordActivity
import com.anisequel.domain.discord.DiscordActivityKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place that knows what the app is showing.
 *
 * ## Why a bus and not a parameter
 *
 * The presence is assembled at the navigation root and the thing it describes is
 * a destination four graphs down. Threading "what is open" through every route
 * would make every screen a participant in a Discord feature it has no other
 * reason to know about. The two writes that exist - the route observer and the
 * detail sheet - announce the fact instead.
 *
 * ## Why the clock lives here
 *
 * Discord's timer must restart when the activity changes. Stamping the start time
 * in the setter means the composer can stay a pure function of its inputs, and a
 * recomposition that re-reads the same activity does not reset the timer.
 *
 * Only *changes* are published. A detail sheet recomposing on every scroll would
 * otherwise send a new presence once a frame.
 */
object DiscordActivityBus {

    private val _activity = MutableStateFlow(
        DiscordActivity(
            kind = DiscordActivityKind.BROWSING,
            status = "Home",
            startedAtMillis = System.currentTimeMillis()
        )
    )

    val activity: StateFlow<DiscordActivity> = _activity.asStateFlow()

    /** The last page-level status, so closing a sheet returns to the right page. */
    @Volatile
    private var pageStatus: String = "Home"

    /** A tab or page change. Titles are cleared: the page is no longer open. */
    @Synchronized
    fun browse(status: String) {
        pageStatus = status
        publish(
            DiscordActivity(
                kind = DiscordActivityKind.BROWSING,
                status = status
            )
        )
    }

    /**
     * A title was opened.
     *
     * [kind] distinguishes the four detail shapes ("anime details", "manga
     * details", "novel details") because the reader chose them in the phrasing
     * editor; a single "details" kind would make three rows of that editor dead.
     */
    @Synchronized
    fun focus(
        kind: DiscordActivityKind,
        title: String?,
        coverUrl: String?,
        pageUrl: String?,
        progress: String? = null
    ) {
        publish(
            DiscordActivity(
                kind = kind,
                title = title,
                coverUrl = coverUrl,
                pageUrl = pageUrl,
                progress = progress,
                status = pageStatus
            )
        )
    }

    /** The sheet closed. Back to the page underneath it. */
    @Synchronized
    fun clearFocus() {
        publish(
            DiscordActivity(
                kind = DiscordActivityKind.BROWSING,
                status = pageStatus
            )
        )
    }

    private fun publish(next: DiscordActivity) {
        val current = _activity.value
        val same = current.kind == next.kind &&
            current.title == next.title &&
            current.status == next.status &&
            current.progress == next.progress &&
            current.coverUrl == next.coverUrl &&
            current.pageUrl == next.pageUrl
        if (same) return
        _activity.value = next.copy(startedAtMillis = System.currentTimeMillis())
    }
}
