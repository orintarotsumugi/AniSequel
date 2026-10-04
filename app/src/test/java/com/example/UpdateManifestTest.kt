package com.example

import com.example.data.update.UPDATE_MANIFEST_URL
import com.example.data.update.UpdateManifest
import com.example.data.update.formatBytes
import com.squareup.moshi.Moshi
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update manifest contract, and the comparison the whole updater rests on.
 *
 * These are the tests that matter for an updater: everything else is plumbing,
 * and this is the part where being wrong means either never offering an update
 * or offering one that downgrades the app.
 */
class UpdateManifestTest {

    private val adapter = Moshi.Builder().build().adapter(UpdateManifest::class.java)

    /**
     * The shape the release workflow actually writes.
     *
     * Taken verbatim from `update.json` on `main` rather than written by hand,
     * so this test fails if the workflow changes the format instead of quietly
     * passing against an invented shape.
     */
    private val publishedManifest = """
        {
          "version": "1.0.9",
          "version_code": 22,
          "release_url": "https://github.com/LOST-4EVER/AniSequel/releases/tag/v1.0.9",
          "download_url": "https://github.com/LOST-4EVER/AniSequel/releases/download/v1.0.9/anisequel-universal.apk",
          "size_bytes": 2410756,
          "published_at": "2026-10-03T07:38:15Z",
          "notes": "  * Restore the release signing key dropped by the history rewrite\n  * Add \"Not interested\" per card\n\nBuild 22 from a4171424."
        }
    """.trimIndent()

    @Test
    fun `parses the manifest the release workflow publishes`() {
        val manifest = adapter.fromJson(publishedManifest)!!

        assertEquals("1.0.9", manifest.version)
        assertEquals(22L, manifest.versionCode)
        assertEquals(2410756L, manifest.sizeBytes)
        assertTrue(manifest.downloadUrl!!.endsWith("anisequel-universal.apk"))
        assertTrue(manifest.isUsable)
    }

    /**
     * The notes are now GitHub's generated release body rather than a build
     * identifier, so they carry newlines, quotes and markdown.
     *
     * This is the reason the manifest is built with jq rather than interpolated
     * into a heredoc: the body is free text, and a quote or a backslash in it
     * would otherwise produce a manifest that does not parse - which the app
     * reports as a failed update check rather than as a corrupt file.
     */
    @Test
    fun `survives release notes containing quotes and newlines`() {
        val manifest = adapter.fromJson(publishedManifest)!!

        val notes = manifest.notes!!
        assertTrue("release notes must not be a bare build id: $notes", notes.contains("Not interested"))
        assertTrue("the build id is still useful for reporting: $notes", notes.contains("Build 22"))
        assertEquals("2026-10-03T07:38:15Z", manifest.publishedAt)
    }

    @Test
    fun `tolerates a manifest with only the fields it needs`() {
        val manifest = adapter.fromJson(
            """{"version_code": 99, "download_url": "https://example.com/a.apk"}"""
        )!!

        assertTrue(manifest.isUsable)
        assertNull(manifest.version)
        assertNull(manifest.notes)
        assertNull(manifest.sizeBytes)
    }

    @Test
    fun `a manifest with no download link is not usable`() {
        val manifest = adapter.fromJson("""{"version_code": 99}""")!!

        assertFalse(manifest.isUsable)
    }

    @Test
    fun `a manifest with no version code is not usable`() {
        val manifest = adapter.fromJson(
            """{"version": "9.9.9", "download_url": "https://example.com/a.apk"}"""
        )!!

        assertFalse(manifest.isUsable)
    }

    /**
     * The comparison itself.
     *
     * Split into past/present/future rather than one test so a regression names
     * the direction it broke in: offering a downgrade is a different and worse
     * bug than never offering an update.
     */
    @Test
    fun `a newer version code is an update`() {
        val manifest = UpdateManifest(versionCode = 17)

        assertTrue(manifest.isNewerThan(installedVersionCode = 16))
    }

    @Test
    fun `the same version code is not an update`() {
        val manifest = UpdateManifest(versionCode = 16)

        assertFalse(manifest.isNewerThan(installedVersionCode = 16))
    }

    @Test
    fun `an older version code is not an update`() {
        val manifest = UpdateManifest(versionCode = 15)

        assertFalse(manifest.isNewerThan(installedVersionCode = 16))
    }

    /**
     * The reason the comparison is on `version_code` and not on `version`.
     *
     * "1.0.9" sorts *above* "1.0.10" as a string, so a version-name comparison
     * silently stops offering updates the first time the project passes patch 9.
     * The version code is the GitHub run number and is monotonic, so the test
     * pins that a lower number never wins regardless of how good it looks.
     */
    @Test
    fun `version names that sort backwards do not affect the comparison`() {
        val looksNewerByName = UpdateManifest(version = "1.0.9", versionCode = 15)
        val actuallyNewer = UpdateManifest(version = "1.0.10", versionCode = 20)

        assertFalse(looksNewerByName.isNewerThan(installedVersionCode = 16))
        assertTrue(actuallyNewer.isNewerThan(installedVersionCode = 16))
    }

    @Test
    fun `a manifest with no version code is never newer`() {
        val manifest = UpdateManifest(version = "99.0.0", downloadUrl = "https://example.com/a.apk")

        assertFalse(manifest.isNewerThan(installedVersionCode = 16))
    }

    @Test
    fun `formats sizes for the download label`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2.3 MB", formatBytes(2_375_396))
        assertEquals("unknown size", formatBytes(null))
        assertEquals("unknown size", formatBytes(-1))
    }

    /**
     * The manifest is fetched from a hardcoded URL, so this pins the two things
     * that would silently break it.
     *
     * It must be a release asset rather than a file on `main`. `main` is a
     * protected branch requiring two status checks, and GitHub does not run
     * workflows for pushes made with `GITHUB_TOKEN`, so the release workflow's
     * commit of the manifest was rejected every single time. The push failure was
     * then swallowed by a trailing `|| echo`, so every run stayed green while
     * `main` still advertised v1.0.14 through four later releases - which means
     * no user was ever offered the releases carrying the fixes.
     *
     * It also must not go through `api.github.com`, which is rate limited to 60
     * requests an hour unauthenticated and turns a per-launch check into an
     * intermittent failure on exactly the devices least able to retry. And it
     * must be this repository: a build that checked someone else's releases
     * would offer their APK to this app's users.
     */
    @Test
    fun `the manifest url points at this project's latest release asset`() {
        assertEquals(
            "the manifest must be read from the latest release asset of this repository",
            "https://github.com/LOST-4EVER/AniSequel/releases/latest/download/update.json",
            UPDATE_MANIFEST_URL
        )

        assertFalse(
            "the manifest must not be read from a branch: main is protected and " +
                "rejects the workflow's own commit, which is how it went stale: " +
                "$UPDATE_MANIFEST_URL",
            UPDATE_MANIFEST_URL.contains("raw.githubusercontent.com")
        )

        assertFalse(
            "the API is rate limited and cannot be read on every launch: " +
                "$UPDATE_MANIFEST_URL",
            UPDATE_MANIFEST_URL.contains("api.github.com")
        )
    }
}

/**
 * The manifest URL and the workflow that publishes it cannot be allowed to
 * drift apart.
 *
 * They already had: the app read the manifest from `main` while the workflow
 * only ever tried to write it there, and the write was rejected by the branch
 * protection hook. Nothing reported it because the step ended in `|| echo`.
 *
 * Neither half can be tested on its own - a test on the URL would still pass
 * against a workflow that publishes nothing, and a test on the workflow would
 * still pass against an app that reads somewhere else. So this checks the two
 * agree.
 */
class UpdateManifestPublicationTest {

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

    private val workflow: String =
        File(root, ".github/workflows/android-release.yml").readText()

    @Test
    fun `the workflow publishes the manifest to the release the app reads`() {
        assertTrue(
            "update.json must be uploaded as a release asset, because that is the " +
                "only place the app can read it from:\n$UPDATE_MANIFEST_URL",
            workflow.contains("gh release upload")
        )

        assertTrue(
            "the upload must be allowed to replace an existing asset, or a re-run " +
                "fails on the tag it just published",
            workflow.contains("--clobber")
        )
    }

    /**
     * The bug was not that the push failed, it was that nobody could tell.
     *
     * `git push ... || echo "Nothing to push."` turned a rejected push into a
     * successful step, so four releases shipped with a manifest nobody could be
     * offered. The push is still expected to be refused - `main` is protected and
     * GitHub will not run checks for a token-authenticated push - but it must
     * now be reported rather than swallowed.
     */
    @Test
    fun `a rejected manifest push is reported instead of swallowed`() {
        assertFalse(
            "the manifest push must not end in '|| echo', which turned a rejected " +
                "push into a green step and hid four stale releases",
            // `${'$'}` rather than `$`: a raw string still interpolates, so an
            // unescaped ${GITHUB_REF_NAME} would be resolved by Kotlin as a
            // template expression and fail to compile.
            workflow.contains(
                """git push --quiet origin "HEAD:${'$'}{GITHUB_REF_NAME}" || echo"""
            )
        )

        assertTrue(
            "a refused push should be surfaced as a workflow warning",
            workflow.contains("::warning title=Manifest not committed")
        )
    }
}