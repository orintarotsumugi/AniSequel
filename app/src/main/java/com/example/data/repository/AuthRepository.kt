package com.example.data.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val accessTokenFlow: Flow<String?>
    val clientIdFlow: Flow<String>

    /**
     * Last known token held in memory, or null if this process has not read it
     * yet. Lets the auth interceptor attach a token without blocking on disk.
     *
     * Implementations must keep this in step with sign-in and sign-out: after
     * [clearAccessToken] returns it must be null, or a revoked token keeps being
     * sent with every request.
     */
    fun cachedAccessToken(): String?

    suspend fun saveAccessToken(token: String)
    suspend fun saveClientId(clientId: String)
    suspend fun clearAccessToken()
    suspend fun getAccessToken(): String?
    suspend fun getClientId(): String
}
