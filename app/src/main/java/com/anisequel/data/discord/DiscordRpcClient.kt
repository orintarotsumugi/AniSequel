package com.anisequel.data.discord

import com.anisequel.domain.discord.DiscordPresence
import com.anisequel.domain.discord.DiscordRelay
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The relay's health answer. Only two fields matter, and both are optional. */
@JsonClass(generateAdapter = true)
internal data class DiscordRelayHealth(
    val ok: Boolean = false,
    @Json(name = "user") val user: String? = null
)

/**
 * Talks to the reader's Discord relay.
 *
 * ## Why a relay at all
 *
 * Discord's Rich Presence RPC is a local IPC transport, and on Android the only
 * supported client is the native **Discord Social SDK** (C++/Unity/Unreal, 1.10+).
 * There is no Kotlin/Java artifact and no public Maven dependency, so a Compose
 * app cannot publish presence by itself without shipping that native SDK and a
 * Discord application id. The honest options were: a non-functional toggle, or a
 * small documented relay that holds the Discord credentials and exposes two
 * endpoints. This is the second one. The app sends only what the preview card
 * shows, and only when the reader links a relay.
 *
 * ## The protocol
 *
 *  - `GET  {base}/v1/health`   -> `{"ok":true,"user":"name"}`
 *  - `POST {base}/v1/presence` -> [DiscordPresenceJson.envelope]
 *  - `DELETE {base}/v1/presence` -> clears the activity
 *
 * Every call is short and fails closed: a relay that cannot be reached produces a
 * visible error and the presence host stops pretending it is live. There is no
 * silent retry loop burning the reader's battery, and no token in this app.
 */
class DiscordRpcClient(
    private val http: OkHttpClient = OkHttpClient()
) {
    private val jsonMediaType = "application/json".toMediaType()
    private val healthAdapter = Moshi.Builder()
        .build()
        .adapter(DiscordRelayHealth::class.java)

    /**
     * Proves the relay is reachable and returns the Discord name it reports.
     *
     * Linking on a saved URL alone would let the Settings page say "Connected"
     * about a typo, and the first real publish would fail minutes later with the
     * reader already gone.
     */
    suspend fun probe(rawBaseUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = DiscordRelay.normalizeBaseUrl(rawBaseUrl)
                ?: throw IOException("Use an https relay URL, for example https://relay.example")
            val request = Request.Builder()
                .url(DiscordRelay.healthUrl(baseUrl))
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("The relay answered ${response.code}.")
                }
                val body = response.body?.string().orEmpty()
                val health = healthAdapter.fromJson(body)
                    ?: throw IOException("The relay did not answer with JSON.")
                if (!health.ok) {
                    throw IOException("The relay refused the check.")
                }
                health.user?.takeIf { it.isNotBlank() } ?: "Discord"
            }
        }
    }

    suspend fun publish(
        rawBaseUrl: String,
        applicationId: String,
        presence: DiscordPresence
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = DiscordRelay.normalizeBaseUrl(rawBaseUrl)
                ?: throw IOException("Use an https relay URL.")
            val body = DiscordPresenceJson.envelope(applicationId, presence)
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(DiscordRelay.presenceUrl(baseUrl))
                .post(body)
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("The relay refused the presence (${response.code}).")
                }
            }
        }
    }

    /** Clears the activity when the reader switches the feature off. */
    suspend fun clear(rawBaseUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = DiscordRelay.normalizeBaseUrl(rawBaseUrl) ?: return@runCatching
            val request = Request.Builder()
                .url(DiscordRelay.presenceUrl(baseUrl))
                .delete()
                .build()
            http.newCall(request).execute().use { response ->
                // 404 is an answer, not a failure: the relay has no activity to
                // clear. Anything else is worth surfacing, because a presence that
                // could not be withdrawn is the one failure this button must report.
                if (!response.isSuccessful && response.code != 404) {
                    throw IOException("The relay could not clear the presence (${response.code}).")
                }
            }
        }
    }
}
