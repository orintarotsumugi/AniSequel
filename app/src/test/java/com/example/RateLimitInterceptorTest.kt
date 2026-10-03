package com.example

import com.example.data.network.RateLimitInterceptor
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rate-limit retry, and the value it must refuse to trust.
 *
 * `Retry-After` is free text from the server. A negative value used to pass the
 * `> MAX_RETRY_AFTER_SECONDS` upper-bound check - because -1 is not greater than
 * 5 - and reach `Thread.sleep(-1000)`, which throws `IllegalArgumentException`
 * out of `intercept`. That turned a "please slow down" 429 into an exception
 * thrown on an OkHttp worker thread, which is a far worse outcome than simply
 * reporting the rate limit.
 *
 * These call `intercept` directly against a fake chain rather than going through
 * a live client, so they exercise the parsing and the sleep decision without
 * actually sleeping for a real number of seconds.
 */
class RateLimitInterceptorTest {

    private class FakeChain(
        private val responses: MutableList<Response>
    ) : Interceptor.Chain {
        var proceedCalls = 0
            private set

        override fun request(): Request = Request.Builder().url("https://graphql.anilist.co/").build()

        override fun proceed(request: Request): Response {
            proceedCalls++
            return responses.removeAt(0)
        }

        override fun connection() = null
        override fun call() = throw UnsupportedOperationException()
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }

    private fun response(code: Int, retryAfter: String?): Response {
        val builder = Response.Builder()
            .request(Request.Builder().url("https://graphql.anilist.co/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body("".toResponseBody(null))
        if (retryAfter != null) builder.header("Retry-After", retryAfter)
        return builder.build()
    }

    @Test
    fun `a negative retry-after is handed back instead of slept on`() {
        val chain = FakeChain(mutableListOf(response(429, "-1")))

        // The bug: this used to throw IllegalArgumentException out of intercept.
        val result = RateLimitInterceptor().intercept(chain)

        assertEquals(429, result.code)
        assertEquals("a negative wait must not trigger a retry", 1, chain.proceedCalls)
    }

    @Test
    fun `a retry-after above the cap is not retried`() {
        val chain = FakeChain(mutableListOf(response(429, "60")))

        val result = RateLimitInterceptor().intercept(chain)

        assertEquals(429, result.code)
        assertEquals("a long penalty is reported, not hidden behind a retry", 1, chain.proceedCalls)
    }

    @Test
    fun `a missing retry-after is not retried`() {
        val chain = FakeChain(mutableListOf(response(429, null)))

        val result = RateLimitInterceptor().intercept(chain)

        assertEquals(429, result.code)
        assertEquals(1, chain.proceedCalls)
    }

    @Test
    fun `garbage in the retry-after header is not retried`() {
        val chain = FakeChain(mutableListOf(response(429, "later")))

        val result = RateLimitInterceptor().intercept(chain)

        assertEquals(429, result.code)
        assertEquals(1, chain.proceedCalls)
    }

    @Test
    fun `a zero-second wait retries exactly once`() {
        val chain = FakeChain(
            mutableListOf(
                response(429, "0"),
                response(200, null)
            )
        )

        val result = RateLimitInterceptor().intercept(chain)

        assertEquals(200, result.code)
        assertEquals("one extra attempt, never a loop", 2, chain.proceedCalls)
    }

    @Test
    fun `a success is passed straight through`() {
        val chain = FakeChain(mutableListOf(response(200, null)))

        val result = RateLimitInterceptor().intercept(chain)

        assertEquals(200, result.code)
        assertEquals(1, chain.proceedCalls)
    }
}
