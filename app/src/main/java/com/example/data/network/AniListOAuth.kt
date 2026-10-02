package com.example.data.network

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The AniList OAuth contract for this app, in one place.
 *
 * ## The redirect URL is registered on AniList, not sent by the app
 *
 * This is the part that is easy to get wrong, and the symptom is unmistakable:
 * you tap "Connect AniList Account", approve in the browser, and the browser
 * then tries to load a page that does not exist. If that page is `localhost`,
 * your app's Redirect URL at <https://anilist.co/developer> is set to
 * `http://localhost`, and the token is being delivered to a web page instead of
 * to this app - so sign-in silently never completes.
 *
 * It must be set to [REDIRECT_URI], which matches the intent filter declared in
 * `AndroidManifest.xml`.
 *
 * ## Do not "fix" this by adding redirect_uri to the authorize URL
 *
 * AniList's mobile guidance is the implicit grant, and the authorize request
 * carries **only** `client_id` and `response_type=token`. Adding a `redirect_uri`
 * or a `state` makes AniList answer `unsupported_grant_type` and reject the
 * flow outright. The working Android clients (Dantotsu, Saikou, hibiki) all
 * send exactly those two parameters for this reason, and the implicit grant is
 * what AniList documents for clients that cannot keep a client secret - which
 * is any app distributed as an APK.
 */
object AniListOAuth {

    /**
     * Must match the `<data android:scheme="anisequel" android:host="oauth" />`
     * intent filter in `AndroidManifest.xml`, and the Redirect URL registered
     * with AniList. AniList requires an exact match.
     */
    const val REDIRECT_URI: String = "anisequel://oauth"

    const val AUTHORIZE_ENDPOINT: String = "https://anilist.co/api/v2/oauth/authorize"

    /** Where the user goes to fix a wrong Redirect URL or look up the client id. */
    const val DEVELOPER_SETTINGS_URL: String = "https://anilist.co/developer"

    /**
     * Only `client_id` and `response_type` - see the note on [REDIRECT_URI].
     *
     * The redirect target is whatever AniList has registered for the app, which
     * is why [REDIRECT_URI] has to be documented to the user rather than sent.
     */
    fun authorizationUrl(clientId: String): String =
        "$AUTHORIZE_ENDPOINT?client_id=$clientId&response_type=token"

    /**
     * Reads the outcome from the redirect.
     *
     * AniList returns `access_token` in the fragment for the implicit grant, and
     * `error` when consent is declined. Values are URL-decoded, which matters
     * for tokens containing `=` or `+`.
     */
    fun parseRedirect(uri: Uri?): RedirectResult {
        if (uri == null) return RedirectResult.NoCallback

        val fragment = uri.fragment
        val parameters = buildMap {
            parseParameters(fragment)?.let(::putAll)
            if (isEmpty()) parseParameters(uri.query)?.let(::putAll)
        }

        if (parameters.isEmpty()) return RedirectResult.NoCallback

        parameters["error"]?.takeIf { it.isNotBlank() }?.let { error ->
            return RedirectResult.Error(
                code = error,
                description = parameters["error_description"]
            )
        }

        parameters["access_token"]?.takeIf { it.isNotBlank() }?.let { token ->
            return RedirectResult.Success(token)
        }

        return RedirectResult.NoCallback
    }

    private fun parseParameters(raw: String?): Map<String, String>? {
        if (raw.isNullOrBlank()) return null
        return raw.split('&')
            .mapNotNull { pair ->
                val separator = pair.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = Uri.decode(pair.substring(0, separator))
                val value = Uri.decode(pair.substring(separator + 1))
                key to value
            }
            .toMap()
    }

    /**
     * Recovers a usable token from whatever the user pasted.
     *
     * The "Paste Token" dialog exists for the manual copy flow, and people
     * paste all of these:
     *
     *  - `access_token=eyJ...` - the parameter copied with its name
     *  - `eyJ...&token_type=Bearer` - the whole redirect fragment
     *  - `anilist.co/api/v2/oauth/null#access_token=eyJ...` - the entire URL
     *  - `"eyJ..."` or `'eyJ...'` - with quotes, straight from the address bar
     *  - `Bearer eyJ...` - with the scheme
     *
     * Every one of those stored verbatim is a token AniList rejects, and the
     * app then reports "Session expired" for a session that was never invalid.
     * Saving the extracted value instead means a sloppy paste still works.
     */
    fun extractToken(pasted: String): String? {
        var value = pasted.trim()

        // A whole URL, or the fragment copied out of one.
        value = value.substringAfterLast('#')

        if (value.startsWith("access_token", ignoreCase = true) && '=' in value) {
            // '=' is also base64 padding, so only the FIRST one separates the
            // parameter name from the value. Splitting on all of them is what
            // silently produced a truncated, unusable token.
            value = value.substringAfter('=')
        } else if (value.startsWith("bearer", ignoreCase = true) && ' ' in value) {
            value = value.substringAfter(' ').trimStart()
        }

        value = value.substringBefore('&')
            .substringBefore('?')
            .trim()
            .trim('"', '\'')

        // A token is a single unbroken run of base64url characters. Whitespace
        // inside it means prose was pasted; trimming at the first space would
        // turn "I think it worked?" into "I", which stores cleanly and then
        // fails every request as a "Session expired".
        val looksLikeToken = value.isNotBlank() &&
            value.none { character -> character.isWhitespace() || character == '/' || character == ':' }

        return value.takeIf { looksLikeToken }
    }

    fun openDeveloperSettings(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_SETTINGS_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

sealed interface RedirectResult {
    data class Success(val accessToken: String) : RedirectResult

    data class Error(val code: String, val description: String?) : RedirectResult

    /** Not our callback at all - e.g. the app was opened from the launcher. */
    data object NoCallback : RedirectResult
}