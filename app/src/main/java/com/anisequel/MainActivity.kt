package com.anisequel

import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.anisequel.data.network.NetworkClient
import com.anisequel.data.network.RateLimitNotice
import com.anisequel.data.network.SystemNotices
import com.anisequel.data.repository.AniListRepositoryImpl
import com.anisequel.data.repository.AniListSnapshots
import com.anisequel.data.repository.AppLockPreferences
import com.anisequel.data.repository.AuthRepository
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.update.UpdateController
import com.anisequel.ui.components.update.UpdatePromptHost
import com.anisequel.ui.components.update.rememberUpdateController
import com.anisequel.ui.navigation.AppNavigation
import com.anisequel.ui.navigation.RestoringPlaceholder
import com.anisequel.ui.security.AppLockGate
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.theme.applyImmersiveMode
import com.anisequel.ui.theme.ThemePalette
import com.anisequel.ui.viewmodel.AuthViewModel
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {

    companion object {
        /** Extra used by the profile-activity widget to request opening the activity screen. */
        const val EXTRA_WIDGET_OPEN_ACTIVITY = "anisequel.widget.open_activity"
    }

    /**
     * Survives a configuration change but not process death.
     *
     * The lock is a field on the Activity, so a rotation built a fresh Activity
     * whose first composition armed the gate again - the reader unlocked,
     * rotated the phone, and was asked to unlock it again. This holds the
     * unlocked state across that recreation. A process death while in the
     * background creates a new ViewModel, so the gate is still armed when the
     * app comes back from the launcher.
     */
    private val appLockState: AppLockStateViewModel by viewModels()

    private lateinit var authRepository: AuthRepository
    private lateinit var themePreferences: ThemePreferences
    private lateinit var appLockPreferences: AppLockPreferences

    /**
     * The stored app-lock choice, mirrored out of the flow so `onStop` can read
     * it. The flow is collected in the composition, which is not alive when the
     * Activity is stopped.
     */
    private var appLockEnabled = false

    /**
     * The full-screen choice, mirrored out of the flow so `onWindowFocusChanged`
     * can re-apply it without composition - the focus callback can arrive before
     * the composition has a settings value, and re-applying a stale one is how the
     * bars end up back on a screen that asked for them hidden.
     */
    private var immersiveEnabled: Boolean? = null

    /**
     * False until the lock preference has actually been read.
     *
     * Without this the first frame would compose the unlocked navigation tree
     * and only then learn it should have been covered - a one-frame view of the
     * reader's list is still a view of it, and on a cold start it can be the
     * frame the system snapshots for the recents switcher.
     */
    private var appLockReady by mutableStateOf(false)

    /** True while the lock gate is drawn over the app. */
    private var _isLocked by mutableStateOf(false)
    val isLocked: Boolean get() = _isLocked

    /**
     * True while the system credential prompt is up.
     *
     * The prompt can stop the Activity on some OEMs. Re-arming the lock from
     * that `onStop` would mark the app locked while the user is in the middle of
     * authenticating, so the prompt's own lifecycle is excluded.
     */
    private var biometricPromptVisible = false

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
                        apiService = NetworkClient.createApiService(authRepository),
                        // The same store the dashboards' repository writes. What
                        // this instance does on sign-in and sign-out is *clear*
                        // it, and clearing a different instance would leave the
                        // signed-out person's list and bio on disk for the next
                        // account. See [AniListSnapshots].
                        snapshots = AniListSnapshots.of(applicationContext)
                    )
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authRepository = (application as AniSequelApplication).authRepository
        themePreferences = ThemePreferences(applicationContext)
        appLockPreferences = AppLockPreferences(applicationContext)

        enableEdgeToEdge()

        // Handle OAuth deep link if launched via custom scheme.
        //
        // Deliberately *not* guarded on `savedInstanceState == null`, which is what
        // it used to be: that guard was written to stop a rotation replaying the
        // callback, and it also dropped the callback that matters most. The sign-in
        // round trip leaves the process in the browser long enough for Android to
        // kill it, and the redirect then arrives as a restored launch with a
        // non-null bundle - so the code the reader just earned was ignored and the
        // app went back to the login screen they had already passed.
        //
        // The replay the guard was protecting against is now impossible by
        // construction: [AuthViewModel.handleAuthRedirect] recognises an identical
        // callback URI and ignores it, which also covers `singleTask` re-delivery
        // from Recents. Idempotency belongs with the thing that is idempotent, not
        // with the lifecycle callback that happened to be running.
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
            // The app-wide rate-limit notice is collected at the composition
            // root, not on a screen. The failure can come from anywhere:
            // AniList is a shared bucket, so a request the dashboard started
            // can be refused while a media sheet is on screen - a screen-owned
            // error surface only covers its own requests, which is how the
            // reader ends up looking at a dead spinner with no explanation.
            //
            // It is a Toast rather than a Scaffold snackbar on purpose. The
            // places a 429 is most likely to land - the comment thread, a
            // title page - are ModalBottomSheets, which are drawn over the
            // host screen: a snackbar would be posted behind the sheet, where
            // the reader is not looking, while a Toast draws over everything.
            //
            // Collecting in the composition is also the visibility guard: no
            // composition means no collector, so a rate limit hit with no
            // context to show a notice in stays silent.
            LaunchedEffect(Unit) {
                RateLimitNotice.events.collect { retryAfterSeconds ->
                    val wait = retryAfterSeconds?.let { " (about ${it}s)" }.orEmpty()
                    Toast.makeText(
                        this@MainActivity,
                        "AniList is busy — you're rate limited. Give it a minute and try again.$wait",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // The other failures and the one write confirmation that have no
            // surface of their own where they happen. Same composition-root
            // reasoning as the rate-limit notice above: a background refresh that
            // comes back on a dead token has no sheet to put "sign in again" on,
            // and a comment that lands after its sheet closed has no inline
            // confirmation left to scroll into — a Toast is the only thing that
            // reaches either. Deduplicated per kind in [SystemNotices], so one
            // outage is one notice rather than one per in-flight request.
            LaunchedEffect(Unit) {
                SystemNotices.events.collect { notice ->
                    val message = when (notice) {
                        SystemNotices.Notice.SessionExpired ->
                            "Your AniList session expired. Sign in again to sync your list."
                        SystemNotices.Notice.ServerDown ->
                            "AniList's servers are down right now. Nothing on this device is broken."
                        SystemNotices.Notice.Offline ->
                            "No connection. Reconnect and it will load."
                        SystemNotices.Notice.CommentPosted ->
                            "Comment posted."
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }

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

            // Full screen is the app's default presentation - applied by its own
            // helper so this method's branching stays about the app's screens.
            ImmersiveModeEffect(
                window = window,
                isLoaded = themeSettings.isLoaded,
                enabled = themeSettings.immersive,
                onChoice = { immersiveEnabled = it }
            )

            // `null` until the stored choice has been read, which is why the
            // initial value is not `false`: the two are different answers, and
            // treating "not read yet" as "off" is the flash this guards.
            val storedAppLock: Boolean? by appLockPreferences.enabled.collectAsState(initial = null)
            LaunchedEffect(storedAppLock) {
                val enabled = storedAppLock ?: return@LaunchedEffect
                appLockEnabled = enabled
                // The recents thumbnail is captured as the task leaves the
                // foreground, which can be before `onStop` runs. The flag has to
                // already be set while the lock is enabled, not added at the
                // moment the gate appears.
                applySecureFlag()

                if (!appLockReady) {
                    appLockReady = true
                    if (enabled && isDeviceSecurelyLocked() && !appLockState.unlockedSinceLaunch) {
                        setLocked(true)
                    }
                } else if (!enabled) {
                    // Turning the lock off in Settings must release a gate that
                    // is already up, not leave it waiting for the next resume.
                    setLocked(false)
                }
            }

            AniSequelTheme(
                themeMode = themeSettings.themeMode,
                dynamicColor = themeSettings.useDynamicColor,
                palette = ThemePalette.fromStorage(themeSettings.paletteId),
                motionStyle = themeSettings.motionStyle,
                trueBlack = themeSettings.trueBlack,
                customColorHex = themeSettings.customColorHex,
                animeThemeActive = themeSettings.animeThemeActive,
                animeThemeColorHex = themeSettings.animeThemeColorHex,
                // Retention's durability, resolved by the preferences because the
                // theme is given a colour rather than the flags: the marker only
                // tints while the feature and the retain switch are both on, so a
                // marker left by a downgrade cannot outrank the reader's palette.
                animeThemeRetainedColorHex = themeSettings.retainedAnimeColorHex,
                animeTintStrength = themeSettings.retainedTint.strength,
                appBackgroundColor = themeSettings.appBackgroundColor,
                fontScale = themeSettings.fontScale,
                customFontPath = themeSettings.customFontPath,
                titleScrollDpPerSecond = themeSettings.titleScrollDpPerSecond
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(immersiveDisplayInsets(themeSettings.immersive))
                ) {
                    // Held on the themed background until the stored theme and
                    // lock state are in. This used to return an empty Surface,
                    // which was the right colour but a blank first frame - the
                    // window had nothing to show and the app looked stopped.
                    // The same branded placeholder the session read uses is
                    // drawn instead, so both pre-screen waits look deliberate.
                    if (!themeSettings.isLoaded || !appLockReady) {
                        RestoringPlaceholder(message = "Restoring your settings\u2026")
                        return@Surface
                    }

                    AppLockGate(
                        locked = isLocked,
                        onUnlock = { requestUnlock() }
                    ) {
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
                            appLockPreferences = appLockPreferences,
                            openProfileActivityOnLaunch = openProfileActivityRequest,
                            onProfileActivityOpened = { openProfileActivityRequest = false }
                        )

                        // A dialog is its own window, so it would draw *over*
                        // the lock overlay rather than under it. The check can
                        // still run while locked; the prompt waits until the
                        // reader has unlocked.
                        if (!isLocked) {
                            UpdatePromptHost(controller = updateController)
                        }
                    }
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
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // A transient edge swipe, a dialog dismissal or an OEM's own window pass can
        // re-show the system bars. Focus returning is the documented moment to put
        // the chosen state back, and doing it here rather than on a timer means the
        // bars never linger longer than the system itself showed them.
        if (hasFocus) immersiveEnabled?.let { window.applyImmersiveMode(it) }
    }

    override fun onResume() {        super.onResume()
        updateControllerRef?.get()?.onAppResumed()
    }

    /**
     * Re-arms the lock as the app leaves the foreground.
     *
     * `onStop` rather than `onPause`: a permission dialog or the system
     * credential prompt pauses the Activity without taking it off screen, and
     * locking there would put the gate up over a screen the user is still
     * looking at. `onStop` is the moment the app is no longer visible, which is
     * exactly when the recents snapshot is taken.
     */
    override fun onStop() {
        super.onStop()
        // A configuration change stops the Activity without the app actually
        // leaving the foreground. Re-arming there put the lock over a rotation
        // (and over the new Activity's restored screen) for no privacy gain.
        if (!isChangingConfigurations &&
            appLockEnabled &&
            !biometricPromptVisible &&
            isDeviceSecurelyLocked()
        ) {
            setLocked(true)
        }
    }

    private fun isDeviceSecurelyLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Shows or clears the lock gate. */
    private fun setLocked(locked: Boolean) {
        _isLocked = locked
        // `unlockedSinceLaunch` is what the recreation path reads. Clearing it
        // when locking also covers the case where the old Activity is stopped
        // for a real background trip and a new one is built before the process
        // dies.
        appLockState.unlockedSinceLaunch = !locked
        applySecureFlag()
    }

    /**
     * Keeps this window out of the recents thumbnail while the lock is on.
     *
     * `FLAG_SECURE` is tied to the *preference*, not to the gate being visible:
     * the system snapshots the task as it leaves the foreground, and a flag
     * added in `onStop` can arrive after that snapshot. With the lock enabled
     * the thumbnail is permanently blank, which also means the reader cannot
     * screenshot their own list - an exchange a privacy lock is expected to
     * make, and the flag is removed the moment the lock is turned off.
     */
    private fun applySecureFlag() {
        if (isLocked || appLockEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Asks the platform for a biometric or device-credential confirmation.
     *
     * The platform prompt is used rather than `androidx.biometric`: the app's
     * floor is API 31, where `setAllowedAuthenticators` and
     * `DEVICE_CREDENTIAL` are both available, so the support library would add
     * a dependency to reproduce an API that is already present.
     *
     * `BIOMETRIC_WEAK` rather than `BIOMETRIC_STRONG`: a face unlock that the
     * platform rates weak is still the lock the owner chose, and refusing it
     * would make the feature unavailable on devices that only have one. A PIN or
     * pattern is accepted too, through `DEVICE_CREDENTIAL`.
     *
     * No secure lock means no gate: [onStop] never locks in that case, and this
     * method fails open if it is somehow reached anyway. Locking someone out of
     * their own list because their phone has no PIN would be worse than the
     * privacy the lock is for.
     */
    private fun requestUnlock() {
        if (!isDeviceSecurelyLocked()) {
            setLocked(false)
            return
        }
        if (biometricPromptVisible) return
        biometricPromptVisible = true

        val prompt = BiometricPrompt.Builder(this)
            .setTitle("Unlock AniSequel")
            .setSubtitle("Confirm it's you to see your list")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        try {
            prompt.authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        biometricPromptVisible = false
                        setLocked(false)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // Cancelled, or the credential was wrong too many times. The
                        // gate stays up; the reader can try again or leave.
                        biometricPromptVisible = false
                    }

                    override fun onAuthenticationFailed() {
                        // One bad fingerprint. The prompt stays up on purpose.
                    }
                }
            )
        } catch (e: Exception) {
            // A prompt that cannot be shown (no enrolled credential, an OEM that
            // rejects the authenticator combination, a window that is already
            // gone) must not latch `biometricPromptVisible`: that made the Unlock
            // button silently do nothing until the Activity was recreated.
            biometricPromptVisible = false
        }
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

/**
 * Only the fact that this Activity has already been unlocked, and only across a
 * configuration change; see [MainActivity.appLockState].
 */
internal class AppLockStateViewModel : ViewModel() {
    var unlockedSinceLaunch: Boolean = false
}

/**
 * Applies the full-screen choice once it is known.
 *
 * Guarded on `isLoaded` because the seeded settings cannot tell "the reader chose
 * windowed" from "nothing has been read yet"; hiding on the seed would re-hide the
 * bars for someone who turned them back on. Extracted from `onCreate` because the
 * choice is a window flag rather than a screen - the composition root does not need
 * one more branch.
 */
@Composable
private fun ImmersiveModeEffect(
    window: Window,
    isLoaded: Boolean,
    enabled: Boolean,
    onChoice: (Boolean) -> Unit
) {
    LaunchedEffect(isLoaded, enabled) {
        if (!isLoaded) return@LaunchedEffect
        onChoice(enabled)
        window.applyImmersiveMode(enabled)
    }
}

/**
 * Pads the app's root by the display cutout while the system bars are hidden.
 *
 * The bars being hidden also zeroes the screens' `statusBarsPadding()`, which is
 * what lets a page own the whole display - but on a cutout device that padding was
 * the only thing keeping a top app bar out from under the camera. Padding by the
 * cutout restores exactly that safety area and nothing else; the bars, when the
 * reader swipes them back, draw over it as usual.
 */
// `@Composable`, not a plain modifier factory: `windowInsetsPadding` reads the
// window's insets during composition, so the function that builds it must be a
// composable even though all it returns is a Modifier.
@Composable
private fun immersiveDisplayInsets(immersive: Boolean): Modifier =
    if (immersive) {
        Modifier.windowInsetsPadding(WindowInsets.displayCutout)
    } else {
        Modifier
    }