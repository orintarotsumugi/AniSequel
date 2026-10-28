package com.anisequel.ui.screens.profile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Velocity

/**
 * Stops a sheet that is already fully open from inheriting an upward fling.
 *
 * ## The bug this is for
 *
 * A `ModalBottomSheet` nests its own `AnchoredDraggable` above its content, and a
 * `LazyColumn` hands whatever velocity it could not spend to that parent. At the
 * bottom of a list - or anywhere on a list too short to scroll - a fast upward
 * fling therefore leaves upward velocity with nowhere to go: the sheet is already
 * at its expanded anchor. Material's anchored draggable keeps trying to settle
 * against the boundary, and the sheet visibly bounces/jumps until the reader
 * touches it. It is worst in the two engagement sheets, whose content is close to
 * full-screen height, and it reads as the sheet being shaken as soon as it opens.
 *
 * The sheet has always been able to travel *down* - that is the drag-to-dismiss
 * gesture - so only upward velocity is consumed here. Downward velocity is left
 * untouched for the sheet's own connection to finish the gesture with.
 *
 * `onPostFling`, not `onPreFling`: before the child flings there is no way to know
 * how much of the velocity it will use, and consuming there would steal a fling
 * from a list that could still scroll. By the post-fling callback the list is
 * done and this is exactly the remainder.
 *
 * Attach it above the scrolling list, with `Modifier.nestedScroll(...)`, inside
 * the sheet's content - above the sheet's own connection in the chain, so the
 * leftover is eaten before the anchored draggable sees it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberSheetFlingGuard(sheetState: SheetState): NestedScrollConnection =
    remember(sheetState) {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if (available.y < 0f && sheetState.currentValue == SheetValue.Expanded) {
                    // Upward, and there is no further up. Spend it here rather than
                    // let the sheet's anchor spring fight it.
                    Velocity(0f, available.y)
                } else {
                    Velocity.Zero
                }
        }
    }
