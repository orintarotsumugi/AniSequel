package com.example.ui.components.detail

import com.example.data.model.MediaEntryEdit
import com.example.data.model.SimpleMediaListEntry

/**
 * Reads and writes the signed-in viewer's own list entry for one media.
 *
 * The Edit tab is handed this rather than a stack of separate callbacks because
 * reading, saving and deleting are three answers to one question - "what is on
 * my list for this show, and change it" - and a screen able to do one of them
 * but not the others is a screen with a dead control on it.
 *
 * `null` wherever the sheet is read-only (a scanned profile, the demo feed)
 * removes the tab entirely rather than drawing it disabled. An editor that
 * cannot save looks identical to one that can until the moment it silently does
 * nothing, which is the failure this type exists to make impossible.
 *
 * The methods suspend so the tab can call them from its own scope and keep its
 * in-progress state local: nothing else on the screen needs to know a save is
 * happening, and threading a saving flag through the dashboard state would make
 * every caller of the sheet carry it.
 */
interface EntryEditor {

    /**
     * The viewer's entry for [mediaId].
     *
     * A media with no entry resolves to an empty [SimpleMediaListEntry] carrying
     * the media id, not a failure: "not on my list yet" is the ordinary state for
     * an arriving show, and the form has to be able to create the entry from it.
     */
    suspend fun load(mediaId: Int): Result<SimpleMediaListEntry>

    /** Creates or replaces the entry with the fields [edit] carries. */
    suspend fun save(edit: MediaEntryEdit): Result<SimpleMediaListEntry>

    /** Removes this entry from the viewer's list. */
    suspend fun delete(entryId: Int): Result<Unit>
}
