package com.anisequel.data.extensions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PLAYER_PREFERENCES_FILE = "anisequel_player"
private const val KEY_DECODER = "decoder"
private const val KEY_RENDERER = "renderer"
private const val KEY_AUDIO = "audio"
private const val KEY_ENHANCE = "enhance"
private const val KEY_SHADER = "shader"
private const val KEY_PITCH_CORRECTION = "pitch_correction"
private const val KEY_CACHE_MINUTES = "cache_minutes"
private const val KEY_SPEED_PRESETS = "speed_presets"
private const val KEY_PLAYBACK_SPEED = "playback_speed"

/** The Playback rows in the screenshot's Player Settings page. */
enum class DecoderMode(val storageValue: String, val label: String, val detail: String) {
    AUTO("auto", "Auto", "MediaCodec, with a software fallback"),
    MEDIACODEC("mediacodec", "mediaCodec", "Hardware decode when the file allows it"),
    SOFTWARE("software", "Software", "Software decode for a file the GPU refuses");

    companion object {
        val DEFAULT = AUTO
        fun fromStorage(value: String?) = entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

enum class RendererMode(val storageValue: String, val label: String, val detail: String) {
    AUTO("auto", "Auto-select (default)", "Let the player pick the surface for the file"),
    SURFACE("surface", "SurfaceView", "The fastest path; overlays draw above the video"),
    TEXTURE("texture", "TextureView", "Composable with transforms; costs a copy per frame");

    companion object {
        val DEFAULT = AUTO
        fun fromStorage(value: String?) = entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

enum class AudioEngineMode(val storageValue: String, val label: String, val detail: String) {
    DEFAULT("default", "Default Android AudioTrack API", "The platform mixer and its own latency"),
    LOW_LATENCY("low_latency", "Low-latency AudioTrack", "Smaller buffers; can crackle on some phones");

    companion object {
        fun fromStorage(value: String?) = entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * The Anime 4K Enhancement presets.
 *
 * These are colour-grade presets, not upscalers: [saturation] and [contrast] are
 * handed to a `ColorMatrix` on the video view. A real spatial upscaler is a
 * shader pipeline and is Phase 3; the page says so, and the toggle still does
 * something visible instead of being a stored lie.
 */
enum class ShaderProfile(
    val storageValue: String,
    val label: String,
    val blurb: String,
    val saturation: Float,
    val contrast: Float
) {
    MID_END(
        storageValue = "mid_end",
        label = "Mid-End",
        blurb = "Eg. GTX 980, GTX 1060, RX 570 - a gentle saturation lift",
        saturation = 1.08f,
        contrast = 1.03f
    ),
    HIGH_END(
        storageValue = "high_end",
        label = "High-End",
        blurb = "Modern phones and desktop GPUs - a stronger contrast pass",
        saturation = 1.14f,
        contrast = 1.07f
    );

    companion object {
        val DEFAULT = HIGH_END
        fun fromStorage(value: String?) = entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

data class PlayerSettings(
    val decoder: DecoderMode = DecoderMode.DEFAULT,
    val renderer: RendererMode = RendererMode.DEFAULT,
    val audio: AudioEngineMode = AudioEngineMode.DEFAULT,
    val enhance: Boolean = false,
    val shaderProfile: ShaderProfile = ShaderProfile.DEFAULT,
    /** Keep the voice at its recorded pitch when the speed changes. */
    val audioPitchCorrection: Boolean = true,
    /** Minutes of read-ahead buffering; the player's LoadControl uses this. */
    val cacheMinutes: Int = 2
)

/**
 * Player Settings, persisted.
 *
 * Each value is read once per process by the navigation root and written by the
 * Settings page, so the page's selected row and the player's actual choice cannot
 * disagree within a session.
 */
class PlayerPreferences(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PLAYER_PREFERENCES_FILE, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        PlayerSettings(
            decoder = DecoderMode.fromStorage(preferences.getString(KEY_DECODER, null)),
            renderer = RendererMode.fromStorage(preferences.getString(KEY_RENDERER, null)),
            audio = AudioEngineMode.fromStorage(preferences.getString(KEY_AUDIO, null)),
            enhance = preferences.getBoolean(KEY_ENHANCE, false),
            shaderProfile = ShaderProfile.fromStorage(preferences.getString(KEY_SHADER, null)),
            audioPitchCorrection = preferences.getBoolean(KEY_PITCH_CORRECTION, true),
            cacheMinutes = preferences.getInt(KEY_CACHE_MINUTES, 2).coerceIn(1, 30)
        )
    )
    val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    private val _speedPresets = MutableStateFlow(readSpeedPresets())
    val speedPresets: StateFlow<List<Float>> = _speedPresets.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(
        preferences.getFloat(KEY_PLAYBACK_SPEED, 1f).coerceIn(MIN_SPEED, MAX_SPEED)
    )
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun setDecoder(decoder: DecoderMode) = update(_settings.value.copy(decoder = decoder)) {
        preferences.edit().putString(KEY_DECODER, decoder.storageValue).apply()
    }

    fun setRenderer(renderer: RendererMode) = update(_settings.value.copy(renderer = renderer)) {
        preferences.edit().putString(KEY_RENDERER, renderer.storageValue).apply()
    }

    fun setAudio(audio: AudioEngineMode) = update(_settings.value.copy(audio = audio)) {
        preferences.edit().putString(KEY_AUDIO, audio.storageValue).apply()
    }

    fun setEnhance(enabled: Boolean) = update(_settings.value.copy(enhance = enabled)) {
        preferences.edit().putBoolean(KEY_ENHANCE, enabled).apply()
    }

    fun setShaderProfile(profile: ShaderProfile) =
        update(_settings.value.copy(shaderProfile = profile)) {
            preferences.edit().putString(KEY_SHADER, profile.storageValue).apply()
        }

    fun setAudioPitchCorrection(enabled: Boolean) =
        update(_settings.value.copy(audioPitchCorrection = enabled)) {
            preferences.edit().putBoolean(KEY_PITCH_CORRECTION, enabled).apply()
        }

    fun setCacheMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 30)
        update(_settings.value.copy(cacheMinutes = clamped)) {
            preferences.edit().putInt(KEY_CACHE_MINUTES, clamped).apply()
        }
    }

    fun setSpeedPresets(presets: List<Float>) {
        val cleaned = presets
            .filter { it.isFinite() && it in MIN_SPEED..MAX_SPEED }
            .map { (it * 100f).toInt() / 100f }
            .distinct()
            .sorted()
            .ifEmpty { DEFAULT_SPEED_PRESETS }
        preferences.edit().putString(KEY_SPEED_PRESETS, cleaned.joinToString(",")).apply()
        _speedPresets.value = cleaned
    }

    fun resetSpeedPresets() = setSpeedPresets(DEFAULT_SPEED_PRESETS)

    fun setPlaybackSpeed(value: Float) {
        val clamped = value.coerceIn(MIN_SPEED, MAX_SPEED)
        preferences.edit().putFloat(KEY_PLAYBACK_SPEED, clamped).apply()
        _playbackSpeed.value = clamped
    }

    private fun readSpeedPresets(): List<Float> {
        val raw = preferences.getString(KEY_SPEED_PRESETS, null) ?: return DEFAULT_SPEED_PRESETS
        return raw.split(',')
            .mapNotNull { it.trim().toFloatOrNull() }
            .filter { it in MIN_SPEED..MAX_SPEED }
            .distinct()
            .sorted()
            .ifEmpty { DEFAULT_SPEED_PRESETS }
    }

    private inline fun update(next: PlayerSettings, write: () -> Unit) {
        write()
        _settings.value = next
    }

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 3f
        val DEFAULT_SPEED_PRESETS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    }
}
