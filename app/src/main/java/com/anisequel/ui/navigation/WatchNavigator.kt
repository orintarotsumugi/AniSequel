package com.anisequel.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.anisequel.data.model.MediaNode

/**
 * Opens the Watch page for a title, from anywhere a title is drawn.
 *
 * A CompositionLocal rather than an `onWatch` parameter threaded through the
 * detail sheet and every host that opens it. The title page is reachable from the
 * dashboard, the Library, a profile, a studio and a relation walk, and each of
 * those already passes eight lambdas into the sheet; adding a ninth to seven call
 * sites would make one missing argument a button that silently does nothing.
 *
 * Null means this build has no navigation host (a preview, or a test that renders
 * the sheet alone). The Watch control is then not drawn rather than drawn dead.
 */
val LocalWatchNavigator = staticCompositionLocalOf<((MediaNode) -> Unit)?> { null }
