package com.anisequel.data.extensions.runtime

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The runtime's network seam: an OkHttp client that owns no sockets.
 *
 * Every request an extension makes is serialised and handed to the host over
 * [IRuntimeHost], which performs it with the app's own client and returns the
 * bytes. The extension never sees the host's client, its cache, or its tokens -
 * it sees a normal OkHttp client whose interceptor happens to cross a process
 * boundary. This is what lets the isolated process work without the INTERNET
 * permission.
 */
object RuntimeNetwork {

    @Volatile
    var host: IRuntimeHost? = null

    const val userAgent: String =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .addInterceptor(TunnelInterceptor())
            .build()
    }

    private val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }
    }

    @Volatile
    private var installed = false

    /** Registers the services an extension's inlined `injectLazy`/`parseAs` expects. */
    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            Injekt.addSingletonFactory(fullType<NetworkHelper>()) { NetworkHelper() }
            Injekt.addSingleton(fullType<Json>(), json)
            installed = true
        }
    }

    private class TunnelInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val channel = host ?: throw IOException("The extension runtime is not attached to the app")
            val request = chain.request()
            val body = request.body?.let { requestBody ->
                Buffer().also { requestBody.writeTo(it) }.readByteArray()
            }
            val payload = RuntimeProtocol.fetchRequest(
                RuntimeFetchRequest(
                    url = request.url.toString(),
                    method = request.method,
                    headers = request.headers.map { it.first to it.second },
                    body = body
                )
            )
            val answer = RuntimeProtocol.fetchResponseFrom(RuntimeProtocol.unwrap(channel.fetch(payload)))
            val headers = okhttp3.Headers.Builder().apply {
                answer.headers.forEach { (name, value) -> add(name, value) }
            }.build()
            val contentType = headers["Content-Type"]?.toMediaTypeOrNull()
            return Response.Builder()
                .request(Request.Builder().url(answer.url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(answer.code)
                .message(answer.message.ifBlank { "OK" })
                .headers(headers)
                .body(answer.body.toResponseBody(contentType))
                .build()
        }
    }
}