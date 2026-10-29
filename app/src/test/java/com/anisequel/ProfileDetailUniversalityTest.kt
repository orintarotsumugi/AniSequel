package com.anisequel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A title or a person opens the same way whichever tab the tap came from.
 *
 * ## What was broken
 *
 * The detail sheet's on-demand fetch had four owners - the dashboard, the activity
 * feed, the studio page and the Library - so opening one title from two tabs was
 * two requests, two caches and two loading flags. Worse, the profile's pinned
 * characters and staff were the only faces in the app that handed their tap to the
 * browser: the same person opened a sheet from a title's cast row and an AniList
 * page from a profile.
 *
 * ## What it does now
 *
 * One `MediaDetailHost` is remembered at the navigation root and passed to every
 * non-dashboard route, and the pinned people rows call the profile store the cast
 * rows already used. The dashboard keeps its own store because it also folds each
 * answer into the recommendation cards behind the sheet; that is an addition to
 * the one rule, not a second one.
 *
 * ## Why this is a source-level test
 *
 * The unit-test source set has no Compose harness, and the failure is not in the
 * drawing - it is in which store and which callback the drawing is handed. The
 * same approach as `RelationDetailResolutionTest` and `FavouriteStudioChipTest`.
 * The data half of the same bug - a favourite that arrived without its media type -
 * is pinned by `UserOverviewQueryTest` and `MediaNodeDisplayTest`.
 */
class ProfileDetailUniversalityTest {

    private fun source(relative: String): String {
        val file = File(findProjectRoot(), "app/src/main/java/$relative")
        assertTrue("could not read ${file.path}", file.isFile)
        return file.readText()
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

    private val navigation = source("com/anisequel/ui/navigation/AppNavigation.kt")
    private val profileDestination = source("com/anisequel/ui/navigation/ProfileDestination.kt")
    private val profileScreen = source("com/anisequel/ui/screens/profile/UserProfileScreen.kt")
    private val homeTab = source("com/anisequel/ui/screens/profile/ProfileHomeTab.kt")

    /**
     * The route call sites, read from every `*NavGraph.kt` builder rather than
     * `AppNavigation.kt` alone: that file was split into per-destination graph
     * files, and a test that names one file fails on the move instead of on a
     * route losing the shared host - which is what it is here to catch.
     */
    private val navigationRoutes: String = run {
        val dir = File(findProjectRoot(), "app/src/main/java/com/anisequel/ui/navigation")
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith("NavGraph.kt") }.orEmpty()
        assertTrue("could not read the navigation graphs at ${dir.path}", files.isNotEmpty())
        files.sortedBy { it.name }.joinToString("\n") { it.readText() }
    }

    @Test
    fun `one detail host serves every non-dashboard route`() {
        // Counted rather than merely present: a second `rememberMediaDetailHost` in a
        // route is exactly the regression - a route-local store re-fetches a title
        // the app already has, and the two can disagree about what it contains.
        assertEquals(
            "the app must remember exactly one detail host, at the navigation root",
            1,
            Regex("""rememberMediaDetailHost\(aniListRepository\)""")
                .findAll(navigation)
                .count()
        )

        listOf("LibraryScreen(", "ProfileDestination(", "StudioScreen(").forEach { route ->
            val call = navigationRoutes.substringAfter(route, missingDelimiterValue = "").take(700)
            assertTrue(
                "$route is not handed the shared detail host; without it the route " +
                    "falls back to a store of its own and the same title opens as a " +
                    "different page there",
                call.contains("detailHost = detailHost")
            )
        }

        assertTrue(
            "ProfileDestination has to forward the host to the screen it builds",
            profileDestination.contains("detailHost = detailHost")
        )
    }

    @Test
    fun `the profile's sheet loads through the shared host`() {
        assertTrue(
            "the profile reads the fetched detail from the shared store",
            profileScreen.contains("val detailNodes by detailHost.details")
        )
        assertTrue(
            "and fires the fetch there too, so the Library's cache is the same cache",
            profileScreen.contains("onLoadDetail = detailHost.onLoadDetail")
        )
        assertFalse(
            "the activity ViewModel no longer owns a detail store; using it again is " +
                "the second cache this replaced",
            profileScreen.contains("activityViewModel.loadDetail")
        )
    }

    @Test
    fun `pinned characters and staff open the in-app profile sheet`() {
        assertTrue(
            "the Home tab has to accept the person callbacks",
            homeTab.contains("onOpenCharacter: ((Int) -> Unit)?")
        )
        assertTrue(homeTab.contains("onOpenStaff: ((Int) -> Unit)?"))
        assertTrue(
            "a pinned character opens by id, which is what the profile sheet fetches",
            homeTab.contains("open(character.id)")
        )
        assertTrue(
            "the same for staff",
            homeTab.contains("open(member.id)")
        )
        assertTrue(
            "the screen supplies the profile store's callbacks",
            profileScreen.contains("onOpenCharacter = profiles.onOpenCharacter")
        )
        assertTrue(profileScreen.contains("onOpenStaff = profiles.onOpenStaff"))
        assertFalse(
            "the pinned rows used to hand the tap straight to the browser, so the " +
                "same face opened a sheet from a title and a web page from a profile",
            homeTab.contains("onOpen = { url -> url?.let { openExternalUrl(context, it) } }")
        )
    }
}
