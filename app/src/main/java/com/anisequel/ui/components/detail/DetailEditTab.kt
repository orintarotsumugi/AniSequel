package com.anisequel.ui.components.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
fun DetailEditTab(
    media: MediaNode,
    editor: EntryEditor,
    modifier: Modifier = Modifier
) {
    val isManga = media.isManga
    val scope = rememberCoroutineScope()

    var draft by remember(media.id) { mutableStateOf<EntryDraft?>(null) }
    var loadError by remember(media.id) { mutableStateOf<String?>(null) }
    var statusMessage by remember(media.id) { mutableStateOf<String?>(null) }
    var isSaving by remember(media.id) { mutableStateOf(false) }

    // Which date, if any, has its picker open. One value rather than one flag per
    // field, so the two pickers can never both be up.
    var openDatePicker by remember(media.id) { mutableStateOf<DateField?>(null) }

    LaunchedEffect(media.id) {
        editor.load(media.id).fold(
            onSuccess = {
                draft = it.toDraft()
                loadError = null
            },
            onFailure = { loadError = it.message ?: "Couldn't load your entry." }
        )
    }

    val currentError = loadError
    if (currentError != null) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        return
    }

    val state = draft
    if (state == null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
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

    fun persist(updated: EntryDraft) {
        isSaving = true
        statusMessage = null
        scope.launch {
            editor.save(
                MediaEntryEdit(
                    mediaId = media.id,
                    status = updated.status,
                    progress = updated.progress,
                    progressVolumes = updated.progressVolumes,
                    score = updated.score,
                    startedAt = updated.startedAt,
                    completedAt = updated.completedAt,
                    isPrivate = updated.isPrivate
                )
            ).fold(
                onSuccess = { saved ->
                    draft = saved.toDraft()
                    statusMessage = "Saved to your AniList list"
                },
                onFailure = { statusMessage = it.message ?: "Couldn't save your changes." }
            )
            isSaving = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var statusMenuOpen by remember(media.id) { mutableStateOf(false) }

        // Status.
        Box {
            EditorCard(
                icon = AppVectorIcons.Info,
                title = "Status",
                subtitle = statusLabel(state.status, isManga),
                onClick = { statusMenuOpen = true },
                testTag = "edit_status_row"
            )
            DropdownMenu(expanded = statusMenuOpen, onDismissRequest = { statusMenuOpen = false }) {
                ENTRY_STATUSES.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.labelFor(isManga)) },
                        onClick = {
                            statusMenuOpen = false
                            draft = state.copy(status = option.apiValue)
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
                subtitle = if (totalUnits > 0) "${state.progress} / $totalUnits" else "${state.progress}",
                testTag = "edit_progress_row"
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (totalUnits > 0) {
                        Slider(
                            value = state.progress.coerceIn(0, totalUnits).toFloat(),
                            onValueChange = { draft = state.copy(progress = it.toInt()) },
                            valueRange = 0f..totalUnits.toFloat(),
                            steps = (totalUnits - 1).coerceAtLeast(0),
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
                            onClick = {
                                draft = state.copy(progress = (state.progress - 1).coerceAtLeast(0))
                            }
                        )
                        Text(
                            text = "${state.progress}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("edit_progress_value")
                        )
                        StepButton(
                            icon = AppVectorIcons.StepUp,
                            contentDescription = "Increase progress",
                            testTag = "edit_progress_plus",
                            onClick = {
                                val ceiling = if (totalUnits > 0) totalUnits else Int.MAX_VALUE
                                draft = state.copy(progress = (state.progress + 1).coerceAtMost(ceiling))
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
            subtitle = "${formatScore(state.score)} / ${state.scoreMax}",
            testTag = "edit_score_row"
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Slider(
                    value = state.score.coerceIn(0.0, state.scoreMax.toDouble()).toFloat(),
                    onValueChange = { draft = state.copy(score = it.toDouble()) },
                    valueRange = 0f..state.scoreMax.toFloat(),
                    steps = (state.scoreMax - 1).coerceAtLeast(0),
                    modifier = Modifier.testTag("edit_score_slider")
                )
            }
        }

        // The two dates. A date the viewer has not set reads as not set rather
        // than as today, which is what makes clearing one meaningful.
        DateRow(
            label = "Start Date",
            value = state.startedAt,
            testTag = "edit_start_date_row",
            onPick = { openDatePicker = DateField.START },
            onClear = { draft = state.copy(startedAt = null) }
        )
        DateRow(
            label = "Finish Date",
            value = state.completedAt,
            testTag = "edit_finish_date_row",
            onPick = { openDatePicker = DateField.FINISH },
            onClear = { draft = state.copy(completedAt = null) }
        )

        // Private entry.
        EditorCard(
            icon = AppVectorIcons.Privacy,
            title = "Private Entry",
            subtitle = "Hidden from your public profile",
            testTag = "edit_private_row"
        ) {
            Switch(
                checked = state.isPrivate,
                onCheckedChange = { checked -> draft = state.copy(isPrivate = checked) },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("edit_private_switch")
            )
        }

        statusMessage?.let { message ->
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
            OutlinedButton(
                onClick = {
                    val entryId = state.entryId ?: return@OutlinedButton
                    isSaving = true
                    statusMessage = null
                    scope.launch {
                        editor.delete(entryId).fold(
                            onSuccess = {
                                // Back to an entry that does not exist yet, so the
                                // form is ready to create a fresh one rather than
                                // showing the values that were just removed.
                                draft = EntryDraft(
                                    entryId = null,
                                    status = null,
                                    progress = 0,
                                    progressVolumes = null,
                                    score = 0.0,
                                    scoreMax = 10,
                                    startedAt = null,
                                    completedAt = null,
                                    isPrivate = false
                                )
                                statusMessage = "Removed from your list"
                            },
                            onFailure = {
                                statusMessage = it.message ?: "Couldn't remove this entry."
                            }
                        )
                        isSaving = false
                    }
                },
                enabled = !isSaving && state.entryId != null,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("edit_delete_button"),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Delete")
            }

            Button(
                onClick = { persist(state) },
                enabled = !isSaving,
                modifier = Modifier
                    .weight(1.3f)
                    .height(46.dp)
                    .testTag("edit_save_button"),
                shape = MaterialTheme.shapes.small
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save changes")
            }
        }
    }

    val picker = openDatePicker
    if (picker != null) {
        val initial = when (picker) {
            DateField.START -> state.startedAt
            DateField.FINISH -> state.completedAt
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochMillisUtc())
        DatePickerDialog(
            onDismissRequest = { openDatePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    val picked = pickerState.selectedDateMillis?.toFuzzyDateUtc()
                    if (picked != null) {
                        draft = when (picker) {
                            DateField.START -> state.copy(startedAt = picked)
                            DateField.FINISH -> state.copy(completedAt = picked)
                        }
                    }
                    openDatePicker = null
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { openDatePicker = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
