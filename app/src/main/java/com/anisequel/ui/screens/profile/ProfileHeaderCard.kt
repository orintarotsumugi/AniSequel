package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.PannableBackdrop
import com.anisequel.ui.components.SavedCopyPill
import com.anisequel.domain.profile.ProfileBannerAnchor
import com.anisequel.domain.profile.ProfileBannerShape
import com.anisequel.domain.profile.ProfileBannerSizing
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.viewmodel.UserOverviewUiState

/**
 * The banner, avatar, name, core stats and account join/update dates.
 *
 * ## What changed, and why
 *
 * The banner was 180dp with a scrim that faded to the page colour across its
 * whole height, so the artwork it exists to show was mostly hidden behind the
 * gradient meant to keep text legible over it. It became a fixed-height hero with
 * the scrim weighted to the bottom two-thirds: the top of the image reads as art,
 * the bottom reads as a surface for the identity line, and the two meet without
 * the hard band a single-stop gradient leaves.
 *
 * That fixed height is gone, and this paragraph used to defend it. It was 260, then
 * 320, and both numbers were right for the fraction of banners that happened to
 * share their ratio - which is the one thing a banner's owner never controls. The
 * height now comes from the picture (see [Banner] and [ProfileBannerShape]), and
 * what is left here is the rule: the banner is the reader's own artwork, so the
 * page makes room for it instead of cropping it to a shape the page chose.
 *
 * The avatar sits on the banner's bottom edge as a rounded squircle with a
 * surface ring *and* a hairline outline - the ring alone is invisible on a dark
 * banner with a dark theme, which is how the avatar used to lose its edge. The
 * join/update dates moved from plain rows into the same capsule treatment the
 * rest of the app uses for metadata, so the header has one visual language rather
 * than two.
 *
 * The two stat tiles keep their prominence but gain a coloured icon chip, so
 * Anime and Manga are distinguishable at a glance without reading the labels.
 */
@Composable
fun ProfileHeaderCard(
    state: UserOverviewUiState.Success,
    modifier: Modifier = Modifier,
    maxWidth: Dp = ProfileMaxContentWidth,
    /** How tall the banner is; see [ProfileBannerShape]. */
    bannerShape: ProfileBannerShape = ProfileBannerShape.DEFAULT,
    /** Which side of a wider-than-the-card banner the header keeps; see [ProfileBannerAnchor]. */
    bannerAnchor: ProfileBannerAnchor = ProfileBannerAnchor.DEFAULT,
    /** Sweep the banner slowly sideways instead of holding one crop. */
    bannerDrift: Boolean = false
) {
    val context = LocalContext.current
    // One request for one portrait, built once per URL rather than on every
    // recomposition.
    //
    // The header is recomposed by the banner's height animation, by the saved-copy
    // pill arriving and by every state publish, and a `model = "<url>"` hands Coil
    // a new request each time. It also had no opinion about the disk cache: a
    // portrait is the same picture on every open, and the entry has to outlive the
    // process to make a revisit free. The URL carries AniList's content hash, so a
    // changed portrait is a new key and stale bytes are unrepresentable.
    val avatarRequest = remember(state.avatarUrl) {
        state.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .diskCacheKey("profile-avatar:$url")
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
        }
    }

    Column(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .testTag("profile_header")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Room for the half of the avatar that hangs below the banner, and
                // a little air before the username.
                .padding(bottom = 46.dp)
        ) {
            Banner(
                state = state,
                shape = bannerShape,
                anchor = bannerAnchor,
                drift = bannerDrift
            )

            AsyncImage(
                model = avatarRequest,
                contentDescription = "${state.username}'s Avatar",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // Nudged down so the avatar sits *on* the banner's bottom edge
                    // rather than flush inside it - the overlap is what ties the
                    // portrait to the art behind it instead of reading as two
                    // stacked blocks.
                    .offset(y = 22.dp)
                    .padding(start = 16.dp)
                    .size(92.dp)
                    // Not on the corner scale, and deliberately so.
                    //
                    // The sweep that put every surface radius onto
                    // `ExpressiveShapes` walked this too and moved the avatar from
                    // 24dp to the scale's 28dp. That is the wrong kind of change:
                    // a portrait is not a card, and its radius is a proportion of
                    // its own 92dp box (24/92) rather than a step on a family. At
                    // 28 it reads as a different shape sitting on the banner
                    // rather than the same one sitting on it.
                    .clip(RoundedCornerShape(ExpressiveShapes.PillCorner))
                    .border(4.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(ExpressiveShapes.PillCorner))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(ExpressiveShapes.PillCorner))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(AppVectorIcons.SequelJump),
                error = rememberVectorPainter(AppVectorIcons.SequelJump),
                fallback = rememberVectorPainter(AppVectorIcons.SequelJump)
            )
        }

        // The banner is the page's first surface and it bleeds to both edges,
        // exactly as the reference clients draw one: a 16dp gutter around a
        // picture the reader chose made it a card among cards. Everything that
        // identifies the account stays on the 16dp grid below it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = state.username,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Under the name rather than over the banner: it annotates the account,
        // not the artwork, and the banner is the one place on this page with
        // enough colour already. Always composed (and zero-height when hidden) so
        // a refresh that lands can animate it away instead of snapping the header
        // below it up by a row.
        SavedCopyPill(
            visible = state.isCached,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        IdentityMetaRow(state = state)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeaderStat(
                label = "Anime",
                value = state.animeCount.toString(),
                icon = AppVectorIcons.Tv,
                modifier = Modifier.weight(1f)
            )
            HeaderStat(
                label = "Manga",
                value = state.mangaCount.toString(),
                icon = AppVectorIcons.SourceBook,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        HeaderSummaryStrip(state = state)
        }
    }
}

/**
 * The three derived numbers: episodes, time watched and mean score.
 *
 * Deliberately a strip rather than three more tiles. The two tiles above answer
 * "how many lists", which is a fact about the account; these answer "how much
 * did it amount to", which is a fact about the person's time - so they are one
 * quieter row rather than three more boxes competing with the head of the page.
 * The values go through the same formatters the Stats tab uses, so a header that
 * says "1d 5h" and a chart that says "1d 5h" are the same computation.
 */
@Composable
private fun HeaderSummaryStrip(state: UserOverviewUiState.Success) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryCell(
                value = state.episodesWatched.toString(),
                label = "Episodes",
                modifier = Modifier.weight(1f)
            )
            SummaryDivider()
            SummaryCell(
                value = formatWatchTime(state.minutesWatched),
                label = "Time",
                modifier = Modifier.weight(1f)
            )
            SummaryDivider()
            SummaryCell(
                value = state.meanScore?.takeIf { it > 0.0 }?.let { formatMeanScore(it) } ?: "-",
                label = "Mean Score",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * The banner artwork with its scrim, sized to the picture it is drawing.
 *
 * ## Why the height is measured rather than a constant
 *
 * It was one fixed height for every profile, which is right for exactly the
 * pictures that happen to share its ratio. AniList hands over whatever its owner
 * uploaded - a 16:9 key visual, a 4:1 panorama, a vertical illustration - and a
 * fixed box crops the first two to their middle band and the third to a slice. The
 * banner now asks [ProfileBannerSizing] how tall to be, and the answer is the
 * picture's own ratio within the two clamps documented there.
 *
 * The measurement comes from [PannableBackdrop] (`onImageSize`), because that is
 * the component holding the decoded painter. Until it arrives - and for the
 * gradient fallback, which has no picture at all - the height is the landscape
 * ratio, so the first frame is a header rather than a collapsed strip, and the
 * settle from that to a portrait banner is the first of the header's two
 * animations.
 *
 * The other is the crop. A banner wider than the card is cropped by the cover fit,
 * and which part survives used to be decided by the transform's centre origin -
 * the reader's choice in the layout sheet now moves it (see [ProfileBannerAnchor]),
 * on [ExpressiveMotion.SettleSpatial] so the artwork slides rather than jumps.
 */
@Composable
private fun Banner(
    state: UserOverviewUiState.Success,
    shape: ProfileBannerShape,
    anchor: ProfileBannerAnchor,
    drift: Boolean,
    modifier: Modifier = Modifier
) {
    val banner = state.bannerUrl?.takeIf { it.isNotBlank() }
    // Keyed on the URL: a different picture is a different measurement, and reusing
    // the last one drew a portrait banner at the previous landscape height for the
    // frame before its own size landed.
    //
    // The remembered size is the second half of the same idea. The measurement only
    // arrives with the decoded picture, so without it every open - including one
    // whose banner was already on disk - laid the header out at the landscape
    // placeholder and animated to the real height a frame later. A revisit should
    // not have to re-measure a picture it has drawn before.
    var imageSize by remember(banner) {
        mutableStateOf(BannerAspectMemory.of(banner) ?: Size.Zero)
    }
    val context = LocalContext.current
    // Same reasoning as the avatar's request: one stable object per URL, and an
    // explicit disk key so the artwork survives the process. A 5MB animated banner
    // is exactly the download a profile should never repeat.
    val bannerRequest = remember(banner) {
        banner?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .diskCacheKey("profile-banner:$url")
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val aspect = ProfileBannerSizing.aspectFor(
            shape = shape,
            imageWidth = imageSize.width,
            imageHeight = imageSize.height
        )
        val targetHeight = ProfileBannerSizing.heightDp(maxWidth.value, aspect).dp
        // Spring physics, and `FastSpatialDp` rather than a literal: this is a
        // layout adaptation the reader did not ask for, so it has to feel like the
        // picture settling into place, not like a settings control responding.
        val height by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = ExpressiveMotion.FastSpatialDp,
            label = "profile_banner_height"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(
                    RoundedCornerShape(
                        bottomStart = ExpressiveShapes.LargeCorner,
                        bottomEnd = ExpressiveShapes.LargeCorner
                    )
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            // The themed base is drawn for *every* profile, not only the ones with no
            // banner URL.
            //
            // Two kinds of account used to fall through to nothing: one with no banner
            // at all, and - the case a `if (banner != null)` cannot see - one whose
            // banner URL exists but will not load. The second left a flat
            // `surfaceContainerHigh` rectangle where the picture should be, which looks
            // less like "no artwork" than like a broken image. Painting the gradient and
            // the sparkle underneath means a failed load reveals a designed header, and
            // a successful one covers it completely.
            BannerFallback()

            if (bannerRequest != null) {
                // The anchor is animated rather than snapped: the picture is the
                // page's largest thing, and a crop that jumps between three
                // positions reads as a broken layout, while a slide reads as the
                // artwork being moved - which is what the setting does.
                //
                // Read in the draw phase by the backdrop (see its `anchor`), so this
                // spring never recomposes the header while it runs.
                val anchorFraction = animateFloatAsState(
                    targetValue = anchor.fraction,
                    animationSpec = ExpressiveMotion.SettleSpatial,
                    label = "profile_banner_anchor"
                )
                PannableBackdrop(
                    model = bannerRequest,
                    contentDescription = "Profile Banner",
                    drift = drift,
                    // The sweep owns the horizontal position while it runs: holding
                    // a side at the same time would push the sweep's centre off to
                    // one edge and it would spend half of every period clamped
                    // there. Turning drift off restores the side, already settled.
                    anchor = { if (drift) 0.5f else anchorFraction.value },
                    onImageSize = { measured ->
                        imageSize = measured
                        BannerAspectMemory.remember(banner, measured)
                    }
                )
                // Weighted toward the bottom so the artwork stays visible at the top
                // while the identity line below has a surface to sit on.
                //
                // The stops are `Copy` of black at a *low* alpha above the midpoint
                // rather than the previous 0.10/0.28: a scrim that darkens a banner the
                // reader can now drag sideways darkens whichever part of the picture is
                // currently on screen, so a heavy one darkens different artwork
                // depending on where they stopped scrolling.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.06f),
                                0.40f to Color.Transparent,
                                0.72f to Color.Black.copy(alpha = 0.22f),
                                1.0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                            )
                        )
                )
            }
        }
    }
}

/**
 * The header's artwork when there is no picture to show.
 *
 * A gradient from the reader's own containers plus the sparkle mark, which is the
 * same language the empty states use. It sits *behind* the banner as well as in
 * place of it, so a broken URL does not punch a hole in the page.
 */
@Composable
private fun BannerFallback() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.90f),
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppVectorIcons.AnimeSparkle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            modifier = Modifier.size(52.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityMetaRow(state: UserOverviewUiState.Success) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetaChip(
            icon = AppVectorIcons.Star,
            text = if (state.isDemoMode) "AniSequel Demo Profile" else "AniList Member",
            emphasised = true
        )
        state.updatedAt?.let {
            MetaChip(
                icon = AppVectorIcons.Schedule,
                text = "Updated ${formatRelativeSeconds(it)}"
            )
        }
        state.createdAt?.let {
            MetaChip(
                icon = AppVectorIcons.Calendar,
                text = "Joined ${formatJoinYear(it)}"
            )
        }
    }
}

/**
 * One piece of header metadata as a capsule.
 *
 * The emphasised variant is the membership line, tinted with `primary` so the
 * identity reads before the dates - the same hierarchy the old stacked rows had,
 * without the two lines of vertical space they cost.
 */
@Composable
private fun MetaChip(
    icon: ImageVector,
    text: String,
    emphasised: Boolean = false
) {
    val container = if (emphasised) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (emphasised) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeaderStat(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

/**
 * The last measured size of every banner this process has drawn, keyed by URL.
 *
 * [Banner] sizes the header from the picture itself, and the picture's dimensions
 * are only known once it has been decoded - so the first frame of every open used
 * the landscape placeholder and the header settled into its real height a moment
 * later. That animation is right for a banner the reader has never seen; it is
 * noise on the tenth visit to a profile whose banner has been on disk since the
 * first, and it is the "header jumps on open" the measurement exists to avoid.
 *
 * Bounded and in-memory on purpose: this is a layout hint, not cache worth a file.
 * Twenty-four entries are more banners than a session moves between, and a wrong
 * or missing hint costs exactly one animation.
 */
internal object BannerAspectMemory {

    internal const val MAX_ENTRIES = 24

    private val sizes = object : LinkedHashMap<String, Size>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Size>?): Boolean =
            size > MAX_ENTRIES
    }

    /** The size measured for [url], or null if this process has never seen it. */
    @Synchronized
    fun of(url: String?): Size? = url?.let(sizes::get)

    /**
     * Records what [url] measured.
     *
     * An unusable measurement is not stored: `PannableBackdrop` reports `Size.Zero`
     * for a banner whose intrinsic size it could not read (an SVG, some animated
     * formats), and remembering that would size the header to the placeholder
     * forever.
     */
    @Synchronized
    fun remember(url: String?, size: Size) {
        if (url == null) return
        if (size.width.isNaN() || size.height.isNaN()) return
        if (size.width <= 0f || size.height <= 0f) return
        sizes[url] = size
    }

    /** Empties the memory. Tests only. */
    @Synchronized
    internal fun clear() {
        sizes.clear()
    }
}
