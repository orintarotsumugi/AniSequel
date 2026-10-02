package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.network.AniListErrorKind
import com.example.data.network.AniListException
import com.example.data.network.AniListOAuth
import com.example.data.network.RedirectResult
import com.example.data.repository.AniListRepository
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
    private val authRepository: AuthRepository,
    /**
     * Used to check a pasted token against AniList before it is believed.
     *
     * Optional so tests and any caller that only has the token store can still
     * construct this; when it is absent the token is stored unchecked, exactly
     * as it used to be.
     */
    private val aniListRepository: AniListRepository? = null
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
        when (val result = AniListOAuth.parseRedirect(uri)) {
            is RedirectResult.Success -> saveToken(result.accessToken)

            is RedirectResult.Error -> _uiState.value = AuthUiState.Error(
                // AniList sends `error=access_denied` when the user backs out of
                // the consent screen. That is not a failure worth alarming them
                // about, so it gets a plain sentence.
                if (result.code.equals("access_denied", ignoreCase = true)) {
                    "AniList access was cancelled."
                } else {
                    result.description?.takeIf { it.isNotBlank() }
                        ?: "AniList returned an error during sign-in (${result.code})."
                }
            )

            // A launch from the launcher, not a callback. Treating this as an
            // error is what produced "No access token found in redirect" for a
            // perfectly normal cold start.
            RedirectResult.NoCallback -> Unit
        }
    }

    /**
     * Stores a token, however it arrived.
     *
     * The value is run through [AniListOAuth.extractToken] first: the paste
     * dialog is a documented path, and people paste `access_token=...`, the
     * whole redirect URL, or a quoted value. Storing any of those verbatim
     * produces a session that fails every request with "Session expired".
     */
    fun saveToken(rawToken: String) {
        val token = AniListOAuth.extractToken(rawToken)
        if (token == null) {
            _uiState.value = AuthUiState.Error(
                "That doesn't look like an AniList token. Copy everything after " +
                    "access_token= in the sign-in URL."
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = AuthUiState.Loading

                // Store first, because the check itself has to authenticate.
                // Anything that does not already hold the new token - the auth
                // interceptor's in-memory copy - would call AniList without it
                // and "reject" a perfectly good token.
                authRepository.saveAccessToken(token)

                val rejection = aniListRepository?.getViewer()?.exceptionOrNull()
                    ?.takeIf { it is AniListException && it.kind == AniListErrorKind.INVALID_SESSION }

                if (rejection != null) {
                    // Undo the save: leaving a token AniList has just refused in
                    // place is what produced a dashboard that says "Session
                    // expired" for a session that never worked.
                    authRepository.clearAccessToken()
                    _uiState.value = AuthUiState.Error(
                        "AniList rejected that token. Copy the part after " +
                            "access_token= in the sign-in URL - the whole URL, " +
                            "the token_type, or the client Secret will not work."
                    )
                    return@launch
                }

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

    /**
     * The URL opened in the browser.
     *
     * Carries only `client_id` and `response_type`: AniList rejects the request
     * with `unsupported_grant_type` when a `redirect_uri` or `state` is also
     * sent, and it sends the token to whatever Redirect URL is registered for
     * the client. See [AniListOAuth] before changing this.
     */
    fun getAuthorizationUrl(): String =
        AniListOAuth.authorizationUrl(
            _clientId.value.ifBlank { AuthRepositoryImpl.DEFAULT_CLIENT_ID }
        )
}
