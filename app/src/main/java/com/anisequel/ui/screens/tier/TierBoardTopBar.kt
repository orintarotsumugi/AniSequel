package com.anisequel.ui.screens.tier

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The bar over the board: the list's name, and the four ways to change what the
 * board shows.
 *
 * Split out of `TierBoardScreen` because it is chrome rather than board: it
 * reads no layout, no drag state and no viewport, and a change to what the board
 * draws should not have to be reviewed against a row of toolbar buttons. Every
 * action only opens a sheet, so the bar takes callbacks and no data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierBoardTopBar(
    title: String,
    filterActive: Boolean,
    onLeave: () -> Unit,
    onZoom: () -> Unit,
    onFilter: () -> Unit,
    onDisplay: () -> Unit,
    onManage: () -> Unit
) {
    TopAppBar(
        // The list's own name, not the feature's. Which list you are in is
        // the one thing a bar over a board full of posters cannot tell you
        // by looking at it.
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            TierBarAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Your tier lists",
                onClick = onLeave,
                testTag = "tier_back_button"
            )
        },
        actions = {
            TierBarAction(
                icon = AppVectorIcons.Search,
                contentDescription = "Zoom",
                onClick = onZoom,
                testTag = "tier_zoom_button"
            )
            Box {
                TierBarAction(
                    icon = AppVectorIcons.Filter,
                    contentDescription = "Filters",
                    onClick = onFilter,
                    testTag = "tier_filter_button"
                )
                // A dot rather than a count: the number of hidden statuses
                // is not a number anybody acts on, but "a filter is on" is
                // the thing that explains a short board.
                if (filterActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(8.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            TierBarAction(
                icon = AppVectorIcons.SectionAppearance,
                contentDescription = "Display options",
                onClick = onDisplay,
                testTag = "tier_display_button"
            )
            // An icon rather than a "Tiers" text button. Four actions and
            // a back arrow have to fit a 360dp bar; the word cost ~92dp
            // and left the list's own name with about a hundred, which is
            // not enough for the one piece of text that says which list
            // is open.
            TierBarAction(
                icon = AppVectorIcons.Tune,
                contentDescription = "Manage tiers",
                onClick = onManage,
                testTag = "tier_manage_button"
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * One action in the board's bar: an icon button with the app's press spring.
 *
 * The five actions were plain `IconButton`s, so pressing one of them was the only
 * place in the tier screen that answered a finger with nothing. The same glyph in
 * `AniTopAppBar` springs, and a control that does not move under a thumb reads as
 * disabled next to the ones that do. The interaction source is shared with the
 * clickable so a gesture that turns into a scroll cancels the press instead of
 * leaving the icon squashed for the whole fling.
 */
@Composable
private fun TierBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .bouncyPress(interactionSource = interactionSource)
            .testTag(testTag),
        interactionSource = interactionSource
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}
