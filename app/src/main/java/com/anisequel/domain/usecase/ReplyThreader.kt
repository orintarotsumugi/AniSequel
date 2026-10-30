package com.anisequel.domain.usecase

/**
 * Works out which replies are answers to which, from the leading `@name`.
 *
 * ## Why this is parsing text at all
 *
 * AniList stores a comment thread as one flat list. The only thing in the data
 * that says "this is a reply to that" is the `@name` the composer inserts, so
 * that mention - not an id, because there is none - is the whole thread
 * structure. Reconstructing it is purely local: no extra request, no change to
 * the query, and a thread whose mention is edited simply re-threads itself.
 *
 * ## What it deliberately does not claim
 *
 * A mention only becomes a level if the named author has already posted in this
 * thread, and only against the *most recent* comment by that author - the one a
 * reader would have been answering. A mention of somebody who has not replied
 * here (an activity owner, a person quoted from elsewhere) is left at root, which
 * is the safe answer: an indent is a claim about who was answering whom, and a
 * wrong claim makes the thread harder to read than a flat list.
 *
 * Depth is capped at [MAX_DEPTH] because four levels of 18dp indent is four
 * levels of narrower text on a phone. Past the cap the mention still says who is
 * being answered; the layout just stops shrinking.
 */
object ReplyThreader {

    /** One reply, reduced to the two fields threading needs. */
    data class Reply(
        val id: Int,
        val author: String?,
        val text: String?
    )

    /**
     * The most levels the UI may indent. See the KDoc above.
     *
     * Level 0 is the thread's own left margin; a level is one indent step.
     */
    const val MAX_DEPTH = 2

    /** AniList's usernames: letters, digits and underscores, case-insensitive. */
    private val LEADING_MENTION = Regex("""^@([A-Za-z0-9_]+)""")

    /**
     * The indent level of every reply id, in the order they appear.
     *
     * Replies keep their server order; the map only says how far in each row
     * draws. Ids missing from the map (which cannot happen for an input reply)
     * read as level zero at the call site.
     */
    fun depths(replies: List<Reply>): Map<Int, Int> {
        if (replies.isEmpty()) return emptyMap()

        val depthById = LinkedHashMap<Int, Int>(replies.size * 2)
        // The latest reply id per author, lowercased. A reply mentioning an
        // author with several comments answers the last one, which is the one
        // that was on screen under the composer.
        val latestByAuthor = HashMap<String, Int>(replies.size * 2)

        replies.forEach { reply ->
            val mentioned = leadingMention(reply.text)
            val parentId = mentioned?.let { latestByAuthor[it] }
            val depth = parentId
                ?.let { (depthById[it] ?: 0) + 1 }
                ?.coerceAtMost(MAX_DEPTH)
                ?: 0
            depthById[reply.id] = depth

            // Stored after the lookup, so a comment that mentions its own author
            // does not become its own parent.
            reply.author?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { author ->
                latestByAuthor[author] = reply.id
            }
        }

        return depthById
    }

    /**
     * The username after a leading `@`, lowercased, or null.
     *
     * Leading whitespace is skipped because the composer's mention starts the
     * body on a new line when the reader had already typed something - and a
     * reply that began "\n@name" is still an answer to them.
     */
    fun leadingMention(text: String?): String? {
        val trimmed = text?.trimStart() ?: return null
        return LEADING_MENTION.find(trimmed)?.groupValues?.getOrNull(1)?.lowercase()
    }
}
