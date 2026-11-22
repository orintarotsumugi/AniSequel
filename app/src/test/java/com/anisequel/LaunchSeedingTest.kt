package com.anisequel

import com.anisequel.ui.components.detail.info.backdropDecodeWidthPx
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.ui.viewmodel.AuthUiState
import com.anisequel.ui.viewmodel.seedAuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two values a launch may carry in from the warm read, and the one cap the
 * Info page's backdrop applies to its own decode.
 *
 * Each is a pure function because each is a decision with two wrong answers that
 * look fine in code:
 *
 *  - the session seed must never turn "not read yet" into "signed out" (that is
 *    the bug `AuthUiState.Restoring` exists for - a login screen on every cold
 *    start for a reader who is signed in), and
 *  - the backdrop cap must never apply to `Crisp` (that is the setting whose whole
 *    point is a sharp picture) nor to a window it has not measured yet (a request
 *    for zero pixels is not a smaller image, it is a failed one).
 */
class LaunchSeedingTest {

    @Test
    fun `an unread session is restoring, not signed out`() {
        assertEquals(AuthUiState.Restoring, seedAuthState(resolved = false, token = null))
        assertEquals(AuthUiState.Restoring, seedAuthState(resolved = false, token = "token"))
    }

    @Test
    fun `a read session seeds the answer it read`() {
        assertEquals(AuthUiState.Unauthenticated, seedAuthState(resolved = true, token = null))
        assertEquals(AuthUiState.Unauthenticated, seedAuthState(resolved = true, token = "   "))
        assertEquals(
            AuthUiState.Authenticated("abc"),
            seedAuthState(resolved = true, token = "abc")
        )
    }

    @Test
    fun `a blurred backdrop decodes smaller than the window`() {
        assertEquals(540, backdropDecodeWidthPx(1080, InfoBackdropBlur.SOFT))
        assertEquals(360, backdropDecodeWidthPx(1080, InfoBackdropBlur.HEAVY))
    }

    /**
     * Crisp is the picture itself, so it keeps the decode the page always asked for.
     */
    @Test
    fun `a crisp backdrop keeps its full resolution`() {
        assertNull(backdropDecodeWidthPx(1080, InfoBackdropBlur.CRISP))
    }

    /**
     * The window is measured by Compose and is zero on the first composition of a
     * `Dialog`, so an unmeasured window has to mean "no cap" rather than a
     * one-pixel request the page would then draw at full screen.
     */
    @Test
    fun `an unmeasured window asks for no cap`() {
        assertNull(backdropDecodeWidthPx(0, InfoBackdropBlur.SOFT))
        assertNull(backdropDecodeWidthPx(-1, InfoBackdropBlur.HEAVY))
    }

    @Test
    fun `the cap is never smaller than one pixel`() {
        assertTrue(backdropDecodeWidthPx(1, InfoBackdropBlur.HEAVY)!! >= 1)
        assertTrue(backdropDecodeWidthPx(2, InfoBackdropBlur.HEAVY)!! >= 1)
    }
}

/**
 * The wiring, read off the source.
 *
 * The warm read is invisible when it works and invisible when it is deleted: the
 * app still opens, it just opens the way it did before - which is exactly the
 * regression nothing else in the build would catch. These pin the three calls that
 * have to exist for the read to reach a screen.
 */
class StartupWarmupWiringTest {

    private val root = findProjectRoot()

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `the warm read starts at process start`() {
        val app = source("app/src/main/java/com/anisequel/AniSequelApplication.kt")

        assertTrue(
            "Application.onCreate no longer starts StartupWarmup, so every launch " +
                "read is back on the critical path, one after another",
            app.contains("StartupWarmup.begin(")
        )
    }

    @Test
    fun `the launch gate is seeded from the warm read`() {
        val activity = source("app/src/main/java/com/anisequel/MainActivity.kt")

        assertTrue(
            "the theme's initial value is no longer seeded, so the first frame draws " +
                "the default theme before the stored one arrives",
            activity.contains("StartupWarmup.themeSettings")
        )
        assertTrue(
            "the interface settings are no longer seeded, so a custom display density " +
                "still re-lays-out the whole app one frame in",
            activity.contains("StartupWarmup.interfaceSettings")
        )
        assertTrue(
            "the app-lock answer is no longer seeded, so a launch with the lock off " +
                "draws the placeholder for a frame before the stored 'off' arrives",
            activity.contains("StartupWarmup.appLockEnabled")
        )
        assertTrue(
            "the session is no longer seeded, so the navigation graph waits a frame " +
                "for a token the process already read",
            activity.contains("seedAuthState(")
        )
    }

    @Test
    fun `the first-run answer is seeded too`() {
        val navigation = source("app/src/main/java/com/anisequel/ui/navigation/AppNavigation.kt")

        assertTrue(
            "the first-run answer is no longer seeded, so AppNavigation draws its own " +
                "placeholder for a frame on every launch even after the warm read",
            navigation.contains("StartupWarmup.onboardingCompleted")
        )
    }

    private fun findProjectRoot(): File {
        var candidate = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(candidate, "gradle.properties").isFile) {
            candidate = candidate.parentFile ?: error("could not locate the project root")
        }
        return candidate
    }
}
