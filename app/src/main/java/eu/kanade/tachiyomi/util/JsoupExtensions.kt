package eu.kanade.tachiyomi.util

import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

/** Clean-room Aniyomi Jsoup ABI. The base URL is kept so `absUrl` resolves. */
fun Response.asJsoup(html: String? = null): Document {
    val body = body ?: throw IOException("Response has no body")
    return Jsoup.parse(html ?: body.string(), request.url.toString())
}