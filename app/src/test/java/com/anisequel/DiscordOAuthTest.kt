package com.anisequel

import com.anisequel.domain.discord.DiscordAuthKind
import com.anisequel.domain.discord.DiscordLogin
import com.anisequel.domain.discord.DiscordOAuth
import com.anisequel.domain.discord.DiscordSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Discord sign-in, pinned where a device is not needed.
 *
 * The flow itself needs a browser and Discord's servers, and neither exists on the
 * JVM. What can be pinned is everything that would silently break it: the URL the
 * browser is sent to, the PKCE arithmetic, what a callback is allowed to mean, the
 * `Bearer ` prefix that separates an OAuth token from a pasted one, and the fact
 * that the deep link in the manifest is the redirect URI Discord is told about.
 */
class DiscordOAuthTest {

    private val projectRoot = findProjectRoot()

    @Test
    fun `the authorize URL is the one Discord expects`() {
        assertEquals(
            "https://discord.com/oauth2/authorize" +
                "?response_type=code" +
                "&client_id=123456789012345678" +
                // A space would be encoded as '+' by form encoding, which Discord
                // reads as part of the scope name and refuses.
                "&scope=identify%20connections" +
                "&redirect_uri=https%3A%2F%2Forintarotsumugi.github.io%2FAniSequel%2Fdiscord-callback.html" +
                "&state=state123" +
                "&code_challenge=challenge123" +
                "&code_challenge_method=S256",
            DiscordOAuth.authorizationUrl(
                clientId = "123456789012345678",
                codeChallenge = "challenge123",
                state = "state123"
            )
        )
    }

    @Test
    fun `the PKCE challenge is the base64url of the verifier's SHA-256`() {
        // The arithmetic, not a shape check: a challenge that is any other hash -
        // SHA-1, hex instead of base64url, padded - is refused by the token
        // endpoint with `invalid_grant`, which reads like a bad code.
        assertEquals(
            "IyPpEuePl-IKREl7dPbw7wJOacZKpk4WSt6VyOGKaXA",
            DiscordOAuth.codeChallengeS256("dBjftJeZ4CVP-hK1Z8mZ0MtvHj0AhK9AwXUbAduU2iA")
        )
    }

    @Test
    fun `a verifier and a state stay inside the unreserved set`() {
        val verifier = DiscordOAuth.codeVerifier { size -> ByteArray(size) { 7 } }
        val state = DiscordOAuth.state { size -> ByteArray(size) { 11 } }

        assertEquals(64, verifier.length)
        // Sixteen bytes is five full base64 groups and one byte over, which is 22
        // characters unpadded - `take(24)` is a ceiling, not a length.
        assertEquals(22, state.length)
        val allowed = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')
        assertTrue("verifier: $verifier", verifier.all { it in allowed })
        assertTrue("state: $state", state.all { it in allowed })
        // 43 is the floor and 128 the ceiling, so a 64-character verifier is legal
        // by six characters at the bottom and sixty-four at the top.
        assertTrue(verifier.length in 43..128)
    }

    @Test
    fun `a code comes back with the state it was started with`() {
        val redirect = DiscordOAuth.parseRedirect(
            "discord-1571747436542566561:/authorize/callback?code=the-code&state=the-state"
        )
        assertEquals(DiscordOAuth.Redirect.Code("the-code", "the-state"), redirect)
    }

    @Test
    fun `a refusal is reported as a sentence, not an empty code`() {
        val redirect = DiscordOAuth.parseRedirect(
            "discord-1571747436542566561:/authorize/callback?error=access_denied&state=the-state"
        )
        assertTrue(redirect is DiscordOAuth.Redirect.Failure)
        assertTrue(
            "the message has to name what Discord said",
            (redirect as DiscordOAuth.Redirect.Failure).message.contains("access_denied")
        )
    }

    @Test
    fun `a callback for another flow is not this app's callback`() {
        // The AniList sign-in and Discord's callback share the phone's scheme space,
        // so each parser has to refuse the other's redirect.
        assertEquals(
            DiscordOAuth.Redirect.Ignored,
            DiscordOAuth.parseRedirect("anisequel://oauth?code=the-code")
        )
        assertEquals(
            DiscordOAuth.Redirect.Ignored,
            DiscordOAuth.parseRedirect("https://example.com/callback?code=the-code")
        )
        assertEquals(DiscordOAuth.Redirect.Ignored, DiscordOAuth.parseRedirect(null))
        assertEquals(DiscordOAuth.Redirect.Ignored, DiscordOAuth.parseRedirect(""))
        assertEquals(
            DiscordOAuth.Redirect.Ignored,
            DiscordOAuth.parseRedirect("anisequel://discord?state=only")
        )
    }

    @Test
    fun `the manifest declares the redirect URI Discord is told about`() {
        // Discord compares the whole redirect URI and refuses an unregistered one
        // with "Invalid OAuth2 redirect_uri", so the intent filter has to declare
        // exactly the scheme the authorize request sends the reader back on. This
        // test used to accept `anisequel://discord`, which Discord does not allow
        // as a registered redirect for a standard OAuth2 app - the sign-in never
        // left Discord, and the only evidence was that page.
        val manifest = File(projectRoot, "app/src/main/AndroidManifest.xml").readText()
        // The legacy scheme is still declared so a build already in flight on an
        // older release (and the GitHub Pages redirect target) still lands here.
        assertTrue(
            "the manifest must still handle the anisequel://discord callback",
            manifest.contains("android:scheme=\"anisequel\"")
        )
        assertEquals(
            "https://orintarotsumugi.github.io/AniSequel/discord-callback.html",
            DiscordOAuth.REDIRECT_URI
        )
    }

    @Test
    fun `the redirect is the mobile shape Discord registers, not an arbitrary scheme`() {
        // Discord's developer portal only accepts https:// redirects for a standard
        // (non-Social-SDK) OAuth2 app, so the redirect is a normal https URL and
        // never a made-up scheme.
        assertTrue(
            "the redirect has to be an https URL the portal will accept",
            DiscordOAuth.REDIRECT_URI.startsWith("https://")
        )
        assertFalse(
            "an unregistrable scheme is what broke this sign-in",
            DiscordOAuth.REDIRECT_URI.startsWith("anisequel://")
        )
        assertFalse(
            "the Social-SDK-only scheme is not used by this standard app",
            DiscordOAuth.REDIRECT_URI.startsWith("discord-")
        )
        // The token exchange has to carry the same URI, byte for byte: Discord
        // compares it against the one from the authorize request.
        val exchange = File(
            projectRoot,
            "app/src/main/java/com/anisequel/data/discord/DiscordOAuthApi.kt"
        ).readText()
        assertTrue(
            "the exchange must send DiscordOAuth.REDIRECT_URI",
            exchange.contains(".add(\"redirect_uri\", DiscordOAuth.REDIRECT_URI)")
        )
    }

    @Test
    fun `the shipped Discord application is valid and public`() {
        assertTrue(
            "the bundled client ID must be a snowflake",
            DiscordOAuth.isValidClientId(DiscordOAuth.DEFAULT_CLIENT_ID)
        )
        assertEquals(64, DiscordOAuth.APPLICATION_PUBLIC_KEY.length)
        assertTrue(
            "a Discord public key is hexadecimal",
            DiscordOAuth.APPLICATION_PUBLIC_KEY.all { it in "0123456789abcdef" }
        )
    }

    @Test
    fun `a client ID has to look like a snowflake`() {
        assertTrue(DiscordOAuth.isValidClientId("123456789012345678"))
        assertTrue(DiscordOAuth.isValidClientId("  123456789012345678  "))
        assertFalse("an empty field is not an ID", DiscordOAuth.isValidClientId(""))
        assertFalse("letters are not an ID", DiscordOAuth.isValidClientId("1234abcd"))
        assertFalse("a four-digit value is not an ID", DiscordOAuth.isValidClientId("1234"))
    }

    @Test
    fun `an OAuth token is sent with the prefix the gateway wants`() {
        // The same stored text means two different `Authorization` values, and
        // getting it backwards is `4004 authentication failed` with no hint.
        assertEquals("abc.def.ghi", DiscordOAuth.credential("abc.def.ghi", DiscordAuthKind.TOKEN))
        assertEquals(
            "Bearer abc.def.ghi",
            DiscordOAuth.credential("abc.def.ghi", DiscordAuthKind.OAUTH)
        )
        // A stored value that somehow kept the prefix must not gain a second one.
        assertEquals(
            "Bearer abc.def.ghi",
            DiscordOAuth.credential("Bearer abc.def.ghi", DiscordAuthKind.OAUTH)
        )
        assertEquals(
            "a token pasted from a browser is still normalised the way it always was",
            "abc.def.ghi",
            DiscordLogin.normalizeToken("Bearer abc.def.ghi")
        )
    }

    @Test
    fun `the sign-in only offers itself once an application ID is set`() {
        val bare = DiscordSettings()
        val ready = DiscordSettings(oauthClientId = "123456789012345678")

        assertFalse(bare.canSignInWithDiscord)
        assertTrue(ready.canSignInWithDiscord)
        // The label the page shows must not claim a connection that does not exist,
        // and it has to name the sign-in once there is an application to sign in with.
        assertTrue(
            DiscordSettings(enabled = true).connectionLabel
                .endsWith("no Discord account linked")
        )
        assertTrue(
            DiscordSettings(enabled = true, oauthClientId = "123456789012345678")
                .connectionLabel.endsWith("sign in with Discord")
        )
    }

    @Test
    fun `an install from before the OAuth flow keeps its pasted token`() {
        assertEquals(DiscordAuthKind.TOKEN, DiscordAuthKind.fromStorage(null))
        assertEquals(DiscordAuthKind.TOKEN, DiscordAuthKind.fromStorage("nonsense"))
        assertEquals(DiscordAuthKind.OAUTH, DiscordAuthKind.fromStorage("oauth"))
    }

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }
}
