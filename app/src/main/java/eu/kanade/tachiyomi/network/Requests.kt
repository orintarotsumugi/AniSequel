package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Clean-room Aniyomi request-builder ABI (`GET`/`POST` helpers). */
@Suppress("unused")
private val DEFAULT_CACHE_CONTROL: CacheControl = CacheControl.Builder().build()
private val DEFAULT_HEADERS: Headers = Headers.Builder().build()
private val DEFAULT_BODY: RequestBody = ByteArray(0).toRequestBody(null)

@Suppress("unused")
fun GET(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL
): Request = Request.Builder().url(url).headers(headers).cacheControl(cache).get().build()

@Suppress("unused")
fun GET(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL
): Request = Request.Builder().url(url).headers(headers).cacheControl(cache).get().build()

@Suppress("unused")
fun POST(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL
): Request = Request.Builder().url(url).headers(headers).cacheControl(cache).post(body).build()