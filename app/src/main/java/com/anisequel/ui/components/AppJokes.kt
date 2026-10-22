package com.anisequel.ui.components

import kotlin.random.Random

/**
 * The lines the random joke toast draws from.
 *
 * ## Why this is not [ApiFailureBody]'s pool
 *
 * The failure pools are about AniList being missing. This one appears while
 * everything is working, so a line about naps and servers would read as a
 * warning about a problem the reader does not have. These are about the hobby
 * instead - backlogs, recaps, rewatches - which is what a random toast can
 * safely be about during normal use.
 *
 * ## Why it is an object with no Android imports
 *
 * The pool is user-facing text that has to stay duplicate-free and large enough
 * to survive a long session; that is a plain JVM test, not a screenshot. Keeping
 * it free of Compose and Android means the test that pins it runs with the rest
 * of the unit tests instead of needing a device.
 *
 * None of these are emoji: the app draws its own vectors, and a joke is carried
 * by the words rather than by a face.
 */
object AppJokes {

    val lines: List<String> = listOf(
        "Every rewatch is a speedrun of your own feelings.",
        "Your backlog is not a queue. It is a lifestyle.",
        "The opening theme is skippable. The emotional damage is not.",
        "Three episodes in, you are legally invested.",
        "A recap episode is the show asking whether you were paying attention.",
        "Filler arcs build character. Mostly the viewer's patience.",
        "The beach episode is mandatory. The plot can wait.",
        "Nobody has ever finished a watchlist. That is the point of it.",
        "The second season was announced four years ago. Stay strong.",
        "Watching at 1.5x is still watching. Mostly.",
        "The final episode is always next week. Even when it is not.",
        "You paused on the credits to feel something. We saw that.",
        "Every \"just one more episode\" is a promise to your sleep schedule.",
        "The soundtrack knows you are crying. It is playing along.",
        "Tier lists are just opinions wearing a nice font.",
        "Nobody remembers the filler. Except that one beach episode.",
        "Your favourite character survives. In the manga.",
        "The dub is fine. The subtitles are also fine. You are fine.",
        "There is no wrong way to watch anime, only a wrong amount of sleep.",
        "Ani-chan counted your list again. It is still a lot.",
        "Anime openings lie. Nothing is that fast.",
        "That show you dropped in episode two gets good in episode nine.",
        "Your watch history is a diary you did not mean to keep.",
        "The movie recap is canon if you believe hard enough.",
        "One does not simply stop after the first arc.",
        "You have recommended this show to four people. None have started it.",
        "The finale is two episodes long. Bring a snack.",
        "The best arc is the one you are about to rewatch.",
        "You can hear the ending theme starting. Do not skip it.",
        "The manga spoiled the anime. The anime spoiled your evening.",
        "Your comfort show knows exactly what it is doing.",
        "Season two is announced. Your schedule is not.",
        "The side characters deserve a spin-off. So does your backlog.",
        "You said last episode out loud. The app heard you.",
        "A twelve-episode show and a free weekend. We both know how this ends.",
        "The plot twist was in the opening. Nobody watches the opening.",
        "Your queue has a queue. It is queues all the way down.",
        "The cliffhanger ended the episode and your sleep schedule took the fall."
    )

    /** One line, uniformly at random. */
    fun pick(random: Random = Random.Default): String = lines[random.nextInt(lines.size)]

    /**
     * One line that is not [previous].
     *
     * The same joke twice in a row is the one thing that makes a random toast
     * feel mechanical rather than random. The retry loop is bounded because a
     * two-line pool cannot avoid a repeat forever, and an unbounded search over a
     * pool of one would hang the caller.
     */
    fun pickDifferent(previous: String?, random: Random = Random.Default): String {
        if (lines.size < 2) return lines.first()
        repeat(8) {
            val candidate = pick(random)
            if (candidate != previous) return candidate
        }
        return lines.firstOrNull { it != previous } ?: lines.first()
    }
}
