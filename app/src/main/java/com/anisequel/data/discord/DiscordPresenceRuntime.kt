package com.anisequel.data.discord

import com.anisequel.domain.discord.DiscordPresence
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the presence host is doing, stated once for both readers.
 *
 * The host is mounted at the navigation root; the page that turns the feature on
 * lives in Settings. Without a shared answer the page would have to guess whether
 * "Enabled" means "on" or "actually reaching Discord", and the difference is the
 * whole reason this feature has a connection card.
 */
sealed interface DiscordRuntimeState {
    /** The reader has not enabled the feature. */
    data object Disabled : DiscordRuntimeState

    /** Enabled and composed locally, but no relay has been linked. */
    data object PreviewOnly : DiscordRuntimeState

    data object Publishing : DiscordRuntimeState

    /** The relay accepted the last presence. */
    data class Live(val relay: String) : DiscordRuntimeState

    /**
     * Something failed.
     *
     * The composed presence is still drawn locally - the reader can see exactly
     * what Discord would have shown - and [message] says why it did not arrive.
     */
    data class Failed(val message: String) : DiscordRuntimeState
}

/**
 * The process-wide presence status.
 *
 * Deliberately not a ViewModel: the host outlives every Settings page and every
 * tab, and the alternative is passing a status through four navigation graphs.
 * The same pattern as `JokeMomentBus`, for the same reason.
 */
object DiscordPresenceRuntime {
    private val _state = MutableStateFlow<DiscordRuntimeState>(DiscordRuntimeState.Disabled)
    val state: StateFlow<DiscordRuntimeState> = _state.asStateFlow()

    /**
     * The last value the host composed.
     *
     * The Settings preview reads it for the joke it is currently quoting: the
     * host owns the rotating line, and a second draw on the page would show a
     * sentence that never reaches Discord.
     */
    private val _presence = MutableStateFlow<DiscordPresence?>(null)
    val presence: StateFlow<DiscordPresence?> = _presence.asStateFlow()

    fun update(next: DiscordRuntimeState) {
        _state.value = next
    }

    fun updatePresence(next: DiscordPresence) {
        _presence.value = next
    }
}

/**
 * The two quick actions that have to reach the app-root host.
 *
 * "New joke" is the only thing the Settings page can ask for that the host owns:
 * the joke currently published is state inside the host, and a second source of
 * truth on the page would let the preview quote a line Discord never received.
 * A shared flow rather than a preference write, because this is an event, not a
 * choice the reader wants remembered across launches.
 */
object DiscordPresenceCommands {
    private val _newJoke = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val newJokeRequested: SharedFlow<Unit> = _newJoke.asSharedFlow()

    fun requestNewJoke() {
        _newJoke.tryEmit(Unit)
    }
}
