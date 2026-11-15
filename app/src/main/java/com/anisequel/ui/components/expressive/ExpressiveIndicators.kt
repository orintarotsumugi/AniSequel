package com.anisequel.ui.components.expressive

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private const val EXPRESSIVE_TAG = "expressive_"

/**
 * The morphing indeterminate loader adhering to Material 3 Expressive standards.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    LoadingIndicator(
        modifier = modifier.testTag("${EXPRESSIVE_TAG}loading_indicator"),
        color = color
    )
}

/**
 * The contained morphing loader for buttons and busy states needing a container backdrop.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    indicatorColor: Color = MaterialTheme.colorScheme.primary
) {
    ContainedLoadingIndicator(
        modifier = modifier.testTag("${EXPRESSIVE_TAG}contained_loading_indicator"),
        containerColor = containerColor,
        indicatorColor = indicatorColor
    )
}

/**
 * The card-sized wave: a thin determinate track drawn by the Material 3
 * component, for a sequel row's episode progress.
 *
 * Not the title page's wave. `ui/components/detail/info/WavyProgressBar` has the
 * same name and is hand-drawn, because on that page the wave's *height* is the
 * progress; this one is the ordinary thin track and lets `LinearWavyProgressIndicator`
 * draw it. Two spellings of one name is a trap for the reader who finds the wrong
 * file first, so: if the caller is a card and the progress is 0..1 of a bar, it is
 * this one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    LinearWavyProgressIndicator(
        progress = progress,
        // The default sits *inside* the caller's modifier and is expressed as a
        // minimum rather than a fixed height, so a caller that sizes the bar keeps
        // its size: a `.height(6.dp)` outside it fixes the row at 6dp, while a
        // caller that passes no size still gets the 10dp the wave needs to be
        // legible. The old chain was `.height(10.dp).then(modifier)` - a fixed
        // default in *front* of the caller - and an outer size constraint is the
        // one that wins, so the 6dp sequel row silently rendered at 10dp.
        modifier = modifier
            .heightIn(min = 10.dp)
            .testTag("${EXPRESSIVE_TAG}wavy_progress"),
        color = color,
        trackColor = trackColor
    )
}
