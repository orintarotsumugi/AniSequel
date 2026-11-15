package com.anisequel

import com.anisequel.domain.discord.DiscordLogin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The sign-in transport, pinned where a device is not needed.
 *
 * 2.1.1 moved both sign-ins out of the app and into the device's browser: AniList
 * through a plain `ACTION_VIEW` instead of a Custom Tab, and Discord through the
 * same intent instead of a `WebView` that scraped the page's storage. Neither can
 * be observed in a JVM test - the browser is another app - but the parts that
 * would quietly regress can be: the URL the browser is sent to, the helper the
 * screens use, and the absence of a web view in the UI tree.
 *
 * The source checks strip comments first, so an explanation of what was removed
 * does not read as a use of it.
 */
class DiscordLoginBrowserTest {

    private val root = findProjectRoot()

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `the browser sign-in points at Discord's own login page`() {
        assertEquals(
            "the browser has to open Discord's own https page, not a lookalike or a proxy",
            "https://discord.com/login",
            DiscordLogin.BROWSER_LOGIN_URL
        )
    }

    /**
     * The prefix a paste carries is not part of the token.
     *
     * `removePrefix` is case-sensitive, so the previous version left a lower-case
     * `bearer` in the value, passed the length check once the space was stripped,
     * and stored something Discord refuses - with nothing in the failure to
     * suggest the paste was the problem.
     */
    @Test
    fun `a pasted token loses its scheme prefix in any case`() {
        val token = "mfa.abcdefghijklmnopqrstuvwxyz1234567890"

        listOf(
            "",
            "Bearer ",
            "bearer ",
            "BEARER ",
            "Bot ",
            "bot ",
            "Bearer Bot ",
            "Bot Bearer ",
            // A copied header carries a tab, not a space. The old literal-space
            // check missed it, then whitespace removal welded the prefix onto
            // the token: length-plausible, and refused by Discord.
            "Bearer\t",
            "Bot\t"
        ).forEach { prefix ->
            assertEquals(
                "the prefix '$prefix' has to be stripped",
                token,
                DiscordLogin.normalizeToken(prefix + token)
            )
        }

        assertEquals(token, DiscordLogin.normalizeToken("  $token  "))
        assertTrue(DiscordLogin.looksLikeToken("bearer $token"))
        // A real token that merely starts with the letters "bot" is not a prefix.
        assertEquals("botanical$token", DiscordLogin.normalizeToken("botanical$token"))
    }

    @Test
    fun `no screen drives its own web view`() {
        val uiRoot = File(root, "app/src/main/java/com/anisequel/ui")

        val offenders = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val code = source(file.relativeTo(root).path)
                code.contains("android.webkit") || code.contains("WebView(")
            }
            .map { it.relativeTo(root).path }
            .toList()

        assertTrue(
            "these screens still run an embedded web view: $offenders. Sign-in is " +
                "the device browser's job; an in-app renderer is a login page the " +
                "reader cannot inspect, and a process to leak.",
            offenders.isEmpty()
        )
    }

    @Test
    fun `the Discord dialog hands the login page to the device browser`() {
        val code = source(
            "app/src/main/java/com/anisequel/ui/screens/settings/DiscordConnectionCards.kt"
        )

        assertTrue(
            "the dialog must open the login page through the shared external-link " +
                "helper, which reports a device with no browser instead of doing nothing",
            code.contains("openExternalUrlOrCopy(context, DiscordLogin.BROWSER_LOGIN_URL)")
        )
    }

    @Test
    fun `AniList sign-in opens the device browser rather than a custom tab`() {
        val code = source("app/src/main/java/com/anisequel/ui/screens/login/OAuthCard.kt")

        assertTrue(
            "the connect button must hand the authorize URL to the device browser",
            code.contains("openExternalUrlOrCopy(context, authUrl)")
        )
        assertFalse(
            "Custom Tabs is an in-app browser surface; this release removed it",
            code.contains("CustomTabsIntent")
        )
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
