package com.anisequel.domain.usecase

/**
 * The bounds AniList enforces on text the app writes.
 *
 * ## Why these are not `maxLines`-style UI preferences
 *
 * Every one of these is a server rule. `SaveActivityReply.text` is documented
 * `Min: 2, Max: 8000`, and AniList answers a one-character comment with a
 * validation error. The composer used to enable its button on `isNotBlank()`,
 * so the shortest comment the field accepted - a single emoji, one code point -
 * was sent, refused, and printed as "Couldn't post that comment." with no hint
 * that the length was the problem. Discovering a server rule by spending a round
 * trip is the exact cost this file exists to remove, and it is why the draft is
 * checked here rather than only in the store.
 *
 * ## Why a verdict instead of a boolean
 *
 * A disabled button with no explanation reads as a broken button. The composer
 * needs to say *which* rule refused the draft - "needs at least 2 characters" is
 * something the reader can fix, and "can't post" is not - so the check returns
 * the reason rather than a `Boolean`.
 */
object ContentLimits {

    /** `SaveActivityReply.text` minimum, in characters. */
    const val REPLY_MIN = 2

    /** `SaveActivityReply.text` maximum, in characters. */
    const val REPLY_MAX = 8000

    /**
     * `SaveTextActivity.text` minimum, in characters.
     *
     * Five, not two. A text activity is a top-level post and AniList gives it a
     * stricter floor than a comment - documented as Min 5, Max 10000 on the live
     * mutation reference. Reusing [REPLY_MIN] here would enable Post on a
     * four-character status that the server refuses, which is the same round-trip
     * mistake [checkReply]'s KDoc describes.
     */
    const val STATUS_MIN = 5

    /** `SaveTextActivity.text` maximum, in characters. */
    const val STATUS_MAX = 10_000

    /** Why [checkReply] refused a draft, or [Valid]. */
    enum class ReplyVerdict {
        /** Sendable. */
        Valid,

        /** Nothing but whitespace - the floor is about content, not keystrokes. */
        Blank,

        /** Trimmed text shorter than [REPLY_MIN]. */
        TooShort,

        /** Longer than [REPLY_MAX]; the field should have stopped accepting it. */
        TooLong
    }

    /**
     * Checks a reply draft against the API's window.
     *
     * Trims before measuring, because the comment store sends the trimmed text: a
     * draft of two spaces and an `a` is one character to AniList, and measuring the
     * untrimmed string would enable a button for a request the server refuses.
     */
    fun checkReply(text: String): ReplyVerdict {
        val trimmed = text.trim()
        return when {
            trimmed.isEmpty() -> ReplyVerdict.Blank
            trimmed.length < REPLY_MIN -> ReplyVerdict.TooShort
            trimmed.length > REPLY_MAX -> ReplyVerdict.TooLong
            else -> ReplyVerdict.Valid
        }
    }

    /**
     * The draft clipped to the ceiling.
     *
     * Applied on every keystroke rather than at send time so the counter and the
     * text can never disagree: a field that accepts 8,001 characters and fails on
     * send is a comment the reader wrote and cannot post.
     */
    fun clipReply(text: String): String =
        if (text.length <= REPLY_MAX) text else text.take(REPLY_MAX)

    /** Why [checkStatus] refused a text activity's draft, or [StatusVerdict.Valid]. */
    enum class StatusVerdict {
        /** Sendable. */
        Valid,

        /** Nothing but whitespace - the floor is about content, not keystrokes. */
        Blank,

        /** Trimmed text shorter than [STATUS_MIN]. */
        TooShort,

        /** Longer than [STATUS_MAX]; the field should have stopped accepting it. */
        TooLong
    }

    /**
     * Checks a text-activity draft against `SaveTextActivity`'s window.
     *
     * The same trim-then-measure rule [checkReply] follows, for the same reason:
     * the store sends the trimmed text, so a draft of spaces around four
     * characters is four characters to AniList.
     */
    fun checkStatus(text: String): StatusVerdict {
        val trimmed = text.trim()
        return when {
            trimmed.isEmpty() -> StatusVerdict.Blank
            trimmed.length < STATUS_MIN -> StatusVerdict.TooShort
            trimmed.length > STATUS_MAX -> StatusVerdict.TooLong
            else -> StatusVerdict.Valid
        }
    }

    /** A text-activity draft clipped to [STATUS_MAX]. See [clipReply]. */
    fun clipStatus(text: String): String =
        if (text.length <= STATUS_MAX) text else text.take(STATUS_MAX)
}
