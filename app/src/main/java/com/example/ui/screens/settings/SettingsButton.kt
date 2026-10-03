package com.example.ui.screens.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** How much weight a settings action carries. */
enum class SettingsButtonVariant {
    /** The one action the section is actually about. */
    Filled,

    /** A real action, but not the main one - a peer of [Filled]. */
    Tonal,

    /** A legitimate action the user may decline. */
    Outlined,

    /** Tertiary: a link out, or something easily repeated. */
    Text
}

/**
 * The button every Settings action is drawn with.
 *
 * Settings had five hand-written buttons across three files, and they did not
 * agree with each other: heights of 50, 48 and whatever the Material default
 * happened to be, corners on `shapes.small` while every card around them used
 * `shapes.medium`, and icons at 14, 16 and 18dp. Beside cards at 18dp corners a
 * 12dp button reads as a different app that happens to be on the same screen,
 * and the ragged edge is most visible in the update card, where "Not now" and
 * "Download" sat side by side at whatever width their labels happened to be.
 *
 * One component fixes all of it at once: a single height, one corner radius
 * shared with the cards, and a 18dp icon slot. [variant] carries the emphasis,
 * so the visual hierarchy stops depending on which file a button was written
 * in.
 *
 * The height is 48dp rather than the Material default, which is the same height
 * `ExpressivePrimaryButton` already uses - so a settings action and a card
 * action now read as one control family across the app.
 */
@Composable
fun SettingsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: SettingsButtonVariant = SettingsButtonVariant.Filled,
    enabled: Boolean = true,
    /** Stretches to the card's width. Right for a lone action, wrong for a pair. */
    fillWidth: Boolean = false,
    /**
     * Overrides the generated tag. Pass one wherever an existing test or an
     * external automation already selects this button by name, so restyling
     * cannot silently detach it.
     */
    testTag: String? = null
) {
    // Text buttons are links, not controls: giving them the same 48dp block
    // makes a list of three of them look like a form.
    val height = if (variant == SettingsButtonVariant.Text) 40.dp else 48.dp
    val shape = MaterialTheme.shapes.large

    val baseModifier = modifier
        .height(height)
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)

    val label: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    when (variant) {
        SettingsButtonVariant.Filled -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = baseModifier,
            shape = shape,
            content = label
        )

        SettingsButtonVariant.Tonal -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = baseModifier,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            content = label
        )

        SettingsButtonVariant.Outlined -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = baseModifier,
            shape = shape,
            content = label
        )

        SettingsButtonVariant.Text -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = baseModifier,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            content = label
        )
    }
}