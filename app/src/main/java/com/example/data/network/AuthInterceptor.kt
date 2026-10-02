package com.example.data.network

import com.example.data.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the AniList bearer token.
 *
 * The token used to be read from DataStore inside `intercept`, blocking the
 * OkHttp thread on disk I/O for every single request - the app issues several
 * per screen. It is now served from an in-memory copy that the repository keeps
 * in step with sign-in and sign-out, so the blocking read happens at most once
 * per process and never returns a token that has been revoked by a logout.
 */
class AuthInterceptor(
    private val authRepository: AuthRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()

        val token = authRepository.cachedAccessToken()
            ?: runBlocking { authRepository.getAccessToken() }

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}