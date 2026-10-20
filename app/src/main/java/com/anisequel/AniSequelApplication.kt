package com.anisequel

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.anisequel.data.network.NetworkClient
import com.anisequel.data.repository.AuthRepository
import com.anisequel.data.repository.AuthRepositoryImpl

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

    override fun newImageLoader(): ImageLoader = NetworkClient.createImageLoader(this)
}