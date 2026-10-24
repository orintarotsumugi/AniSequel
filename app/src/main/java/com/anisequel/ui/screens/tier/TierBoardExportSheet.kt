package com.anisequel.ui.screens.tier

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierDisplay
import com.anisequel.ui.components.expressive.ExpressivePrimaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * "Export tier list": render the whole board, then share or save it.
 *
 * ## Why the whole board and not a screenshot
 *
 * The board is inside a scrolling column, so a screen capture is the viewport by
 * definition. [renderTierListImage] lays the *visible* assignment out at the
 * exporter's 1080-unit width instead, which fits eleven posters across and puts
 * every row in one file - a 300-title list is one image rather than forty
 * screenshots. The board's current zoom is deliberately not used: 35% is a
 * useful way to look at the board, not a useful resolution to export at.
 *
 * ## Failures are shown, not thrown
 *
 * A poster that 404s, a bitmap too large for the heap, or a device with no app
 * that accepts `image/png` are all ordinary outcomes here. The sheet renders and
 * reports the failure in place; the alternative is an export button that closes
 * the app on a bad connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierExportSheet(
    listName: String,
    tiers: List<TierDefinition>,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    display: TierDisplay,
    /**
     * How many titles the board holds, before the filters.
     *
     * The sheet is handed the *visible* assignment, because that is what the
     * image would contain. That means an empty assignment has two causes - a list
     * nobody has ranked, and a filter hiding every ranked title - and only the
     * unfiltered count can tell them apart. Without it the sheet told somebody
     * with a full board to "rank at least one title".
     */
    ranked: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Placements whose title is no longer in the library are still in the
    // assignment so the board can keep the slot, but the exporter drops them -
    // there is no poster to draw - so the count here is a promise about the
    // image, not about the stored document.
    val drawableIds = remember(candidates) { candidates.mapTo(HashSet()) { it.mediaId } }
    val titleCount = remember(assignment, drawableIds) {
        assignment.tierIdByMediaId.keys.count { it in drawableIds }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .testTag("tier_export_sheet")
        ) {
            Text(
                text = "Export tier list",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Renders every tier and every ranked poster as one PNG, not " +
                    "just what fits on screen. The current filters are kept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$titleCount ${if (titleCount == 1) "title" else "titles"}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            if (titleCount == 0) {
                Text(
                    // Two different empty boards: one has nothing ranked, the
                    // other has ranked titles the filters are holding back. The
                    // fix is different, so the sentence cannot be the same.
                    text = if (ranked == 0) {
                        "Rank at least one title before exporting."
                    } else {
                        "No ranked title is visible with the current filters. " +
                            "Clear one to include it in the image."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ExpressivePrimaryButton(
                    text = "Share image",
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val bitmap = renderTierListImageSafely(
                                context = context,
                                listName = listName,
                                tiers = tiers,
                                assignment = assignment,
                                candidates = candidates,
                                display = display
                            )
                            if (bitmap == null) {
                                error = "The image could not be rendered. Try again " +
                                    "with fewer titles or a stronger connection."
                                busy = false
                                return@launch
                            }
                            val shared = try {
                                shareTierListImage(context, bitmap, listName)
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: Exception) {
                                false
                            } finally {
                                bitmap.recycle()
                            }
                            busy = false
                            if (shared) {
                                onDismiss()
                            } else {
                                error = "No app on this device accepted the image."
                            }
                        }
                    },
                    loading = busy,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val bitmap = renderTierListImageSafely(
                                context = context,
                                listName = listName,
                                tiers = tiers,
                                assignment = assignment,
                                candidates = candidates,
                                display = display
                            )
                            if (bitmap == null) {
                                error = "The image could not be rendered. Try again " +
                                    "with fewer titles or a stronger connection."
                                busy = false
                                return@launch
                            }
                            val saved = try {
                                saveTierListImage(context, bitmap, listName)
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: Exception) {
                                false
                            } finally {
                                bitmap.recycle()
                            }
                            busy = false
                            if (saved) {
                                Toast.makeText(
                                    context,
                                    "Saved to Pictures/AniSequel",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            } else {
                                error = "The image could not be saved to your gallery."
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Save to Pictures")
                }
            }
        }
    }
}

/**
 * Renders, turning every ordinary failure into "no image" for the sheet.
 *
 * `CancellationException` is rethrown: the sheet's scope cancels when it closes,
 * and swallowing that would leave a coroutine running after the UI is gone.
 */
private suspend fun renderTierListImageSafely(
    context: Context,
    listName: String,
    tiers: List<TierDefinition>,
    assignment: TierAssignment,
    candidates: List<TierCandidate>,
    display: TierDisplay
): Bitmap? = try {
    renderTierListImage(
        context = context,
        listName = listName,
        tiers = tiers,
        assignment = assignment,
        candidates = candidates,
        display = display
    )
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Throwable) {
    null
}
