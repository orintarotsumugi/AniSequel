package com.anisequel.data.discord

import com.anisequel.domain.discord.DiscordActivityType
import com.anisequel.domain.discord.DiscordLogin
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
    private var readyDeferred: CompletableDeferred<Result<GatewayReady>>? = null

    /** Verifies a token against Discord's HTTP API and returns the account. */
    suspend fun verify(rawToken: String): Result<DiscordAccount> = withContext(Dispatchers.IO) {
        runCatching {
            val token = DiscordLogin.normalizeToken(rawToken)
            if (!DiscordLogin.looksLikeToken(token)) {
                throw IOException("That doesn't look like a Discord token.")
            }
            val request = Request.Builder()
                .url("https://discord.com/api/v9/users/@me")
                .get()
                .addHeader("Authorization", token)
                .addHeader("Accept", "application/json")
                .build()
            http.newCall(request).execute().use { response ->
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
                DiscordAccount(
                    username = user.globalName?.takeIf { it.isNotBlank() }
                        ?: user.username?.takeIf { it.isNotBlank() }
                        ?: "Discord",
                    userId = user.id.orEmpty(),
                    avatarUrl = avatarUrl(user)
                )
            }
        }
    }

    suspend fun publish(
        rawToken: String,
        presence: DiscordPresence,
        status: DiscordOnlineStatus,
        activityType: DiscordActivityType
    ): Result<DiscordAccount> = mutex.withLock {
        val token = DiscordLogin.normalizeToken(rawToken)
        if (!DiscordLogin.looksLikeToken(token)) {
            return Result.failure(IOException("That doesn't look like a Discord token."))
        }
        ensureConnected(token).mapCatching { account ->
            sendPresence(presence, status, activityType)
            account
        }
    }

    suspend fun clear(rawToken: String): Result<Unit> = mutex.withLock {
        runCatching {
            val token = DiscordLogin.normalizeToken(rawToken)
            if (token.isBlank()) return@runCatching
            if (connected && activeToken == token && socket != null) {
                val payload = DiscordPresenceJson.gatewayPresenceUpdate(
                    presence = null,
                    status = DiscordOnlineStatus.ONLINE,
                    activityType = DiscordActivityType.PLAYING
                )
                socket?.send(payload)
            }
        }
    }

    suspend fun disconnect() = mutex.withLock {
        teardown(closeCode = 1000, reason = "client disconnect")
    }

    private suspend fun ensureConnected(token: String): Result<DiscordAccount> {
        val existingReady = ready
        if (connected && activeToken == token && existingReady != null && socket != null) {
            return Result.success(existingReady.toAccount())
        }
        teardown(closeCode = 4000, reason = "reconnect")
        val deferred = CompletableDeferred<Result<GatewayReady>>()
        readyDeferred = deferred
        activeToken = token
        identified.set(false)
        lastSequence.set(-1L)

        val request = Request.Builder()
            .url("wss://gateway.discord.gg/?v=9&encoding=json")
            .build()
        socket = http.newWebSocket(request, GatewayListener(token))

        val result = withTimeoutOrNull(15_000L) { deferred.await() }
            ?: Result.failure(IOException("Discord did not answer in time. Check your connection and token."))
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
        val ws = socket
        socket = null
        runCatching { ws?.close(closeCode, reason) }
        runCatching { ws?.cancel() }
    }

    private inner class GatewayListener(private val token: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // wait for HELLO
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
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
                            "\"capabilities\":65," +
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
            connected = false
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected = false
            if (ready == null) {
                readyDeferred?.complete(Result.failure(IOException(closeReason(code, reason))))
                readyDeferred = null
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
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
        4004 -> "Discord rejected this token. Sign in again or paste a fresh token."
        4014 -> "Discord does not allow this connection for your account."
        else -> if (reason.isNotBlank()) "Discord closed the connection: $reason" else "Discord closed the connection ($code)."
    }

    private fun avatarUrl(user: DiscordUser): String {
        val id = user.id ?: return ""
        val hash = user.avatar ?: return ""
        val ext = if (hash.startsWith("a_")) "gif" else "png"
        return "https://cdn.discordapp.com/avatars/$id/$hash.$ext?size=128"
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
)

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
    fun toAccount(): DiscordAccount = DiscordAccount(
        username = user?.globalName?.takeIf { it.isNotBlank() }
            ?: user?.username?.takeIf { it.isNotBlank() }
            ?: "Discord",
        userId = user?.id.orEmpty(),
        avatarUrl = if (user?.id != null && user.avatar != null) {
            val ext = if (user.avatar.startsWith("a_")) "gif" else "png"
            "https://cdn.discordapp.com/avatars/${user.id}/${user.avatar}.$ext?size=128"
        } else ""
    )
}
