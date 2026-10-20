package com.anisequel.ui.components.detail.info

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.alternativeTitleRows
import com.anisequel.data.model.studioCredits
import com.anisequel.data.model.synonymList
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The reference's three disclosure rows: Titles, Synonyms, Producers.
 *
 * ## Why these are rows rather than cards
 *
 * Each one is a *list* that belongs beside the title it describes, and every one of
 * them used to be poured into the page: alternative names printed as label/value
 * pairs in a card, synonyms comma-joined into one of those pairs, and the
 * publishers mixed into the studio row of the specs grid. The reference gives each
 * its own row and its own sheet, which is what lets the page stay short while the
 * complete list stays one tap away - and it is why the Producers sheet can list all
 * six companies without wrapping the details card around them.
 *
 * ## Producers are the studios AniList marked non-main
 *
 * `StudioConnection.edges` carries `isMain`, and the specs card already uses it to
 * put the animation studio first. Everything with `isMain == false` is the
 * production committee - the publishers, licensors and music labels AniList files
 * in the same connection - which is exactly what the reference calls Producers. A
 * payload with no edges is treated as all-main (see `studioCredits`), so the row is
 * simply absent rather than listing the animation studio as its own producer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoMetaRowsSection(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val titleRows = remember(media) { media.alternativeTitleRows }
    val synonyms = remember(media) { media.synonymList }
    val producers = remember(media) {
        media.studioCredits.filterNot { it.isMain }.map { it.name }.distinct()
    }

    if (titleRows.isEmpty() && synonyms.isEmpty() && producers.isEmpty()) return

    var open by remember { mutableStateOf<MetaRow?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val border = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_meta_rows_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (titleRows.isNotEmpty()) {
            MetaRowButton(
                icon = AppVectorIcons.List,
                label = "Titles",
                border = border,
                onClick = { open = MetaRow.TITLES },
                testTag = "info_row_titles"
            )
        }
        if (synonyms.isNotEmpty()) {
            MetaRowButton(
                icon = AppVectorIcons.Tag,
                label = "Synonyms",
                count = synonyms.size,
                border = border,
                onClick = { open = MetaRow.SYNONYMS },
                testTag = "info_row_synonyms"
            )
        }
        if (producers.isNotEmpty()) {
            MetaRowButton(
                icon = AppVectorIcons.Studio,
                label = "Producers",
                count = producers.size,
                border = border,
                onClick = { open = MetaRow.PRODUCERS },
                testTag = "info_row_producers"
            )
        }
    }

    open?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { open = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                when (row) {
                    MetaRow.TITLES -> TitleRows(rows = titleRows, border = border)
                    MetaRow.SYNONYMS -> Chips(values = synonyms, border = border)
                    MetaRow.PRODUCERS -> Chips(values = producers, border = border)
                }
            }
        }
    }
}

/** Which list the sheet is showing. */
private enum class MetaRow(val title: String) {
    TITLES("Titles"),
    SYNONYMS("Synonyms"),
    PRODUCERS("Producers")
}

@Composable
private fun MetaRowButton(
    icon: ImageVector,
    label: String,
    border: BorderStroke,
    onClick: () -> Unit,
    testTag: String,
    count: Int? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.98f)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 54.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            count?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = AppVectorIcons.SequelArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * The title sheet: one labelled row per name, each with a copy control.
 *
 * Copy is the only action a name needs - the reader is usually pasting it into a
 * search somewhere else - and the copy is the platform clipboard rather than
 * Compose's, for the same reason `InfoExtrasCard`'s is.
 */
@Composable
private fun TitleRows(rows: List<Pair<String, String>>, border: BorderStroke) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (label, value) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = border,
                        modifier = Modifier
                            // 48dp: a copy control sized to its glyph is the
                            // smallest target on the sheet.
                            .size(48.dp)
                            .bouncyPress(pressedScale = 0.9f)
                            .clickable(role = Role.Button) { copy(context, label, value) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppVectorIcons.CopyLink,
                                contentDescription = "Copy $label title",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(values: List<String>, border: BorderStroke) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { value ->
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = border
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}


private fun copy(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("$label title", value))
}
