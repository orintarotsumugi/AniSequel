package com.example.data.network

import com.example.BuildConfig
import com.example.data.repository.AuthRepository
import com.squareup.moshi.Moshi
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
}
