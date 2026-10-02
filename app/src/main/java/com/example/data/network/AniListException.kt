package com.example.data.network

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

    UNKNOWN
}

class AniListException(
    val kind: AniListErrorKind,
    message: String,
    /** Seconds to wait, from the `Retry-After` header, when [kind] is [AniListErrorKind.RATE_LIMITED]. */
    val retryAfterSeconds: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Turns an HTTP status into a classified failure.
 *
 * Separate from the Retrofit plumbing so the mapping can be reasoned about (and
 * tested) without constructing a network response.
 */
fun aniListHttpError(
    code: Int,
    retryAfterSeconds: Int? = null,
    cause: Throwable? = null
): AniListException = when (code) {
    401, 403 -> AniListException(
        kind = AniListErrorKind.INVALID_SESSION,
        message = "Session expired. Please re-authenticate.",
        cause = cause
    )
    429 -> AniListException(
        kind = AniListErrorKind.RATE_LIMITED,
        message = "AniList is rate limiting this device. Try again in ${retryAfterSeconds ?: 60}s.",
        retryAfterSeconds = retryAfterSeconds,
        cause = cause
    )
    404 -> AniListException(
        kind = AniListErrorKind.NOT_FOUND,
        message = "Not found on AniList.",
        cause = cause
    )
    in 500..599 -> AniListException(
        kind = AniListErrorKind.SERVER,
        message = "AniList is having trouble right now (HTTP $code). Try again shortly.",
        cause = cause
    )
    else -> AniListException(
        kind = AniListErrorKind.UNKNOWN,
        message = "Unexpected response from AniList (HTTP $code).",
        cause = cause
    )
}