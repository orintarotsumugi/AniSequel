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
    /**
     * The stored session has not been read from disk yet, so nothing is known.
     *
     * Distinct from [Loading] on purpose. [Loading] means "we know who you are
     * and are doing something to them" - it is what the sign-in card shows a
     * spinner for - and it happens *after* the app already knows what to draw.
     * [Restoring] means the opposite: the answer is not known yet, and anything
     * that branches on it would be guessing.
     *
     * It used to be [Loading] that filled this role, and `AppNavigation` chose
     * its start destination with `is Authenticated ? DASHBOARD : LOGIN` - so
     * "not known yet" fell into the LOGIN branch. The result was a login screen
     * on every cold start, and - because the OAuth round trip sends the app to
     * the browser long enough for the process to be killed - a login screen
     * every time the user came back *from* signing in, which read as the app
     * forgetting they had asked to sign in.
     */
    data object Restoring : AuthUiState

    /** We know who the user is, and are currently acting on their behalf. */
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

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Restoring)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _clientId = MutableStateFlow(AuthRepositoryImpl.DEFAULT_CLIENT_ID)
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    /**
     * Set once a real OAuth callback has been processed.
     *
     * Guards the one write in this class that must not be clobbered by the
     * stored-token read racing it - see [checkCurrentAuth].
     */
    @Volatile
    private var hasHandledRedirect = false

    init {
        checkCurrentAuth()
    }

    private fun checkCurrentAuth() {
        viewModelScope.launch {
            try {
                val currentClientId = authRepository.clientIdFlow.first()
                _clientId.value = currentClientId

                val token = authRepository.accessTokenFlow.first()

                // A redirect can land while this is still reading from disk -
                // MainActivity hands the deep link to the ViewModel in onCreate,
                // and the stored-token read is on another dispatcher. When the
                // two finished in the wrong order this coroutine wrote
                // Unauthenticated *after* the redirect had already written
                // Authenticated, so a successful sign-in silently dropped back
                // to the login screen. The redirect wins if it arrived first.
                if (hasHandledRedirect) return@launch

                if (!token.isNullOrBlank()) {
                    _uiState.value = AuthUiState.Authenticated(token)
                } else {
                    _uiState.value = AuthUiState.Unauthenticated
                }
            } catch (e: Exception) {
                if (!hasHandledRedirect) {
                    _uiState.value = AuthUiState.Unauthenticated
                }
            }
        }
    }

    fun handleAuthRedirect(uri: Uri?) {
        when (val result = AniListOAuth.parseRedirect(uri)) {
            is RedirectResult.Success -> {
                hasHandledRedirect = true
                saveToken(result.accessToken)
            }

            is RedirectResult.Error -> {
                hasHandledRedirect = true
                _uiState.value = AuthUiState.Error(
                    // AniList sends `error=access_denied` when the user backs out
                    // of the consent screen. That is not a failure worth
                    // alarming them about, so it gets a plain sentence.
                    if (result.code.equals("access_denied", ignoreCase = true)) {
                        "AniList access was cancelled."
                    } else {
                        result.description?.takeIf { it.isNotBlank() }
                            ?: "AniList returned an error during sign-in (${result.code})."
                    }
                )
            }

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
                // The token stayed on disk precisely because the check never
                // finished. A session that was never verified by AniList must
                // not be resurrected as Authenticated on the next cold start,
                // which would produce a dashboard that only says "Session
                // expired" and never recover on its own.
                runCatching { authRepository.clearAccessToken() }
                _uiState.value = AuthUiState.Error(e.message ?: "Failed to save token")
            }
        }
    }

    /**
     * Returns to [AuthUiState.Unauthenticated] from an [AuthUiState.Error].
     *
     * `Error` used to be terminal, and the sign-in card disabled its "Connect
     * AniList Account" button whenever the state was an `Error`. The two
     * together meant the most common way of *reaching* that state - backing out
     * of AniList's consent screen - left the user with no way to try again:
     * the only button that can restart the flow was the one that had just been
     * switched off, and the process had to be killed to get it back. The same
     * held after pasting a token AniList refused, which is the exact moment
     * somebody most wants to try again with a better copy.
     *
     * Deliberately only ever moves *away* from `Error`. A stored token read in
     * progress is not something to clear, and [Authenticated] is the state the
     * user actually wants to keep.
     */
    fun dismissAuthError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Unauthenticated
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
                aniListRepository?.clearDetailCache()
                _uiState.value = AuthUiState.Unauthenticated
            } catch (e: Exception) {
                // Clearing the on-disk session did not succeed, so the session
                // is still on disk. Claiming "signed out" anyway would leave
                // the next cold start resurrecting a session this user asked
                // for to be gone; say that it failed instead.
                _uiState.value = AuthUiState.Error(e.message ?: "Couldn't sign out. Try again.")
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
