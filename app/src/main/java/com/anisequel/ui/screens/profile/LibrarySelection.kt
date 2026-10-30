package com.anisequel.ui.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.repository.LibraryPreferences
import com.anisequel.data.repository.LibraryViewStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The Library's remembered view: what the reader last chose, plus the store its next
 * choice is written to.
 *
 * [answered] false means DataStore has not answered yet - the tab draws a spinner for
 * that one window rather than a grid in a view the reader did not pick. It is not the
 * same as "nothing stored": an account that has never chosen sits at [answered] with
 * every field null, which the tab reads as its defaults.
 *
 * ## Why the answer is resolved here and not inside the tab
 *
 * The four values used to be collected inside `ProfileLibraryTab`, each starting from
 * `null` because a `collectAsStateWithLifecycle` initial value is read once per
 * composition - and the profile screen switches tabs through an `AnimatedContent`,
 * which **disposes** the tab it animates away from. So every visit to the Library
 * re-asked DataStore, and until the answer came back the tab drew its "resolving"
 * spinner. Leaving the Library and coming back looked exactly like a fresh load: the
 * same spinner, on a preference that had not changed, every single time. For an account
 * that had never chosen a type it was worse than a flash - a flow that has an answer
 * and a flow that has none both emit `null` there, so the spinner never resolved at all.
 *
 * Resolved one level up - in `AppNavigation`, which outlives the page - so the question
 * is asked once per process rather than once per visit. The grid still *receives* the
 * values as state rather than reading flows, which is what lets its own state
 * initializers - the `rememberSaveable`s recreated on each re-entry - start on the
 * remembered view instead of on the defaults for one frame.
 *
 * [preferences] travels with the values because the tab must be able to *write* a choice
 * before the first answer arrives; the controls are tappable in that window, and a write
 * would otherwise have nothing to go to.
 */
class RememberedLibraryView(
    /**
     * Where the tab's next choice is written.
     *
     * The reader's own [LibraryPreferences] on their Library page; see
     * [borrowedLibraryView] for the store that forgets, which is what a *stranger's*
     * list has to be given.
     */
    val preferences: LibraryViewStore,
    /** Whether DataStore has answered at least once. False is the only resolving state. */
    val answered: Boolean,
    /** The remembered media type, or null when none is stored or the name is unknown. */
    val type: LibraryType?,
    val filter: LibraryFilter?,
    val sort: LibrarySort?,
    /**
     * The remembered sort direction, or null when none is stored.
     *
     * Null is a real state rather than a missing default: a stored sort without a
     * direction cannot come from this app (the pair is written in one edit).
     */
    val sortAscending: Boolean?
)

/**
 * The remembered view as one flow: the first emission is the stored snapshot, and it
 * arrives even when nothing is stored - which is what lets `answered` tell "not answered
 * yet" apart from "never chosen". The four values move together because they are one
 * DataStore file and one view of the tab; the half-restored selection the old four
 * independent collections could draw does not exist here.
 */
internal fun rememberedSelectionFlow(preferences: LibraryPreferences): Flow<RememberedLibraryView> =
    combine(
        preferences.lastType,
        preferences.lastFilter,
        preferences.lastSort,
        preferences.lastSortAscending
    ) { type, filter, sort, ascending ->
        RememberedLibraryView(
            preferences = preferences,
            answered = true,
            type = storedEntry(type, LibraryType.entries),
            filter = storedEntry(filter, LibraryFilter.entries),
            sort = storedEntry(sort, LibrarySort.entries),
            sortAscending = ascending
        )
    }

/**
 * The refreshed [RememberedLibraryView] for the caller's composition.
 *
 * `remember`ed on the store so a recomposition does not resubscribe to DataStore; the
 * holder is rebuilt only when a value actually changes.
 */
@Composable
fun rememberLibrarySelection(preferences: LibraryPreferences): RememberedLibraryView {
    val remembered by remember(preferences) { rememberedSelectionFlow(preferences) }
        .collectAsStateWithLifecycle(initialValue = null)

    return remembered ?: RememberedLibraryView(
        preferences = preferences,
        answered = false,
        type = null,
        filter = null,
        sort = null,
        sortAscending = null
    )
}

/**
 * The entry named [stored], or null when it is absent or this build no longer has it.
 *
 * An unknown name resolving to null means the tab shows its default rather than an
 * empty list - the recoverable outcome for a preference written by a build that knew an
 * enum entry this one does not.
 */
private fun <T : Enum<T>> storedEntry(stored: String?, entries: List<T>): T? =
    stored?.let { name -> entries.firstOrNull { it.name == name } }

/**
 * A borrowed list's view: local defaults, written nowhere.
 *
 * [ProfileLibraryTab] is the browsing surface for two different owners now, and only
 * one of them owns the preferences. Reusing the viewer's saved view for a follower's
 * list would read the viewer's own choices onto a stranger's grid - a Manga-only,
 * by-score view they never chose for *this* person - and, worse, every chip they tap
 * there would write into the store the viewer's own Library page opens from, so
 * leaving somebody's profile would silently re-arrange the reader's own page.
 *
 * [answered] is `true` and it is not decoration: `false` is the tab's "DataStore has
 * not answered yet" spinner, and this store is never going to answer. The defaults
 * below are the answer.
 */
fun borrowedLibraryView(): RememberedLibraryView = RememberedLibraryView(
    preferences = ForgetfulLibraryViewStore,
    answered = true,
    // Anime, not All: the borrowed half this tab can draw on the first frame is the
    // list the profile already fetched. Their manga is a second, separate download -
    // so `All` would draw one type's entries under the other type's label until the
    // reader tapped the chip that asks.
    type = LibraryType.ANIME,
    filter = LibraryFilter.ALL,
    sort = LibrarySort.LIST_ORDER,
    sortAscending = true
)

/**
 * A store that forgets every choice.
 *
 * The tab's chips still work - the grid re-sorts immediately - they just do not
 * outlive the page, which is the whole difference between the reader's own library
 * and somebody else's.
 */
private object ForgetfulLibraryViewStore : LibraryViewStore {
    override suspend fun setType(value: String) = Unit
    override suspend fun setFilter(value: String) = Unit
    override suspend fun setSort(name: String, ascending: Boolean) = Unit
}
