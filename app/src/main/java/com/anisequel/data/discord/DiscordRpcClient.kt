package com.anisequel.data.discord

import com.anisequel.domain.discord.DiscordActivityType
import com.anisequel.domain.discord.DiscordAuthKind
import com.anisequel.domain.discord.DiscordLogin
import com.anisequel.domain.discord.DiscordOAuth
import com.anisequel.domain.discord.DiscordOnlineStatus
import com.anisequel.domain.discord.DiscordPresence
import com.squareup.moshi.Moshi
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Direct Discord Rich Presence client.
 *
 * Connects to Discord's gateway with the reader's own token and sends
 * presence updates from this device. No relay, no local build step, no
 * third-party middle service.
 */
class DiscordRpcClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        /**
         * The one client for the process.
         *
         * There used to be one per owner: the app-root publisher held its own, and
         * the Settings page built another in a `remember` for the login check. Two
         * things followed from that, both wrong.
         *
         *  - **Signing out from Settings disconnected the wrong client.** The page
         *    called `disconnect()` on the instance it had just constructed, so the
         *    socket that was actually publishing - the publisher's - stayed open
         *    until the preference flow happened to reach it. The call that looked
         *    like the fix was a no-op on the instance that mattered.
         *  - **Every visit to the page bought a client nobody closed.** Each one
         *    carries an `OkHttpClient` connection pool and its own supervision scope,
         *    and `remember` disposes neither. The leak is small per visit and grows
         *    with them.
         *
         * One instance also means one gateway socket by construction: two clients
         * publishing could open two sessions for one account, which Discord answers
         * by closing one of them at random.
         */
        val shared: DiscordRpcClient by lazy { DiscordRpcClient() }
    }

    private val moshi = Moshi.Builder().build()
    private val userAdapter = moshi.adapter(DiscordUser::class.java)
    private val envelopeAdapter = moshi.adapter(GatewayEnvelope::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var heartbeatJob: Job? = null
    @Volatile private var activeToken: String? = null
    @Volatile private var ready: GatewayReady? = null
    @Volatile private var connected: Boolean = false
    private val lastSequence = AtomicLong(-1L)
    private val identified = AtomicBoolean(false)

    /**
     * Bumped whenever a disconnect is requested.
     *
     * A connect runs while holding [mutex], but [disconnect] must not wait for
     * that lock: a slow gateway is allowed to spend fifteen seconds before READY,
     * and the reader who just signed out should not. The epoch is how the two meet
     * without the lock - a connect that started before the bump notices the change
     * and abandons its socket instead of publishing the session the reader closed.
     */
    private val sessionEpoch = AtomicLong(0L)

    // Written from OkHttp's listener thread and read by the coroutine waiting for
    // READY; without `@Volatile` the waiter can keep seeing `null` (and a late
    // READY can be dropped) because nothing orders the two threads' caches.
    @Volatile private var readyDeferred: CompletableDeferred<Result<GatewayReady>>? = null

    /** Verifies a token against Discord's HTTP API and returns the account. */
    suspend fun verify(rawToken: String, bearer: Boolean = false): Result<DiscordAccount> = withContext(Dispatchers.IO) {
        runCatching {
            val token = credential(rawToken, bearer)
            if (!DiscordLogin.looksLikeToken(token)) {
                throw IOException("That doesn't look like a Discord token.")
            }
            val request = Request.Builder()
                .url("https://discord.com/api/v9/users/@me")
                .get()
                .addHeader("Authorization", token)
                .addHeader("Accept", "application/json")
                .build()
            // The presence client is built for a websocket: `readTimeout(0)` means
            // *no* read timeout, which is right for a gateway socket and wrong for
            // a one-shot HTTP call - a stalled login check would hang the dialog
            // forever. Same pool and dispatcher, finite timeouts.
            val httpClient = http.newBuilder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    throw IOException(
                        when (code) {
                            401 -> "Discord rejected this token. Sign in again or paste a fresh token."
                            403 -> "Discord blocked this request. Check the account and try again."
                            429 -> "Discord rate limited the login check. Wait a moment and retry."
                            else -> "Discord could not verify the account ($code)."
                        }
                    )
                }
                val body = response.body?.string().orEmpty()
                val user = userAdapter.fromJson(body) ?: throw IOException("Discord returned an unreadable account.")
                DiscordAccount.from(user)
            }
        }
    }

    suspend fun publish(
        rawToken: String,
        presence: DiscordPresence,
        status: DiscordOnlineStatus,
        activityType: DiscordActivityType,
        bearer: Boolean = false
    ): Result<DiscordAccount> = mutex.withLock {
        val token = credential(rawToken, bearer)
        if (!DiscordLogin.looksLikeToken(token)) {
            return Result.failure(IOException("That doesn't look like a Discord token."))
        }
        ensureConnected(token).mapCatching { account ->
            sendPresence(presence, status, activityType)
            account
        }
    }

    /**
     * The token as Discord has to receive it.
     *
     * A pasted user token is sent bare. One minted by this app's own OAuth grant
     * is sent with a `Bearer ` prefix, in the header *and* in the gateway's
     * IDENTIFY - Discord refuses an OAuth user token on the gateway without it,
     * with `4004 authentication failed` and no hint about which half was wrong.
     * [rawToken] is normalised first so a stored value that somehow kept the
     * prefix does not end up with two.
     */
    private fun credential(rawToken: String, bearer: Boolean): String = DiscordOAuth.credential(
        token = rawToken,
        kind = if (bearer) DiscordAuthKind.OAUTH else DiscordAuthKind.TOKEN
    )

    /**
     * Hides the presence, if the socket is free to say so right now.
     *
     * Deliberately non-blocking. Every caller clears and then disconnects, and the
     * disconnect is what actually ends the session, so waiting here for a publish
     * that may still be inside its fifteen-second READY wait bought nothing and
     * made Log out sit on "Connecting…" for the rest of that timeout. The lock is
     * only held long enough to send one frame when nobody else is using it.
     */
    suspend fun clear(rawToken: String, bearer: Boolean = false): Result<Unit> {
        val token = credential(rawToken, bearer)
        if (token.isBlank()) return Result.success(Unit)
        if (!mutex.tryLock()) return Result.success(Unit)
        return try {
            runCatching {
                if (connected && activeToken == token && socket != null) {
                    val payload = DiscordPresenceJson.gatewayPresenceUpdate(
                        presence = null,
                        status = DiscordOnlineStatus.ONLINE,
                        activityType = DiscordActivityType.PLAYING
                    )
                    socket?.send(payload)
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Ends the session immediately, even while a connect is still waiting.
     *
     * This used to take [mutex], so a sign-out during a slow gateway queued behind
     * the very `publish` whose pending READY it was supposed to cancel: the
     * teardown that completes the READY deferred could not run until the deferred
     * had already timed out, which is the opposite of what Log out is for. Bumping
     * the epoch and tearing down outside the lock lets the waiter fail at once and
     * release the lock, and [ensureConnected] checks the epoch so a socket opened in
     * that window is closed rather than left publishing for a signed-out account.
     */
    suspend fun disconnect() {
        sessionEpoch.incrementAndGet()
        teardown(closeCode = 1000, reason = "client disconnect")
    }

    private suspend fun ensureConnected(token: String): Result<DiscordAccount> {
        val existingReady = ready
        if (connected && activeToken == token && existingReady != null && socket != null) {
            return Result.success(existingReady.toAccount())
        }
        teardown(closeCode = 4000, reason = "reconnect")
        val epoch = sessionEpoch.get()
        val deferred = CompletableDeferred<Result<GatewayReady>>()
        readyDeferred = deferred
        activeToken = token
        identified.set(false)
        lastSequence.set(-1L)

        val request = Request.Builder()
            .url("wss://gateway.discord.gg/?v=9&encoding=json")
            .build()
        socket = http.newWebSocket(request, GatewayListener(token, epoch))

        // A disconnect can land between the epoch read above and this assignment;
        // its teardown then saw no deferred to complete. Check once the deferred and
        // socket exist, so that case fails now instead of waiting out the timeout.
        if (epoch != sessionEpoch.get()) {
            readyDeferred = null
            teardown(closeCode = 4000, reason = "disconnected while connecting")
            return Result.failure(IOException("The Discord connection was closed."))
        }

        val result = withTimeoutOrNull(15_000L) { deferred.await() }
        if (result == null) {
            // The socket is still trying to connect at this point. Leaving it
            // open meant a late READY could flip `connected` back on behind a UI
            // that had already reported a timeout, so the next publish silently
            // reused a session the reader was told had failed.
            readyDeferred = null
            teardown(closeCode = 4000, reason = "connect timeout")
            return Result.failure(
                IOException("Discord did not answer in time. Check your connection and token; presence needs the gateway.connect scope, which requires Discord approval.")
            )
        }
        return result.map { it.toAccount() }
    }

    private fun sendPresence(
        presence: DiscordPresence,
        status: DiscordOnlineStatus,
        activityType: DiscordActivityType
    ) {
        val payload = DiscordPresenceJson.gatewayPresenceUpdate(presence, status, activityType)
        val sent = socket?.send(payload) == true
        if (!sent) throw IOException("The Discord connection was lost while sending your presence.")
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            val jitter = (intervalMs * 0.1).toLong().coerceAtLeast(250L)
            delay(jitter.coerceAtMost(intervalMs))
            while (true) {
                val seq = lastSequence.get().takeIf { it >= 0 }
                val payload = if (seq == null) "{\"op\":1,\"d\":null}" else "{\"op\":1,\"d\":$seq}"
                socket?.send(payload)
                delay(intervalMs)
            }
        }
    }

    private fun teardown(closeCode: Int, reason: String) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        connected = false
        identified.set(false)
        ready = null
        // The account this session belonged to goes with the socket. Leaving it
        // behind meant a signed-out token kept sitting in the shared client's
        // memory, and the next connect could compare against a token nobody was
        // using any more. The sequence number belongs to the gateway session too;
        // carrying it into a new one asks Discord to resume a session it closed.
        activeToken = null
        lastSequence.set(-1L)
        // A publish waiting on READY has to be released when the socket goes
        // away, instead of being left to its own 15-second timeout. Signing out
        // during a slow connect used to leave the Settings page saying
        // "Connecting…" for the full wait, and the one thing that would have
        // cancelled it was the token change the reader had just made.
        //
        // `complete` on an already-completed or absent deferred is a no-op or a
        // null-check away, so the failure paths that complete it first are safe.
        readyDeferred?.complete(Result.failure(IOException("The Discord connection closed.")))
        readyDeferred = null
        val ws = socket
        socket = null
        runCatching { ws?.close(closeCode, reason) }
        runCatching { ws?.cancel() }
    }

    /**
     * Whether a callback still belongs to the session that created it.
     *
     * A reconnect disposes the old socket only after the new one is in place, and
     * OkHttp delivers the old listener's `onFailure` on its own thread. Without
     * this, a socket that failed a second late tore down its replacement - the
     * reconnect succeeded, then the old failure closed the live session, and the
     * next publish started over. The epoch closes the same door for a callback
     * racing a sign-out.
     */
    private fun isCurrent(webSocket: WebSocket, epoch: Long): Boolean =
        epoch == sessionEpoch.get() && webSocket === socket

    private inner class GatewayListener(
        private val token: String,
        private val epoch: Long
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // wait for HELLO
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent(webSocket, epoch)) return
            val envelope = runCatching { envelopeAdapter.fromJson(text) }.getOrNull() ?: return
            envelope.s?.let { lastSequence.set(it) }
            when (envelope.op) {
                10 -> { // HELLO
                    val hello = runCatching {
                        // d is nested JSON; easiest to parse by extracting field
                        Regex("\"heartbeat_interval\"\\s*:\\s*(\\d+)").find(text)
                            ?.groupValues?.getOrNull(1)?.toLongOrNull()
                    }.getOrNull() ?: 41_250L
                    startHeartbeat(hello)
                    if (identified.compareAndSet(false, true)) {
                        val identify = "{" +
                            "\"op\":2,\"d\":{" +
                            "\"token\":${jsonString(token)}," +
                            "\"properties\":{" +
                            "\"os\":\"Android\"," +
                            "\"browser\":\"AniSequel\"," +
                            "\"device\":\"AniSequel\"" +
                            "}," +
                            "\"presence\":{\"status\":\"online\",\"since\":0,\"activities\":[],\"afk\":false}," +
                            "\"compress\":false" +
                            "}}"
                        webSocket.send(identify)
                    }
                }
                0 -> { // DISPATCH
                    if (envelope.t == "READY") {
                        val user = extractReadyUser(text)
                        if (!isCurrent(webSocket, epoch)) return
                        val readyEvent = GatewayReady(user = user)
                        ready = readyEvent
                        connected = true
                        readyDeferred?.complete(Result.success(readyEvent))
                        readyDeferred = null
                    }
                }
                1 -> { // HEARTBEAT request
                    val seq = lastSequence.get().takeIf { it >= 0 }
                    val payload = if (seq == null) "{\"op\":1,\"d\":null}" else "{\"op\":1,\"d\":$seq}"
                    webSocket.send(payload)
                }
                7, 9 -> { // RECONNECT / INVALID_SESSION
                    connected = false
                    readyDeferred?.complete(Result.failure(IOException("Discord asked to reconnect. Try enabling Rich Presence again.")))
                    readyDeferred = null
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent(webSocket, epoch)) return
            connected = false
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent(webSocket, epoch)) return
            connected = false
            if (ready == null) {
                readyDeferred?.complete(Result.failure(IOException(closeReason(code, reason))))
                readyDeferred = null
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrent(webSocket, epoch)) return
            connected = false
            val message = response?.code?.let { "Discord connection failed ($it)." }
                ?: t.message?.takeIf { it.isNotBlank() }
                ?: "Discord connection failed."
            readyDeferred?.complete(Result.failure(IOException(message)))
            readyDeferred = null
            teardown(closeCode = 4000, reason = "failure")
        }
    }


    private fun extractReadyUser(text: String): DiscordUser? {
        val userJson = extractJsonObjectAfter(text, "\"user\"") ?: return null
        return runCatching { userAdapter.fromJson(userJson) }.getOrNull()
    }

    private fun extractJsonObjectAfter(text: String, key: String): String? {
        val keyIndex = text.indexOf(key)
        if (keyIndex < 0) return null
        val colon = text.indexOf(':', keyIndex + key.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '{') return null
        var depth = 0
        var inString = false
        var escape = false
        val start = i
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else when (c) {
                    '\\' -> escape = true
                    '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }

    private fun closeReason(code: Int, reason: String): String = when (code) {
        4004 -> "Discord rejected this token. Presence needs the gateway.connect scope, which requires Discord approval; sign in again once it is granted, or paste a fresh token."
        4014 -> "Discord does not allow this connection for your account."
        else -> if (reason.isNotBlank()) "Discord closed the connection: $reason" else "Discord closed the connection ($code)."
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') {
                    append("\\u")
                    append(c.code.toString(16).padStart(4, '0'))
                } else append(c)
            }
        }
        append('"')
    }
}

data class DiscordAccount(
    val username: String,
    val userId: String = "",
    val avatarUrl: String = ""
) {
    companion object {
        /**
         * One mapping from a Discord user to the account the app remembers.
         *
         * The name, the ID and the avatar URL used to be assembled in three places
         * - the HTTP check, the gateway's READY and the gateway's reconnect - and
         * the avatar arithmetic had already drifted between them. Whatever answers
         * the account now goes through here.
         */
        internal fun from(user: DiscordUser?): DiscordAccount {
            val name = user?.globalName?.takeIf { it.isNotBlank() }
                ?: user?.username?.takeIf { it.isNotBlank() }
                ?: "Discord"
            val id = user?.id.orEmpty()
            val hash = user?.avatar
            val avatar = if (id.isNotEmpty() && !hash.isNullOrBlank()) {
                val ext = if (hash.startsWith("a_")) "gif" else "png"
                "https://cdn.discordapp.com/avatars/$id/$hash.$ext?size=128"
            } else {
                ""
            }
            return DiscordAccount(username = name, userId = id, avatarUrl = avatar)
        }
    }
}

@JsonClass(generateAdapter = true)
internal data class DiscordUser(
    val id: String? = null,
    val username: String? = null,
    @Json(name = "global_name") val globalName: String? = null,
    val avatar: String? = null
)

@JsonClass(generateAdapter = true)
internal data class GatewayEnvelope(
    val op: Int = -1,
    val s: Long? = null,
    val t: String? = null
)

@JsonClass(generateAdapter = true)
internal data class GatewayHello(
    @Json(name = "heartbeat_interval") val heartbeatInterval: Long = 41_250L
)

internal data class GatewayReady(
    val user: DiscordUser? = null
) {
    fun toAccount(): DiscordAccount = DiscordAccount.from(user)
}
