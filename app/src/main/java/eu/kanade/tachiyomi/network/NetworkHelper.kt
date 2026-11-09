package eu.kanade.tachiyomi.network

import com.anisequel.data.extensions.runtime.RuntimeNetwork
import okhttp3.OkHttpClient

/**
 * Clean-room Aniyomi network-helper ABI.
 *
 * The real helper owns an OkHttp client with direct network access. An isolated
 * process has no INTERNET permission, so this one hands back the runtime's
 * tunnelled client: every request is proxied through the host. Extensions see
 * the same `client` / `cloudflareClient` / `defaultUserAgentProvider` surface.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class NetworkHelper {
    val client: OkHttpClient get() = RuntimeNetwork.client
    val cloudflareClient: OkHttpClient get() = client
    fun defaultUserAgentProvider(): String = RuntimeNetwork.userAgent
}