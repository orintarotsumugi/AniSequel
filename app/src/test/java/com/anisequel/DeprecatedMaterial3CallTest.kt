package com.anisequel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * The build log stays free of Material3 deprecation warnings.
 *
 * ## Why this is a test and not a cleanup
 *
 * Eighteen of these sat in the release log for months and nothing reported them: a
 * deprecated call compiles, passes every unit test, signs and ships. They accumulated the way
 * that usually goes - one call site migrated to the new API in a pull request, the other six
 * left as they were because the new shape was awkward, and the awkwardness then spread. The
 * two cases that made the awkwardness real are recorded where the code is:
 * [com.anisequel.ui.components.rememberBoundSliderState] exists because Material3's replacement
 * for the value-first `Slider` deliberately ignores later changes to its initial value, steps
 * and range, which is wrong for a slider bound to a draft or a Reset button; and
 * [com.anisequel.ui.components.rememberFullHeightSheetState] exists because
 * `skipPartiallyExpanded` became a *set of stops*, and ten call sites spelling that set by hand
 * is ten chances to spell it differently.
 *
 * So this is the same instrument as [MinimumSdkReachabilityTest] and
 * [KeystoreIntegrityTest]: a property of the source that no runtime can report, checked from
 * the files.
 *
 * ## What it does not check
 *
 * Deprecations outside Material3 - a `androidx.compose` API the pinned BOM marks obsolete but
 * which this list has no entry for will not be caught here. It catches the three that were
 * actually in this project's log, because a scan of every `@Deprecated` symbol in every
 * dependency is a scan of somebody else's changelog.
 */
class DeprecatedMaterial3CallTest {

    private val sources: List<File> by lazy {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("no settings.gradle.kts above the working directory")
        }
        File(dir, "app/src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
    }

    /**
     * [text] without its comments.
     *
     * Needed because this repository's convention is to write the failure that made a change
     * necessary, which means the migrated helpers name the deprecated calls they replaced in
     * their own KDoc. Those sentences are the point of the file; only the code is the contract.
     */
    private fun codeOnly(text: String): String =
        text.lines().filterNot { line ->
            val t = line.trimStart()
            t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
        }.joinToString("\n")

    private fun rel(file: File): String = file.path.substringAfter("app/src/main/java/")

    @Test
    fun `no sheet asks for the retired modal bottom sheet state helper`() {
        sources.forEach { file ->
            val code = codeOnly(file.readText())
            assertFalse(
                "${rel(file)} calls rememberModalBottomSheetState. Use " +
                    "rememberFullHeightSheetState(), or rememberBottomSheetState with the exact " +
                    "set of stops a sheet is measured for.",
                code.contains("rememberModalBottomSheetState(")
            )
        }
    }

    @Test
    fun `no slider passes its value where the state belongs`() {
        sources.forEach { file ->
            val code = codeOnly(file.readText())
            assertFalse(
                "${rel(file)} builds a Slider with valueRange =, which is the deprecated " +
                    "value-first overload. Pass state = rememberBoundSliderState(value, steps, " +
                    "trackRange) so the thumb still follows an external write.",
                code.contains("valueRange")
            )
        }
    }

    @Test
    fun `every transformable handler takes the centroid it is handed`() {
        // The three-argument form is the deprecated one; the replacement puts the pinch's
        // centroid first. A handler that ignores it still compiles and behaves as before, which
        // is why the arity is what is checked rather than anything about the body.
        val threeArg = Regex("""rememberTransformableState\s*\{\s*([^{}/]*?)->""")
        sources.forEach { file ->
            val code = codeOnly(file.readText())
            threeArg.findAll(code).forEach { match ->
                val params = match.groupValues[1].split(",").count { it.isNotBlank() }
                assertEquals(
                    "${rel(file)} remembers a TransformableState with a $params-argument " +
                        "handler; the centroid comes first now:\n  ${match.value.trim()}",
                    4,
                    params
                )
            }
        }
    }
}
