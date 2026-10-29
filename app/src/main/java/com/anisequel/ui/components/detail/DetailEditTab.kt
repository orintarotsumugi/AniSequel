package com.anisequel.ui.components.detail

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaEntryEdit
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.hasNotAired
import com.anisequel.data.model.isManga
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressivePrimaryButton
import com.anisequel.ui.components.expressive.bouncyPress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The "Edit" tab of the detail sheet: the viewer's own AniList entry.
 *
 * ## Why this is a tab and not another button
 *
 * The sheet already answers "what is this show?"; this answers the other question
 * a person opens a title with - "where is it in *my* list?". Status, progress,
 * score and the two dates are the whole of an AniList entry, and they belong on
 * the same page as the title they describe rather than behind a long-press on a
 * card somewhere else.
 *
 * ## Why the state is local
 *
 * The draft lives here, not in the ViewModel. Nothing outside this tab renders an
 * in-progress edit, so hoisting it would make every owner of the sheet carry a
 * saving flag and an error string it never reads. The one thing that *is* shared
 * is [EntryEditor], which is where the request actually goes.
 *
 * It is held one level up, on [EntryEditState], because `AnimatedContent` disposes
 * the tab it animates away from - so state remembered *here* was rebuilt from
 * nothing on every tab switch, spinner and all, and the entry was fetched again
 * for a list AniList had already answered. See [EntryEditState] for that.
 *
 * ## Why edits are staged rather than saved as you touch them
 *
 * Every control moves the local draft and nothing else; only "Save changes" sends
 * a request. Saving on each change would put a write on AniList for every step of
 * a slider and every tap of the stepper - the account's whole request budget for
 * the minute - and would make a mis-tap impossible to undo, because the server
 * would already hold it. Staging is also what the "Save changes" button on this
 * form means to a reader, and what makes "Delete" safe beside it.
 *
 * The tab is only built when the caller can write - see [EntryEditor] - so there
 * is no disabled state to get wrong: an editor that cannot save is not drawn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailEditTab(
    media: MediaNode,
    editor: EntryEditor,
    state: EntryEditState,
    /**
     * The *sheet's* scope, not one remembered inside this tab.
     *
     * A tab-scoped coroutine dies with the tab, and `AnimatedContent` disposes the
     * tab the moment the reader switches to Info - which cancelled a save that was
     * already on the wire. The request still reached AniList, but nothing was left
     * to read the answer, so `isSaving` never returned to false and the tab came
     * back stuck on its spinner. The sheet outlives every tab switch, so a save
     * launched there finishes and lands whether or not its tab is still drawn.
     */
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val isManga = media.isManga

    // The entry is fetched by the sheet, which owns [state]. Nothing to launch
    // here any more, and nothing to re-fetch when this tab is rebuilt.
    val currentError = state.loadError
    if (currentError != null) {
        // A failed load used to be a dead end: the message replaced the form and
        // nothing could ask again, so a request that timed out left the tab
        // unusable until the sheet was closed and reopened. Retrying re-runs the
        // same one-shot load; it is safe to press twice because [EntryEditState.load]
        // is a no-op once a draft exists.
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppVectorIcons.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = currentError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextButton(onClick = { scope.launch { state.load(editor, media.id) } }) {
                    Text(
                        text = "Try again",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        return
    }

    val entryState = state.draft
    if (entryState == null) {
        // The morphing loader, not a bare `CircularProgressIndicator`.
        //
        // This is the app's own `LoadingIndicator` wrapper, used in twenty-odd
        // places across the profile tabs, the sheets and the dashboard. The
        // detail tab was one of the few still on the plain M3 spinner, so the
        // one moment this screen admits to waiting looked like a different app
        // from the moment before it - and a tab that rebuilds on every switch
        // showed it repeatedly.
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveLoadingIndicator(modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Loading your entry...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val totalUnits = if (isManga) media.chapters ?: 0 else media.episodes ?: 0
    // Every control reads this, not only the two buttons: the save response
    // *replaces* the draft, so anything edited while the request is in flight is
    // overwritten when the answer lands. Locking the form is what makes the
    // replacement honest instead of a silently lost drag.
    val enabled = !state.isSaving

    fun persist(updated: EntryDraft) {
        state.isSaving = true
        state.statusMessage = null
        scope.launch {
            try {
                editor.save(
                    MediaEntryEdit(
                        mediaId = media.id,
                        status = updated.status,
                        progress = updated.progress,
                        progressVolumes = updated.progressVolumes,
                        score = updated.score,
                        startedAt = updated.startedAt,
                        completedAt = updated.completedAt,
                        isPrivate = updated.isPrivate,
                        notes = updated.notes
                    )
                ).fold(
                    onSuccess = { saved ->
                        // The save payload has no `Viewer` in it, so the reply alone
                        // cannot say how the score should be drawn. The draft's own
                        // format is what keeps a hundred-point account on its scale
                        // after a save whose score happens to be below ten.
                        state.markSaved(saved.toDraft(updated.scoreFormat))
                        state.statusMessage = "Saved to your AniList list"
                    },
                    onFailure = { state.statusMessage = it.message ?: "Couldn't save your changes." }
                )
            } finally {
                // Even a cancelled or throwing save has to hand the form back in a
                // usable state; without this the busy button outlived the request.
                state.isSaving = false
            }
        }
    }

    // The fields scroll inside the tab, and the two actions are pinned below them.
    //
    // Both used to be one column inside the *sheet's* scroll, with the buttons as the
    // form's last row - after Status, Progress, Score, both dates, Private and the
    // status line. On a 640dp-tall screen that put "Save changes" roughly 170dp below
    // the fold, so the first thing a reader who had just changed a score had to do was
    // scroll to find the button that keeps it, and a form whose primary action is only
    // reachable by scrolling reads as a form with no action at all. The pinned bar is
    // drawn as the same outlined card as the rows above it (see [EditorCard]), because
    // it is the last row of this form rather than a new kind of surface.
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status.
            Box {
                EditorCard(
                    icon = AppVectorIcons.Info,
                    title = "Status",
                    subtitle = statusLabel(entryState.status, isManga),
                    onClick = { state.statusMenuOpen = true },
                    testTag = "edit_status_row",
                    enabled = enabled
                )
                DropdownMenu(
                    expanded = state.statusMenuOpen,
                    onDismissRequest = { state.statusMenuOpen = false }
                ) {
                    ENTRY_STATUSES.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.labelFor(isManga)) },
                            onClick = {
                                state.statusMenuOpen = false
                                state.draft = entryState.copy(status = option.apiValue)
                            }
                        )
                    }
                }
            }

            // Progress, hidden for a title that has not aired.
            //
            // Asking somebody how many episodes of something they have not seen,
            // of something that does not exist yet, is not a useful question - and
            // the slider is not a neutral one either: it opens on the episode
            // count, invites a tap, and writes that number to AniList. A planner
            // entry for a show two years out is the common case here, so this is
            // the row most likely to be wrong rather than right.
            //
            // `hasAired` is deliberately about *AniList's* status and not the
            // user's own `startedAt` date: someone who marks a show as started
            // before it airs has an opinion about it, and hiding the row would then
            // take away the one field they were reaching for.
            if (!media.hasNotAired) {
                EditorCard(
                    icon = AppVectorIcons.BookmarkDone,
                    title = "Progress",
                    subtitle = if (totalUnits > 0) {
                        "${entryState.progress} / $totalUnits"
                    } else {
                        "${entryState.progress}"
                    },
                    testTag = "edit_progress_row"
                ) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        if (totalUnits > 0) {
                            Slider(
                                value = entryState.progress.coerceIn(0, totalUnits).toFloat(),
                                onValueChange = {
                                    state.draft = entryState.copy(progress = it.toInt())
                                },
                                valueRange = 0f..totalUnits.toFloat(),
                                steps = (totalUnits - 1).coerceAtLeast(0),
                                enabled = enabled,
                                modifier = Modifier.testTag("edit_progress_slider")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StepButton(
                                icon = AppVectorIcons.StepDown,
                                contentDescription = "Decrease progress",
                                testTag = "edit_progress_minus",
                                enabled = enabled,
                                onClick = {
                                    state.draft = entryState.copy(
                                        progress = (entryState.progress - 1).coerceAtLeast(0)
                                    )
                                }
                            )
                            Text(
                                text = "${entryState.progress}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("edit_progress_value")
                            )
                            StepButton(
                                icon = AppVectorIcons.StepUp,
                                contentDescription = "Increase progress",
                                testTag = "edit_progress_plus",
                                enabled = enabled,
                                onClick = {
                                    val ceiling = if (totalUnits > 0) totalUnits else Int.MAX_VALUE
                                    // Stop at the ceiling *before* adding. A title
                                    // with no known total used `Int.MAX_VALUE` as
                                    // its ceiling, and `Int.MAX_VALUE + 1` overflows
                                    // to `Int.MIN_VALUE` - so the plus button on an
                                    // unscheduled show stepped the count backwards
                                    // into the negative.
                                    if (entryState.progress < ceiling) {
                                        state.draft = entryState.copy(progress = entryState.progress + 1)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Score, in the account's own scale and sent back unchanged. See
            // `EntryDraft.score` for why it is not normalised to ten.
            EditorCard(
                icon = AppVectorIcons.Star,
                title = "Score",
                // Zero is AniList's own "no score": an unscored entry loads as 0
                // and saving 0 clears the score again. Printing "0 / 10" for it
                // claimed the reader had rated the title the lowest possible
                // mark, which is the opposite of having rated it at all.
                subtitle = if (entryState.score <= 0.0) {
                    "Not scored"
                } else {
                    "${formatScore(entryState.score)} / ${entryState.scoreMax}"
                },
                testTag = "edit_score_row"
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    // Ticks - and the value a drag lands on - come from the
                    // account's format, not from the denominator: one tick per
                    // whole point on every scale was why a decimal entry showing
                    // "8.5 / 10" could only be dragged to 8 or 9. `quantise`
                    // puts each drag on a value the format actually holds, which
                    // is also what keeps a continuous slider (a hundred-point
                    // scale draws no ticks) landing on whole points.
                    val scale = ScoreScale(entryState.scoreMax, entryState.scoreStep)
                    Slider(
                        value = entryState.score.coerceIn(0.0, entryState.scoreMax.toDouble()).toFloat(),
                        onValueChange = { state.draft = entryState.copy(score = scale.quantise(it.toDouble())) },
                        valueRange = 0f..entryState.scoreMax.toFloat(),
                        steps = scale.steps,
                        enabled = enabled,
                        modifier = Modifier.testTag("edit_score_slider")
                    )
                }
            }

            // The two dates. A date the viewer has not set reads as not set rather
            // than as today, which is what makes clearing one meaningful.
            DateRow(
                label = "Start Date",
                value = entryState.startedAt,
                testTag = "edit_start_date_row",
                onPick = { state.openDatePicker = DateField.START },
                onClear = { state.draft = entryState.copy(startedAt = null) },
                enabled = enabled
            )
            DateRow(
                label = "Finish Date",
                value = entryState.completedAt,
                testTag = "edit_finish_date_row",
                onPick = { state.openDatePicker = DateField.FINISH },
                onClear = { state.draft = entryState.copy(completedAt = null) },
                enabled = enabled
            )

            // Private entry.
            EditorCard(
                icon = AppVectorIcons.Privacy,
                title = "Private Entry",
                subtitle = "Hidden from your public profile",
                testTag = "edit_private_row"
            ) {
                Switch(
                    checked = entryState.isPrivate,
                    onCheckedChange = { checked ->
                        state.draft = entryState.copy(isPrivate = checked)
                    },
                    enabled = enabled,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag("edit_private_switch")
                )
            }

            // Notes. AniList's own `notes` field, which the app was already reading
            // into `SimpleMediaListEntry` but never drawing or sending.
            EditorCard(
                icon = AppVectorIcons.EditEntry,
                title = "Notes",
                subtitle = "Only you can see this",
                testTag = "edit_notes_row"
            ) {
                OutlinedTextField(
                    value = entryState.notes,
                    onValueChange = { note -> state.draft = entryState.copy(notes = note) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .testTag("edit_notes_field"),
                    label = { Text("Private note") },
                    minLines = 3,
                    maxLines = 6,
                    shape = MaterialTheme.shapes.small,
                    enabled = enabled
                )
            }
        }

        // The action row, pinned below the fields.
        //
        // "Nothing to send" is a real state - it is the state the form is in from the
        // moment it loads - so the Save button is disabled until the draft differs
        // from the entry AniList confirmed, and the line above it says which state
        // this is rather than leaving a dead button unexplained. Tapping Save on an
        // untouched form used to send a whole entry snapshot back to AniList to
        // change nothing.
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val actionHint = state.statusMessage
                    ?: if (!state.hasUnsavedChanges) "No changes to save yet" else null
                actionHint?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("edit_status_message")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val deleteInteraction = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = {
                            val entryId = entryState.entryId ?: return@OutlinedButton
                            state.isSaving = true
                            state.statusMessage = null
                            scope.launch {
                                try {
                                    editor.delete(entryId).fold(
                                        onSuccess = {
                                            // Back to an entry that does not exist yet, so the
                                            // form is ready to create a fresh one rather than
                                            // showing the values that were just removed.
                                            state.cleared()
                                            state.statusMessage = "Removed from your list"
                                        },
                                        onFailure = {
                                            state.statusMessage =
                                                it.message ?: "Couldn't remove this entry."
                                        }
                                    )
                                } finally {
                                    state.isSaving = false
                                }
                            }
                        },
                        enabled = !state.isSaving && entryState.entryId != null,
                        // 48dp, the app's expressive button height, rather than the
                        // 46dp this used to be: it sits beside the primary action, and
                        // two heights half a pixel apart read as a misalignment rather
                        // than as hierarchy.
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .bouncyPress(pressedScale = 0.96f, interactionSource = deleteInteraction)
                            .testTag("edit_delete_button"),
                        shape = MaterialTheme.shapes.medium,
                        interactionSource = deleteInteraction
                    ) {
                        Text("Delete")
                    }

                    // The app's shared primary button rather than a stock `Button`:
                    // it carries the press spring, the shape morph and the
                    // loader-on-its-own-container pairing every other busy action in
                    // the app uses. The stock pair here had it inverted -
                    // `onPrimary` container with a `primary` spinner - which was a
                    // light disc with a coloured hole rather than the button thinking.
                    ExpressivePrimaryButton(
                        text = "Save changes",
                        onClick = { persist(entryState) },
                        modifier = Modifier.weight(1.3f),
                        loading = state.isSaving,
                        enabled = state.hasUnsavedChanges,
                        testTag = "edit_save_button"
                    )
                }
            }
        }
    }

    val picker = state.openDatePicker
    if (picker != null) {
        val initial = when (picker) {
            DateField.START -> entryState.startedAt
            DateField.FINISH -> entryState.completedAt
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochMillisUtc())
        DatePickerDialog(
            onDismissRequest = { state.openDatePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    val picked = pickerState.selectedDateMillis?.toFuzzyDateUtc()
                    if (picked != null) {
                        state.draft = when (picker) {
                            DateField.START -> entryState.copy(startedAt = picked)
                            DateField.FINISH -> entryState.copy(completedAt = picked)
                        }
                    }
                    state.openDatePicker = null
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { state.openDatePicker = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
