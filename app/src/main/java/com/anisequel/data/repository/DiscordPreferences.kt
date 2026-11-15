package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.domain.discord.DiscordActivityKind
import com.anisequel.domain.discord.DiscordJokeInterval
import com.anisequel.domain.discord.DiscordPhrasing
import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.domain.jokes.JokeTone
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.discordDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_discord_prefs"
)

/**
 * The Discord presence switch, its phrasing and what it may say.
 *
 * Application-scoped and read through a [Flow] for the same reason as
 * [JokePreferences]: the presence host at the app root and the page in Settings
 * are two readers of one preference, and two DataStore instances would let the
 * preview describe a presence the app never publishes.
 *
 * ## What is not stored
 *
 * There is no Discord token and no OAuth secret here. The link this app makes is
 * to the reader's own relay (see `DiscordRpcClient`); the relay holds whatever
 * Discord credentials it needs. Storing a Discord user token in a sideloaded APK
 * would put a credential that can read a whole account in an app that updates
 * itself from a GitHub release.
 */
class DiscordPreferences(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("discord_enabled")
        val RELAY_URL = stringPreferencesKey("discord_relay_url")
        val APPLICATION_ID = stringPreferencesKey("discord_application_id")
        val LINKED = booleanPreferencesKey("discord_linked")
        val LINKED_USER = stringPreferencesKey("discord_linked_user")
        val SHOW_ACTIVITY = booleanPreferencesKey("discord_show_activity")
        val SHOW_COVER = booleanPreferencesKey("discord_show_cover")
        val SHOW_STATS = booleanPreferencesKey("discord_show_stats")
        val SHOW_JOKES = booleanPreferencesKey("discord_show_jokes")
        val SHOW_BUTTONS = booleanPreferencesKey("discord_show_buttons")
        val HIDE_TITLES = booleanPreferencesKey("discord_hide_titles")
        val PREVIEW_PINNED = booleanPreferencesKey("discord_preview_pinned")
        val JOKE_INTERVAL = stringPreferencesKey("discord_joke_interval")
        val JOKE_TONES = stringSetPreferencesKey("discord_joke_tones")
        val JOKE_IN_DETAILS = booleanPreferencesKey("discord_joke_in_details")
        val CUSTOM_JOKES = stringSetPreferencesKey("discord_custom_jokes")

        fun details(kind: DiscordActivityKind) =
            stringPreferencesKey("discord_details_${kind.storageValue}")

        fun state(kind: DiscordActivityKind) =
            stringPreferencesKey("discord_state_${kind.storageValue}")
    }

    val settings: Flow<DiscordSettings> = context.discordDataStore.data.map { preferences ->
        DiscordSettings(
            enabled = preferences[Keys.ENABLED] ?: false,
            relayUrl = preferences[Keys.RELAY_URL].orEmpty(),
            applicationId = preferences[Keys.APPLICATION_ID].orEmpty(),
            linked = preferences[Keys.LINKED] ?: false,
            linkedUser = preferences[Keys.LINKED_USER].orEmpty(),
            showActivity = preferences[Keys.SHOW_ACTIVITY] ?: true,
            showCover = preferences[Keys.SHOW_COVER] ?: true,
            showStats = preferences[Keys.SHOW_STATS] ?: true,
            showJokes = preferences[Keys.SHOW_JOKES] ?: true,
            showButtons = preferences[Keys.SHOW_BUTTONS] ?: true,
            hideTitles = preferences[Keys.HIDE_TITLES] ?: false,
            previewPinned = preferences[Keys.PREVIEW_PINNED] ?: true,
            jokeInterval = DiscordJokeInterval.fromStorage(preferences[Keys.JOKE_INTERVAL]),
            // An absent key is every tone, matching the joke toast's own default:
            // reading it as empty would leave a fresh install with nothing to draw
            // from, and the composer would fall back to "all" anyway.
            jokeTones = JokeTone.fromStorage(
                preferences[Keys.JOKE_TONES] ?: JokeTone.entries.map { it.storageValue }.toSet()
            ),
            jokeInDetails = preferences[Keys.JOKE_IN_DETAILS] ?: false,
            customJokes = preferences[Keys.CUSTOM_JOKES].orEmpty().sorted(),
            phrasing = DiscordActivityKind.entries.mapNotNull { kind ->
                val details = preferences[Keys.details(kind)]
                val state = preferences[Keys.state(kind)]
                if (details == null && state == null) {
                    null
                } else {
                    val default = DiscordPhrasing.defaultFor(kind)
                    kind to DiscordPhrasing(
                        details = details ?: default.details,
                        state = state ?: default.state
                    )
                }
            }.toMap()
        )
    }
        // An unreadable store must not leave the page looking like every switch
        // is broken. A terminated flow would keep drawing the last value and
        // ignore all later edits, so the defaults are the only visible, honest
        // state; the persisted choices return if the read recovers.
        .catch { error ->
            if (error is IOException) emit(DiscordSettings()) else throw error
        }

    suspend fun setEnabled(enabled: Boolean) {
        context.discordDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setShowActivity(show: Boolean) {
        context.discordDataStore.edit { it[Keys.SHOW_ACTIVITY] = show }
    }

    suspend fun setShowCover(show: Boolean) {
        context.discordDataStore.edit { it[Keys.SHOW_COVER] = show }
    }

    suspend fun setShowStats(show: Boolean) {
        context.discordDataStore.edit { it[Keys.SHOW_STATS] = show }
    }

    suspend fun setShowJokes(show: Boolean) {
        context.discordDataStore.edit { it[Keys.SHOW_JOKES] = show }
    }

    suspend fun setShowButtons(show: Boolean) {
        context.discordDataStore.edit { it[Keys.SHOW_BUTTONS] = show }
    }

    suspend fun setHideTitles(hide: Boolean) {
        context.discordDataStore.edit { it[Keys.HIDE_TITLES] = hide }
    }

    suspend fun setPreviewPinned(pinned: Boolean) {
        context.discordDataStore.edit { it[Keys.PREVIEW_PINNED] = pinned }
    }

    suspend fun setJokeInterval(interval: DiscordJokeInterval) {
        context.discordDataStore.edit { it[Keys.JOKE_INTERVAL] = interval.storageValue }
    }

    suspend fun setJokeTones(tones: Set<JokeTone>) {
        context.discordDataStore.edit { preferences ->
            preferences[Keys.JOKE_TONES] = tones.map { it.storageValue }.toSet()
        }
    }

    suspend fun setJokeInDetails(inDetails: Boolean) {
        context.discordDataStore.edit { it[Keys.JOKE_IN_DETAILS] = inDetails }
    }

    /**
     * Remembers a relay the probe actually answered.
     *
     * Deliberately one write: [linked] must never be true while [relayUrl] or the
     * application id still describes the previous relay, which is what two
     * separate `edit` calls would allow if the process died between them. A
     * presence sent to the wrong relay is a privacy failure, not a cosmetic one.
     */
    suspend fun link(relayUrl: String, applicationId: String, userName: String) {
        context.discordDataStore.edit { preferences ->
            preferences[Keys.RELAY_URL] = relayUrl
            preferences[Keys.APPLICATION_ID] = applicationId
            preferences[Keys.LINKED] = true
            preferences[Keys.LINKED_USER] = userName
        }
    }

    /** Forgets the link but keeps the switches and the reader's custom lines. */
    suspend fun unlink() {
        context.discordDataStore.edit { preferences ->
            preferences[Keys.LINKED] = false
            preferences[Keys.LINKED_USER] = ""
        }
    }

    suspend fun setTemplate(kind: DiscordActivityKind, phrasing: DiscordPhrasing) {
        context.discordDataStore.edit { preferences ->
            preferences[Keys.details(kind)] = phrasing.details
            preferences[Keys.state(kind)] = phrasing.state
        }
    }

    /**
     * Restores one activity's shipped phrasing.
     *
     * The keys are *removed*, not written with the defaults: writing them would
     * make a later build that improves its defaults unable to reach this reader,
     * because an explicit value always wins. Removing is the only honest reset.
     */
    suspend fun resetTemplate(kind: DiscordActivityKind) {
        context.discordDataStore.edit { preferences ->
            preferences.remove(Keys.details(kind))
            preferences.remove(Keys.state(kind))
        }
    }

    suspend fun addCustomJoke(line: String) {
        val trimmed = line.trim().take(280)
        if (trimmed.isEmpty()) return
        context.discordDataStore.edit { preferences ->
            preferences[Keys.CUSTOM_JOKES] =
                preferences[Keys.CUSTOM_JOKES].orEmpty() + trimmed
        }
    }

    suspend fun removeCustomJoke(line: String) {
        context.discordDataStore.edit { preferences ->
            preferences[Keys.CUSTOM_JOKES] = preferences[Keys.CUSTOM_JOKES]
                .orEmpty()
                .minus(line)
        }
    }

    suspend fun clearCustomJokes() {
        context.discordDataStore.edit { preferences ->
            preferences.remove(Keys.CUSTOM_JOKES)
        }
    }
}
