package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Clean-room Aniyomi rate-limit ABI. A sliding window of `permits` requests per
 * `period`; the calling thread waits when the window is full.
 */
@Suppress("unused", "DEPRECATION")
@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS
): OkHttpClient.Builder = rateLimit(permits, unit.toMillis(period).milliseconds)

fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds
): OkHttpClient.Builder = addInterceptor(WindowRateLimiter(permits, period.inWholeMilliseconds))

internal class WindowRateLimiter(
    private val permits: Int,
    private val periodMs: Long,
    private val matches: (okhttp3.HttpUrl) -> Boolean = { true }
) : Interceptor {
    private val stamps = ArrayDeque<Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        if (matches(chain.request().url)) acquire()
        return chain.proceed(chain.request())
    }

    private fun acquire() {
        if (permits <= 0 || periodMs <= 0) return
        synchronized(stamps) {
            while (true) {
                val now = System.currentTimeMillis()
                while (stamps.isNotEmpty() && now - stamps.first() >= periodMs) stamps.removeFirst()
                if (stamps.size < permits) {
                    stamps.addLast(now)
                    return
                }
                val wait = periodMs - (now - stamps.first())
                if (wait > 0) {
                    runCatching { Thread.sleep(wait) }
                }
            }
        }
    }
}