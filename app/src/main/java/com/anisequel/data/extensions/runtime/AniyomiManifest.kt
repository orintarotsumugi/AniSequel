package com.anisequel.data.extensions.runtime

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** What the host can learn from an extension APK's manifest without running it. */
data class AniyomiPackage(
    val packageName: String,
    val versionName: String,
    val sourceClasses: List<String>,
    val nsfw: Boolean
)

/**
 * The Aniyomi manifest contract, read host-side.
 *
 * `getPackageArchiveInfo` parses the manifest without installing or executing
 * anything, so the feature flag, source class names and NSFW bit are learned
 * before any code crosses into the runtime. The class-name resolution is a pure
 * function so a JVM test can pin the `;`-separated, `.`-prefixed shape.
 */
object AniyomiManifest {
    const val ANIME_FEATURE = "tachiyomi.animeextension"
    const val LEGACY_FEATURE = "tachiyomi.extension"

    private const val ANIME_CLASS = "tachiyomi.animeextension.class"
    private const val ANIME_FACTORY = "tachiyomi.animeextension.factory"
    private const val ANIME_NSFW = "tachiyomi.animeextension.nsfw"
    private const val LEGACY_CLASS = "tachiyomi.extension.class"
    private const val LEGACY_FACTORY = "tachiyomi.extension.factory"
    private const val LEGACY_NSFW = "tachiyomi.extension.nsfw"

    fun isAnimeExtension(featureNames: List<String>): Boolean =
        ANIME_FEATURE in featureNames || LEGACY_FEATURE in featureNames

    /**
     * Resolves the instantiable class names. The `.class` metadata may name a
     * source or a factory; a leading `.` is relative to the package, and several
     * classes are `;`-separated. The `.factory` key is the fallback.
     */
    fun sourceClasses(packageName: String, classMeta: String?, factoryMeta: String?): List<String> {
        val raw = classMeta?.takeIf { it.isNotBlank() } ?: factoryMeta.orEmpty()
        return raw.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) packageName + it else it }
            .distinct()
    }

    fun classMeta(data: android.os.Bundle?): String? =
        data?.getString(ANIME_CLASS) ?: data?.getString(LEGACY_CLASS)

    fun factoryMeta(data: android.os.Bundle?): String? =
        data?.getString(ANIME_FACTORY) ?: data?.getString(LEGACY_FACTORY)

    fun nsfw(data: android.os.Bundle?): Boolean =
        data?.getInt(ANIME_NSFW, 0) == 1 || data?.getInt(LEGACY_NSFW, 0) == 1
}

/** Reads and caches [AniyomiPackage] metadata for staged extension files. */
class AniyomiManifestReader(context: Context) {

    private val packageManager = context.applicationContext.packageManager
    private val cache = ConcurrentHashMap<String, Pair<Long, AniyomiPackage?>>()

    fun read(apk: File): AniyomiPackage? {
        if (!apk.isFile) return null
        val stamp = apk.lastModified()
        cache[apk.absolutePath]?.let { (cachedStamp, cached) ->
            if (cachedStamp == stamp) return cached
        }
        val parsed = runCatching { parse(apk) }.getOrNull()
        cache[apk.absolutePath] = stamp to parsed
        return parsed
    }

    private fun parse(apk: File): AniyomiPackage? {
        @Suppress("DEPRECATION")
        val archive = packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_META_DATA)
            ?: return null
        val features = archive.reqFeatures.orEmpty().mapNotNull { it.name }
        if (!AniyomiManifest.isAnimeExtension(features)) return null
        val data = archive.applicationInfo?.metaData
        val classes = AniyomiManifest.sourceClasses(
            archive.packageName,
            AniyomiManifest.classMeta(data),
            AniyomiManifest.factoryMeta(data)
        )
        if (classes.isEmpty()) return null
        return AniyomiPackage(
            packageName = archive.packageName,
            versionName = archive.versionName.orEmpty(),
            sourceClasses = classes,
            nsfw = AniyomiManifest.nsfw(data)
        )
    }
}