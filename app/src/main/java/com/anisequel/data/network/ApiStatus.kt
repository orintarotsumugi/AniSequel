package com.anisequel.data.network

/**
 * What AniList looks like from here, in words the reader can act on.
 *
 * "Connection error" was the whole vocabulary: it means "the internet", "their
 * server", "our token" and "a bug in this build" all at once, and the advice
 * that follows it ("try again") is wrong for half of those. This enum is the
 * distinction the error screen draws the moment something fails.
 *
 * [UNKNOWN] is deliberately its own value rather than a synonym for "offline".
 * A probe that could not run has told us nothing, and a screen that says
 * "AniList is down" in that case is guessing.
 */
enum class ApiReachability {
    /** Nothing has been checked, or the check itself could not complete. */
    UNKNOWN,

    /** A real request round-tripped in a reasonable time. */
    ONLINE,

    /** A request completed, but took long enough that it counts as a warning. */
    SLOW,

    /** AniList answered - it just asked for fewer requests first. */
    RATE_LIMITED,

    /** This device has no usable connection, so nothing left it. */
    OFFLINE,

    /** The request reached AniList and came back as a server-side failure. */
    DOWN,

    /** A request went out and nothing answered before the probe gave up. */
    NO_ANSWER;

    /** True only when AniList actually answered us. */
    val isReachable: Boolean
        get() = this == ONLINE || this == SLOW

    /** The short label the status strip leads with. */
    val label: String
        get() = when (this) {
            UNKNOWN -> "AniList status unknown"
            ONLINE -> "AniList is reachable"
            SLOW -> "AniList is reachable, slowly"
            RATE_LIMITED -> "AniList is rate limiting"
            OFFLINE -> "AniList is unreachable"
            DOWN -> "AniList is down"
            NO_ANSWER -> "AniList is not answering"
        }

    /**
     * The sentence under [label], which has to explain *why* the verdict is
     * what it is - a status that says "unreachable" next to a working Wi-Fi
     * icon is more confusing than no status at all.
     */
    fun detail(latencyMillis: Long? = null): String = when (this) {
        UNKNOWN -> "The check did not finish, so this is not a verdict either way."
        ONLINE -> latencyMillis
            ?.let { "A test request came back in ${formatLatency(it)}." }
            ?: "A test request came back."
        SLOW -> latencyMillis
            ?.let { "A test request took ${formatLatency(it)}. It is there, just busy." }
            ?: "A test request came back, but slowly."
        RATE_LIMITED -> "The last request was refused for arriving too fast. Give it a minute."
        OFFLINE -> "This device has no usable connection, so nothing can leave it."
        DOWN -> "The request reached AniList and came back as a server error."
        NO_ANSWER -> "The test request timed out before AniList answered."
    }
}

/**
 * One probe's result.
 *
 * [latencyMillis] is present only when the request actually completed, which is
 * what lets the strip say "came back in 240 ms" instead of merely "reachable".
 */
data class ApiStatusSnapshot(
    val reachability: ApiReachability,
    val latencyMillis: Long? = null
)

/**
 * Friendly copy for one failure kind.
 *
 * The joke is a [List] rather than a single line so repeated failures do not
 * show the same sentence every time, and [quipFor] picks one deterministically
 * from the seed it is given - deterministic so that a recomposition cannot
 * reshuffle the joke under the reader's eyes.
 *
 * Callers that want a *retry* to land on a different line append a per-instance
 * random seed to the key: seeding on `kind:message` alone is stable across every
 * retry of the same failure, which is the loop the pool exists to break. See
 * `ApiFailureBody` and `ProfileErrorView`.
 */
data class ApiErrorCopy(
    val headline: String,
    val detail: String,
    val quips: List<String>
) {
    fun quipFor(seed: String): String = quips[seed.hashCode().mod(quips.size)]
}

/**
 * Whether this failure is worth spending a request to diagnose.
 *
 * Only the failures that leave the reachability genuinely ambiguous qualify.
 * A rejected session, a 404 or a malformed reply *proves* AniList answered, so
 * probing would only restate what we already know - and doing it during a rate
 * limit would stack another refused request on top of the one that just failed.
 */
fun AniListErrorKind.isWorthProbing(): Boolean = when (this) {
    AniListErrorKind.OFFLINE,
    AniListErrorKind.SERVER,
    AniListErrorKind.UNKNOWN -> true

    else -> false
}

/**
 * Whether this failure is a connection problem rather than an answer.
 *
 * Drives the heartbreak: a dead token or a missing profile is AniList talking to
 * us, so a broken heart would be theatre - the session needs a sign-in, not
 * sympathy. Everything that leaves reachability in doubt earns the animation.
 */
fun AniListErrorKind?.isConnectionTrouble(): Boolean =
    toReachability() != ApiReachability.ONLINE

/**
 * The reachability this failure kind already proves, before any probe runs.
 *
 * A failed request is evidence. If AniList rejected the token, or said it had no
 * such user, then AniList was obviously reachable - so the status strip can say
 * so immediately instead of leaving a spinner up for a check that can only
 * agree.
 */
fun AniListErrorKind?.toReachability(): ApiReachability = when (this) {
    AniListErrorKind.OFFLINE -> ApiReachability.OFFLINE
    AniListErrorKind.SERVER -> ApiReachability.DOWN
    AniListErrorKind.RATE_LIMITED -> ApiReachability.RATE_LIMITED
    AniListErrorKind.INVALID_SESSION,
    AniListErrorKind.NOT_FOUND,
    AniListErrorKind.INVALID_REQUEST,
    AniListErrorKind.MALFORMED_RESPONSE -> ApiReachability.ONLINE

    AniListErrorKind.UNKNOWN, null -> ApiReachability.UNKNOWN
}

/**
 * The error screen's voice: what broke, what it means, and one line of levity.
 *
 * ## Why "Ani-chan"
 *
 * AniList's failures are, from this app's side, always somebody else's server
 * taking a nap. Naming that somebody gives the screen a subject to be fond of
 * instead of an API to be angry at, which is the difference between "AniList
 * connection error" and a message a person can read without sighing.
 *
 * ## Why the pools are this large
 *
 * A retry loop is exactly when the same joke is seen the most. One line per
 * failure kind stops being funny on the third attempt and starts reading as the
 * app not noticing that anything changed. [DOWN_JOKES] is deliberately a single
 * shared pool for every "it did not answer" failure rather than three
 * per-kind lists: the reader does not care whether the cause was the socket,
 * a 500 or a timeout, only that Ani-chan is not there - and one large pool
 * gives them a different line for far longer than three small ones.
 *
 * None of these are emoji: the app draws its own vectors, and a joke is carried
 * by the words rather than by a face.
 */
fun AniListErrorKind.friendlyCopy(): ApiErrorCopy = when (this) {
    AniListErrorKind.INVALID_SESSION -> ApiErrorCopy(
        headline = "Ani-chan forgot your name",
        detail = "The AniList session has expired. Sign in again and the app will pick up " +
            "exactly where it left off - your list is safe on AniList's side.",
        quips = listOf(
            "It's not you. It's the token.",
            "Ani-chan has an enormous memory for anime and a tiny one for tokens.",
            "Sessions expire. The list does not.",
            "Ani-chan will remember you again after a sign-in.",
            "The token had a good run.",
            "You are logged out - emotionally and technically.",
            "Ani-chan needs to see some ID.",
            "The session packed its bags and left a note."
        )
    )

    AniListErrorKind.RATE_LIMITED -> ApiErrorCopy(
        headline = "Ani-chan needs a breather",
        detail = "AniList limits how often apps may ask. Give it a minute and the same " +
            "request will go through.",
        quips = listOf(
            "Too many questions at once.",
            "Ani-chan is holding up one finger: one at a time, please.",
            "The API equivalent of \"wait your turn\".",
            "Ani-chan is not ignoring you. Ani-chan is pacing itself.",
            "Less refresh spam, more patience.",
            "You are going faster than the plot.",
            "The refresh button is not a slot machine.",
            "Ani-chan can only read so fast.",
            "One request per breath. House rules.",
            "That was not a challenge. It was a limit.",
            "Ani-chan is asking you to slow your roll.",
            "The request queue is a queue, not a suggestion.",
            "You are spending requests like they are free. They are not."
        )
    )

    AniListErrorKind.OFFLINE -> ApiErrorCopy(
        headline = "Ani-chan can't hear you",
        detail = "This device has no usable connection right now. Check Wi-Fi or mobile " +
            "data, then try again.",
        quips = OFFLINE_JOKES
    )

    AniListErrorKind.SERVER -> ApiErrorCopy(
        headline = "Ani-chan is having a nap",
        detail = "AniList's servers are not answering right now. Nothing on this device is " +
            "broken, and nothing you did caused it.",
        quips = DOWN_JOKES
    )

    AniListErrorKind.NOT_FOUND -> ApiErrorCopy(
        headline = "Ani-chan can't find that one",
        detail = "AniList has no record under that name. Check the spelling - the account " +
            "may also have been renamed or deleted.",
        quips = listOf(
            "It is not hiding. It genuinely is not there.",
            "Ani-chan checked under the couch.",
            "Maybe it is going by a different name now.",
            "Ani-chan looked. Twice.",
            "This profile is in the witness protection program now.",
            "Ani-chan searched the whole wardrobe. Nothing.",
            "It exists in your heart, not in the database."
        )
    )

    AniListErrorKind.MALFORMED_RESPONSE -> ApiErrorCopy(
        headline = "Ani-chan said something odd",
        detail = "AniList answered in a shape this version cannot read. Updating the app " +
            "usually fixes it.",
        quips = listOf(
            "That reply was in a language we do not speak.",
            "Ani-chan sneezed mid-sentence.",
            "The JSON arrived wearing a fake moustache.",
            "We asked for a list and got a riddle.",
            "The parser looked at it and went home.",
            "Looks like a list. Smells like a list. Refuses to parse like one.",
            "Ani-chan sent a haiku instead of a schema."
        )
    )

    AniListErrorKind.INVALID_REQUEST -> ApiErrorCopy(
        headline = "Ani-chan didn't understand that",
        detail = "AniList rejected the request. This is a bug on our side, not something " +
            "you did - trying again is still worth a shot.",
        quips = listOf(
            "The query was valid in our head.",
            "Ani-chan tilted its head.",
            "That request made sense right up until it did not.",
            "This one is our bug, not yours.",
            "We wrote the question. AniList disagreed with the grammar.",
            "The request was declined by the server's taste."
        )
    )

    AniListErrorKind.UNKNOWN -> ApiErrorCopy(
        headline = "Ani-chan is confused",
        detail = "Something unexpected happened while talking to AniList. Trying again is " +
            "the honest suggestion.",
        quips = listOf(
            "That answer made no sense to us either.",
            "Ani-chan shrugged. We are shrugging with it.",
            "Something happened. It was not the documented thing.",
            "The API blinked first.",
            "Ani-chan is improvising.",
            "This is either a bug or a very fancy feature.",
            "The server sent a postcard. No return address."
        )
    )
}

/**
 * Copy for a *probe result*, where there is no exception to classify.
 *
 * The connection dialog and the recovery moment know only what the test request
 * did, so they need the same voice as the error screen without inventing an
 * [AniListErrorKind] to get it. Reachable results get their own pool on
 * purpose: "Ani-chan is awake" should not be one of the lines that also appears
 * when everything is on fire.
 */
fun ApiReachability.friendlyCopy(): ApiErrorCopy = when (this) {
    ApiReachability.ONLINE,
    ApiReachability.SLOW -> ApiErrorCopy(
        headline = "Ani-chan is awake",
        detail = detail(),
        quips = listOf(
            "Heart intact. Go watch something.",
            "The server is up. The watchlist is not going to shrink itself.",
            "All clear. Ani-chan even combed its hair for this.",
            "Reachable, responsive, and mildly smug about it.",
            "No glue required today."
        )
    )

    ApiReachability.OFFLINE -> AniListErrorKind.OFFLINE.friendlyCopy()
    ApiReachability.DOWN -> AniListErrorKind.SERVER.friendlyCopy()
    ApiReachability.RATE_LIMITED -> AniListErrorKind.RATE_LIMITED.friendlyCopy()
    ApiReachability.UNKNOWN -> AniListErrorKind.UNKNOWN.friendlyCopy()

    ApiReachability.NO_ANSWER -> ApiErrorCopy(
        headline = "Ani-chan is not picking up",
        detail = "The test request went out and nothing came back before we gave up. " +
            "AniList may be very slow, or something between here and there is dropping it.",
        quips = DOWN_JOKES
    )
}

/**
 * The big one: every joke about AniList not being there.
 *
 * Shared by [AniListErrorKind.SERVER] and every "no answer" result, because the
 * reader's experience is identical in all of them. Nearly seventy lines is not
 * excess: the whole point is that a person hammering Retry does not see the same
 * sentence twice before they give up, and that a joke which lands badly for one
 * reader is not the only one on offer.
 */
private val DOWN_JOKES = listOf(
    "Shhh. Ani-chan is sleeping.",
    "Ani-chan tripped over a server cable.",
    "A server fan stopped spinning somewhere in the world.",
    "Ani-chan rebooted and cannot find its socks.",
    "AniList is having a day. So is the poor server.",
    "Maintenance: the anime way of saying \"brb\".",
    "Ani-chan is buffering its feelings.",
    "The server is on a training arc. It will be back stronger.",
    "Ani-chan went to get a glass of water. It has been forty minutes.",
    "The database is doing its best.",
    "Somewhere, a load balancer is crying.",
    "Ani-chan is currently held together by hope and duct tape.",
    "The backend has entered its filler-arc era.",
    "AniList is rebooting. Please imagine a tiny progress bar.",
    "A wild 500 appeared. It is not very effective.",
    "Ani-chan is meditating. Loudly.",
    "The servers are having a group nap.",
    "Ani-chan spilled ramen on the racks.",
    "Even the cloud needs a rainy day.",
    "Ani-chan is waiting for the next episode of its own uptime.",
    "The health check came back with a shrug.",
    "Ani-chan is out buying milk. Classic.",
    "Somewhere, a sysadmin is typing very fast.",
    "The API is doing the anime thing where it stares dramatically at the horizon.",
    "Ani-chan is on a side quest.",
    "The server room is currently a vibe, not a service.",
    "Ani-chan is asleep in a pile of figurines.",
    "The uptime graph is doing modern art.",
    "Ani-chan put the database in the washing machine. Again.",
    "This is not a bug. It is a plot twist.",
    "Ani-chan is charging its aura.",
    "The request is fine. The server is on break.",
    "Ani-chan is having a small, temporary existential crisis.",
    "Somewhere, a cache is missing.",
    "The API is in its silent-protagonist arc.",
    "Ani-chan is at the convenience store. Do not wait up.",
    "The server is doing cardio. It will be back out of breath.",
    "Ani-chan forgot its lines.",
    "The backend is rebooting in the way that means \"soon\".",
    "Nobody panic. Except the server. It is panicking.",
    "Ani-chan is practicing its dramatic pause.",
    "The server went to the beach. It left a note.",
    "Ani-chan is recompiling its soul.",
    "The 500 is a cry for help, not a status code.",
    "Ani-chan is waiting for the dub to come out.",
    "The racks are warm, the API is cold.",
    "Ani-chan rolled a natural one.",
    "This episode is a recap. The servers are recapping.",
    "Ani-chan is rereading the same chapter until the server comes back.",
    "Somebody deployed on a Friday. We all know how this ends.",
    "Ani-chan is in the onsen. The steam got into the racks.",
    "The request is queued behind a very long opening theme.",
    "Ani-chan is waiting for the recap episode to finish. It has been twenty minutes.",
    "The backend is having a beach episode.",
    "Ani-chan is monologuing before it answers. It always does this.",
    "The server is in a flashback arc. Give it a moment.",
    "Ani-chan dropped the database. It bounced. Twice.",
    "The uptime needle is twitching ominously. That is a technical term.",
    "Ani-chan is stuck in an isekai where the Wi-Fi is a plot device.",
    "The server is powering up. Give it three episodes.",
    "Ani-chan is resolving a cliffhanger with the database.",
    "The CDN is having a filler arc.",
    "Ani-chan is waiting for the sub to drop. Any minute now.",
    "The load balancer rolled a critical failure.",
    "Ani-chan is in the recap movie. Same frames, no new data.",
    "The request reached the server and was never seen again.",
    "Ani-chan is doing the thing where it stands dramatically in the rain.",
    "Somewhere a cron job is crying in production.",
    "Ani-chan is re-reading the error log like it is a light novel.",
    "The server is in a coma arc. The doctors say three episodes.",
    "Ani-chan rolled the credits early and hopes you did not notice.",
    "The response is stuck behind a very slow gate.",
    "Ani-chan is buffering its opening theme.",
    "Somewhere, a container restarted and took the plot with it.",
    "The request went to the beach episode. It will be back with no new information.",
    "Ani-chan is practicing its comeback monologue. It needs a minute.",
    "The database is doing that thing where it stares out of a train window.",
    "Ani-chan tripped on the ethernet cable it was told not to touch.",
    "The uptime is fine. The uptime is lying.",
    "The API is in a training arc. Nobody knows how long those take.",
    "Ani-chan is waiting for its turn in the queue. There is no queue.",
    "Somewhere, a health check just said 'lol'.",
    "The server is pretending to be a background character.",
    "Ani-chan is doing the recap. Same plot, no data.",
    "The backend is powering up with a very long stock-footage sequence.",
    "Ani-chan took the day off to reread its own documentation.",
    "The request reached the server and was politely declined by the void.",
    "Ani-chan is on a vision quest. It forgot to bring the response.",
    "The socket is open. The answer is not in it.",
    "Ani-chan is doing that thing where it stands on a cliff until sunrise.",
    "Ani-chan is waiting for the filler arc to end. So are we."
)

/** Offline deserves its own voice: the problem is the reader's side, not Ani-chan's. */
private val OFFLINE_JOKES = DOWN_JOKES + listOf(
    "Even Ani-chan needs a signal.",
    "The request is ready. The internet is not.",
    "Somewhere between here and AniList, a cable is loose.",
    "Your Wi-Fi and AniList are not currently on speaking terms.",
    "Airplane mode: the one setting AniList cannot argue with.",
    "This is the part where the anime cuts to static.",
    "Ani-chan is knocking. Nobody is opening the door.",
    "The router blinkenlights are not speaking API.",
    "The data is out there. Somewhere.",
    "Your packets went out for cigarettes and did not come back."
)

/** "240 ms" below a second, "3.2 s" above it. */
internal fun formatLatency(latencyMillis: Long): String =
    if (latencyMillis < 1_000L) {
        "$latencyMillis ms"
    } else {
        String.format(java.util.Locale.US, "%.1f s", latencyMillis / 1_000.0)
    }
