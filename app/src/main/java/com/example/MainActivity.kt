package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.network.NetworkClient
import com.example.data.repository.AniListRepositoryImpl
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.repository.ThemeMode
import com.example.data.repository.ThemePreferences
import com.example.ui.components.UpdatePromptHost
import com.example.ui.components.rememberUpdateController
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.AniSequelTheme
import com.example.ui.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {

    private lateinit var authRepository: AuthRepository
    private lateinit var themePreferences: ThemePreferences

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
        themePreferences = ThemePreferences(applicationContext)

        enableEdgeToEdge()

        // Handle OAuth deep link if launched via custom scheme
        handleDeepLinkIntent(intent)

        setContent {
            // Collected here rather than inside the theme so the whole tree
            // recomposes when the preference changes. Reading it inside
            // AniSequelTheme would restart only the theme's own subtree, and the
            // settings screen's switch would sit next to a preview that had
            // already repainted.
            //
            // Seeded from the defaults so the first frame has a real theme
            // instead of flashing the light one before the read completes.
            val themeSettings by themePreferences.settings.collectAsState(
                initial = ThemePreferences.ThemeSettings(
                    themeMode = ThemeMode.DEFAULT,
                    useDynamicColor = false
                )
            )

            AniSequelTheme(
                themeMode = themeSettings.themeMode,
                dynamicColor = themeSettings.useDynamicColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Held on the themed background until the stored theme is in.
                    // The Surface already paints colorScheme.background, so this
                    // shows the right colour from the first frame rather than a
                    // default-themed app that repaints a moment later.
                    if (!themeSettings.isLoaded) {
                        return@Surface
                    }

                    val updateController = rememberUpdateController()

                    // One check per launch, after the first frame.
                    //
                    // Keyed on a constant so it runs exactly once rather than on
                    // every recomposition, and deliberately *after* the app is
                    // usable: a dialog that blocks the first screen while
                    // GitHub is slow is a worse experience than one that
                    // arrives a moment later.
                    LaunchedEffect(Unit) {
                        updateController.check()
                    }

                    AppNavigation(
                        authRepository = authRepository,
                        authViewModel = authViewModel,
                        themePreferences = themePreferences
                    )

                    UpdatePromptHost(controller = updateController)
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
