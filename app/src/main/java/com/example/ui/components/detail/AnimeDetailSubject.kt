package com.example.ui.components.detail

import com.example.data.model.ArrivingEntry
import com.example.data.model.MediaCoverImage
import com.example.data.model.MediaNode
import com.example.data.model.MediaTitle
import com.example.data.model.MissedSequel
import com.example.data.model.NextAiringEpisode
import com.example.data.model.SimpleMediaListEntry

/**
 * What the detail sheet is showing: a sequel recommendation, or a plain list or
 * activity entry that has no parent.
 *
 * One type so the sheet, its header, its specs tabs and its action row take a
 * single parameter instead of two nullable ones every consumer has to unwrap.
 * This is the [ArrivingEntry] doc's warning seen from the UI side: rather than
 * a fake [MissedSequel] with a meaningless parent, a row with no parent is a
 * [Media].
 *
 * The media content is the same [MediaNode] either way; only the parent
 * narrative and the add-to-list action are sequel-specific, and both are gated
 * on [sequel] being non-null.
 */
sealed interface AnimeDetailSubject {
    val media: MediaNode
    val mediaId: Int get() = media.id

    /** The watched entry this is a sequel to, or null for a plain entry. */
    val sequel: MissedSequel? get() = null

    /** A franchise recommendation, with a parent the viewer already watched. */
    data class Sequel(override val sequel: MissedSequel) : AnimeDetailSubject {
        override val media: MediaNode get() = sequel.sequelMedia
    }

    /** A show the viewer already tracks: an arriving row or an activity item. */
    data class Media(override val media: MediaNode) : AnimeDetailSubject
}

/**
 * The display node for an arriving row.
 *
 * [ArrivingEntry] is a summary - id, title, status, schedule, progress - and the
 * sheet's detail query fills in the synopsis, artwork, studio and the rest. This
 * builds the summary's own node so the sheet has something to draw on the first
 * frame, before that response lands.
 */
fun ArrivingEntry.toDetailMedia(): MediaNode = MediaNode(
    id = mediaId,
    title = MediaTitle(userPreferred = title),
    status = status,
    episodes = episodes,
    startDate = startDate,
    coverImage = MediaCoverImage(large = coverUrl, color = coverColor),
    nextAiringEpisode = if (nextAiringAt != null || nextEpisodeNumber != null) {
        NextAiringEpisode(episode = nextEpisodeNumber, airingAt = nextAiringAt)
    } else {
        null
    },
    mediaListEntry = progress?.let { SimpleMediaListEntry(progress = it) }
)
