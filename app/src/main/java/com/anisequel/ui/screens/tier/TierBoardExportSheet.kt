package com.anisequel.ui.screens.tier

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    /**
     * How many titles the library holds, before the filters.
     *
     * A board whose placements are all missing from the *visible* candidates can
     * mean two different things, and they have different fixes: the library is
     * still loading (wait) or the ranked titles are gone from it (rank others).
     * An empty unfiltered library is the loading case; a full one is not.
     */
    librarySize: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The heading is this export's own choice, not a stored board setting: what
    // reads well as a name under a poster on a phone is not what reads well at 4K
    // in a chat window. Saveable so a rotation mid-choice keeps it.
    var showTitle by rememberSaveable { mutableStateOf(true) }
    var titleSizeName by rememberSaveable {
        mutableStateOf(TierExportTitleSize.MEDIUM.name)
    }
    var titleFontName by rememberSaveable {
        mutableStateOf(TierExportTitleFont.SANS.name)
    }
    val titleOptions = TierExportTitleOptions(
        showTitle = showTitle,
        size = TierExportTitleSize.entries.firstOrNull { it.name == titleSizeName }
            ?: TierExportTitleSize.MEDIUM,
        font = TierExportTitleFont.entries.firstOrNull { it.name == titleFontName }
            ?: TierExportTitleFont.SANS
    )
    val request = remember(listName, tiers, assignment, candidates, display, titleOptions) {
        TierExportRequest(
            listName = listName,
            tiers = tiers,
            assignment = assignment,
            candidates = candidates,
            display = display,
            title = titleOptions
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
                    targetWidth = quality.width,
                    titleOptions = request.title
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
                // The sheet gained title controls, so on a short screen the
                // buttons at the bottom would otherwise sit off the edge with no
                // way to reach them.
                .verticalScroll(rememberScrollState())
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
                    "name search - and the board's title settings are kept; the " +
                    "heading below is yours to size, face and hide.",
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
                Spacer(modifier = Modifier.height(18.dp))
                TierExportTitlePicker(
                    showTitle = showTitle,
                    onShowTitleChange = { showTitle = it },
                    size = titleOptions.size,
                    onSizeChange = { titleSizeName = it.name },
                    font = titleOptions.font,
                    onFontChange = { titleFontName = it.name },
                    sampleTitle = request.listName
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
                TierExportEmptyHint(
                    ranked = ranked,
                    visibleRanked = assignment.tierIdByMediaId.size,
                    librarySize = librarySize
                )
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

/**
 * Why there is nothing to export - which depends on what the filters, the
 * library and the ranking each did.
 *
 * The cases are tried in the order that makes the previous one impossible:
 * nothing ranked; every placement filtered out; the library itself empty (the
 * shapes are still loading); otherwise the ranked titles are not in the library
 * any more - possibly some of them merely filtered out on top of that.
 */
@Composable
private fun TierExportEmptyHint(
    ranked: Int,
    visibleRanked: Int,
    librarySize: Int
) {
    val scopeHidden = ranked > visibleRanked
    val message = when {
        ranked == 0 -> "Rank at least one title before exporting."
        visibleRanked == 0 ->
            "No ranked title is visible with the current filters. " +
                "Clear one to include it in the image."
        // The board keeps the slots for these placements - which is why the
        // assignment is not empty - but an export has no poster to draw for
        // them. With an empty library the titles have not arrived yet, and
        // "rank a title" would be advice to redo work that is already done.
        librarySize == 0 ->
            "Your library has not loaded yet, so there are no posters to draw. " +
                "The export will work once it does."
        scopeHidden ->
            "Some ranked titles are hidden by the current filters, and the rest " +
                "are no longer in your library. Clear a filter to check."
        else ->
            "The ranked titles are no longer in your library, so there are no " +
                "posters to draw. Rank a title you still have."
    }
    Text(
        text = message,
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
 * says the largest file they can actually get.
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
 * for, [estimate] says the size the layout implies at the full memory budget.
 * It is a ceiling rather than a promise - the renderer halves its budget when an
 * allocation fails, so a board too tall for the heap comes out smaller than
 * this - which is why the caption says "Up to". It is null for the first frame
 * while the measurement runs, and the caption simply stays out of the way rather
 * than showing a guessed number that then changes.
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
        text = estimate?.let { "Up to ${it.width} x ${it.height} px PNG" }
            ?: "Measuring the board...",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Everything the render step needs, in the shape the two actions share. */
/**
 * The heading's own controls: whether it shows, how big, and in which face.
 *
 * The sample under the chips is the preview: the chosen name, at the chosen size
 * and family, on the export's surface. A size named "L" means nothing until the
 * reader sees their own list name at it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierExportTitlePicker(
    showTitle: Boolean,
    onShowTitleChange: (Boolean) -> Unit,
    size: TierExportTitleSize,
    onSizeChange: (TierExportTitleSize) -> Unit,
    font: TierExportTitleFont,
    onFontChange: (TierExportTitleFont) -> Unit,
    sampleTitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Title",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "The list's name at the top of the image.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = showTitle,
            onCheckedChange = onShowTitleChange,
            modifier = Modifier.testTag("tier_export_title_switch")
        )
    }

    if (!showTitle) return

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Size",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TierExportTitleSize.entries.forEach { option ->
            ExpressiveStateChip(
                label = option.displayName,
                selected = option == size,
                onClick = { onSizeChange(option) },
                modifier = Modifier.testTag("tier_export_title_size_${option.name}"),
                minTouchTargetHeight = 48.dp
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = "Font",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TierExportTitleFont.entries.forEach { option ->
            ExpressiveStateChip(
                label = option.displayName,
                selected = option == font,
                onClick = { onFontChange(option) },
                modifier = Modifier.testTag("tier_export_title_font_${option.name}"),
                minTouchTargetHeight = 48.dp
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tier_export_title_sample")
    ) {
        Text(
            text = sampleTitle.ifBlank { "Your tier list" },
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = font.toComposeFamily(),
                fontSize = (20f * size.scale).sp
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

private fun TierExportTitleFont.toComposeFamily(): FontFamily = when (this) {
    TierExportTitleFont.SANS -> FontFamily.SansSerif
    TierExportTitleFont.SERIF -> FontFamily.Serif
    TierExportTitleFont.MONO -> FontFamily.Monospace
}

private data class TierExportRequest(
    val listName: String,
    val tiers: List<TierDefinition>,
    val assignment: TierAssignment,
    val candidates: List<TierCandidate>,
    val display: TierDisplay,
    val title: TierExportTitleOptions = TierExportTitleOptions()
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
        titleOptions = request.title,
        onProgress = onProgress
    )
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Throwable) {
    null
}
