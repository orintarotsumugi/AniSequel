package com.example.data.update

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The published build's update manifest, as written by the release workflow.
 *
 * This is the `update.json` the CI commits to `main` after every release. It is
 * deliberately the *only* thing the app has to understand about a release: it
 * already carries the version, the real version code, the release page and a
 * direct link to the APK, so there is no second format to keep in step.
 *
 * Every field is nullable. A manifest is a file on a public URL that anyone can
 * edit and that this app reads over a network that can be intercepted, so a
 * missing or mistyped field has to be a value the app can decline rather than an
 * exception that takes the settings screen down with it. `version` and
 * `versionCode` are the only two a download cannot proceed without, and
 * [isUsable] is what decides that.
 */
@JsonClass(generateAdapter = true)
data class UpdateManifest(
    @Json(name = "version") val version: String? = null,
    @Json(name = "version_code") val versionCode: Long? = null,
    @Json(name = "release_url") val releaseUrl: String? = null,
    @Json(name = "download_url") val downloadUrl: String? = null,
    @Json(name = "size_bytes") val sizeBytes: Long? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "notes") val notes: String? = null
) {
    /**
     * Whether this manifest is complete enough to act on.
     *
     * Requires a download URL and a version code. The release URL is optional
     * because it is only used to open the page in a browser, and a missing
     * notes string is not worth refusing an update over - a UI that renders
     * null as "no notes" degrades; a UI that throws on a malformed manifest
     * does not.
     */
    val isUsable: Boolean
        get() = !downloadUrl.isNullOrBlank() && versionCode != null

    /**
     * Whether this release is newer than what is installed.
     *
     * Compared on `version_code`, never on `version`. The version *name* is a
     * human label that the workflow derives by string munging, and string
     * comparison gets the ordering wrong exactly where it matters: "1.0.9"
     * sorts above "1.0.10". The version code is the GitHub run number, so it is
     * monotonic by construction.
     */
    fun isNewerThan(installedVersionCode: Long): Boolean {
        val remote = versionCode ?: return false
        return remote > installedVersionCode
    }
}

/** The outcome of asking GitHub whether there is anything newer. */
sealed interface UpdateCheckResult {
    /** The published build is the one running, or the manifest is unusable. */
    data class UpToDate(val installedVersion: String) : UpdateCheckResult

    /** A newer build exists and can be downloaded. */
    data class Available(val manifest: UpdateManifest) : UpdateCheckResult

    /** The check itself failed - no network, DNS, 404, malformed JSON. */
    data class Failed(val reason: String) : UpdateCheckResult
}

/** What the download step produced. */
sealed interface UpdateDownloadResult {
    data class Success(val file: java.io.File, val manifest: UpdateManifest) : UpdateDownloadResult
    data class Failed(val reason: String) : UpdateDownloadResult
}

/**
 * The update manifest's URL.
 *
 * ## Why this is the release asset and not `main`
 *
 * It used to be `raw.githubusercontent.com/.../main/update.json`, written by a
 * commit the release workflow pushed back to `main`. That could not work, and the
 * failure was invisible:
 *
 *  - `main` is a protected branch requiring two status checks. GitHub does not
 *    run workflows for pushes made with `GITHUB_TOKEN`, so those checks can
 *    never report on such a commit and the hook declines every push with
 *    "2 of 2 required status checks are expected".
 *  - The push step ended in `|| echo "Nothing to push."`, which turned that
 *    rejection into a *successful* step. Every run was green.
 *
 * The result was four consecutive releases (v1.0.15 through v1.0.18) published
 * while the manifest on `main` still advertised v1.0.14. Because `version_code`
 * is compared rather than the version name, an app already on 35 correctly
 * decided "not newer" - and no user was ever offered the releases that
 * contained the fixes. The updater was not broken so much as deaf, and the only
 * visible symptom was that bugs were never handed to anyone.
 *
 * A release asset has none of those problems: it is written by the same
 * authenticated job that publishes the APK, it needs no branch write, and
 * `releases/latest` tracks the newest non-draft release, so the URL below is
 * stable while its contents move forward with every release.
 */
const val UPDATE_MANIFEST_URL: String =
    "https://github.com/LOST-4EVER/AniSequel/releases/latest/download/update.json"

/** Human-readable size, for the "12.4 MB" line under the update button. */
fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "unknown size"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    return String.format("%.1f MB", kb / 1024.0)
}