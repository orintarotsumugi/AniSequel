package com.anisequel.data.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.domain.notifications.NotificationAlertPreferences
import com.anisequel.domain.notifications.NotificationDeliveryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationAlertDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_notification_alerts"
)

/**
 * The background-alert switches and the delivery cursor.
 *
 * Two kinds of state in one file, and they are separate on purpose:
 *
 *  - the switches are a device preference, like the inbox category switches;
 *  - the cursor is per AniList account, because a DataStore outlives sign-out.
 *    A shared cursor would make the next account start with the previous
 *    person's newest notification already delivered - or, worse, make their
 *    history look new if their ids happened to be higher.
 *
 * The delivery cursor is *not* [com.anisequel.data.repository.NotificationReadStore].
 * Reading an inbox card means the reader has seen it; delivery means Android
 * actually posted an alert. Mixing them would either silence an alert because
 * the card was opened on another device, or mark a card read because a phone
 * was woken while the reader was asleep.
 */
class NotificationAlertStore(private val context: Context) : NotificationCursorStore {

    private object Keys {
        val ENABLED = booleanPreferencesKey("background_enabled")
        val AIRING = booleanPreferencesKey("background_airing")
        val LIKES = booleanPreferencesKey("background_likes")
        val REPLIES = booleanPreferencesKey("background_replies")
        val MENTIONS = booleanPreferencesKey("background_mentions")
        val MESSAGES = booleanPreferencesKey("background_messages")
        val FOLLOWS = booleanPreferencesKey("background_follows")
        val MEDIA_UPDATES = booleanPreferencesKey("background_media_updates")
        val SUBMISSIONS = booleanPreferencesKey("background_submissions")
        val INTERVAL = intPreferencesKey("background_interval_minutes")

        /**
         * The account the app last saw signed in.
         *
         * Written by the navigation graph when the dashboard resolves a viewer,
         * read by the worker so a periodic check costs one notification page
         * instead of a viewer request as well. It is not an authorisation: the
         * worker still needs a token, and the cursor is keyed by this id.
         */
        val ACTIVE_ACCOUNT = intPreferencesKey("active_account_id")

        fun baseline(accountId: Int) = booleanPreferencesKey("delivery_baseline_$accountId")
        fun cursor(accountId: Int) = intPreferencesKey("delivery_cursor_$accountId")
        fun deliveredIds(accountId: Int) = stringSetPreferencesKey("delivery_ids_$accountId")
        fun checkedAt(accountId: Int) = longPreferencesKey("delivery_checked_$accountId")
        fun alertCount(accountId: Int) = intPreferencesKey("delivery_alerts_$accountId")
        fun resumePage(accountId: Int) = intPreferencesKey("delivery_resume_page_$accountId")
    }

    val preferences: Flow<NotificationAlertPreferences> =
        context.notificationAlertDataStore.data.map { stored ->
            NotificationAlertPreferences(
                enabled = stored[Keys.ENABLED] ?: false,
                airing = stored[Keys.AIRING] ?: true,
                likes = stored[Keys.LIKES] ?: true,
                replies = stored[Keys.REPLIES] ?: true,
                mentions = stored[Keys.MENTIONS] ?: true,
                messages = stored[Keys.MESSAGES] ?: true,
                follows = stored[Keys.FOLLOWS] ?: true,
                mediaUpdates = stored[Keys.MEDIA_UPDATES] ?: true,
                submissions = stored[Keys.SUBMISSIONS] ?: true,
                intervalMinutes = NotificationAlertPreferences.clampInterval(
                    stored[Keys.INTERVAL] ?: NotificationAlertPreferences.DEFAULT_INTERVAL_MINUTES
                )
            )
        }

    suspend fun loadPreferences(): NotificationAlertPreferences = preferences.first()

    suspend fun setEnabled(enabled: Boolean) = write(Keys.ENABLED, enabled)

    suspend fun setAiring(enabled: Boolean) = write(Keys.AIRING, enabled)

    suspend fun setLikes(enabled: Boolean) = write(Keys.LIKES, enabled)

    suspend fun setReplies(enabled: Boolean) = write(Keys.REPLIES, enabled)

    suspend fun setMentions(enabled: Boolean) = write(Keys.MENTIONS, enabled)

    suspend fun setMessages(enabled: Boolean) = write(Keys.MESSAGES, enabled)

    suspend fun setFollows(enabled: Boolean) = write(Keys.FOLLOWS, enabled)

    suspend fun setMediaUpdates(enabled: Boolean) = write(Keys.MEDIA_UPDATES, enabled)

    suspend fun setSubmissions(enabled: Boolean) = write(Keys.SUBMISSIONS, enabled)

    suspend fun setIntervalMinutes(minutes: Int) {
        context.notificationAlertDataStore.edit { stored ->
            stored[Keys.INTERVAL] = NotificationAlertPreferences.clampInterval(minutes)
        }
    }

    suspend fun activeAccountId(): Int? =
        context.notificationAlertDataStore.data.first()[Keys.ACTIVE_ACCOUNT]

    suspend fun recordActiveAccount(accountId: Int) {
        context.notificationAlertDataStore.edit { it[Keys.ACTIVE_ACCOUNT] = accountId }
    }

    /**
     * Forgets the last signed-in account on sign-out.
     *
     * The cursor is keyed by id, so leaving this behind does not mix two
     * accounts' history - but a worker that runs before the next sign-in could
     * read it as the account its token belongs to. Clearing it makes that check
     * fall back to the viewer lookup the worker already knows how to do.
     */
    suspend fun clearActiveAccount() {
        context.notificationAlertDataStore.edit { it.remove(Keys.ACTIVE_ACCOUNT) }
    }

    fun deliveryState(accountId: Int): Flow<NotificationDeliveryState> =
        context.notificationAlertDataStore.data.map { stored -> stored.toDeliveryState(accountId) }

    override suspend fun load(accountId: Int): NotificationDeliveryState =
        context.notificationAlertDataStore.data.first().toDeliveryState(accountId)

    override suspend fun save(accountId: Int, state: NotificationDeliveryState) {
        context.notificationAlertDataStore.edit { stored ->
            stored[Keys.baseline(accountId)] = state.baselineEstablished
            stored[Keys.cursor(accountId)] = state.deliveredThroughId ?: 0
            stored[Keys.checkedAt(accountId)] = state.lastCheckedAtMillis ?: 0L
            stored[Keys.alertCount(accountId)] = state.lastAlertCount
            stored[Keys.resumePage(accountId)] = state.resumePage.coerceAtLeast(1)
            if (state.deliveredIds.isEmpty()) {
                stored.remove(Keys.deliveredIds(accountId))
            } else {
                stored[Keys.deliveredIds(accountId)] =
                    state.deliveredIds.map(Int::toString).toSet()
            }
        }
    }

    private suspend fun write(key: Preferences.Key<Boolean>, value: Boolean) {
        context.notificationAlertDataStore.edit { it[key] = value }
    }

    private fun Preferences.toDeliveryState(accountId: Int): NotificationDeliveryState =
        NotificationDeliveryState(
            baselineEstablished = this[Keys.baseline(accountId)] ?: false,
            deliveredThroughId = this[Keys.cursor(accountId)],
            deliveredIds = this[Keys.deliveredIds(accountId)]
                .orEmpty()
                .mapNotNull(String::toIntOrNull)
                .toSet(),
            lastCheckedAtMillis = this[Keys.checkedAt(accountId)]?.takeIf { it > 0L },
            lastAlertCount = this[Keys.alertCount(accountId)] ?: 0,
            resumePage = (this[Keys.resumePage(accountId)] ?: 1).coerceAtLeast(1)
        )
}
