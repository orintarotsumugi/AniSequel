package com.example.data.network

import com.example.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

/**
 * Attaches the AniList bearer token to every request.
 *
 * The previous version called `runBlocking { repository.getAccessToken() }` on
 * the OkHttp dispatcher thread for *every* request. That is a disk read (the
 * token lives in DataStore) performed while holding a worker thread, and after
 * signing out it could still serve a stale token to a request that was already
 * in flight.
 *
 * Now the token is collected once into an in-memory reference and refreshed
 * whenever DataStore emits. The very first request waits for that first emission
 * (DataStore reads are a few milliseconds once warm) - without it, the request
 * that follows a cold start could race ahead of the collector and go out with no
 * Authorization header at all, which AniList answers with "Viewer not found"
 * instead of the list the user is waiting for.
 */
class AuthInterceptor(
    private val authRepository: AuthRepository
) : Interceptor {

    private val cachedToken = AtomicReference<String?>(null)
    private var isCollecting = false

    override fun intercept(chain: Interceptor.Chain): Response {
        startCollectingIfNeeded()
        val token = awaitFirstToken()

        val requestBuilder = chain.request().newBuilder()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }

    @Synchronized
    private fun startCollectingIfNeeded() {
        if (isCollecting) return
        isCollecting = true

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            authRepository.accessTokenFlow.collect { token ->
                cachedToken.set(token)
            }
        }
    }

    private fun awaitFirstToken(): String? {
        // Already warm - no blocking, no extra read.
        cachedToken.get()?.let { return it }

        return runBlocking {
            // A first() on the same DataStore flow is the authoritative read, and
            // this only ever runs before the collector's first emission.
            val fresh = authRepository.accessTokenFlow.first()
            cachedToken.set(fresh)
            fresh
        }
    }
}