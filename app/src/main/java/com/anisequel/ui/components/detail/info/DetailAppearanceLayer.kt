package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anisequel.data.model.MediaNode
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.data.repository.InfoBackdropTint
import com.anisequel.data.repository.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The title page's appearance editor, opened over the page it edits.
 *
 * Kept out of the page itself because it is the one overlay whose job is not the
 * page: every chip writes a preference the page reads straight back, so the sheet
 * behind the overlay is the live preview rather than a copy of it. That also keeps
 * five preference writes out of a function that is already the page's whole
 * composition.
 */
@Composable
internal fun DetailAppearanceLayer(
    media: MediaNode,
    preferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope,
    onClose: () -> Unit
) {
    InfoAppearanceEditor(
        media = media,
        source = settings.infoBackdropSource,
        blur = settings.infoBackdropBlur,
        tint = settings.infoBackdropTint,
        onSelectSource = { chosen ->
            scope.launch { preferences.setInfoBackdropSource(chosen) }
        },
        onSelectBlur = { chosen ->
            scope.launch { preferences.setInfoBackdropBlur(chosen) }
        },
        onSelectTint = { chosen ->
            scope.launch { preferences.setInfoBackdropTint(chosen) }
        },
        onReset = {
            scope.launch {
                preferences.setInfoBackdropSource(InfoBackdropSource.DEFAULT)
                preferences.setInfoBackdropBlur(InfoBackdropBlur.DEFAULT)
                preferences.setInfoBackdropTint(InfoBackdropTint.DEFAULT)
            }
        },
        onClose = onClose,
        modifier = Modifier.fillMaxSize()
    )
}