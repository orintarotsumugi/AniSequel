package com.anisequel.data.extensions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val WATCH_PREFERENCES_FILE = "anisequel_watch"
private const val KEY_MAPPINGS = "mappings_v1"
private const val KEY_PROGRESS = "progress_v1"
private const val KEY_WATCHLIST = "watchlist_v1"
private const val KEY_LAYOUT = "episode_layout"
private const val KEY_ARTWORK = "episode_artwork"
private const val KEY_SERVER = "preferred_server"
private const val KEY_SOURCE_SETTINGS = "source_settings_v1"

/** How much of an episode card the list draws. The screenshot's three choices. */
enum class EpisodeLayout(
    val storageValue: String,
    val label: String,
    val blurb: String
) {
    MINIMAL(
        storageValue = "minimal",
        label = "Minimal",
        blurb = "Simple layout displaying episode number, title and progress without thumbnail"
    ),
    COMPACT(
        storageValue = "compact",
        label = "Compact",
        blurb = "Compact card with episode thumbnail and title"
    ),
    DETAILED(
        storageValue = "detailed",
        label = "Detailed",
        blurb = "Full-width detailed card with large thumbnail and episode metadata"
    );

    companion object {
        val DEFAULT = DETAILED
        fun fromStorage(value: String?): EpisodeLayout =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * The source result the reader chose for one AniList title.
 *
 * A mapping is per AniList id, not per title string: two shows can share a
 * name, and the Watch page is opened from a media id. Re-searching a title that
 * already has a mapping is an explicit "Wrong Title?" action, not something a
 * recomposition can undo.
 */
data class SourceMapping(
    val mediaId: Int,
    val sourceId: String,
    val animeId: String,
    val animeTitle: String,
    val coverUrl: String?,
    val updatedAt: Long = System.currentTimeMillis()
)

/** The resume point for one title's current episode. */
data class WatchProgress(
    val mediaId: Int,
    val sourceId: String,
    val animeId: String,
    val animeTitle: String,
    val coverUrl: String?,
    val episodeId: String,
    val episodeName: String,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val fraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    val isNearlyFinished: Boolean
        get() = durationMs > 0L && positionMs >= durationMs - 15_000L
}

/**
 * The source filters from the Gotaku/Sora settings page.
 *
 * These live on the Watch side rather than in [ExtensionPreferences] because
 * they describe how a *playback source* is asked for files, not which
 * repositories the extension catalogue knows about. Two stores also means the
 * Extension Manager can keep its own NSFW switch while a reader hides 18+
 * sources from the Watch page without changing the catalogue.
 *
 * [preferredQuality] and [hideNsfw] are the two values that change this beta:
 * the server picker uses the quality to auto-select a matching file, and the
 * source sheet hides NSFW rows. The server-type, audio and hoster fields are
 * persisted for the sandboxed extension runtimes that will report those labels;
 * Internet Archive serves a direct file and a quality only, and the settings
 * page says so rather than pretending the stored filters changed the list.
 */
data class SourceSettings(
    val preferredType: String = "All",
    val preferredQuality: String = "1080p",
    val preferredServerType: String = "Streaming (Embed)",
    val preferredAudioType: String = "Soft Sub",
    val excludedAudioTypes: Set<String> = emptySet(),
    val showDownloadHosters: Boolean = false,
    val hideNsfw: Boolean = true
) {
    companion object {
        val DEFAULT = SourceSettings()
        val TYPES = listOf("All", "Anime", "Manga", "Novel")
        val QUALITIES = listOf("Auto", "480p", "720p", "1080p", "1440p", "2160p")
        val SERVER_TYPES = listOf("Streaming (Embed)", "Direct file", "Auto")
        val AUDIO_TYPES = listOf("Soft Sub", "Hard Sub", "Dub", "Raw", "Auto")
    }
}

/**
 * Source choice, progress and episode-list preferences.
 *
 * One file for the Watch page's four small facts. They are written from the
 * player's tick, the episode sheet and the source sheet, so a DataStore key per
 * fact would be four suspend hops for one screen; SharedPreferences is the same
 * trade the app's Device page already documents.
 */
class WatchPreferences(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(WATCH_PREFERENCES_FILE, Context.MODE_PRIVATE)

    private val _mappings = MutableStateFlow(readMappings())
    val mappings: StateFlow<Map<Int, SourceMapping>> = _mappings.asStateFlow()

    private val _progress = MutableStateFlow(readProgress())
    val progress: StateFlow<Map<Int, WatchProgress>> = _progress.asStateFlow()

    private val _watchlist = MutableStateFlow(readWatchlist())
    val watchlist: StateFlow<Set<Int>> = _watchlist.asStateFlow()

    private val _episodeLayout = MutableStateFlow(
        EpisodeLayout.fromStorage(preferences.getString(KEY_LAYOUT, null))
    )
    val episodeLayout: StateFlow<EpisodeLayout> = _episodeLayout.asStateFlow()

    private val _showArtwork = MutableStateFlow(preferences.getBoolean(KEY_ARTWORK, true))
    val showArtwork: StateFlow<Boolean> = _showArtwork.asStateFlow()

    private val _preferredServer = MutableStateFlow(preferences.getString(KEY_SERVER, null))
    val preferredServer: StateFlow<String?> = _preferredServer.asStateFlow()

    private val _sourceSettings = MutableStateFlow(readSourceSettings())
    val sourceSettings: StateFlow<SourceSettings> = _sourceSettings.asStateFlow()

    fun saveMapping(mapping: SourceMapping) {
        val next = _mappings.value + (mapping.mediaId to mapping)
        preferences.edit().putString(KEY_MAPPINGS, mappingsToJson(next)).apply()
        _mappings.value = next
        // A different source means a different episode id space, so a resume point
        // from the old source would point at an episode that does not exist. The
        // reader picked a new source on purpose; the old position is not theirs to
        // resume from.
        if (_progress.value[mapping.mediaId]?.sourceId != mapping.sourceId) {
            clearProgress(mapping.mediaId)
        }
    }

    fun clearMapping(mediaId: Int) {
        val next = _mappings.value - mediaId
        preferences.edit().putString(KEY_MAPPINGS, mappingsToJson(next)).apply()
        _mappings.value = next
    }

    fun saveProgress(progress: WatchProgress) {
        val next = _progress.value + (progress.mediaId to progress)
        preferences.edit().putString(KEY_PROGRESS, progressToJson(next)).apply()
        _progress.value = next
    }

    fun clearProgress(mediaId: Int) {
        val next = _progress.value - mediaId
        preferences.edit().putString(KEY_PROGRESS, progressToJson(next)).apply()
        _progress.value = next
    }

    fun toggleWatchlist(mediaId: Int) {
        val next = if (mediaId in _watchlist.value) {
            _watchlist.value - mediaId
        } else {
            _watchlist.value + mediaId
        }
        preferences.edit().putString(KEY_WATCHLIST, JSONArray(next.toList()).toString()).apply()
        _watchlist.value = next
    }

    fun setEpisodeLayout(layout: EpisodeLayout) {
        preferences.edit().putString(KEY_LAYOUT, layout.storageValue).apply()
        _episodeLayout.value = layout
    }

    fun setShowArtwork(show: Boolean) {
        preferences.edit().putBoolean(KEY_ARTWORK, show).apply()
        _showArtwork.value = show
    }

    fun setPreferredServer(server: String?) {
        preferences.edit().putString(KEY_SERVER, server).apply()
        _preferredServer.value = server
    }

    fun setSourceSettings(settings: SourceSettings) {
        preferences.edit()
            .putString(KEY_SOURCE_SETTINGS, sourceSettingsToJson(settings))
            .apply()
        _sourceSettings.value = settings
    }

    fun updateSourceSettings(transform: (SourceSettings) -> SourceSettings) {
        setSourceSettings(transform(_sourceSettings.value))
    }

    private fun readMappings(): Map<Int, SourceMapping> {
        val raw = preferences.getString(KEY_MAPPINGS, null) ?: return emptyMap()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<Int, SourceMapping>()
        obj.keys().forEach { key ->
            val mediaId = key.toIntOrNull() ?: return@forEach
            val item = obj.optJSONObject(key) ?: return@forEach
            val sourceId = item.optString("sourceId").takeIf { it.isNotBlank() } ?: return@forEach
            val animeId = item.optString("animeId").takeIf { it.isNotBlank() } ?: return@forEach
            out[mediaId] = SourceMapping(
                mediaId = mediaId,
                sourceId = sourceId,
                animeId = animeId,
                animeTitle = item.optString("animeTitle"),
                coverUrl = item.optString("coverUrl").takeIf { it.isNotBlank() },
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
        }
        return out
    }

    private fun readProgress(): Map<Int, WatchProgress> {
        val raw = preferences.getString(KEY_PROGRESS, null) ?: return emptyMap()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<Int, WatchProgress>()
        obj.keys().forEach { key ->
            val mediaId = key.toIntOrNull() ?: return@forEach
            val item = obj.optJSONObject(key) ?: return@forEach
            val episodeId = item.optString("episodeId").takeIf { it.isNotBlank() } ?: return@forEach
            out[mediaId] = WatchProgress(
                mediaId = mediaId,
                sourceId = item.optString("sourceId"),
                animeId = item.optString("animeId"),
                animeTitle = item.optString("animeTitle"),
                coverUrl = item.optString("coverUrl").takeIf { it.isNotBlank() },
                episodeId = episodeId,
                episodeName = item.optString("episodeName"),
                episodeNumber = item.optInt("episodeNumber", 0),
                positionMs = item.optLong("positionMs", 0),
                durationMs = item.optLong("durationMs", 0),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
        }
        return out
    }

    private fun readWatchlist(): Set<Int> {
        val raw = preferences.getString(KEY_WATCHLIST, null) ?: return emptySet()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                array.optInt(index, 0).takeIf { it > 0 }?.let(::add)
            }
        }
    }

    private fun readSourceSettings(): SourceSettings {
        val raw = preferences.getString(KEY_SOURCE_SETTINGS, null)
            ?: return SourceSettings.DEFAULT
        val obj = runCatching { JSONObject(raw) }.getOrNull()
            ?: return SourceSettings.DEFAULT
        val excluded = obj.optJSONArray("excludedAudioTypes")?.let { array ->
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index)
                        .takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }
        }.orEmpty()
        return SourceSettings(
            preferredType = obj.optString("preferredType")
                .takeIf { it.isNotBlank() } ?: SourceSettings.DEFAULT.preferredType,
            preferredQuality = obj.optString("preferredQuality")
                .takeIf { it.isNotBlank() } ?: SourceSettings.DEFAULT.preferredQuality,
            preferredServerType = obj.optString("preferredServerType")
                .takeIf { it.isNotBlank() } ?: SourceSettings.DEFAULT.preferredServerType,
            preferredAudioType = obj.optString("preferredAudioType")
                .takeIf { it.isNotBlank() } ?: SourceSettings.DEFAULT.preferredAudioType,
            excludedAudioTypes = excluded,
            showDownloadHosters = obj.optBoolean(
                "showDownloadHosters",
                SourceSettings.DEFAULT.showDownloadHosters
            ),
            hideNsfw = obj.optBoolean("hideNsfw", SourceSettings.DEFAULT.hideNsfw)
        )
    }

    private fun sourceSettingsToJson(settings: SourceSettings): String = JSONObject()
        .put("preferredType", settings.preferredType)
        .put("preferredQuality", settings.preferredQuality)
        .put("preferredServerType", settings.preferredServerType)
        .put("preferredAudioType", settings.preferredAudioType)
        .put("excludedAudioTypes", JSONArray(settings.excludedAudioTypes.toList()))
        .put("showDownloadHosters", settings.showDownloadHosters)
        .put("hideNsfw", settings.hideNsfw)
        .toString()

    private fun mappingsToJson(mappings: Map<Int, SourceMapping>): String {
        val obj = JSONObject()
        mappings.forEach { (mediaId, mapping) ->
            obj.put(
                mediaId.toString(),
                JSONObject()
                    .put("sourceId", mapping.sourceId)
                    .put("animeId", mapping.animeId)
                    .put("animeTitle", mapping.animeTitle)
                    .put("coverUrl", mapping.coverUrl)
                    .put("updatedAt", mapping.updatedAt)
            )
        }
        return obj.toString()
    }

    private fun progressToJson(progress: Map<Int, WatchProgress>): String {
        val obj = JSONObject()
        progress.forEach { (mediaId, point) ->
            obj.put(
                mediaId.toString(),
                JSONObject()
                    .put("sourceId", point.sourceId)
                    .put("animeId", point.animeId)
                    .put("animeTitle", point.animeTitle)
                    .put("coverUrl", point.coverUrl)
                    .put("episodeId", point.episodeId)
                    .put("episodeName", point.episodeName)
                    .put("episodeNumber", point.episodeNumber)
                    .put("positionMs", point.positionMs)
                    .put("durationMs", point.durationMs)
                    .put("updatedAt", point.updatedAt)
            )
        }
        return obj.toString()
    }
}
