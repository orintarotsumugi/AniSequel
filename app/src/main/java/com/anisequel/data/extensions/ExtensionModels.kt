package com.anisequel.data.extensions

import java.net.URI
import java.net.URLEncoder

/**
 * The four extension ecosystems the Extensions tab understands.
 *
 * They are *repository formats*, not runtimes. Aniomi and CloudStream entries are
 * APKs/JARs written against a third-party API; Sora and LNReader entries are
 * JavaScript modules. This beta reads and installs all four, and plays the
 * built-in native source (see [NativeAnimeSource]); loading a third-party APK or
 * JS module into this process is Phase 2 and is stated in the UI rather than
 * pretended.
 */
enum class ExtensionKind(val label: String) {
    ANIYOMI("Aniyomi"),
    CLOUDSTREAM("CloudStream"),
    SORA("Sora"),
    LNREADER("LNReader")
}

/** What a repository's entries are *for*. The screenshot's Anime/Manga/Novel row. */
enum class ExtensionContent(val label: String) {
    ANIME("Anime"),
    MANGA("Manga"),
    NOVEL("Novel")
}

/**
 * One index URL the reader added.
 *
 * [id] is the resolved index URL itself. A generated hash would make two copies
 * of the same repository look like two repositories, which is exactly the
 * duplicate a paste of a repo root and a paste of its index would produce.
 */
data class ExtensionRepository(
    val id: String,
    val url: String,
    val kind: ExtensionKind,
    val content: ExtensionContent,
    val addedAt: Long = System.currentTimeMillis()
) {
    /** The line under the URL in the repository list. */
    val displayHost: String
        get() = runCatching {
            URI(url).host?.removePrefix("www.")
        }.getOrNull().orEmpty().ifBlank { "unknown host" }

    /** The path shown beside the host: short enough for one row. */
    val displayPath: String
        get() = runCatching {
            val path = URI(url).path.orEmpty()
            if (path.length <= 52) path else "\u2026" + path.takeLast(51)
        }.getOrNull().orEmpty()
}

/**
 * One installable row in a repository.
 *
 * [key] is stable across a version bump, so an update is recognised as the same
 * extension rather than as a second, independently installed extension. It is
 * built from the kind and the package/module name because those are the two
 * fields a repository never renames; the display name is routinely rewritten.
 */
data class CatalogExtension(
    val key: String,
    val repositoryId: String,
    val repositoryUrl: String,
    val kind: ExtensionKind,
    val content: ExtensionContent,
    val name: String,
    val packageName: String,
    val version: String,
    val language: String,
    val nsfw: Boolean,
    val iconUrl: String?,
    val downloadUrl: String?,
    val description: String?
) {
    /** The `.apk`/`.js` tail, which is what "Install" will actually fetch. */
    val fileName: String
        get() = downloadUrl
            ?.substringAfterLast('/')
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }
            ?: "${packageName.ifBlank { name }}.apk"
}

/**
 * What this device has installed, as this app recorded it.
 *
 * PackageManager is deliberately not the source of truth. From Android 11 an app
 * cannot see arbitrary installed packages unless it declares them in `<queries>`,
 * and an extension repository's package names are not knowable at build time. The
 * install we performed is a fact we own; a package that was uninstalled in
 * another Settings screen is detected on the next refresh where the platform lets
 * us ask, and otherwise the row offers Uninstall rather than lying.
 */
data class InstalledExtension(
    val key: String,
    val packageName: String,
    val name: String,
    val version: String,
    val kind: ExtensionKind,
    val content: ExtensionContent,
    val apkPath: String?,
    val iconUrl: String?,
    val installedAt: Long,
    /** True once the platform installer reported that the package was replaced. */
    val needsRestart: Boolean
)

/**
 * A catalog plus what failed while reading it.
 *
 * A repository that is down must not hide the three that answered, so failures
 * are collected beside the entries instead of replacing them.
 */
data class ExtensionCatalog(
    val entries: List<CatalogExtension> = emptyList(),
    val errors: List<CatalogFailure> = emptyList(),
    val loadedAt: Long = 0L
) {
    val isCold: Boolean get() = loadedAt == 0L
}

data class CatalogFailure(
    val repository: ExtensionRepository,
    val message: String
)

/**
 * A source the player can actually open.
 *
 * Installed third-party extensions are listed too, with [playable] false, so the
 * source sheet tells the reader what they installed instead of hiding it. The
 * native source is the one the beta can play.
 */
data class PlayableSource(
    val id: String,
    val name: String,
    val kind: ExtensionKind,
    val content: ExtensionContent,
    val language: String,
    val nsfw: Boolean,
    val iconUrl: String?,
    val playable: Boolean,
    val note: String
)

/** One result from a source search. */
data class SourceAnime(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val description: String? = null,
    val year: Int? = null,
    val score: Int? = null
)

/** One playable file a source reports for an episode. */
data class SourceServer(
    val id: String,
    val name: String,
    val url: String,
    val quality: String? = null
)

/** One episode, with every server the source offered for it. */
data class SourceEpisode(
    val id: String,
    val name: String,
    val number: Int,
    val thumbnailUrl: String?,
    val servers: List<SourceServer>,
    val durationSeconds: Long? = null
)

/** What a source returns once a title has been picked. */
data class SourceDetails(
    val anime: SourceAnime,
    val description: String?,
    val episodes: List<SourceEpisode>
)

/**
 * Percent-encodes one URL path segment.
 *
 * `URLEncoder` is form encoding: it writes a space as `+`. Archive.org's download
 * paths treat `+` literally, so a file called "Episode 1.mp4" asked for as
 * "Episode+1.mp4" is a 404. The replacement is the whole point of this helper.
 */
internal fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")
