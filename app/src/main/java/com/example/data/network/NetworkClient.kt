package com.example.data.network

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.BuildConfig
import com.example.data.repository.AuthRepository
import com.squareup.moshi.Moshi
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    private const val BASE_URL = "https://graphql.anilist.co/"

    fun createApiService(authRepository: AuthRepository): AniListApiService {
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(authRepository))
            // Before AuthInterceptor so a retried request re-reads the token.
            .addInterceptor(RateLimitInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Keep-alive for longer than OkHttp's 5s default. The dashboard
            // fires several requests in quick succession, and a connection torn
            // down between two of them means a fresh TLS handshake to AniList
            // each time - the single largest avoidable cost in a burst.
            .connectionPool(ConnectionPool(5, 60, TimeUnit.SECONDS))
            .retryOnConnectionFailure(true)

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

        // No KotlinJsonAdapterFactory: every model in data/model is annotated
        // @JsonClass(generateAdapter = true), so Moshi resolves a generated
        // adapter for each one. The reflective factory was dead weight, and it
        // drags kotlin-reflect into every build that enables R8.
        val moshi = Moshi.Builder().build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(AniListApiService::class.java)
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
            .respectCacheHeaders(false)
            .build()

    private const val MEMORY_CACHE_PERCENT = 0.25
    private const val DISK_CACHE_BYTES = 128L * 1024 * 1024
}