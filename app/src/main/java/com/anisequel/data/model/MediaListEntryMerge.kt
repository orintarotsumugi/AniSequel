package com.anisequel.data.model

/**
 * The viewer's own entry, folded from a summary and the detail answer.
 *
 * ## The bug this exists for
 *
 * `MediaNode.withDetail` used to leave `mediaListEntry` alone, which reads as
 * "the summary wins" but was "the summary is the only one that counts": a
 * `MediaListCollection` response puts the status on the list entry rather than on
 * the entry's own media, so a Library row's node arrived with a null entry and
 * left with one — the detail query's `mediaListEntry { status progress }`
 * discarded. The Info page then had no status to draw and offered "Add to
 * Planning" on a completed show, whose AniList mutation overwrites the entry and
 * would have moved it back to Planning.
 *
 * ## Which side wins
 *
 * The summary wins every field it has. That is the opposite of the other fields
 * `withDetail` merges, and deliberate:
 *
 *  - `withListStatus` folds a list row's status into the summary at the tap site
 *    so the title page does not wait for a fetch, and that status is what the
 *    reader was just looking at.
 *  - `withSummaryProgress` exists to keep the summary's viewer data over the
 *    detail's for the same reason.
 *  - A detail answer can be a cached snapshot from before the reader's last edit;
 *    the summary is the node the host recomposes from after a list refresh.
 *
 * The detail fills the blanks, which is the half that was broken: a summary with
 * no entry at all now gets the fetched one instead of nothing.
 *
 * Fields are folded rather than one entry replacing the other because the two
 * know different things: an arriving row knows the viewer is 4 episodes in but
 * not which list they are on, a list query's entry knows both, and a detail
 * answer knows both for the moment it was fetched.
 * `TrackedListStatusTest` pins both directions.
 */
internal fun mergeListEntry(
    summary: SimpleMediaListEntry?,
    detail: SimpleMediaListEntry?
): SimpleMediaListEntry? = when {
    summary == null -> detail
    detail == null -> summary
    else -> summary.copy(
        status = summary.status ?: detail.status,
        progress = summary.progress ?: detail.progress,
        progressVolumes = summary.progressVolumes ?: detail.progressVolumes,
        score = summary.score ?: detail.score
    )
}
