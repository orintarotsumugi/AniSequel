package com.anisequel.data.extensions.runtime

import com.anisequel.data.extensions.SourceAnime
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

/**
 * Maps the Aniyomi source model onto the host's DTOs.
 *
 * The source's own `url` is the identity in its namespace: an anime is its page
 * path, an episode its watch path. A [Video] carries a hoster page (`url`) and
 * the playable file (`videoUrl`); the file is what the player opens, and the
 * video's headers travel with it because a bare URL is a 403 on most hosters.
 */
object AniyomiMapping {

    fun anime(source: SAnime): SourceAnime = SourceAnime(
        id = source.url,
        title = source.title,
        coverUrl = source.thumbnail_url,
        description = source.description,
        year = null,
        score = null
    )

    fun episode(source: SEpisode): SourceEpisode = SourceEpisode(
        id = source.url,
        name = source.name,
        number = source.episode_number.toInt(),
        thumbnailUrl = null,
        servers = emptyList(),
        durationSeconds = null
    )

    fun server(source: Video): SourceServer {
        val file = source.videoUrl?.takeIf { it.isNotBlank() && it != "null" } ?: source.url
        val headers = LinkedHashMap<String, String>()
        source.headers?.forEach { (name, value) -> headers[name] = value }
        return SourceServer(
            id = file,
            name = source.quality.ifBlank { file.substringAfterLast('/') },
            url = file,
            quality = source.quality.takeIf { it.isNotBlank() },
            headers = headers
        )
    }

    /** Rebuilds the source-side anime from the host DTO, preferring a cached one. */
    fun toSAnime(dto: SourceAnime, cached: SAnime?): SAnime {
        val target = cached ?: SAnime.create()
        target.url = dto.id
        if (dto.title.isNotBlank()) target.title = dto.title
        dto.coverUrl?.let { target.thumbnail_url = it }
        dto.description?.let { target.description = it }
        return target
    }

    fun toSEpisode(dto: SourceEpisode): SEpisode = SEpisode.create().apply {
        url = dto.id
        name = dto.name
        episode_number = dto.number.toFloat()
    }
}