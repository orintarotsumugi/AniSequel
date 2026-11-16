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
     * Discord does not accept an arbitrary custom scheme such as
     * `anisequel://discord` as a registered redirect URI for a standard (non-Social-SDK)
     * OAuth2 application: its developer portal only accepts `https://` redirects, so
     * the authorize request stopped on Discord's "Invalid OAuth2 redirect_uri" page
     * and no callback ever reached the phone. (The `discord-<application id>:/authorize/callback`
     * form only works for applications enrolled in the Social SDK.)
     *
     * The redirect is therefore a normal https URL on GitHub Pages that forwards the
     * callback to the app's `anisequel://discord` scheme. It has to be registered,
     * verbatim, in the Discord developer portal, and GitHub Pages has to be enabled
     * for this repository (Settings -> Pages -> source: main branch, /docs folder).
     */
    const val REDIRECT_URI: String = "https://orintarotsumugi.github.io/AniSequel/discord-callback.html"

    const val AUTHORIZE_URL: String = "https://discord.com/oauth2/authorize"

    /** The token endpoint. POST, form encoded, `application/x-www-form-urlencoded`. */
    const val TOKEN_URL: String = "https://discord.com/api/oauth2/token"

    const val API_ME_URL: String = "https://discord.com/api/v9/users/@me"

    /** Where a reader without a client ID makes one, and registers the redirect. */
    const val DEVELOPER_PORTAL_URL: String = "https://discord.com/developers/applications"

    /**
     * The public Discord application this build ships with.
     *
     * Its redirect is registered as [REDIRECT_URI], so a fresh install can offer
     * the one-tap sign-in without making the reader create an application first.
     * The ID is public by design; the token endpoint still requires PKCE, and no
     * client secret is shipped in an APK.
     */
    const val DEFAULT_CLIENT_ID: String = "1571747436542566561"

    /**
     * The application's public key, as shown in Discord's developer portal.
     *
     * Discord uses it to verify interaction payloads. The current RPC/OAuth
     * features do not receive interactions, so nothing reads this at runtime yet;
     * it is recorded here with the application it belongs to rather than left to
     * be copied out of a chat message when an interaction endpoint is added.
     */
    const val APPLICATION_PUBLIC_KEY: String =
        "4d3ce4d546bc9f7949e02b1be4abd0d032054d7515c6e9579d7a93427f52dd5d"

    /**
     * The scopes asked for.
     *
     * `identify` is what `/users/@me` needs, so the app can name the account it
     * linked. `gateway.connect` is the one that matters for the presence
     * feature (a user token is only allowed on the gateway when it carries it),
     * but it is not a public scope: requesting it on an application that has not
     * been approved by Discord makes the whole authorize request fail with
     * "The requested scope is invalid, unknown, or malformed." So only the
     * public `identify` scope is requested here; the presence stays off unless
     * the application is later approved for `gateway.connect`.
     */
    const val SCOPES: String = "identify"

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
        // Callback shapes accepted:
        //  - the legacy `anisequel://discord` (kept so an in-flight sign-in from
        //    an older build still resolves, and the GitHub Pages redirect target),
        //  - the Social-SDK `discord-<application id>:/authorize/callback`.
        // The browser follows the https GitHub Pages redirect to anisequel://discord,
        // so this parser only ever sees those two schemes in practice.
        // Either way the query carries the code.
        val scheme = uri.substringBefore(':', missingDelimiterValue = "")
        val isAnisequel = scheme.equals("anisequel", ignoreCase = true)
        val isDiscordMobile = scheme.startsWith("discord-", ignoreCase = true)
        if (!isAnisequel && !isDiscordMobile) return Redirect.Ignored

        val beforeQuery = uri.substringBefore('?')
        val validTarget = when {
            isAnisequel -> uri.substringAfter("anisequel://", missingDelimiterValue = "")
                .substringBefore('?')
                .trimEnd('/')
                .equals("discord", ignoreCase = true)
            else -> beforeQuery.contains("/authorize/callback", ignoreCase = true)
        }
        if (!validTarget) return Redirect.Ignored

        val withoutScheme = if (isAnisequel) {
            uri.substringAfter("anisequel://", missingDelimiterValue = "")
        } else {
            uri.substringAfter(':', missingDelimiterValue = "").removePrefix("//")
        }
        if (withoutScheme.isBlank()) return Redirect.Ignored

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
            char.toString().toByteArray(Charsets.UTF_8).forEach { bytes += it }
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
