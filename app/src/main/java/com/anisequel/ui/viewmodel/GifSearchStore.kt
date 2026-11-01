package com.anisequel.ui.viewmodel

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

    private var searchJob: Job? = null
    private var pageJob: Job? = null

    /**
     * The GIFs this sheet has already put into a draft, newest first.
     *
     * An empty search box shows them, which is the picker's answer to the most
     * common second visit: the reader is back for the same reaction they used a
     * minute ago. It is session-scoped and says so in the panel rather than
     * pretending to be a library - a durable history of images somebody pasted into
     * a public comment is a thing to think about before shipping, not a side effect
     * of a picker.
     */
    private val usedUrls = ArrayDeque<String>()

    /** Records a pick and returns the strip's contents, newest first. */
    fun rememberUsed(url: String) {
        usedUrls.remove(url)
        usedUrls.addFirst(url)
        while (usedUrls.size > MAX_USED) usedUrls.removeLast()
    }

    /** The recent picks, in the order the grid should show them. */
    fun usedThisSession(): List<String> = usedUrls.toList()

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
        searchJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
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
        _state.value = _state.value.copy(isSearching = true, error = null, note = null)
        val result = repositoryResult { provider.search(query, cursor = null) }
        val current = _state.value
        if (current.query != query) return
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
            if (latest.query != current.query) return@launch
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

    private fun emptyNote(query: String): String = when {
        !provider.catalogueIsNarrow -> "Nothing found for \"$query\"."
        else -> "No animated GIF tagged \"$query\" there. Boorus match tags rather " +
            "than sentences, so try fewer words, or one name like \"holo\"."
    }

    companion object {
        /** How long a pause in the field has to be before it counts as a search. */
        const val DEBOUNCE_MILLIS = 450L

        const val BLANK_QUERY_NOTE = "Type to search for a GIF."

        /** How many recent picks the empty search shows. */
        const val MAX_USED = 12
    }
}
