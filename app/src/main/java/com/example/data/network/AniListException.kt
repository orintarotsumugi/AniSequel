package com.example.data.network

import com.example.data.model.AniListErrorEnvelope
import com.example.data.model.GraphQLError
import com.squareup.moshi.Moshi

/**
 * Why a call to AniList failed, as something the UI can branch on.
 *
 * The screen used to decide what to show by looking for words in an exception
 * message (`contains("invalid")`, `contains("token")`), which meant any error
 * that happened to include the word "invalid" was treated as a dead session and
 * offered a "Sign in again" button. Classifying at the boundary keeps that
 * decision honest.
 */
enum class AniListErrorKind {
    /** The stored token was rejected. Re-authenticating is the only fix. */
    INVALID_SESSION,

    /** AniList's rate limiter (or burst limiter) rejected the request. */
    RATE_LIMITED,

    /** The device has no usable connection. */
    OFFLINE,

    /** AniList returned 5xx. */
    SERVER,

    /** The requested user or media does not exist. */
    NOT_FOUND,

    /** The server rejected the query or the mutation. */
    INVALID_REQUEST,

    /**
     * AniList answered successfully with a body this build cannot parse.
     *
     * Its own kind because it is neither a dead session nor a transport
     * failure: retrying re-sends the same request and gets the same body, and
     * "Sign in again" cannot help. What helps is a build whose models match
     * the API.
     */
    MALFORMED_RESPONSE,

    UNKNOWN
}

class AniListException(
    val kind: AniListErrorKind,
    message: String,
    /** Seconds to wait, from the `Retry-After` header, when [kind] is [AniListErrorKind.RATE_LIMITED]. */
    val retryAfterSeconds: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

private val errorEnvelopeMoshi: Moshi = Moshi.Builder().build()

/**
 * Reads the `errors` array out of an AniList error response body.
 *
 * AniList returns a GraphQL error with a non-2xx status, and Retrofit never
 * gives that body to a suspend function whose return type is not
 * `Response<T>` - it throws `HttpException` and the reason is lost unless it is
 * read here. A body that is absent, empty or not this envelope parses to null
 * rather than throwing, because a failure to *classify* must never replace the
 * failure the user actually had.
 */
internal fun parseAniListErrorBody(body: String?): AniListErrorEnvelope? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        errorEnvelopeMoshi.adapter(AniListErrorEnvelope::class.java).fromJson(body)
    }.getOrNull()
}

/**
 * Classifies an AniList GraphQL error by what it says.
 *
 * [GraphQLError.status] is preferred over the message wherever the two can be
 * trusted, but **400 is deliberately not one of those statuses**: AniList uses
 * it for a rejected token *and* for a malformed query, so on its own it says
 * nothing. Only the message separates them - "Invalid token" versus
 * "Cannot query field ... on type ..." - and signing a working user out over a
 * typo in a query would be worse than the misclassification being fixed here.
 */
internal fun GraphQLError.toAniListException(): AniListException {
    val text = message.orEmpty()

    // AniList's own wording for an auth failure, which never overlaps with the
    // wording it uses for a bad query.
    val reportsAuthFailure = text.contains("token", ignoreCase = true) ||
        text.contains("unauthorized", ignoreCase = true) ||
        text.contains("unauthenticated", ignoreCase = true) ||
        text.contains("access denied", ignoreCase = true)

    return when {
        status == 429 -> AniListException(
            kind = AniListErrorKind.RATE_LIMITED,
            message = "AniList is rate limiting this device. Try again in a moment."
        )
        status == 401 || status == 403 -> AniListException(
            kind = AniListErrorKind.INVALID_SESSION,
            message = "Session expired. Please re-authenticate."
        )
        status == 404 || text.contains("not found", ignoreCase = true) ->
            AniListException(AniListErrorKind.NOT_FOUND, text)
        reportsAuthFailure -> AniListException(
            kind = AniListErrorKind.INVALID_SESSION,
            message = "Session expired. Please re-authenticate."
        )
        else -> AniListException(AniListErrorKind.INVALID_REQUEST, text)
    }
}

/**
 * Turns an HTTP status into a classified failure.
 *
 * Separate from the Retrofit plumbing so the mapping can be reasoned about (and
 * tested) without constructing a network response.
 *
 * [reportedError] is the first entry of `errors` parsed out of the response
 * body. It is not an optimisation, it is the whole point: AniList answers a
 * rejected token with **HTTP 400** and
 * `{"errors":[{"message":"Invalid token","status":400}]}`, which is the same
 * status it uses for a malformed query. Classifying on the status alone made
 * every dead session an unknown error, so the dashboard offered a Retry button
 * that could never succeed instead of "Sign in again", and `AuthViewModel` -
 * which only discards a token AniList has refused - stored the refused token
 * and signed the user straight into it.
 */
fun aniListHttpError(
    code: Int,
    retryAfterSeconds: Int? = null,
    cause: Throwable? = null,
    reportedError: GraphQLError? = null
): AniListException {
    // Verdicts the body cannot overturn, decided first. 429 is also the only
    // place `Retry-After` exists, so it has to be answered here.
    if (code == 401 || code == 403) {
        return AniListException(
            kind = AniListErrorKind.INVALID_SESSION,
            message = "Session expired. Please re-authenticate.",
            cause = cause
        )
    }
    if (code == 429) {
        return AniListException(
            kind = AniListErrorKind.RATE_LIMITED,
            message = "AniList is rate limiting this device. Try again in ${retryAfterSeconds ?: 60}s.",
            retryAfterSeconds = retryAfterSeconds,
            cause = cause
        )
    }
    if (code in 500..599) {
        return AniListException(
            kind = AniListErrorKind.SERVER,
            message = "AniList is having trouble right now (HTTP $code). Try again shortly.",
            cause = cause
        )
    }

    // 400 and 404 are the statuses AniList overloads, so the body decides.
    if (reportedError != null) return reportedError.toAniListException()

    return when (code) {
        404 -> AniListException(
            kind = AniListErrorKind.NOT_FOUND,
            message = "Not found on AniList.",
            cause = cause
        )
        else -> AniListException(
            kind = AniListErrorKind.UNKNOWN,
            message = "Unexpected response from AniList (HTTP $code).",
            cause = cause
        )
    }
}
