package com.anisequel.domain.discord

import java.security.MessageDigest
import java.util.Base64

/**
 * The OAuth2 leg of Discord sign-in.
 *
 * ## Why authorization code + PKCE and not a token in the browser
 *
 * Discord gives a browser no way to hand a token back to an app, which is why the
 * previous flow asked the reader to copy the token out of their own session by
 * hand. An application with a registered redirect can instead be handed an
 * authorization *code*, and this device exchanges that code for an access token
 * itself. Nothing the reader has to read, copy or paste.
 *
 * The client is public - it ships inside an APK, so it cannot keep a secret - and
 * that is exactly the case PKCE exists for: the exchange carries a verifier that
 * never left this device, so a code intercepted from the redirect is worthless to
 * whoever took it. Discord requires PKCE for custom-scheme redirects for the same
 * reason.
 *
 * Everything here is a pure function of its inputs. The randomness is passed in
 * from a caller, so the tests pin the exact verifier, challenge and URL a device
 * would produce instead of only their shape.
 */
object DiscordOAuth {

    /**
     * Where Discord sends the reader back to.
     *
     * A custom scheme, because Discord no longer accepts `http://localhost` for
     * native apps and a loopback server would be a listener this app does not need.
     * It has to match, exactly, the redirect registered for the application in
     * Discord's developer portal - Discord refuses the authorize request otherwise,
     * before the reader sees a login page.
     *
     * The intent filter in the manifest declares the same scheme and host; a test
     * pins that agreement, because a typo here is a sign-in that silently returns
     * to the home screen.
     */
    const val REDIRECT_URI: String = "anisequel://discord"

    const val AUTHORIZE_URL: String = "https://discord.com/oauth2/authorize"

    /** The token endpoint. POST, form encoded, `application/x-www-form-urlencoded`. */
    const val TOKEN_URL: String = "https://discord.com/api/oauth2/token"

    const val API_ME_URL: String = "https://discord.com/api/v9/users/@me"

    /** Where a reader without a client ID makes one, and registers the redirect. */
    const val DEVELOPER_PORTAL_URL: String = "https://discord.com/developers/applications"

    /**
     * The scopes asked for.
     *
     * `identify` is what `/users/@me` needs, so the app can name the account it
     * linked. `gateway.connect` is the one that matters for the feature: a user
     * token minted from an OAuth grant is only allowed on the gateway when it
     * carries this scope, and the presence is published over the gateway. Asking
     * for both keeps the token useful for the thing the page is for.
     */
    const val SCOPES: String = "identify gateway.connect"

    /** PKCE's S256 method: the only one Discord accepts. */
    const val CHALLENGE_METHOD: String = "S256"

    /**
     * Whether a value can be a Discord application ID.
     *
     * A snowflake: digits only, and long. This is checked before the browser is
     * opened rather than after the round trip, because a malformed ID comes back
     * as Discord's own "Invalid OAuth2 URL" page, which reads as the app being
     * broken rather than a field being wrong.
     */
    fun isValidClientId(raw: String): Boolean {
        val value = raw.trim()
        return value.length in 15..22 && value.all { it.isDigit() }
    }

    /**
     * A code verifier, 43 to 128 characters of the unreserved set.
     *
     * Base64url of 48 random bytes is 64 characters and uses nothing outside
     * `[A-Za-z0-9-._~]`, so no further encoding is needed. Longer than the
     * 43-character floor because the entropy, not the floor, is what stops a
     * guessed verifier from completing someone else's stolen code.
     */
    fun codeVerifier(random: (Int) -> ByteArray): String =
        base64Url(random(48)).take(64)

    /** A state value, so a callback this app did not start is refused. */
    fun state(random: (Int) -> ByteArray): String =
        base64Url(random(16)).take(24)

    /**
     * The S256 challenge: base64url of the SHA-256 of the verifier's ASCII bytes.
     *
     * Hashed rather than sent as-is, which is the entire point of PKCE: the code
     * challenge travels in a URL other apps can see, and only its preimage is
     * needed at the token endpoint.
     */
    fun codeChallengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return base64Url(digest.digest(verifier.toByteArray(Charsets.US_ASCII)))
    }

    /**
     * The authorize URL the device's browser is sent to.
     *
     * Parameters are emitted in a fixed order so the tests can pin the whole URL
     * rather than a set of fragments: a scope list that lost its separator, or a
     * redirect URI that stopped being encoded, both still "contain" every value
     * and are both refused by Discord.
     */
    fun authorizationUrl(clientId: String, codeChallenge: String, state: String): String =
        buildString {
            append(AUTHORIZE_URL)
            append('?')
            append("response_type=code")
            append("&client_id=").append(encode(clientId.trim()))
            append("&scope=").append(encode(SCOPES))
            append("&redirect_uri=").append(encode(REDIRECT_URI))
            append("&state=").append(encode(state))
            append("&code_challenge=").append(encode(codeChallenge))
            append("&code_challenge_method=").append(CHALLENGE_METHOD)
        }

    /**
     * What came back on [REDIRECT_URI].
     *
     * `state` is returned rather than checked here: only the caller that started
     * the flow holds the value to compare against, and a comparison made in this
     * object would have to trust whatever was passed in anyway.
     */
    sealed interface Redirect {
        /** Discord accepted the reader and sent a code back. */
        data class Code(val code: String, val state: String) : Redirect

        /** Discord refused, or the reader pressed Cancel. */
        data class Failure(val message: String) : Redirect

        /** Not this app's Discord callback, or one with nothing usable in it. */
        data object Ignored : Redirect
    }

    /**
     * Reads a redirect URI into a [Redirect].
     *
     * Deliberately string-based rather than `android.net.Uri`: the decision is
     * worth a unit test, and a pure function of a string can be tested on the JVM
     * without a device. The parse only has to handle this app's own scheme, so it
     * is a query split and nothing more.
     */
    fun parseRedirect(uri: String?): Redirect {
        if (uri.isNullOrBlank()) return Redirect.Ignored
        val withoutScheme = uri.substringAfter("anisequel://", missingDelimiterValue = "")
        if (withoutScheme.isEmpty()) return Redirect.Ignored
        val host = withoutScheme.substringBefore('?').trimEnd('/')
        if (!host.equals("discord", ignoreCase = true)) return Redirect.Ignored

        val query = withoutScheme.substringAfter('?', missingDelimiterValue = "")
        val parameters = query.split('&')
            .mapNotNull { pair ->
                if (pair.isBlank()) return@mapNotNull null
                val name = decode(pair.substringBefore('='))
                val value = decode(pair.substringAfter('=', missingDelimiterValue = ""))
                name to value
            }
            .toMap()

        // Discord reports a refusal as `error`; a reader who pressed Cancel is
        // reported the same way. Both mean no code was issued.
        val error = parameters["error"]
        if (!error.isNullOrBlank()) {
            val description = parameters["error_description"]
            return Redirect.Failure(
                if (description.isNullOrBlank()) {
                    "Discord did not grant access ($error)."
                } else {
                    "Discord did not grant access: $description"
                }
            )
        }
        val code = parameters["code"]
        if (code.isNullOrBlank()) return Redirect.Ignored
        return Redirect.Code(code = code, state = parameters["state"].orEmpty())
    }

    /**
     * The stored token as Discord's servers have to receive it.
     *
     * One function, because two places send a Discord credential - the HTTP check
     * and the gateway's IDENTIFY - and a `Bearer ` prefix that only one of them
     * adds is a sign-in that half works: the account is named by `/users/@me` and
     * then the gateway closes with `4004 authentication failed`.
     */
    fun credential(token: String, kind: DiscordAuthKind): String {
        val normalized = DiscordLogin.normalizeToken(token)
        return when (kind) {
            DiscordAuthKind.OAUTH -> "Bearer $normalized"
            DiscordAuthKind.TOKEN -> normalized
        }
    }

    /** Where the reader creates the application whose ID this page asks for. */
    fun setupHint(): String =
        "Create an application at discord.com/developers/applications, add " +
            "$REDIRECT_URI as a redirect URI, and paste its Application ID here."

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /**
     * Percent-encoding for a query value.
     *
     * `URLEncoder` is form encoding: it turns a space into `+`, which Discord
     * reads as a literal plus in a scope list and refuses. Only the unreserved
     * characters are left alone, which is what RFC 3986 asks for.
     */
    internal fun encode(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val char = byte.toInt().toChar()
            if (char.isUnreserved()) {
                append(char)
            } else {
                append('%')
                append(hex(byte.toInt()))
            }
        }
    }

    private fun decode(value: String): String {
        if ('%' !in value && '+' !in value) return value
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (byte != null) {
                    bytes += byte.toByte()
                    index += 3
                    continue
                }
            }
            // A literal '+' is kept: this is a query string, not a form body, and
            // the values here are base64url codes and states with no spaces in
            // them either way.
            bytes += char.toString().toByteArray(Charsets.UTF_8)
            index++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun Char.isUnreserved(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' ||
            this == '-' || this == '.' || this == '_' || this == '~'

    private fun hex(value: Int): String {
        val digits = "0123456789ABCDEF"
        val byte = value and 0xFF
        return "${digits[byte shr 4]}${digits[byte and 0x0F]}"
    }
}

/** How the stored Discord credential is presented to Discord's servers. */
enum class DiscordAuthKind(
    val storageValue: String,
    val displayName: String
) {
    /**
     * A token the reader pasted from their own session.
     *
     * Sent raw, which is what a user token expects in an `Authorization` header
     * and what the gateway's IDENTIFY wants.
     */
    TOKEN("token", "Pasted token"),

    /**
     * An OAuth2 access token, sent with a `Bearer ` prefix.
     *
     * The prefix is part of the credential, not decoration: the gateway only
     * accepts a user token minted by an OAuth grant when the IDENTIFY payload's
     * token starts with `Bearer `, and Discord's HTTP API only reads it as a
     * bearer token when the header says so. Stripping it - which is exactly what
     * [DiscordLogin.normalizeToken] does to a paste - is why an OAuth token can
     * look correct and still be refused.
     */
    OAUTH("oauth", "Discord sign-in");

    companion object {
        fun fromStorage(value: String?): DiscordAuthKind =
            entries.firstOrNull { it.storageValue == value } ?: TOKEN
    }
}
