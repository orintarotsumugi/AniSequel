package com.anisequel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * How R8 is allowed to run.
 *
 * ## What went wrong
 *
 * A release build is R8 plus a few minutes of everything else, and R8's own
 * wall time was found swinging between 80 seconds and 18 minutes across runs
 * that built the same code with the same task graph - see the comment in
 * `settings.gradle.kts` for the four measured runs. R8 is the biggest allocation
 * source in the build and it used to share the Gradle daemon's heap with the
 * in-process Kotlin compiler, so how long it took depended on how much heap was
 * left over from the compile in front of it rather than on what it was asked to
 * do.
 *
 * ## Why these are tests
 *
 * Every way to get the twenty minutes back that a reader is likely to try leaves
 * a green build: turning `isMinifyEnabled` off, taking the non-optimizing default
 * proguard file, adding `-dontoptimize` to the shipped rule set. They all ship an
 * APK that is larger than the one that was tested, and nothing fails. So the
 * settings that make the release build *both* fast and honest are pinned here
 * rather than left to be reasoned about again.
 */
class R8BuildConfigTest {

    private val root: File = findProjectRoot()

    private val settingsScript: String = File(root, "settings.gradle.kts").readText()
    private val buildScript: String = File(root, "app/build.gradle.kts").readText()
    private val catalogs: String = File(root, "gradle/libs.versions.toml").readText()

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

    /** `key = "1.2.3"` from the `[versions]` block of the version catalogue. */
    private fun catalogueVersion(key: String): String? =
        Regex("""^\s*$key\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
            .find(catalogs)
            ?.groupValues
            ?.get(1)

    /**
     * The settings plugin is the same AGP under a settings-plugin marker, and it
     * is declared on its own line because a settings script cannot read the
     * version catalogue. A mismatch is not a build failure: it resolves, and the
     * execution profile is then read by a different AGP than the one doing the
     * R8 run it configures.
     */
    @Test
    fun `the settings plugin runs the same AGP version as the modules`() {
        val agp = catalogueVersion("agp")
        assertNotNull("libs.versions.toml has no `agp` version", agp)

        val settingsVersion = Regex("""id\("com\.android\.settings"\)\s*version\s*"([^"]+)"""")
            .find(settingsScript)
            ?.groupValues
            ?.get(1)

        assertNotNull(
            "settings.gradle.kts no longer applies com.android.settings, so the R8 " +
                "execution profile below cannot be declared",
            settingsVersion
        )
        assertEquals(
            "com.android.settings in settings.gradle.kts must be the same version as `agp` " +
                "in gradle/libs.versions.toml",
            agp,
            settingsVersion
        )
    }

    /**
     * The profile itself. `runInSeparateProcess` is the half that stops R8
     * sharing the daemon's heap; the `-Xmx` is the half that makes its ceiling
     * something written down.
     */
    @Test
    fun `R8 runs out of process with its own heap`() {
        assertTrue(
            "settings.gradle.kts must keep `runInSeparateProcess = true`: R8 sharing the " +
                "Gradle daemon's heap is what turned a 2-minute R8 run into an 18-minute one",
            Regex("""runInSeparateProcess\s*=\s*true""").containsMatchIn(settingsScript)
        )
        assertTrue(
            "the R8 execution profile must set its own heap (`jvmOptions.addAll(listOf(\"-Xmx...\"))`)",
            Regex("""jvmOptions[\s\S]{0,120}-Xmx\d+""").containsMatchIn(settingsScript)
        )
    }

    /**
     * The release variant still shrinks, obfuscates and optimizes. Removing any
     * of the three makes R8 fast and the APK wrong, which is why this is pinned
     * in the same file as the profile that makes it fast and correct.
     */
    @Test
    fun `the release variant still shrinks, optimizes and drops unused resources`() {
        assertTrue(
            "release must keep `isMinifyEnabled = true`",
            Regex("""isMinifyEnabled\s*=\s*true""").containsMatchIn(buildScript)
        )
        assertTrue(
            "release must keep `isShrinkResources = true`",
            Regex("""isShrinkResources\s*=\s*true""").containsMatchIn(buildScript)
        )
        assertTrue(
            "release must build its proguard rules on `proguard-android-optimize.txt`; the " +
                "non-optimize file keeps every class it reaches",
            buildScript.contains("proguard-android-optimize.txt")
        )
    }

    /**
     * `-dontoptimize` exists for exactly one caller - a throwaway build that only
     * has to prove the keep rules produce a working APK - and it lives in its own
     * file so it cannot be reached by the rule set a release ships. Nothing else
     * picks that file up, and the shipped one must never grow the flag.
     */
    @Test
    fun `only the throwaway rule set turns optimization off`() {
        val shipped = File(root, "app/proguard-rules.pro").readText()
        assertTrue(
            "app/proguard-rules.pro is what a release ships; `-dontoptimize` there would " +
                "strip the whole-program optimization passes from every published APK",
            !shipped.contains("-dontoptimize")
        )

        val throwaway = File(root, "app/proguard-fast-rules.pro")
        assertTrue(
            "app/proguard-fast-rules.pro is referenced by app/build.gradle.kts but is missing",
            throwaway.isFile
        )
        assertTrue(
            "app/proguard-fast-rules.pro must be the file that carries `-dontoptimize`",
            throwaway.readText().contains("-dontoptimize")
        )
    }
}
