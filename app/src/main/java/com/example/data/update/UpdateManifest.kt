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
 * `raw.githubusercontent.com` serves the file on `main` directly, without the
 * API's 60-requests-an-hour unauthenticated rate limit and without the several
 * seconds a cold `api.github.com` DNS + TLS setup takes on a phone. A check that
 * is meant to run on every launch cannot depend on either.
 *
 * It does mean the manifest reflects whatever is on `main`, which for this repo
 * is written by the release workflow itself in the same job that publishes the
 * APK - so the two cannot disagree for more than the length of one commit.
 */
const val UPDATE_MANIFEST_URL: String =
    "https://raw.githubusercontent.com/LOST-4EVER/AniSequel/main/update.json"

/** Human-readable size, for the "12.4 MB" line under the update button. */
fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "unknown size"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    return String.format("%.1f MB", kb / 1024.0)
}