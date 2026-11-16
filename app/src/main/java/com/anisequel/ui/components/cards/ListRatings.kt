package com.anisequel.ui.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.anisequel.data.model.MediaListEntryItem
import kotlin.math.roundToInt

/**
 * Whose list a library card is drawing.
 *
 * The distinction exists because the same card is drawn for two different owners -
 * the reader's own Library and somebody else's profile - and the two are configured
 * separately. It is *not* a fact about the entry: an AniList list entry carries one
 * score, and it belongs to whoever's list it came from. That is why this is a
 * parameter rather than, say, `entry.score != null` meaning "mine".
 */
enum class ListOwner {
    /** The signed-in reader's own list, on the Library page. */
    MINE,

    /** Somebody else's list, on their profile. */
    THEIRS
}

/**
 * The two rating switches, read together.
 *
 * Both default to on: the personal score is the number a reader opens a library for,
 * and the community average is the fallback that has always been drawn. Off means
 * "show the average instead", never "show nothing" - an unrated entry has no personal
 * number to hide, and a card that lost its only number would look broken.
 */
data class ListRatingVisibility(
    val showMine: Boolean = true,
    val showTheirs: Boolean = true
) {
    fun shows(owner: ListOwner): Boolean = when (owner) {
        ListOwner.MINE -> showMine
        ListOwner.THEIRS -> showTheirs
    }

    companion object {
        val DEFAULT = ListRatingVisibility()
    }
}

/**
 * Handed down from the app root, beside [LocalCardDesign].
 *
 * A composition local for the same reason the card design is one: the switch lives in
 * Settings, the cards live in two screens and four list modes, and threading a
 * boolean through every layer between them is how one caller ends up not being told.
 */
val LocalListRatings = staticCompositionLocalOf { ListRatingVisibility.DEFAULT }

/**
 * The number a library card prints as its rating.
 *
 * ## Why this is one function
 *
 * The two lists used to answer this differently. The borrowed list preferred the
 * entry's own score and fell back to the community average; the new-form Library read
 * the average outright. So on your own Library "84%" was what AniList's community
 * thought, and on a follower's profile "84%" was what *they* thought - the same glyphs
 * meaning two different numbers depending on which page you were on.
 *
 * Both now read this. The entry's score is the owner's own rating (AniList returns it
 * in POINT_100 on every list the app fetches, including other users'), the average is
 * the fallback for an unrated entry, and the reader's switch decides whether the
 * personal number is preferred.
 */
@Composable
fun libraryRating(entry: MediaListEntryItem, owner: ListOwner): Int? {
    val given = givenRating(entry)
    val visible = LocalListRatings.current.shows(owner)
    return if (given != null && visible) given else communityRating(entry)
}

/**
 * The score the entry's owner gave it, or null when they never scored it.
 *
 * `> 0` because AniList writes 0 for "not scored" rather than omitting the field: a
 * zero here is a missing rating, and drawing it as "0%" would be a verdict the owner
 * never gave.
 *
 * Split out of [libraryRating] so "does this entry have a rating, and what is it" has
 * one definition: the list row, the poster caption and the profile's own row all ask
 * the same question, and three copies of the `0`-means-missing rule is three places
 * for it to drift.
 */
fun givenRating(entry: MediaListEntryItem): Int? = entry.score?.roundToInt()?.takeIf { it > 0 }

/** The community average, or null when AniList has none for the title. */
fun communityRating(entry: MediaListEntryItem): Int? =
    entry.media.averageScore?.takeIf { it > 0 }
