package com.anisequel

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every name on the Credits screen is a real address.
 *
 * ## What this is for
 *
 * A credit is only a thank-you if the reader can reach the thing being thanked.
 * "Thanks to itsmechinmoy" with a mistyped profile URL is worse than no link at
 * all: it looks like a page, it is tappable, and it lands on a GitHub 404. The
 * URLs are also the one part of that screen that cannot be checked by looking at
 * it - the card draws identically whether the address resolves or not.
 *
 * So this pins the six addresses by value, and pins that they go through the
 * app's one opener rather than through an `Intent` written at the call site. The
 * opener copies the address when nothing can open it, which is the difference
 * between a tap that does nothing and a tap that leaves the reader something to
 * paste.
 */
class CreditsLinksTest {

    private fun source(path: String): String {
        val file = File(findProjectRoot(), path)
        assertTrue(
            "could not read ${file.path}; a guard passing on a missing file is the " +
                "same failure it exists to prevent",
            file.isFile
        )
        return file.readText()
    }

    private val credits
        get() = source("app/src/main/java/com/anisequel/ui/screens/settings/CreditsSection.kt")

    @Test
    fun `the developer and every helper link to the page they name`() {
        val creditSource = credits
        listOf(
            "https://github.com/orintarotsumugi",
            "https://github.com/itsmechinmoy",
            "https://github.com/itsmechinmoy/Dantotsu",
            "https://github.com/aayush2622",
            "https://github.com/aayush2622/Dartotsu",
            "https://github.com/RyanYuuki/AnymeX"
        ).forEach { url ->
            assertTrue(
                "the credits page no longer links $url, so the card thanks somebody " +
                    "the reader cannot reach",
                creditSource.contains("\"$url\"")
            )
        }
    }

    @Test
    fun `a credit chip opens through the app's one opener`() {
        val creditSource = credits
        assertTrue(
            "a credit chip does not open its address, so it is a pill that looks " +
                "tappable and does nothing",
            creditSource.contains("openExternalUrl(context, link.url)")
        )
        assertTrue(
            "the means of opening is not imported, or the chip reaches for a " +
                "second Intent at the call site",
            creditSource.contains("import com.anisequel.ui.components.openExternalUrl")
        )
    }

    @Test
    fun `the credits page is one row of the About shelf`() {
        val about = source("app/src/main/java/com/anisequel/ui/screens/settings/InfoSettingsTab.kt")
        assertTrue(
            "About has no Credits row, so the page has no door",
            about.contains("about_row_credits")
        )
        assertTrue(
            "the Credits row is not dispatched, so it opens a blank screen",
            about.contains("AboutSection.CREDITS -> CreditsSectionBody(")
        )
        assertTrue(
            "the repository address is copied instead of shared, so the credits page " +
                "and Help & links can drift apart",
            about.contains("internal const val GITHUB_REPOSITORY_URL =")
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
