package com.anisequel.ui.screens.tier

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The bar over the board: the list's name, and the four ways to change what the
 * board shows.
 *
 * ## Smaller than the app's top app bar on purpose
 *
 * This was a `TopAppBar`, which is 64dp of chrome with its own title typography
 * tuned for a *destination* page. The board is a workspace: the name is a label on
 * the tool, not a page heading, and 64dp above a wall of posters is a strip of the
 * screen spent on nothing. The bar is now 52dp - a 40dp back button, a
 * `titleMedium` name, and the four actions grouped into one pill - so the board
 * starts higher and the chrome reads as part of the tool.
 *
 * The grouped pill is also what makes the actions read as one set. Four loose icon
 * buttons beside a title look like four unrelated controls; one rounded surface
 * with hairlines of space inside it says they all change the same board. It is the
 * same shape language as the detail page's floating controls, which is the point:
 * the board and the title page should feel like two views of one library.
 *
 * Search is one of those actions now. The bar used to draw the search glyph over
 * the *zoom* sheet, so the one icon everybody reads as "find" opened a slider -
 * and the board had no way to find a title by name at all. Search opens
 * [TierSearchSheet]; zoom stays on the board's own floating stepper, where the
 * pinch it mirrors also lives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierBoardTopBar(
    title: String,
    filterActive: Boolean,
    onLeave: () -> Unit,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    onDisplay: () -> Unit,
    onManage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TierBarAction(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Your tier lists",
            onClick = onLeave,
            testTag = "tier_back_button",
            size = 40
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 8.dp)
        )

        Surface(
            shape = ExpressiveShapes.pill,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.testTag("tier_bar_actions")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TierBarAction(
                    icon = AppVectorIcons.Search,
                    contentDescription = "Find a title",
                    onClick = onSearch,
                    testTag = "tier_search_button",
                    size = 38
                )
                Box {
                    TierBarAction(
                        icon = AppVectorIcons.Filter,
                        contentDescription = "Filters",
                        onClick = onFilter,
                        testTag = "tier_filter_button",
                        size = 38
                    )
                    // A dot rather than a count: the number of hidden statuses
                    // is not a number anybody acts on, but "a filter is on" is
                    // the thing that explains a short board.
                    if (filterActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 7.dp, end = 8.dp)
                                .size(7.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                // A grid, not the palette: the library screen already uses the
                // palette for its theme sheet, and these two bars sit one tab
                // apart. This one changes how the board is drawn - titles, zoom,
                // caption length - which is a layout question.
                TierBarAction(
                    icon = AppVectorIcons.ViewGrid,
                    contentDescription = "Display options",
                    onClick = onDisplay,
                    testTag = "tier_display_button",
                    size = 38
                )
                TierBarAction(
                    icon = AppVectorIcons.Tune,
                    contentDescription = "Manage tiers",
                    onClick = onManage,
                    testTag = "tier_manage_button",
                    size = 38
                )
            }
        }
    }
}

/**
 * One action in the board's bar: an icon button with the app's press spring.
 *
 * The actions were plain `IconButton`s, so pressing one of them was the only
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
    testTag: String,
    size: Int
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size.dp)
            .bouncyPress(pressedScale = 0.9f, interactionSource = interactionSource)
            .testTag(testTag),
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size((size - 16).dp)
        )
    }
}
