package com.anisequel.data.extensions

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Reads the four repository index shapes this app accepts.
 *
 * The formats are deliberately not modelled with Moshi. They are owned by other
 * projects and every one of them has grown aliases for the same field (`pkg`,
 * `internalName`, `url`, `apk`, `jar`); a strict adapter would fail the whole
 * index on the one entry that spells a field differently, while a reader that
 * asks several names degrades to skipping a single row. That is the correct
 * failure for a catalogue whose only job is to show what can be installed.
 */
internal object ExtensionIndex {

    /**
     * How many candidates a pasted repository URL may expand into.
     *
     * A repo root is resolved by trying the conventional index locations in
     * order. Sixteen covers the Aniyomi `repo/index.min.json` layout on the
     * `main`, `master` and `repo` branches, CloudStream's `repo.json` and
     * `plugins.json`, and the common Sora/LNReader tails. That is more than a
     * direct index needs and still small enough that a wrong URL costs a handful
     * of requests, not a crawl.
     */
    private const val MAX_CANDIDATES = 16

    fun looksLikeJson(body: String): Boolean {
        val first = body.firstOrNull { !it.isWhitespace() } ?: return false
        return first == '[' || first == '{'
    }

    /**
     * Every URL worth trying for [raw], in order, without duplicates.
     *
     * The first entry is the best guess and is what a direct `.json` URL uses.
     * GitHub's `blob` and `tree` pages are HTML, not JSON, so they are rewritten
     * before anything is fetched; a reader who copies the address bar should not
     * have to know that raw.githubusercontent.com exists.
     */
    fun candidateUrls(raw: String): List<String> {
        val input = normalizeScheme(raw.trim())
        if (input.isBlank()) return emptyList()

        val candidates = mutableListOf<String>()
        val githubBlob = Regex(
            "https?://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)",
            RegexOption.IGNORE_CASE
        ).find(input)
        if (githubBlob != null) {
            val (user, repo, branch, path) = githubBlob.destructured
            candidates += "https://raw.githubusercontent.com/$user/$repo/$branch/$path"
        }

        val githubTree = Regex(
            "https?://github\\.com/([^/]+)/([^/]+)/tree/([^/]+)/?(.*)",
            RegexOption.IGNORE_CASE
        ).find(input)
        if (githubTree != null) {
            val (user, repo, branch, path) = githubTree.destructured
            val base = "https://raw.githubusercontent.com/$user/$repo/$branch"
            if (path.isNotBlank()) {
                candidates += "$base/${path.trimEnd('/')}/index.min.json"
                candidates += "$base/${path.trimEnd('/')}/repo/index.min.json"
            }
            candidates += "$base/repo/index.min.json"
            candidates += "$base/index.min.json"
            candidates += "$base/repo/plugins.json"
            candidates += "$base/plugins.json"
        }

        val githubRoot = Regex(
            "https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$",
            RegexOption.IGNORE_CASE
        ).find(input)
        if (githubRoot != null) {
            val (user, repo) = githubRoot.destructured
            // `repo` is a branch name in several Aniyomi indexes, not only a
            // directory on `main` - the seeded yuzono index lives at
            // raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json.
            for (branch in listOf("main", "master", "repo")) {
                candidates += "https://raw.githubusercontent.com/$user/$repo/$branch/repo/index.min.json"
                candidates += "https://raw.githubusercontent.com/$user/$repo/$branch/index.min.json"
                candidates += "https://raw.githubusercontent.com/$user/$repo/$branch/repo/plugins.json"
                candidates += "https://raw.githubusercontent.com/$user/$repo/$branch/plugins.json"
                candidates += "https://raw.githubusercontent.com/$user/$repo/$branch/repo.json"
            }
        }

        val codeberg = Regex(
            "https?://codeberg\\.org/([^/]+)/([^/]+?)(?:\\.git)?/?$",
            RegexOption.IGNORE_CASE
        ).find(input)
        if (codeberg != null) {
            val (user, repo) = codeberg.destructured
            for (branch in listOf("main", "master", "repo")) {
                candidates += "https://codeberg.org/$user/$repo/raw/branch/$branch/repo/index.min.json"
                candidates += "https://codeberg.org/$user/$repo/raw/branch/$branch/index.min.json"
                candidates += "https://codeberg.org/$user/$repo/raw/branch/$branch/repo/plugins.json"
            }
        }

        // The input itself, plus the conventional tails for a bare repository
        // root or a directory. Appended after the specific rewrites so a URL that
        // is already an index always stays first.
        candidates += input
        if (!input.endsWith(".json", ignoreCase = true)) {
            val base = input.trimEnd('/')
            candidates += "$base/index.min.json"
            candidates += "$base/repo/index.min.json"
            candidates += "$base/plugins.json"
            candidates += "$base/repo.json"
        }

        return candidates
            .map { it.replace(" ", "%20") }
            .distinct()
            .take(MAX_CANDIDATES)
    }

    /**
     * What kind of repository [url] most likely is, from the URL alone.
     *
     * The body is the better witness, and [kindFor] prefers it. This is only the
     * answer for a URL whose body has not been read yet - it labels the Add Repo
     * preview before the first request.
     */
    fun kindForUrl(url: String): ExtensionKind {
        val lower = url.lowercase()
        return when {
            "cloudstream" in lower -> ExtensionKind.CLOUDSTREAM
            "lnreader" in lower -> ExtensionKind.LNREADER
            "sora" in lower -> ExtensionKind.SORA
            "aniyomi" in lower || "anime-repo" in lower || "animeextension" in lower ->
                ExtensionKind.ANIYOMI
            "recloudstream" in lower -> ExtensionKind.CLOUDSTREAM
            else -> ExtensionKind.ANIYOMI
        }
    }

    /** The content type the URL suggests, overridden per entry where the package says. */
    fun contentForUrl(url: String, kind: ExtensionKind): ExtensionContent {
        val lower = url.lowercase()
        if (kind == ExtensionKind.LNREADER || "novel" in lower) return ExtensionContent.NOVEL
        if ("manga" in lower && kind == ExtensionKind.ANIYOMI) return ExtensionContent.MANGA
        return ExtensionContent.ANIME
    }

    /**
     * Reads [body] as an index, returning an empty list for a body that is not
     * one. An unknown root is not an exception: a repository that answers 200
     * with an HTML page is indistinguishable from a bad URL, and the caller
     * wants to try the next candidate rather than surface a parser stack.
     */
    fun parse(
        body: String,
        repositoryId: String,
        repositoryUrl: String,
        kind: ExtensionKind,
        content: ExtensionContent
    ): List<CatalogExtension> {
        val array = rootArray(body) ?: return emptyList()
        val out = ArrayList<CatalogExtension>(array.length())
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            parseEntry(entry, repositoryId, repositoryUrl, kind, content)?.let(out::add)
        }
        return out
    }

    /**
     * Which kind the *entries* look like.
     *
     * A CloudStream index has `tvTypes` and `fileSize`; an Aniyomi index has an
     * `apk`; a JS module index has a `url` and no package. The URL is only the
     * tie-breaker, because a mirror can be hosted under any name.
     */
    fun kindFor(body: String, url: String): ExtensionKind {
        val array = rootArray(body) ?: return kindForUrl(url)
        val first = (0 until array.length()).firstNotNullOfOrNull { array.optJSONObject(it) }
        if (first != null) {
            if (first.has("tvTypes") || first.has("apiVersion") || first.has("fileSize")) {
                return ExtensionKind.CLOUDSTREAM
            }
            if (first.has("apk") || first.has("pkg")) return ExtensionKind.ANIYOMI
            if (first.has("internalName") && first.has("url")) return ExtensionKind.CLOUDSTREAM
            val lowerUrl = url.lowercase()
            if ("lnreader" in lowerUrl) return ExtensionKind.LNREADER
            if ("sora" in lowerUrl) return ExtensionKind.SORA
        }
        return kindForUrl(url)
    }

    private fun parseEntry(
        entry: JSONObject,
        repositoryId: String,
        repositoryUrl: String,
        kind: ExtensionKind,
        content: ExtensionContent
    ): CatalogExtension? {
        val name = entry.stringOf("name", "extensionName", "title")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val packageName = entry.stringOf(
            "pkg",
            "packageName",
            "package",
            "internalName",
            "internalname",
            "id"
        ).orEmpty()
        val version = entry.stringOf("version", "versionName", "version_name", "code")
            .orEmpty()
            .ifBlank { "1" }
        val language = entry.stringOf("lang", "language", "locale")
            .orEmpty()
            .ifBlank { "en" }
        val icon = entry.stringOf("iconUrl", "icon", "icon_url", "logo")
            .orEmpty()
            .takeIf { it.isNotBlank() }
        val description = entry.stringOf("description", "summary", "desc")
            ?.takeIf { it.isNotBlank() }
        val downloadRaw = entry.stringOf(
            "apk",
            "apkUrl",
            "downloadUrl",
            "download_url",
            "url",
            "jar",
            "file",
            "module"
        ).orEmpty().takeIf { it.isNotBlank() }
        val download = downloadRaw?.let { resolve(repositoryUrl, it) }
        val nsfw = entry.isNsfw()
        val entryContent = contentForEntry(entry, packageName, content)

        val keyName = packageName.ifBlank { download ?: name }.lowercase()
        return CatalogExtension(
            key = "${kind.name}:$keyName",
            repositoryId = repositoryId,
            repositoryUrl = repositoryUrl,
            kind = kind,
            content = entryContent,
            name = name,
            packageName = packageName,
            version = version,
            language = language,
            nsfw = nsfw,
            iconUrl = icon?.let { resolve(repositoryUrl, it) } ?: icon,
            downloadUrl = download,
            description = description
        )
    }

    private fun contentForEntry(
        entry: JSONObject,
        packageName: String,
        fallback: ExtensionContent
    ): ExtensionContent {
        val pkg = packageName.lowercase()
        if (pkg.contains("mangaextension") || pkg.contains(".manga.")) return ExtensionContent.MANGA
        if (pkg.contains("animeextension") || pkg.contains(".anime.")) return ExtensionContent.ANIME

        val tvTypes = entry.optJSONArray("tvTypes")
        if (tvTypes != null) {
            val text = (0 until tvTypes.length()).joinToString(" ") { tvTypes.optString(it) }
            if (text.contains("Anime", ignoreCase = true) ||
                text.contains("OVA", ignoreCase = true) ||
                text.contains("Cartoon", ignoreCase = true)
            ) {
                return ExtensionContent.ANIME
            }
        }
        return fallback
    }

    private fun JSONObject.isNsfw(): Boolean {
        if (optInt("nsfw", 0) == 1) return true
        if (optBoolean("nsfw", false) || optBoolean("isNsfw", false)) return true
        val rating = stringOf("contentRating", "rating").orEmpty()
        if (rating.contains("NSFW", ignoreCase = true) ||
            rating.contains("18", ignoreCase = true)
        ) {
            return true
        }
        return stringOf("name").orEmpty().contains("NSFW", ignoreCase = true)
    }

    private fun rootArray(body: String): JSONArray? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("[")) {
            return runCatching { JSONArray(trimmed) }.getOrNull()
        }
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        return obj.optJSONArray("extensions")
            ?: obj.optJSONArray("plugins")
            ?: obj.optJSONArray("data")
            ?: obj.optJSONArray("sources")
            ?: obj.optJSONArray("items")
    }

    private fun JSONObject.stringOf(vararg keys: String): String? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key)
            if (value is String && value.isNotBlank()) return value.trim()
            if (value is Number) return value.toString()
            if (value is Boolean) return value.toString()
        }
        return null
    }

    /** Resolves an index-relative asset against the index URL's own directory. */
    fun resolve(baseUrl: String, relative: String): String {
        if (relative.startsWith("http://", ignoreCase = true) ||
            relative.startsWith("https://", ignoreCase = true)
        ) {
            return relative
        }
        if (relative.startsWith("//")) return "https:$relative"
        return runCatching {
            URI(baseUrl).resolve(relative).toString()
        }.getOrDefault(relative)
    }

    private fun normalizeScheme(input: String): String = when {
        input.isBlank() -> input
        input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true) -> input
        input.startsWith("github.com/", ignoreCase = true) -> "https://$input"
        input.startsWith("raw.githubusercontent.com/", ignoreCase = true) -> "https://$input"
        input.startsWith("codeberg.org/", ignoreCase = true) -> "https://$input"
        else -> input
    }
}
