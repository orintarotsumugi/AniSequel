package com.anisequel.data.discord

import com.anisequel.domain.discord.DiscordAuthKind
import com.anisequel.domain.discord.DiscordOAuth
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The two HTTP calls of the Discord sign-in.
 *
 * Separate from [DiscordRpcClient] because they are a different protocol: the
 * gateway client opens a socket that lives for hours, this makes two one-shot
 * requests and throws the connection away. Sharing the socket client would mean
 * sharing its `readTimeout(0)` - "no timeout at all", correct for a gateway and a
 * hang forever for a login.
 *
 * ## The exchange
 *
 * `POST /oauth2/token` with the code, the verifier and the redirect URI. The
 * client is public, so there is no secret to send; PKCE is what proves this is
 * the same app that started the flow. Discord answers with an access token, and
 * since that token is minted from an OAuth grant it has to travel as
 * `Authorization: Bearer <token>` - not the raw header a pasted user token uses.
 */
class DiscordOAuthApi(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        /**
         * One per process, for the same reason `DiscordRpcClient.shared` is: the
         * dialog can be opened more than once, and an `OkHttpClient` built per
         * composition is a connection pool nobody closes.
         */
        val shared: DiscordOAuthApi by lazy { DiscordOAuthApi() }
    }

    private val moshi = Moshi.Builder().build()
    private val tokenAdapter = moshi.adapter(OAuthTokenResponse::class.java)
    private val errorAdapter = moshi.adapter(OAuthErrorResponse::class.java)
    private val userAdapter = moshi.adapter(DiscordUser::class.java)

    /**
     * Trades an authorization code for an access token.
     *
     * [codeVerifier] is the preimage of the challenge that went out with the
     * authorize request. Discord compares its hash, which is what makes an
     * intercepted code useless without it.
     */
    suspend fun exchange(
        clientId: String,
        code: String,
        codeVerifier: String
    ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", clientId.trim())
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", DiscordOAuth.REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()
            val request = Request.Builder()
                .url(DiscordOAuth.TOKEN_URL)
                .post(body)
                .addHeader("Accept", "application/json")
                .build()
            http.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = runCatching { errorAdapter.fromJson(payload) }.getOrNull()
                    throw IOException(tokenFailureMessage(response.code, error))
                }
                val token = tokenAdapter.fromJson(payload)
                    ?: throw IOException("Discord returned an unreadable token. Try again.")
                if (token.accessToken.isBlank()) {
                    throw IOException("Discord approved the sign-in but sent no token.")
                }
                token
            }
        }
    }

    /**
     * The account a bearer token belongs to.
     *
     * Also the only proof that the token works: a code exchange can succeed while
     * the grant is missing `identify`, in which case this is the call that says so.
     */
    suspend fun me(accessToken: String): Result<DiscordAccount> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(DiscordOAuth.API_ME_URL)
                .get()
                // A bearer token is read from the header's scheme; sent the way a
                // pasted user token is sent, this comes back 401 with nothing that
                // points at the header.
                .addHeader(
                    "Authorization",
                    DiscordOAuth.credential(accessToken, DiscordAuthKind.OAUTH)
                )
                .addHeader("Accept", "application/json")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        when (response.code) {
                            401 -> "Discord rejected the sign-in. Start it again."
                            403 -> "Discord blocked this account. Check it and try again."
                            429 -> "Discord rate limited the sign-in check. Wait a moment."
                            else -> "Discord could not read the account (${response.code})."
                        }
                    )
                }
                val body = response.body?.string().orEmpty()
                val user = userAdapter.fromJson(body)
                    ?: throw IOException("Discord returned an unreadable account.")
                DiscordAccount.from(user)
            }
        }
    }

    private fun tokenFailureMessage(code: Int, error: OAuthErrorResponse?): String {
        val detail = error?.description?.takeIf { it.isNotBlank() }
            ?: error?.error?.takeIf { it.isNotBlank() }
        return when {
            // The reader-visible shape of a stale flow: the code was already used,
            // or the state it came back with was started by a different app.
            code == 400 && detail != null -> "Discord refused the sign-in: $detail"
            code == 400 -> "Discord refused the sign-in. Start it again."
            code == 401 -> "Discord did not accept this application's credentials."
            code == 429 -> "Discord rate limited the sign-in. Wait a moment and retry."
            else -> "Discord could not complete the sign-in ($code)."
        }
    }
}

/** What the token endpoint returns. Only the fields this app acts on. */
@JsonClass(generateAdapter = true)
data class OAuthTokenResponse(
    @Json(name = "access_token") val accessToken: String = "",
    @Json(name = "token_type") val tokenType: String = "",
    @Json(name = "expires_in") val expiresIn: Long = 0L,
    @Json(name = "refresh_token") val refreshToken: String = "",
    val scope: String = ""
)

@JsonClass(generateAdapter = true)
internal data class OAuthErrorResponse(
    val error: String? = null,
    @Json(name = "error_description") val description: String? = null
)
