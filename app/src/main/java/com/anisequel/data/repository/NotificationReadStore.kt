package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.domain.notifications.NotificationPreferences
import com.anisequel.domain.notifications.NotificationReadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_notifications"
)

/**
 * The reader choices from Settings > Notifications.
 *
 * One store for the category switches, one for the per-account read mark, and
 * they are separate on purpose: the switches are about what this device wants
 * to show, while the read mark is about what this account has already been
 * shown. Signing into somebody else must not inherit the previous account's
 * inbox contents or read mark, so the mark is keyed by account; the category
 * switches remain a shared device preference.
 */
class NotificationPreferencesStore(private val context: Context) {

    private object Keys {
        val TRACKING = booleanPreferencesKey("tracking_enabled")
        val MARK_ON_OPEN = booleanPreferencesKey("mark_read_on_open")
        val AIRING = booleanPreferencesKey("category_airing")
        val LIKES = booleanPreferencesKey("category_likes")
        val REPLIES = booleanPreferencesKey("category_replies")
        val MENTIONS = booleanPreferencesKey("category_mentions")
        val MESSAGES = booleanPreferencesKey("category_messages")
        val FOLLOWS = booleanPreferencesKey("category_follows")
        val MEDIA_UPDATES = booleanPreferencesKey("category_media_updates")
        val SUBMISSIONS = booleanPreferencesKey("category_submissions")
    }

    val preferences: Flow<NotificationPreferences> =
        context.notificationStateDataStore.data.map { stored ->
            NotificationPreferences(
                trackingEnabled = stored[Keys.TRACKING] ?: true,
                markReadOnOpen = stored[Keys.MARK_ON_OPEN] ?: true,
                airing = stored[Keys.AIRING] ?: true,
                likes = stored[Keys.LIKES] ?: true,
                replies = stored[Keys.REPLIES] ?: true,
                mentions = stored[Keys.MENTIONS] ?: true,
                messages = stored[Keys.MESSAGES] ?: true,
                follows = stored[Keys.FOLLOWS] ?: true,
                mediaUpdates = stored[Keys.MEDIA_UPDATES] ?: true,
                submissions = stored[Keys.SUBMISSIONS] ?: true
            )
        }

    suspend fun setTrackingEnabled(enabled: Boolean) = write(Keys.TRACKING, enabled)

    suspend fun setMarkReadOnOpen(enabled: Boolean) = write(Keys.MARK_ON_OPEN, enabled)

    suspend fun setAiring(enabled: Boolean) = write(Keys.AIRING, enabled)

    suspend fun setLikes(enabled: Boolean) = write(Keys.LIKES, enabled)

    suspend fun setReplies(enabled: Boolean) = write(Keys.REPLIES, enabled)

    suspend fun setMentions(enabled: Boolean) = write(Keys.MENTIONS, enabled)

    suspend fun setMessages(enabled: Boolean) = write(Keys.MESSAGES, enabled)

    suspend fun setFollows(enabled: Boolean) = write(Keys.FOLLOWS, enabled)

    suspend fun setMediaUpdates(enabled: Boolean) = write(Keys.MEDIA_UPDATES, enabled)

    suspend fun setSubmissions(enabled: Boolean) = write(Keys.SUBMISSIONS, enabled)

    private suspend fun write(key: Preferences.Key<Boolean>, value: Boolean) {
        context.notificationStateDataStore.edit { it[key] = value }
    }
}

/**
 * The local read mark for one AniList account.
 *
 * Every key names the account, because a DataStore outlives sign-out. A shared
 * watermark would make the next account start with the previous person newest
 * notification already read - or, worse, make their old rows suddenly unread if
 * the id happened to be newer than the new account baseline.
 *
 * [load] returns a one-shot snapshot so the ViewModel can anchor a page without
 * waiting for the Flow's first emission and racing the first render.
 */
class NotificationReadStore(private val context: Context) {

    private object Keys {
        fun anchored(accountId: Int) = booleanPreferencesKey("anchored_$accountId")
        fun readThrough(accountId: Int) = intPreferencesKey("read_through_$accountId")
        fun readIds(accountId: Int) = stringSetPreferencesKey("read_ids_$accountId")
        fun resetGeneration(accountId: Int) = intPreferencesKey("reset_generation_$accountId")
    }

    fun state(accountId: Int): Flow<NotificationReadState> =
        context.notificationStateDataStore.data.map { stored -> stored.toReadState(accountId) }

    suspend fun load(accountId: Int): NotificationReadState =
        context.notificationStateDataStore.data.first().toReadState(accountId)

    /** Writes the first-run baseline, once, from a fetched page. */
    suspend fun anchor(accountId: Int, state: NotificationReadState) {
        context.notificationStateDataStore.edit { stored ->
            if (stored[Keys.anchored(accountId)] == true) return@edit
            // Keep the store's reset fence. The caller's snapshot was loaded
            // before this edit and can legitimately carry an older generation.
            val generation = maxOf(
                stored[Keys.resetGeneration(accountId)] ?: 0,
                state.resetGeneration
            )
            stored.writeReadState(
                accountId,
                state.copy(anchored = true, resetGeneration = generation)
            )
        }
    }

    suspend fun markRead(accountId: Int, ids: Collection<Int>) {
        if (ids.isEmpty()) return
        context.notificationStateDataStore.edit { stored ->
            val current = stored.toReadState(accountId)
            val next = ids.fold(current) { state, id -> state.markRead(id) }
            stored.writeReadState(accountId, next)
        }
    }

    suspend fun markAllRead(accountId: Int, throughId: Int) {
        context.notificationStateDataStore.edit { stored ->
            val current = stored.toReadState(accountId)
            stored.writeReadState(accountId, current.markAllRead(throughId))
        }
    }

    /**
     * Forgets this account baseline.
     *
     * The next page seeds a fresh baseline from the server count. This does not
     * touch AniList and never sends resetNotificationCount: it only says this
     * device may decide again what it has been shown.
     */
    suspend fun reset(accountId: Int) {
        context.notificationStateDataStore.edit { stored ->
            stored.remove(Keys.anchored(accountId))
            stored.remove(Keys.readThrough(accountId))
            stored.remove(Keys.readIds(accountId))
            // This fence is what lets a live inbox tell a real reset apart from
            // a DataStore emission that was queued before the reader clicked.
            stored[Keys.resetGeneration(accountId)] =
                (stored[Keys.resetGeneration(accountId)] ?: 0) + 1
        }
    }

    private fun Preferences.toReadState(accountId: Int): NotificationReadState =
        NotificationReadState(
            anchored = this[Keys.anchored(accountId)] ?: false,
            readThroughId = this[Keys.readThrough(accountId)],
            readIds = this[Keys.readIds(accountId)].orEmpty().mapNotNull(String::toIntOrNull).toSet(),
            resetGeneration = this[Keys.resetGeneration(accountId)] ?: 0
        )

    private fun androidx.datastore.preferences.core.MutablePreferences.writeReadState(
        accountId: Int,
        state: NotificationReadState
    ) {
        this[Keys.anchored(accountId)] = state.anchored
        this[Keys.resetGeneration(accountId)] = state.resetGeneration
        state.readThroughId?.let { this[Keys.readThrough(accountId)] = it }
            ?: remove(Keys.readThrough(accountId))
        if (state.readIds.isEmpty()) {
            remove(Keys.readIds(accountId))
        } else {
            this[Keys.readIds(accountId)] = state.readIds.map(Int::toString).toSet()
        }
    }
}
