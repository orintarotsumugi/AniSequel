package com.anisequel.ui.components.seasonal

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import com.anisequel.domain.seasonal.SeasonalEffect

/**
 * How each shape is posed on a Settings chip, and the chip that reads that pose.
 *
 * ## What the glyph used to be
 *
 * [SeasonalEffectGlyph] drew one frame of the sky squeezed into eighteen dp: radius
 * `0.32` of the chip's shorter side, rotation zero, variant zero. Three failures came out
 * of that, and all three arrived as "the shape icons are smudges":
 *
 *  - **The winter row was one flake, three times.** Snow, Powder, Crystal and Needle share
 *    one dispatcher and pick their drawing from `variant`, which the glyph pinned to zero:
 *    Snow drew its stellar dendrite and Crystal drew its near-identical stellar dendrite,
 *    two chips apart, so the page asked the reader to tell four shapes apart from the
 *    words beside them. Needle at zero was a single sliver and Powder a grain, which is the
 *    only reason the row was not four copies of one picture.
 *  - **One fraction for ten reaches.** A powder grain reaches 1.3 radii and a seed's halo
 *    1.28, while a blossom reaches 0.95, so a shared fraction had to fit the worst case and
 *    drew everything else smaller than the chip allowed.
 *  - **The five-pixel detail gate.** Every shape drops its branches, veins and barbs at or
 *    below a radius of five pixels. `0.32` put an eighteen dp chip within two pixels of that
 *    line, so one density step down - or one smaller chip - and the fern became six bare
 *    spokes, the fan lost its ribs and the seed lost its crown. The smallest fraction here
 *    moves the line from a sixteen-px chip to a fourteen-px one, and mdpi - the low end this
 *    app installs on - gives an eighteen dp box eighteen pixels, comfortably over it.
 *
 * ## Why the legend is its own layer rather than a second set of icons
 *
 * A chip is a pictogram whose job is to be named; the sky's job is to look like weather.
 * The layer rolls a variant per particle and turns it over as it falls, and that
 * randomness is pinned by `SeasonalEffectTest` - a legend wants the opposite: one canonical
 * drawing per shape, the same posture every time, the detailed ink at a size that clears
 * the gate. Both still call [drawSeasonalParticle], which is the invariant that stops a
 * chip promising a shape the sky does not draw; this file chooses *which* drawing and *how
 * it is posed* and adds nothing to the ink. Splitting it out also keeps
 * `SeasonalParticleShapes.kt` inside the file-size ceiling that the pose table alone would
 * have pushed past.
 *
 * The squeeze is deliberately not passed here: the glyph was never edge-on. What it lacked
 * was reach, posture and a floor for its thinnest lines.
 */

/** The floor for a shape whose ink *is* thin lines: a flake's arms, a seed's filaments. */
private const val ARM_STROKE_FLOOR_PX = 1.05f

/** The floor for a shape whose body is solid: a petal, a leaf, a clover. */
private const val SOLID_STROKE_FLOOR_PX = 0.85f

/** One shape's pose on a chip: which of its drawings, how big, how tilted, how thick. */
internal data class GlyphLegend(
    /**
     * Radius as a fraction of the chip's shorter side, fitted to that shape's own reach.
     *
     * Kept under the half-side so nothing spills out of the box the chip hands the canvas:
     * each value is close to `0.47 / reach` and never more, where reach is the farthest ink
     * from the centre in radii - a petiole at 1.2, a grain's halo at 1.3, a blossom at 0.95.
     * Rotation about the centre cannot break that, whatever the tilt.
     */
    val radiusFraction: Float,
    /** Which of the shape's [SeasonalEffect.variantCount] drawings the chip shows. */
    val variant: Int,
    /**
     * A fixed tilt in degrees, so ten chips are ten pictures.
     *
     * Small on purpose: six-armed flakes alias every sixty degrees and a five-petal
     * blossom every seventy-two, so a tilt past thirty would restart the sameness it is
     * here to break, and a big one would read as the sky's tumble rather than as a logo.
     */
    val rotationDegrees: Float,
    /**
     * The thinnest line the chip will emit, in device pixels.
     *
     * [drawSeasonalParticle] caps it at six percent of the radius, so a floor can rescue a
     * hairline on a dense screen without fusing twelve filaments into one fan on a weak one.
     */
    val strokeFloor: Float
)

/**
 * The legend for one shape.
 *
 * Each `variant` is the drawing that best *names* the shape at chip size, which is not
 * always the one its blurb leads with - the mixed snow sky opens on a dendrite, but a chip
 * showing a dendrite next to the Crystal chip's dendrite names nothing at all. Where a shape
 * has several palettes the variant also picks the readable end of them, because a chip is the
 * one place a near-white flake has to stand on a light surface with no neighbours to help it.
 */
internal fun seasonalGlyphLegend(effect: SeasonalEffect): GlyphLegend = when (effect) {
    // A plate: the mixed sky's flattest, most hexagonal flake, on the darkest of its five
    // palettes. Giving Snow the plate and Crystal the dendrite is what separates the two
    // chips that used to be the same drawing.
    SeasonalEffect.SNOW -> GlyphLegend(
        radiusFraction = 0.40f,
        variant = 2,
        rotationDegrees = -8f,
        strokeFloor = ARM_STROKE_FLOOR_PX
    )
    // The grain, at the middle of its three sizes: the big soft halo is part of the drawing
    // and has to stay inside the chip, so powder is the smallest legend here on purpose.
    SeasonalEffect.POWDER -> GlyphLegend(
        radiusFraction = 0.36f,
        variant = 0,
        rotationDegrees = 0f,
        strokeFloor = ARM_STROKE_FLOOR_PX
    )
    // The dense dendrite, which is what "Crystal" claims, and the tilt is there so it cannot
    // be mistaken for the plate two chips along the same winter row.
    SeasonalEffect.CRYSTAL -> GlyphLegend(
        radiusFraction = 0.40f,
        variant = 0,
        rotationDegrees = 14f,
        strokeFloor = ARM_STROKE_FLOOR_PX
    )
    // Three columns bound together rather than one sliver: the blurb promises columns, and
    // a single hairline is the least legible thing in the app at eighteen dp.
    SeasonalEffect.NEEDLE -> GlyphLegend(
        radiusFraction = 0.40f,
        variant = 1,
        rotationDegrees = -26f,
        strokeFloor = ARM_STROKE_FLOOR_PX
    )
    // The blossom, not a petal: variant two is the deepest pink of the sakura's three
    // palettes and the drawing the blurb leads with, and five petals read as a flower where a
    // single petal at chip size reads as a scrap of confetti.
    SeasonalEffect.SAKURA -> GlyphLegend(
        radiusFraction = 0.45f,
        variant = 2,
        rotationDegrees = 18f,
        strokeFloor = SOLID_STROKE_FLOOR_PX
    )
    // Four leaves. The three-leaf variant exists to make the four feel like a find in the
    // sky; on a chip it is just a shape with a gap in it.
    SeasonalEffect.CLOVER -> GlyphLegend(
        radiusFraction = 0.42f,
        variant = 0,
        rotationDegrees = -14f,
        strokeFloor = SOLID_STROKE_FLOOR_PX
    )
    // The most-cut leaf of the maple's three, in the reddest of its palettes: notch depth is
    // what separates a maple from an oak once both are only a chip wide.
    SeasonalEffect.MAPLE -> GlyphLegend(
        radiusFraction = 0.40f,
        variant = 2,
        rotationDegrees = 22f,
        strokeFloor = SOLID_STROKE_FLOOR_PX
    )
    // Five lobes at the widest spread. Seven lobes is the prettier oak in the sky and a row
    // of one-pixel bumps on a chip, so the legend takes the chunkiest margin the shape has.
    SeasonalEffect.OAK -> GlyphLegend(
        radiusFraction = 0.37f,
        variant = 1,
        rotationDegrees = -20f,
        strokeFloor = SOLID_STROKE_FLOOR_PX
    )
    // The deeper of the two notches - 0.74 of the radius rather than 0.62 - because the
    // split fan is what says ginkgo, and the ribs and the margin differ by under a pixel
    // between the variants while the notch does not.
    SeasonalEffect.GINKGO -> GlyphLegend(
        radiusFraction = 0.40f,
        variant = 1,
        rotationDegrees = 10f,
        strokeFloor = SOLID_STROKE_FLOOR_PX
    )
    // The twelve-hair crown rather than fourteen: the hairs are the shape, and the floor
    // thickens them, so the count that leaves air between them is the one the chip wants.
    SeasonalEffect.DANDELION -> GlyphLegend(
        radiusFraction = 0.37f,
        variant = 0,
        rotationDegrees = 6f,
        strokeFloor = ARM_STROKE_FLOOR_PX
    )
}

/**
 * A chip-sized sample of one shape, used instead of emoji on the shape chips.
 *
 * The `Path` is remembered for the life of the chip rather than built in the draw lambda:
 * the shape code resets whatever path it is handed, so one instance is enough, and the
 * Settings page draws a glyph per chip. A fresh path per draw was small, but it was per-chip
 * and per-frame on a page that is nothing but chips.
 */
@Composable
fun SeasonalEffectGlyph(effect: SeasonalEffect, modifier: Modifier = Modifier) {
    val legend = seasonalGlyphLegend(effect)
    val path = remember { Path() }
    Canvas(modifier = modifier) {
        drawSeasonalParticle(
            effect = effect,
            center = center,
            radius = size.minDimension * legend.radiusFraction,
            rotationDegrees = legend.rotationDegrees,
            alpha = 1f,
            variant = legend.variant,
            path = path,
            strokeFloor = legend.strokeFloor
        )
    }
}
