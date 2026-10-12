package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.MediaListEntryItem
import com.example.data.repository.AniListRepository
import com.example.data.repository.StoredTierList
import com.example.data.repository.TierListPreferences
import com.example.domain.tier.StandardTiers
import com.example.domain.tier.TierAssignment
import com.example.domain.tier.TierCandidate
import com.example.domain.tier.TierDefinition
import com.example.domain.tier.seedFromScores
import com.example.domain.tier.unseedableCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The tier list: which titles are in which tier, and the tiers themselves.
 *
 * ## Two ways in, because they answer different needs
 *
 * Seeding files everything by AniList score in one tap, which is how a person
 * with 271 titles ever gets a tier list at all. Starting empty is the other
 * half: seeding asserts a judgement the person did not make, and some would
 * rather drag thirty titles than accept a hundred opinions they did not form.
 *
 * Seeding never overwrites a list that already has placements. Somebody who has
 * spent an afternoon ranking things and then pulls to refresh must not lose the
 * afternoon, which is the same rule the repository caches follow.
 *
 * ## Unrated titles are not seeded
 *
 * AniList answers 0 or null for a title nobody has scored, and filing those into
 * the bottom tier asserts something nobody decided - in the same typeface as the
 * judgements the person did make. They are counted and reported instead, so the
 * UI can say "12 titles have no score yet" rather than silently ranking them.
 */
class TierListViewModel(
    private val preferences: TierListPreferences,
    private val aniListRepository: AniListRepository,
    /** Whose list this is the tier list of. Null for a signed-out viewer. */
    private val userId: Int? = null,
    private val isDemo: Boolean = false
) : ViewModel() {

    /**
     * Reads the viewer's own list, then hands it to [setLibrary].
     *
     * The repository caches the list under `user:<id>`, so this is a **cache
     * hit** for anyone who has already opened their profile in this session and
     * costs no request at all. Fetching it again anyway would spend one of
     * AniList's 30-a-minute budget to re-read a list the app is looking at.
     *
     * Loading here rather than being handed the profile screen's entries is not a
     * convenience: this is its own route, so there is no profile screen to hand
     * them over from.
     */
    fun loadLibrary() {
        val id = userId
        if (id == null && !isDemo) return
        if (_candidates.value.isNotEmpty()) return
        viewModelScope.launch {
            val result = if (isDemo) {
                Result.success(aniListRepository.getDemoAnimeList())
            } else {
                aniListRepository.getUserAnimeList(id!!, forceRefresh = false)
            }
            result.onSuccess { collection ->
                setLibrary(
                    collection.lists.orEmpty().flatMap { it.entries.orEmpty() }
                )
            }
        }
    }

    val stored: StateFlow<StoredTierList> = preferences.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StoredTierList())

    /**
     * The assignment with entries in deleted tiers removed.
     *
     * A persisted assignment can name a tier that no longer exists - somebody
     * removed a row and its entries were still filed under it. Dropping those
     * ids here, in one place, keeps the invariant: a title is either in a tier
     * that exists or in none.
     */
    val liveAssignment: StateFlow<TierAssignment> = stored
        .map { list ->
            val live = list.tiers.map { it.id }.toSet()
            TierAssignment(list.assignment.tierIdByMediaId.filterValues { it in live })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TierAssignment())

    /** How many entries are filed under a tier that no longer exists. */
    val orphanedCount: StateFlow<Int> = stored
        .map { list ->
            val live = list.tiers.map { it.id }.toSet()
            list.assignment.tierIdByMediaId.count { it.value !in live }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private val _candidates = MutableStateFlow<List<TierCandidate>>(emptyList())
    val candidates: StateFlow<List<TierCandidate>> = _candidates.asStateFlow()

    private val _isSeeding = MutableStateFlow(false)
    val isSeeding: StateFlow<Boolean> = _isSeeding.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /**
     * Hands the ViewModel the person's library to rank.
     *
     * Called with the entries the profile screen already holds rather than
     * fetching here: re-reading the list would spend one of AniList's
     * 30-a-minute requests to obtain data the app is already looking at.
     */
    fun setLibrary(entries: List<MediaListEntryItem>) {
        _candidates.value = entries.mapNotNull { entry ->
            val media = entry.media
            val title = media.title?.displayTitle?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TierCandidate(
                mediaId = media.id,
                title = title,
                coverUrl = media.coverImage?.bestUrl,
                averageScore = media.averageScore
            )
        }
    }

    /**
     * Files every scored title into a tier.
     *
     * Refuses when a placement already exists, so this cannot be the thing that
     * overwrites work.
     */
    fun seedFromScores(userId: Int?) {
        if (_isSeeding.value) return
        if (!stored.value.isEmpty) return
        val pool = _candidates.value
        if (pool.isEmpty()) return

        _isSeeding.value = true
        viewModelScope.launch {
            val assignment = seedFromScores(pool, stored.value.tiers)
            preferences.setAssignment(assignment)
            if (userId != null) preferences.markSeededFor(userId)

            val skipped = unseedableCount(pool)
            val placed = assignment.tierIdByMediaId.size
            _notice.value = when {
                placed == 0 -> "Nothing to rank yet - none of your titles has a score."
                skipped > 0 -> "Ranked $placed titles. $skipped " +
                    (if (skipped == 1) "has" else "have") +
                    " no score yet and " +
                    (if (skipped == 1) "was" else "were") +
                    " left for you to place."
                else -> "Ranked $placed titles."
            }
            _isSeeding.value = false
        }
    }

    /** Files one title into a tier, or removes it when [tierId] is null. */
    fun moveTo(mediaId: Int, tierId: String?) {
        viewModelScope.launch { preferences.moveTo(mediaId, tierId) }
    }

    /** Empties every tier, keeping the reader's own tiers and colours. */
    fun clearPlacements() {
        viewModelScope.launch { preferences.clearPlacements() }
    }

    /** Renames a tier, keeping its id so nothing filed under it is orphaned. */
    fun renameTier(tierId: String, label: String) {
        viewModelScope.launch {
            val trimmed = label.trim()
            if (trimmed.isEmpty()) return@launch
            preferences.setTiers(
                stored.value.tiers.map { if (it.id == tierId) it.copy(label = trimmed) else it }
            )
        }
    }

    /** Adds a tier below the last one, with an id derived from its position. */
    fun addTier() {
        viewModelScope.launch {
            val current = stored.value.tiers
            val id = "custom_${current.size}_" +
                current.joinToString("") { it.id }.hashCode().toUInt().toString(16)
            preferences.setTiers(current + TierDefinition(id, "New tier", NEW_TIER_COLOR))
        }
    }

    /**
     * Removes a tier, and every entry filed under it.
     *
     * The entries are dropped rather than promoted to the tier above. Promoting
     * would silently claim the person had ranked them there, and an entry nobody
     * placed is a truer state than one placed by a deletion.
     */
    fun removeTier(tierId: String) {
        viewModelScope.launch {
            val current = stored.value.tiers
            // Never to zero: with no rows left there is nothing to drag onto.
            if (current.size <= 1) return@launch
            preferences.setTiers(current.filterNot { it.id == tierId })
            preferences.setAssignment(
                TierAssignment(
                    stored.value.assignment.tierIdByMediaId.filterValues { it != tierId }
                )
            )
        }
    }

    /** Titles in no tier - what a "start empty" list puts on screen. */
    fun unranked(placed: Set<Int>): List<TierCandidate> =
        _candidates.value.filter { it.mediaId !in placed }

    fun dismissNotice() {
        _notice.value = null
    }

    class Factory(
        private val preferences: TierListPreferences,
        private val aniListRepository: AniListRepository,
        private val userId: Int? = null,
        private val isDemo: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TierListViewModel(preferences, aniListRepository, userId, isDemo) as T
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val NEW_TIER_COLOR = 0xFF9E9A99
    }
}