package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object Unauthenticated : AuthUiState
    data class Authenticated(val token: String) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _clientId = MutableStateFlow(AuthRepositoryImpl.DEFAULT_CLIENT_ID)
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    init {
        checkCurrentAuth()
    }

    private fun checkCurrentAuth() {
        viewModelScope.launch {
            try {
                val currentClientId = authRepository.clientIdFlow.first()
                _clientId.value = currentClientId

                val token = authRepository.accessTokenFlow.first()
                if (!token.isNullOrBlank()) {
                    _uiState.value = AuthUiState.Authenticated(token)
                } else {
                    _uiState.value = AuthUiState.Unauthenticated
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Unauthenticated
            }
        }
    }

    fun handleAuthRedirect(uri: Uri?) {
        if (uri == null) return
        val fragment = uri.fragment ?: ""
        val urlString = uri.toString()

        val token = extractTokenFromUrl(fragment, urlString)
        if (!token.isNullOrBlank()) {
            saveToken(token)
        } else {
            _uiState.value = AuthUiState.Error("No access token found in redirect")
        }
    }

    private fun extractTokenFromUrl(fragment: String, fullUrl: String): String? {
        val target = if (fragment.isNotBlank()) fragment else fullUrl.substringAfter("#", "")
        if (target.isBlank()) return null

        val params = target.split("&")
        for (param in params) {
            val parts = param.split("=")
            if (parts.size >= 2 && parts[0] == "access_token") {
                return parts[1]
            }
        }
        return null
    }

    fun saveToken(token: String) {
        viewModelScope.launch {
            try {
                _uiState.value = AuthUiState.Loading
                authRepository.saveAccessToken(token)
                _uiState.value = AuthUiState.Authenticated(token)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Failed to save token")
            }
        }
    }

    fun updateClientId(newClientId: String) {
        viewModelScope.launch {
            try {
                authRepository.saveClientId(newClientId)
                _clientId.value = newClientId
            } catch (e: Exception) {
                // handle error if needed
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authRepository.clearAccessToken()
                _uiState.value = AuthUiState.Unauthenticated
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Unauthenticated
            }
        }
    }

    fun getAuthorizationUrl(): String {
        val currentClientId = _clientId.value.ifBlank { AuthRepositoryImpl.DEFAULT_CLIENT_ID }
        return "https://anilist.co/api/v2/oauth/authorize?client_id=$currentClientId&response_type=token"
    }
}
