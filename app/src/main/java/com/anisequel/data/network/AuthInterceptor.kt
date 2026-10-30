package com.anisequel.data.network

import com.anisequel.data.repository.AuthRepository
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

    /**
     * Whether [authRepository]'s memory cache has been observed carrying "no
     * token" rather than "not read yet".
     *
     * `cachedAccessToken()` is null for both, so without this the signed-out
     * case re-entered the blocking disk read on *every* request - the exact
     * cost the in-memory cache was added to remove. Once a read has positively
     * answered null, only a later sign-in can change the answer, and that writes
     * the cache directly ([AuthRepository.saveAccessToken]).
     */
    @Volatile
    private var resolvedWithoutToken = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()

        val token = authRepository.cachedAccessToken()
            ?: if (resolvedWithoutToken) {
                null
            } else {
                runBlocking { authRepository.getAccessToken() }
                    .also { resolvedWithoutToken = it.isNullOrBlank() }
            }

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}