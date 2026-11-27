package com.anisequel.ui.components.detail

import com.anisequel.data.model.MediaEntryEdit
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.repository.AniListRepository

/**
 * The entry editor for a host that is not the dashboard: the Library, the studio
 * page and the profile all open the same sheet this feeds.
 *
 * ## Why this exists at all
 *
 * The sheet used to have two shapes. The dashboard passed an
 * [com.anisequel.ui.components.detail.EntryEditor] and got the "My Entry"
 * destination along with it; the Library, the studio page and the profile
 * passed nothing, so the same show opened with a different navigation bar and
 * one fewer tab depending on which list the reader came from. Editing a score
 * from the dashboard and then opening the same title from their own library
 * showed them a sheet that could not do what the other one could - the same
 * title, two apps.
 *
 * There is nothing screen-specific to pass, which is why this is one class
 * rather than three: the entry on the tab is always the **viewer's own** for
 * that media. `AniListRepository.getMediaListEntry` asks under the session when
 * it is given no id, so opening a stranger's favourites tab offers *your* entry
 * for that title - the same thing the dashboard offers - never theirs.
 *
 * ## Why there is no post-save refresh here
 *
 * This editor has no list of its own to reload, so a refresh written into it would
 * have to be a callback its hosts implement - three copies of the same "and now
 * re-read the library", which is exactly how the two shapes this class replaced
 * drifted apart in the first place.
 *
 * `DashboardViewModel` still reloads inside its own `save` and `delete`, because
 * every count on that screen is derived from the list it holds in memory and a
 * status moved to Completed has to leave "Currently arriving" immediately.
 *
 * Everywhere else the refresh is the repository's: a write drops its list caches
 * (`AniListRepositoryImpl.saveMediaListEntry` → `invalidateLists()`) and bumps
 * `listMutationVersion`, which the pages that draw a list follow for themselves -
 * the Activity feed, and now the Library and profile pages through
 * `UserOverviewViewModel.watchListMutations`. That comment used to say the next
 * pull-to-refresh would re-read the server's answer, and it did: the list on screen
 * was the one from before the save, so finishing a title from the Library left its
 * row under Currently Watching until the reader pulled the grid down.
 */
class RepositoryEntryEditor(
    private val repository: AniListRepository,
    /**
     * The signed-in viewer's id, or null to let AniList resolve the entry under
     * the session.
     *
     * Null is a *valid* request here and not a signed-out one: `MediaList` in
     * `GET_MEDIA_LIST_ENTRY` treats an omitted `userId` as the auth viewer, which
     * is the same person the dashboard names explicitly. A null id with no
     * session fails in the repository like any other call, and the tab surfaces
     * that failure rather than drawing a form it cannot save.
     */
    private val viewerId: Int? = null
) : EntryEditor {

    override suspend fun load(mediaId: Int): Result<SimpleMediaListEntry> =
        repository.getMediaListEntry(mediaId, viewerId)

    override suspend fun save(edit: MediaEntryEdit): Result<SimpleMediaListEntry> =
        repository.saveMediaListEntry(edit)

    override suspend fun delete(entryId: Int): Result<Unit> =
        repository.deleteMediaListEntry(entryId)
}
