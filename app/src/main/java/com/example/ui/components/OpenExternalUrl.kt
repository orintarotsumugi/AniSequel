package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands a URL to whatever app the user has for it.
 *
 * Moved out of `ui/screens/settings/SettingsComponents.kt` when the profile
 * screen needed it too. It is a `Context`-and-`String` helper with nothing to do
 * with settings, and a profile that opens its favourites on AniList had two
 * choices: import a function named out of a settings package, or copy it.
 * Copying is how two implementations of "start an ACTION_VIEW intent and hope"
 * end up disagreeing about the `NEW_TASK` flag, which is the one part of it that
 * matters when the launching context is not an Activity.
 */
fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}