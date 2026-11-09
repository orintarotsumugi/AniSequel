package com.anisequel.data.extensions.runtime

import android.os.ParcelFileDescriptor
import com.anisequel.data.extensions.SourceAnime
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
import dalvik.system.InMemoryDexClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.animesource.model.SAnime
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Loads an Aniyomi extension APK inside the isolated runtime process only.
 *
 * The dex travels as a read-only descriptor and is loaded with
 * [InMemoryDexClassLoader]: an isolated process has no writable storage, so the
 * file-based `PathClassLoader` the reference app uses cannot apply here. Loading
 * from memory is the file-free equivalent and keeps the extension's classes out
 * of the host process entirely. The parent loader is the runtime's own, which is
 * where the clean-room `eu.kanade` ABI lives.
 */
class AniyomiLoader {

    private val sources = ConcurrentHashMap<String, LoadedSource>()

    fun load(apk: ParcelFileDescriptor, request: RuntimeProtocol.LoadRequest): List<RuntimeSourceInfo> {
        sources.values.filter { it.packageName == request.packageName }.takeIf { it.isNotEmpty() }?.let {
            return it.map { info -> RuntimeSourceInfo(info.id, info.name, info.lang, info.nsfw) }
        }
        val dex = FileInputStream(apk.fileDescriptor).use { extractDex(it) }
        if (dex.isEmpty()) throw IOException("This APK contains no dex code")
        val loader = InMemoryDexClassLoader(dex.toTypedArray(), javaClass.classLoader)
        val created = ArrayList<LoadedSource>()
        for (className in request.sourceClasses) {
            val instance = Class.forName(className, true, loader).getDeclaredConstructor().newInstance()
            val published = when (instance) {
                is AnimeSourceFactory -> instance.createSources()
                is AnimeSource -> listOf(instance)
                else -> throw IOException("$className is not an anime source or factory")
            }
            for (source in published) {
                val id = "${request.packageName}:${source.id}"
                val lang = (source as? AnimeCatalogueSource)?.lang ?: "en"
                val loaded = LoadedSource(id, request.packageName, source, lang, request.nsfw)
                sources[id] = loaded
                created += loaded
            }
        }
        if (created.isEmpty()) throw IOException("The extension published no source")
        return created.map { RuntimeSourceInfo(it.id, it.name, it.lang, it.nsfw) }
    }

    fun source(sourceId: String): LoadedSource =
        sources[sourceId] ?: throw IOException("This source is not loaded in the runtime")

    fun unload(packageName: String) {
        sources.entries.removeAll { it.value.packageName == packageName }
    }

    class LoadedSource(
        val id: String,
        val packageName: String,
        private val source: AnimeSource,
        val lang: String,
        val nsfw: Boolean
    ) {
        val name: String get() = source.name
        private val animeCache = ConcurrentHashMap<String, SAnime>()

        suspend fun search(query: String, page: Int): List<SourceAnime> {
            val catalogue = source as? AnimeCatalogueSource
                ?: throw IOException("${source.name} cannot be searched")
            val result = catalogue.getSearchAnime(page, query, catalogue.getFilterList())
            return result.animes.map { anime ->
                animeCache[anime.url] = anime
                AniyomiMapping.anime(anime)
            }
        }

        suspend fun details(dto: SourceAnime): SourceAnime {
            val anime = AniyomiMapping.toSAnime(dto, animeCache[dto.id])
            val updated = source.getAnimeDetails(anime)
            animeCache[updated.url] = updated
            return AniyomiMapping.anime(updated)
        }

        suspend fun episodes(dto: SourceAnime): List<SourceEpisode> {
            val anime = AniyomiMapping.toSAnime(dto, animeCache[dto.id])
            return source.getEpisodeList(anime).map { AniyomiMapping.episode(it) }
        }

        suspend fun resolve(dto: SourceEpisode): List<SourceServer> {
            val episode = AniyomiMapping.toSEpisode(dto)
            return source.getVideoList(episode).map { AniyomiMapping.server(it) }
        }
    }

    companion object {
        private const val MAX_DEX_BYTES = 64L * 1024 * 1024
        private val DEX_NAME = Regex("classes(\\d*)\\.dex")

        /**
         * Pulls every `classesN.dex` out of the APK, in load order.
         *
         * Separated from the class loader so a JVM test can pin the extraction
         * (and its size cap) against a zip fixture without an Android runtime.
         */
        fun extractDex(input: InputStream): List<ByteBuffer> {
            val collected = LinkedHashMap<String, ByteArray>()
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !DEX_NAME.matches(entry.name)) continue
                    val bytes = zip.readBytes()
                    total += bytes.size
                    if (total > MAX_DEX_BYTES) throw IOException("This extension's code is too large to load")
                    collected[entry.name] = bytes
                }
            }
            return collected.entries
                .sortedWith(compareBy({ it.key != "classes.dex" }, { dexIndex(it.key) }, { it.key }))
                .map { ByteBuffer.wrap(it.value) }
        }

        private fun dexIndex(name: String): Int =
            DEX_NAME.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }
}