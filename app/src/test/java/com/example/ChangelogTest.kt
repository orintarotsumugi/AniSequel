package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.changelog.Changelog
import com.example.data.changelog.ChangelogRepository
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The bundled release notes.
 *
 * ## Why this is a test and not a convention
 *
 * `assets/changelog.json` exists so the app's "What's new" card and the release
 * page can never disagree — one file, several readers. That only works if the
 * file is actually correct, and a release workflow reads it without any of this
 * repo's tests running. Three ways it went wrong before, all invisible in review:
 *
 *  - **No notes at all.** The file is hand-edited; a version whose notes nobody
 *    wrote renders an empty card in the app and a release page that falls back to
 *    a generic description. That is what `update.json`'s `notes` actually said,
 *    because it was copied from the release body rather than from this file.
 *  - **A duplicated or out-of-order version.** The file is hand-edited and
 *    prepend-only, so the newest entry drifting to the bottom is easy.
 *  - **Malformed JSON.** Hand-edited JSON is a syntax error away, and the reader
 *    deliberately swallows parse errors so the Settings screen cannot crash. That
 *    safety net is exactly what would hide a broken file from a release build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChangelogTest {

    private lateinit var changelog: ChangelogRepository

    @Before
    fun setUp() {
        changelog = ChangelogRepository(ApplicationProvider.getApplicationContext<Context>())
    }

    /** The parsed file must be non-trivial; a silently empty parse fails here. */
    @Test
    fun `the bundled changelog parses and is not empty`() {
        val loaded = changelog.load()

        assertNotNull(
            "assets/changelog.json could not be parsed. The reader swallows parse " +
                "errors so the Settings screen survives a bad file, which means a " +
                "syntax error would otherwise ship as an empty What's new card.",
            loaded.versions
        )
        assertTrue(
            "assets/changelog.json has no versions in it. Every consumer of the " +
                "release notes reads this file.",
            !loaded.versions!!.isEmpty()
        )
    }

    /**
     * The one that actually bites: notes that exist nowhere.
     *
     * ## Why this is not an exact match on [com.example.BuildConfig.VERSION_NAME]
     *
     * It started out that way, and it cannot work. The release workflow does not
     * use the version in `gradle.properties`: the resolver step advances the patch
     * number past whatever the newest published release is, so a repo sitting at
     * 1.0.23 with v1.0.28 already published releases as 1.0.29. No file can
     * contain that number before the build runs, so a test pinning
     * `BuildConfig.VERSION_NAME` was pinning a number CI invents.
     *
     * Every consumer therefore reads the newest block, and this asserts that
     * block has notes in it. `latestWithNotes` returning null is what an empty
     * What's new card looks like.
     */
    @Test
    fun `the newest version has release notes`() {
        val latest = changelog.latestWithNotes()

        assertNotNull(
            "assets/changelog.json has no version with usable notes. The What's new " +
                "card in Settings > Info renders empty, and the release page falls " +
                "back to a generic description of the app.",
            latest
        )
        assertTrue(
            "the newest block in assets/changelog.json (version ${latest!!.version}) " +
                "has no entries with titles. A heading with nothing under it is worse " +
                "than no heading.",
            latest.entries!!.any { !it.title.isNullOrBlank() }
        )
    }

    /**
     * Newest first, because that is the order every consumer renders in.
     *
     * `latestWithNotes` takes the first block, so this ordering *is* the contract
     * for which release gets described — not a cosmetic preference.
     */
    @Test
    fun `versions are listed newest first`() {
        val versions = changelog.versionsWithNotes()

        assertTrue("no version has any notes", versions.isNotEmpty())
        // Components compared as a single integer rather than a list: `List<Int>`
        // is not itself `Comparable`, and the implicit inference of that
        // overloaded `sortedByDescending` is what failed to compile here.
        val sorted = versions.map { it.version }
            .filterNotNull()
            .sortedByDescending { version -> version.sortKey() }
        assertEquals(
            "assets/changelog.json must list versions newest first. Found $sorted.",
            sorted,
            versions.map { it.version }
        )
    }

    /** No duplicates: two blocks for one version means two answers to one question. */
    @Test
    fun `no version is listed twice`() {
        val names = changelog.load().versions!!.mapNotNull { it.version }

        val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(
            "assets/changelog.json lists $duplicates more than once. The Info tab and " +
                "the release workflow would each pick one arbitrarily.",
            duplicates.isEmpty()
        )
    }

    /**
     * A version with an empty entry list is worse than an absent version.
     *
     * `versionsWithNotes` skips these, so nothing renders them today — but leaving
     * one in the file means editing the reader to change what a release says. A
     * hand-written file is easier to keep honest if empty blocks are deleted.
     */
    @Test
    fun `no listed version is empty`() {
        val empty = changelog.load().versions!!
            .filter { it.entries.isNullOrEmpty() }
            .mapNotNull { it.version }

        assertTrue(
            "assets/changelog.json has versions with no entries: $empty. Remove the " +
                "block rather than leaving a heading with nothing under it.",
            empty.isEmpty()
        )
    }

    /**
     * The block is rendered as an exact string, and the workflow sorts on it.
     *
     * A block written `"v1.0.24"` sorts and reads wrong, and the difference between
     * that and a clean number is invisible in a diff.
     */
    @Test
    fun `every version is a bare dotted number`() {
        changelog.load().versions!!.forEach { entry ->
            val version = entry.version
            assertTrue(
                "'$version' is not a bare dotted number. The version is shown " +
                    "verbatim in Settings and sorted numerically by ChangelogTest and " +
                    "by the release workflow, so a leading 'v' or trailing space is a " +
                    "silent mismatch.",
                version != null && Regex("""\d+(\.\d+)+""").matches(version)
            )
        }
    }

    /**
     * The file the APK ships is the same file the workflow reads from the repo.
     *
     * Two copies would defeat the entire point: the app would show one set of
     * notes and the release page another, which is the drift this replaced.
     */
    @Test
    fun `the asset is the repository file the release workflow reads`() {
        val asset = ApplicationProvider.getApplicationContext<Context>()
            .assets
            .open("changelog.json")
            .bufferedReader()
            .use { it.readText() }

        val onDisk = File(findProjectRoot(), "app/src/main/assets/changelog.json")
            .takeIf { it.isFile }
            ?.readText()

        assertNotNull(
            "could not locate app/src/main/assets/changelog.json from " +
                "${System.getProperty("user.dir")}. The release workflow reads that " +
                "path to write the release body, so its absence has to fail here.",
            onDisk
        )
        assertEquals(
            "the bundled changelog.json differs from the one in app/src/main/assets. " +
                "The app ships the asset and the release workflow reads the file; if " +
                "these ever differ, the app and the download page describe different " +
                "builds.",
            onDisk,
            asset
        )
    }

    /**
     * The structure the workflow parses, pinned here so a field rename fails the
     * test task rather than producing a release page with an empty list.
     *
     * Parsed with Moshi rather than `org.json` so it exercises the same reader the
     * app uses. This is the same assertion as the first test with the reader in
     * between — kept deliberately, because the reader swallows errors and that is
     * the property most likely to be broken by a future "harmless" change.
     */
    @Test
    fun `the file parses into the model the app reads it with`() {
        val asset = ApplicationProvider.getApplicationContext<Context>()
            .assets
            .open("changelog.json")
            .bufferedReader()
            .use { it.readText() }

        val parsed = Moshi.Builder().build()
            .adapter(Changelog::class.java)
            .fromJson(asset)

        assertNotNull("Moshi could not read the asset", parsed)
        assertTrue(parsed!!.versions!!.isNotEmpty())
    }

    /**
 * `1.0.24` → 10024, so the sort is numeric.
 *
 * String comparison is wrong here for the same reason the workflow compares patch
 * numbers as integers: "1.0.9" sorts above "1.0.10", which would make an older
 * release look like the newest one — and "newest" is what every consumer renders.
 */
private fun String.sortKey(): Long {
    val parts = split('.').mapNotNull { it.toIntOrNull() }
    require(parts.isNotEmpty()) { "'$this' has no numeric components" }
    return parts.fold(0L) { acc, part -> acc * 100L + part }
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
}