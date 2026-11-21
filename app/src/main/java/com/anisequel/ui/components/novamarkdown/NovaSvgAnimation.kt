package com.anisequel.ui.components.novamarkdown

/**
 * Whether a card animates, and therefore whether a captured frame may stand in for it.
 *
 * ## What this is for
 *
 * A dynamic card is drawn once in a Chromium `WebView` and kept as a bitmap, because
 * a `LazyColumn` full of live renderers cannot scroll (see [NovaDynamicSvg]). For a
 * static card that trade costs nothing: the captured frame *is* the picture.
 *
 * For an animated one it costs the picture. AniList's stat cards and signature
 * strips animate themselves in - a bar that grows from zero, a panel that fades up,
 * a strip that scrolls its text - so the frame the capture lands on is whatever the
 * animation was doing a quarter of a second after `onPageFinished`. That is
 * frequently an empty or half-drawn card, which reads as a broken image rather than
 * as a still of an animation. So an animated document is never captured while the
 * device is animating things: it keeps a real renderer for as long as it is on
 * screen, and gives it up when it scrolls away.
 *
 * ## Why these two shapes, read where they can be
 *
 * An SVG animates through exactly two mechanisms once scripting is stripped, which
 * [sanitizeNovaSvg] does:
 *
 *  - SMIL - `<animate>`, `<animateTransform>`, `<animateMotion>`, `<animateColor>`
 *    and `<set>`. `<set>` is included although it is a single change rather than a
 *    loop: it fires on a `begin` time, so a frame captured before that time is the
 *    *before* state.
 *  - CSS - an `animation` (or `-webkit-animation`, which older exporters still emit)
 *    declaration, or the `@keyframes` it refers to. Those are read only inside a
 *    `<style>` block or a `style="..."` attribute, because that is the only place
 *    a declaration can be one: a bio is prose as often as it is graphics, and a card
 *    whose `<text>` happens to read "animation: the art of moving things" is not a
 *    card that moves.
 *
 * CSS *transitions* are deliberately not matched. They only run on a state change,
 * and the state changes here are hover and focus, which a disabled `WebView` in a
 * scrolling list never receives - marking a card animated for them would put a live
 * renderer on screen for a picture that can never move.
 */

/** SMIL: the elements whose presence means the picture changes over time. */
private val SMIL_ANIMATION = Regex(
    """<(animate|animateTransform|animateMotion|animateColor|set)\b""",
    RegexOption.IGNORE_CASE,
)

/** CSS: a `<style>` block, whose contents are stylesheet text. */
private val SVG_STYLE_BLOCK = Regex("""<style\b[^>]*>([\s\S]*?)</style\s*>""", RegexOption.IGNORE_CASE)

/** CSS: the value of a `style="..."` attribute, the other place a declaration lives. */
private val SVG_STYLE_ATTRIBUTE = Regex("""\bstyle\s*=\s*("[^"]*"|'[^']*')""", RegexOption.IGNORE_CASE)

/** CSS: a keyframe block, with or without a vendor prefix. */
private val CSS_KEYFRAMES = Regex("""@(-[a-z]+-)?keyframes\b""", RegexOption.IGNORE_CASE)

/**
 * CSS: an `animation` or `animation-name` declaration.
 *
 * The lookbehind keeps `my-animation:` and `animations:` from matching; the trailing
 * `:` keeps a property that merely starts with the word - there is none in CSS, but
 * a custom property like `--animation-x` is one character away.
 */
private val CSS_ANIMATION = Regex(
    """(?<![\w-])(-[a-z]+-)?animation(-name)?\s*:""",
    RegexOption.IGNORE_CASE,
)

/**
 * Whether [markup] will draw more than one picture while it is on screen.
 *
 * Read on the sanitised document, not the download: `sanitizeNovaSvg` removes the
 * `<script>` tags that are the third way to animate an SVG, so a document that only
 * moved by script arrives here as static - which is what it is, once the script is
 * gone.
 */
internal fun novaSvgIsAnimated(markup: String): Boolean {
    if (SMIL_ANIMATION.containsMatchIn(markup)) return true
    val css = buildString {
        SVG_STYLE_BLOCK.findAll(markup).forEach { append(it.groupValues[1]).append('\n') }
        SVG_STYLE_ATTRIBUTE.findAll(markup).forEach { append(it.groupValues[1]).append('\n') }
    }
    return CSS_KEYFRAMES.containsMatchIn(css) || CSS_ANIMATION.containsMatchIn(css)
}

/**
 * Whether [asset] may be replaced by a captured frame.
 *
 * Two independent things have to hold, and this is the one place both are decided:
 *
 *  - the document must not animate, or the frame is a still of an animation the
 *    reader was meant to see; and
 *  - the device must still be animating things. With the system's animations
 *    switched off - accessibility, or a developer setting - a card that would
 *    otherwise stay live is captured instead, because a reader who asked for no
 *    motion did not ask for a remote banner to ignore them. In that case even an
 *    animated card is fine as a bitmap: it is not going to move anywhere.
 */
internal fun novaSvgCanBeCaptured(asset: NovaSvgAsset, animatorsEnabled: Boolean): Boolean =
    !asset.animated || !animatorsEnabled
