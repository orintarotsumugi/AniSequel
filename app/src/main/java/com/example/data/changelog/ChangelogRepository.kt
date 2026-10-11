package com.example.data.changelog

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

/** One line of "what changed" in a release. */
@JsonClass(generateAdapter = true)
data class ChangelogItem(
    @Json(name = "title") val title: String? = null,
    @Json(name = "detail") val detail: String? = null
)

/**
 * One released version and the notes a user would want before updating.
 *
 * Every field nullable for the same reason as [com.example.data.update.UpdateManifest]:
 * this is a file that is edited by hand, shipped inside the APK, and read by code
 * that must not throw because somebody left a comma out. A malformed entry
 * degrades to a version with no notes, which is boring and survivable.
 */
@JsonClass(generateAdapter = true)
data class ChangelogVersion(
    @Json(name = "version") val version: String? = null,
    @Json(name = "entries") val entries: List<ChangelogItem>? = null
)

@JsonClass(generateAdapter = true)
data class Changelog(
    @Json(name = "versions") val versions: List<ChangelogVersion>? = null
)

/**
 * The app's own release notes, bundled in the APK.
 *
 * ## Why a file rather than a Kotlin list
 *
 * The notes used to be a hardcoded `listOf(ChangelogEntry(...))` in the Info tab.
 * Two things were wrong with that, and neither was visible in the source:
 *
 *  - It was **stale**. The list described v1.0.21 features long after v1.0.23
 *    shipped, because nothing connected "what we shipped" to "what the app
 *    claims it shipped". It also had no version attached, so there was no way to
 *    check.
 *  - It was a **third** copy of the release notes, alongside the GitHub release
 *    body and `update.json`'s `notes`. The workflow wrote those two from whatever
 *    GitHub generated, which is why `update.json` shipped a `notes` field that
 *    opened with "## AniSequel / Find the anime sequels you finished..." —
 *    a description of the app, not a list of changes.
 *
 * Moving the notes into `assets/changelog.json` makes this file the single
 * source. The Info tab reads it from the APK, and the release workflow reads the
 * same file from the checkout to write the release body. Two consumers, one
 * source, so they cannot drift.
 *
 * Read synchronously from an asset on the calling thread. It is a few hundred
 * bytes on the local filesystem and it is read once per Info-tab composition;
 * wrapping it in a coroutine to avoid a few hundred microseconds would be a
 * bigger cost than the read.
 */
class ChangelogRepository(private val context: Context) {

    private val moshi = Moshi.Builder().build()

    /**
     * Parsed once and kept. A file-backed asset cannot change while the process
     * lives, so re-reading it per composition would only repeat work that has
     * already been done.
     */
    @Volatile
    private var cached: Changelog? = null

    fun load(): Changelog =
        cached ?: synchronized(this) {
            cached ?: readAsset().also { cached = it }
        }

    /**
     * The newest version block in the file, or null when it has no usable notes.
     *
     * ## Why "newest block" and not "the block for [BuildConfig.VERSION_NAME]"
     *
     * An exact lookup by version looks more correct and cannot work. The release
     * workflow *derives* the version it publishes: it advances the patch number
     * past whatever the newest published release is, so a repo whose
     * `gradle.properties` says 1.0.23 with v1.0.28 already published releases as
     * 1.0.29. No changelog file can be written ahead of that, because the number
     * does not exist until the build runs.
     *
     * So the newest block is what every consumer reads, and the block's own
     * `version` is displayed alongside it. A build one patch ahead of the notes
     * then honestly says "here is what changed through 1.0.24" instead of either
     * showing an empty card or, worse, silently borrowing the wrong block.
     *
     * `ChangelogTest` still pins the ordering, so "newest" means new *notes*
     * rather than "whatever happens to be at the top of the file".
     */
    fun latestWithNotes(): ChangelogVersion? =
        versionsWithNotes().firstOrNull()

    /**
     * The notes for [version], or empty when that version has none.
     *
     * Kept for callers that genuinely want one specific version - a "what changed
     * in this release" deep link, for instance. Nothing in the app uses it today,
     * which is the point: the Info tab must not use it.
     *
     * Relies on the file's newest-first order (`ChangelogTest` pins it), so this
     * returns the *newest* matching block if a version is ever duplicated. It used
     * to reverse the list and take the first, which returned the *oldest* match -
     * the opposite of what the signature promises.
     */
    fun entriesFor(version: String): List<ChangelogItem> =
        load().versions
            ?.firstOrNull { it.version == version }
            ?.entries
            ?.filterNot { it.title.isNullOrBlank() }
            .orEmpty()

    /**
     * Versions newest-first, for the "what's changed" summary that lists history.
     *
     * Skips entries with no usable notes rather than rendering an empty heading -
     * a version with nothing to say is better absent than present and blank.
     */
    fun versionsWithNotes(): List<ChangelogVersion> =
        load().versions
            ?.filter { version -> !version.version.isNullOrBlank() && !version.entries.isNullOrEmpty() }
            .orEmpty()

    private fun readAsset(): Changelog = try {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        moshi.adapter(Changelog::class.java).fromJson(json) ?: Changelog()
    } catch (e: Exception) {
        // A missing or malformed changelog must not take the Settings screen down
        // with it. The tab renders an empty list instead.
        Changelog()
    }

    private companion object {
        const val ASSET_NAME = "changelog.json"
    }
}