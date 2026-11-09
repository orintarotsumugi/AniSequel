package com.anisequel.data.extensions

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The last good catalogue, kept across process starts.
 *
 * The index is 255 entries from the recommended Aniyomi store alone, and a cold
 * open used to show a spinner while every repository was fetched again. This
 * cache makes the first frame populated and lets the network refresh replace it
 * when it answers. It is intentionally in `cacheDir`: the OS may evict it, and
 * losing it costs one fetch, never a screen.
 */
internal class ExtensionCatalogCache(context: Context) {

    private val file = File(context.cacheDir, "extension-catalog-v3.json")

    fun read(): ExtensionCatalog? {
        if (!file.isFile || file.length() > MAX_CACHE_BYTES) return null
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        val savedAt = root.optLong("savedAt", 0L)
        val array = root.optJSONArray("entries") ?: return null
        val entries = buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                parseEntry(obj)?.let(::add)
            }
        }
        if (entries.isEmpty()) return null
        return ExtensionCatalog(entries = entries, loadedAt = savedAt)
    }

    fun write(catalog: ExtensionCatalog) {
        if (catalog.entries.isEmpty()) return
        val array = JSONArray()
        catalog.entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("key", entry.key)
                    .put("repositoryId", entry.repositoryId)
                    .put("repositoryUrl", entry.repositoryUrl)
                    .put("kind", entry.kind.name)
                    .put("content", entry.content.name)
                    .put("name", entry.name)
                    .put("packageName", entry.packageName)
                    .put("version", entry.version)
                    .put("language", entry.language)
                    .put("nsfw", entry.nsfw)
                    .put("iconUrl", entry.iconUrl)
                    .put("downloadUrl", entry.downloadUrl)
                    .put("description", entry.description)
                    .put("sourceNames", entry.sourceNames.joinToString("\n"))
            )
        }
        val root = JSONObject()
            .put("savedAt", catalog.loadedAt)
            .put("entries", array)
        runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(file)) {
                file.writeText(root.toString())
                temp.delete()
            }
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    private fun parseEntry(obj: JSONObject): CatalogExtension? {
        val key = obj.optString("key").takeIf { it.isNotBlank() } ?: return null
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
        val kind = runCatching { ExtensionKind.valueOf(obj.optString("kind")) }
            .getOrDefault(ExtensionKind.ANIYOMI)
        val content = runCatching { ExtensionContent.valueOf(obj.optString("content")) }
            .getOrDefault(ExtensionContent.ANIME)
        return CatalogExtension(
            key = key,
            repositoryId = obj.optString("repositoryId"),
            repositoryUrl = obj.optString("repositoryUrl"),
            kind = kind,
            content = content,
            name = name,
            packageName = obj.optString("packageName"),
            version = obj.optString("version"),
            language = obj.optString("language").ifBlank { "en" },
            nsfw = obj.optBoolean("nsfw", false),
            iconUrl = obj.optString("iconUrl").takeIf { it.isNotBlank() },
            downloadUrl = obj.optString("downloadUrl").takeIf { it.isNotBlank() },
            description = obj.optString("description").takeIf { it.isNotBlank() },
            sourceNames = obj.optString("sourceNames")
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
        )
    }

    private companion object {
        const val MAX_CACHE_BYTES = 8L * 1024 * 1024
    }
}
