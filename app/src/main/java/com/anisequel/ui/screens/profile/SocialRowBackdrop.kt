package com.anisequel.ui.screens.profile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * One person's own AniList banner, drawn behind a row that names them.
 *
 * AniList gives every account a cover image and the social lists already know
 * whose row they are drawing, but the rows used to show an avatar on the app's
 * surface and nothing else - forty near-identical pills of text, where the thing
 * each person chose to hang behind their own profile is the fastest way to find
 * them. The reference for this is the site's own follower list.
 *
 * The scrim is the app's surface colour rather than black: the text over it keeps
 * its theme role instead of being pinned to white, and a reader on a light theme
 * does not get a dark slab in the middle of a list. It is weighted towards the
 * leading edge, where the avatar and the name sit, so the artwork stays the most
 * visible thing at the end of the row a reader is scanning past.
 *
 * A [BoxScope] extension because `matchParentSize` is: the backdrop has to take
 * the row's measured size without influencing it, which is exactly what that
 * modifier exists for and `fillMaxSize` does not do.
 */
@Composable
internal fun BoxScope.PersonBannerBackdrop(
    bannerUrl: String?,
    context: Context = LocalContext.current
) {
    val url = bannerUrl?.takeIf { it.isNotBlank() } ?: return
    AsyncImage(
        // Remembered rather than built inline: Coil compares requests by value, so
        // a fresh instance on every recomposition is a cache lookup that can never
        // hit - and a row recomposes on press, on scroll and when the list pages.
        model = remember(url, context) {
            ImageRequest.Builder(context)
                .data(url)
                .size(ROW_BANNER_DECODE_PX)
                .crossfade(true)
                .build()
        },
        contentDescription = null,
        modifier = Modifier.matchParentSize(),
        contentScale = ContentScale.Crop
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.62f),
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.42f)
                    )
                )
            )
    )
}

/**
 * Rows are wide and short, so a full-width banner is decoded at the width it can
 * ever be drawn at rather than at the site's 1920px original.
 */
private const val ROW_BANNER_DECODE_PX = 400
