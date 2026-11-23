package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level guards for the Library's two grids, which are what a type switch
 * between the anime and manga halves breaks.
 *
 * The failure it pins: scrolling in one half, switching to the other, switching
 * back quickly, and finding covers drawn across each other and over the shelf
 * header. Two things were being asked of one lazy layout, and neither is visible
 * to a JVM unit test as behaviour - there is no Compose test runtime in this
 * source set (`androidx.compose.ui.test.junit4` is `androidTestImplementation`
 * only), so the structural requirements are pinned the way
 * [com.anisequel.AnimationWiringTest] pins its own.
 *
 * 1. **A type switch must get a fresh lazy layout.** The layout keeps per-key
 *    animation and measurement state - the fade a removed key is playing, the
 *    offsets placement springs are travelling between, the height it measured a
 *    cell for. A switch removes every key that state is about, so handing the new
 *    list to the same layout draws the outgoing posters over the incoming cells
 *    and leaves rows at the other list's heights until something else forces a
 *    remeasure. Leaving the page and coming back "fixed" it because that is also
 *    a fresh layout. `key(...)` around the grid is that reset.
 * 2. **The scroll reset must not race the swap.** It used to run from a
 *    `LaunchedEffect(type)` calling the suspending `scrollToItem(0)`, which runs
 *    on the frame *after* the one that replaced the item set, and can be
 *    cancelled outright by a second tap. The grid therefore laid the new list out
 *    at the old list's index first and was then jumped, with a spring on every
 *    cell. `requestScrollToItem` is not a coroutine and is applied by the same
 *    measure pass that lays the new list out.
 * 3. **The animations are still there for the changes they belong to.** A filter,
 *    a sort or a refresh keeps most keys; deleting `animateItem` instead of keying
 *    the layout would also pass 1 and 2 and would take that away.
 */
class LibrarySwitchStabilityTest {

    private val novaGrid = source("ui/screens/library/NovaLibraryCards.kt")
    private val novaScreen = source("ui/screens/library/NovaLibraryScreen.kt")
    private val classicTab = source("ui/screens/profile/ProfileLibraryTab.kt")

    @Test
    fun `the new look's grid is rebuilt for each media type`() {
        assertTrue(
            "the anime and manga grids must not share one lazy layout's state",
            novaGrid.contains("key(isManga) {")
        )
        assertTrue(
            "the keyed block has to contain the grid, not sit beside it",
            novaGrid.indexOf("key(isManga) {") < novaGrid.indexOf("LazyVerticalGrid(")
        )
    }

    @Test
    fun `the classic grid is rebuilt for each type scope`() {
        assertTrue(
            "ANIME, MANGA and ALL are three different key sets",
            classicTab.contains("key(type) {")
        )
        assertTrue(
            "the keyed block has to contain the grid, not sit beside it",
            classicTab.indexOf("key(type) {") < classicTab.indexOf("LazyVerticalGrid(")
        )
    }

    @Test
    fun `a type switch resets the scroll without suspending`() {
        listOf("new look" to novaScreen, "classic" to classicTab).forEach { (name, code) ->
            assertTrue(
                "$name: the reset must be applied by the next measure pass",
                code.contains("requestScrollToItem(0)")
            )
            assertFalse(
                "$name: a coroutine scroll runs a frame late and can be cancelled",
                code.contains("scrollToItem(")
            )
        }
    }

    @Test
    fun `a list that keeps most of its keys still animates`() {
        assertTrue(
            "filtering and sorting must still slide survivors into place",
            novaGrid.contains("Modifier.animateItem(")
        )
        assertTrue(
            "the classic grid shares the same behaviour",
            classicTab.contains("Modifier.animateItem(")
        )
    }

    private fun source(path: String): String = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/$path"
    ).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

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
