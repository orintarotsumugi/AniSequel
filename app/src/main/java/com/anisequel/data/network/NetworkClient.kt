package com.anisequel.data.network

import android.content.Context
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.anisequel.BuildConfig
import com.anisequel.data.repository.AuthRepository
import com.squareup.moshi.Moshi
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    private const val BASE_URL = "https://graphql.anilist.co/"

    /**
     * The one Moshi for this process.
     *
     * It has two readers now, which is why it is a property rather than a local
     * built inside [buildApiService]: the Retrofit converter reads AniList's
     * answers with it, and `SnapshotStore` writes those same answers to disk and
     * reads them back with it. Two instances would work - Moshi is stateless -
     * but an adapter that can parse a `MediaNode` off the wire and one that can
     * parse it off disk should be the same adapter, or a model change has two
     * places to be right in.
     *
     * No KotlinJsonAdapterFactory: every model in data/model is annotated
     * @JsonClass(generateAdapter = true), so Moshi resolves a generated
     * adapter for each one. The reflective factory was dead weight, and it
     * drags kotlin-reflect into every build that enables R8.
     */
    val json: Moshi = Moshi.Builder()
        .add(String::class.java, FlexibleStringAdapter)
        .build()

    /**
     * The one API service for this process.
     *
     * `createApiService` used to build a fresh `OkHttpClient`, `Retrofit` and
     * `Moshi` on every call, and the app calls it twice on every launch -
     * `MainActivity` builds one for the auth ViewModel and `AppNavigation`
     * builds another for the dashboards. Two clients means two connection pools,
     * two dispatcher thread pools and two DNS caches, and it throws away the
     * whole point of the 60-second keep-alive below: a request made through the
     * second client cannot reuse a connection the first one already has open, so
     * the app pays for a second TLS handshake to AniList on a cold start.
     *
     * Memoised on the repository *identity* rather than on the object being
     * non-null: the service is bound to the `AuthInterceptor` that reads that
     * repository's token, so two different repositories must not share a client.
     * In this app there is only ever one - `AuthRepositoryImpl` is created once
     * in `MainActivity.onCreate` - but a test or a future multi-account build
     * that passed a different one would otherwise silently send the wrong token.
     */
    @Volatile
    private var cachedRepository: AuthRepository? = null

    @Volatile
    private var cachedService: AniListApiService? = null

    @Volatile
    private var cachedClient: OkHttpClient? = null

    private val serviceLock = Any()

    fun createApiService(authRepository: AuthRepository): AniListApiService {
        cachedService?.let { existing ->
            if (cachedRepository === authRepository) return existing
        }

        return synchronized(serviceLock) {
            cachedService?.let { existing ->
                if (cachedRepository === authRepository) return@synchronized existing
            }

            // A previous client pointed at a different repository - or a
            // race lost scenario - would otherwise leak its dispatcher
            // thread pool and keep-alive sockets for the whole session.
            cachedClient?.connectionPool?.evictAll()
            cachedClient?.dispatcher?.executorService?.shutdown()

            val (service, client) = buildApiService(authRepository)
            cachedRepository = authRepository
            cachedService = service
            cachedClient = client
            service
        }
    }

    private fun buildApiService(authRepository: AuthRepository): Pair<AniListApiService, OkHttpClient> {
        val clientBuilder = OkHttpClient.Builder()
            // RateLimitInterceptor first, so it is the *outermost* application
            // interceptor. The comment below has always said the retry has to
            // re-read the token, but the two `addInterceptor` calls were in the
            // opposite order: AuthInterceptor ran first and the retry proceeded
            // *down* the chain from the rate-limit interceptor, so it never
            // passed through AuthInterceptor again. With this order a retried
            // request is re-authorised, which matters after a sign-in refreshes
            // the in-memory token while a request is in flight.
            .addInterceptor(RateLimitInterceptor())
            .addInterceptor(AuthInterceptor(authRepository))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // A hard ceiling on the whole call, not on each read. `readTimeout`
            // resets every time a byte arrives, so a server that trickles the
            // body can hold a call - and the spinner - open indefinitely; only a
            // call timeout bounds that. It has to leave room for the rate-limit
            // interceptor's own retry (up to 5s) plus a second attempt, which is
            // why it is comfortably above the 30s read timeout rather than
            // equal to it.
            .callTimeout(90, TimeUnit.SECONDS)
            // Keep-alive for longer than OkHttp's 5s default. The dashboard
            // fires several requests in quick succession, and a connection torn
            // down between two of them means a fresh TLS handshake to AniList
            // each time - the single largest avoidable cost in a burst.
            .connectionPool(ConnectionPool(5, 60, TimeUnit.SECONDS))
            .retryOnConnectionFailure(true)

        // AniList's limiter is per-account, and this app never wants more than a
        // handful of requests in flight at once; the OkHttp default of 64 is
        // there for a server doing bulk fetches. Capping it keeps a burst of
        // poster/detail requests from opening 64 sockets against a host that
        // rate-limits after ~30 requests a minute anyway.
        clientBuilder.dispatcher(Dispatcher().apply { maxRequests = 16 })

        // Body-level logging used to be unconditional, which meant every release
        // build wrote the whole GraphQL request - including the
        // `Authorization: Bearer <anilist token>` header - into logcat, where
        // anyone with `adb logcat` on a connected device could read someone
        // else's AniList session. Debug only now, and redacted even there.
        if (BuildConfig.DEBUG) {
            clientBuilder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                    redactHeader("Authorization")
                }
            )
        }

        val okHttpClient = clientBuilder.build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(json))
            .build()

        return retrofit.create(AniListApiService::class.java) to okHttpClient
    }

    /**
     * The image loader every poster goes through.
     *
     * ## Why this is not the default
     *
     * Coil's default loader keeps a small memory cache and a modest disk cache.
     * This app's screen is essentially a wall of posters - a 476-entry account
     * scrolls past hundreds of 96dp covers - and the default gets them evicted
     * between scrolls, so scrolling back up re-downloads art the app had already
     * fetched. The sizes below are a fraction of the heap rather than a fixed
     * number, because the right number depends on the device.
     *
     * R8 does not strip art: `crossfade` is off deliberately because a fade on
     * every one of hundreds of posters costs frames on the scroll that is the
     * screen's main job.
     *
     * `bitmapConfig` is left at the platform default, which is a hardware bitmap
     * on API 26+. Hardware bitmaps are uploaded to the GPU once and drawn as a
     * texture, so scrolling a wall of covers does not re-upload each frame -
     * which is the single biggest GPU cost this screen can have.
     */
    fun createImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("anilist_cover_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(false)
            // No OkHttp response cache here on purpose. Coil's disk cache is
            // keyed on the image URL and already serves repeat loads without a
            // network call, and `respectCacheHeaders(false)` forces every miss
            // to revalidate anyway - so a second cache would add a layer that
            // can never be hit and duplicate 32MB of the covers on disk.
            .respectCacheHeaders(false)
            .components {
                // `ImageDecoderDecoder` is the platform decoder, and it is what
                // decodes *animated* WebP and APNG - not just GIF. AniList bios
                // are full of animated WebP, and without this those render as
                // their first frame with no indication anything is missing, which
                // reads as a broken image rather than a still one.
                //
                // This used to be `if (SDK_INT >= P) { ImageDecoderDecoder }
                // add(GifDecoder)`, because `ImageDecoder` arrived in API 28 and
                // `GifDecoder` was the only animated decoder below it. `minSdk`
                // is 31 now, so the guard could not be false on any device this
                // app installs on and the fallback below it was unreachable. The
                // guard and the fallback are deleted; see AGENTS.md §2, "raising
                // minSdk is a deletion".
                //
                // `coil-gif` is NOT a dependency that can be dropped with them.
                // Both decoders live in that one artifact - removing it makes
                // this line fail to resolve, which is exactly how CI answered a
                // change that tried. What is gone is the unreachable
                // `GifDecoder`: no call site references it any more, so R8 prunes
                // its classes out of the release, while the artifact stays for
                // the decoder that actually runs.
                add(ImageDecoderDecoder.Factory())
            }
            .build()

    private const val MEMORY_CACHE_PERCENT = 0.25
    private const val DISK_CACHE_BYTES = 128L * 1024 * 1024
}
