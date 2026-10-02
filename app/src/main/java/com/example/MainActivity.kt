package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.network.NetworkClient
import com.example.data.repository.AniListRepositoryImpl
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthRepositoryImpl
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.AniSequelTheme
import com.example.ui.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {

    private lateinit var authRepository: AuthRepository

    private val authViewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(
                    authRepository = authRepository,
                    // So a pasted token is checked against AniList before the app
                    // navigates to a dashboard that will only report "Session
                    // expired" if it is wrong.
                    aniListRepository = AniListRepositoryImpl(
                        NetworkClient.createApiService(authRepository)
                    )
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authRepository = AuthRepositoryImpl(applicationContext)

        enableEdgeToEdge()

        // Handle OAuth deep link if launched via custom scheme
        handleDeepLinkIntent(intent)

        setContent {
            AniSequelTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        authRepository = authRepository,
                        authViewModel = authViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "anisequel" && uri.host == "oauth") {
            authViewModel.handleAuthRedirect(uri)
        }
    }
}
