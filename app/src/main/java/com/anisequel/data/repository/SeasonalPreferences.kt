package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_DENSITY
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_PILE_DEPTH
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_SIZE_SCALE
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_SPEED_SCALE
import com.anisequel.domain.seasonal.DEFAULT_SEASONAL_WIND_STRENGTH
import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.SeasonalSettings
import com.anisequel.domain.seasonal.particleSizeScale
import com.anisequel.domain.seasonal.particleSpeedScale
import com.anisequel.domain.seasonal.pileDepthScale
import com.anisequel.domain.seasonal.windStrengthScale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.seasonalDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_seasonal"
)

/**
 * The reader's seasonal overlay: whether it is on, which shape it draws, how
 * dense, large and fast it is, whether it settles and how it takes the wind.
 *
 * A DataStore of its own rather than keys on [ThemePreferences], for the same
 * reason the joke and arriving preferences have one: this is a body of choices
 * that changes together, and a dedicated file keeps a corrupted seasonal block
 * from taking the theme down with it. It is read through a [Flow] so the overlay
 * and the Settings preview share one answer - two readers of one file, rather
 * than a preview that describes a sky the app is not drawing.
 *
 * Writes clamp. The sliders already keep to their ranges, but a preference file
 * is a file: a value restored from an old build, or from a device whose editor
 * rewrote it, must land inside the range the canvas can draw. `sanitised()` also
 * runs on read, because a `NaN` reaching a draw radius is a crash rather than a
 * strange-looking particle.
 *
 * The four 1.8.1 keys default sensibly when absent, which is what keeps an
 * install that predates them from needing a migration: snow settles and the sky
 * takes a gust out of the box, and a shrug of the switch turns each off.
 */
class SeasonalPreferences(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("seasonal_enabled")
        val EFFECT = stringPreferencesKey("seasonal_effect")
        val DENSITY = floatPreferencesKey("seasonal_density")
        val SIZE_SCALE = floatPreferencesKey("seasonal_size_scale")
        val SPEED_SCALE = floatPreferencesKey("seasonal_speed_scale")
        val SETTLE = booleanPreferencesKey("seasonal_settle")
        val PILE_DEPTH = floatPreferencesKey("seasonal_pile_depth")
        val REACT_TO_WIND = booleanPreferencesKey("seasonal_react_to_wind")
        val WIND_STRENGTH = floatPreferencesKey("seasonal_wind_strength")
    }

    val settings: Flow<SeasonalSettings> = context.seasonalDataStore.data.map { preferences ->
        SeasonalSettings(
            enabled = preferences[Keys.ENABLED] ?: false,
            effect = SeasonalEffect.fromStorage(preferences[Keys.EFFECT]),
            density = preferences[Keys.DENSITY] ?: DEFAULT_SEASONAL_DENSITY,
            sizeScale = preferences[Keys.SIZE_SCALE] ?: DEFAULT_SEASONAL_SIZE_SCALE,
            speedScale = preferences[Keys.SPEED_SCALE] ?: DEFAULT_SEASONAL_SPEED_SCALE,
            settle = preferences[Keys.SETTLE] ?: true,
            pileDepth = preferences[Keys.PILE_DEPTH] ?: DEFAULT_SEASONAL_PILE_DEPTH,
            reactToWind = preferences[Keys.REACT_TO_WIND] ?: true,
            windStrength = preferences[Keys.WIND_STRENGTH] ?: DEFAULT_SEASONAL_WIND_STRENGTH
        ).sanitised()
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.seasonalDataStore.edit { preferences ->
            preferences[Keys.ENABLED] = enabled
        }
    }

    suspend fun setEffect(effect: SeasonalEffect) {
        context.seasonalDataStore.edit { preferences ->
            preferences[Keys.EFFECT] = effect.storageValue
        }
    }

    suspend fun setDensity(density: Float) {
        val clamped = SeasonalSettings(density = density).sanitised().density
        context.seasonalDataStore.edit { preferences ->
            preferences[Keys.DENSITY] = clamped
        }
    }

    suspend fun setSizeScale(sizeScale: Float) {
        context.seasonalDataStore.edit { preferences ->
            preferences[Keys.SIZE_SCALE] = particleSizeScale(sizeScale)
        }
    }

    suspend fun setSpeedScale(speedScale: Float) {
        context.seasonalDataStore.edit { preferences ->
            preferences[Keys.SPEED_SCALE] = particleSpeedScale(speedScale)
        }
    }

    suspend fun setSettle(settle: Boolean) {
        context.seasonalDataStore.edit { preferences ->
            preferences[Keys.SETTLE] = settle
        }
    }

    suspend fun setPileDepth(pileDepth: Float) {
        context.seasonalDataStore.edit { preferences ->
            preferences[Keys.PILE_DEPTH] = pileDepthScale(pileDepth)
        }
    }

    suspend fun setReactToWind(reactToWind: Boolean) {
        context.seasonalDataStore.edit { preferences ->
            preferences[Keys.REACT_TO_WIND] = reactToWind
        }
    }

    suspend fun setWindStrength(windStrength: Float) {
        context.seasonalDataStore.edit { preferences ->
            preferences[Keys.WIND_STRENGTH] = windStrengthScale(windStrength)
        }
    }

    /** Back to the shipped sky: off, snow, the middle of every slider. */
    suspend fun reset() {
        context.seasonalDataStore.edit { preferences ->
            preferences.remove(Keys.ENABLED)
            preferences.remove(Keys.EFFECT)
            preferences.remove(Keys.DENSITY)
            preferences.remove(Keys.SIZE_SCALE)
            preferences.remove(Keys.SPEED_SCALE)
            preferences.remove(Keys.SETTLE)
            preferences.remove(Keys.PILE_DEPTH)
            preferences.remove(Keys.REACT_TO_WIND)
            preferences.remove(Keys.WIND_STRENGTH)
        }
    }
}
