package com.anisequel.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * The followed users who have one title on their list, as AniList answers it.
 *
 * ## Where this comes from
 *
 * `Page.mediaList(mediaId:, type:, isFollowing: true)` - one request for every
 * followed user's entry on a title. The obvious alternative, asking for the
 * viewer's following list and then one `MediaList` per person, is N+1 requests
 * against an API currently degraded to 30 a minute, and the card that draws this
 * is a footnote on a page the reader opened for something else.
 *
 * `isFollowing` filters to the *authenticated* viewer's follows, so a signed-out
 * session answers with an error or an empty page. The store swallows both and the
 * card simply does not draw; friends are never worth an error message over.
 *
 * ## Why the payload is a page and not a list
 *
 * Because that is the shape the connection has, and unwrapping it in the query
 * is not possible - GraphQL has no way to say "just the nodes". [MediaFriendsPage]
 * is therefore also where the one-page limit is documented: AniList caps `perPage`
 * at 50 and this asks for 50, so a viewer following more than fifty people with
 * this title on their lists sees the first fifty. Paging would mean a second and
 * third round trip on every title open for a case that is rare and cosmetic.
 */
@JsonClass(generateAdapter = true)
data class MediaFriendsData(
    @Json(name = "Page") val page: MediaFriendsPage? = null
)

@JsonClass(generateAdapter = true)
data class MediaFriendsPage(
    val mediaList: List<MediaFriendEntry>? = null
)

/**
 * One followed user's list entry for the title.
 *
 * [user] is nullable even though the query always selects it: a list entry whose
 * owner has since been deleted comes back with no user at all, and
 * [toMediaFriend] drops those rather than drawing an avatar with no name.
 *
 * [score] is asked for as `score(format: POINT_10_DECIMAL)` rather than the bare
 * field. The bare field is in whatever scale that *user* scores on - one person's
 * 85 is another's 8.5 - so a row of friends' ratings would be four incompatible
 * numbers side by side. Normalising at the query is the only place that can be
 * done correctly.
 */
@JsonClass(generateAdapter = true)
data class MediaFriendEntry(
    val status: String? = null,
    val score: Double? = null,
    val progress: Int? = null,
    val progressVolumes: Int? = null,
    val user: FollowUser? = null
)

/**
 * One friend's entry, in the shape the Info page draws.
 *
 * A separate type from [MediaFriendEntry] because the wire model carries the
 * things that are only interesting *before* the row is drawn - the raw status
 * enum, a score in AniList's "zero means unrated" convention - and the card
 * should not have to remember which of those mean what. The mapping, the
 * filtering and the ordering all happen once, in the repository, so the same
 * title opened twice cannot come back in two different orders.
 */
data class MediaFriend(
    val userId: Int,
    val name: String,
    val avatarUrl: String?,
    /** The rating on AniList's 0-10 decimal scale, or null when unrated. */
    val score: Double?,
    /** Episodes watched, or chapters read. Never negative. */
    val progress: Int,
    /** Volumes read, manga only. Null when the friend has not tracked any. */
    val progressVolumes: Int?
)

/**
 * The rating as the row prints it - "8.5", "8" - or null when there is none.
 *
 * AniList reports an unrated entry as `0`, not as null, so a bare `score` would
 * print "0" for everyone who finished a show without scoring it. The trailing
 * `.0` is dropped because "8.0" reads like a measurement and "8" reads like a
 * rating.
 */
val MediaFriend.scoreLabel: String?
    get() = score
        ?.takeIf { it > 0.0 }
        ?.let { String.format(Locale.ROOT, "%.1f", it).removeSuffix(".0") }

/**
 * How far this friend got: "12 / 24 eps", or "12 eps" when the total is unknown.
 *
 * The total comes from the *media*, not from the entry - a list entry carries no
 * episode count - and a title AniList has no length for says only what the friend
 * has watched rather than "12 / null".
 */
fun MediaFriend.progressLabel(isManga: Boolean, total: Int?): String {
    val noun = if (isManga) "chs" else "eps"
    val knownTotal = total?.takeIf { it > 0 }
    return if (knownTotal != null) "$progress / $knownTotal $noun" else "$progress $noun"
}

/**
 * The whole row as one TalkBack sentence.
 *
 * The visible row is three separate `Text`s (name, rating, progress) plus an
 * avatar with no description. Read in order that is "Mika, 8.5, 12 / 24 eps",
 * which a screen reader pronounces as a slash and a list of fragments. One
 * merged description says the same thing in words, and it is the only thing the
 * row conveys, so nothing is lost by merging it.
 *
 * Volumes are spoken but not drawn: a manga row has one line for its progress
 * and "12 / 50 chs, 3 volumes" does not fit the column the avatar sets.
 */
fun MediaFriend.spokenLabel(isManga: Boolean, total: Int?): String {
    val noun = if (isManga) "chapter" else "episode"
    val knownTotal = total?.takeIf { it > 0 }
    val progressText = when {
        knownTotal != null -> "$progress of $knownTotal ${noun}s"
        progress == 1 -> "1 $noun"
        else -> "$progress ${noun}s"
    }
    val volumesText = progressVolumes
        ?.takeIf { isManga && it > 0 }
        ?.let { ", $it ${if (it == 1) "volume" else "volumes"}" }
        .orEmpty()
    val rating = scoreLabel?.let { "rated $it" } ?: "not rated"
    return "$name, $rating, $progressText$volumesText"
}

/**
 * A wire entry as a drawable friend, or null when the row should not be drawn.
 *
 * `PLANNING` is the one status that means the friend has *not* watched or read
 * the title yet - the card exists to answer "has anyone I follow seen this", and
 * a plan is the answer "no, not yet" with an avatar on it. Every other status,
 * including `DROPPED` and `PAUSED`, means they did watch some of it and that is
 * worth knowing.
 */
fun MediaFriendEntry.toMediaFriend(): MediaFriend? {
    val person = user ?: return null
    if (status.equals("PLANNING", ignoreCase = true)) return null
    return MediaFriend(
        userId = person.id,
        name = person.name,
        avatarUrl = person.avatar?.large ?: person.avatar?.medium,
        score = score,
        progress = (progress ?: 0).coerceAtLeast(0),
        progressVolumes = progressVolumes
    )
}

/**
 * Best rating first, then furthest through, then by name.
 *
 * Client-side because the connection's sort argument is not one this app has
 * verified against the live API, and because the ordering is a display decision:
 * the reader wants the friend whose taste they trust at the front, not whatever
 * order the list happens to be stored in.
 */
fun List<MediaFriend>.sortedForDisplay(): List<MediaFriend> = sortedWith(
    compareByDescending<MediaFriend> { it.score?.takeIf { score -> score > 0.0 } ?: -1.0 }
        .thenByDescending { it.progress }
        .thenBy { it.name.lowercase(Locale.ROOT) }
)
