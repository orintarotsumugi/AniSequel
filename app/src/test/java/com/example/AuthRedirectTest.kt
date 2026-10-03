package com.example

import android.net.Uri
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaNode
import com.example.data.model.SimpleMediaListEntry
import com.example.data.model.ViewerProfile
import com.example.data.network.AniListErrorKind
import com.example.data.network.AniListException
import com.example.data.repository.AniListRepository
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
import org.junit.Assert.assertNull
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

    /**
     * The failure this closes: a token that AniList refuses used to be stored
     * silently, the app navigated to the dashboard, and the dashboard then said
     * "Session expired" - pointing at the session rather than at the paste.
     */
    @Test
    fun `a token AniList rejects is not stored and says why`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository, RejectingRepository())
        advanceUntilIdle()

        viewModel.saveToken("not-a-real-token")
        advanceUntilIdle()

        assertNull("a rejected token must not be left behind", repository.getAccessToken())

        val state = viewModel.uiState.value
        assertTrue("expected an error, got $state", state is AuthUiState.Error)
        assertTrue(
            "the message has to name the paste, not the session: ${(state as AuthUiState.Error).message}",
            state.message.contains("access_token=")
        )
    }

    @Test
    fun `a token AniList accepts is stored`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository, AcceptingRepository())
        advanceUntilIdle()

        viewModel.saveToken("access_token=eyJhbGciOi.payload&token_type=Bearer")
        advanceUntilIdle()

        assertEquals("eyJhbGciOi.payload", repository.getAccessToken())
        assertTrue(viewModel.uiState.value is AuthUiState.Authenticated)
    }

    /**
     * Only a refused token is a problem. Being offline or rate limited says
     * nothing about the paste, and treating those as a bad token would lock a
     * valid token out of the app.
     */
    @Test
    fun `a network failure during the check does not discard the token`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(
            repository,
            FailingRepository(
                AniListException(AniListErrorKind.OFFLINE, "Can't reach AniList.")
            )
        )
        advanceUntilIdle()

        viewModel.saveToken("eyJhbGciOi.payload")
        advanceUntilIdle()

        assertEquals("eyJhbGciOi.payload", repository.getAccessToken())
        assertTrue(viewModel.uiState.value is AuthUiState.Authenticated)
    }

    private abstract class StubAniListRepository : AniListRepository {
        override suspend fun getUserByName(userName: String): Result<ViewerProfile> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getUserAnimeList(
            userId: Int,
            forceRefresh: Boolean
        ): Result<MediaListCollection> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getUserAnimeListByUsername(
            userName: String,
            forceRefresh: Boolean
        ): Result<MediaListCollection> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getMediaDetail(mediaId: Int): Result<MediaNode> =
            Result.failure(UnsupportedOperationException())

        override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> =
            Result.failure(UnsupportedOperationException())

        override fun getDemoProfile(): ViewerProfile = throw UnsupportedOperationException()
        override fun getDemoAnimeList(): MediaListCollection = throw UnsupportedOperationException()
    }

    private class RejectingRepository : StubAniListRepository() {
        override suspend fun getViewer(): Result<ViewerProfile> =
            Result.failure(AniListException(AniListErrorKind.INVALID_SESSION, "Invalid token"))
    }

    private class FailingRepository(private val error: AniListException) : StubAniListRepository() {
        override suspend fun getViewer(): Result<ViewerProfile> = Result.failure(error)
    }

    private class AcceptingRepository : StubAniListRepository() {
        override suspend fun getViewer(): Result<ViewerProfile> = Result.success(
            ViewerProfile(id = 1, name = "tester")
        )
    }
}