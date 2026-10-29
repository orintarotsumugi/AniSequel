package com.anisequel.ui.screens.tier

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons

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
            IconButton(
                onClick = onLeave,
                modifier = Modifier.testTag("tier_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Your tier lists"
                )
            }
        },
        actions = {
            IconButton(
                onClick = onZoom,
                modifier = Modifier.testTag("tier_zoom_button")
            ) {
                Icon(
                    imageVector = AppVectorIcons.Search,
                    contentDescription = "Zoom"
                )
            }
            Box {
                IconButton(
                    onClick = onFilter,
                    modifier = Modifier.testTag("tier_filter_button")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Filter,
                        contentDescription = "Filters"
                    )
                }
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
            IconButton(
                onClick = onDisplay,
                modifier = Modifier.testTag("tier_display_button")
            ) {
                Icon(
                    imageVector = AppVectorIcons.SectionAppearance,
                    contentDescription = "Display options"
                )
            }
            // An icon rather than a "Tiers" text button. Four actions and
            // a back arrow have to fit a 360dp bar; the word cost ~92dp
            // and left the list's own name with about a hundred, which is
            // not enough for the one piece of text that says which list
            // is open.
            IconButton(
                onClick = onManage,
                modifier = Modifier.testTag("tier_manage_button")
            ) {
                Icon(
                    imageVector = AppVectorIcons.Tune,
                    contentDescription = "Manage tiers"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
