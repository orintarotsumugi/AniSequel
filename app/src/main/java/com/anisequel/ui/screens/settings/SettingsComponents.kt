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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    /**
     * The icon tile's background. Defaults to the quiet `secondaryContainer`
     * role; the Theme tab passes richer roles so its sections read as the
     * colourful half of Settings without the Edit and Info tabs changing.
     */
    iconContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    /** The icon drawn on [iconContainer]. */
    iconContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    /**
     * The icon tile's outline.
     *
     * Defaults to the theme's small rounded square. The Settings pages that pass
     * an Expressive polygon here are opting into the decorative family, and the
     * tile is the only element in the app entitled to it: it is a fixed 34dp
     * square, and `RoundedPolygon` is authored on a square perimeter - see
     * `ExpressiveShapes` for what happens when one outlines a wide control.
     */
    iconShape: Shape = MaterialTheme.shapes.small,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(iconShape)
                            .background(iconContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconContent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        // Every settings tab is a stack of these cards, so the title
                        // is the only stable landmark a screen-reader user can jump
                        // to. The subtitle stays out of the heading role.
                        modifier = Modifier.semantics { heading() }
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        CollapsibleText(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    badge()
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
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
 * Full width and 40dp tall rather than a 24dp link: the whole strip is the
 * target, so the control that reveals the rest of the text is not the smallest
 * thing on the card. The chevron points at the direction the text will move, and
 * the label carries the meaning for a reader who does not use the glyph.
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
            .heightIn(min = 40.dp)
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            imageVector = if (expanded) AppVectorIcons.ExpandLess else AppVectorIcons.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
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
    // A switch that moves without a tick is the one place a settings screen can
    // feel like a picture of a switch. TextHandleMove is the app's light tick -
    // long-press is reserved for a destructive or dragged action.
    val haptics = LocalHapticFeedback.current
    // The subtitle's collapse, hoisted out of the toggleable row: the "Show more"
    // button has to live outside it (see [CollapsedTextBody]), and a paragraph
    // that changes with the switch above it starts collapsed again.
    val subtitleExpanded = rememberSaveable(subtitle) { mutableStateOf(false) }
    val subtitleOverflowed = remember(subtitle) { mutableStateOf(false) }
    // The whole row is the target, not just the 52dp switch: a settings row whose
    // label does nothing when tapped is the most common way a screen like this
    // feels broken, and Android has trained people to expect the label to work.
    //
    // `toggleable` rather than `clickable` + a separate switch callback. The old
    // pair made two focus targets out of one setting - TalkBack read the label,
    // then the switch as an unrelated control - and fired the tick twice when the
    // thumb itself was tapped. With the switch inert (`onCheckedChange = null`)
    // there is one node, one state and one tick, and the row still answers the
    // whole width. `mergeDescendants` is what makes the label part of that node
    // rather than a sibling read before it.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = { next ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCheckedChange(next)
                    }
                )
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                CollapsedTextBody(
                    text = subtitle,
                    expanded = subtitleExpanded.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    collapsedLines = 2,
                    overflowed = subtitleOverflowed
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                // Inert: the row owns the action. A second callback here would be a
                // second focus target and a second haptic for one tap.
                onCheckedChange = null,
                enabled = enabled
            )
        }
        if (subtitleOverflowed.value) {
            ExpandToggle(
                expanded = subtitleExpanded.value,
                onToggle = { subtitleExpanded.value = !subtitleExpanded.value }
            )
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
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}
