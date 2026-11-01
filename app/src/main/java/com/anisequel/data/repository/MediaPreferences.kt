package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.data.security.KeystoreTokenCipher
import com.anisequel.data.security.TokenCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DEFAULT_CHAT_VIEW = true
private const val DEFAULT_POLLING = true
private const val DEFAULT_GLOBAL_FEED = false
private val Context.commentMediaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_comment_media"
)

/**
 * What the comment sheet does, and what it is allowed to send where.
 *
 * ## The three settings and why each one is a setting
 *
 * [CommentMediaSettings.chatView] decides whether a thread is fetched as the
 * newest window or as the whole list. It is on by default because the window is
 * strictly less work - a thread of nine hundred comments is one page rather than
 * nine hundred rows - but it is a switch because a reader who wants the old
 * behaviour, or who is comparing the two, should be able to have it without
 * arguing with the app.
 *
 * [CommentMediaSettings.pollForNewComments] is separated from it on purpose.
 * Opening at the newest message is a decision about one request; keeping the sheet
 * up to date while it is open is a request every fifteen seconds, and a reader on a
 * metered connection may well want the first and not the second. A single switch
 * that meant both would force a choice between the two.
 *
 * [CommentMediaSettings.tenorApiKey] is the one that reaches a third party with a
 * credential of its own, so it is stored encrypted and is never sent anywhere but
 * Tenor. See [TokenCipher] for why a keystore key rather than a plaintext field.
 * The reader's AniList token and this key live under *different* keystore entries:
 * an unreadable GIF key must not be able to destroy the entry the session token is
 * sealed under, which is the reason [KeystoreTokenCipher] takes its alias.
 */
data class CommentMediaSettings(
    val chatView: Boolean = DEFAULT_CHAT_VIEW,
    val pollForNewComments: Boolean = DEFAULT_POLLING,
    val tenorApiKey: String? = null,
    val globalActivityFeed: Boolean = DEFAULT_GLOBAL_FEED
) {
    /** Whether a GIF picker should use Tenor rather than the anonymous default. */
    val usesTenor: Boolean get() = !tenorApiKey.isNullOrBlank()
}

class MediaPreferences(
    private val context: Context,
    private val cipher: TokenCipher = KeystoreTokenCipher(TENOR_KEY_ALIAS)
) {

    /**
 * Chat view is on by default: a window is strictly less work than the whole
 * thread, and the reader who wants the old behaviour can turn it off one tap
 * deeper than the one that would have to be taken to notice it was missing.
 */

/** Polling is on with it, and separately switchable - see [CommentMediaSettings]. */

/**
 * Strangers are opt-in. The site-wide stream is the one feed in the app that is
 * full of people the reader did not choose to follow, and it spends the same
 * ~30-requests-a-minute budget the dashboard, the list and the friends feed draw
 * on - so it ships off and is turned on once, in Settings.
 */

private object Keys {
        val CHAT_VIEW = booleanPreferencesKey("comment_chat_view")
        val POLL_COMMENTS = booleanPreferencesKey("comment_poll_new")
        val TENOR_KEY_ENCRYPTED = stringPreferencesKey("tenor_api_key_encrypted")
        val GLOBAL_FEED = booleanPreferencesKey("global_activity_feed")
    }

    val settings: Flow<CommentMediaSettings> = context.commentMediaDataStore.data.map { preferences ->
        CommentMediaSettings(
            chatView = preferences[Keys.CHAT_VIEW] ?: DEFAULT_CHAT_VIEW,
            pollForNewComments = preferences[Keys.POLL_COMMENTS] ?: DEFAULT_POLLING,
            // Unreadable ciphertext means the keystore key was invalidated, which is
            // not a question to ask again on every read: the value is treated as
            // never having been set, and the next save overwrites it.
            globalActivityFeed = preferences[Keys.GLOBAL_FEED] ?: DEFAULT_GLOBAL_FEED,
            tenorApiKey = preferences[Keys.TENOR_KEY_ENCRYPTED]
                ?.let { cipher.decrypt(it) }?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun setChatView(enabled: Boolean) {
        context.commentMediaDataStore.edit { it[Keys.CHAT_VIEW] = enabled }
    }

    suspend fun setPollForNewComments(enabled: Boolean) {
        context.commentMediaDataStore.edit { it[Keys.POLL_COMMENTS] = enabled }
    }

    /**
     * Stores [apiKey], or clears it when blank.
     *
     * The blank case is a real one and it has to leave no ciphertext behind: a
     * reader who pastes a key, then decides they do not want to search Tenor, should
     * get the anonymous provider back rather than a saved-but-empty credential that
     * every search then rejects.
     */
    suspend fun setTenorApiKey(apiKey: String?) {
        val trimmed = apiKey?.trim().orEmpty()
        context.commentMediaDataStore.edit { preferences ->
            if (trimmed.isEmpty()) {
                preferences.remove(Keys.TENOR_KEY_ENCRYPTED)
            } else {
                preferences[Keys.TENOR_KEY_ENCRYPTED] = cipher.encrypt(trimmed)
            }
        }
    }

    /**
     * Whether the Activity tab offers the site-wide stream.
     *
     * Off by default: it is the one feed in the app full of strangers, and it
     * spends the same ~30-requests-a-minute budget the dashboard, the list and the
     * friends feed are drawing on. A reader who wants it turns it on once.
     */
    suspend fun setGlobalActivityFeed(enabled: Boolean) {
        context.commentMediaDataStore.edit { it[Keys.GLOBAL_FEED] = enabled }
    }

    /** Back to the shipped defaults, including forgetting the key. */
    suspend fun reset() {
        context.commentMediaDataStore.edit { preferences ->
            preferences.remove(Keys.CHAT_VIEW)
            preferences.remove(Keys.POLL_COMMENTS)
            preferences.remove(Keys.TENOR_KEY_ENCRYPTED)
            preferences.remove(Keys.GLOBAL_FEED)
        }
    }

    companion object {
        /** Not the AniList token's alias - see the class documentation. */
        const val TENOR_KEY_ALIAS = "anisequel_gif_provider_key"
    }
}
