package com.anisequel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Anything the app does at runtime on a version of Android starts with an
 * `SDK_INT` comparison, and every `minSdk` raise silently retires some of them.
 *
 * ## What this caught
 *
 * `NetworkClient.createImageLoader` registered Coil's animated-image decoders
 * with:
 *
 * ```kotlin
 * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
 *     add(ImageDecoderDecoder.Factory())
 * }
 * add(GifDecoder.Factory())
 * ```
 *
 * `ImageDecoder` arrived in API 28 and `GifDecoder` (from `coil-gif`) was the
 * fallback below it. `minSdk` is 31, so the condition is true on every device
 * this app can be installed on: the fallback could not run, and it still cost
 * every APK the `coil-gif` artifact. This is the failure mode AGENTS.md §2 names
 * - "a guard that reads as load-bearing and cannot fail is worse than no guard,
 * because the next reader assumes there is a device class it excludes" - and
 * nothing in the repository reported it. It was found by reading the file.
 *
 * ## Why it is a test and not a note
 *
 * The edit that produced it was `minSdk = 29` → `minSdk = 31`, which looks like
 * a number in a build file. The dead branch was 200 lines away in another
 * directory, its removal was invisible in that diff, and the cost was framed as
 * a size win that no review of the version bump would connect to it.
 *
 * ## What it does not check
 *
 * - Comparisons on something other than `Build.VERSION.SDK_INT` - a value read
 *   into a local first, or a `when (SDK_INT) { in 24..30 -> }` range, which has
 *   no comparison operator for this to read. The `>=`/`>`/`<=`/`<` forms cover
 *   every one that exists in this codebase today; a range would need adding
 *   here deliberately.
 * - Branches that are constant because the *upper* bound moved - `SDK_INT > 99`
 *   is false everywhere, but knowing that means knowing the newest API level
 *   the app will ever run on, which is a guess about the future.
 *
 * Reversed comparisons (`Build.VERSION_CODES.P <= Build.VERSION.SDK_INT`) *are*
 * read, by flipping the operator, because writing them the other way round is a
 * style choice rather than an exotic shape - and a guard that silently skips the
 * form it does not expect is the failure this file exists to prevent.
 */
class MinimumSdkReachabilityTest {

    private val root: File = findProjectRoot()

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }

    /**
     * The API level behind each `Build.VERSION_CODES` constant, up to the
     * highest one this project could compile against.
     *
     * A table rather than a clever parse of `android.jar`: the point of the test
     * is to be readable next to the branches it judges. A constant that is not
     * in here fails the test rather than being ignored, so a new one has to be
     * added deliberately.
     */
    private val apiLevels = mapOf(
        "BASE" to 1, "BASE_1_1" to 2, "CUPCAKE" to 3, "DONUT" to 4,
        "ECLAIR" to 5, "ECLAIR_0_1" to 6, "ECLAIR_MR1" to 7, "FROYO" to 8,
        "GINGERBREAD" to 9, "GINGERBREAD_MR1" to 10, "HONEYCOMB" to 11,
        "HONEYCOMB_MR1" to 12, "HONEYCOMB_MR2" to 13, "ICE_CREAM_SANDWICH" to 14,
        "ICE_CREAM_SANDWICH_MR1" to 15, "JELLY_BEAN" to 16, "JELLY_BEAN_MR1" to 17,
        "JELLY_BEAN_MR2" to 18, "KITKAT" to 19, "KITKAT_WATCH" to 20,
        "LOLLIPOP" to 21, "LOLLIPOP_MR1" to 22, "M" to 23, "N" to 24, "N_MR1" to 25,
        "O" to 26, "O_MR1" to 27, "P" to 28, "Q" to 29, "R" to 30, "S" to 31,
        "S_V2" to 32, "TIRAMISU" to 33, "UPSIDE_DOWN_CAKE" to 34,
        "VANILLA_ICE_CREAM" to 35, "BAKLAVA" to 36
    )

    /** `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P`, and the numeric form. */
    private val forward = Regex(
        """Build\.VERSION\.SDK_INT\s*(>=|<=|>|<)\s*(Build\.VERSION_CODES\.([A-Z0-9_]+)|\d+)"""
    )

    /** The same question asked the other way round: `28 <= Build.VERSION.SDK_INT`. */
    private val reversed = Regex(
        """(Build\.VERSION_CODES\.([A-Z0-9_]+)|\d+)\s*(>=|<=|>|<)\s*Build\.VERSION\.SDK_INT"""
    )

    /** One comparison, normalised to "SDK_INT <op> level". */
    private data class Comparison(val op: String, val level: Int, val text: String)

    private fun apiLevel(constantName: String, rawOperand: String, where: String, line: String): Int =
        if (constantName.isNotEmpty()) {
            apiLevels[constantName] ?: error(
                "Build.VERSION_CODES.$constantName is not in this test's table, so the " +
                    "comparison at $where could not be judged:\n  $line\n" +
                    "Add the constant and its API level to `apiLevels` - do not delete it " +
                    "from the scan."
            )
        } else {
            rawOperand.toIntOrNull() ?: error(
                "could not read the API level of the comparison at $where:\n  $line"
            )
        }

    private fun comparisonsIn(line: String, where: String): List<Comparison> {
        val found = mutableListOf<Comparison>()

        for (match in forward.findAll(line)) {
            val (op, rawOperand, constantName) = match.destructured
            found.add(Comparison(op, apiLevel(constantName, rawOperand, where, line), match.value))
        }

        for (match in reversed.findAll(line)) {
            val (rawOperand, constantName, op) = match.destructured
            // `28 <= SDK_INT` is `SDK_INT >= 28`. Without the flip the level would
            // be read against the wrong side and a dead branch would look alive.
            val flipped = when (op) {
                ">=" -> "<="
                "<=" -> ">="
                ">" -> "<"
                else -> ">"
            }
            found.add(Comparison(flipped, apiLevel(constantName, rawOperand, where, line), match.value))
        }

        return found
    }

    private fun minSdk(): Int {
        val text = File(root, "app/build.gradle.kts").readText()
        return Regex("""minSdk\s*=\s*(\d+)""").find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: error(
                "could not read `minSdk = <n>` out of app/build.gradle.kts. This test " +
                    "exists to catch dead version branches, so it has to fail when it can " +
                    "no longer find the floor it judges them against."
            )
    }

    private fun kotlinSources(): List<File> =
        File(root, "app/src/main")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    /**
     * Source with comments removed.
     *
     * These files explain the *deleted* branches in prose, and the prose quotes
     * the old code - `SDK_INT >= P` appears in a comment in `UpdateManager.kt`
     * and in a KDoc block here. Reading those as code would report a branch that
     * has already been removed, which is the same trap `KeystoreIntegrityTest`
     * documents for the workflow's shell comments.
     */
    private fun withoutComments(source: String): String =
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lineSequence()
            .joinToString("\n") { line -> line.substringBefore("//") }

    private data class Branch(val file: String, val line: Int, val text: String, val reason: String)

    private fun deadBranches(): List<Branch> {
        val floor = minSdk()
        val dead = mutableListOf<Branch>()

        for (file in kotlinSources()) {
            val lines = withoutComments(file.readText()).lines()
            lines.forEachIndexed { index, line ->
                val where = "${file.name}:${index + 1}"
                for (comparison in comparisonsIn(line, where)) {
                    val level = comparison.level
                    val op = comparison.op

                    // `SDK_INT >= 31` is true on every supported device. So is
                    // `SDK_INT > 30`. Below the floor, `< 31` and `<= 30` are
                    // false everywhere. Everything else is a real question.
                    val alwaysTrue = (op == ">=" && level <= floor) ||
                        (op == ">" && level < floor)
                    val alwaysFalse = (op == "<" && level <= floor) ||
                        (op == "<=" && level < floor)

                    if (alwaysTrue || alwaysFalse) {
                        dead.add(
                            Branch(
                                file = file.relativeTo(root).path,
                                line = index + 1,
                                text = comparison.text,
                                reason = if (alwaysTrue) {
                                    "this condition is true on every device the app runs on " +
                                        "(API $level vs minSdk $floor), so the branch it guards " +
                                        "is not a branch"
                                } else {
                                    "this condition is false on every device the app runs on " +
                                        "(API $level vs minSdk $floor), so the code it guards " +
                                        "can never run"
                                }
                            )
                        )
                    }
                }
            }
        }
        return dead
    }

    /**
     * `minSdk` is a deletion, not a number (AGENTS.md §2).
     *
     * When this fails, delete the dead branch and whatever only existed for it -
     * including a dependency that was only holding up the unreachable side, the
     * way `coil-gif` was. Suppressing the comparison is the wrong repair: the
     * point is that the next reader cannot tell a live guard from a dead one.
     */
    @Test
    fun `no version branch is decided before the app starts`() {
        val dead = deadBranches()

        assertTrue(
            "These `Build.VERSION.SDK_INT` branches can no longer go both ways at " +
                "minSdk ${minSdk()}:\n" +
                dead.joinToString("\n") { "  ${it.file}:${it.line} - ${it.text}\n      ${it.reason}" } +
                "\n\nDelete the branch and its fallback copy rather than leaving it to " +
                "read as load-bearing. See AGENTS.md §2.",
            dead.isEmpty()
        )
    }
}
