package com.anisequel.data.extensions.runtime

import com.anisequel.data.extensions.ExtensionContent
import com.anisequel.data.extensions.ExtensionKind
import com.anisequel.data.extensions.InstalledExtension
import com.anisequel.data.extensions.NativeAnimeSource
import com.anisequel.data.extensions.SourceAnime
import com.anisequel.data.extensions.SourceDetails
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/**
 * A staged Aniyomi APK presented as a normal source.
 *
 * The Watch page sees the same [NativeAnimeSource] contract as the built-in
 * Archive source; the difference is entirely below this class, in the isolated
 * runtime the calls are forwarded to. The APK is loaded once per process and the
 * first source it publishes is used - per-source selection inside a multi-source
 * package is a later phase.
 */
class RuntimeAnimeSource(
    private val record: InstalledExtension,
    private val manifest: AniyomiPackage,
    private val client: ExtensionRuntimeClient
) : NativeAnimeSource {

    override val id: String = record.key
    override val name: String = record.name
    override val kind: ExtensionKind = ExtensionKind.ANIYOMI
    override val content: ExtensionContent = ExtensionContent.ANIME
    override val language: String = "en"
    override val nsfw: Boolean = manifest.nsfw
    override val note: String = "Runs in AniSequel's sandboxed extension runtime."

    @Volatile
    private var runtimeId: String? = null
    private val loadMutex = Mutex()

    override suspend fun search(query: String): List<SourceAnime> =
        client.search(ensureLoaded(), query)

    override suspend fun details(animeId: String): SourceDetails {
        val sourceId = ensureLoaded()
        val stub = SourceAnime(id = animeId, title = "", coverUrl = null)
        val updated = client.details(sourceId, stub)
        val episodes = client.episodes(sourceId, updated)
        return SourceDetails(anime = updated, description = updated.description, episodes = episodes)
    }

    override suspend fun resolveServers(animeId: String, episodeId: String): List<SourceServer> {
        val sourceId = ensureLoaded()
        val episode = SourceEpisode(
            id = episodeId,
            name = "",
            number = 0,
            thumbnailUrl = null,
            servers = emptyList()
        )
        return client.resolve(sourceId, episode)
    }

    private suspend fun ensureLoaded(): String {
        runtimeId?.let { return it }
        return loadMutex.withLock {
            runtimeId?.let { return@withLock it }
            val path = record.apkPath ?: throw IOException("This extension has no staged file")
            val apk = File(path)
            if (!apk.isFile) throw IOException("This extension's file is missing; reinstall it")
            val sources = client.load(manifest, apk)
            val first = sources.firstOrNull()
                ?: throw IOException("${record.name} published no playable source")
            first.id.also { runtimeId = it }
        }
    }
}