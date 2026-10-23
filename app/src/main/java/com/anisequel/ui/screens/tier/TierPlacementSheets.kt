package com.anisequel.ui.screens.tier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The sheets the board opens, in their own file.
 *
 * They were part of the screen file, which was past 1,500 lines and being read as
 * one thing. The split is by responsibility rather than by size: a sheet's job is
 * to hand one decision back to the caller, the board's is to arrange posters, and
 * the two change for different reasons - which is the rule the repository's own
 * conventions set out.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierPlaceSheet(
    title: String,
    coverUrl: String?,
    tiers: List<TierDefinition>,
    currentTierId: String?,
    onMove: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            // The cover is what the person just tapped, so the sheet opens with
            // the thing they are moving rather than only its name. A title-only
            // header made a sheet about a poster look like a sheet about a string.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val placeholder = rememberVectorPainter(AppVectorIcons.List)
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 40.dp, height = 60.dp)
                        .clip(RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentScale = ContentScale.Crop,
                    placeholder = placeholder,
                    error = placeholder,
                    fallback = placeholder
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = title.ifBlank { "Move to" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TierChoiceList(
                tiers = tiers,
                currentTierId = currentTierId,
                onPick = onMove,
                onRemove = onRemove
            )
        }
    }
}

/**
 * The "start empty" route: the library's unranked titles, then the tiers.
 *
 * Both steps live in *one* sheet, switching on internal state, because
 * `ModalBottomSheet` presents into its own window and two of them up at once -
 * even for a single frame - is the documented way to reach "only one sheet can be
 * presented". Picking a title here therefore closes nothing; it replaces the
 * contents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierAddTitlesSheet(
    unranked: List<TierCandidate>,
    tiers: List<TierDefinition>,
    currentTierOf: (Int) -> String?,
    onMove: (mediaId: Int, tierId: String?, beforeMediaId: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var pickingId by remember { mutableStateOf<Int?>(null) }
    val picking = pickingId?.let { id -> unranked.firstOrNull { it.mediaId == id } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (picking == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .testTag("tier_add_titles_sheet"),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item(key = "header") {
                    Text(
                        text = if (unranked.isEmpty()) {
                            "Every title in your library is ranked."
                        } else {
                            "Add titles (${unranked.size} unranked)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(unranked, key = { it.mediaId }) { candidate ->
                    TierCandidateRow(
                        candidate = candidate,
                        onClick = { pickingId = candidate.mediaId }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = picking.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TierChoiceList(
                    tiers = tiers,
                    currentTierId = currentTierOf(picking.mediaId),
                    onPick = { tierId ->
                        onMove(picking.mediaId, tierId, null)
                        // Back to the unranked list, so placing one title flows
                        // straight into placing the next. The picker used to stay
                        // open on the title it had just filed, which read as the
                        // sheet being stuck rather than as a place to keep working.
                        pickingId = null
                    },
                    onRemove = null
                )
            }
        }
    }
}

/**
 * The tiers as a list of choices, plus optionally the way back out.
 *
 * The colour dot beside each label is drawn from the tier's own `colorArgb`, which
 * is what keeps a renamed tier recognisable as the row it came from.
 */
@Composable
private fun TierChoiceList(
    tiers: List<TierDefinition>,
    currentTierId: String?,
    onPick: (String) -> Unit,
    onRemove: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        tiers.forEach { tier ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { onPick(tier.id) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(tierRowColor(tier.colorArgb))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = tier.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (tier.id == currentTierId) {
                    Icon(
                        imageVector = AppVectorIcons.Done,
                        contentDescription = "Currently in this tier",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (onRemove != null) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            TextButton(
                onClick = onRemove,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = "Remove from this list",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun TierCandidateRow(candidate: TierCandidate, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val placeholder = rememberVectorPainter(AppVectorIcons.List)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Before the gesture, so the minimum is inside the hit area.
            .defaultMinSize(minHeight = 48.dp)
            .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = candidate.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(width = 32.dp, height = 48.dp)
                .clip(RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = candidate.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val score = candidate.averageScore
        if (score != null && score > 0) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Rename, add and remove tiers.
 *
 * Removing a tier *drops* its entries rather than promoting them to the tier above.
 * `TierListViewModel.removeTier` documents why, and the dialog repeats it in the
 * reader's terms: promoting would file a title under a judgement the person never
 * made, and an entry nobody placed is a truer state than one placed by a deletion.
 * A dialog that only asked "Delete tier?" would leave somebody losing an afternoon
 * of ranking to one tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierManageSheet(
    tiers: List<TierDefinition>,
    memberCounts: Map<String, Int>,
    rankedCount: Int,
    onRename: (String, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onColor: (TierDefinition) -> Unit,
    onScoreFloor: (TierDefinition) -> Unit,
    onClearPlacements: () -> Unit,
    onDismiss: () -> Unit
) {
    var pendingRemoval by remember { mutableStateOf<TierDefinition?>(null) }
    var clearingPlacements by remember { mutableStateOf(false) }
    val rowActions = TierManageRowActions(
        rename = onRename,
        remove = { tier -> pendingRemoval = tier },
        color = onColor,
        scoreFloor = onScoreFloor
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .testTag("tier_manage_sheet"),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Text(
                    text = "Tiers",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            items(tiers, key = { it.id }) { tier ->
                TierManageRow(
                    tier = tier,
                    members = memberCounts[tier.id] ?: 0,
                    canRemove = tiers.size > 1,
                    actions = rowActions
                )
            }

            item(key = "add") {
                TextButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Add a tier")
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                // The one *destructive* action that lives with the tiers rather
                // than with the board: it empties the rows without deleting any of
                // them, which is what somebody rebuilding a ranking wants and is
                // exactly the opposite of removing a tier. It is undoable, which is
                // why the dialog can say so plainly instead of performing it.
                TextButton(
                    onClick = { clearingPlacements = true },
                    enabled = rankedCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Empty every tier",
                        color = if (rankedCount > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }

    if (clearingPlacements) {
        AlertDialog(
            onDismissRequest = { clearingPlacements = false },
            title = { Text("Empty every tier?") },
            text = {
                Text(
                    "The $rankedCount " + (if (rankedCount == 1) "title" else "titles") +
                        " go back to being unranked, and your tiers, names and colours " +
                        "stay exactly as they are. You can undo this straight after."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearPlacements()
                    clearingPlacements = false
                }) {
                    Text("Empty it", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearingPlacements = false }) { Text("Cancel") }
            }
        )
    }

    val removal = pendingRemoval
    if (removal != null) {
        val members = memberCounts[removal.id] ?: 0
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove \"${removal.label}\"?") },
            text = {
                Text(
                    text = if (members == 0) {
                        "This tier is empty, so nothing else changes."
                    } else {
                        "The $members " + (if (members == 1) "title" else "titles") +
                            " filed under it " + (if (members == 1) "is" else "are") +
                            " removed with it, not moved into another tier. They go back" +
                            " to your library, unranked. You can undo this straight after."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(removal.id)
                    pendingRemoval = null
                }) {
                    Text("Remove tier", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("Keep it")
                }
            }
        )
    }
}

/**
 * What one tier row can ask the sheet to do.
 *
 * Kept together so the row itself stays about laying out a caption, a swatch, a
 * band and two buttons. Removing is a request to open the confirmation, not to
 * delete: the dialog lives in the sheet because it needs the tier's member count
 * and the shared `pendingRemoval` state.
 */
private class TierManageRowActions(
    val rename: (String, String) -> Unit,
    val remove: (TierDefinition) -> Unit,
    val color: (TierDefinition) -> Unit,
    val scoreFloor: (TierDefinition) -> Unit
)

/**
 * One tier in the manage sheet.
 *
 * Extracted from [TierManageSheet] because the rename field is the only branch the
 * row has and keeping it in the sheet made every row's editing state part of the
 * sheet's. `remember(tier.id)` gives each row its own: scrolling a half-typed name
 * away and back starts the rename again rather than reopening it mid-word.
 */
@Composable
private fun TierManageRow(
    tier: TierDefinition,
    members: Int,
    canRemove: Boolean,
    actions: TierManageRowActions
) {
    var renaming by remember(tier.id) { mutableStateOf(false) }

    if (renaming) {
        TierRenameField(
            initial = tier.label,
            maxLength = TIER_LABEL_MAX_LENGTH,
            onCancel = { renaming = false },
            onConfirm = { label ->
                actions.rename(tier.id, label)
                renaming = false
            }
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The swatch is the colour control, not a decoration. A separate "change
        // colour" row for every tier would double the height of the sheet to edit
        // the one property people change most.
        IconButton(
            onClick = { actions.color(tier) },
            modifier = Modifier.testTag("tier_colour_button")
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(tierRowColor(tier.colorArgb))
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${tier.label} - $members",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = { actions.scoreFloor(tier) },
                modifier = Modifier
                    .defaultMinSize(minHeight = 32.dp)
                    .testTag("tier_band_button")
            ) {
                Text(
                    text = tier.scoreFloor?.let { "Rating $it+" } ?: "Set rating band",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tier.scoreFloor != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        TextButton(
            onClick = { renaming = true },
            modifier = Modifier.defaultMinSize(
                minWidth = 48.dp,
                minHeight = 48.dp
            )
        ) {
            Text("Rename")
        }
        TextButton(
            onClick = { actions.remove(tier) },
            // `removeTier` refuses to go below one row, because with nothing left
            // there is nowhere to drag onto. Disabled rather than hidden so the
            // control does not appear and disappear as tiers come and go.
            enabled = canRemove,
            modifier = Modifier.defaultMinSize(
                minWidth = 48.dp,
                minHeight = 48.dp
            )
        ) {
            Text(
                text = "Remove",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** A tier's label is a row caption, so it is capped far below a list's name. */
internal const val TIER_LABEL_MAX_LENGTH = 24

/**
 * A short text field with a confirm and a cancel.
 *
 * Shared by renaming a tier and renaming a list, because both are "type a short
 * label and keep it": one field with one cap is what stops a row caption and a
 * list's name drifting to different rules.
 */
@Composable
internal fun TierRenameField(
    initial: String,
    maxLength: Int,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // `remember`, not `rememberSaveable`: a half-typed label is a transient edit,
    // and restoring one into a row that was re-entered minutes later would be a
    // silent overwrite of whatever the name is by then.
    var text by remember { mutableStateOf(initial) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(maxLength) },
            singleLine = true,
            shape = ExpressiveShapes.pill,
            // The cap is shown, not just enforced: a field that silently stops
            // accepting characters reads as a keyboard that has stopped working.
            supportingText = { Text("${text.length}/$maxLength") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("tier_rename_field")
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = { onConfirm(text) }) {
            Icon(
                imageVector = AppVectorIcons.Done,
                contentDescription = "Save name"
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = AppVectorIcons.Close,
                contentDescription = "Cancel rename"
            )
        }
    }
}
