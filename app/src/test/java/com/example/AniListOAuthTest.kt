package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.AniListOAuth
import com.example.data.network.RedirectResult
import com.example.data.repository.AuthRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The OAuth contract with AniList.
 *
 * The failure this guards against is silent and hard to self-diagnose: if the
 * Redirect URL registered at anilist.co/developer is not the one this app
 * declares, AniList delivers the token to a web page, the browser opens
 * something that will not load, and the app simply never hears back.
 */
@RunWith(RobolectricTestRunner::class)
class AniListOAuthTest {

    @Test
    fun `the authorize request carries only the parameters AniList accepts for mobile`() {
        val url = AniListOAuth.authorizationUrl("23668")

        assertEquals(
            "https://anilist.co/api/v2/oauth/authorize?client_id=23668&response_type=token",
            url
        )
        assertTrue("response_type=token is the implicit grant AniList documents for mobile",
            url.contains("response_type=token"))
        // AniList rejects the flow with unsupported_grant_type when a
        // redirect_uri or state is also sent, so "adding it for clarity" is
        // how this screen breaks.
        assertFalse("a redirect_uri makes AniList reject the request", url.contains("redirect_uri"))
        assertFalse("a state makes AniList reject the request", url.contains("state="))
    }

    @Test
    fun `authorization uses this app's own AniList client`() {
        // AniList delivers the token to whatever Redirect URL is registered
        // against the client named in the authorize request. Authorizing
        // against a borrowed client id sends the token to somebody else's app,
        // so sign-in can never complete no matter how the Redirect URL here is
        // configured - it is the wrong client's redirect that is used.
        assertEquals(
            "the shipped client id must be AniSequel's own AniList app",
            "52542",
            AuthRepositoryImpl.DEFAULT_CLIENT_ID
        )

        val url = AniListOAuth.authorizationUrl(AuthRepositoryImpl.DEFAULT_CLIENT_ID)
        assertTrue(
            "the authorize request must name our own client, was $url",
            url.contains("client_id=52542")
        )
    }

    @Test
    fun `the redirect url is the one this build can actually handle`() {
        // Resolved against the merged manifest of the build under test, so a
        // change to AndroidManifest.xml that breaks the callback fails here
        // rather than in a browser tab on someone's phone.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val handlers = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, Uri.parse(AniListOAuth.REDIRECT_URI)),
            0
        )

        assertEquals(
            "${AniListOAuth.REDIRECT_URI} is not handled by this app - the OAuth " +
                "callback would go nowhere and sign-in would never complete",
            listOf(context.packageName),
            handlers.map { it.activityInfo.packageName }
        )
    }

    @Test
    fun `the redirect url is a custom scheme, not localhost`() {
        val uri = Uri.parse(AniListOAuth.REDIRECT_URI)

        assertEquals("anisequel", uri.scheme)
        assertFalse(
            "a localhost redirect is delivered to a browser, not to the app",
            AniListOAuth.REDIRECT_URI.contains("localhost")
        )
    }

    @Test
    fun `reads the token out of the redirect fragment`() {
        val result = AniListOAuth.parseRedirect(
            Uri.parse("anisequel://oauth#access_token=abc123&token_type=Bearer")
        )

        assertEquals(RedirectResult.Success("abc123"), result)
    }

    @Test
    fun `keeps a token containing url-encoded padding intact`() {
        // %3D is a padded base64 '='. Decoding the whole value rather than
        // splitting on '=' is what keeps the token usable.
        val result = AniListOAuth.parseRedirect(
            Uri.parse("anisequel://oauth#access_token=eyJhbGciOi.payload%3D")
        )

        assertEquals(RedirectResult.Success("eyJhbGciOi.payload="), result)
    }

    @Test
    fun `a declined consent screen is a cancellation, not a missing token`() {
        val result = AniListOAuth.parseRedirect(
            Uri.parse("anisequel://oauth#error=access_denied")
        )

        assertEquals(RedirectResult.Error("access_denied", null), result)
    }

    @Test
    fun `a cold start from the launcher is not mistaken for a callback`() {
        // Launching the app normally delivers no data at all; treating that as a
        // failed redirect put "No access token found in redirect" on screen.
        assertEquals(RedirectResult.NoCallback, AniListOAuth.parseRedirect(null))
    }

    /**
     * People paste every shape of token value, and storing any of them verbatim
     * produces a session AniList rejects - which the app then reports as
     * "Session expired", pointing at the wrong thing entirely.
     */
    @Test
    fun `recovers a token from the things people actually paste`() {
        val token = "eyJhbGciOiJIUzI1NiJ9.payload.signature"

        assertEquals(token, AniListOAuth.extractToken(token))
        assertEquals(token, AniListOAuth.extractToken("access_token=$token"))
        assertEquals(token, AniListOAuth.extractToken("access_token=$token&token_type=Bearer"))
        assertEquals(token, AniListOAuth.extractToken("  access_token=$token  "))
        assertEquals(token, AniListOAuth.extractToken("Bearer $token"))
        assertEquals(token, AniListOAuth.extractToken("\"$token\""))
        assertEquals(token, AniListOAuth.extractToken("'$token'"))
        assertEquals(
            token,
            AniListOAuth.extractToken("https://anilist.co/api/v2/oauth/null#access_token=$token&token_type=Bearer")
        )
    }

    @Test
    fun `base64 padding in a token survives`() {
        // '=' is padding, not a separator, and splitting on it produced a token
        // that looked pasted correctly and then failed every request.
        val padded = "eyJhbGciOiJIUzI1NiJ9.payload=="

        assertEquals(padded, AniListOAuth.extractToken("access_token=$padded"))
        assertEquals(padded, AniListOAuth.extractToken("access_token=$padded&token_type=Bearer"))
    }

    @Test
    fun `something that is not a token is rejected rather than stored`() {
        assertNull(AniListOAuth.extractToken(""))
        assertNull(AniListOAuth.extractToken("   "))
        assertNull(AniListOAuth.extractToken("I think it worked?"))
    }
}