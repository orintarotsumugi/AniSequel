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
 * `DashboardViewModel` reloads the whole dashboard inside `save` and `delete`,
 * because every count on that screen is derived from the list it holds in
 * memory and a status moved to Completed has to leave "Currently arriving"
 * immediately. This editor's hosts hold no list of their own: the repository
 * already drops its list cache on every write
 * (`AniListRepositoryImpl.saveMediaListEntry` → `invalidateLists()`), so the
 * next pull-to-refresh or navigation re-reads the server's answer instead of a
 * copy this class would have had to know how to update.
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
