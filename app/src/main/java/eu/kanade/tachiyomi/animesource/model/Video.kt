package eu.kanade.tachiyomi.animesource.model

import android.net.Uri
import okhttp3.Headers

/** A sub/dub track, lib-14 ABI. */
data class Track(val url: String, val lang: String)

/**
 * Clean-room Aniyomi lib-14 video ABI.
 *
 * Both constructors are load-bearing: older extensions call the `uri` secondary
 * form, newer ones the primary. `videoUrl` is the playable file; `url` is the
 * hoster page it came from.
 */
@Suppress("unused")
data class Video(
    val url: String,
    val quality: String,
    var videoUrl: String?,
    val headers: Headers? = null,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList()
) {
    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        uri: Uri? = null,
        headers: Headers? = null
    ) : this(url, quality, videoUrl, headers)
}