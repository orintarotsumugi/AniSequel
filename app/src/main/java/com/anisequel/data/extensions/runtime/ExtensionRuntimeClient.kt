package com.anisequel.data.extensions.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import com.anisequel.data.extensions.ExtensionHttp
import com.anisequel.data.extensions.SourceAnime
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The host's handle on the isolated runtime (Phase 2.1).
 *
 * Binder calls block, so each one runs on a worker with a deadline. A call that
 * overruns is cancelled and the service is unbound: with no remaining bindings
 * the system reaps the isolated process, which both frees the blocked worker
 * (its binder dies) and guarantees a hung extension cannot keep the player
 * waiting. The next call binds a fresh process and reloads.
 *
 * The connection is only accepted when it names this app's own service
 * component; `exported="false"` already stops other apps binding, and this
 * guards the host against talking to anything else.
 */
class ExtensionRuntimeClient(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var runtime: IExtensionRuntime? = null

    @Volatile
    private var binding = false

    private val bindLock = Any()
    private val loaded = ConcurrentHashMap<String, List<RuntimeSourceInfo>>()
    private val workers = Executors.newCachedThreadPool { task ->
        Thread(task, "extension-runtime-call").apply { isDaemon = true }
    }
    private val hostChannel = HostChannel()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val expected = ComponentName(appContext, ExtensionRuntimeService::class.java)
            if (name != expected) {
                runCatching { appContext.unbindService(this) }
                binding = false
                return
            }
            runtime = IExtensionRuntime.Stub.asInterface(service)
            binding = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            runtime = null
            binding = false
        }

        override fun onBindingDied(name: ComponentName?) {
            runtime = null
            binding = false
        }

        override fun onNullBinding(name: ComponentName?) {
            runtime = null
            binding = false
        }
    }

    suspend fun load(manifest: AniyomiPackage, apk: File): List<RuntimeSourceInfo> {
        loaded[manifest.packageName]?.let { return it }
        val request = RuntimeProtocol.loadRequest(
            manifest.packageName,
            manifest.sourceClasses,
            manifest.nsfw,
            Process.myUid()
        )
        val payload = ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            invoke { it.load(request, descriptor, hostChannel) }
        }
        val sources = RuntimeProtocol.loadResultFrom(RuntimeProtocol.unwrap(payload))
        loaded[manifest.packageName] = sources
        return sources
    }

    suspend fun search(sourceId: String, query: String): List<SourceAnime> =
        RuntimeProtocol.animesFrom(RuntimeProtocol.unwrap(invoke { it.search(sourceId, query, 1) }))

    suspend fun details(sourceId: String, anime: SourceAnime): SourceAnime {
        val payload = invoke { it.details(sourceId, RuntimeProtocol.animeToJson(anime).toString()) }
        return RuntimeProtocol.animeFrom(RuntimeProtocol.unwrap(payload).getJSONObject("anime"))
    }

    suspend fun episodes(sourceId: String, anime: SourceAnime): List<SourceEpisode> =
        RuntimeProtocol.episodesFrom(
            RuntimeProtocol.unwrap(invoke { it.episodes(sourceId, RuntimeProtocol.animeToJson(anime).toString()) })
        )

    suspend fun resolve(sourceId: String, episode: SourceEpisode): List<SourceServer> =
        RuntimeProtocol.serversFrom(
            RuntimeProtocol.unwrap(invoke { it.resolve(sourceId, RuntimeProtocol.episodeToJson(episode).toString()) })
        )

    fun forget(packageName: String) {
        loaded.remove(packageName)
    }

    private suspend fun invoke(block: (IExtensionRuntime) -> String): String {
        val service = ensureRuntime()
        val payload = withContext(Dispatchers.IO) {
            val future = workers.submit(Callable { block(service) })
            try {
                future.get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                future.cancel(true)
                hardReset()
                throw IOException("The extension timed out and its process was restarted")
            } catch (error: ExecutionException) {
                throw IOException(error.cause?.message ?: "The extension call failed", error.cause)
            }
        }
        return payload
    }

    private suspend fun ensureRuntime(): IExtensionRuntime {
        runtime?.let { return it }
        synchronized(bindLock) {
            if (runtime == null && !binding) {
                binding = true
                val intent = Intent(appContext, ExtensionRuntimeService::class.java)
                val accepted = runCatching {
                    appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                }.getOrDefault(false)
                if (!accepted) {
                    binding = false
                    runCatching { appContext.unbindService(connection) }
                    throw IOException("The extension runtime could not be started")
                }
            }
        }
        return withTimeout(BIND_TIMEOUT_MS) {
            while (runtime == null) delay(50)
            runtime ?: throw IOException("The extension runtime disconnected")
        }
    }

    private fun hardReset() {
        synchronized(bindLock) {
            runtime = null
            binding = false
            runCatching { appContext.unbindService(connection) }
        }
        loaded.clear()
    }

    /**
     * The host end of the network tunnel. Runs on a binder thread and performs
     * the request with the app's extension client - never the AniList client,
     * which carries the viewer's token. Responses are size-capped.
     */
    private inner class HostChannel : IRuntimeHost.Stub() {
        override fun fetch(requestJson: String?): String = RuntimeProtocol.envelope {
            val request = RuntimeProtocol.fetchRequestFrom(requestJson.orEmpty())
            if (!request.url.startsWith("http://") && !request.url.startsWith("https://")) {
                throw IOException("Extensions may only request http(s) addresses")
            }
            val builder = Request.Builder().url(request.url)
            request.headers.forEach { (name, value) -> builder.addHeader(name, value) }
            if (request.headers.none { it.first.equals("User-Agent", ignoreCase = true) }) {
                builder.header("User-Agent", USER_AGENT)
            }
            val method = request.method.uppercase()
            val body = request.body
            when {
                body != null -> builder.method(method, body.toRequestBody())
                method in setOf("POST", "PUT", "PATCH") -> builder.method(method, ByteArray(0).toRequestBody())
                else -> builder.method(method, null)
            }
            ExtensionHttp.client.newCall(builder.build()).execute().use { response ->
                val bytes = response.body?.byteStream()?.use { readCapped(it) } ?: ByteArray(0)
                val headers = response.headers.map { it.first to it.second }
                RuntimeProtocol.fetchResponseObject(
                    response.code,
                    response.message,
                    response.request.url.toString(),
                    headers,
                    bytes
                )
            }
        }
    }

    private fun readCapped(stream: InputStream): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(chunk)
            if (read == -1) break
            total += read
            if (total > MAX_FETCH_BYTES) throw IOException("The extension's response is too large")
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val CALL_TIMEOUT_MS = 45_000L
        const val BIND_TIMEOUT_MS = 15_000L
        const val MAX_FETCH_BYTES = 16 * 1024 * 1024
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}