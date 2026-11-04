package com.anisequel.ui.components.seasonal

import androidx.compose.ui.graphics.Color
import com.anisequel.domain.seasonal.SeasonalEffect

/**
 * The ink each shape is drawn with.
 *
 * ## Why the colours are fixed rather than theme roles
 *
 * A snowflake tinted `onSurface` is black on the light theme and white on the
 * dark one, which reads as debris rather than as snow, and a seasonal effect that
 * changes colour with the wallpaper is no longer the season the reader picked.
 * Fixed hues also let a single shape carry a palette - a maple leaf is orange
 * with a lighter tip, not one flat orange - without a composition lookup per
 * particle per frame.
 *
 * Every palette has three roles: the body, a highlight for the curled or lit
 * edge, and a deep tone for veins and shadow. On a light surface the lighter
 * tones are quiet; on a dark one they are the point. That is a deliberate trade
 * for decoration, not a contrast claim. The 1.8.0 palette measured below 3:1
 * against a white surface for every shape but maple; the new palettes keep that
 * trade rather than pretending it was solved, because a flake dark enough for
 * 3:1 on white is a grey smudge on a true-black page.
 */
internal class ParticlePalette(
    /** The body of the shape. */
    val base: Color,
    /** The lit edge, the inner petal, the centre of a spark. */
    val light: Color,
    /** Veins, stems, the seed at the bottom of a dandelion. */
    val deep: Color
)

/**
 * The palettes, built once.
 *
 * They used to be constructed inside [particlePalette], which allocates a
 * `ParticlePalette` per particle per frame - sixty-four objects sixty times a
 * second for values that never change. Hoisting them keeps the draw loop to
 * lookups. `Color` is an inline value class, so these lists are the only objects.
 *
 * Each list's length must equal its effect's `variantCount`, or a variant that
 * the domain generates would silently borrow another drawing's colours. The
 * counts live in `SeasonalEffect`; a new variant needs a palette here as well.
 */
private val SNOW_PALETTES = listOf(
    ParticlePalette(Color(0xFFDCEEFF), Color(0xFFFFFFFF), Color(0xFF9FCBEC)),
    ParticlePalette(Color(0xFFD6EAFB), Color(0xFFF6FCFF), Color(0xFF9CC8E8)),
    ParticlePalette(Color(0xFFBFDDF4), Color(0xFFEFF9FF), Color(0xFF87B8DE)),
    ParticlePalette(Color(0xFFE8F4FF), Color(0xFFFFFFFF), Color(0xFFA6D0EE)),
    ParticlePalette(Color(0xFFC9E4F6), Color(0xFFF4FBFF), Color(0xFF8BB9DC))
)
private val POWDER_PALETTES = listOf(
    ParticlePalette(Color(0xFFEAF5FF), Color(0xFFFFFFFF), Color(0xFFB7D5EC)),
    ParticlePalette(Color(0xFFD9ECFA), Color(0xFFF8FCFF), Color(0xFFA4C8E4)),
    ParticlePalette(Color(0xFFF4FAFF), Color(0xFFFFFFFF), Color(0xFFC2DCEC))
)
private val CRYSTAL_PALETTES = listOf(
    ParticlePalette(Color(0xFFD4ECFF), Color(0xFFFFFFFF), Color(0xFF7FB4DC)),
    ParticlePalette(Color(0xFFE2F3FF), Color(0xFFF7FCFF), Color(0xFF8EC0E6)),
    ParticlePalette(Color(0xFFC5E2F8), Color(0xFFEEF8FF), Color(0xFF6EA6D0))
)
private val NEEDLE_PALETTES = listOf(
    ParticlePalette(Color(0xFFD8EEFB), Color(0xFFFFFFFF), Color(0xFF94C2E0)),
    ParticlePalette(Color(0xFFC7E4F4), Color(0xFFF3FAFF), Color(0xFF7EAFD2))
)
private val SAKURA_PALETTES = listOf(
    ParticlePalette(Color(0xFFF2A2C2), Color(0xFFFFE0EC), Color(0xFFCC7097)),
    ParticlePalette(Color(0xFFF7B7CE), Color(0xFFFFEAF2), Color(0xFFD982A9)),
    ParticlePalette(Color(0xFFE58FB4), Color(0xFFFFD6E5), Color(0xFFB85C86))
)
private val CLOVER_PALETTES = listOf(
    ParticlePalette(Color(0xFF63B168), Color(0xFFB9E6B7), Color(0xFF356E3D)),
    ParticlePalette(Color(0xFF7CC47F), Color(0xFFD2EFC9), Color(0xFF3F8249))
)
private val DANDELION_PALETTES = listOf(
    ParticlePalette(Color(0xFFEADBB8), Color(0xFFFCF5E4), Color(0xFF7C5F3D)),
    ParticlePalette(Color(0xFFF4E9CC), Color(0xFFFFFFFF), Color(0xFF8C6E48))
)
private val MAPLE_PALETTES = listOf(
    ParticlePalette(Color(0xFFE57A34), Color(0xFFF6B86A), Color(0xFFAD4A21)),
    ParticlePalette(Color(0xFFD9662F), Color(0xFFF0A263), Color(0xFFA94422)),
    ParticlePalette(Color(0xFFC24E3C), Color(0xFFE98A63), Color(0xFF8E3526))
)
private val OAK_PALETTES = listOf(
    ParticlePalette(Color(0xFFD6A348), Color(0xFFF3D590), Color(0xFF96682A)),
    ParticlePalette(Color(0xFFC9943D), Color(0xFFF0CD81), Color(0xFF8E6428)),
    ParticlePalette(Color(0xFFB97F36), Color(0xFFE8BC6D), Color(0xFF83571F))
)
private val GINKGO_PALETTES = listOf(
    ParticlePalette(Color(0xFFEBCB58), Color(0xFFFCF2B5), Color(0xFFB79424)),
    ParticlePalette(Color(0xFFE3BC3B), Color(0xFFF8EA9B), Color(0xFFB28A1E))
)

/** The palette one variant of one shape is drawn from. */
internal fun particlePalette(effect: SeasonalEffect, variant: Int): ParticlePalette {
    val palettes = when (effect) {
        SeasonalEffect.SNOW -> SNOW_PALETTES
        SeasonalEffect.POWDER -> POWDER_PALETTES
        SeasonalEffect.CRYSTAL -> CRYSTAL_PALETTES
        SeasonalEffect.NEEDLE -> NEEDLE_PALETTES
        SeasonalEffect.SAKURA -> SAKURA_PALETTES
        SeasonalEffect.CLOVER -> CLOVER_PALETTES
        SeasonalEffect.DANDELION -> DANDELION_PALETTES
        SeasonalEffect.MAPLE -> MAPLE_PALETTES
        SeasonalEffect.OAK -> OAK_PALETTES
        SeasonalEffect.GINKGO -> GINKGO_PALETTES
    }
    return palettes[((variant % palettes.size) + palettes.size) % palettes.size]
}
