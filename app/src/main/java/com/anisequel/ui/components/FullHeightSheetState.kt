package com.anisequel.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

/**
 * The state for a sheet that opens to full height without stopping half-way.
 *
 * ## Why this exists as a function
 *
 * Material3 moved the bottom sheet onto `SheetState` and replaced
 * `rememberModalBottomSheetState(skipPartiallyExpanded = true)` with
 * `rememberBottomSheetState`, where the same instruction is no longer a boolean about one
 * stop but *the set of stops the sheet may settle in* - `Hidden` and `Expanded`, with
 * `PartiallyExpanded` left out. Every sheet in this app wants exactly that set: they are
 * all opened by a tap on a card or a filter chip, they all measure their own content, and
 * a half-open stop is a scrollable area with a clipped bottom row, which is the one thing
 * [rememberSheetFlingGuard] exists to keep from fighting. Ten call sites is also ten
 * chances to spell `setOf(...)` slightly differently, and a sheet that silently gains a
 * partial stop looks like a layout bug rather than a state bug.
 *
 * ## Why the initial value is `Hidden`
 *
 * A modal sheet's composable is entered and left with the flag that opens it, so the state
 * is what the open and dismiss animations run against - there is no resting half-state to
 * start in. `Hidden` is also what the deprecated helper passed, so this changes nothing
 * about the first frame: the sheet still animates up from off-screen, it does not appear
 * already settled and then jump.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun rememberFullHeightSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
)
