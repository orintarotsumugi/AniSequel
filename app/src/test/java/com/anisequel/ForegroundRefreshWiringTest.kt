package com.anisequel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Which resume re-buys the anime list, and which one only re-arms the timer.
 *
 * ## The bug this pins shut
 *
 * The freshness check was a `LifecycleResumeEffect` in `DashboardScreen`, on the
 * reasoning that it was "tied to the Activity rather than to this destination".
 * It is not: inside a `NavHost` the composition's lifecycle owner is the
 * destination's `NavBackStackEntry`, and an entry reports `ON_RESUME` every time
 * the reader returns to its page. So the check ran after every visit to Settings,
 * the Library, a title page or a sheet.
 *
 * With the default 30-minute interval that costs nothing, which is why it went
 * unnoticed. On `Always` - a zero-length window that is overdue the instant it is
 * measured - every one of those returns re-downloaded the whole collection, the
 * app's most expensive query, for a list the reader had not touched. It read as
 * the app fetching the API again whenever Settings was opened and closed.
 *
 * The fix is not a flag or a cooldown in the check. It is that the two events are
 * now different ones: the *app* coming back (the Activity's lifecycle, read at the
 * root, handed down) runs the check, and the *page* coming back (a disposal and a
 * recomposition) only arms the timer - whose first wait is "how long until this
 * list is due", so a list that genuinely aged out still refreshes at once.
 */
class ForegroundRefreshWiringTest {

    private val root = findProjectRoot()

    /** Comments stripped: half of these sentences name the call they replaced. */
    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val screen =
        "app/src/main/java/com/anisequel/ui/screens/dashboard/DashboardScreen.kt"
    private val viewModel = "app/src/main/java/com/anisequel/ui/viewmodel/DashboardViewModel.kt"

    @Test
    fun `the app's resume runs the check, keyed on a flag read at the root`() {
        val code = source(screen)
        assertTrue(
            "the dashboard no longer reacts to the app's own foreground state, so an " +
                "app reopened on a stale list never notices",
            code.contains("LaunchedEffect(dashboardViewModel, appResumed)")
        )
        assertTrue(
            "the foreground effect no longer checks the list",
            code.contains("dashboardViewModel.onForegrounded()")
        )
        assertFalse(
            "the freshness check is driven by this destination's lifecycle again - " +
                "that fires on every return to the page, which is the Settings " +
                "round trip re-downloading the list",
            code.contains("LifecycleResumeEffect(dashboardViewModel")
        )
        val nav = source("app/src/main/java/com/anisequel/ui/navigation/AppNavigation.kt")
        assertTrue(
            "the foreground flag is no longer read where the lifecycle owner is the " +
                "Activity",
            nav.contains("val appResumed = rememberIsResumed()")
        )
        assertTrue(
            "the root no longer hands the app's foreground state to the dashboard",
            nav.contains("appResumed = appResumed")
        )
    }

    @Test
    fun `the page's return arms the timer without checking`() {
        val code = source(screen)
        assertTrue(
            "composing the page no longer arms the freshness timer",
            code.contains("dashboardViewModel.onShown()")
        )
        assertTrue(
            "leaving the page no longer stops the timer, so it spends requests on a " +
                "screen nobody is looking at",
            code.contains("onDispose { dashboardViewModel.onHidden() }")
        )

        val vm = source(viewModel)
        assertTrue(
            "coming back to the page checks staleness again, which is the re-fetch " +
                "on every return to Home",
            vm.contains("freshnessWatch.start(checkDue = false)")
        )
    }

    @Test
    fun `every dashboard route states whether the app is in the foreground`() {
        val graph = source("app/src/main/java/com/anisequel/ui/navigation/DashboardNavGraph.kt")
        assertEquals(
            "a dashboard route was added without the app's foreground flag, so its " +
                "list never refreshes on an app resume",
            3,
            Regex("""appResumed = appResumed""").findAll(graph).count()
        )
        assertFalse(
            "the flag is given a default the caller can forget; a constant `true` " +
                "fires once per composition, which is the page-resume bug again",
            graph.contains("appResumed: Boolean = ")
        )
    }

    private fun findProjectRoot(): File {
        var candidate = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(candidate, "gradle.properties").isFile) {
            candidate = candidate.parentFile ?: error("could not locate project root")
        }
        return candidate
    }
}
