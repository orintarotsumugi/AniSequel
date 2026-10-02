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
            // AniList sends `error=access_denied` when the user backs out of the
            // consent screen. That is not a failure worth alarming them about.
            val declined = fragment.contains("error=", ignoreCase = true) ||
                    urlString.contains("error=", ignoreCase = true)
            _uiState.value = AuthUiState.Error(
                if (declined) "AniList access was cancelled." else "No access token found in redirect"
            )
        }
    }

    private fun extractTokenFromUrl(fragment: String, fullUrl: String): String? {
        val target = if (fragment.isNotBlank()) fragment else fullUrl.substringAfter("#", "")
        if (target.isBlank()) return null

        // `split("=")` truncated any token containing base64 padding at the first
        // '=', producing a token that looked pasted but silently failed every
        // request afterwards. substringAfter keeps the value whole.
        return target.split("&")
            .firstOrNull { it.substringBefore("=") == "access_token" }
            ?.substringAfter("=", "")
            ?.takeIf { it.isNotBlank() }
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
            val trimmed = newClientId.trim()
            if (trimmed.isBlank()) return@launch
            try {
                authRepository.saveClientId(trimmed)
                _clientId.value = trimmed
            } catch (e: Exception) {
                _clientId.value = authRepository.clientIdFlow.first()
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
