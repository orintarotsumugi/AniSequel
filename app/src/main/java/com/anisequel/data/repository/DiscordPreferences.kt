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
import com.anisequel.domain.discord.DiscordActivityType
import com.anisequel.domain.discord.DiscordAuthKind
import com.anisequel.domain.discord.DiscordJokeInterval
import com.anisequel.domain.discord.DiscordLogin
import com.anisequel.domain.discord.DiscordOnlineStatus
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
 * Discord Rich Presence settings and direct account login state.
 *
 * Application-scoped and read through a [Flow]: the presence host at the app
 * root and the Settings page are two readers of one preference.
 *
 * The token is the reader's own Discord token used to connect to Discord's
 * gateway from this device. No relay is involved.
 */
class DiscordPreferences(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("discord_enabled")
        val TOKEN = stringPreferencesKey("discord_token")
        val AUTH_KIND = stringPreferencesKey("discord_auth_kind")
        val OAUTH_CLIENT_ID = stringPreferencesKey("discord_oauth_client_id")
        val LINKED = booleanPreferencesKey("discord_linked")
        val USERNAME = stringPreferencesKey("discord_username")
        val USER_ID = stringPreferencesKey("discord_user_id")
        val AVATAR_URL = stringPreferencesKey("discord_avatar_url")
        val SHOW_ACTIVITY = booleanPreferencesKey("discord_show_activity")
        val SHOW_COVER = booleanPreferencesKey("discord_show_cover")
        val SHOW_STATS = booleanPreferencesKey("discord_show_stats")
        val SHOW_JOKES = booleanPreferencesKey("discord_show_jokes")
        val SHOW_BUTTONS = booleanPreferencesKey("discord_show_buttons")
        val SHOW_ELAPSED = booleanPreferencesKey("discord_show_elapsed")
        val HIDE_TITLES = booleanPreferencesKey("discord_hide_titles")
        val PREVIEW_PINNED = booleanPreferencesKey("discord_preview_pinned")
        val JOKE_INTERVAL = stringPreferencesKey("discord_joke_interval")
        val JOKE_TONES = stringSetPreferencesKey("discord_joke_tones")
        val JOKE_IN_DETAILS = booleanPreferencesKey("discord_joke_in_details")
        val CUSTOM_JOKES = stringSetPreferencesKey("discord_custom_jokes")
        val ACTIVITY_TYPE = stringPreferencesKey("discord_activity_type")
        val ONLINE_STATUS = stringPreferencesKey("discord_online_status")
        val ACTIVITY_NAME = stringPreferencesKey("discord_activity_name")
        val PRIMARY_BUTTON = stringPreferencesKey("discord_primary_button")
        val SECONDARY_BUTTON = stringPreferencesKey("discord_secondary_button")
        val SECONDARY_BUTTON_URL = stringPreferencesKey("discord_secondary_button_url")
        val REDUCE_CHATTER = booleanPreferencesKey("discord_reduce_chatter")

        fun details(kind: DiscordActivityKind) =
            stringPreferencesKey("discord_details_${kind.storageValue}")

        fun state(kind: DiscordActivityKind) =
            stringPreferencesKey("discord_state_${kind.storageValue}")
    }

    val settings: Flow<DiscordSettings> = context.discordDataStore.data.map { preferences ->
        val linkedFlag = preferences[Keys.LINKED] ?: false
        DiscordSettings(
            enabled = preferences[Keys.ENABLED] ?: false,
            token = preferences[Keys.TOKEN].orEmpty(),
            // An install linked before the OAuth flow existed holds a pasted token
            // and no kind, which reads as TOKEN and keeps behaving exactly as it did.
            authKind = DiscordAuthKind.fromStorage(preferences[Keys.AUTH_KIND]),
            oauthClientId = preferences[Keys.OAUTH_CLIENT_ID].orEmpty(),
            // A relay-era install has `LINKED` set but no token, because the
            // relay held the credentials. Reading that flag as "connected" drew
            // "Connected to Discord" over a preview that could never publish, so
            // a link only counts when there is a token to publish with.
            linked = linkedFlag && !preferences[Keys.TOKEN].isNullOrBlank(),
            username = preferences[Keys.USERNAME].orEmpty(),
            userId = preferences[Keys.USER_ID].orEmpty(),
            avatarUrl = preferences[Keys.AVATAR_URL].orEmpty(),
            showActivity = preferences[Keys.SHOW_ACTIVITY] ?: true,
            showCover = preferences[Keys.SHOW_COVER] ?: true,
            showStats = preferences[Keys.SHOW_STATS] ?: true,
            showJokes = preferences[Keys.SHOW_JOKES] ?: true,
            showButtons = preferences[Keys.SHOW_BUTTONS] ?: true,
            showElapsedTime = preferences[Keys.SHOW_ELAPSED] ?: true,
            hideTitles = preferences[Keys.HIDE_TITLES] ?: false,
            previewPinned = preferences[Keys.PREVIEW_PINNED] ?: true,
            jokeInterval = DiscordJokeInterval.fromStorage(preferences[Keys.JOKE_INTERVAL]),
            // An absent key means every tone.
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
            }.toMap(),
            activityType = DiscordActivityType.fromStorage(preferences[Keys.ACTIVITY_TYPE]),
            onlineStatus = DiscordOnlineStatus.fromStorage(preferences[Keys.ONLINE_STATUS]),
            // Stored blank means "use the default", and the *composer* applies
            // that. Filling the default in here instead would make an emptied
            // field snap back to "AniSequel" while the reader's caret was still
            // in it, because the stored value flows straight back out.
            activityName = preferences[Keys.ACTIVITY_NAME].orEmpty(),
            primaryButtonLabel = preferences[Keys.PRIMARY_BUTTON].orEmpty(),
            secondaryButtonLabel = preferences[Keys.SECONDARY_BUTTON].orEmpty(),
            secondaryButtonUrl = preferences[Keys.SECONDARY_BUTTON_URL].orEmpty(),
            reduceNetworkChatter = preferences[Keys.REDUCE_CHATTER] ?: true
        )
    }
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

    suspend fun setShowElapsedTime(show: Boolean) {
        context.discordDataStore.edit { it[Keys.SHOW_ELAPSED] = show }
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

    suspend fun setActivityType(type: DiscordActivityType) {
        context.discordDataStore.edit { it[Keys.ACTIVITY_TYPE] = type.storageValue }
    }

    suspend fun setOnlineStatus(status: DiscordOnlineStatus) {
        context.discordDataStore.edit { it[Keys.ONLINE_STATUS] = status.storageValue }
    }

    suspend fun setActivityName(name: String) {
        context.discordDataStore.edit { it[Keys.ACTIVITY_NAME] = name.trim() }
    }

    suspend fun setPrimaryButtonLabel(label: String) {
        context.discordDataStore.edit { it[Keys.PRIMARY_BUTTON] = label.trim() }
    }

    suspend fun setSecondaryButton(label: String, url: String) {
        context.discordDataStore.edit { preferences ->
            preferences[Keys.SECONDARY_BUTTON] = label.trim()
            preferences[Keys.SECONDARY_BUTTON_URL] = url.trim()
        }
    }

    /**
     * Puts the presence's appearance back to what the app ships.
     *
     * Only the customization card's keys: switching the feature off, the token,
     * the account and the phrasing the reader wrote for each activity are all
     * choices with more weight than a label, and a "reset" that quietly signed
     * them out of Discord would be the wrong kind of reset.
     */
    suspend fun resetCustomization() {
        context.discordDataStore.edit { preferences ->
            preferences.remove(Keys.ACTIVITY_NAME)
            preferences.remove(Keys.PRIMARY_BUTTON)
            preferences.remove(Keys.SECONDARY_BUTTON)
            preferences.remove(Keys.SECONDARY_BUTTON_URL)
            preferences.remove(Keys.ACTIVITY_TYPE)
            preferences.remove(Keys.ONLINE_STATUS)
            preferences.remove(Keys.SHOW_ELAPSED)
            preferences.remove(Keys.REDUCE_CHATTER)
        }
    }

    suspend fun setReduceNetworkChatter(reduce: Boolean) {
        context.discordDataStore.edit { it[Keys.REDUCE_CHATTER] = reduce }
    }

    /** Remembers a verified token and Discord identity in one write. */
    suspend fun link(
        token: String,
        username: String,
        userId: String,
        avatarUrl: String,
        authKind: DiscordAuthKind = DiscordAuthKind.TOKEN
    ) {
        context.discordDataStore.edit { preferences ->
            preferences[Keys.TOKEN] = DiscordLogin.normalizeToken(token)
            preferences[Keys.AUTH_KIND] = authKind.storageValue
            preferences[Keys.LINKED] = true
            preferences[Keys.USERNAME] = username
            preferences[Keys.USER_ID] = userId
            preferences[Keys.AVATAR_URL] = avatarUrl
        }
    }

    /**
     * Stores the Discord application the reader signed in with.
     *
     * Kept when the account is unlinked: it describes the app, not the account, and
     * asking for it again after every log out would be a field the reader has to
     * hunt down twice. Blank is stored as a removal, so a cleared field stops
     * offering a sign-in that cannot work.
     */
    suspend fun setOAuthClientId(clientId: String) {
        val trimmed = clientId.trim()
        context.discordDataStore.edit { preferences ->
            if (trimmed.isEmpty()) {
                preferences.remove(Keys.OAUTH_CLIENT_ID)
            } else {
                preferences[Keys.OAUTH_CLIENT_ID] = trimmed
            }
        }
    }

    /** Forgets the token and identity but keeps the reader's settings and phrasing. */
    suspend fun unlink() {
        context.discordDataStore.edit { preferences ->
            preferences[Keys.TOKEN] = ""
            preferences[Keys.AUTH_KIND] = DiscordAuthKind.TOKEN.storageValue
            preferences[Keys.LINKED] = false
            preferences[Keys.USERNAME] = ""
            preferences[Keys.USER_ID] = ""
            preferences[Keys.AVATAR_URL] = ""
        }
    }

    suspend fun setTemplate(kind: DiscordActivityKind, phrasing: DiscordPhrasing) {
        context.discordDataStore.edit { preferences ->
            preferences[Keys.details(kind)] = phrasing.details
            preferences[Keys.state(kind)] = phrasing.state
        }
    }

    /** Restores one activity's shipped phrasing by removing the override keys. */
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
