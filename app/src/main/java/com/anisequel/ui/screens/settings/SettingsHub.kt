package com.anisequel.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import com.anisequel.ui.components.icons.Icons
import com.anisequel.ui.components.icons.automirrored.filled.ArrowBack
import com.anisequel.ui.components.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.expressive.ExpressiveShapes

/**
 * The pieces the Settings hub is built from.
 *
 * Settings used to be three tabs under a small app bar. The tabs were a filing
 * cabinet: "Info", "Theme" and "Edit" are names for files, not for the things a
 * reader wants to change, and everything in the third one - refresh rate, quick
 * add, app lock, sign-in - was bundled because it had nowhere else to go. The
 * hub is a shelf instead: one row per intention, each with a sentence saying what
 * is behind it, and a sub-page for the detail.
 *
 * The row is deliberately a whole surface rather than a `ListItem`: the icon tile
 * is what makes a long list scannable at arm's length, and the 28dp corner is the
 * app's large shape token. Each row is its own card with a gap, not a segmented
 * group, so a mis-tap between two rows lands on background rather than on the
 * neighbouring setting.
 */
@Composable
fun SettingsHubHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        val haptics = LocalHapticFeedback.current
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClose()
            },
            modifier = Modifier
                .size(48.dp)
                .bouncyPress(pressedScale = 0.9f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag("settings_back_button")
        ) {
            Icon(
                imageVector = AppVectorIcons.Close,
                contentDescription = "Close settings",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * A sub-page's way back, and the only place its name is stated.
 *
 * The title lives here rather than in a `TopAppBar` because the tab bodies
 * already draw their own section headings; a second bar above them made the
 * screen read as a dialog inside a dialog.
 */
@Composable
fun SettingsSubHeader(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * What the back arrow announces, and the tag it is found by.
     *
     * The Interface page's parts are a level below a page rather than below the
     * shelf, so "Back to settings" there would promise two steps away from one. A
     * default keeps every existing caller's sentence and tag unchanged.
     */
    backDescription: String = "Back to settings",
    backTestTag: String = "settings_sub_back"
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onBack()
            },
            // 48dp, not 44: the platform's minimum touch target is 48, and this
            // is the one control every settings page is left through. The 4dp
            // came off the header's own padding budget rather than the target.
            modifier = Modifier
                .size(48.dp)
                .bouncyPress(pressedScale = 0.9f)
                .testTag(backTestTag)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = backDescription
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The quiet label above a group of rows.
 *
 * [compact] is for a label that is already inside a page using
 * [SettingsScrollColumn]: that column supplies the 20dp gutter and a 16dp gap
 * between every child, so a label there must not also indent itself by 28dp and
 * add 26dp above and 10dp below. Doing both put the heading 48dp from the screen
 * edge while its cards sat at 20dp, and left a 56dp hole above a heading that
 * had 26dp below it - the uneven rhythm the Theme page showed most clearly.
 * The shelf keeps the inset label because its rows are indented from the label,
 * so there the extra padding is doing the grouping.
 */
@Composable
fun SettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(
                start = if (compact) 0.dp else 28.dp,
                top = if (compact) 6.dp else 26.dp,
                bottom = if (compact) 0.dp else 10.dp
            )
            // The labels are the only landmarks in a long shelf; without the
            // heading role a screen reader reads one undifferentiated list of
            // rows and cannot jump between Personalisation and About.
            .semantics { heading() }
    )
}

/**
 * One row on the shelf: what it is named, what it says under the name, and where
 * tapping it goes.
 *
 * A value rather than a composable because the group needs to know how many rows it
 * holds in order to put a divider *between* them. Passing a `content` lambda instead
 * meant either counting them at the call site or drawing a rule after the last one,
 * which is a line under nothing.
 */
data class SettingsHubEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val testTag: String,
    val onClick: () -> Unit,
    /**
     * The setting's current state, drawn on the row itself.
     *
     * This is the difference between a table of contents and a control panel.
     * The shelf used to describe only what was behind each row, so "is OLED on?",
     * "how often does the list refresh?" and "is the app lock set?" each cost a
     * page visit and a Back to answer. Everything here is a value the page below
     * is already reading; passing it up costs one collector at the shelf and saves
     * the whole trip.
     *
     * Null when the row has nothing honest to say - Updates has no answer until it
     * has checked, and a guess would be worse than silence. Never a restatement of
     * the subtitle: this line is the *answer*, the subtitle is the *question*.
     */
    val value: String? = null,
    /**
     * The page or part a search result belongs to, printed above its title.
     *
     * Null for an ordinary shelf row: repeating "Settings" over every row would
     * be a second label for the screen the reader is already on. Search results
     * set it because their rows are a flat list drawn from every page.
     */
    val eyebrow: String? = null,
    /** The title with query matches already styled; null draws the plain title. */
    val titleHighlight: AnnotatedString? = null,
    /** The subtitle with query matches already styled; null draws the plain text. */
    val subtitleHighlight: AnnotatedString? = null
)

/**
 * A group of shelf rows inside one surface, separated by hairlines.
 *
 * ## Why one card and not a card per row
 *
 * Each row used to be its own raised surface with a 10dp gap, on the reasoning that
 * a mis-tap between two rows should land on background rather than on a neighbour.
 * That is true of a *list*, and this is not a list - it is five groups of two to
 * four destinations, and drawn as separate cards the grouping was carried by the
 * label alone. The reader scanning for "Device Manager" had no visual answer to
 * which section a row belonged to; the gap between two rows in a group was the same
 * size as the gap between two groups.
 *
 * One surface per group, with a rule between the rows, is what makes the label
 * structural: the rows inside the card are the ones under that heading, and the
 * white space between cards is the only separation that means "different subject".
 * The mis-tap argument survives as the divider's inset - the rule stops where the
 * icon tile ends, so the touch target of a row is the whole width including the
 * gutter beside its icon, and the boundary between two rows is a line, not a hole.
 */
@Composable
fun SettingsHubGroup(
    entries: List<SettingsHubEntry>,
    modifier: Modifier = Modifier,
    /**
     * The tonal family every tile in this card is drawn in.
     *
     * Assigned per group by the shelf, so colour identifies the section a row
     * belongs to. `NEUTRAL` is the default because a group of search results has
     * no section to name; those rows take their variety from their position
     * instead - see [settingsGroupPalette].
     */
    tone: SettingsGroupTone = SettingsGroupTone.NEUTRAL
) {
    if (entries.isEmpty()) return
    Surface(
        shape = ExpressiveShapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        // Inset past the icon tile (16dp padding + 44dp tile + 14dp
                        // gap) so the rule reads as the boundary between two rows of
                        // *text*, not as a cut through the whole card.
                        modifier = Modifier.padding(start = 74.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                }
                SettingsHubEntryRow(entry = entry, index = index, tone = tone)
            }
        }
    }
}

@Composable
private fun SettingsHubEntryRow(
    entry: SettingsHubEntry,
    index: Int,
    tone: SettingsGroupTone
) {
    val haptics = LocalHapticFeedback.current
    // Shared with `bouncyPress` so the press comes from the row's own clickable,
    // which cancels when the gesture is claimed as a scroll. The fallback pointer
    // loop would spring every row the thumb passes while scrolling the shelf.
    val interactionSource = remember { MutableInteractionSource() }
    val palette = settingsGroupPalette(tone, index)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open ${entry.title}"
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                entry.onClick()
            }
            .testTag(entry.testTag)
            .semantics {
                // The value is the answer the row exists for; without this a
                // screen reader reads the question and no state.
                if (entry.value != null) stateDescription = entry.value
            }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // The chevron is `KeyboardArrowRight` rather than a custom vector because it
        // is the platform's own "there is another page this way", which is exactly the
        // promise the row makes. The icon tile takes its family from the group and its
        // silhouette from its position - see [settingsGroupPalette] and
        // [settingsTileShape] for why neither is seeded from the title any more.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(settingsTileShape(index))
                .background(palette.first),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = palette.second,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            if (entry.eyebrow != null) {
                Text(
                    text = entry.eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.size(2.dp))
            }
            Text(
                text = entry.titleHighlight ?: AnnotatedString(entry.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = entry.subtitleHighlight ?: AnnotatedString(entry.subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (entry.value != null) {
            // Narrow and end-aligned: a value is a glance, and a long one (a
            // palette name, a version) must never push the title into an ellipsis
            // to say itself. `labelMedium` in the primary role rather than a pill:
            // a coloured capsule on every row would turn the shelf into a control
            // panel of badges, and the value is information, not an action.
            Text(
                text = entry.value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 104.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * The account, as a card rather than a row.
 *
 * The shelf used to open with a normal row for the account and a second row for
 * the connection check - the only action row on the shelf, and one that made the
 * page's contract "every row is a page" false. Both are one subject: whether the
 * app is talking to AniList as somebody. So the account is a wide card at the top
 * that carries its own live state, and the connection check became the chip on it.
 *
 * A hero surface rather than a stronger row because it is the one thing on this
 * screen whose answer changes what every page below it is looking at - a real
 * AniList list or the built-in demo - and it is glanced at, not read.
 */
@Composable
internal fun SettingsAccountCard(
    name: String?,
    subtitle: String,
    connectionLabel: String,
    /** True reachable, false unreachable, null not yet checked. */
    connectionHealthy: Boolean?,
    onOpenAccount: () -> Unit,
    onCheckConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val cardInteraction = remember { MutableInteractionSource() }
    val chipInteraction = remember { MutableInteractionSource() }
    val chipContainer = when (connectionHealthy) {
        true -> scheme.secondaryContainer
        false -> scheme.errorContainer
        null -> scheme.surfaceContainerHigh
    }
    val chipContent = when (connectionHealthy) {
        true -> scheme.onSecondaryContainer
        false -> scheme.onErrorContainer
        null -> scheme.onSurfaceVariant
    }

    Surface(
        shape = ExpressiveShapes.extraLarge,
        color = scheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.38f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyPress(pressedScale = 0.98f, interactionSource = cardInteraction)
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "Open AniList account"
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenAccount()
                    }
                    .testTag("settings_account_card")
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(settingsTileShape(0))
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()
                    if (initial != null) {
                        Text(
                            text = initial.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = AppVectorIcons.SectionAniList,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AniList",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary
                    )
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = name ?: "Not signed in",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // The connection check, as the account's own status. Tapping it runs
            // the check the old shelf row ran; the row is a fact, the chip is the
            // action, and they are about the same session.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = ExpressiveShapes.pill,
                    color = chipContainer,
                    contentColor = chipContent,
                    modifier = Modifier
                        .bouncyPress(pressedScale = 0.94f, interactionSource = chipInteraction)
                        .clip(ExpressiveShapes.pill)
                        .clickable(
                            interactionSource = chipInteraction,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "Check the AniList connection"
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCheckConnection()
                        }
                        .testTag("settings_row_connection")
                ) {
                    // The padding sits on the content, inside the surface: on the
                    // surface's own modifier it would inset the pill rather than its
                    // label, and the text would touch the capsule's ends.
                    Row(
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (connectionHealthy == false) {
                                AppVectorIcons.SignalOff
                            } else {
                                AppVectorIcons.SignalOn
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Connection: $connectionLabel",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/** The build stamp at the bottom of the shelf. */
@Composable
fun SettingsVersionFooter(versionName: String, versionCode: Int) {
    Text(
        text = "AniSequel $versionName ($versionCode)",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 32.dp)
            .testTag("settings_version_footer")
    )
}
