package com.anisequel.ui.screens.tier

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
 *
 * ## The work lives in [TierExportSession], not in the button
 *
 * Share and save are the same pipeline with a different last step, and both have
 * to survive a recomposition halfway through. Keeping that in one small object -
 * rather than in two nearly identical `scope.launch` blocks inside the layout -
 * is what leaves this composable with nothing in it but what the sheet looks
 * like.
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
    val request = remember(listName, tiers, assignment, candidates, display) {
        TierExportRequest(
            listName = listName,
            tiers = tiers,
            assignment = assignment,
            candidates = candidates,
            display = display
        )
    }
    // Kept current rather than captured: the session is remembered for as long as
    // the request is, and the host hands in a fresh lambda on every recomposition.
    val dismiss = rememberUpdatedState(onDismiss)
    val session = remember(request) {
        TierExportSession(context, scope, request) { dismiss.value() }
    }
    // Placements whose title is no longer in the library are still in the
    // assignment so the board can keep the slot, but the exporter drops them -
    // there is no poster to draw - so the count here is a promise about the
    // image, not about the stored document.
    val drawableIds = remember(candidates) { candidates.mapTo(HashSet()) { it.mediaId } }
    val titleCount = remember(assignment, drawableIds) {
        assignment.tierIdByMediaId.keys.count { it in drawableIds }
    }

    // Resolution is a choice for this export, not a stored preference. 4K is the
    // default because the file usually ends up on a larger screen than the phone
    // that made it; the exporter's memory guard lowers the effective scale on a
    // board too tall for the heap, which is why the caption below is a measurement
    // rather than the quality the person picked.
    var quality by rememberSaveable { mutableStateOf(TierExportQuality.ULTRA) }
    // Measured off the main thread: the layout pass is cheap but it is a full
    // pass over the assignment, and the sheet is opening at the same time.
    val sizeEstimate by produceState<TierExportSize?>(null, request, quality) {
        value = if (titleCount == 0) {
            null
        } else {
            withContext(Dispatchers.Default) {
                estimateTierExportSize(
                    listName = request.listName,
                    tiers = request.tiers,
                    assignment = request.assignment,
                    candidates = request.candidates,
                    display = request.display,
                    targetWidth = quality.width
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!session.busy) onDismiss() },
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
                    "just what fits on screen. The current filters - including a " +
                    "name search - and the board's title settings are kept.",
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

            if (titleCount > 0) {
                Spacer(modifier = Modifier.height(18.dp))
                TierExportQualityPicker(
                    selected = quality,
                    onSelect = { quality = it },
                    estimate = sizeEstimate
                )
            }

            TierExportError(session.error)

            // The render is slow enough on a large list that a button that only
            // says "loading" reads as stuck: fetching a 300-title board's covers
            // in batches of eight takes long enough to wonder whether it started.
            // The fraction is per poster batch, so it moves while the covers are
            // arriving, which is the whole of the wait.
            if (session.busy) {
                TierExportProgress(progress = session.progress)
            }

            Spacer(modifier = Modifier.height(18.dp))
            if (titleCount == 0) {
                TierExportEmptyHint(ranked = ranked)
            } else {
                ExpressivePrimaryButton(
                    text = "Share image",
                    onClick = { session.share(quality.width) },
                    loading = session.busy,
                    enabled = !session.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { session.save(quality.width) },
                    enabled = !session.busy,
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

/** The last failure, if there was one, in the sheet rather than in a snackbar. */
@Composable
private fun TierExportError(message: String?) {
    if (message == null) return
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

/**
 * The render's progress, while it is running.
 *
 * A determinate bar rather than the button's spinner: the renderer knows exactly
 * how many tiles it has drawn, and "42%" is an answer where an indeterminate bar
 * is only an apology. The bar is the same width as the buttons below it, so the
 * sheet does not jump sideways when it appears.
 */
@Composable
private fun TierExportProgress(progress: Float) {
    Spacer(modifier = Modifier.height(14.dp))
    Column(modifier = Modifier.testTag("tier_export_progress")) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Rendering the image... ${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Why there is nothing to export - which depends on what the filters did. */
@Composable
private fun TierExportEmptyHint(ranked: Int) {
    Text(
        // Two different empty boards: one has nothing ranked, the other has
        // ranked titles the filters are holding back. The fix is different, so
        // the sentence cannot be the same.
        text = if (ranked == 0) {
            "Rank at least one title before exporting."
        } else {
            "No ranked title is visible with the current filters. " +
                "Clear one to include it in the image."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The export resolutions offered, in the order a phone's screen sizes them.
 *
 * The width is the only dial: the height follows the board's aspect ratio, and
 * the exporter can still lower the scale when the whole bitmap would not fit the
 * heap. Naming the numbers instead of calling them "High"/"Highest" is deliberate
 * - a person choosing 4K is choosing a pixel count, and the caption underneath
 * says what they will actually get.
 */
private enum class TierExportQuality(val width: Int, val label: String) {
    STANDARD(TIER_EXPORT_WIDTH_STANDARD, "1080p"),
    HIGH(TIER_EXPORT_WIDTH_HIGH, "2K"),
    ULTRA(TIER_EXPORT_WIDTH_ULTRA, "4K")
}

/**
 * The resolution chips, plus the measured output size.
 *
 * The caption is the honest half of the control: the chips say what was asked
 * for, [estimate] says what the file will be after the heap guard. It is null for
 * the first frame while the measurement runs, and the caption simply stays out of
 * the way rather than showing a guessed number that then changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierExportQualityPicker(
    selected: TierExportQuality,
    onSelect: (TierExportQuality) -> Unit,
    estimate: TierExportSize?
) {
    Text(
        text = "Resolution",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TierExportQuality.entries.forEach { option ->
            ExpressiveStateChip(
                label = option.label,
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.testTag("tier_export_quality_${option.width}"),
                minTouchTargetHeight = 48.dp
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = estimate?.let { "${it.width} x ${it.height} px PNG" }
            ?: "Measuring the board...",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Everything the render step needs, in the shape the two actions share. */
private data class TierExportRequest(
    val listName: String,
    val tiers: List<TierDefinition>,
    val assignment: TierAssignment,
    val candidates: List<TierCandidate>,
    val display: TierDisplay
)

/**
 * Which button was pressed, and the two outcomes it can produce.
 *
 * The pair is attached to the action rather than left to the caller, so Share and
 * Save cannot report each other's failure text - they are two lines in the same
 * pipeline and the difference is exactly this enum.
 */
private enum class TierExportAction(
    val success: TierExportOutcome,
    val failure: TierExportOutcome
) {
    SHARE(TierExportOutcome.Shared, TierExportOutcome.ShareFailed),
    SAVE(TierExportOutcome.Saved, TierExportOutcome.SaveFailed)
}

private enum class TierExportOutcome(val message: String?) {
    Shared(null),
    Saved(null),
    RenderFailed(
        "The image could not be rendered. Try again with fewer titles or a " +
            "stronger connection."
    ),
    ShareFailed("No app on this device accepted the image."),
    SaveFailed("The image could not be saved to your gallery.")
}

/**
 * The sheet's in-flight state and the two side effects it can start.
 *
 * A plain class remembered by the sheet rather than two lambdas inside it: the
 * buttons used to carry a dozen lines each of render/share/report, and both
 * copies had to remember to clear `busy` on every exit - including the one where
 * the bitmap is null and the one where the share intent is refused. [start] has
 * that exit path once.
 */
private class TierExportSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val request: TierExportRequest,
    private val onDismiss: () -> Unit
) {
    var busy by mutableStateOf(false)
        private set

    /**
     * How much of the image has been rendered, 0..1.
     *
     * Only meaningful while [busy]: the renderer reports per batch of posters, and
     * the bar is drawn from this only while an export is running, so a stale
     * fraction from the last export cannot appear under an idle sheet.
     */
    var progress by mutableFloatStateOf(0f)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun share(targetWidth: Int) = start(TierExportAction.SHARE, targetWidth)

    fun save(targetWidth: Int) = start(TierExportAction.SAVE, targetWidth)

    private fun start(action: TierExportAction, targetWidth: Int) {
        if (busy) return
        busy = true
        error = null
        progress = 0f
        scope.launch {
            val outcome = exportTierListImage(action, context, request, targetWidth) { value ->
                // The renderer runs on `Dispatchers.Default`; this state is read by
                // the sheet on the main thread. Hopping here rather than writing
                // Compose state from the drawing thread keeps the write and the
                // frame that shows it in the same order.
                withContext(Dispatchers.Main.immediate) { progress = value }
            }
            busy = false
            progress = 0f
            when (outcome) {
                TierExportOutcome.Shared -> onDismiss()
                TierExportOutcome.Saved -> {
                    Toast.makeText(
                        context,
                        "Saved to Pictures/AniSequel",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
                else -> error = outcome.message
            }
        }
    }
}

/**
 * Renders the board and hands the bitmap to [action], clearing the bitmap on
 * every path - including the failure one, because a 16 MP bitmap held by a
 * finished export is the largest thing this screen allocates.
 *
 * [CancellationException] is rethrown: the sheet's scope cancels when it closes,
 * and swallowing that would leave a coroutine running after the UI is gone.
 */
private suspend fun exportTierListImage(
    action: TierExportAction,
    context: Context,
    request: TierExportRequest,
    targetWidth: Int,
    onProgress: suspend (Float) -> Unit
): TierExportOutcome {
    val bitmap = renderTierListImageSafely(context, request, targetWidth, onProgress)
        ?: return TierExportOutcome.RenderFailed
    return try {
        val delivered = when (action) {
            TierExportAction.SHARE -> shareTierListImage(context, bitmap, request.listName)
            TierExportAction.SAVE -> saveTierListImage(context, bitmap, request.listName)
        }
        if (delivered) action.success else action.failure
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        action.failure
    } finally {
        bitmap.recycle()
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
    request: TierExportRequest,
    targetWidth: Int,
    onProgress: suspend (Float) -> Unit
): Bitmap? = try {
    renderTierListImage(
        context = context,
        listName = request.listName,
        tiers = request.tiers,
        assignment = request.assignment,
        candidates = request.candidates,
        display = request.display,
        targetWidth = targetWidth,
        onProgress = onProgress
    )
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Throwable) {
    null
}
