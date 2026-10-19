package com.anisequel.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.domain.usecase.ContentLimits
import com.anisequel.ui.viewmodel.CommentThreadStore

/**
 * The manage dialog for one of the viewer's own comments.
 *
 * Three steps in one window: the menu, the editor, and the confirmation. State -
 * which comment, which step, the draft in flight, the failure of the last
 * attempt - lives in [CommentThreadStore] rather than in the sheet, so a request
 * that outlives a recomposition still has somewhere to land and a failure is
 * still on screen next to the control that will retry it.
 *
 * The busy states rename their buttons rather than disabling silently: a delete
 * is a round trip, and a button that goes grey with nothing else changing reads
 * as a broken tap.
 *
 * A window of its own rather than a row in the thread: an edit in flight is
 * anchored to a comment id, not to a position, and the row that opened it can be
 * recycled by the list while the dialog is still up.
 */
@Composable
internal fun ReplyActionDialog(
    action: CommentThreadStore.ReplyAction,
    onMode: (CommentThreadStore.ReplyAction.Mode) -> Unit,
    onDraftChange: (String) -> Unit,
    onSubmitEdit: () -> Unit,
    onSubmitDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    when (action.mode) {
        CommentThreadStore.ReplyAction.Mode.ACTIONS -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Your comment") },
            confirmButton = {
                TextButton(onClick = { onMode(CommentThreadStore.ReplyAction.Mode.EDIT) }) {
                    Text("Edit")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onMode(CommentThreadStore.ReplyAction.Mode.CONFIRM_DELETE) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        )

        CommentThreadStore.ReplyAction.Mode.EDIT -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit comment") },
            text = {
                Column {
                    OutlinedTextField(
                        value = action.draft,
                        onValueChange = onDraftChange,
                        enabled = !action.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activity_reply_edit_field"),
                        // The same 2..8000 window the composer enforces, from the
                        // one place both read: see [ContentLimits].
                        placeholder = { Text("Edit your comment") },
                        maxLines = 6,
                        shape = MaterialTheme.shapes.large
                    )
                    if (action.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = action.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onSubmitEdit,
                    enabled = !action.isBusy &&
                        ContentLimits.checkReply(action.draft) == ContentLimits.ReplyVerdict.Valid
                ) {
                    Text(if (action.isBusy) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !action.isBusy) {
                    Text("Cancel")
                }
            }
        )

        CommentThreadStore.ReplyAction.Mode.CONFIRM_DELETE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete this comment?") },
            text = {
                Column {
                    Text("This can't be undone.")
                    if (action.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = action.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onSubmitDelete, enabled = !action.isBusy) {
                    Text(
                        text = if (action.isBusy) "Deleting..." else "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !action.isBusy) {
                    Text("Cancel")
                }
            }
        )
    }
}
