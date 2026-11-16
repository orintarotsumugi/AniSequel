package com.anisequel.data.discord

import com.anisequel.domain.discord.DiscordOAuth
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sign-in that is in flight, and the callback that answers it.
 *
 * ## Why this is a process-wide object
 *
 * The reader leaves the app to authorize in the browser, and Discord sends them
 * back through a deep link that [com.anisequel.MainActivity] receives. At that
 * moment nothing in the composition can be trusted to still be the thing that
 * started the flow: the dialog may have been dismissed, the page may have been
 * left, and on a low-memory device the Activity may have been rebuilt around a
 * fresh composition. The verifier - which never leaves this device and is the
 * whole security argument for PKCE - therefore has to live somewhere that
 * outlives a composable, and the callback has to be handed over rather than
 * looked for.
 *
 * ## What it deliberately does not survive
 *
 * A process death. A pending session is one navigation away from being pointless
 * once the app has restarted, and pretending otherwise would mean writing a PKCE
 * verifier to disk for no benefit. A callback that arrives with no session is
 * reported as [Outcome.Stale] and the page tells the reader to start again, which
 * costs one tap and says what happened.
 */
object DiscordOAuthFlow {

    /** One authorize request: the ID it was made for, and the PKCE pair behind it. */
    data class Session(
        val clientId: String,
        val verifier: String,
        val challenge: String,
        val state: String
    )

    /** What a callback resolves to, once there is something to resolve it against. */
    sealed interface Outcome {
        /** The redirect belongs to the session in [session]. */
        data class Ready(val session: Session, val redirect: DiscordOAuth.Redirect) : Outcome

        /**
         * A callback with nothing on this device that could have started it.
         *
         * Either the app was restarted while the browser was open, or another app
         * opened this scheme. Neither is a security problem - no code is exchanged
         * without a verifier - but both need a sentence rather than silence.
         */
        data class Stale(val message: String) : Outcome
    }

    private val random = SecureRandom()

    private val _pending = MutableStateFlow<Session?>(null)

    /** The flow the reader is expected to finish, for a page that wants to say so. */
    val pending: StateFlow<Session?> = _pending.asStateFlow()

    private val _outcome = MutableStateFlow<Outcome?>(null)

    /** The callback waiting to be handled, or the reason one could not be. */
    val outcome: StateFlow<Outcome?> = _outcome.asStateFlow()

    /**
     * Starts a flow for [clientId] and returns what the authorize URL needs.
     *
     * Called when the reader taps the sign-in button, before the browser opens, so
     * the verifier exists before a code can possibly come back.
     */
    fun begin(clientId: String): Session {
        val verifier = DiscordOAuth.codeVerifier { size -> randomBytes(size) }
        val session = Session(
            clientId = clientId.trim(),
            verifier = verifier,
            challenge = DiscordOAuth.codeChallengeS256(verifier),
            state = DiscordOAuth.state { size -> randomBytes(size) }
        )
        _pending.value = session
        _outcome.value = null
        return session
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    /**
     * The deep link, from whichever entry point received it.
     *
     * Ignores a URI that is not this app's Discord callback, so a redirected
     * `anisequel://oauth` from the AniList sign-in cannot clear this flow.
     */
    fun deliver(uri: String?) {
        val redirect = DiscordOAuth.parseRedirect(uri)
        if (redirect is DiscordOAuth.Redirect.Ignored) return
        val session = _pending.value
        if (session == null) {
            _outcome.value = Outcome.Stale(
                "That Discord sign-in was not started here, so it cannot be finished. " +
                    "Start it again from this page."
            )
            return
        }
        // The state is the only thing that ties a code to *this* request. Discord
        // echoes it back; a mismatch means the code was minted for another flow.
        if (redirect is DiscordOAuth.Redirect.Code && redirect.state != session.state) {
            _pending.value = null
            _outcome.value = Outcome.Stale(
                "Discord sent back a sign-in this app did not start. Nothing was linked."
            )
            return
        }
        _pending.value = null
        _outcome.value = Outcome.Ready(session, redirect)
    }

    /** Clears the outcome once the page has acted on it. */
    fun consume() {
        _outcome.value = null
    }

    /** Forgets a flow the reader abandoned, so a stale verifier is not left behind. */
    fun cancel() {
        _pending.value = null
        _outcome.value = null
    }

    /** True when a picker or dialog should say a sign-in is waiting on the browser. */
    fun isWaiting(): Boolean = _pending.value != null || _outcome.value != null
}
