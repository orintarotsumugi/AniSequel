package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the one packaging setting that decides how big the shipped APK is.
 *
 * ## What went wrong
 *
 * v1.0.16 shipped a 2.31 MB APK. v1.0.17 shipped 4.15 MB, an 80% jump in one
 * release, and it stayed there through v1.0.18.
 *
 * It was not the code getting bigger. Comparing the two APKs entry by entry,
 * `classes.dex` had in fact *shrunk* - 4,028,848 uncompressed bytes down to
 * 3,953,880 - and every other entry was within noise. What changed was how it
 * was stored: at v1.0.16 the dex was DEFLATEd into 2,018,462 bytes in the APK,
 * and at v1.0.17 it was stored verbatim at 3,953,880. That single flip accounts
 * for the entire increase.
 *
 * The cause was `minSdk` being raised from 24 to 29. From Android Gradle Plugin's
 * documented behaviour, dex files are left uncompressed when `minSdk >= 28` so
 * the runtime can map them straight out of the APK instead of extracting them.
 * Raising the floor therefore silently opted this project into shipping 3.95 MB
 * of uncompressed dex.
 *
 * ## Why this is worth a test
 *
 * Nothing about that change looks like a size regression in review: raising
 * minSdk is a deliberate, well-reasoned platform decision, and the comment
 * explaining it never mentions the APK. The effect was only visible by
 * comparing published release sizes, which nobody does routinely. A test that
 * fails when the setting is removed is the only thing that reports it at the
 * moment it happens.
 *
 * `useLegacyPackaging` is not free - compressed dex means the installer
 * decompresses it into a second copy on disk, so the installed footprint grows
 * and installing takes marginally longer. That is the right way round for an app
 * distributed as a sideloaded APK, where the download is what every user waits
 * on. Whoever flips this should be sure they still want that trade.
 */
class ApkPackagingTest {

    private val buildScript: String =
        File(findProjectRoot(), "app/build.gradle.kts").readText()

    /**
     * The Gradle test task runs with the project directory as the working
     * directory, but resolve upward anyway so the test does not depend on which
     * module is running it.
     */
    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }

    @Test
    fun `dex is packed compressed so the APK stays near its previous size`() {
        assertTrue(
            "app/build.gradle.kts must keep dex.useLegacyPackaging = true. Without " +
                "it AGP stores classes.dex uncompressed and the APK grows from " +
                "about 2.3 MB to about 4.2 MB, which is what happened when " +
                "minSdk moved to 29.",
            buildScript.contains("useLegacyPackaging = true")
        )
    }

    /**
     * The setting only has an effect inside `packaging { dex { ... } }`.
     *
     * `useLegacyPackaging` also exists under `jniLibs`, where it means something
     * entirely different - compressing native libraries, which is the opposite
     * trade. A string match on its own cannot tell the two apart, so this checks
     * it sits in the dex block rather than the native-library one.
     */
    @Test
    fun `the setting is applied to dex rather than to native libraries`() {
        val dexBlock = Regex("""dex\s*\{[^}]*useLegacyPackaging\s*=\s*true""", RegexOption.DOT_MATCHES_ALL)

        assertTrue(
            "useLegacyPackaging = true must be inside packaging { dex { ... } }; " +
                "under jniLibs it compresses native libraries instead, which is a " +
                "different setting entirely",
            dexBlock.containsMatchIn(buildScript)
        )
    }
}