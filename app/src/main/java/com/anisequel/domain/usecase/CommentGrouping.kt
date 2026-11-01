package com.anisequel.domain.usecase

/**
 * Which comments are a continuation of the one above them.
 *
 * ## Why a chat view needs this at all
 *
 * A comment thread on AniList is a flat list, and the list is drawn with an
 * avatar, a name and a timestamp on every row. One person typing five short
 * messages therefore costs five headers, and the reader's eye has to clear the
 * same name and the same face each time before it can read the sentence it came
 * for. Collapsing the repeat - the thing every chat app on the phone does - is
 * what turns that list into a conversation.
 *
 * ## The three conditions, and why each one is needed
 *
 * Same author, adjacent row, within [CONTINUATION_WINDOW_SECONDS], and at the same
 * indent depth. Any one of them alone is not enough:
 *
 * - Two people can hold the same display name only in the sense that they cannot;
 *   [Row.authorId] is used rather than the name, and a missing id (AniList does
 *   not always send the author) means the row keeps its header, because an
 *   unknown author is not the same author.
 * - Adjacency is what "back-to-back" means. An answer that came nine rows later
 *   is a separate turn in the conversation, and hiding its header would claim
 *   otherwise.
 * - The window is what separates one burst of typing from the same person's next
 *   comment on the topic tomorrow. A gap of hours with no header looks like one
 *   sentence that was split for no reason.
 * - Depth is not cosmetic here. [ReplyThreader] indents a row under the comment
 *   it answers; merging a row into a header that belongs to a different branch
 *   would draw an answer as if it were a continuation of the wrong person.
 *
 * The result is a set of ids rather than a list of flags, so a caller that reorders
 * or filters rows cannot be left holding a flag that points at the wrong index.
 */
object CommentGrouping {

    /**
     * How close together two comments must be to read as one turn.
     *
     * Ten minutes, the same window the browser chat views use: long enough that a
     * person who types a second message while thinking does not get a repeated
     * header, short enough that the same person's reply to somebody else an hour
     * later is clearly a new turn.
     */
    const val CONTINUATION_WINDOW_SECONDS = 10 * 60

    /** One drawn row, reduced to what grouping can see. */
    data class Row(
        val id: Int,
        val authorId: Int?,
        val createdAt: Int?,
        val depth: Int = 0
    )

    /**
     * The rows that continue the row above them.
     *
     * Never includes the first row: there is nothing above it to continue, and a
     * thread that opened with a headerless comment would look broken rather than
     * compact.
     */
    fun continuationIds(rows: List<Row>): Set<Int> {
        if (rows.size < 2) return emptySet()
        val grouped = LinkedHashSet<Int>(rows.size)
        for (index in 1 until rows.size) {
            val row = rows[index]
            val previous = rows[index - 1]
            val author = row.authorId ?: continue
            if (previous.authorId != author) continue
            if (row.depth != previous.depth) continue
            val then = previous.createdAt
            val now = row.createdAt
            if (then == null || now == null) continue
            if (now - then > CONTINUATION_WINDOW_SECONDS) continue
            if (now < then) continue
            grouped += row.id
        }
        return grouped
    }
}
