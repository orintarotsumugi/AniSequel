package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.data.model.MessageNode
import com.anisequel.data.model.MessageSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.messageVisibilityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_message_visibility"
)

/**
 * What the reader has taken out of the inbox, and what they have not read yet.
 *
 * Both halves are about *visibility*, not about the message: AniList has no
 * "delete this row from my inbox" for a message you received, and a row this app
 * removed server-side would be a message that stopped existing for the person who
 * sent it too. So hiding is the reader's own view of their own list, which is why
 * it is a preference and not a mutation.
 *
 * A message id is stored under the side it was hidden from, so hiding a row in
 * **Sent** cannot also make the same activity vanish from **Received**. The side is
 * decided from the row itself ([MessageNode.isSentBy]), not from whichever chip was
 * selected, because under `All` the two sources are one list and the chip says
 * nothing about the row's direction.
 *
 * [flagNew] is the option that decides whether an unread received row is marked at
 * all. It defaults to on: the marking is the difference between an inbox and a list
 * of receipts. Switching it off must not clear [lastReceivedSeenAt] - they are
 * different decisions, and turning the badge back on should show the same rows it
 * showed before.
 *
 * [lastReceivedSeenAt] is the epoch second the Messages segment was last open on
 * screen. A received message newer than it is new. It is never written from
 * AniList's side: `resetNotificationCount` marks the *site's* notifications read,
 * which changes somebody else's badge to fix this app's list, and the received query
 * deliberately never sends it.
 */
data class MessageVisibility(
    val hiddenReceived: Set<Int> = emptySet(),
    val hiddenSent: Set<Int> = emptySet(),
    val lastReceivedSeenAt: Long = 0L,
    val flagNew: Boolean = true
) {
    /** True when the reader dismissed this row from the side it belongs to. */
    fun isHidden(message: MessageNode, viewerId: Int?): Boolean =
        message.sideOf(viewerId).let { side ->
            if (side == MessageSource.SENT) hiddenSent else hiddenReceived
        }.contains(message.id)

    /** True for a received row the reader has not looked at since it arrived. */
    fun isNew(message: MessageNode, viewerId: Int?): Boolean {
        if (!flagNew) return false
        if (message.sideOf(viewerId) != MessageSource.RECEIVED) return false
        val createdAt = message.createdAt ?: return false
        return createdAt.toLong() > lastReceivedSeenAt
    }

    /** How many rows the page can bring back. */
    val hiddenCount: Int get() = hiddenReceived.size + hiddenSent.size
}

/**
 * The reader's own view of their inbox.
 *
 * A DataStore of its own rather than keys on [SeasonalPreferences] or
 * [MediaPreferences], for the reason both of those give: this is a body of choices
 * that changes together, and a corrupted visibility block must not take a media
 * list down with it.
 */
class MessageVisibilityPreferences(private val context: Context) {

    private object Keys {
        val HIDDEN_RECEIVED = stringSetPreferencesKey("hidden_received")
        val HIDDEN_SENT = stringSetPreferencesKey("hidden_sent")
        val LAST_RECEIVED_SEEN = longPreferencesKey("last_received_seen_at")
        val FLAG_NEW = booleanPreferencesKey("flag_new_messages")
    }

    val visibility: Flow<MessageVisibility> =
        context.messageVisibilityDataStore.data.map { preferences ->
            MessageVisibility(
                hiddenReceived = preferences[Keys.HIDDEN_RECEIVED].orEmpty().toIntIds(),
                hiddenSent = preferences[Keys.HIDDEN_SENT].orEmpty().toIntIds(),
                lastReceivedSeenAt = preferences[Keys.LAST_RECEIVED_SEEN] ?: 0L,
                flagNew = preferences[Keys.FLAG_NEW] ?: true
            )
        }

    /** Takes [messageId] out of [side] of the reader's list. */
    suspend fun hide(side: MessageSource, messageId: Int) {
        val key = when (side) {
            MessageSource.SENT -> Keys.HIDDEN_SENT
            MessageSource.RECEIVED -> Keys.HIDDEN_RECEIVED
        }
        context.messageVisibilityDataStore.edit { preferences ->
            preferences[key] = preferences[key].orEmpty() + messageId.toString()
        }
    }

    /** Puts every dismissed row back, on both sides. */
    suspend fun showAll() {
        context.messageVisibilityDataStore.edit { preferences ->
            preferences.remove(Keys.HIDDEN_RECEIVED)
            preferences.remove(Keys.HIDDEN_SENT)
        }
    }

    /** Records that the reader was looking at the inbox at [epochSecond]. */
    suspend fun markReceivedSeen(epochSecond: Long) {
        context.messageVisibilityDataStore.edit { preferences ->
            preferences[Keys.LAST_RECEIVED_SEEN] = epochSecond
        }
    }

    suspend fun setFlagNew(enabled: Boolean) {
        context.messageVisibilityDataStore.edit { preferences ->
            preferences[Keys.FLAG_NEW] = enabled
        }
    }
}

/**
 * The stored strings back to ids, dropping anything that is not a number.
 *
 * A preference file is a file: a value written by a future build, or edited by
 * something that is not this app, must not take the tab down. The alternative -
 * throwing on one bad entry - hides *every* message behind one corrupt row.
 */
private fun Set<String>.toIntIds(): Set<Int> =
    mapNotNull { it.toIntOrNull() }.toSet()
