package com.anisequel.ui.screens.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.displayName
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.domain.ui.AppLayout
import com.anisequel.domain.ui.HomeSequelStyle
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * Previews for the two layout choices on the Interface page.
 *
 * A chip is a name; a reader choosing between "Classic" and "New look" is choosing
 * a shape. These draw the shape with the reader's own covers and the actual theme
 * colours, so the switch is understood before it is tapped - and because they are
 * built from the same `AppLayout` / `LibraryViewMode` enums the pages use, a new
 * mode cannot quietly appear without a preview.
 */
@Composable
internal fun AppLayoutPreviews(
    selected: AppLayout,
    entries: List<MediaListEntryItem>,
    onSelect: (AppLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppLayout.entries.forEach { layout ->
            val interactionSource = remember { MutableInteractionSource() }
            val active = layout == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.RadioButton,
                        onClickLabel = "Use the ${layout.displayName} layout"
                    ) { onSelect(layout) }
                    .semantics { role = Role.RadioButton }
                    .testTag("app_layout_${layout.storageValue}"),
                shape = RoundedCornerShape(20.dp),
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = BorderStroke(
                    width = if (active) 2.dp else 1.dp,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 132dp was shorter than the Nova mini's own fixed rows
                            // (header, greeting, shortcuts, covers, bar), so the bar
                            // at the bottom was clipped off - the one difference a
                            // reader is choosing between. The height now has room for
                            // every row plus a weighted gap; the card labels sit below.
                            .height(168.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(7.dp)
                    ) {
                        if (layout == AppLayout.NOVA) {
                            NovaLayoutMini(entries = entries)
                        } else {
                            ClassicLayoutMini(entries = entries)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = layout.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (layout == AppLayout.NOVA) "Shelf and rails" else "Original feed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassicLayoutMini(entries: List<MediaListEntryItem>) {
    Column(modifier = Modifier.fillMaxSize()) {
        MiniTopBar()
        Spacer(modifier = Modifier.height(7.dp))
        MiniStatStrip()
        Spacer(modifier = Modifier.height(7.dp))
        entries.take(2).forEachIndexed { index, entry ->
            MiniClassicRow(entry = entry, index = index)
            Spacer(modifier = Modifier.height(5.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        MiniBottomBar()
    }
}

@Composable
private fun NovaLayoutMini(entries: List<MediaListEntryItem>) {
    Column(modifier = Modifier.fillMaxSize()) {
        MiniTopBar()
        Spacer(modifier = Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(9.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.62f))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            MiniShortcut(entries.getOrNull(0))
            MiniShortcut(entries.getOrNull(1))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            entries.take(3).forEach { entry ->
                MiniCover(
                    entry = entry,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        MiniBottomBar()
    }
}

/**
 * The bar both layouts share.
 *
 * The two previews used to end at their content, which made the layouts look like
 * two feeds rather than two ways of using one app. The bar is what the reader
 * actually navigates, and since 1.9.0 both layouts draw the same one, so it is
 * drawn identically here too. The selected dot is Home in both.
 */
@Composable
private fun MiniBottomBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == 0) 6.dp else 4.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == 0) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun MiniTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            )
        }
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    }
}

@Composable
private fun MiniStatStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }
    }
}

@Composable
private fun MiniClassicRow(entry: MediaListEntryItem?, index: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniCover(
            entry = entry,
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        )
        Column(modifier = Modifier.padding(horizontal = 5.dp)) {
            Box(
                modifier = Modifier
                    .width(if (index == 0) 56.dp else 42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun RowScope.MiniShortcut(entry: MediaListEntryItem?) {
    Row(
        modifier = Modifier
            .weight(1f)
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            )
        }
        MiniCover(
            entry = entry,
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
        )
    }
}

/**
 * The two shapes the new Home's sequel list can take, drawn from the reader's
 * own covers.
 *
 * Shares [MiniCover] with the layout previews above, and the sample titles fall
 * back to the same place: a reader choosing between "Rows" and "Cards" is
 * choosing how much of a poster they get, which is exactly the thing a label
 * cannot tell them.
 */
@Composable
internal fun SequelStylePreview(
    style: HomeSequelStyle,
    entries: List<MediaListEntryItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Preview \u00b7 ${style.displayName}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(10.dp)
                .testTag("home_sequel_style_preview")
        ) {
            Crossfade(
                targetState = style,
                animationSpec = ExpressiveMotion.FastEffects,
                label = "home_sequel_style_preview_mode"
            ) { current ->
                when (current) {
                    HomeSequelStyle.ROWS -> Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // The weight is applied here, in the Column, rather than
                        // inside MiniSequelRow: `Modifier.weight` only exists on a
                        // Column/Row scope, and the row's own body was reaching for
                        // it from outside one - which is a compile error the first
                        // CI run after this preview was written.
                        MiniSequelRow(
                            entry = entries.getOrNull(0),
                            modifier = Modifier.weight(1f)
                        )
                        MiniSequelRow(
                            entry = entries.getOrNull(1),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HomeSequelStyle.CARDS -> Column(modifier = Modifier.fillMaxSize()) {
                        MiniCover(
                            entry = entries.getOrNull(0),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        MiniBar(
                            fraction = 0.7f,
                            height = 6.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MiniBar(
                            fraction = 0.42f,
                            height = 4.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                        Spacer(modifier = Modifier.height(7.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(13.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.38f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniSequelRow(
    entry: MediaListEntryItem?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniCover(
            entry = entry,
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight()
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)) {
            MiniBar(
                fraction = 0.72f,
                height = 5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(9.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.34f))
            )
        }
    }
}

@Composable
private fun MiniBar(fraction: Float, height: Dp, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
internal fun LibraryViewPreview(
    mode: LibraryViewMode,
    entries: List<MediaListEntryItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Preview · ${mode.displayName}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(10.dp)
                .testTag("library_view_preview")
        ) {
            Crossfade(
                targetState = mode,
                animationSpec = ExpressiveMotion.FastEffects,
                label = "library_view_preview_mode"
            ) { current ->
                when (current) {
                    LibraryViewMode.GRID -> MiniGrid(entries = entries, columns = 3, showTitle = true)
                    LibraryViewMode.COMPACT -> MiniGrid(entries = entries, columns = 4, showTitle = false)
                    LibraryViewMode.LIST -> MiniList(entries = entries)
                }
            }
        }
    }
}

@Composable
private fun MiniGrid(
    entries: List<MediaListEntryItem>,
    columns: Int,
    showTitle: Boolean
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(columns) { index ->
            Column(modifier = Modifier.weight(1f)) {
                MiniCover(
                    entry = entries.getOrNull(index),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.68f)
                )
                if (showTitle) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = previewTitle(entries.getOrNull(index)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniList(entries: List<MediaListEntryItem>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        repeat(3) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniCover(
                    entry = entries.getOrNull(index),
                    modifier = Modifier
                        .width(34.dp)
                        .fillMaxHeight()
                )
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = previewTitle(entries.getOrNull(index)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniCover(
    entry: MediaListEntryItem?,
    modifier: Modifier = Modifier
) {
    // A preview with no library used to draw four flat grey boxes, so the reader
    // choosing a layout saw placeholders rather than poster-shaped covers. The
    // gradient is the same pair the app uses for cover fallbacks; it keeps the
    // shape readable without inventing a fake title.
    val cover = entry?.media?.posterColor.toCoverColorOrNull()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (cover != null) {
                    Modifier.background(cover)
                } else {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    )
                }
            )
    ) {
        AsyncImage(
            model = entry?.media?.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun previewTitle(entry: MediaListEntryItem?): String =
    entry?.media?.displayName ?: "Dandadan"
