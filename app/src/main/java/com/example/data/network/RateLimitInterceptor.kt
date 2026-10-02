package com.example.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Absorbs the short rate-limit spikes AniList's burst limiter produces.
 *
 * AniList documents 90 requests per minute - currently degraded to 30 - plus a
 * burst limiter that rejects requests that arrive too close together. When it
 * trips, the response carries `Retry-After` with the number of seconds to wait.
 *
 * The retry here is deliberately narrow:
 *
 *  - **One** extra attempt, not a loop. A client that retries until it succeeds
 *    is indistinguishable from the hammering the limiter exists to stop.
 *  - Only when the wait is [MAX_RETRY_AFTER_SECONDS] or less. A 60-second
 *    penalty is not something to hide behind a spinner; it is reported to the
 *    user as a rate-limit error with the wait time so they can decide.
 */
class RateLimitInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != HTTP_TOO_MANY_REQUESTS) return response

        val waitSeconds = response.header("Retry-After")?.toIntOrNull()
        if (waitSeconds == null || waitSeconds > MAX_RETRY_AFTER_SECONDS) {
            return response
        }

        response.close()
        Thread.sleep(TimeUnit.SECONDS.toMillis(waitSeconds.toLong()))

        val retry = chain.proceed(chain.request())
        // If the retry is throttled again, the caller sees the 429 and the
        // repository turns it into a rate-limit error with the real wait time.
        return retry
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_RETRY_AFTER_SECONDS = 5
    }
}