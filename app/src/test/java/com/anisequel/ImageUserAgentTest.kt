package com.anisequel

import com.anisequel.data.network.NetworkClient
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The cover images went dark and looked like slow loading.
 *
 * The image client used to ship no `User-Agent`, so every cover request left
 * with OkHttp's default `okhttp/4.12.0` - and image CDNs answer that with a
 * 403 (Coil's own tracker documents the class of failure for hosts that check
 * the header). Nothing surfaced it: the API client is a different OkHttpClient
 * and kept working, and the failing images fell back to the same painter as
 * their placeholders, so a forbidden request rendered as "still loading"
 * forever.
 *
 * Pinned at the interceptor itself because the bug is a header on a request,
 * and running a fake chain is the only way to read that header without a
 * socket in the test. (Coil 2's built `ImageLoader` does not expose the client
 * it was built with, so the wiring half is pinned by reading the source, the
 * same way [MinimumSdkReachabilityTest] pins a branch that cannot run.)
 */
class ImageUserAgentTest {

    private class RecordingChain(private val original: Request) : Interceptor.Chain {
        var proceeded: Request? = null

        override fun request(): Request = original

        override fun proceed(request: Request): Response {
            proceeded = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("captured")
                .build()
        }

        override fun connection(): Connection? = null

        override fun call(): Call = throw NotImplementedError("the interceptor must not need the call")

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    @Test
    fun `a cover request leaves with a browser-like user agent`() {
        val chain = RecordingChain(Request.Builder().url("https://cdn.example/cover.jpg").build())

        NetworkClient.ImageUserAgentInterceptor.intercept(chain)

        val agent = requireNotNull(chain.proceeded?.header("User-Agent")) {
            "the image client sent no User-Agent, which is what made every CDN " +
                "answer a 403 the app rendered as a permanent placeholder"
        }
        assertTrue(
            "a UA that still advertises the HTTP library is the string the CDNs " +
                "reject: $agent",
            !agent.startsWith("okhttp")
        )
        assertTrue("expected a Mozilla-compatible string: $agent", agent.startsWith("Mozilla/5.0"))
    }

    @Test
    fun `the interceptor rewrites rather than appends`() {
        val chain = RecordingChain(
            Request.Builder()
                .url("https://cdn.example/cover.jpg")
                .header("User-Agent", "okhttp/4.12.0")
                .build()
        )

        NetworkClient.ImageUserAgentInterceptor.intercept(chain)

        val headers = requireNotNull(chain.proceeded?.headers("User-Agent"))
        assertEquals(
            "two User-Agends is one header the server may read either way",
            1,
            headers.size
        )
    }

    /**
     * The interceptor is on the cover client, not just defined.
     *
     * `createImageLoader` is what every `AsyncImage` in the app resolves to,
     * and a missing `.addInterceptor` line is the whole bug returning. Coil 2
     * gives no way back from a built `ImageLoader` to its client, so this
     * reads the builder source directly - the same instrument [MinimumSdk-
     * ReachabilityTest] uses for a branch no runtime can reach.
     */
    @Test
    fun `the image loader builder registers the interceptor`() {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("no settings.gradle.kts above the working directory")
        }
        val source = File(
            dir,
            "app/src/main/java/com/anisequel/data/network/NetworkClient.kt"
        ).readText()

        val imageLoaderBody = source.substringAfter("fun createImageLoader")

        assertTrue(
            "the cover client is built without the UA interceptor, so CDN " +
                "requests are back to sending the bare okhttp agent and silently 403ing",
            imageLoaderBody.contains(".addInterceptor(ImageUserAgentInterceptor)")
        )
    }
}
