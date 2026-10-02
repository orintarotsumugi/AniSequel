package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.AniListOAuth
import com.example.data.network.RedirectResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}