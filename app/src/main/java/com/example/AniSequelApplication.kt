package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.data.network.NetworkClient

/**
 * Supplies Coil with the app's tuned [ImageLoader].
 *
 * Without this, Coil builds its own default loader with a small memory cache,
 * and the screen - which is essentially a wall of posters - re-downloads cover
 * art it already has every time the user scrolls back up.
 */
class AniSequelApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = NetworkClient.createImageLoader(this)
}