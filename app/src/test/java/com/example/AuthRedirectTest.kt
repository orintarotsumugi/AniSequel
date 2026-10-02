package com.example

import android.net.Uri
import com.example.data.repository.AuthRepository
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthRedirectTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthRepository : AuthRepository {
        private val token = MutableStateFlow<String?>(null)
        private val clientId = MutableStateFlow("23668")

        override val accessTokenFlow: Flow<String?> = token
        override val clientIdFlow: Flow<String> = clientId

        override fun cachedAccessToken(): String? = token.value

        override suspend fun saveAccessToken(token: String) {
            this.token.value = token
        }

        override suspend fun saveClientId(clientId: String) {
            this.clientId.value = clientId
        }

        override suspend fun clearAccessToken() {
            token.value = null
        }

        override suspend fun getAccessToken(): String? = token.value

        override suspend fun getClientId(): String = clientId.value
    }

    @Test
    fun `reads a token from the oauth redirect fragment`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)
        advanceUntilIdle()

        viewModel.handleAuthRedirect(Uri.parse("anisequel://oauth#access_token=abc123&token_type=Bearer"))
        advanceUntilIdle()

        assertEquals("abc123", repository.getAccessToken())
        assertTrue(viewModel.uiState.value is AuthUiState.Authenticated)
    }

    @Test
    fun `keeps the padding characters that split used to chop off`() = runTest(dispatcher) {
        // The redirect came back as `access_token=eyJhbGci.payload=`, and
        // `split("=")` returned only `eyJhbGci.payload`, a token that then failed
        // every single request while looking correctly pasted.
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)
        advanceUntilIdle()

        viewModel.handleAuthRedirect(Uri.parse("anisequel://oauth#access_token=eyJhbGci.payload=x"))
        advanceUntilIdle()

        assertEquals("eyJhbGci.payload=x", repository.getAccessToken())
    }

    @Test
    fun `a declined consent screen is reported as a cancellation`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)
        advanceUntilIdle()

        viewModel.handleAuthRedirect(Uri.parse("anisequel://oauth#error=access_denied"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.Error)
        assertEquals(
            "AniList access was cancelled.",
            (state as AuthUiState.Error).message
        )
    }
}