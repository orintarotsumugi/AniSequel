package com.anisequel.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.rememberStateColor
import com.anisequel.ui.components.expressive.expressiveShape
import com.anisequel.ui.components.expressive.bouncyPress

val MaxSettingsContentWidth = 640.dp

/**
 * One titled block of the Settings screen.
 *
 * The header used to be a bare [Text] in `titleMedium` at `FontWeight.Bold`, which
 * is the same weight a screen title uses - so "Appearance & Theme" and "AniList
 * API Configuration" read as headings for the page rather than as labels for
 * what is directly below them, and two cards' worth of bold 16sp in a scrolling
 * column became a wall of shouting.
 *
 * Now it is an icon tile plus a title, optionally with a one-line subtitle that
 * says what the card is for. The tile is `secondaryContainer` rather than
 * `primary` on purpose: under Material You the primary role follows the user's
 * wallpaper, and a saturated wallpaper-derived tile next to a wall icon reads as
 * a coloured sticker. `secondaryContainer` is the quiet role, which is what a
 * 34dp decoration wants to be.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    badge: (@Composable () -> Unit)? = null,
    iconContainer: Color? = null,
    iconContent: Color? = null,
    iconShape: Shape? = null,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val palette = settingsTilePalette()
    val resolvedContainer = iconContainer ?: palette.first
    val resolvedContent = iconContent ?: palette.second
    val resolvedShape = iconShape ?: settingsDecorativeShape(title)
    // Not animated, and that is the fix. Every call site passes a *scheme role*
    // that is constant for the life of the card, so this spring was animating a
    // value that never changed - the same bug `LoginHero` and the tier board's
    // ghost each carried a comment about. It looks harmless and is not: the scheme
    // is itself animated during a theme change, so this second spring eased toward
    // a target that was still moving, and the tile arrived after the card it sits
    // on.
    Card(
        modifier = modifier.fillMaxWidth()
            .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize),
        shape = ExpressiveShapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        // A card edge at 0.38 of `outlineVariant` is 1.2:1 on the light card, and
        // light `surfaceContainerLow` sits 1.03:1 from the page behind it - so the
        // border was the only thing defining the card and it could not be seen.
        border = BorderStroke(1.dp, AniSequelTheme.emphasis.divider)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(resolvedShape).background(resolvedContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = resolvedContent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = scheme.onSurface,
                        modifier = Modifier.semantics { heading() })
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        CollapsibleText(text = subtitle, style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant)
                    }
                }
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    badge()
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

/**
 * Decorative Material 3 Expressive silhouettes for fixed-square icon tiles.
 *
 * The silhouette comes from the tile's *position*, not from the letters in its
 * title. It used to be `title.hashCode() % 4`, which meant that renaming a row
 * silently changed the shape it was drawn in, that two rows could collide by
 * luck, and that the set of silhouettes on a shelf carried no meaning at all.
 * Position is stable for as long as the row stays in its place, which is the one
 * thing a reader can actually learn: the first row of a group is a cookie, the
 * second a burst, and so on. A group's rows therefore look like a family, and
 * two shelves start their families the same way.
 */
@Composable
internal fun settingsTileShape(index: Int): Shape =
    when (index.coerceAtLeast(0) % 4) {
        0 -> expressiveShape(ExpressiveShapes.cookie)
        1 -> expressiveShape(ExpressiveShapes.burst)
        2 -> expressiveShape(ExpressiveShapes.diamond)
        else -> expressiveShape(ExpressiveShapes.flower)
    }

/**
 * The one tonal family every settings tile is drawn in.
 *
 * ## Why there is only one
 *
 * There used to be four: `LOOK`/`CONTENT`/`DEVICE` gave each shelf a colour of its
 * own, and search results took a third family from their position. On the shelf
 * that read as a colour chart - the screenshot that started this had a blue
 * Interface tile, a pink Library one and a grey Device Manager one, three shelves
 * apart, saying a fact nobody asked for: which heading the row was filed under.
 * The heading above the card already says that.
 *
 * So the tile says one thing now, with one colour: *this row opens a page*. The
 * silhouette still comes from the row's position - see [settingsTileShape] - which
 * is what keeps a group looking like a family rather than like a repeating stamp,
 * and is the one axis on which variety costs nothing.
 *
 * `secondaryContainer` rather than `primary` for the reason the section cards
 * already record: under Material You the primary role follows the wallpaper, and a
 * saturated wallpaper-derived tile beside an ordinary glyph reads as a sticker.
 */
@Composable
internal fun settingsTilePalette(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return scheme.secondaryContainer to scheme.onSecondaryContainer
}

/**
 * Decorative silhouettes for a *section card's* tile.
 *
 * Kept separate from [settingsTileShape] because a card's tile has no position in
 * a list to read - it is seeded from the card's title so one card always draws
 * the same way. That is the honest use of a hash: identity for a single surface,
 * not ordering for a set of rows.
 */
@Composable
internal fun settingsDecorativeShape(seed: String): Shape =
    when ((seed.hashCode() and Int.MAX_VALUE) % 4) {
        0 -> expressiveShape(ExpressiveShapes.cookie)
        1 -> expressiveShape(ExpressiveShapes.burst)
        2 -> expressiveShape(ExpressiveShapes.diamond)
        else -> expressiveShape(ExpressiveShapes.flower)
    }

/**
 * An explanatory paragraph, collapsed to a two-line preview until it is asked for.
 *
 * The Settings pages grew a paragraph under nearly every control - what a ceiling
 * does, why a cache clears on the next launch, which image host is tried first -
 * and at full length those paragraphs turn a page of switches into a page of
 * prose that has to be scrolled past to reach the next switch. The text does not
 * shrink: it is drawn exactly as written once expanded, and this only decides how
 * much of it is on screen at rest.
 *
 * Two lines, not one: a single line is too short to tell whether the paragraph is
 * worth opening, so the reader has to expand it to find out. Two lines carry the
 * subject of the sentence, which is what makes the rest optional.
 *
 * The toggle appears only once the collapsed text has actually overflowed, so a
 * one-line note gains no "Show more" that would reveal nothing - and it is
 * latched, not live: `hasVisualOverflow` goes false the moment the text is
 * expanded, and a live toggle would delete its own "Show less" as it was pressed.
 * The collapse is its own state keyed on the text, so a paragraph that changes
 * underneath the reader (a switch flips, a count updates) starts collapsed again
 * rather than showing the new sentence at an old length.
 */
@Composable
fun CollapsibleText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    collapsedLines: Int = 2
) {
    val expanded = rememberSaveable(text) { mutableStateOf(false) }
    val overflowed = remember(text) { mutableStateOf(false) }

    Column(
        modifier = modifier.animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
    ) {
        CollapsedTextBody(
            text = text,
            expanded = expanded.value,
            style = style,
            color = color,
            collapsedLines = collapsedLines,
            overflowed = overflowed
        )
        if (overflowed.value) {
            ExpandToggle(
                expanded = expanded.value,
                onToggle = { expanded.value = !expanded.value }
            )
        }
    }
}

/**
 * The text half of [CollapsibleText], with the clamp but no toggle of its own.
 *
 * Split out because [SwitchRow] needs the same clamp inside its toggleable row
 * while the "Show more" control has to sit outside it: a button nested inside a
 * row that is one big switch would be swallowed by the row's merged semantics,
 * so the one thing a screen reader user could not reach is the rest of the
 * explanation.
 */
@Composable
private fun CollapsedTextBody(
    text: String,
    expanded: Boolean,
    style: TextStyle,
    color: Color,
    collapsedLines: Int,
    overflowed: MutableState<Boolean>,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
        overflow = TextOverflow.Ellipsis,
        // A latch, not a mirror: once the collapsed preview has overflowed, the
        // toggle stays for good, including while the text is expanded.
        onTextLayout = { layout ->
            if (!expanded && layout.hasVisualOverflow) overflowed.value = true
        },
        modifier = modifier
    )
}

/**
 * "Show more" / "Show less" under a collapsed paragraph.
 *
 * Full width and 48dp tall rather than a 24dp link: the whole strip is the
 * target, so the control that reveals the rest of the text is not the smallest
 * thing on the card, and it meets the same 48dp floor as every other control on
 * the page. The chevron points at the direction the text will move, and the
 * label carries the meaning for a reader who does not use the glyph.
 */
@Composable
private fun ExpandToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Back to 48dp. The 36 that replaced 40 made this the only control on
            // a Settings card under the platform's own touch-target minimum; the
            // strip is only visible while a paragraph overflows and its height is
            // what a thumb aims at, not a decoration.
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .bouncyPress(pressedScale = 0.97f)
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) {
                    "Collapse this explanation"
                } else {
                    "Read the whole explanation"
                },
                onClick = onToggle
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (expanded) "Show less" else "Show more",
            // The smallest step of the scale, not the caption's: this sits under a
            // body paragraph nobody is reading twice, and at labelMedium it was the
            // loudest thing in the card.
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            imageVector = if (expanded) AppVectorIcons.ExpandLess else AppVectorIcons.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * The caption above a control that has no label of its own.
 *
 * `OutlinedTextField` draws its `label` by notching the top stroke, so a label
 * and a section header end up competing for the same horizontal line and the
 * label appears to collide with the border. Rendering the name here instead
 * keeps the field's outline intact and reads the same at a glance.
 */
@Composable
fun SettingsFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val subtitleExpanded = rememberSaveable(subtitle) { mutableStateOf(false) }
    val subtitleOverflowed = remember(subtitle) { mutableStateOf(false) }
    // The checked row's wash, as an opaque tone rather than a 28% tint.
    //
    // Two bugs in one line: `secondaryContainer` at 0.28 alpha over a card is a
    // 1.1:1 difference - a state you have to be told about - and an alpha is
    // measured against whatever is *behind* it, so the same switch row sat on a
    // different colour depending on which card it was in. The endpoints below are
    // opaque, so the row reads identically anywhere, and the neutral end is the
    // card's own container so an unchecked row is not a slightly different shade
    // of the surface it is drawn on.
    //
    // `rememberStateColor` rather than `animateColorAsState` for the reason the
    // chip records: the scheme is itself animated during a theme change, so a
    // colour target re-targets on every frame of one.
    val rowContainer = rememberStateColor(
        active = checked,
        whenInactive = MaterialTheme.colorScheme.surfaceContainerLow,
        whenActive = lerp(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.secondaryContainer,
            0.55f
        ),
        animationSpec = ExpressiveMotion.FastEffects,
        label = "settings_switch_row_container"
    )
    Column(modifier = modifier.fillMaxWidth()
        .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(ExpressiveShapes.large).background(rowContainer)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { next ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(next)
                })
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                CollapsedTextBody(text = subtitle, expanded = subtitleExpanded.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    collapsedLines = 2, overflowed = subtitleOverflowed)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
        if (subtitleOverflowed.value) {
            ExpandToggle(expanded = subtitleExpanded.value, onToggle = { subtitleExpanded.value = !subtitleExpanded.value })
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        // Centred, not top: the package id is the only value that wraps, and on a
        // two-line row a top-aligned label read as though it belonged to the line
        // above it.
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * The scrolling body every settings page uses.
 *
 * No `state` parameter: it had one while Type & display swapped its two halves in
 * place, and the halves are a shelf of their own now, so every caller wants the
 * same fresh scroll per level and `rememberSaveableStateHolder` is what preserves
 * it. A parameter no call site passes reads as an option and is not.
 */
@Composable
fun SettingsScrollColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = MaxSettingsContentWidth)
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content
        )
    }
}
