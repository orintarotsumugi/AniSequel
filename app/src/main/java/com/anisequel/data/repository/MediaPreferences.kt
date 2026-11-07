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
import com.anisequel.domain.gif.GifProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Chat view is on by default: a window is strictly less work than the whole
 * thread, and the reader who wants the old behaviour can turn it off one tap
 * deeper than the one that would have to be taken to notice it was missing.
 */
private const val DEFAULT_CHAT_VIEW = true

/** Polling is on with it, and separately switchable - see [CommentMediaSettings]. */
private const val DEFAULT_POLLING = true

/**
 * Strangers are opt-in. The site-wide stream is the one feed in the app that is
 * full of people the reader did not choose to follow, and it spends the same
 * ~30-requests-a-minute budget the dashboard, the list and the friends feed draw
 * on - so it ships off and is turned on once, in Settings.
 */
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
 * [CommentMediaSettings.tenorApiKey], [CommentMediaSettings.klipyApiKey] and
 * [CommentMediaSettings.giphyApiKey] are the fields that reach a third party with a
 * credential of their own. They are stored encrypted and each is sent only to its
 * own provider. See [TokenCipher] for why a keystore key rather than a plaintext
 * field. The reader's AniList token and these keys live under *different* keystore
 * entries: an unreadable GIF key must not be able to destroy the entry the session
 * token is sealed under, which is the reason [KeystoreTokenCipher] takes its alias.
 *
 * ## Why the provider is its own key
 *
 * It used to be implied by "a Tenor key exists", so saving a key silently switched
 * the picker and clearing it switched back. With three keyed providers that rule
 * cannot say which one was meant. [gifProvider] records the choice; the keys are
 * independent, so changing provider does not delete a credential the reader may
 * want again later.
 */
data class CommentMediaSettings(
    val chatView: Boolean = DEFAULT_CHAT_VIEW,
    val pollForNewComments: Boolean = DEFAULT_POLLING,
    val tenorApiKey: String? = null,
    val globalActivityFeed: Boolean = DEFAULT_GLOBAL_FEED,
    val gifProvider: GifProvider = GifProvider.DEFAULT,
    val klipyApiKey: String? = null,
    val giphyApiKey: String? = null
) {
    /** Whether a Tenor key is saved, independently of which provider is chosen. */
    val usesTenor: Boolean get() = !tenorApiKey.isNullOrBlank()

    /** The key the chosen provider needs, or null when it needs none. */
    val activeGifKey: String?
        get() = gifProvider.keyFrom(tenorApiKey, klipyApiKey, giphyApiKey)

    /**
     * The provider a picker can actually use.
     *
     * A keyed provider with no key is a search that can only fail, so it falls back
     * to the shipped default rather than showing an error under the search field
     * before the reader has typed anything. Since the bundled-key change
     * ([GifProvider.DEFAULT] is Klipy and the app carries a key for it) the fallback
     * is itself a search that answers. The stored choice is kept, so pasting the key
     * later resumes the provider the reader picked.
     */
    val resolvedGifProvider: GifProvider
        get() = if (gifProvider.needsKey && activeGifKey.isNullOrBlank()) {
            GifProvider.DEFAULT
        } else {
            gifProvider
        }
}

class MediaPreferences(
    private val context: Context,
    private val cipher: TokenCipher = KeystoreTokenCipher(TENOR_KEY_ALIAS)
) {

    private object Keys {
        val CHAT_VIEW = booleanPreferencesKey("comment_chat_view")
        val POLL_COMMENTS = booleanPreferencesKey("comment_poll_new")
        val TENOR_KEY_ENCRYPTED = stringPreferencesKey("tenor_api_key_encrypted")
        val KLIPY_KEY_ENCRYPTED = stringPreferencesKey("klipy_api_key_encrypted")
        val GIPHY_KEY_ENCRYPTED = stringPreferencesKey("giphy_api_key_encrypted")
        val GIF_PROVIDER = stringPreferencesKey("gif_provider")
        val GLOBAL_FEED = booleanPreferencesKey("global_activity_feed")
    }

    val settings: Flow<CommentMediaSettings> = context.commentMediaDataStore.data.map { preferences ->
        // Unreadable ciphertext means the keystore key was invalidated, which is
        // not a question to ask again on every read: the value is treated as
        // never having been set, and the next save overwrites it.
        val tenorKey = preferences[Keys.TENOR_KEY_ENCRYPTED]
            ?.let { cipher.decrypt(it) }?.takeIf { it.isNotBlank() }
        // The provider was implied by the Tenor key before there was a choice. An
        // install upgrading from that build has a key and no stored choice, and
        // silently dropping it would send a reader who chose Tenor back to the
        // booru without saying so.
        val provider = GifProvider.fromStorage(preferences[Keys.GIF_PROVIDER])
            ?: if (tenorKey != null) GifProvider.TENOR else GifProvider.DEFAULT
        CommentMediaSettings(
            chatView = preferences[Keys.CHAT_VIEW] ?: DEFAULT_CHAT_VIEW,
            pollForNewComments = preferences[Keys.POLL_COMMENTS] ?: DEFAULT_POLLING,
            globalActivityFeed = preferences[Keys.GLOBAL_FEED] ?: DEFAULT_GLOBAL_FEED,
            gifProvider = provider,
            tenorApiKey = tenorKey,
            klipyApiKey = preferences[Keys.KLIPY_KEY_ENCRYPTED]
                ?.let { cipher.decrypt(it) }?.takeIf { it.isNotBlank() },
            giphyApiKey = preferences[Keys.GIPHY_KEY_ENCRYPTED]
                ?.let { cipher.decrypt(it) }?.takeIf { it.isNotBlank() }
        )
    }
        // DataStore runs its own reads on IO, but this `map` body - three
        // keystore decryptions per snapshot - runs wherever the collector
        // collects, and every collector is a composition. `AuthRepositoryImpl`
        // wraps the same pattern for the same reason: a keystore round trip on
        // the main thread is the stutter on cold start, only worse here because
        // three keys and several simultaneous collectors pay it, and every
        // settings toggle re-emits the file and re-pays them all.
        .flowOn(Dispatchers.IO)

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
    suspend fun setTenorApiKey(apiKey: String?) = setEncryptedKey(Keys.TENOR_KEY_ENCRYPTED, apiKey)

    /** The Klipy key, under its own entry so switching providers keeps both. */
    suspend fun setKlipyApiKey(apiKey: String?) = setEncryptedKey(Keys.KLIPY_KEY_ENCRYPTED, apiKey)

    /** The GIPHY key, under its own entry so switching providers keeps both. */
    suspend fun setGiphyApiKey(apiKey: String?) = setEncryptedKey(Keys.GIPHY_KEY_ENCRYPTED, apiKey)

    /**
     * The index a search is sent to.
     *
     * Stored even when the chosen provider has no key: the picker falls back at read
     * time, and a reader who pastes the key later should not have to pick again.
     */
    suspend fun setGifProvider(provider: GifProvider) {
        context.commentMediaDataStore.edit { preferences ->
            preferences[Keys.GIF_PROVIDER] = provider.storageValue
        }
    }

    /**
     * Stores [apiKey] encrypted, or clears it when blank.
     *
     * The blank case is a real one and it has to leave no ciphertext behind: a
     * reader who pastes a key, then decides they do not want that provider, should
     * get the anonymous default back rather than a saved-but-empty credential that
     * every search then rejects.
     */
    private suspend fun setEncryptedKey(key: Preferences.Key<String>, apiKey: String?) {
        val trimmed = apiKey?.trim().orEmpty()
        context.commentMediaDataStore.edit { preferences ->
            if (trimmed.isEmpty()) {
                preferences.remove(key)
            } else {
                preferences[key] = cipher.encrypt(trimmed)
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
            preferences.remove(Keys.KLIPY_KEY_ENCRYPTED)
            preferences.remove(Keys.GIPHY_KEY_ENCRYPTED)
            preferences.remove(Keys.GIF_PROVIDER)
            preferences.remove(Keys.GLOBAL_FEED)
        }
    }

    companion object {
        /** Not the AniList token's alias - see the class documentation. */
        const val TENOR_KEY_ALIAS = "anisequel_gif_provider_key"
    }
}
