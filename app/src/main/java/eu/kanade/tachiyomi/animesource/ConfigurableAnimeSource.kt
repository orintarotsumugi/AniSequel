package eu.kanade.tachiyomi.animesource

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import com.anisequel.data.extensions.runtime.RuntimeSharedPreferences

/**
 * Clean-room Aniyomi configurable-source ABI.
 *
 * The preference screen is never built by this runtime (extension settings UI is
 * a later phase), but the interface must exist or a configurable source fails to
 * load. Preferences are an in-memory store: the isolated process cannot write
 * the app's files, and a stored switch that silently vanished would be worse
 * than one scoped to the session.
 */
interface ConfigurableAnimeSource {
    fun getSourcePreferences(): SharedPreferences = RuntimeSharedPreferences()
    fun setupPreferenceScreen(screen: PreferenceScreen)
}