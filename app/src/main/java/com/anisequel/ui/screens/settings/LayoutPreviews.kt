package com.anisequel.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.domain.ui.AppLayout
import com.anisequel.domain.ui.InfoPageStyle
import com.anisequel.domain.ui.NavBarLift
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.AniSequelTheme

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
    modifier: Modifier = Modifier,
    /**
     * How far the mini's bottom bar floats above its lower edge, in dp.
     *
     * The Interface page passes the slider's value, so the previews move with it -
     * the reader is not choosing a number, they are choosing a gap they can see.
     * First-run setup leaves it at zero because it has no bar control.
     */
    barLift: Int = 0
) {
    // The mini layouts follow the slider on the same spring the real bar uses.
    //
    // The animated value stays a `State` and is consumed inside each mini's
    // `graphicsLayer`: reading it here made both mini trees recompose, re-measure
    // and re-lay out on every frame of a spring whose only visible effect is one
    // 18dp capsule moving. The guard still runs on every frame - see the minis.
    val animatedLift = animateFloatAsState(
        targetValue = NavBarLift.clamp(barLift).toFloat(),
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "app_layout_preview_lift"
    )
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
                    // The border and the tint say "chosen" only to a reader who
                    // can see them. The tile is a radio choice, so it now reports
                    // its own selected state instead of leaving TalkBack to infer
                    // it from a click label that is identical on both tiles.
                    .semantics {
                        role = Role.RadioButton
                        selected = active
                    }
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
                        // The emphasis boundary, not half of `outlineVariant`: at
                        // 50% the unselected tiles had no edge at all in the light
                        // scheme, so a row of previews read as one blob and the
                        // selected one was the only shape in it. See
                        // `AniSequelTheme.emphasis` and the credits card's border.
                        AniSequelTheme.emphasis.boundary
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
                            // reader is choosing between. 284dp is the height at which
                            // the miniature stops being a diagram and starts being a
                            // page: the header, the greeting, two shortcut tiles, a row
                            // of covers with their names and the bar, at proportions
                            // the real screen has. The tallest bar lift still fits
                            // above the bar's lane, so nothing it demonstrates is
                            // clipped.
                            .height(284.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(9.dp)
                    ) {
                        if (layout == AppLayout.NOVA) {
                            NovaLayoutMini(entries = entries, lift = animatedLift)
                        } else {
                            ClassicLayoutMini(entries = entries, lift = animatedLift)
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

/**
 * How much of the real lift a mini shows.
 *
 * A mini is a schematic, not a phone: 284dp of card stands in for a 700dp screen,
 * so drawing a 96dp gap at 96dp inside it would push the bar into the header. A
 * quarter is the scale that keeps the *proportion* right - the full range still
 * moves the mini bar a quarter of the way up its own screen - while leaving the
 * mini's own rows intact. The phone-shaped preview below the slider draws the real
 * dp, which is where the reader judges the actual gap.
 */
private const val MINI_LIFT_SCALE = 0.25f

/** The mini's bar, translated in the draw phase so the spring costs no layout. */
@Composable
private fun miniBarLift(lift: State<Float>): Modifier = Modifier.graphicsLayer {
    translationY = -NavBarLift.clampAnimatedDp(lift.value).dp.toPx() * MINI_LIFT_SCALE
}

@Composable
private fun ClassicLayoutMini(entries: List<MediaListEntryItem>, lift: State<Float>) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The lane the bar sits in, so no row runs under it at zero lift.
                .padding(bottom = 22.dp)
        ) {
            MiniTopBar()
            Spacer(modifier = Modifier.height(7.dp))
            MiniStatStrip()
            Spacer(modifier = Modifier.height(7.dp))
            // getOrNull rather than take(2): with an empty or one-entry library the
            // preview used to shrink to one row or none, so the shape a reader is
            // choosing disappeared precisely when they had no posters to demonstrate
            // it with. A null entry draws the themed placeholder cover.
            repeat(3) { index ->
                MiniClassicRow(entry = previewEntry(entries, index), index = index)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
        // The bar floats over the feed, which is what the real bar does; it used to
        // take a gap out of the column, which pushed the rows up as the slider moved.
        MiniBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(miniBarLift(lift))
        )
    }
}

@Composable
private fun NovaLayoutMini(entries: List<MediaListEntryItem>, lift: State<Float>) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 22.dp)
        ) {
            MiniTopBar()
            Spacer(modifier = Modifier.height(9.dp))
            // The greeting was two grey bars. It is the first sentence of the new
            // Home, and a reader choosing between the two layouts is choosing whether
            // a page opens with a person's name or with a stat strip - so the words
            // are the preview, not a rectangle standing in for them.
            Text(
                text = "Good evening",
                style = miniTitleStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "3 sequels are airing today",
                style = miniCaptionStyle(),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniShortcut(
                    entry = previewEntry(entries, 0),
                    label = "Continue",
                    detail = "12 / 24",
                    modifier = Modifier.weight(1f)
                )
                MiniShortcut(
                    entry = previewEntry(entries, 1),
                    label = "Planning",
                    detail = "8 titles",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Continue watching",
                style = miniCaptionStyle(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { index ->
                    MiniCoverColumn(
                        entry = previewEntry(entries, index),
                        progress = index == 0,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        MiniBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(miniBarLift(lift))
        )
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
private fun MiniBottomBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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
                            MaterialTheme.colorScheme.primary
                        } else {
                            // These dots are a *page indicator* in a preview - a
                            // control, not decoration. 0.38 of `onSurfaceVariant` is
                            // 1.7:1 on a light card, so the "you are on page 1 of 4"
                            // information was the least visible thing in the sample.
                            AniSequelTheme.emphasis.skeleton
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
            Text(
                text = "AniSequel",
                style = miniBodyStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "Home",
                style = miniCaptionStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
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

/**
 * The three numbers the classic page opens with.
 *
 * Drawn as numbers and labels rather than three grey blocks: the strip is what makes
 * the classic layout feel like a dashboard, and "watching / completed / planning"
 * with counts is the whole difference between it and a list of covers.
 */
@Composable
private fun MiniStatStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("42" to "Watching", "61" to "Done", "25" to "Planned")
            .forEach { (count, label) ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = count,
                        style = miniCaptionStyle(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = label,
                        style = miniCaptionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
    }
}

/**
 * One classic row: the cover, the name, and the line underneath it.
 *
 * The two grey bars this replaces said "a row has two lines of text", which the
 * reader could already see. The name and the progress line say *where* those two
 * lines sit against the cover and how much of the width they take - which is the
 * thing that differs between the two layouts at the same row height.
 */
@Composable
private fun MiniClassicRow(entry: MediaListEntryItem?, index: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniCover(
            entry = entry,
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = previewTitle(entry),
                style = miniBodyStyle(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (index == 0) "Episode 12 of 24" else "Completed",
                style = miniCaptionStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = if (index == 0) "84%" else "${91 - index * 7}%",
            style = miniCaptionStyle(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 6.dp),
            maxLines = 1
        )
    }
}

/**
 * One shortcut tile: a word for what it does and the number it does it with.
 *
 * The tile's right-hand thumbnail stays - it is what makes the row read as shortcuts
 * rather than buttons - but the label is now the label the reader will tap.
 */
@Composable
private fun RowScope.MiniShortcut(
    entry: MediaListEntryItem?,
    label: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp)
        ) {
            Text(
                text = label,
                style = miniCaptionStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = detail,
                style = miniBodyStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
        MiniCover(
            entry = entry,
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
        )
    }
}

/**
 * A cover with its name under it, and a progress capsule on the one in flight.
 *
 * The capsule is drawn the way the Library draws it - top-left, over the artwork -
 * because that corner is a design decision too, and a reader picking the shelf layout
 * is picking where their progress will sit.
 */
@Composable
private fun MiniCoverColumn(
    entry: MediaListEntryItem?,
    progress: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box {
            MiniCover(
                entry = entry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
            )
            if (progress) {
                Text(
                    text = "12/24",
                    style = miniCaptionStyle(),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = previewTitle(entry),
            style = miniCaptionStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The two title pages, drawn as the shape a reader is choosing between.
 *
 * The same bargain as [AppLayoutPreviews]: a name is not a preview. "Nova" could
 * mean anything until the card shows the key art, the poster lockup, the stats
 * strip and the reading cards; "Classic" could look like a downgrade until the
 * card shows the hero and the section stack it actually is. Both are drawn with
 * the app's own colours and type steps, and both read from [InfoPageStyle] - so a
 * third page added later cannot appear without a preview beside it.
 */
@Composable
internal fun InfoPageStylePreviews(
    selected: InfoPageStyle,
    onSelect: (InfoPageStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoPageStyle.entries.forEach { style ->
            val interactionSource = remember { MutableInteractionSource() }
            val active = style == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.RadioButton,
                        onClickLabel = "Use the ${style.displayName} title page"
                    ) { onSelect(style) }
                    // Same as the layout tiles above: the selected page is a state
                    // a screen reader has to be told, not a colour it has to guess.
                    .semantics {
                        role = Role.RadioButton
                        selected = active
                    }
                    .testTag("info_page_style_${style.storageValue}"),
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
                        // The emphasis boundary, not half of `outlineVariant`: at
                        // 50% the unselected tiles had no edge at all in the light
                        // scheme, so a row of previews read as one blob and the
                        // selected one was the only shape in it. See
                        // `AniSequelTheme.emphasis` and the credits card's border.
                        AniSequelTheme.emphasis.boundary
                    }
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(8.dp)
                    ) {
                        if (style == InfoPageStyle.NOVA) {
                            NovaInfoMini()
                        } else {
                            ClassicInfoMini()
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = style.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = style.blurb,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The shipped page: a banner, a poster and title, then the card stack. */
@Composable
private fun ClassicInfoMini() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 7.dp, bottom = 6.dp)
                    .width(24.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 37.dp, bottom = 6.dp)
            ) {
                Text(
                    text = "Title",
                    style = miniBodyStyle(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "TV · 24m",
                    style = miniCaptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        repeat(3) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .fillMaxWidth(if (index == 1) 0.5f else 0.68f)
                        .clip(CircleShape)
                        .background(AniSequelTheme.emphasis.skeleton)
                )
            }
        }
    }
}

/** The redesign: key art, poster lockup, status capsule, stat strip, progress. */
@Composable
private fun NovaInfoMini() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 7.dp, bottom = 7.dp)
                    .width(26.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 39.dp, bottom = 7.dp)
            ) {
                Text(
                    text = "Title",
                    style = miniBodyStyle(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = "Releasing · TV",
                    style = miniCaptionStyle(),
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 7.dp, bottom = 7.dp)
                    .fillMaxWidth(0.38f)
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("7.5", "34K", "503", "24m").forEach { value ->
                Text(
                    text = value,
                    style = miniCaptionStyle(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .fillMaxWidth(0.55f)
                    .clip(CircleShape)
                    .background(AniSequelTheme.emphasis.skeleton)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
        )
    }
}

/**
 * The three type steps the minis draw with.
 *
 * Composed from the app's own steps and scaled down rather than written as bare sp
 * values, so a change to the type scale reaches the previews too - and so the mini's
 * text keeps its share of the frame instead of becoming 11sp captions in a 100dp
 * column, which is what the first attempt at real text looked like.
 *
 * The reader's own font scale is divided out here. A mini is a picture of a page
 * inside a fixed-height frame: at 175% its 7sp captions and 9sp names would grow
 * into rows that no longer fit the diagram, and the shape the reader is choosing
 * would be the thing clipped. The labels, chips and descriptions around the frame
 * still follow the setting normally, so nothing that has to be *read* shrinks.
 */
@Composable
private fun miniSp(base: Float) =
    (base / LocalDensity.current.fontScale.coerceAtLeast(0.5f)).sp

@Composable
internal fun miniTitleStyle() = MaterialTheme.typography.titleSmall.copy(
    fontSize = miniSp(11f),
    lineHeight = miniSp(13f)
)

@Composable
internal fun miniBodyStyle() = MaterialTheme.typography.labelSmall.copy(
    fontSize = miniSp(9f),
    lineHeight = miniSp(11f)
)

@Composable
internal fun miniCaptionStyle() = MaterialTheme.typography.labelSmall.copy(
    fontSize = miniSp(7f),
    lineHeight = miniSp(9f)
)
