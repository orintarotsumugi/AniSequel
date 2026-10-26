package com.anisequel

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.anisequel.data.diagnostics.CrashReports
import com.anisequel.data.network.NetworkClient
import com.anisequel.data.repository.AuthRepository
import com.anisequel.data.repository.AuthRepositoryImpl
import com.anisequel.data.repository.DiagnosticsPreferences

/**
 * Supplies Coil with the app's tuned [ImageLoader].
 *
 * Without this, Coil builds its own default loader with a small memory cache,
 * and the screen - which is essentially a wall of posters - re-downloads cover
 * art it already has every time the user scrolls back up.
 */
class AniSequelApplication : Application(), ImageLoaderFactory {

    /**
     * The one auth repository for the process.
     *
     * It used to be constructed in `MainActivity.onCreate`, which meant every
     * rotation built a second repository while the retained `AuthViewModel` kept
     * the first one. The Activity's navigation tree and the ViewModel then
     * disagreed about the token cache and the DataStore instance - a sign-out
     * through the old ViewModel could leave the new Activity's cache warm - and
     * NetworkClient's per-repository service cache was defeated on each rotation.
     */
    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(applicationContext)
    }

    /**
     * The diagnostics setting, created here so its file is read while the app is
     * healthy rather than in the crash handler that consults it.
     */
    private val diagnosticsPreferences by lazy {
        DiagnosticsPreferences(applicationContext)
    }

    /**
     * The one place a force-stop is recorded.
     *
     * Here rather than in `MainActivity` because the crashes worth catching are not
     * all in the Activity: a background repository call or a widget update runs
     * without one, and a handler installed by the first Activity would miss exactly
     * the failures that happen before any Activity exists. The handler chains to
     * the platform's own, so the app still stops - it just leaves its trace on disk
     * for the About page to show. See [CrashReports] for why there is no backend.
     *
     * The setting is passed as a lambda, not read here: it is off by default and can
     * be flipped in Settings while the app runs, so the answer that matters is the
     * one at crash time.
     */
    override fun onCreate() {
        super.onCreate()
        val preferences = diagnosticsPreferences
        CrashReports.install(this) { preferences.crashReportsEnabled }
    }

    override fun newImageLoader(): ImageLoader = NetworkClient.createImageLoader(this)
}