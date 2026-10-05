package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The version the app reports about itself.
 *
 * Settings > About and the "What's new" heading both read `BuildConfig`, which
 * comes from `gradle.properties`. That file was frozen at 1.0.0 for six
 * releases while CI published 1.0.6, because the workflow *derives* the release
 * version from the newest tag rather than reading the file - so nothing ever
 * read it, and nothing failed when it went stale. A locally built APK cheerfully
 * reported a version that does not exist.
 *
 * There are three places the version can be written and all three have to agree:
 * `gradle.properties` (what a local build uses), the fallback constants in
 * `app/build.gradle.kts` (what a build uses if that file cannot be read), and
 * `update.json` (what an updater compares against). Two of those used to be
 * independent copies.
 */
class VersionBaselineTest {

    private val root: File = findProjectRoot()

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

    private fun gradleProperty(key: String): String? =
        File(root, "gradle.properties")
            .readLines()
            .firstOrNull { it.trimStart().startsWith("$key=") }
            ?.substringAfter('=')
            ?.trim()

    private fun buildScriptConstant(name: String): String? =
        Regex("""val\s+$name\s*=\s*"([^"]*)"""")
            .find(File(root, "app/build.gradle.kts").readText())
            ?.groupValues
            ?.get(1)

    private fun buildScriptIntConstant(name: String): String? =
        Regex("""val\s+$name\s*=\s*(\d+)""")
            .find(File(root, "app/build.gradle.kts").readText())
            ?.groupValues
            ?.get(1)

    private fun updateManifestVersion(): String? =
        Regex(""""version"\s*:\s*"([^"]+)"""")
            .find(File(root, "update.json").readText())
            ?.groupValues
            ?.get(1)

    private fun updateManifestVersionCode(): Long? =
        Regex(""""version_code"\s*:\s*(\d+)""")
            .find(File(root, "update.json").readText())
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

    @Test
    fun `the baseline version is well formed`() {
        val name = gradleProperty("anisequelVersionName")

        assertTrue("no anisequelVersionName in gradle.properties", name != null)
        assertTrue(
            "versionName must be major.minor.patch, was '$name'",
            Regex("""^\d+\.\d+\.\d+$""").matches(name!!)
        )
        assertTrue(
            "versionCode must be a positive integer, was " +
                "'${gradleProperty("anisequelVersionCode")}'",
            (gradleProperty("anisequelVersionCode")?.toIntOrNull() ?: 0) > 0
        )
    }

    /**
     * The build script's fallbacks are a *copy* of gradle.properties, because
     * Gradle evaluates project properties before the build script body runs and
     * they cannot be read back out of it. That makes this the pair most likely to
     * drift: someone bumps gradle.properties, the local build still falls back to
     * the old literal, and nothing reports it.
     */
    @Test
    fun `the build script fallbacks match gradle properties`() {
        assertEquals(
            "BASELINE_VERSION_NAME in app/build.gradle.kts must match " +
                "anisequelVersionName, or a local build reports the wrong version",
            gradleProperty("anisequelVersionName"),
            buildScriptConstant("BASELINE_VERSION_NAME")
        )

        assertEquals(
            "BASELINE_VERSION_CODE in app/build.gradle.kts must match " +
                "anisequelVersionCode",
            gradleProperty("anisequelVersionCode"),
            buildScriptIntConstant("BASELINE_VERSION_CODE")
        )
    }

    /**
     * `update.json` is written by the release workflow, not by hand. It is
     * asserted rather than edited here: the check that matters is that the
     * committed manifest names the same version the app is built at, because an
     * updater comparing against a different number never offers an update.
     */
    @Test
    fun `the update manifest agrees with the baseline`() {
        val manifestVersion = updateManifestVersion()
        val baseline = gradleProperty("anisequelVersionName")

        assertTrue("update.json has no version", manifestVersion != null)

        // The manifest is written *after* a release, so it is allowed to be one
        // version ahead of the baseline on a build that just published - but it
        // must never be behind it, which is what a stale manifest looks like.
        val manifestPatch = manifestVersion!!.substringAfterLast('.').toIntOrNull() ?: -1
        val baselinePatch = baseline!!.substringAfterLast('.').toIntOrNull() ?: -1

        assertTrue(
            "update.json (${manifestVersion}) is older than the baseline " +
                "version ($baseline): a stale manifest means an updater would " +
                "never offer the current build",
            manifestPatch >= baselinePatch
        )
    }

    /**
     * The baseline must not fall behind the newest published release.
     *
     * `update.json` is generated by the release workflow and is the file the app
     * actually reads, so it is always current. `gradle.properties` is not: the
     * workflow's manifest step tries to commit the new baseline back to a
     * protected branch with `GITHUB_TOKEN`, which GitHub refuses, so the
     * committed file stays where it was left until a human moves it. That left it
     * four releases behind - 1.0.14 while 1.0.18 was published - with every test
     * in this file green, because "the manifest must not be behind the baseline"
     * says nothing about the other direction.
     *
     * One release of slack is allowed, because a build that has just published is
     * legitimately one step ahead before its baseline commit lands. More than that
     * is drift, and it is what made a local build report a version that has not
     * existed for four releases.
     */
    @Test
    fun `the baseline does not trail the newest published release`() {
        val manifestPatch = updateManifestVersion()!!.substringAfterLast('.').toIntOrNull() ?: -1
        val baselinePatch = gradleProperty("anisequelVersionName")!!
            .substringAfterLast('.').toIntOrNull() ?: -1

        assertTrue(
            "gradle.properties reports $baselinePatch but $manifestPatch is published - " +
                "the baseline trails the newest release by more than the one version the " +
                "manifest sync is allowed to be behind. Local builds and Settings > About " +
                "will report a version that does not exist.",
            manifestPatch - baselinePatch <= 1
        )
    }

    @Test
    fun `the update manifest version code is not behind the baseline`() {
        val manifestCode = updateManifestVersionCode()
        val baselineCode = gradleProperty("anisequelVersionCode")?.toLongOrNull()

        assertTrue("update.json has no version_code", manifestCode != null)
        assertTrue(
            "update.json version_code ($manifestCode) is behind the baseline " +
                "($baselineCode)",
            manifestCode!! >= baselineCode!!
        )
    }
}