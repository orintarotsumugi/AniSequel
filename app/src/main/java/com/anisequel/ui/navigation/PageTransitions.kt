package com.anisequel.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import com.anisequel.ui.components.expressive.ExpressiveMotion

/**
 * How one page gives way to the next.
 *
 * Split out of `AppNavigation.kt` because it changes for a different reason: that
 * file is the destination table - which routes exist, what each one is handed -
 * and this one is motion. The two only meet at `NavHost`'s transition lambdas, so
 * keeping the springs and the direction rules here means a change to how pages
 * move does not have to be read through twelve `composable()` registrations.
 *
 * Every function takes the two routes it is moving between rather than a state
 * object, because a transition lambda has nothing else to give it.
 */

/**
 * The route transitions use the app's shared motion tokens rather than their own
 * copies.
 *
 * These two were private re-declarations, forty lines apart, of the springs
 * [ExpressiveMotion] already publishes:
 *
 *  - The slide was `ExpressiveMotion.DefaultSpatialOffset` exactly - the same
 *    `INSTANT`/`SMOOTH`/`CHILL` branches, the same `NoBouncy`/`MediumLow` in
 *    `SMOOTH`, the same `0.65f` in `CHILL`. A change to how the app slides would
 *    have moved every list and press and left the pages on the old physics, with
 *    nothing to notice it but the eye.
 *  - The fade was `ExpressiveMotion.DefaultEffects` *except* that its `SMOOTH`
 *    branch was pinned to `StiffnessMediumLow` while the shared effects token uses
 *    `StiffnessMedium`. That was not a decision anyone made - it was the copy
 *    drifting - so the routes now fade at the same speed as every other fade in
 *    the app. The difference is one spring stiffness on a ~200ms fade; it is not
 *    a motion redesign, and it is the point of having one token.
 *
 * A pure slide crosses one surface over the other; the slide plus a short fade
 * gives the incoming surface something to compose against - without it, the
 * underneath content reads as the background rather than as replaced content
 * while the slide is moving. The fade is the effects token, not the spatial one,
 * for the same reason colour transitions are: it must not overshoot.
 *
 * ## The page expands as it arrives
 *
 * The transition is asymmetric, and deliberately so. The incoming page travels
 * only a quarter of a screen and springs up from 94%, while the page it covers
 * shifts a twelfth and settles back to 98%. A symmetric pair - both sliding a
 * full width, both fading - reads as two cards swapping places. This reads as the
 * new page growing out from over the old one, which is the same "expanding"
 * language the app's lists, sheets and counters already use.
 *
 * Both spatial values are shared tokens: the scale uses [ExpressiveMotion.BouncySpatial]
 * so the arrival gets a little overshoot, and the departure uses
 * [ExpressiveMotion.FastSpatial] so it does not wobble while it is being covered.
 * `MotionStyle.INSTANT` turns both into `snap()` inside those tokens, and
 * `SMOOTH` removes the overshoot without changing the shape.
 */
internal val RouteSlideSpring get() = ExpressiveMotion.DefaultSpatialOffset
internal val RouteFadeEffect get() = ExpressiveMotion.DefaultEffects

/**
 * The slide spring for a move *between two of the bar's pages*.
 *
 * A tab switch is not a push: it is a control the reader can chain faster than
 * any animation - hold, swipe twice, and two slides are requested in a few
 * frames. On [RouteSlideSpring] the first is still moving when the second
 * starts, so the reader sees a stutter instead of a switch. This is the fast
 * token for exactly that case; Settings, a studio and somebody else's profile
 * keep the calm one, because those are pushes the reader is not racing.
 */
internal val RouteTabSlideSpring get() = ExpressiveMotion.FastSpatialOffset

/**
 * The spring a page move should slide on, chosen by what kind of move it is.
 *
 * Both branches are typed `FiniteAnimationSpec<IntOffset>`, so this can be
 * passed straight to `slideIntoContainer`/`slideOutOfContainer` without a cast.
 */
internal fun pageSlideSpring(
    from: String?,
    to: String?
): FiniteAnimationSpec<IntOffset> =
    if (tabDirection(from, to) != null) RouteTabSlideSpring else RouteSlideSpring

/**
 * Which way a move between two of the bar's pages travels, or null when the move
 * is not one - a Settings page, a studio, somebody else's profile.
 *
 * The bar's order is the direction: moving to a tab further right travels left (the
 * new page comes from the right, the old one leaves to the left), and moving back
 * travels right. Before this, every forward move slid left and every pop slid right,
 * so Library → Home - a move *backwards* along the bar - looked exactly like Home →
 * Library. The direction now comes from [MainTab]'s declaration order, which is the
 * same order the bar is built from, so a tab added in the middle is placed
 * correctly without touching the transition.
 *
 * A pop is the same rule read the other way: the pair is (popped, revealed), so
 * Library → Home is already `Right` and needs no special case.
 */
internal fun tabDirection(
    from: String?,
    to: String?
): AnimatedContentTransitionScope.SlideDirection? {
    val fromTab = mainTabForRoute(from) ?: return null
    val toTab = mainTabForRoute(to) ?: return null
    if (fromTab == toTab) return null
    return if (toTab.ordinal > fromTab.ordinal) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

/**
 * The tier list arrives from below rather than from the side.
 *
 * It is a tab, but it is not a page laid out like its neighbours: a board is a
 * surface the reader drags, and the bar steps aside for it. Rising from the bottom
 * edge is what says "this is a different kind of screen" before the reader has read
 * a single word of it, and it matches the bar leaving downwards at the same moment.
 */
internal fun isTierList(route: String?): Boolean = route == AppRoutes.TIER_LIST

/**
 * Which way the incoming page travels, for a push or a pop.
 *
 * A tab switch decides its own direction from the bar's order. Everything else -
 * Settings, a studio, a scanned dashboard, somebody else's profile - used to
 * default to [AnimatedContentTransitionScope.SlideDirection.Left] on both a push
 * and a pop, because [popEnterPage] simply called [enterPage]. The result was that
 * a pop revealed the previous page from the *same* edge the pushed page had just
 * entered from: Settings slid in from the right, and Back slid the dashboard in
 * from the right too, as though the stack had grown another page rather than
 * unwound one. The pushed page's own exit already knew to leave to the right;
 * only the page underneath it disagreed, so the two halves of one gesture moved
 * in opposite directions.
 *
 * A pop is the push read backwards, so the revealed page enters from the left
 * while the popped page leaves to the right. The tab case needs no special
 * handling: its pair is already (popped, revealed), and [tabDirection] returns
 * the direction for that pair.
 */
internal fun pageEnterDirection(
    from: String?,
    to: String?,
    popped: Boolean
): AnimatedContentTransitionScope.SlideDirection =
    tabDirection(from, to) ?: if (popped) {
        AnimatedContentTransitionScope.SlideDirection.Right
    } else {
        AnimatedContentTransitionScope.SlideDirection.Left
    }

/**
 * The fade that rides with a page slide, by the kind of move it is.
 *
 * A tab switch is already on the fast offset token; pairing it with the calm
 * `DefaultEffects` fade made the incoming page still be fading in after the slide
 * had landed, and a chained tap restarted the fade mid-flight. Pushes keep the
 * calm fade - they are not raced.
 */
private fun pageFade(from: String?, to: String?): FiniteAnimationSpec<Float> =
    if (tabDirection(from, to) != null) ExpressiveMotion.FastEffects else RouteFadeEffect

/**
 * The expansion that rides with a page slide, by the kind of move it is.
 *
 * Bouncy overshoot is a celebration and belongs to a push the reader chose; on a
 * tab switch it turns every quick Home -> Library -> Home into three overlapping
 * wobbles. Tab moves get the fast, clean token and a slightly smaller travel.
 */
private fun pageScale(from: String?, to: String?): FiniteAnimationSpec<Float> =
    if (tabDirection(from, to) != null) ExpressiveMotion.FastSpatial else ExpressiveMotion.BouncySpatial

/** The scale a page starts at; tab moves travel less because they repeat. */
private fun pageInitialScale(from: String?, to: String?): Float =
    if (tabDirection(from, to) != null) 0.96f else 0.94f

/**
 * The shared arrival: a short slide, a fade and a small expansion.
 *
 * Extracted so [enterPage] and [popEnterPage] cannot drift apart again - the pop
 * used to call the push, which is exactly how the direction bug above survived.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideInPage(
    direction: AnimatedContentTransitionScope.SlideDirection,
    spring: FiniteAnimationSpec<IntOffset>,
    fade: FiniteAnimationSpec<Float> = RouteFadeEffect,
    scale: FiniteAnimationSpec<Float> = ExpressiveMotion.BouncySpatial,
    initialScale: Float = 0.94f
): EnterTransition = slideIntoContainer(
    direction,
    animationSpec = spring,
    // The incoming page starts a quarter of a page toward its entry edge instead
    // of a full width. A full-width slide is a card being turned over; a shorter
    // travel with a growing page reads as this screen expanding out of the one
    // before it, and it keeps both surfaces on screen while they cross.
    initialOffset = { fullWidth -> fullWidth / 4 }
) + fadeIn(fade) + scaleIn(
    initialScale = initialScale,
    animationSpec = scale
)

/** A page arriving over the one before it. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.enterPage(
    from: String?,
    to: String?
): EnterTransition = if (isTierList(to)) {
    slideInVertically(
        // `pageSlideSpring`, not the calm `RouteSlideSpring` this used to hardcode:
        // the board is one of the bar's pages, so arriving at it is a tab switch
        // whenever the reader tapped Tiers. The fast token is what the bar's own
        // leave motion is on (`MainChrome` hides it on that token), and the calm
        // spring is longer than a chained tab tap - the first rise would still be in
        // flight when the next move started, which is the stutter
        // `RouteTabSlideSpring` exists to remove. A push onto the board still gets
        // the calm spring, because that is what `pageSlideSpring` returns for it.
        animationSpec = pageSlideSpring(from, to),
        // A third of the page rather than all of it: enough travel to read as a
        // rise, short enough that the board's own header is not still arriving
        // when the finger gets there.
        initialOffsetY = { fullHeight -> fullHeight / 3 }
    ) + fadeIn(pageFade(from, to)) + scaleIn(
        initialScale = 0.96f,
        animationSpec = pageScale(from, to)
    )
} else {
    // `pageSlideSpring` still chooses the fast token for a tab switch and the
    // calm one for a push; the fade and the scale follow the same kind of move,
    // so one transition cannot outlive the other.
    slideInPage(
        direction = pageEnterDirection(from, to, popped = false),
        spring = pageSlideSpring(from, to),
        fade = pageFade(from, to),
        scale = pageScale(from, to),
        initialScale = pageInitialScale(from, to)
    )
}

/** A page being covered, or being popped away. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.exitPage(
    from: String?,
    to: String?,
    popped: Boolean
): ExitTransition = when {
    // The board is leaving: it falls back down the way it came, on the same spring
    // that brought it in - see [enterPage] for why that is the fast token on a tab
    // move.
    isTierList(from) -> slideOutVertically(
        animationSpec = pageSlideSpring(from, to),
        targetOffsetY = { fullHeight -> fullHeight / 3 }
    ) + fadeOut(pageFade(from, to))
    // The board is arriving over this page. It fades and settles back rather than
    // sliding sideways: the board is rising from the bottom edge and the bar is
    // leaving through it, so a third horizontal movement would give the same frame
    // two directions to read. This is the mirror of `popEnterPage`'s fade, which is
    // what the reader sees on the way back.
    isTierList(to) -> fadeOut(pageFade(from, to)) + scaleOut(
        targetScale = 0.98f,
        animationSpec = ExpressiveMotion.FastSpatial
    )
    else -> {
        val direction = pageEnterDirection(from, to, popped)
        slideOutOfContainer(
            direction,
            animationSpec = pageSlideSpring(from, to),
            // The page being covered barely moves: it is the fixed surface the new
            // one grows over, not a card sliding away.
            targetOffset = { fullWidth -> fullWidth / 12 }
        ) + fadeOut(pageFade(from, to)) + scaleOut(
            targetScale = 0.98f,
            animationSpec = ExpressiveMotion.FastSpatial
        )
    }
}

/**
 * The page a pop reveals.
 *
 * Coming back from the tier list is a fade rather than a slide: the tier list is
 * travelling downwards, and a second horizontal slide underneath it would make the
 * two movements fight over the same frame.
 *
 * Everything else is the arrival [enterPage] draws, with the pop's direction. It
 * used to call [enterPage] itself, which hardcoded the push direction - so Back
 * slid the previous page in from the right, the edge the pushed page had just
 * entered from, instead of from the left as the pushed page left to the right.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterPage(
    from: String?,
    to: String?
): EnterTransition = if (isTierList(from)) {
    fadeIn(pageFade(from, to)) + scaleIn(
        initialScale = 0.98f,
        animationSpec = ExpressiveMotion.FastSpatial
    )
} else {
    slideInPage(
        direction = pageEnterDirection(from, to, popped = true),
        spring = pageSlideSpring(from, to),
        fade = pageFade(from, to),
        scale = pageScale(from, to),
        initialScale = pageInitialScale(from, to)
    )
}
