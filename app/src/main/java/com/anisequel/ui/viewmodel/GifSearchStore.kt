package com.anisequel.ui.viewmodel

import com.anisequel.data.network.GifPage
import com.anisequel.data.network.GifResult
import com.anisequel.data.network.GifSearchApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The GIF picker's state: a query, the results it produced, and the next page.
 *
 * ## Why a search has a debounce and a scope
 *
 * A GIF index charges for patience. Typing "naruto" on a phone keyboard is nine
 * keystrokes, and asking for results after every one of them is nine requests and
 * eight sets of tiles that get replaced before they finished loading - and both
 * Safebooru rate-limits anonymous callers and a Tenor key has its own quota, so the
 * ninth request of a burst is the one that comes back empty. One search per pause
 * is what a picker looks like from the inside: the tiles settle, and the budget
 * goes to searches the reader actually meant.
 *
 * [DEBOUNCE_MILLIS] is 450 rather than the 300 a text field usually uses because
 * this field is searched by thumb on glass, where the gap between two keystrokes is
 * longer than on a keyboard.
 *
 * ## Why it is not a ViewModel
 *
 * The picker's whole life is one open composer, and its state has to survive
 * nothing that the composer's own composition does not survive. [scope] is handed
 * in rather than owned so the search dies with the sheet that opened it.
 */
class GifSearchStore(
    private val scope: CoroutineScope,
    private val provider: GifSearchApi
) {

    /**
     * What the browser draws.
     *
     * [note] is the one-sentence line for the states that are not results and not
     * errors: an empty query, a search that legitimately found nothing, a provider
     * whose index does not carry the kind of thing being asked for. A grid that
     * simply goes blank reads as a broken app, and the sentence is what makes it
     * the index's answer rather than the app's failure.
     */
    data class State(
        val query: String = "",
        val results: List<GifResult> = emptyList(),
        val next: String? = null,
        val isSearching: Boolean = false,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
        val note: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Which index the panel is searching, for its hints and placeholder. */
    val providerName: String get() = provider.providerName

    /**
     * Why that index is not the one the reader chose, or null when it is.
     *
     * Set when the chosen provider needs a key that is not saved and the anonymous
     * booru is answering instead - a substitution the placeholder already reveals in
     * one word and the reader deserves a sentence about.
     */
    val substitutionNote: String? get() = provider.substitutionNote

    private var searchJob: Job? = null
    private var pageJob: Job? = null

    /**
     * First pages already fetched, by normalised query, least recently used first.
     *
     * Backspacing "crying" to "cryin" and typing the "g" again sent the same request
     * twice, and so did every tap on a suggestion chip the reader had already
     * tried - each one a round trip and a skeleton over a grid that was on screen a
     * second ago. A cached query is answered at once, without the debounce, because
     * there is no request left to coalesce.
     */
    private val firstPages = object : LinkedHashMap<String, GifPage>(MAX_CACHED_QUERIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GifPage>?): Boolean =
            size > MAX_CACHED_QUERIES
    }

    /**
     * The GIFs this sheet has already put into a draft, newest first.
     *
     * An empty search box shows them, which is the picker's answer to the most
     * common second visit: the reader is back for the same reaction they used a
     * minute ago. It is session-scoped and says so in the panel rather than
     * pretending to be a library - a durable history of images somebody pasted into
     * a public comment is a thing to think about before shipping, not a side effect
     * of a picker.
     *
     * A [GifResult] is kept rather than its URL, because the strip draws the tile and
     * the tile is only safe when it is the provider's small copy: an animated
     * drawable is decoded at its **intrinsic** frame size however it is displayed,
     * and [com.anisequel.data.network.SafebooruGifs] rejects a full-size animation
     * for exactly that reason. Keeping the embed URL alone threw the tile's whole
     * budget away and put up to twelve full-resolution animations - the catalogue
     * runs to 5200x5200 - in one scrolling grid, which is an OutOfMemoryError rather
     * than a preview. The dimensions come back with the record so the guard still
     * applies.
     *
     * Its own flow rather than a field of [State], because every `State(...)` in this
     * class is a fresh search clearing the grid, and the strip survives one.
     */
    private val _recent = MutableStateFlow<List<GifResult>>(emptyList())
    val recent: StateFlow<List<GifResult>> = _recent.asStateFlow()

    /** Records a pick: newest first, one copy of an embed, capped at [MAX_USED]. */
    fun rememberUsed(gif: GifResult) {
        _recent.value = (
            listOf(gif) + _recent.value.filterNot { it.embedUrl == gif.embedUrl }
            ).take(MAX_USED)
    }

    /** The recent picks, in the order the grid should show them. */
    fun usedThisSession(): List<GifResult> = _recent.value

    /** Rewrites the query and schedules the search it deserves. */
    fun setQuery(query: String) {
        val current = _state.value
        if (query == current.query) return
        // Both jobs, not just the search: a page from the *previous* query that
        // is still in flight would otherwise land later and clear the flag it
        // set, and a state left with `isLoadingMore = true` blocks every future
        // page of the new search behind a spinner that never stops.
        searchJob?.cancel()
        pageJob?.cancel()
        _state.value = current.copy(
            query = query,
            error = null,
            isLoadingMore = false,
            next = null
        )
        if (query.isBlank()) {
            // Clearing the field clears the grid with it. Leaving another search's
            // results under an empty field is how a reader taps a GIF they did not
            // ask for.
            _state.value = State(note = BLANK_QUERY_NOTE)
            return
        }
        val cached = synchronized(firstPages) { firstPages.containsKey(cacheKey(query)) }
        searchJob = scope.launch {
            if (!cached) delay(DEBOUNCE_MILLIS)
            runFirstSearch(query)
        }
    }

    /** Runs the search now - the keyboard's Enter, and the retry after a failure. */
    fun searchNow() {
        val query = _state.value.query
        if (query.isBlank()) return
        searchJob?.cancel()
        pageJob?.cancel()
        _state.value = _state.value.copy(isLoadingMore = false)
        searchJob = scope.launch { runFirstSearch(query) }
    }

    private suspend fun runFirstSearch(query: String) {
        val key = cacheKey(query)
        val hit = synchronized(firstPages) { firstPages[key] }
        if (hit == null) _state.value = _state.value.copy(isSearching = true, error = null, note = null)
        val result = if (hit != null) {
            Result.success(hit)
        } else {
            repositoryResult { provider.search(query, cursor = null) }
                .onSuccess { page -> synchronized(firstPages) { firstPages[key] = page } }
        }
        val current = _state.value
        if (current.query != query) {
            // The field moved on, so this answer belongs to nothing - but the
            // "searching" flag was set *for* this query, and a newer search only
            // raises it again behind its own debounce. Returning without lowering it
            // left the skeleton shimmering over a grid that would never arrive.
            _state.value = current.copy(isSearching = false)
            return
        }
        result.fold(
            onSuccess = { page ->
                _state.value = current.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    results = page.results,
                    next = page.next,
                    error = null,
                    note = if (page.results.isEmpty()) emptyNote(query) else null
                )
            },
            onFailure = { error ->
                _state.value = current.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    results = emptyList(),
                    next = null,
                    error = error.message ?: "The GIF search did not answer."
                )
            }
        )
    }

    /**
     * Asks for the page after the ones already on screen.
     *
     * Refused while a search is in flight, and while there is no cursor to ask
     * with: a grid that keeps firing requests at its own end is the difference
     * between a picker and a rate-limit notice.
     */
    fun loadMore() {
        val current = _state.value
        val cursor = current.next ?: return
        if (current.isLoadingMore || current.isSearching || current.query.isBlank()) return
        pageJob?.cancel()
        pageJob = scope.launch {
            _state.value = _state.value.copy(isLoadingMore = true, error = null)
            val result = repositoryResult { provider.search(current.query, cursor) }
            val latest = _state.value
            if (latest.query != current.query) {
                // The same race as the first search, with the same reason: the
                // "loading more" flag belongs to the request that just gave up, and
                // nothing else is going to lower it.
                _state.value = latest.copy(isLoadingMore = false)
                return@launch
            }
            result.fold(
                onSuccess = { page ->
                    val seen = latest.results.mapTo(HashSet()) { it.id }
                    _state.value = latest.copy(
                        isLoadingMore = false,
                        results = latest.results + page.results.filter { it.id !in seen },
                        next = page.next
                    )
                },
                onFailure = { error ->
                    _state.value = latest.copy(
                        isLoadingMore = false,
                        error = error.message ?: "The next page did not arrive."
                    )
                }
            )
        }
    }

    /** Drops everything, for a picker that is being closed or re-provisioned. */
    fun reset() {
        searchJob?.cancel()
        pageJob?.cancel()
        _state.value = State(note = BLANK_QUERY_NOTE)
    }

    /**
     * The sentence for a search that came back empty.
     *
     * The provider writes it because the useful advice depends on the index: a booru
     * needs "name one tag", nekos.best needs "try a reaction", and a keyed general
     * index needs neither. Building the booru sentence here gave every narrow index
     * the booru's explanation of a failure it does not have.
     */
    private fun emptyNote(query: String): String = provider.emptyHint(query)

    companion object {
        /** How long a pause in the field has to be before it counts as a search. */
        const val DEBOUNCE_MILLIS = 450L

        /** A sheet's worth of searches; each entry is one page of URLs, not images. */
        const val MAX_CACHED_QUERIES = 16

        /** "Holo ", "holo" and "HOLO" are one search on both providers. */
        internal fun cacheKey(query: String): String =
            query.trim().lowercase().replace(Regex("\\s+"), " ")

        const val BLANK_QUERY_NOTE = "Type to search for a GIF."

        /** How many recent picks the empty search shows. */
        const val MAX_USED = 12
    }
}
