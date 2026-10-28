package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.domain.tier.StandardTiers
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierListDocument
import com.anisequel.domain.tier.TierMediaType
import com.anisequel.domain.tier.decodeDocumentIds
import com.anisequel.domain.tier.decodeTierListDocument
import com.anisequel.domain.tier.encodeDocumentIds
import com.anisequel.domain.tier.encodeTierListDocument
import com.anisequel.domain.tier.legacyTierListDocument
import com.anisequel.domain.tier.mostRecentTierList
import com.anisequel.domain.tier.newTierListId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tierListDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_tier_list_prefs"
)

/**
 * The person's tier lists: several of them, each written on its own.
 *
 * ## Why this is a library and not one list
 *
 * It used to be one list in one pair of keys, and the trophy opened the board
 * directly. That made "start over" and "keep what I have" the same button, so
 * nobody could keep a favourites-only ranking and a full-library one at the same
 * time - and the only entry point into the feature was an empty board with two
 * buttons on it. A list is now a *document* with a name and an id, the store holds
 * as many as the person makes, and the screen that opens is the one that shows
 * them.
 *
 * ## Why an index of ids rather than the store's own keys
 *
 * `Preferences` is an unordered map, and enumerating its keys would hand the
 * library screen whatever order the file happened to be written in - which changes
 * when a key is added or removed. An explicit list is also what makes an
 * interrupted delete harmless: a document left behind under a key that is no
 * longer in the index is invisible rather than a nameless list on the screen.
 *
 * ## Why a document is read by id, not from its own contents
 *
 * The id is the key. It is not duplicated inside the value, because a value that
 * can disagree with its own key is a document that cannot be opened rather than one
 * that is merely misnamed.
 *
 * ## The whole library is decoded on every read
 *
 * Deliberate. The alternative is a per-document cache that has to be kept honest
 * against the disk on every write, and a cache that disagrees with storage shows a
 * tier list that is not the one stored - which is the failure this feature is least
 * able to afford. A handful of lists of a few hundred short strings is
 * microseconds; a stale cache is a lost afternoon.
 */
class TierListPreferences(private val context: Context) {

    private object Keys {
        /** The ids of every saved list, in the order they were created. */
        val INDEX = stringPreferencesKey("library_ids_v1")

        /**
         * The list the board was last showing.
         *
         * Separate from the index on purpose: it changes when a list is *opened*,
         * which is a much better answer to "carry on where you left off" than the
         * most recently edited one - a list can be edited by a seeding pass the
         * person barely watched.
         */
        val LAST_OPENED = stringPreferencesKey("last_opened_v1")

        // There used to be a `seeded_for_v1` key here, recording which AniList
        // account had been offered "Rank from AniList scores" so the offer could
        // be made once per account. Nothing ever read it back: the offer ended up
        // on the empty state of every list, which is the better behaviour now that
        // lists are documents - a new list *is* a new offer, with no flag to keep
        // in step. The write stayed behind as a string that looked load-bearing and
        // had to be reasoned about by every reader. It is gone rather than wired
        // up, because "already seeded" is not a question the library has.
        //
        // The key itself is not deleted from anyone's stored preferences, and does
        // not need to be: DataStore is a map and an unread key is inert.

        /**
         * The single list this store held before lists became documents.
         *
         * Read once by [migrateLegacyList] and then left alone. Deleting them would
         * make the migration one-way with no way back if this format ever has to be
         * read again - and a few hundred bytes of dead keys is cheaper than an
         * unrecoverable load.
         */
        val LEGACY_TIERS = stringPreferencesKey("tiers_v1")
        val LEGACY_ASSIGNMENT = stringPreferencesKey("assignment_v1")

        /** One list's own key. Ids are generated, so they are key-safe. */
        fun document(id: String) = stringPreferencesKey("list_${id}_v1")
    }

    /** Every saved list, plus which one was last opened. */
    val library: Flow<StoredTierLibrary> = context.tierListDataStore.data.map(::readLibrary)

    private fun readLibrary(prefs: Preferences): StoredTierLibrary {
        val ids = decodeDocumentIds(prefs[Keys.INDEX])
        val documents = ids.mapNotNull { id ->
            decodeTierListDocument(id, prefs[Keys.document(id)])
        }
        return StoredTierLibrary(
            documents = documents,
            // An id that is not in the index is stale - the list was deleted - and
            // offering to continue it would open nothing.
            lastOpenedId = prefs[Keys.LAST_OPENED]?.takeIf { opened -> opened in ids }
        )
    }

    /**
     * Applies [change] to one list and returns the list **as it was before** it was
     * applied, or null when there is no such list.
     *
     * ## One write, and the previous value out of the same transaction
     *
     * The whole document is written in a single `edit`, so a tier removal and the
     * placements it drops are one change rather than two - which is what used to
     * leave a list whose placements named a row that no longer existed, for as long
     * as it took the second write to land, and for ever if the process died in
     * between.
     *
     * Returning the *previous* document is what makes undo exact. A snapshot taken
     * by the caller can be one write behind (two quick drags and the second
     * snapshot is the state before the first), and undo would then jump back past
     * work the person wants to keep. Read inside the transaction, the snapshot is
     * by construction the state this change replaced.
     *
     * `updatedAt` is stamped here rather than by the caller: "when was this last
     * edited" is a property of the write, and a caller that forgot to set it would
     * sort the list it just edited to the bottom of the library.
     */
    suspend fun mutate(
        listId: String,
        change: (TierListDocument) -> TierListDocument
    ): TierListMutation? {
        var result: TierListMutation? = null
        context.tierListDataStore.edit { prefs ->
            val current = decodeTierListDocument(listId, prefs[Keys.document(listId)])
                ?: return@edit
            val changed = change(current)
            if (changed == current) {
                // A drag that put a title back where it already was, or a removal
                // the rules refused. Writing anyway would bump `updatedAt` and
                // reorder the library by "recently edited" for an edit that did not
                // happen, so a no-op is reported as one and nothing is written.
                result = TierListMutation(before = current, after = current)
                return@edit
            }
            val updated = changed.copy(
                id = listId,
                updatedAt = System.currentTimeMillis()
            )
            prefs[Keys.document(listId)] = encodeTierListDocument(updated)
            // Editing a list is evidence it is the one being worked in, so this
            // also keeps "carry on" pointing at it even if the open was not
            // recorded - a write that only ever helps the next launch.
            prefs[Keys.LAST_OPENED] = listId
            result = TierListMutation(before = current, after = updated)
        }
        return result
    }

    /**
     * Puts [document] back exactly as it was, for undo.
     *
     * Separate from [mutate] because undo is not a change *to* the current state,
     * it is a replacement of it: the snapshot already holds the answer, and running
     * it through the change function would apply the edit that is being undone a
     * second time. The list has to still exist for this to mean anything - undoing a
     * move on a list that has since been deleted would resurrect the list's contents
     * under an id nothing indexes.
     */
    suspend fun restore(document: TierListDocument) {
        context.tierListDataStore.edit { prefs ->
            val ids = decodeDocumentIds(prefs[Keys.INDEX])
            if (ids.none { it == document.id }) return@edit
            prefs[Keys.document(document.id)] = encodeTierListDocument(
                document.copy(updatedAt = System.currentTimeMillis())
            )
            prefs[Keys.LAST_OPENED] = document.id
        }
    }

    /**
     * Makes a new, empty list and returns its id.
     *
     * Empty means empty placements with the reader's *default* tiers, which is the
     * other half of the feature: seeding asserts a judgement nobody made, and
     * somebody who wants to drag thirty titles by hand should not have to delete a
     * hundred opinions first.
     */
    suspend fun createEmpty(
        name: String,
        tiers: List<TierDefinition> = StandardTiers,
        mediaType: TierMediaType = TierMediaType.ANIME
    ): String {
        var created = ""
        context.tierListDataStore.edit { prefs ->
            val ids = decodeDocumentIds(prefs[Keys.INDEX])
            val id = newTierListId(ids)
            val document = TierListDocument(
                id = id,
                name = name,
                mediaType = mediaType,
                tiers = tiers,
                updatedAt = System.currentTimeMillis()
            )
            prefs[Keys.document(id)] = encodeTierListDocument(document)
            prefs[Keys.INDEX] = encodeDocumentIds(ids + id)
            prefs[Keys.LAST_OPENED] = id
            created = id
        }
        return created
    }

    /** Records which list the board is showing, without touching its contents. */
    suspend fun setLastOpened(listId: String) {
        context.tierListDataStore.edit { it[Keys.LAST_OPENED] = listId }
    }

    /**
     * Deletes a list and everything in it.
     *
     * The document key is removed as well as the index entry. Leaving it behind
     * would look harmless - nothing reads a key that is not indexed - but the ids
     * are sequential, so the next list created could take the removed one's id and
     * inherit its contents. That is a deleted list coming back from the dead, and
     * it would look like the app restoring work rather than resurrecting it.
     */
    suspend fun delete(listId: String) {
        context.tierListDataStore.edit { prefs ->
            val ids = decodeDocumentIds(prefs[Keys.INDEX])
            if (ids.none { it == listId }) return@edit
            prefs.remove(Keys.document(listId))
            prefs[Keys.INDEX] = encodeDocumentIds(ids.filterNot { it == listId })
            if (prefs[Keys.LAST_OPENED] == listId) prefs.remove(Keys.LAST_OPENED)
        }
    }

    /**
     * Brings the pre-library list into the library, once.
     *
     * Called on the way into the screen rather than from the flow, because a `map`
     * over the data is a *read* and a write hidden inside one is both surprising and
     * illegal-looking; and called every time rather than guarded by a flag, because
     * the guard is the index itself: once it exists, there is nothing to migrate.
     *
     * The old keys are read but not cleared. A list that was ranked over months is
     * the one piece of state in this app that cannot be regenerated, and the
     * migration runs exactly once over data that only exists on installs that
     * upgraded - so leaving the source of it in place costs almost nothing and
     * means a bug here is recoverable by reading it again.
     */
    suspend fun migrateLegacyList() {
        context.tierListDataStore.edit { prefs ->
            // Once the index exists there is nothing to move, and it is never
            // absent again: every list that is created writes it.
            if (prefs[Keys.INDEX] != null) return@edit
            val document = legacyTierListDocument(
                tiers = prefs[Keys.LEGACY_TIERS],
                assignment = prefs[Keys.LEGACY_ASSIGNMENT]
            ) ?: return@edit

            prefs[Keys.document(document.id)] = encodeTierListDocument(
                document.copy(updatedAt = System.currentTimeMillis())
            )
            prefs[Keys.INDEX] = encodeDocumentIds(listOf(document.id))
        }
    }
}

/**
 * One write's before and after.
 *
 * The save is handed both so the caller can (a) push [before] for undo and (b)
 * tell a change from a no-op without re-running the change function outside the
 * transaction it belongs to.
 */
data class TierListMutation(
    val before: TierListDocument,
    val after: TierListDocument
)

/** Everything the tier list screen needs to draw the library, as one value. */
data class StoredTierLibrary(
    val documents: List<TierListDocument> = emptyList(),
    /** The list the board was last showing, or null when it was never recorded. */
    val lastOpenedId: String? = null
) {
    /** The list with [id], or null when there is no such list (yet). */
    fun document(id: String?): TierListDocument? =
        id?.let { wanted -> documents.firstOrNull { it.id == wanted } }

    /** What the library offers as "carry on where you left off". */
    val recent: TierListDocument? get() = mostRecentTierList(documents, lastOpenedId)
}
