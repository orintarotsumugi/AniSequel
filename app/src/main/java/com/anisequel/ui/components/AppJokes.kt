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
        "The cliffhanger ended the episode and your sleep schedule took the fall.",
        "The sequel was announced before you finished the prequel.",
        "You opened the app to check one show. That was twenty minutes ago.",
        "Nobody has ever needed a tier list. Everybody has made one.",
        "The insert song started and suddenly you are running somewhere.",
        "Your 'on hold' list is a museum of good intentions.",
        "Episode one of a long runner is a commitment, not a sample.",
        "The villain had a point. The soundtrack agreed with them.",
        "You remember the ending theme. Not the ending.",
        "Somewhere, a second season is still 'in production'.",
        "Your list says paused. Your heart says rewatch.",
        "The three-episode rule was invented by somebody with free time.",
        "You will absolutely start it tomorrow. Right after this one.",
        "The episode you skipped had the one line everybody quotes.",
        "Your list is a museum, and you are the only visitor.",
        "A rewatch is the same show with better hindsight.",
        "A two-cour show is a weekend with a plot.",
        "You know the opening lyrics. You do not know the plot.",
        "The movie is canon until somebody mentions the movie.",
        "Your queue has seniority now.",
        "The best episode is always the one you cannot remember.",
        "Nobody stops at the end of an arc. That is what arcs are for.",
        "The side story explained more than the main one.",
        "Season three is where the source material catches up.",
        "You paused to read the subs and missed the face.",
        "The fight was two frames and three weeks of discussion.",
        "Your comfort episode is doing a lot of work.",
        "The plot hole is load-bearing. Leave it alone.",
        "You can hear the insert song. It is that one.",
        "The special was mostly recap and entirely necessary.",
        "You will rewatch the good arc and skip the sad one. Again.",
        "You did not choose the three-episode rule. The three-episode rule chose you.",
        "The power system makes no sense and you have a forty-minute video explaining it.",
        "Your favourite show is one bad recommendation away from ruining a friendship.",
        "Nobody warned you that the recap movie is three movies.",
        "You know the studio. You do not know the director. That is fine.",
        "The side quest episode is load-bearing and nobody admits it.",
        "Somewhere in your list is a show you started during a different life.",
        "The ending was ambiguous. The fan wiki is not.",
        "You have cried at a talking animal. It was a good scene.",
        "The opening changes in season two and you have opinions.",
        "Your backlog has a backlog. It is a family tree now.",
        "The beach episode had no budget and all the impact.",
        "You recognise the voice actor before the character.",
        "The manga reader in your group chat is being very calm about it.",
        "Your comfort show has a sad episode and you skip it every time.",
        "The movie recap added two scenes and you watched all of it.",
        "A show about people eating is somehow the most tense thing you own.",
        "The tournament arc is a calendar event.",
        "You will not skip the second opening. You have tried.",
        "The best fight was in the episode with the worst animation.",
        "Your phone wallpaper is a screenshot from episode nine.",
        "The show has a beach episode, a festival episode and a hot spring episode. It is a documentary.",
        "You have defended a plot hole on the internet. It was a good plot hole.",
        "The ending song starts and you are not ready.",
        "The season finale was a trailer for the next season.",
        "Your watchlist knows things about you that your friends do not.",
        "A recap episode is just the show checking in on you.",
        "You started a rewatch to fall asleep and now it is four in the morning.",
        "The villain's backstory was one episode and it broke you.",
        "Nobody in the show has a normal job and nobody asks.",
        "The opening spoils the ending and you still watch it every time.",
        "The filler episode introduced your favourite character.",
        "You have a show you only watch when it rains.",
        "The second movie does not exist. You and the fandom have agreed.",
        "Your queue is sorted by mood, not by release date. Obviously.",
        "The episode with the train was a bottle episode and a masterpiece.",
        "You paused to check the character's name and lost the plot.",
        "The new season premiered and you are still on season one.",
        "The studio announced a movie and the fandom has already written essays.",
        "You keep a mental list of shows to recommend and never recommend them.",
        "The plot is a metaphor for growing up. The beach episode is not.",
        "Your favourite character has seven minutes of screen time.",
        "The show ended and the fan art did not.",
        "You have three tabs open about a show you have not started.",
        "The opening band got popular and now the tickets cost a season.",
        "The dub has one line that lives in your head forever.",
        "You know the exact episode where it gets good.",
        "The show is about grief and also giant robots. Both work.",
        "Your watchlist has a show you added for the poster alone.",
        "The spin-off is better than it has any right to be.",
        "You have watched the same fight scene more than the show.",
        "The episode title was a spoiler and you read it anyway.",
        "The fandom invented a ship and the show noticed.",
        "You have a favourite ending song and it is not the popular one.",
        "The show is twelve episodes and the fandom has a three-hour lore video.",
        "Your 'planning' list is a wish, not a plan.",
        "The character you related to in season one is the villain now.",
        "You can name the studio from the lighting.",
        "The anime-original ending has its defenders. You are one of them.",
        "The show has a talking cat. That is the whole pitch.",
        "You started it for the animation and stayed for the feelings.",
        "The second arc is better and nobody believes you.",
        "The show has an episode about cooking and it is the best one.",
        "You have recommended the same show to three people this week.",
        "The opening is 90 seconds and you have heard it 400 times.",
        "The show ended on a cliffhanger and the manga is on hiatus.",
        "You have a favourite background character. It is fine.",
        "The show is good. The fan edits are better. Do not tell anyone.",
        "You know the release schedule by heart and still check daily.",
        "The season is eight episodes and the recap takes two.",
        "Your comfort character is problematic and you have made peace.",
        "The show was cancelled and the fandom is still writing letters.",
        "The new animation style grew on you. It took a season.",
        "You have watched a show for a side character and no other reason.",
        "The show's pacing is a crime and you love it anyway.",
        "You can hear the soundtrack in your head right now.",
        "The show has a time skip and everyone looks tired.",
        "The best episode has almost no dialogue.",
        "You have a show you only watch in the background and still follow.",
        "The show has a hot spring episode and the plot waited. Respect.",
        "Your list has a show from a genre you claimed not to like.",
        "The show made you look up a real historical figure.",
        "The ending was rushed and you have accepted it. Mostly.",
        "You have a personal ranking of the openings. So does everyone.",
        "The show has a narrator and he is the best character.",
        "You started a show because a clip was 15 seconds long.",
        "The show has an episode from the villain's perspective and it rules.",
        "You are three episodes behind and the group chat is a minefield.",
        "The show's food looks better than anything you ate today.",
        "You have a show you refuse to finish because then it is over.",
        "The show has a cold open and you always forget the plot.",
        "You know a show is good when the credits song hurts.",
        "The show's title is a spoiler in a language you do not speak.",
        "You have an anime you only recommend to people you trust.",
        "The show has a training arc and you have notes.",
        "You have watched a finale twice and cried once. Guess which.",
        "The show's mascot character is doing more plot than the hero.",
        "Your 'dropped' list is mostly shows you will restart next year.",
        "The show has a silent episode and it is the loudest one.",
        "You recognise the composer's work in the first four notes.",
        "The show's worldbuilding is in the background art and you noticed.",
        "You have quoted a line at somebody who has not seen it. Worth it.",
        "The show has a rival who is right and a hero who is loud.",
        "The anime is a commercial for the manga and it worked.",
        "You have a show you watch only for the ending songs.",
        "The show's cast is enormous and you know all their names.",
        "The episode was 22 minutes and the discussion was four hours.",
        "You can tell the budget episode by the still frames.",
        "The show's best arc is the one everybody else skips.",
        "You have a favourite season and it is not the first one."
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

    /**
     * One line that is not in [seen], or a fresh pick once the pool is spent.
     *
     * Avoiding only the previous line meant the toast could repeat a joke it had
     * told an hour ago - a reader does not experience the pool as a sequence of
     * adjacent draws, they experience it as "has this app told me this before".
     * The caller keeps the set and clears it when every line has been used, which
     * turns the pool into a shuffle bag: each line once per cycle, in random
     * order, and never the same line twice in a row across the seam.
     */
    fun pickUnseen(seen: Set<String>, random: Random = Random.Default): String {
        if (lines.isEmpty()) return ""
        val unseen = lines.filterNot { it in seen }
        if (unseen.isEmpty()) return pick(random)
        return unseen[random.nextInt(unseen.size)]
    }
}
