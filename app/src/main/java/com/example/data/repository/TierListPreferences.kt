package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.tier.StandardTiers
import com.example.domain.tier.TierAssignment
import com.example.domain.tier.TierDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tierListDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_tier_list_prefs"
)

/**
 * The person's tier list, and the tiers themselves.
 *
 * ## Why the tiers are stored and not hardcoded
 *
 * `StandardTiers` is the *default*, not the answer. Renaming "A" to "Excellent"
 * or inserting a "Favourites" row above "S" is the sort of thing a tier list is
 * for, and an app that cannot do it is a screenshot with extra steps. The score
 * bands stay in `domain/tier` - "what does S mean" must have one answer - while
 * the *labels, order and count* are the reader's.
 *
 * ## Why media ids and not titles
 *
 * Ids survive a rename, a re-release and a re-spelling, which titles do not. A
 * list keyed on titles goes stale the moment AniList edits one, and the entry
 * silently disappears from the list the person built.
 *
 * ## The serialisation is line-based, not JSON
 *
 * `m0:tierA,m1:tierB` per line. It is not a format anyone has to read, but it
 * survives a corrupt or unexpected value far better than one does: a line that
 * does not parse is skipped, rather than the whole list failing to load and
 * taking every drag the person ever made with it.
 */
class TierListPreferences(private val context: Context) {

    private object Keys {
        val TIERS = stringPreferencesKey("tiers_v1")
        val ASSIGNMENT = stringPreferencesKey("assignment_v1")
        val SEEDED_FOR = stringPreferencesKey("seeded_for_v1")
    }

    /** The persisted list, defaulted to [StandardTiers] when never written. */
    val state: Flow<StoredTierList> = context.tierListDataStore.data.map { prefs ->
        StoredTierList(
            tiers = decodeTiers(prefs[Keys.TIERS]),
            assignment = decodeAssignment(prefs[Keys.ASSIGNMENT]),
            seededFor = prefs[Keys.SEEDED_FOR]
        )
    }

    /**
     * Records which profile the current assignment belongs to.
     *
     * A tier list is a personal judgement about one person's library, so opening
     * somebody else's profile must not overwrite it with their titles. The seed
     * is keyed on the signed-in viewer's AniList id.
     */
    suspend fun markSeededFor(userId: Int) {
        context.tierListDataStore.edit { it[Keys.SEEDED_FOR] = userId.toString() }
    }

    suspend fun setAssignment(assignment: TierAssignment) {
        context.tierListDataStore.edit { it[Keys.ASSIGNMENT] = encodeAssignment(assignment) }
    }

    suspend fun setTiers(tiers: List<TierDefinition>) {
        context.tierListDataStore.edit { it[Keys.TIERS] = encodeTiers(tiers) }
    }

    /** Files a media id into a tier, or removes it when [tierId] is null. */
    suspend fun moveTo(mediaId: Int, tierId: String?) {
        context.tierListDataStore.edit { prefs ->
            val current = decodeAssignment(prefs[Keys.ASSIGNMENT]).tierIdByMediaId.toMutableMap()
            if (tierId == null) current.remove(mediaId) else current[mediaId] = tierId
            prefs[Keys.ASSIGNMENT] = encodeAssignment(TierAssignment(current))
        }
    }

    /** Clears every placement but keeps the reader's own tiers. */
    suspend fun clearPlacements() {
        context.tierListDataStore.edit { it[Keys.ASSIGNMENT] = "" }
    }

    internal companion object {
        const val SEPARATOR = ":"
        const val FIELD_SEPARATOR = ","
        const val PAIR_SEPARATOR = ";"

        /**
         * `label|color|id`, comma-separated, ordered as given.
         *
         * Id last so a rename rewrites only the visible part of a line, and the
         * reader's ids survive every future format change.
         */
        fun encodeTiers(tiers: List<TierDefinition>): String =
            tiers.joinToString(FIELD_SEPARATOR) {
                listOf(it.label, it.colorArgb.toString(), it.id).joinToString("|")
            }

        fun decodeTiers(raw: String?): List<TierDefinition> {
            if (raw.isNullOrBlank()) return StandardTiers
            val parsed = raw.split(FIELD_SEPARATOR).mapNotNull { field ->
                val parts = field.split('|')
                if (parts.size != 3) return@mapNotNull null
                val color = parts[1].toLongOrNull() ?: return@mapNotNull null
                val label = parts[0].trim()
                val id = parts[2].trim()
                if (label.isEmpty() || id.isEmpty()) null else TierDefinition(id, label, color)
            }
            // An unreadable file yields the standard tiers rather than an empty
            // screen: a person who has built a list would rather see the default
            // than see nothing and assume their work was deleted.
            return parsed.ifEmpty { StandardTiers }
        }

        fun encodeAssignment(assignment: TierAssignment): String =
            assignment.tierIdByMediaId.entries.joinToString(PAIR_SEPARATOR) {
                "${it.key}${SEPARATOR}${it.value}"
            }

        fun decodeAssignment(raw: String?): TierAssignment {
            if (raw.isNullOrBlank()) return TierAssignment()
            val map = raw.split(PAIR_SEPARATOR).mapNotNull { pair ->
                val parts = pair.split(SEPARATOR)
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val tierId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id to tierId
            }.toMap()
            return TierAssignment(map)
        }
    }
}

/** What [TierListPreferences] holds, as one value so the UI collects a single flow. */
data class StoredTierList(
    val tiers: List<TierDefinition> = StandardTiers,
    val assignment: TierAssignment = TierAssignment(),
    /** The AniList id the current assignment was seeded for, or null if never. */
    val seededFor: String? = null
) {
    val isEmpty: Boolean get() = assignment.isEmpty
}