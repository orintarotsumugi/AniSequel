package com.anisequel.ui.navigation

import android.net.Uri
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.pageUrl
import com.anisequel.data.model.posterUrl
import com.anisequel.data.model.studioName

/**
 * The AniList facts the Watch page needs before a source has answered.
 *
 * The page is opened from the detail sheet, which already holds a `MediaNode`;
 * passing the four visible facts through the route means the hero draws on the
 * first frame instead of showing a blank title while a source is searched. The
 * source's own title and cover supersede them once a match lands - that is what
 * the "Found: ..." card is for.
 */
data class WatchAnime(
    val id: Int,
    val title: String,
    val coverUrl: String?,
    val bannerUrl: String?,
    val studio: String?,
    val status: String?,
    val format: String?,
    val episodeCount: Int?,
    val year: Int?,
    val score: Int?,
    val listStatus: String?,
    val anilistUrl: String
)

/**
 * Routes for the watch flow: the title page and the player.
 *
 * The player takes no arguments; see [com.anisequel.data.extensions.PlaybackQueue]
 * for why. The Watch route is a pattern with query arguments rather than a single
 * JSON blob because Navigation can restore each argument on process death and a
 * JSON-encoded argument has no type information to restore it with.
 */
object WatchRoutes {
    const val WATCH = "anime_watch/{mediaId}" +
        "?title={title}&cover={cover}&banner={banner}&studio={studio}" +
        "&status={status}&format={format}&episodes={episodes}&year={year}" +
        "&score={score}&listStatus={listStatus}&anilistUrl={anilistUrl}"

    const val PLAYER = "watch_player"

    val watchArguments = listOf(
        navArgument("mediaId") { type = NavType.IntType },
        navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("cover") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("banner") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("studio") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("status") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("format") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("episodes") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("year") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("score") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("listStatus") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("anilistUrl") { type = NavType.StringType; nullable = true; defaultValue = null }
    )

    /** Builds the route from the title the reader opened. */
    fun forMedia(media: MediaNode): String {
        fun enc(value: String?): String = Uri.encode(value.orEmpty())
        return "anime_watch/${media.id}" +
            "?title=${enc(media.displayName)}" +
            "&cover=${enc(media.posterUrl)}" +
            "&banner=${enc(media.bannerImage)}" +
            "&studio=${enc(media.studioName)}" +
            "&status=${enc(media.status)}" +
            "&format=${enc(media.format)}" +
            "&episodes=${enc(media.episodes?.toString())}" +
            "&year=${enc(media.seasonYear?.toString())}" +
            "&score=${enc(media.averageScore?.toString())}" +
            "&listStatus=${enc(media.mediaListEntry?.status)}" +
            "&anilistUrl=${enc(media.pageUrl)}"
    }

    fun from(entry: NavBackStackEntry): WatchAnime {
        val args = entry.arguments
        return WatchAnime(
            id = args?.getInt("mediaId") ?: 0,
            title = args?.getString("title").orEmpty().ifBlank { "Anime" },
            coverUrl = args?.getString("cover")?.takeIf { it.isNotBlank() },
            bannerUrl = args?.getString("banner")?.takeIf { it.isNotBlank() },
            studio = args?.getString("studio")?.takeIf { it.isNotBlank() },
            status = args?.getString("status")?.takeIf { it.isNotBlank() },
            format = args?.getString("format")?.takeIf { it.isNotBlank() },
            episodeCount = args?.getString("episodes")?.toIntOrNull(),
            year = args?.getString("year")?.toIntOrNull(),
            score = args?.getString("score")?.toIntOrNull(),
            listStatus = args?.getString("listStatus")?.takeIf { it.isNotBlank() },
            anilistUrl = args?.getString("anilistUrl").orEmpty()
                .ifBlank { "https://anilist.co/anime/${args?.getInt("mediaId") ?: 0}" }
        )
    }
}
