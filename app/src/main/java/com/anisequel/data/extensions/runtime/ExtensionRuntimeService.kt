package com.anisequel.data.extensions.runtime

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.IOException

/**
 * The sandboxed extension runtime (Phase 2.1).
 *
 * Declared `isolatedProcess="true"` in the manifest: this code runs in a process
 * with its own UID, no permissions and no access to the app's private data. It
 * can only read the APK descriptor the host hands it and call back through
 * [IRuntimeHost] for network. A misbehaving extension crashes or hangs this
 * process, never the player; the host's per-call timeout unbinds and the system
 * reaps the isolated process.
 *
 * The caller identity is captured from the real binder UID at load time, not
 * from the request payload, and every later call is checked against it.
 */
class ExtensionRuntimeService : Service() {

    private val loader = AniyomiLoader()

    @Volatile
    private var hostUid: Int = -1

    override fun onCreate() {
        super.onCreate()
        RuntimeNetwork.install()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : IExtensionRuntime.Stub() {

        override fun load(requestJson: String?, apk: ParcelFileDescriptor?, host: IRuntimeHost?): String =
            RuntimeProtocol.envelope {
                hostUid = Binder.getCallingUid()
                RuntimeNetwork.host = host
                val request = RuntimeProtocol.loadRequestFrom(JSONObject(requestJson.orEmpty()))
                val descriptor = apk ?: throw IOException("No extension file was supplied")
                val sources = descriptor.use { loader.load(it, request) }
                RuntimeProtocol.loadResult(sources)
            }

        override fun search(sourceId: String?, query: String?, page: Int): String = call {
            RuntimeProtocol.animesToJson(loader.source(sourceId.orEmpty()).search(query.orEmpty(), page))
        }

        override fun details(sourceId: String?, animeJson: String?): String = call {
            val anime = RuntimeProtocol.animeFrom(JSONObject(animeJson.orEmpty()))
            val updated = loader.source(sourceId.orEmpty()).details(anime)
            JSONObject().put("anime", RuntimeProtocol.animeToJson(updated))
        }

        override fun episodes(sourceId: String?, animeJson: String?): String = call {
            val anime = RuntimeProtocol.animeFrom(JSONObject(animeJson.orEmpty()))
            RuntimeProtocol.episodesToJson(loader.source(sourceId.orEmpty()).episodes(anime))
        }

        override fun resolve(sourceId: String?, episodeJson: String?): String = call {
            val episode = RuntimeProtocol.episodeFrom(JSONObject(episodeJson.orEmpty()))
            RuntimeProtocol.serversToJson(loader.source(sourceId.orEmpty()).resolve(episode))
        }

        override fun unload(packageName: String?) {
            runCatching {
                checkCaller()
                loader.unload(packageName.orEmpty())
            }
        }
    }

    private fun call(block: suspend () -> JSONObject): String = RuntimeProtocol.envelope {
        checkCaller()
        runBlocking { block() }
    }

    private fun checkCaller() {
        if (hostUid != -1 && Binder.getCallingUid() != hostUid) {
            throw IOException("The caller is not the app that owns this runtime")
        }
    }
}