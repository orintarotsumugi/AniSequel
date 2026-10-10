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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.network.NetworkClient
import com.example.data.repository.AniListRepositoryImpl
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.repository.ThemeMode
import com.example.data.repository.ThemePreferences
import com.example.ui.components.update.UpdateController
import com.example.ui.components.update.UpdatePromptHost
import com.example.ui.components.update.rememberUpdateController
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.AniSequelTheme
import com.example.ui.theme.ThemePalette
import com.example.ui.viewmodel.AuthViewModel
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {

    companion object {
        /** Extra used by the profile-activity widget to request opening the activity screen. */
        const val EXTRA_WIDGET_OPEN_ACTIVITY = "anisequel.widget.open_activity"
        /** Extra used by the missed-sequels widget to request opening the main dashboard. */
        const val EXTRA_WIDGET_OPEN_DASHBOARD = "anisequel.widget.open_dashboard"
        /** Extra used by the arriving-sequels widget to request opening the arriving feed. */
        const val EXTRA_WIDGET_OPEN_ARRIVING = "anisequel.widget.open_arriving"
    }

    private lateinit var authRepository: AuthRepository
    private lateinit var themePreferences: ThemePreferences

    /**
     * Set when the home-screen widget asked to open the profile's Activity tab.
     *
     * Compose state rather than a plain `Boolean` because the widget's tap can
     * arrive at three different moments - a cold launch, a warm launch through
     * `onNewIntent`, or not at all - and only the first two of those happen during
     * a composition. A field that the composition does not observe would be read
     * once, on whichever frame happened to look at it.
     *
     * Cleared by [AppNavigation] once it has navigated, so the request is not
     * replayed on the next recomposition.
     */
    private var openProfileActivityRequest by mutableStateOf(false)

    /**
     * Held so [onResume] can reach the updater.
     *
     * The controller is process-wide and created inside the composition, so it
     * does not exist at the time `onCreate` starts. Assigning it during
     * composition and reading it here is what lets returning from the
     * install-permission Settings screen continue straight into the installer.
     *
     * Weakly held on purpose: this field is only ever a way to reach the shared
     * singleton, never its owner. A strong reference from the Activity to a
     * process-scoped object that outlives it would keep a rotated-away Activity
     * alive, which is the leak this whole controller design exists to avoid.
     */
    private var updateControllerRef: WeakReference<UpdateController>? = null

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

        // The widget's tap, on a fresh launch only.
        //
        // Guarded on `savedInstanceState`, because the extras live on the intent
        // for as long as the Activity does: without the guard, a rotation would
        // re-read the extra and jump back to the profile from wherever the user
        // had navigated to since. `onNewIntent` is the other half of this - see
        // below.
        if (savedInstanceState == null &&
            intent?.getBooleanExtra(EXTRA_WIDGET_OPEN_ACTIVITY, false) == true
        ) {
            openProfileActivityRequest = true
        }

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
                dynamicColor = themeSettings.useDynamicColor,
                palette = ThemePalette.fromStorage(themeSettings.paletteId),
                motionStyle = themeSettings.motionStyle,
                trueBlack = themeSettings.trueBlack,
                customColorHex = themeSettings.customColorHex,
                animeThemeActive = themeSettings.animeThemeActive,
                animeThemeColorHex = themeSettings.animeThemeColorHex,
                animeThemeRetainColor = themeSettings.animeThemeRetainColor,
                appBackgroundColor = themeSettings.appBackgroundColor
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
                    updateControllerRef = WeakReference(updateController)

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
                        themePreferences = themePreferences,
                        openProfileActivityOnLaunch = openProfileActivityRequest,
                        onProfileActivityOpened = { openProfileActivityRequest = false }
                    )

                    UpdatePromptHost(controller = updateController)
                }
            }
        }
    }

    /**
     * The updater's chance to notice that the install permission arrived.
     *
     * From Android 8 an app cannot open the system installer until the user has
     * granted it `REQUEST_INSTALL_PACKAGES` in Settings, which means the first
     * update has to leave the app and come back. That return is an `onResume`,
     * and it is the only place the grant can be observed: nothing in this
     * process is told when the user flips a switch in another app's settings
     * screen. So the updater is handed the resume and re-checks, and if the
     * permission has arrived it opens the installer on the APK it already has
     * rather than making the user press a third button to get what they asked
     * for two presses ago.
     *
     * A no-op unless an update is actually waiting on the permission.
     */
    override fun onResume() {
        super.onResume()
        updateControllerRef?.get()?.onAppResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)

        // A second tap on the widget while the app is already open. MainActivity
        // is `singleTask`, so this is the only path that tap can take - it never
        // reaches `onCreate`.
        if (intent.getBooleanExtra(EXTRA_WIDGET_OPEN_ACTIVITY, false)) {
            openProfileActivityRequest = true
        }
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "anisequel" && uri.host == "oauth") {
            authViewModel.handleAuthRedirect(uri)
        }
    }
}
