package com.anisequel.domain.seasonal

import kotlin.math.roundToInt

/**
 * The ceiling the reader puts on how fast the sky may step.
 *
 * ## Why this is not folded into [SeasonalQuality]
 *
 * Quality answers *how much* is drawn - how many flakes, how detailed, how dense
 * the drift. This answers *how often*, and the two are independent choices a
 * reader makes for different reasons: someone with a 120Hz phone who wants a full
 * sky still may want it stepping at thirty because the frame time is what
 * competes with their scrolling, and someone on a 60Hz panel cannot be given 120
 * however high-end their chipset is.
 *
 * [AUTO] is the shipped answer and the honest one: it follows the quality tier,
 * which is what every install did before this setting existed.
 */
enum class SeasonalFrameRate(
    val storageValue: String,
    val displayName: String,
    val blurb: String,
    /** Requested steps per second, or null to take the tier's own rate. */
    val requestedHz: Int?
) {
    AUTO(
        storageValue = "auto",
        displayName = "Auto",
        blurb = "Whatever the quality tier asks for.",
        requestedHz = null
    ),
    THIRTY(
        storageValue = "30",
        displayName = "30 Hz",
        blurb = "Half the frame budget; the drift moves in larger, rarer steps.",
        requestedHz = 30
    ),
    SIXTY(
        storageValue = "60",
        displayName = "60 Hz",
        blurb = "The rate every phone screen can reach.",
        requestedHz = 60
    ),
    ONE_TWENTY(
        storageValue = "120",
        displayName = "120 Hz",
        blurb = "As fast as the screen will go. Needs a 120Hz panel to mean anything.",
        requestedHz = 120
    ),
    ONE_FORTY_FOUR(
        storageValue = "144",
        displayName = "144 Hz",
        blurb = "The ceiling on gaming panels. Hidden unless this screen reports one.",
        requestedHz = 144
    );

    companion object {
        val DEFAULT = AUTO

        fun fromStorage(value: String?): SeasonalFrameRate =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/** Slowest the sky may step. Below this the fall reads as a slideshow. */
const val MIN_SEASONAL_FRAME_HZ = 24

/** Fastest the sky may step, and the rate the frame loop is allowed to ask for. */
const val MAX_SEASONAL_FRAME_HZ = 144

/** The rate Auto draws at when nothing about the device is known. */
const val DEFAULT_SEASONAL_FRAME_HZ = 60

/**
 * The rate the sky will actually step at, on this choice, this tier and this screen.
 *
 * [deviceHz] is the panel's reported refresh rate. Android names the modes
 * differently from people: 59.94 and 119.88 are what everyone calls 60 and 120. A
 * zero or
 * negative reading (a context with no display, a stub in a test, a fold whose
 * outer screen has not been queried yet) is treated as 60 rather than as
 * "unbounded", because the failure this guards is the overlay asking a 60Hz panel
 * for 120 frames it will never get and spending the difference on a loop that
 * never lands.
 *
 * The cap is then the *lower* of what was asked and what the panel can give, so
 * 120 Hz on a 60Hz phone is 60 and 120 Hz on a 120Hz phone is 120. The floor is
 * [MIN_SEASONAL_FRAME_HZ] so a panel reporting something exotic cannot pace the
 * sky below a crawl.
 */
fun seasonalFrameHz(
    rate: SeasonalFrameRate,
    tierHz: Int,
    deviceHz: Float
): Int {
    val panel = if (deviceHz <= 0f) {
        DEFAULT_SEASONAL_FRAME_HZ
    } else {
        // Rounded, not truncated: 119.88 is a 120 Hz panel, and a 120 Hz budget
        // paced against 119.88 skips a step every second and a half.
        deviceHz.roundToInt()
    }.coerceAtLeast(MIN_SEASONAL_FRAME_HZ)
    val asked = rate.requestedHz ?: tierHz
    return asked.coerceIn(MIN_SEASONAL_FRAME_HZ, MAX_SEASONAL_FRAME_HZ)
        .coerceAtMost(panel)
}

/**
 * The rates this screen can actually be run at, as the chips the reader is offered.
 *
 * A 60Hz phone showing a 120 Hz pill is a promise the app cannot keep, and the
 * pill would resolve to 60 anyway - so the choice list is built from the panel.
 * The tolerance is not sloppiness: a mode reported as 119.81 is a 120Hz mode and
 * a comparison against an exact 120 would hide the pill on every phone in the
 * shop. [currentHz] is folded in because `supportedRefreshRates` is empty on some
 * ROMs and on a context with no display, and the *active* rate is always known.
 *
 * [SeasonalFrameRate.AUTO] and the caps *below* the panel are offered whatever the
 * screen reports: a cap is a reduction, and 30 Hz on a 144 Hz panel is a choice a
 * reader is entitled to make to get their battery back.
 */
fun availableSeasonalFrameRates(currentHz: Float, supportedHz: List<Float>): List<SeasonalFrameRate> {
    val best = (supportedHz + listOf(currentHz)).maxOrNull() ?: 0f
    val panel = if (best <= 0f) DEFAULT_SEASONAL_FRAME_HZ.toFloat() else best
    return SeasonalFrameRate.entries.filter { rate ->
        val ceiling = rate.requestedHz ?: return@filter true
        panel >= ceiling - PANEL_RATE_TOLERANCE
    }
}

/** Panels report 59.94 and 119.81; a name is close enough when it is within this. */
private const val PANEL_RATE_TOLERANCE = 0.5f
