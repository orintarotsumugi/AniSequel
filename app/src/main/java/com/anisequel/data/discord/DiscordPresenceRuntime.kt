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
 */
sealed interface DiscordRuntimeState {
    /** The reader has not enabled the feature. */
    data object Disabled : DiscordRuntimeState

    /** Enabled and composed locally, but no Discord account is linked. */
    data object PreviewOnly : DiscordRuntimeState

    /** Connecting or refreshing the live presence. */
    data object Connecting : DiscordRuntimeState

    /** A token is linked and the gateway session is live. */
    data class Live(val account: String) : DiscordRuntimeState

    /**
     * Something failed.
     *
     * The composed presence is still drawn locally and [message] says why it
     * did not reach Discord.
     */
    data class Failed(val message: String) : DiscordRuntimeState
}

/** The process-wide presence status. */
object DiscordPresenceRuntime {
    private val _state = MutableStateFlow<DiscordRuntimeState>(DiscordRuntimeState.Disabled)
    val state: StateFlow<DiscordRuntimeState> = _state.asStateFlow()

    /** The last value the host composed. */
    private val _presence = MutableStateFlow<DiscordPresence?>(null)
    val presence: StateFlow<DiscordPresence?> = _presence.asStateFlow()

    fun update(next: DiscordRuntimeState) {
        _state.value = next
    }

    fun updatePresence(next: DiscordPresence) {
        _presence.value = next
    }
}

/** Quick actions that must reach the app-root host. */
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
