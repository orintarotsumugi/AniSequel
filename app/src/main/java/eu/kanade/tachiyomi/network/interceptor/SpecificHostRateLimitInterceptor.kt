package eu.kanade.tachiyomi.network.interceptor

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Clean-room Aniyomi per-host rate-limit ABI; see [rateLimit]. */
@Suppress("unused", "DEPRECATION")
@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS
): OkHttpClient.Builder = rateLimitHost(httpUrl, permits, unit.toMillis(period).milliseconds)

fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Duration = 1.seconds
): OkHttpClient.Builder = addInterceptor(
    WindowRateLimiter(permits, period.inWholeMilliseconds) { it.host == httpUrl.host }
)

@Suppress("unused")
fun OkHttpClient.Builder.rateLimitHost(
    url: String,
    permits: Int,
    period: Duration = 1.seconds
): OkHttpClient.Builder = rateLimitHost(url.toHttpUrl(), permits, period)