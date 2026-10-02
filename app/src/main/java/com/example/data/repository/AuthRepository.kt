package com.example.data.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val accessTokenFlow: Flow<String?>
    val clientIdFlow: Flow<String>
    
    suspend fun saveAccessToken(token: String)
    suspend fun saveClientId(clientId: String)
    suspend fun clearAccessToken()
    suspend fun getAccessToken(): String?
    suspend fun getClientId(): String
}
