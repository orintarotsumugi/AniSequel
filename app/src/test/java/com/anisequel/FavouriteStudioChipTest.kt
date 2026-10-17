package com.anisequel

import com.anisequel.data.network.GraphQLQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The pinned studios on a profile are the one favourites row that went nowhere.
 *
 * ## What was broken
 *
 * `FavouriteStudioRow` drew each studio as a `Text` inside a tinted capsule - a chip
 * that is indistinguishable from a control and does nothing when tapped. Every other
 * favourites row on the profile opens something: anime and manga open the detail sheet,
 * characters and staff hand off to the browser, and studios were the only row whose
 * answer was silence.
 *
 * A studio somebody has pinned is the strongest statement a profile makes about the
 * kind of thing they watch, and the studio page - a whole filmography, shelved by year
 * - already existed and was already the destination the title page's own credits used.
 * So the row was a dead end pointed at a door that was already built.
 *
 * ## Why this is a test and not a note
 *
 * The failing edit is a `Text` becoming a `Row` with a `clickable`, threaded through
 * three files as a new lambda parameter. Nothing about that diff looks like it could
 * silently break, and the most likely way it *does* break later is the quiet one: a
 * new caller of `FavouriteStudioRow` that omits `onOpenStudio` and gets the default,
 * which is a no-op, and renders a dead chip again. The default is deliberate - a
 * profile row must not crash - so the wiring is what has to be pinned.
 */
class FavouriteStudioChipTest {

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

    private val components = source("com/anisequel/ui/screens/profile/ProfileComponents.kt")
    private val homeTab = source("com/anisequel/ui/screens/profile/ProfileHomeTab.kt")
    private val screen = source("com/anisequel/ui/screens/profile/UserProfileScreen.kt")

    @Test
    fun `the chip takes the studio's id and hands it on`() {
        assertTrue(
            "the row has to accept the same `(Int, String?)` pair the Info page's own " +
                "studio credits use, so one definition of \"open this studio\" reaches " +
                "one route",
            components.contains("onOpenStudio: (Int, String?) -> Unit")
        )
        assertTrue(
            "and the id has to reach the callback - a chip that only knows the name " +
                "cannot address a studio page, which is fetched by id",
            components.contains("onOpenStudio(id, label)")
        )
    }

    @Test
    fun `a chip with no id is drawn as text rather than as a dead button`() {
        // AniList's favourites always carry an id; a fixture need not, and a chip that
        // looks tappable and is not is worse than one that plainly is not.
        assertTrue(
            "the clickable has to be gated on the id existing, the same rule the title " +
                "page's own credits follow",
            components.contains("if (id != null)")
        )
    }

    @Test
    fun `the home tab forwards the callback`() {
        assertTrue(
            "`ProfileHomeTab` has to accept it...",
            homeTab.contains("onOpenStudio: (Int, String?) -> Unit")
        )
        assertTrue(
            "...and hand it to the row",
            homeTab.contains("onOpenStudio = onOpenStudio")
        )
    }

    @Test
    fun `the screen threads the callback from its own parameter to the tab`() {
        assertTrue(
            "`UserProfileScreen` is the only place that knows the route, so the lambda " +
                "has to be a parameter there rather than built inside the tab",
            screen.contains("onOpenStudio: (Int, String?) -> Unit")
        )
        assertTrue(
            "and it has to reach `ProfileTabsContent`",
            screen.contains("onOpenStudio = onOpenStudio")
        )
        assertTrue(
            "and the Home tab inside it",
            screen.contains("onOpenStudio = onOpenStudio,")
        )
    }

    /**
     * The one that actually closes the gap.
     *
     * The chain is `ProfileDestination` (which owns the `NavController`) ->
     * `UserProfileScreen` -> `ProfileTabsContent` -> `ProfileHomeTab` -> the row.
     * A break anywhere leaves a chip that renders perfectly and does nothing, which is
     * exactly how this row behaved before.
     */
    @Test
    fun `the route actually routes`() {
        val destination = source("com/anisequel/ui/navigation/ProfileDestination.kt")

        assertTrue(
            "`ProfileDestination` is the only holder of the NavController, so it is the " +
                "only place a studio hop can be turned into navigation",
            destination.contains("StudioRoutes.studio(")
        )
    }

    /**
     * A pinned chip opens that studio's page, so the overview has to carry the two
     * fields the page leads with.
     *
     * It asked for `id name isAnimationStudio` alone. `favourites` is the studio page's
     * own headline number and `siteUrl` is the link its bar falls back to, so both were
     * being re-fetched on arrival - which breaks the "one request to look at a profile,
     * more only if the reader taps something" bargain the overview is built to keep.
     */
    @Test
    fun `favourite studios carry the fields their page leads with`() {
        val studios = GraphQLQueries.GET_USER_OVERVIEW.substringAfter("studios(perPage: 12)")

        listOf("id", "name", "isAnimationStudio", "favourites", "siteUrl").forEach { field ->
            assertTrue(
                "the pinned studio chips open that studio's page, so the overview has " +
                    "to carry `$field` rather than re-fetching it on arrival:\n$studios",
                studios.contains(field)
            )
        }
    }

    /**
     * Braces and parens balanced.
     *
     * Widening a selection is how a document goes malformed, and the symptom would be
     * an AniList 400 on the *overview* - five tabs and a header, not one row.
     */
    @Test
    fun `the overview query is still balanced`() {
        val query = GraphQLQueries.GET_USER_OVERVIEW
        assertEquals("braces", query.count { it == '{' }, query.count { it == '}' })
        assertEquals("parens", query.count { it == '(' }, query.count { it == ')' })
    }
}