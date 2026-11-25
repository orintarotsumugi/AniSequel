package com.anisequel.ui.components.detail.info

import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.data.repository.InfoBackdropTint

/**
 * Draws whichever Info page style the reader selected.
 *
 * The two pages take the same [InfoPageArgs]; only their internal composition
 * differs. Keeping the choice here means the sheet builds one argument value
 * instead of repeating it into two twenty-parameter calls, which is how the
 * styles drifted apart before.
 */
@Composable
fun InfoPageHost(
    useNova: Boolean,
    args: InfoPageArgs,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    bottomContentPadding: Dp = 24.dp,
    onOpenAppearance: (() -> Unit)? = null,
    infoBackdropSource: InfoBackdropSource = InfoBackdropSource.DEFAULT,
    infoBackdropBlur: InfoBackdropBlur = InfoBackdropBlur.DEFAULT,
    infoBackdropTint: InfoBackdropTint = InfoBackdropTint.DEFAULT
) {
    if (useNova) {
        NovaInfoPage(
            args = args,
            modifier = modifier,
            listState = listState,
            bottomContentPadding = bottomContentPadding,
            onOpenAppearance = onOpenAppearance,
            infoBackdropSource = infoBackdropSource,
            infoBackdropBlur = infoBackdropBlur,
            infoBackdropTint = infoBackdropTint
        )
    } else {
        AnimeInfoPage(
            args = args,
            modifier = modifier,
            listState = listState,
            bottomContentPadding = bottomContentPadding,
            onOpenAppearance = onOpenAppearance,
            infoBackdropSource = infoBackdropSource,
            infoBackdropBlur = infoBackdropBlur,
            infoBackdropTint = infoBackdropTint
        )
    }
}
